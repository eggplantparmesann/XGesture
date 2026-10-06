package com.slideindex.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.util.ForegroundNotificationChannels
import kotlin.math.roundToInt

object OcrModelDownloadNotifications {

    private const val CHANNEL_ID = "ocr_model_download"
    const val NOTIFICATION_ID = 3001

    /**
     * 幂等建渠道并回查确认存在。
     *
     * 不再"已存在就跳过"：渠道被删掉时要重建，被关掉时要能被调用方感知。
     * [OcrModelDownloadService.startForeground] 之前必须先过这一关。
     */
    fun ensureChannel(context: Context): ForegroundNotificationChannels.Result =
        ForegroundNotificationChannels.ensureUsable(
            context = context,
            id = CHANNEL_ID,
            name = context.getString(R.string.ocr_download_notification_channel),
            importance = NotificationManager.IMPORTANCE_LOW,
            tag = "OcrModelDownload",
        )

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun buildDownloadNotification(context: Context, state: OcrModelDownloadState?): Notification {
        ensureChannel(context)
        val title = context.getString(R.string.ocr_download_notification_title)
        val text = when (state?.phase) {
            OcrModelDownloadPhase.DOWNLOADING -> {
                val downloaded = formatMegabytes(state.bytesDownloaded)
                val total = state.totalBytes?.let(::formatMegabytes)
                if (total != null) {
                    context.getString(R.string.ocr_download_notification_progress, downloaded, total)
                } else {
                    context.getString(R.string.ocr_download_notification_progress_indeterminate, downloaded)
                }
            }
            OcrModelDownloadPhase.VERIFYING ->
                context.getString(R.string.ocr_download_notification_verifying)
            OcrModelDownloadPhase.FINALIZING ->
                context.getString(R.string.ocr_download_notification_finalizing)
            OcrModelDownloadPhase.READY ->
                context.getString(R.string.ocr_download_notification_done)
            OcrModelDownloadPhase.FAILED ->
                context.getString(R.string.ocr_download_notification_failed)
            OcrModelDownloadPhase.CANCELLED ->
                context.getString(R.string.ocr_download_notification_cancelled)
            else -> context.getString(R.string.ocr_download_notification_starting)
        }
        val progress = state?.progress?.times(100f)?.roundToInt()?.coerceIn(0, 100)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            .setOngoing(state?.phase == OcrModelDownloadPhase.DOWNLOADING)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progress != null && state.phase == OcrModelDownloadPhase.DOWNLOADING) {
            builder.setProgress(100, progress, false)
        }
        return builder.build()
    }

    fun notify(context: Context, state: OcrModelDownloadState?) {
        notifyIfPermitted(context, buildDownloadNotification(context, state))
    }

    fun showDone(context: Context) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.ocr_download_notification_title))
            .setContentText(context.getString(R.string.ocr_download_notification_done))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(context, notification)
    }

    fun showFailed(context: Context) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.ocr_download_notification_title))
            .setContentText(context.getString(R.string.ocr_download_notification_failed))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(context, notification)
    }

    private fun notifyIfPermitted(context: Context, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun formatMegabytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb < 10.0) {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            "${mb.roundToInt()} MB"
        }
    }
}
