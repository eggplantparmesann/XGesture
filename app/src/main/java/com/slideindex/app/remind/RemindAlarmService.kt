package com.slideindex.app.remind

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.slideindex.app.R
import com.slideindex.app.util.ForegroundNotificationChannels

class RemindAlarmService : Service() {
    private var ringtone: Ringtone? = null
    private var stopped = false
    private var overlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopAlarm() }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) stopAlarm()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val minutes = intent?.getIntExtra(RemindAlarmReceiver.EXTRA_MINUTES, 1) ?: 1
        startAlarm(minutes)
        return START_NOT_STICKY
    }

    private fun startAlarm(minutes: Int) {
        if (stopped) return
        val channelId = CHANNEL_ID
        // 先建渠道并确认存在，再 startForeground：渠道缺失会让系统把这条通知判为非法，
        // 进而在 AMS 侧异步 killMisbehavingService()（Bad notification for startForeground）。
        val channel = ForegroundNotificationChannels.ensureUsable(
            context = this,
            id = channelId,
            name = getString(R.string.gesture_remind_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
            tag = TAG,
        )
        if (!channel.canPromote) {
            Log.e(TAG, "提醒通知渠道不可用，跳过前台化，仅显示浮层")
            showOverlay(minutes)
            playAlarmSound()
            vibrate()
            mainHandler.postDelayed(autoStopRunnable, 5 * 60_000L)
            return
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.gesture_remind_notify_title))
            .setContentText(getString(R.string.gesture_remind_notify_body, minutes))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()
        runCatching { startForeground(NOTIFY_ID_BASE + minutes, notification) }
            .onFailure { error -> Log.e(TAG, "startForeground failed", error) }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        showOverlay(minutes)
        playAlarmSound()
        vibrate()
        mainHandler.postDelayed(autoStopRunnable, 5 * 60_000L)
    }

    @Suppress("DEPRECATION")
    private fun showOverlay(minutes: Int) {
        runCatching {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val textColor = if (isDark) 0xFFF0F6FC.toInt() else 0xFF1F2328.toInt()
            val bodyColor = if (isDark) 0xFF8B949E.toInt() else 0xFF656D76.toInt()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            val root = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (isDark) Color.argb(180, 0, 0, 0) else Color.argb(160, 255, 255, 255))
                setOnClickListener { stopAlarm() }
            }
            root.addView(TextView(this).apply {
                text = "⏰"
                textSize = 48f
                gravity = Gravity.CENTER
            })
            root.addView(TextView(this).apply {
                text = getString(R.string.gesture_remind_notify_body, minutes)
                textSize = 16f
                setTextColor(bodyColor)
                gravity = Gravity.CENTER
                setPadding(0, 48, 0, 0)
            })
            wm.addView(root, params)
            overlayView = root
        }
    }

    private fun playAlarmSound() {
        runCatching {
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        }
    }

    private fun vibrate() {
        runCatching {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 1000, 500), intArrayOf(1, 0), 2)
            )
        }
    }

    private fun stopAlarm() {
        if (stopped) return
        stopped = true
        mainHandler.removeCallbacks(autoStopRunnable)
        ringtone?.stop()
        ringtone = null
        runCatching { unregisterReceiver(screenOffReceiver) }
        overlayView?.let { view ->
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) }
            overlayView = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RemindAlarmService"
        private const val CHANNEL_ID = "gesture_remind_ring"
        const val NOTIFY_ID_BASE = 5000
    }
}
