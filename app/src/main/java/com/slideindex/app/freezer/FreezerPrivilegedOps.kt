package com.slideindex.app.freezer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import com.slideindex.app.BuildConfig
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.search.ral.HiddenFrameworkAccess
import com.slideindex.app.util.TaskManagerUtil
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 对齐雹 [HShell.setAppDisabled] / [HShizuku.setAppDisabled]：优先 PM API，其次 shell。
 * 解冻时兼容旧版以 `pm disable`（系统级）冻结的应用，需 Root 或 Shizuku(Root)。
 */
internal object FreezerPrivilegedOps {
    private const val APP_INFO_FLAGS =
        PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS

    fun isAppDisabled(context: Context, packageName: String): Boolean =
        appFlags(context, packageName)?.first?.not() ?: false

    /** 是否处于「已暂停」（应用挂起）状态；公开常量 [ApplicationInfo.FLAG_SUSPENDED]。 */
    fun isAppSuspended(context: Context, packageName: String): Boolean =
        appFlags(context, packageName)?.second ?: false

    /**
     * 一次查询同时得到启用状态与挂起标志。
     * Compose 组合期会对每个网格项调用，不能拆成两次 `getApplicationInfo`。
     */
    fun appState(context: Context, packageName: String): FreezerAppState {
        val flags = appFlags(context, packageName) ?: return FreezerAppState.ACTIVE
        return FreezerAppState.of(enabled = flags.first, suspended = flags.second)
    }

    /**
     * 暂停 / 冻结这些包会让用户「回不来」或没有意义：本应用自身、system、SystemUI、当前桌面。
     * AOSP 对挂起自带的拒绝名单不含输入法与 SystemUI，这里补一层。
     */
    fun isProtectedPackage(context: Context, packageName: String): Boolean {
        if (packageName == BuildConfig.APPLICATION_ID) return true
        if (packageName == ANDROID_PACKAGE || packageName == SYSTEM_UI_PACKAGE) return true
        val homePackage = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, 0)
            }
            resolved?.activityInfo?.packageName
        }.getOrNull()
        return homePackage == packageName
    }

    private fun appFlags(context: Context, packageName: String): Pair<Boolean, Boolean>? = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, APP_INFO_FLAGS)
        info.enabled to ((info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0)
    }.getOrNull()

    fun setAppDisabled(context: Context, packageName: String, disabled: Boolean): Pair<Boolean, String> {
        if (!appExists(context, packageName)) {
            return false to "package not found"
        }
        val shizukuRoot = TaskManagerUtil.probeRootAvailable()
        val useRoot = PrivilegeGateway.isRootMode() ||
            (PrivilegeGateway.isShizukuMode() && shizukuRoot)
        val userId = resolveUserId(useRoot)

        if (disabled) {
            TaskManagerUtil.forceStopPackage(packageName)
            TaskManagerUtil.runShellCommandLine(
                "am force-stop --user $userId $packageName",
                useRoot = useRoot
            )
        }

        val errors = mutableListOf<String>()

        if (TaskManagerUtil.hasShizukuPermission()) {
            when (val result = setViaShizukuPackageManager(context, packageName, disabled, shizukuRoot, userId)) {
                is AttemptResult.Success -> return true to ""
                is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
            }
            if (!disabled) {
                when (val result = setViaShizukuPackageManager(
                    context = context,
                    packageName = packageName,
                    disabled = false,
                    shizukuRoot = shizukuRoot,
                    userId = userId,
                    enabledState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                )) {
                    is AttemptResult.Success -> return true to ""
                    is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
                }
            }
        }

        if (useRoot) {
            when (val result = setViaShell(context, packageName, disabled, userId, useRoot = true)) {
                is AttemptResult.Success -> return true to ""
                is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
            }
        }

        if (PrivilegeGateway.isShizukuMode() && TaskManagerUtil.hasShizukuPermission() && !useRoot) {
            when (val result = setViaShell(context, packageName, disabled, userId, useRoot = false)) {
                is AttemptResult.Success -> return true to ""
                is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
            }
        }

        val detail = errors.lastOrNull().orEmpty()
        if (!disabled && !useRoot && isSystemDisabled(packageName, userId)) {
            return false to NEED_ROOT_FOR_SYSTEM_DISABLE
        }
        return false to detail
    }

    /**
     * 对齐雹 [HShizuku.setAppSuspended]：应用挂起（暂停）后应用仍安装，桌面图标保留但灰化、点击弹系统对话框。
     *
     * 先走 `IPackageManager.setPackagesSuspendedAsUser` 反射（A12/13/14.0/14-QPR1 为 7 参，
     * A14-QPR2/15/16 为 9 参），失败再走 `pm suspend/unsuspend` shell；全程以回读
     * [ApplicationInfo.FLAG_SUSPENDED] 判定结果（PMS 对受保护包的拒绝是静默的）。
     */
    fun setAppSuspended(
        context: Context,
        packageName: String,
        suspended: Boolean,
        dialogMessage: String?
    ): Pair<Boolean, String> {
        if (!appExists(context, packageName)) {
            return false to "package not found"
        }
        val shizukuRoot = TaskManagerUtil.probeRootAvailable()
        val useRoot = PrivilegeGateway.isRootMode() ||
            (PrivilegeGateway.isShizukuMode() && shizukuRoot)
        val userId = resolveUserId(useRoot)

        if (suspended) {
            // 挂起只拦启动、不拦后台（框架不做 force-stop），这里与冻结保持一致先杀进程。
            TaskManagerUtil.forceStopPackage(packageName)
            TaskManagerUtil.runShellCommandLine(
                "am force-stop --user $userId $packageName",
                useRoot = useRoot
            )
        }

        val errors = mutableListOf<String>()

        if (TaskManagerUtil.hasShizukuPermission()) {
            // 经 Shizuku 且能拿到 root 时可用本应用包名记账（卸载本应用时系统会一并清掉该挂起）；
            // 否则只能是 shell 自己的包名。
            val viaShizukuRoot = shizukuRoot
            val keys = if (suspended) {
                listOf(if (viaShizukuRoot) BuildConfig.APPLICATION_ID else SHELL_PACKAGE)
            } else {
                // 挂起按「发起方包名 + user」分别记账：取消暂停时把可能用过的键都清一遍。
                buildList {
                    if (viaShizukuRoot) add(BuildConfig.APPLICATION_ID)
                    add(SHELL_PACKAGE)
                }
            }
            for (key in keys) {
                val result = setViaShizukuSuspended(context, packageName, suspended, key, dialogMessage, userId)
                if (result is AttemptResult.Failure && result.detail.isNotBlank()) errors += result.detail
                if (waitForSuspended(context, packageName, suspended)) return true to ""
            }
        }

        if (useRoot) {
            val result = setViaShellSuspended(context, packageName, suspended, userId, useRoot = true, dialogMessage)
            if (result is AttemptResult.Failure && result.detail.isNotBlank()) errors += result.detail
            if (waitForSuspended(context, packageName, suspended)) return true to ""
        }

        if (PrivilegeGateway.isShizukuMode() && TaskManagerUtil.hasShizukuPermission() && !useRoot) {
            val result = setViaShellSuspended(context, packageName, suspended, userId, useRoot = false, dialogMessage)
            if (result is AttemptResult.Failure && result.detail.isNotBlank()) errors += result.detail
            if (waitForSuspended(context, packageName, suspended)) return true to ""
        }

        return false to errors.lastOrNull().orEmpty()
    }

    private fun setViaShizukuSuspended(
        context: Context,
        packageName: String,
        suspended: Boolean,
        suspendingPackage: String,
        dialogMessage: String?,
        userId: Int
    ): AttemptResult = runCatching {
        val packageManager = bindShizukuPackageManager()
        val method = packageManager.javaClass.methods.firstOrNull {
            it.name == "setPackagesSuspendedAsUser"
        } ?: return@runCatching AttemptResult.Failure("setPackagesSuspendedAsUser not found")
        val dialogInfo = if (suspended) buildSuspendDialogInfo(dialogMessage) else null
        val packages = arrayOf(packageName)
        val args: Array<Any?> = when (method.parameterTypes.size) {
            // A12 / A13 / A14.0 / A14-QPR1
            7 -> arrayOf(packages, suspended, null, null, dialogInfo, suspendingPackage, userId)
            // A14-QPR2 / A15 / A16（flags = 0 表示普通挂起，非隔离）
            9 -> arrayOf(packages, suspended, null, null, dialogInfo, 0, suspendingPackage, userId, userId)
            else -> return@runCatching AttemptResult.Failure(
                "unsupported setPackagesSuspendedAsUser(${method.parameterTypes.size} args)"
            )
        }
        // PMS 返回的是「处理失败的包名数组」。
        val failed = method.invoke(packageManager, *args) as? Array<*> ?: emptyArray<Any?>()
        if (failed.isNotEmpty()) {
            AttemptResult.Failure("suspend rejected: ${failed.joinToString()}")
        } else {
            AttemptResult.Success
        }
    }.getOrElse { error ->
        AttemptResult.Failure(error.message ?: error.javaClass.simpleName)
    }

    private fun setViaShellSuspended(
        context: Context,
        packageName: String,
        suspended: Boolean,
        userId: Int,
        useRoot: Boolean,
        dialogMessage: String?
    ): AttemptResult {
        val verb = if (suspended) "suspend" else "unsuspend"
        val dialogArg = if (suspended && !dialogMessage.isNullOrBlank()) {
            " --dialogMessage ${shellQuote(dialogMessage)}"
        } else {
            ""
        }
        val commands = if (suspended) {
            buildList {
                add("pm $verb --user $userId$dialogArg $packageName")
                if (useRoot) add("pm $verb$dialogArg $packageName")
            }
        } else {
            buildList {
                if (useRoot) add("pm $verb $packageName")
                add("pm $verb --user $userId $packageName")
            }
        }
        var lastOutput = ""
        for (command in commands) {
            val result = TaskManagerUtil.runShellCommandLine(command, useRoot = useRoot)
            lastOutput = result.output.trim().ifBlank { lastOutput }
            if (waitForSuspended(context, packageName, suspended)) {
                return AttemptResult.Success
            }
        }
        return AttemptResult.Failure(lastOutput)
    }

    /**
     * 反射构造隐藏类 `SuspendDialogInfo`：自定义正文 + 中性按钮「取消暂停」，
     * 让用户能自己在系统对话框里恢复（本应用进程已在启动时整表豁免 hidden API）。
     * 构造失败时返回 null —— 系统退化为默认文案且没有自救按钮。
     */
    private fun buildSuspendDialogInfo(message: String?): Any? = runCatching {
        val builderClass = Class.forName("android.content.pm.SuspendDialogInfo\$Builder")
        val builder = builderClass.getDeclaredConstructor().newInstance()
        if (!message.isNullOrBlank()) {
            builderClass.getMethod("setMessage", CharSequence::class.java).invoke(builder, message)
        }
        builderClass.getMethod("setNeutralButtonAction", Int::class.javaPrimitiveType)
            .invoke(builder, NEUTRAL_BUTTON_ACTION_UNSUSPEND)
        builderClass.getMethod("build").invoke(builder)
    }.getOrNull()

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun waitForSuspended(context: Context, packageName: String, suspended: Boolean): Boolean {
        repeat(8) {
            if (isAppSuspended(context, packageName) == suspended) return true
            Thread.sleep(60)
        }
        return isAppSuspended(context, packageName) == suspended
    }

    private fun appExists(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, APP_INFO_FLAGS)
        true
    }.getOrDefault(false)

    private fun resolveUserId(useRoot: Boolean): Int {
        val shellUser = TaskManagerUtil.runShellCommandLine("am get-current-user", useRoot = useRoot)
            .output
            .trim()
            .toIntOrNull()
        if (shellUser != null) return shellUser
        return currentUserId()
    }

    private fun currentUserId(): Int {
        return Process.myUserHandle().hashCode()
    }

    private fun bindShizukuPackageManager(): Any {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        return HiddenFrameworkAccess.bindPackageManager(binder)
    }

    private fun getApplicationEnabledSetting(packageName: String, userId: Int): Int? = runCatching {
        val packageManager = bindShizukuPackageManager()
        packageManager.javaClass.getMethod(
            "getApplicationEnabledSetting",
            String::class.java,
            Int::class.javaPrimitiveType
        ).invoke(packageManager, packageName, userId) as Int
    }.getOrNull()

    private fun isSystemDisabled(packageName: String, userId: Int): Boolean {
        val state = getApplicationEnabledSetting(packageName, userId) ?: return false
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun setViaShizukuPackageManager(
        context: Context,
        packageName: String,
        disabled: Boolean,
        shizukuRoot: Boolean,
        userId: Int,
        enabledState: Int = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    ): AttemptResult {
        return runCatching {
            val packageManager = bindShizukuPackageManager()
            val newState = when {
                !disabled -> enabledState
                shizukuRoot -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            packageManager.javaClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            ).invoke(
                packageManager,
                packageName,
                newState,
                PackageManager.DONT_KILL_APP,
                userId,
                BuildConfig.APPLICATION_ID
            )
            if (waitForState(context, packageName, disabled)) {
                AttemptResult.Success
            } else {
                AttemptResult.Failure("shizuku pm state mismatch")
            }
        }.getOrElse { error ->
            AttemptResult.Failure(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun setViaShell(
        context: Context,
        packageName: String,
        disabled: Boolean,
        userId: Int,
        useRoot: Boolean
    ): AttemptResult {
        val verb = if (disabled) "disable" else "enable"
        val commands = if (disabled) {
            buildList {
                add("pm $verb --user $userId $packageName")
                if (useRoot) add("pm $verb $packageName")
            }
        } else {
            buildList {
                if (useRoot) add("pm $verb $packageName")
                add("pm $verb --user $userId $packageName")
                if (!useRoot) add("pm $verb $packageName")
            }
        }
        var lastOutput = ""
        for (command in commands) {
            val result = TaskManagerUtil.runShellCommandLine(command, useRoot = useRoot)
            lastOutput = result.output.trim().ifBlank { lastOutput }
            if (waitForState(context, packageName, disabled)) {
                return AttemptResult.Success
            }
        }
        return AttemptResult.Failure(lastOutput)
    }

    private fun waitForState(context: Context, packageName: String, disabled: Boolean): Boolean {
        repeat(8) {
            if (isAppDisabled(context, packageName) == disabled) return true
            Thread.sleep(60)
        }
        return isAppDisabled(context, packageName) == disabled
    }

    private sealed interface AttemptResult {
        data object Success : AttemptResult
        data class Failure(val detail: String) : AttemptResult
    }

    const val NEED_ROOT_FOR_SYSTEM_DISABLE = "__need_root_for_system_disable__"

    private const val SHELL_PACKAGE = "com.android.shell"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ANDROID_PACKAGE = "android"

    /** `SuspendDialogInfo.BUTTON_ACTION_UNSUSPEND`。 */
    private const val NEUTRAL_BUTTON_ACTION_UNSUSPEND = 1
}
