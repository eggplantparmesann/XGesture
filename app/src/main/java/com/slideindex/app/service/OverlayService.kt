package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.shake.FaceDownGestureHost
import com.slideindex.app.shake.ShakeGestureHost
import com.slideindex.app.util.ForegroundNotificationChannels
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service for persistent notification and settings sync.
 * Edge overlays are hosted by [SlideIndexAccessibilityService] (SideGesture-style).
 */
@dagger.hilt.android.AndroidEntryPoint
class OverlayService : LifecycleService() {

    @javax.inject.Inject lateinit var deps: AppDependencies
    @javax.inject.Inject lateinit var shakeGestureHost: ShakeGestureHost
    @javax.inject.Inject lateinit var faceDownGestureHost: FaceDownGestureHost

    override fun onCreate() {
        super.onCreate()
        // 渠道由 [promoteToForeground] 内部先建好再 startForeground，这里不再单独建。
        promoteToForeground()
        GestureToggleTileWarmup.requestListening(this, "overlayService")
        shakeGestureHost.start(lifecycleScope)
        faceDownGestureHost.start(lifecycleScope)
        // 立刻回一帧状态：主进程的看门狗"唤醒 overlay"之后靠这帧判断进程是否真的活着。
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "serviceStart")
        lifecycleScope.launch {
            OverlayServiceLifecycle.recoverAccessibilityBinding(
                this@OverlayService,
                deps.settingsRepository.readSnapshot(),
            )
        }
        startAccessibilityWatchdog()
    }

    private fun startAccessibilityWatchdog() {
        lifecycleScope.launch {
            while (isActive) {
                delay(ACCESSIBILITY_WATCHDOG_INTERVAL_MS)
                // 心跳：主进程以此判断 overlay 进程是否还活着（镜像新鲜度）。
                com.slideindex.app.overlay.OverlayStatePort.publish(this@OverlayService, "heartbeat")
                val settings = deps.settingsRepository.settings.first()
                if (!settings.serviceEnabled) {
                    AccessibilityRecoverNotifier.clearOffline(this@OverlayService)
                    continue
                }
                val outcome = OverlayServiceLifecycle.recoverAccessibilityBinding(
                    this@OverlayService,
                    settings,
                )
                if (outcome == AccessibilityRecoverOutcome.Failed) {
                    // 设置里显示已开启、实际却连不上（覆盖安装或被系统杀掉后系统拒绝重绑）：
                    // 静默重绑做不到时，得明确告诉用户点哪里恢复，不能只写日志。
                    AccessibilityRecoverNotifier.notifyOffline(this@OverlayService)
                } else {
                    AccessibilityRecoverNotifier.clearOffline(this@OverlayService)
                }
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // startForegroundService() requires startForeground() on every delivery, not only in onCreate().
        if (!promoteToForeground()) {
            // 渠道被用户关闭时只降级、不自杀：常驻能力靠无障碍服务兜底，自杀反而会让用户更难恢复。
            Log.w(TAG, "本次唤醒未能进入前台，服务继续以非前台状态运行")
        }
        // 每一次被"唤醒"都回一帧状态：主进程看门狗靠这帧判断 :overlay 是否活着。
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "serviceCommand")
        when (intent?.action) {
            ACTION_RELOAD_APPS -> SlideIndexAccessibilityService.reloadApps()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        shakeGestureHost.stop()
        faceDownGestureHost.stop()
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean {
        // 硬前提：先建渠道并回查确认它存在，再 startForeground。
        // 渠道取不到时 AMS 会在 ServiceRecord.postNotification() 里判为非法通知并
        // killMisbehavingService()，进程被系统强杀（日志：Bad notification for startForeground）。
        // 那个判定在 AMS 的 handler 线程异步执行，所以外面套 try/catch 是无效的。
        if (!ensureNotificationChannel().canPromote) {
            Log.e(TAG, "通知渠道不可用，跳过前台化，避免被系统判定为非法前台服务通知")
            return false
        }
        val notification = buildNotification()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            Log.e(TAG, "startForeground failed", error)
        }.isSuccess
    }

    /** 幂等建渠道并回查：返回 [ForegroundNotificationChannels.Result]。 */
    private fun ensureNotificationChannel(): ForegroundNotificationChannels.Result =
        ForegroundNotificationChannels.ensureUsable(
            context = this,
            id = CHANNEL_ID,
            name = getString(R.string.app_name),
            importance = NotificationManager.IMPORTANCE_LOW,
            tag = TAG,
        )

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "OverlayService"

        const val ACTION_RELOAD_APPS = "com.slideindex.app.RELOAD_APPS"

        @Volatile
        var foregroundPackage: String? = null

        @Volatile
        var gestureForegroundPackage: String? = null

        fun captureGestureForegroundPackage() {
            gestureForegroundPackage = foregroundPackage
        }

        private const val CHANNEL_ID = "slide_index_service"
        private const val NOTIFICATION_ID = 1001
        private const val ACCESSIBILITY_WATCHDOG_INTERVAL_MS = 25_000L
    }
}
