package com.slideindex.app.translate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.slideindex.app.util.queryIntentActivitiesCompat

/**
 * 「跳转翻译」能用的 App 通道探测。
 *
 * 文本直送（`ACTION_PROCESS_TEXT`）首选：文本会进目标 App 的输入框；
 * 分享（`ACTION_SEND`）兜底：部分 App 只注册了分享入口。
 *
 * 探测用 [PackageManager.MATCH_DEFAULT_ONLY]，和真正 `startActivity` 隐式 Intent 的条件一致，
 * 避免把"声明了但拉不起来"的组件算进来。
 */
object TranslateAppCapability {
    fun processTextPackages(context: Context): Set<String> =
        handlerPackages(context, Intent.ACTION_PROCESS_TEXT)

    fun sendPackages(context: Context): Set<String> =
        handlerPackages(context, Intent.ACTION_SEND)

    /** 两个通道的并集，用于给用户列"能收文本的 App"。 */
    fun textHandlerPackages(context: Context): Set<String> =
        processTextPackages(context) + sendPackages(context)

    /** Android 11+ 的包可见性：需要 `<queries>` 或 QUERY_ALL_PACKAGES 才看得到别的 App。 */
    private fun handlerPackages(context: Context, action: String): Set<String> =
        runCatching {
            context.packageManager
                .queryIntentActivitiesCompat(
                    Intent(action).setType("text/plain"),
                    PackageManager.MATCH_DEFAULT_ONLY,
                )
                .mapNotNullTo(mutableSetOf()) { it.activityInfo?.packageName }
        }.getOrDefault(emptySet())
}
