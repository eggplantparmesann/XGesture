package com.slideindex.app.translate

import android.content.Context
import android.graphics.drawable.Drawable
import java.util.Locale

/** 「选择翻译 App」列表里的一项。 */
data class TranslateAppTarget(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * 列出"能接收取词文本"的 App：`ACTION_PROCESS_TEXT`（文本直送）与 `ACTION_SEND`（分享）的并集。
 *
 * 取并集而不是只看 `PROCESS_TEXT`：部分 App 只注册了分享入口，反过来也有只做文本直送的；
 * 列表里的每一项，运行时都至少有一条通道能拉起来（见 `TranslateLaunchPlanner`）。
 */
object TranslateAppTargetResolver {
    fun listTargets(context: Context): List<TranslateAppTarget> =
        TranslateAppCapability.textHandlerPackages(context)
            .mapNotNull { packageName -> toTarget(context, packageName) }
            .sortedWith(compareBy({ it.label.lowercase(Locale.getDefault()) }, { it.packageName }))

    fun searchTargets(targets: List<TranslateAppTarget>, query: String): List<TranslateAppTarget> {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) return targets
        return targets.filter {
            it.label.lowercase(Locale.getDefault()).contains(needle) ||
                it.packageName.lowercase(Locale.getDefault()).contains(needle)
        }
    }

    private fun toTarget(context: Context, packageName: String): TranslateAppTarget? {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        return TranslateAppTarget(
            packageName = packageName,
            label = runCatching { pm.getApplicationLabel(info).toString().trim() }
                .getOrDefault(packageName)
                .ifBlank { packageName },
            icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
        )
    }
}
