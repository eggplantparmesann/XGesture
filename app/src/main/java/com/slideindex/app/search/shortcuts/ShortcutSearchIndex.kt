package com.slideindex.app.search.shortcuts

import android.content.Context
import android.util.Log
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.ShortcutDisplayRules
import com.slideindex.app.util.ShortcutKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 搜索面板「应用快捷方式」索引。
 *
 * 数据源与 App 内的快捷方式选择器（任务切换菜单 / 快捷启动器）完全一致：[AppShortcutLoader]
 * 解析各 App 清单中的 `android.app.shortcuts`（静态快捷方式），免权限、带 5 分钟缓存。
 *
 * 动态 / 固定快捷方式（微信会话、浏览器标签等）只有 Shizuku / Root 才能枚举
 * （见 [com.slideindex.app.util.TaskManagerUtil.loadCategorizedSystemShortcutMap]，走整机 dumpsys），
 * 代价不适合在搜索面板呼出时同步执行，故本索引不包含。
 */
object ShortcutSearchIndex {
    private const val TAG = "ShortcutSearchIndex"

    private val mutex = Mutex()

    @Volatile
    private var cached: List<ShortcutSearchEntry>? = null

    @Volatile
    private var cachedAppsKey: Int = 0

    fun invalidate() {
        cached = null
    }

    suspend fun ensureLoaded(
        context: Context,
        apps: List<AppInfo>,
    ): List<ShortcutSearchEntry> {
        val appsKey = appsKey(apps)
        cached?.takeIf { cachedAppsKey == appsKey }?.let { return it }
        return mutex.withLock {
            cached?.takeIf { cachedAppsKey == appsKey } ?: withContext(Dispatchers.IO) {
                load(context.applicationContext, apps)
            }.also {
                cached = it
                cachedAppsKey = appsKey
            }
        }
    }

    suspend fun search(
        context: Context,
        apps: List<AppInfo>,
        query: String,
        limit: Int,
    ): List<ShortcutSearchEntry> =
        ShortcutSearchMatcher.search(ensureLoaded(context, apps), query, limit)

    private fun appsKey(apps: List<AppInfo>): Int = apps.map { it.packageName }.sorted().hashCode()

    private fun load(context: Context, apps: List<AppInfo>): List<ShortcutSearchEntry> {
        val catalog = runCatching {
            AppShortcutLoader.loadShortcutCatalog(context, apps)
        }.onFailure { error ->
            Log.w(TAG, "loadShortcutCatalog failed: ${error.message}")
        }.getOrNull() ?: return emptyList()

        val entries = mutableListOf<ShortcutSearchEntry>()
        catalog.groups.forEach { group ->
            group.shortcuts.forEach { item ->
                if (item.type != TaskSwitcherMenuItemType.SHORTCUT) return@forEach
                val label = item.label.trim()
                if (label.isEmpty()) return@forEach
                val shortcutId = item.shortcutId?.trim().orEmpty().ifEmpty { label }
                if (!ShortcutDisplayRules.isDisplayable(shortcutId, label)) return@forEach
                entries += ShortcutSearchEntry(
                    packageName = group.app.packageName,
                    appLabel = group.app.label,
                    label = label,
                    shortcutId = shortcutId,
                    intentUris = item.intentUris.orEmpty(),
                    kind = item.kind ?: ShortcutKind.STATIC,
                )
            }
        }
        return entries.distinctBy { it.dedupeKey }
    }
}
