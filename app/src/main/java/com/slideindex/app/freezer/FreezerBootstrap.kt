package com.slideindex.app.freezer

/**
 * Portions derived from EdgeX (https://github.com/oxohang/EdgeX)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object FreezerBootstrap {
    /**
     * 扫描「已停用 **或** 已挂起」的桌面应用：两种状态都可能来自别的冻结 / 暂停工具（或本应用的旧数据），
     * 需要导入冰箱列表统一管理。
     */
    fun scanImportableLauncherPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DISABLED_COMPONENTS)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@filter false
                isImportableState(
                    enabled = info.enabled,
                    suspended = (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
                )
            }
            .toSet()
    }

    /** 已停用（冻结）或已挂起（暂停）都值得导入；正常使用的应用不需要。 */
    fun isImportableState(enabled: Boolean, suspended: Boolean): Boolean = !enabled || suspended

    fun importablePackages(scanned: Set<String>, excluded: Set<String>): Set<String> =
        scanned - excluded
}
