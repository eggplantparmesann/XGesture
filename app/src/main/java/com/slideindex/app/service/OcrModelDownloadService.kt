package com.slideindex.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.slideindex.app.download.DownloadProgressRelay
import com.slideindex.app.download.OcrModelDownloadChannel
import com.slideindex.app.ocr.OcrEntryPoint
import com.slideindex.app.ocr.OcrModelDownloadController
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.ocr.OcrModelDownloader
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台下载 Service：大模型文件后台不被杀，下载状态与设置页 ViewModel 解耦。
 */
class OcrModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastStartId = -1

    private val downloader: OcrModelDownloader by lazy {
        EntryPointAccessors.fromApplication(applicationContext, OcrEntryPoint::class.java)
            .ocrModelDownloader()
    }

    /** 把进度发到主进程（设置页），`:engine` 里的单例主进程读不到。 */
    private val progressRelay by lazy {
        DownloadProgressRelay(applicationContext, OcrModelDownloadChannel.ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForegroundCompat(OcrModelDownloadController.state.value)

        if (job?.isActive == true) {
            val requestedModelId = intent?.getStringExtra(EXTRA_MODEL_ID).orEmpty()
            if (requestedModelId.isNotBlank() &&
                requestedModelId != OcrModelDownloadController.activeModelId
            ) {
                OcrModelDownloadController.update(
                    OcrModelDownloadState(
                        modelId = requestedModelId,
                        phase = OcrModelDownloadPhase.FAILED,
                        errorMessage = "another_download_in_progress"
                    )
                )
            }
            return START_NOT_STICKY
        }

        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID).orEmpty()
        val wifiOnly = intent?.getBooleanExtra(EXTRA_WIFI_ONLY, false) == true
        if (modelId.isBlank()) {
            stopSelfResult(lastStartId)
            return START_NOT_STICKY
        }

        OcrModelDownloadController.onStart(modelId)
        OcrModelDownloadController.update(
            OcrModelDownloadState(
                modelId = modelId,
                phase = OcrModelDownloadPhase.DOWNLOADING
            )
        )

        job = scope.launch {
            var lastNotifiedProgress = -1
            val observer = launch {
                OcrModelDownloadController.state.collectLatest { state ->
                    if (state == null) return@collectLatest
                    val progress = state.progress?.times(100f)?.toInt() ?: -1
                    if (state.phase == OcrModelDownloadPhase.DOWNLOADING &&
                        progress >= 0 &&
                        (progress - lastNotifiedProgress >= NOTIFY_STEP || progress >= 100)
                    ) {
                        lastNotifiedProgress = progress
                        OcrModelDownloadNotifications.notify(this@OcrModelDownloadService, state)
                    }
                    startForegroundCompat(state)
                    progressRelay.publish(
                        phase = state.phase.name,
                        payload = OcrModelDownloadChannel.encode(state),
                        percent = if (state.phase == OcrModelDownloadPhase.DOWNLOADING) {
                            progress
                        } else {
                            null
                        },
                    )
                }
            }

            downloader.executeDownload(modelId, wifiOnly)

            // 先停掉转发，再显式补发终态：终态是在 IO 线程写进 controller 的，observer 在主线程上
            // 还没轮到处理就被 cancel，那一次发射会被丢掉——这正是"下载完成页面收不到 READY →
            // 不自动选中、进度卡直接消失"的根因。
            observer.cancel()
            val finalState = OcrModelDownloadController.state.value
            if (finalState != null) {
                progressRelay.publish(
                    phase = finalState.phase.name,
                    percent = null,
                    payload = OcrModelDownloadChannel.encode(finalState),
                    force = true,
                )
            }
            when (finalState?.phase) {
                OcrModelDownloadPhase.READY -> {
                    stopForegroundCompat()
                    OcrModelDownloadNotifications.showDone(this@OcrModelDownloadService)
                }
                OcrModelDownloadPhase.FAILED,
                OcrModelDownloadPhase.CANCELLED,
                -> {
                    stopForegroundCompat()
                    OcrModelDownloadNotifications.showFailed(this@OcrModelDownloadService)
                }
                else -> stopForegroundCompat()
            }
            // 这里**不能**清快照：终态广播可能还在投递路上，清掉会让订阅方读到 null；
            // 过期由订阅侧按时间戳判定（DownloadProgressChannel.isFresh）。
            OcrModelDownloadController.clearActive()
            stopSelfResult(lastStartId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        job?.cancel()
        val modelId = OcrModelDownloadController.activeModelId
        if (!modelId.isNullOrBlank()) {
            val failed = OcrModelDownloadState(
                modelId = modelId,
                phase = OcrModelDownloadPhase.FAILED,
                errorMessage = "download_timeout"
            )
            OcrModelDownloadController.update(failed)
            // 超时也要让页面看见失败，而不是只剩"进度卡住了"。
            progressRelay.publish(
                phase = failed.phase.name,
                percent = null,
                payload = OcrModelDownloadChannel.encode(failed),
                force = true,
            )
        }
        OcrModelDownloadController.clearActive()
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        OcrModelDownloadController.clearActive()
        super.onDestroy()
    }

    private fun startForegroundCompat(state: OcrModelDownloadState?) {
        // 渠道先建好并确认存在，再 startForeground：渠道取不到时系统会判为非法前台服务
        // 通知并在 AMS 侧异步杀进程（Bad notification for startForeground）。
        if (!OcrModelDownloadNotifications.ensureChannel(this).canPromote) {
            Log.e(TAG, "下载通知渠道不可用，跳过后台下载前台化")
            return
        }
        val notification = OcrModelDownloadNotifications.buildDownloadNotification(this, state)
        ServiceCompat.startForeground(
            this,
            OcrModelDownloadNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "OcrModelDownloadSvc"
        private const val EXTRA_MODEL_ID = "extra_model_id"
        private const val EXTRA_WIFI_ONLY = "extra_wifi_only"
        private const val NOTIFY_STEP = 2

        fun start(context: Context, modelId: String, wifiOnly: Boolean) {
            val intent = Intent(context, OcrModelDownloadService::class.java).apply {
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_WIFI_ONLY, wifiOnly)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
