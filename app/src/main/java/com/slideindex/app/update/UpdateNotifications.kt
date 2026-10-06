package com.slideindex.app.update

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
import com.slideindex.app.util.ForegroundNotificationChannels

object UpdateNotifications {
    private const val CHANNEL_UPDATE = "app_update"
    private const val CHANNEL_DOWNLOAD = "app_update_download"

    const val NOTIFICATION_ID_NEW_VERSION = 7101
    const val NOTIFICATION_ID_DOWNLOAD = 7102

    fun ensureChannels(context: Context) {
        // 只做幂等 upsert，不再 delete + recreate：删除会导致渠道出现"短暂不存在"的窗口，
        // 若此刻前台服务正要 startForeground 就会被系统判为非法通知而杀进程。
        // 已存在的渠道重要性由系统按用户设置保留，createNotificationChannel 不会覆盖用户选择。
        ForegroundNotificationChannels.create(
            context = context,
            id = CHANNEL_UPDATE,
            name = context.getString(R.string.update_channel_update),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )
        ForegroundNotificationChannels.create(
            context = context,
            id = CHANNEL_DOWNLOAD,
            name = context.getString(R.string.update_channel_download),
            importance = NotificationManager.IMPORTANCE_LOW,
        )
    }

    /**
     * 下载前台服务专用：建渠道并确认它可用。
     * [com.slideindex.app.update.DownloadService.startForeground] 之前必须先过这一关。
     */
    fun ensureDownloadChannel(context: Context): ForegroundNotificationChannels.Result =
        ForegroundNotificationChannels.ensureUsable(
            context = context,
            id = CHANNEL_DOWNLOAD,
            name = context.getString(R.string.update_channel_download),
            importance = NotificationManager.IMPORTANCE_LOW,
            tag = "UpdateDownload",
        )

    private fun contentIntent(context: Context, showUpdate: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (showUpdate) {
                putExtra(UpdateIntents.EXTRA_SHOW_UPDATE, true)
            }
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun showNewVersion(context: Context, version: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_new_title))
            .setContentText(
                context.getString(
                    R.string.update_notification_new_content,
                    UpdateChecker.displayVersion(version),
                ),
            )
            .setContentIntent(contentIntent(context, showUpdate = true))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(context, NOTIFICATION_ID_NEW_VERSION, notification)
    }

    fun cancelNewVersion(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_NEW_VERSION)
    }

    fun buildDownloadNotification(context: Context, progress: Int): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_downloading_title))
            .setContentText("$progress%")
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context, showUpdate = false))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyDownloadProgress(context: Context, progress: Int) {
        notifyIfPermitted(
            context,
            NOTIFICATION_ID_DOWNLOAD,
            buildDownloadNotification(context, progress),
        )
    }

    fun showDownloadDone(context: Context, version: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_done_title))
            .setContentText(
                context.getString(
                    R.string.update_notification_done_content,
                    UpdateChecker.displayVersion(version),
                ),
            )
            .setContentIntent(contentIntent(context, showUpdate = false))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(context, NOTIFICATION_ID_DOWNLOAD, notification)
    }

    fun showDownloadFailed(context: Context) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_failed_title))
            .setContentText(context.getString(R.string.update_notification_failed_content))
            .setContentIntent(contentIntent(context, showUpdate = false))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(context, NOTIFICATION_ID_DOWNLOAD, notification)
    }

    private fun notifyIfPermitted(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
