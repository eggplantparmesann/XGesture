package com.slideindex.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.slideindex.app.R

/**
 * 前台服务通知的渠道保障。
 *
 * 背景：`Service.startForeground()` 用的通知渠道一旦取不到，AMS 会在
 * `ServiceRecord.postNotification()` 里直接抛异常并走
 * `killMisbehavingService()`，进程必崩（日志表现为
 * `RemoteServiceException$CannotPostForegroundServiceNotificationException:
 * Bad notification for startForeground`）。更麻烦的是那条判定发生在 AMS 的
 * handler 线程、异步执行，所以在 `startForeground()` 外面包 `try/catch` 是无效的：
 * 调用本身通常正常返回。
 *
 * 因此规矩定死：**先把渠道建出来、再确认它真的存在，最后才 `startForeground()`**。
 * 建渠道是幂等的（已存在时是空操作，被删掉时会重建），所以下面统一走
 * 「建 → 查」两步。
 */
object ForegroundNotificationChannels {

    private const val TAG = "FgsNotificationChannel"

    /** 渠道为 [NotificationManager.IMPORTANCE_NONE] 时返回该常量，日志里比裸 0 好读。 */
    private fun describeImportance(importance: Int): String =
        if (importance == NotificationManager.IMPORTANCE_NONE) "NONE(用户已关闭)" else importance.toString()

    /**
     * 幂等建渠道；已存在则系统忽略本次调用。
     *
     * 返回创建之后回查到的 [NotificationChannel]，取不到时返回 null。
     */
    fun create(
        context: Context,
        id: String,
        name: String,
        importance: Int,
        description: String? = null,
        configure: (NotificationChannel.() -> Unit)? = null,
    ): NotificationChannel? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        return runCatching {
            val channel = NotificationChannel(id, name, importance).apply {
                description?.let { this.description = it }
                configure?.invoke(this)
            }
            manager.createNotificationChannel(channel)
            manager.getNotificationChannel(id)
        }.onFailure { error ->
            Log.e(TAG, "create channel($id) failed", error)
        }.getOrNull()
    }

    /**
     * 建渠道并确认它此刻可用。**必须在 [android.app.Service.startForeground] 之前调用。**
     *
     * [NotificationManager.IMPORTANCE_NONE]（用户关掉了这个渠道）也会返回 [exists]
     * 为 true——渠道记录还在，只是被静音。那种情况下 startForeground 不一定会崩，
     * 但通知对用户不可见，调用方通常应该据此静默降级。
     */
    fun ensureUsable(
        context: Context,
        id: String,
        name: String,
        importance: Int,
        description: String? = null,
        tag: String = TAG,
        configure: (NotificationChannel.() -> Unit)? = null,
    ): Result {
        val created = create(context, id, name, importance, description, configure)
        val channel = created ?: runCatching {
            context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(id)
        }.getOrNull()
        if (channel == null) {
            val reason = "channel($id) 创建后回查仍为空：startForeground 会被系统判为非法通知并杀掉进程"
            Log.e(tag, reason)
            return Result(exists = false, blocked = false, importance = Int.MIN_VALUE, reason = reason)
        }
        val blocked = channel.importance == NotificationManager.IMPORTANCE_NONE
        if (blocked) {
            Log.e(
                tag,
                "channel($id) importance=${describeImportance(channel.importance)}：" +
                    "用户在系统设置里关掉了这个通知渠道，startForeground 通知对用户不可见",
            )
        }
        return Result(
            exists = true,
            blocked = blocked,
            importance = channel.importance,
            reason = if (blocked) {
                "channel($id) 当前可见性为 IMPORTANCE_NONE（用户在系统设置里关掉了这个通知渠道）：" +
                    "startForeground 后用户看不到通知，且部分 ROM 会直接判为非法通知"
            } else {
                null
            },
        )
    }

    data class Result(
        /** 渠道在系统里确实存在——这是 startForeground 不崩的硬前提。 */
        val exists: Boolean,
        /** 渠道存在但被用户关闭（importance == NONE）。 */
        val blocked: Boolean,
        val importance: Int,
        val reason: String?,
    ) {
        /** 存在且未被关闭，可以安全地 startForeground。 */
        val canPromote: Boolean get() = exists && !blocked
    }

    /**
     * 启动期预建"随时可能被拉起"的常驻渠道。
     *
     * 覆盖两类高危时机：开机自启/覆盖安装后 AMS 立刻重启常驻服务、以及清数据后第一次冷启。
     * 渠道信息与各 Service 内部使用的保持一致（渠道名/重要性改这里一处即可）。
     * 只做幂等 upsert，不改用户已做的选择。
     */
    fun preCreatePersistentChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        create(
            context,
            id = "slide_index_service",
            name = context.getString(R.string.app_name),
            importance = NotificationManager.IMPORTANCE_LOW,
        )
        create(
            context,
            id = "clipboard_monitor",
            name = context.getString(R.string.clipboard_monitor_notification_channel_name),
            importance = NotificationManager.IMPORTANCE_MIN,
            description = context.getString(R.string.clipboard_monitor_notification_channel_desc),
        )
        create(
            context,
            id = "gesture_remind_ring",
            name = context.getString(R.string.gesture_remind_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
        )
        create(
            context,
            id = "screen_capture",
            name = context.getString(R.string.screen_capture_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
        )
        create(
            context,
            id = "screen_record",
            name = context.getString(R.string.screen_record_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
        )
    }
}
