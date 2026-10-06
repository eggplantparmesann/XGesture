package com.slideindex.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState
import com.slideindex.app.util.ForegroundNotificationChannels
import kotlin.math.roundToInt

object NativeEnginePackDownloadNotifications {
    private const val CHANNEL_ID = "native_engine_pack_download"
    const val NOTIFICATION_ID = 3002

    /**
     * 幂等建渠道并回查确认存在。
     *
     * 不再"已存在就跳过"：渠道被删掉时要重建，被关掉时要能被调用方感知。
     * [NativeEnginePackDownloadService.startForeground] 之前必须先过这一关。
     */
    fun ensureChannel(context: Context): ForegroundNotificationChannels.Result =
        ForegroundNotificationChannels.ensureUsable(
            context = context,
            id = CHANNEL_ID,
            name = context.getString(R.string.native_engine_download_channel),
            importance = NotificationManager.IMPORTANCE_LOW,
            tag = "NativeEnginePackDownload",
        )

    fun buildDownloadNotification(
        context: Context,
        state: NativeEnginePackDownloadState?
    ): Notification {
        ensureChannel(context)
        val title = context.getString(R.string.native_engine_download_title)
        val text = when (state?.phase) {
            NativeEnginePackDownloadPhase.DOWNLOADING ->
                context.getString(R.string.native_engine_download_progress)
            NativeEnginePackDownloadPhase.VERIFYING ->
                context.getString(R.string.native_engine_download_verifying)
            NativeEnginePackDownloadPhase.EXTRACTING ->
                context.getString(R.string.native_engine_download_extracting)
            NativeEnginePackDownloadPhase.READY ->
                context.getString(R.string.native_engine_download_done)
            NativeEnginePackDownloadPhase.FAILED ->
                context.getString(R.string.native_engine_download_failed)
            NativeEnginePackDownloadPhase.CANCELLED ->
                context.getString(R.string.native_engine_download_cancelled)
            else -> context.getString(R.string.native_engine_download_starting)
        }
        val progress = state?.progress?.times(100f)?.roundToInt()?.coerceIn(0, 100)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (progress != null) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }
}
