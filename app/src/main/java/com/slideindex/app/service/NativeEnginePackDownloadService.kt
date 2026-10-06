package com.slideindex.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.slideindex.app.download.DownloadProgressRelay
import com.slideindex.app.download.NativeEnginePackDownloadChannel
import com.slideindex.app.nativeengine.NativeEngineEntryPoint
import com.slideindex.app.nativeengine.NativeEnginePackDownloadController
import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState
import com.slideindex.app.nativeengine.NativeEnginePackDownloader
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NativeEnginePackDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastStartId = -1

    private val downloader: NativeEnginePackDownloader by lazy {
        EntryPointAccessors.fromApplication(applicationContext, NativeEngineEntryPoint::class.java)
            .nativeEnginePackDownloader()
    }

    /** 进度发到主进程设置页（下载服务在 :engine，主进程读不到进程内单例）。 */
    private val progressRelay by lazy {
        DownloadProgressRelay(applicationContext, NativeEnginePackDownloadChannel.ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForegroundCompat(NativeEnginePackDownloadController.state.value)

        if (job?.isActive == true) {
            val requestedPackId = intent?.getStringExtra(EXTRA_PACK_ID).orEmpty()
            if (requestedPackId.isNotBlank() &&
                requestedPackId != NativeEnginePackDownloadController.activePackId
            ) {
                NativeEnginePackDownloadController.update(
                    NativeEnginePackDownloadState(
                        packId = requestedPackId,
                        phase = NativeEnginePackDownloadPhase.FAILED,
                        errorMessage = "another_download_in_progress"
                    )
                )
            }
            return START_NOT_STICKY
        }

        val packId = intent?.getStringExtra(EXTRA_PACK_ID).orEmpty()
        val wifiOnly = intent?.getBooleanExtra(EXTRA_WIFI_ONLY, false) == true
        if (packId.isBlank()) {
            stopSelfResult(lastStartId)
            return START_NOT_STICKY
        }

        NativeEnginePackDownloadController.onStart(packId)
        NativeEnginePackDownloadController.update(
            NativeEnginePackDownloadState(
                packId = packId,
                phase = NativeEnginePackDownloadPhase.DOWNLOADING
            )
        )

        job = scope.launch {
            val observer = launch {
                NativeEnginePackDownloadController.state.collectLatest { state ->
                    if (state == null) return@collectLatest
                    progressRelay.publish(
                        phase = state.phase.name,
                        percent = if (state.phase == NativeEnginePackDownloadPhase.DOWNLOADING) {
                            state.progress?.times(100f)?.toInt()
                        } else {
                            null
                        },
                        payload = NativeEnginePackDownloadChannel.encode(state),
                    )
                }
            }
            downloader.executeDownload(packId, wifiOnly)
            // 与 OCR 下载同理：终态在 IO 线程写入，observer 在主线程被 cancel 之前来不及转发，
            // 必须显式补发一次，否则页面收不到 READY/FAILED。
            observer.cancel()
            val finalState = NativeEnginePackDownloadController.state.value
            if (finalState != null) {
                progressRelay.publish(
                    phase = finalState.phase.name,
                    percent = null,
                    payload = NativeEnginePackDownloadChannel.encode(finalState),
                    force = true,
                )
            }
            when (finalState?.phase) {
                NativeEnginePackDownloadPhase.READY -> stopForegroundCompat()
                NativeEnginePackDownloadPhase.FAILED,
                NativeEnginePackDownloadPhase.CANCELLED,
                -> stopForegroundCompat()
                else -> stopForegroundCompat()
            }
            NativeEnginePackDownloadController.clearActive()
            stopSelfResult(lastStartId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        NativeEnginePackDownloadController.clearActive()
        super.onDestroy()
    }

    private fun startForegroundCompat(state: NativeEnginePackDownloadState?) {
        // 渠道先建好并确认存在，再 startForeground：渠道取不到时系统会判为非法前台服务
        // 通知并在 AMS 侧异步杀进程（Bad notification for startForeground）。
        if (!NativeEnginePackDownloadNotifications.ensureChannel(this).canPromote) {
            Log.e(TAG, "下载通知渠道不可用，跳过后台下载前台化")
            return
        }
        val notification = NativeEnginePackDownloadNotifications.buildDownloadNotification(this, state)
        ServiceCompat.startForeground(
            this,
            NativeEnginePackDownloadNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "NativeEngineDlSvc"
        private const val EXTRA_PACK_ID = "extra_pack_id"
        private const val EXTRA_WIFI_ONLY = "extra_wifi_only"

        fun start(context: Context, packId: String, wifiOnly: Boolean) {
            val intent = Intent(context, NativeEnginePackDownloadService::class.java).apply {
                putExtra(EXTRA_PACK_ID, packId)
                putExtra(EXTRA_WIFI_ONLY, wifiOnly)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
