package com.slideindex.app.freezer

/**
 * Portions derived from EdgeX (https://github.com/oxohang/EdgeX)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreezerOperations {
    fun hasShellAccess(): Boolean = TaskManagerUtil.hasPrivilegedAccess()

    /** 一次查询出三态；Compose 组合期逐项调用，不要拆成多次包管理查询。 */
    fun stateOf(context: Context, packageName: String): FreezerAppState =
        FreezerPrivilegedOps.appState(context, packageName)

    fun isFrozen(context: Context, packageName: String): Boolean =
        FreezerPrivilegedOps.isAppDisabled(context, packageName)

    fun isPaused(context: Context, packageName: String): Boolean =
        FreezerPrivilegedOps.isAppSuspended(context, packageName)

    /** 本应用自身、system、SystemUI、当前桌面等「动了就回不来」的包。 */
    fun isProtectedPackage(context: Context, packageName: String): Boolean =
        FreezerPrivilegedOps.isProtectedPackage(context, packageName)

    suspend fun setFrozen(context: Context, packageName: String, frozen: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!TaskManagerUtil.hasPrivilegedAccess()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
            if (frozen && FreezerPrivilegedOps.isProtectedPackage(context, packageName)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.freezer_protected_package, Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
            if (frozen && isPaused(context, packageName)) {
                // 停用会让桌面图标消失，挂起态在它面前「看不见」：先取消暂停再停用，
                // 否则之后解冻出来仍是挂起态，用户会以为解冻失败。
                FreezerPrivilegedOps.setAppSuspended(context, packageName, suspended = false, dialogMessage = null)
            }
            val (success, detail) = FreezerPrivilegedOps.setAppDisabled(context, packageName, frozen)
            if (success) return@withContext true
            withContext(Dispatchers.Main) {
                val message = when (detail) {
                    FreezerPrivilegedOps.NEED_ROOT_FOR_SYSTEM_DISABLE ->
                        context.getString(R.string.freezer_unfreeze_need_root)
                    else -> {
                        val messageRes = if (frozen) {
                            R.string.freezer_freeze_failed
                        } else {
                            R.string.freezer_unfreeze_failed
                        }
                        detail.take(160).ifBlank { null }?.let {
                            context.getString(messageRes, it)
                        } ?: context.getString(R.string.freezer_permission_required)
                    }
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            false
        }

    /**
     * 暂停（应用挂起）：应用仍安装，桌面图标保留但灰化、点击弹系统对话框。
     * 与冻结的差别是「图标不消失」，代价是挂起只拦启动、不拦后台。
     */
    suspend fun setPaused(context: Context, packageName: String, paused: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!TaskManagerUtil.hasPrivilegedAccess()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
            if (paused) {
                if (FreezerPrivilegedOps.isProtectedPackage(context, packageName)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.freezer_protected_package, Toast.LENGTH_SHORT).show()
                    }
                    return@withContext false
                }
                if (isFrozen(context, packageName)) {
                    // 挂起本身不要求 enabled，但停用的应用图标已经消失，暂停没有意义：先解冻。
                    if (!setFrozen(context, packageName, frozen = false)) return@withContext false
                }
            }
            val dialogMessage = if (paused) {
                context.getString(
                    R.string.freezer_pause_dialog_message,
                    context.getString(R.string.app_name),
                )
            } else {
                null
            }
            val (success, detail) =
                FreezerPrivilegedOps.setAppSuspended(context, packageName, paused, dialogMessage)
            if (success) return@withContext true
            withContext(Dispatchers.Main) {
                val messageRes = if (paused) {
                    R.string.freezer_pause_failed
                } else {
                    R.string.freezer_unpause_failed
                }
                val message = detail.take(160).ifBlank { null }?.let {
                    context.getString(messageRes, it)
                } ?: context.getString(R.string.freezer_permission_required)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            false
        }

    /** 点击冰箱里的应用：按当前状态恢复（解冻 / 取消暂停）后再启动。 */
    suspend fun launchAndRestore(
        context: Context,
        appRepository: AppRepository,
        settings: AppSettings,
        app: AppInfo,
        fullscreen: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        when (stateOf(context, app.packageName)) {
            FreezerAppState.FROZEN -> if (!setFrozen(context, app.packageName, frozen = false)) {
                return@withContext false
            }
            FreezerAppState.PAUSED -> if (!setPaused(context, app.packageName, paused = false)) {
                return@withContext false
            }
            FreezerAppState.ACTIVE -> Unit
        }
        withContext(Dispatchers.Main) {
            launchApp(context, app, settings, appRepository, fullscreen)
        }
    }

    private fun launchApp(
        context: Context,
        app: AppInfo,
        settings: AppSettings,
        appRepository: AppRepository,
        fullscreen: Boolean
    ): Boolean {
        val effectiveSettings = if (!fullscreen) {
            settings.copy(freeWindow = settings.freeWindow.copy(freeWindowEnabled = true))
        } else {
            settings
        }
        if (appRepository.launchApp(app, effectiveSettings, fullscreen = fullscreen)) return true
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(app.packageName)
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, flags)
        }.firstOrNull() ?: return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply {
            setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 重新冻结：只处理「使用中」的成员，已暂停的保持暂停（不把它变成图标消失的冻结）。 */
    suspend fun refreezeAll(context: Context, packages: Set<String>): Int =
        freezeAll(context, packages)

    suspend fun freezeAll(context: Context, packages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (stateOf(context, pkg).isActive && setFrozen(context, pkg, frozen = true)) count++
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.freezer_refreeze_done, count, count),
                Toast.LENGTH_SHORT,
            ).show()
        }
        count
    }

    suspend fun pauseAll(context: Context, packages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (stateOf(context, pkg).isActive && setPaused(context, pkg, paused = true)) count++
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.freezer_pause_all_done, count, count),
                Toast.LENGTH_SHORT,
            ).show()
        }
        count
    }

    suspend fun unpauseAll(context: Context, packages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (stateOf(context, pkg).isPaused && setPaused(context, pkg, paused = false)) count++
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.freezer_unpause_all_done, count, count),
                Toast.LENGTH_SHORT,
            ).show()
        }
        count
    }

    suspend fun unfreezeAll(context: Context, packages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (stateOf(context, pkg).isFrozen && setFrozen(context, pkg, frozen = false)) count++
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.freezer_unfreeze_all_done, count, count),
                Toast.LENGTH_SHORT,
            ).show()
        }
        count
    }
}
