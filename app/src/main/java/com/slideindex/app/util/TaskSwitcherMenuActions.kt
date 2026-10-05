package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.R

object TaskSwitcherMenuActions {
    private const val TAG = "TaskSwitcherMenuActions"

    fun buildMenuItems(context: Context): List<TaskSwitcherMenuItem> {
        return listOf(
            TaskSwitcherMenuItem(
                label = context.getString(R.string.task_switcher_menu_free_window),
                type = TaskSwitcherMenuItemType.FREE_WINDOW,
            ),
            TaskSwitcherMenuItem(
                label = context.getString(R.string.task_switcher_menu_app_info),
                type = TaskSwitcherMenuItemType.APP_INFO,
            ),
            TaskSwitcherMenuItem(
                label = context.getString(R.string.task_switcher_menu_force_stop),
                type = TaskSwitcherMenuItemType.FORCE_STOP,
            ),
        )
    }

    fun execute(
        context: Context,
        item: TaskSwitcherMenuItem,
        packageName: String,
        settings: AppSettings,
        onSessionEnd: (() -> Unit)? = null,
    ) {
        when (item.type) {
            TaskSwitcherMenuItemType.SHORTCUT -> {
                AppShortcutLoader.launchShortcut(context, packageName, item)
            }
            TaskSwitcherMenuItemType.FREE_WINDOW -> {
                launchFreeWindow(
                    context,
                    packageName,
                    settings,
                    app = null,
                    onSessionEnd = onSessionEnd,
                )
            }
            TaskSwitcherMenuItemType.APP_INFO -> {
                openAppInfo(context, packageName)
            }
            TaskSwitcherMenuItemType.FORCE_STOP -> {
                Thread {
                    TaskManagerUtil.forceStopPackage(packageName)
                }.start()
            }
        }
    }

    /**
     * 小窗打开：照搬 SideGesture「应用小窗(7.0+)」——解析该包 launcher Activity，
     * 一次 `startActivity` + ActivityOptions；不搬移已有任务，不校验、不重试。
     */
    fun launchFreeWindow(
        context: Context,
        packageName: String,
        settings: AppSettings,
        app: AppInfo? = null,
        onSessionEnd: (() -> Unit)? = null,
    ) {
        val effective = settings.copy(freeWindow = settings.freeWindow.copy(freeWindowEnabled = true))
        FreeWindowLauncher.launchPackageInFreeWindow(
            context = context,
            packageName = app?.packageName ?: packageName,
            settings = effective,
        )
        onSessionEnd?.invoke()
    }

    private fun openAppInfo(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        }.onFailure { error ->
            Log.e(TAG, "openAppInfo($packageName) failed", error)
        }
    }
}
