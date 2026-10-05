package com.slideindex.app.shizuku

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.util.Log

/**
 * Moves an existing task into a free window.
 *
 * Every strategy is verified against the task's real windowingMode before it is
 * reported as successful: [shellCommand] only reports the process exit code, so an
 * unverified strategy could look successful while the task stayed fullscreen.
 *
 * There is deliberately no "am start --activity-clear-top" fallback: relaunching the
 * task that way clears the activities above the launch entry and drops the user back
 * to the app's main page.
 */
internal class TaskManagerFreeWindowOperations(
    private val shell: TaskShellPort = DefaultTaskShellPort,
    private val tasks: TaskManagerTaskOperations = TaskManagerTaskOperations(),
    private val verifier: (Int) -> FreeWindowMoveStatus = { FreeWindowMoveStatus.UNKNOWN },
) {

    fun moveTaskToFreeWindow(
        taskIdStr: String?,
        windowingMode: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Boolean {
        return try {
            val taskId = taskIdStr?.toIntOrNull()?.takeIf { it > 0 } ?: return false
            val bounds = Rect(left, top, right, bottom)
            val frontTaskId = tasks.readFrontTask()?.taskId
            val isFrontTask = frontTaskId == taskId
            Log.i(
                TAG,
                "moveTaskToFreeWindow taskId=$taskId frontTaskId=$frontTaskId isFront=$isFrontTask mode=$windowingMode bounds=$bounds"
            )
            if (isFrontTask) {
                moveFrontTaskToFreeWindow(taskId, windowingMode, bounds)
            } else {
                moveBackgroundTaskToFreeWindow(taskId, windowingMode, bounds)
            }
        } catch (error: Exception) {
            Log.e(TAG, "moveTaskToFreeWindow failed", error)
            false
        }
    }

    private fun moveFrontTaskToFreeWindow(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        for (mode in candidateWindowingModes(windowingMode)) {
            if (applyAndVerify(taskId, mode, bounds, ::moveTaskToFreeWindowViaRecents)) {
                Log.i(TAG, "moveFrontTaskToFreeWindow($taskId) via recents mode=$mode succeeded")
                return true
            }
            if (applyAndVerify(taskId, mode, bounds, ::moveTaskToFreeWindowViaSystemApi)) {
                Log.i(TAG, "moveFrontTaskToFreeWindow($taskId) via ATM mode=$mode succeeded")
                return true
            }
        }
        if (applyAndVerify(taskId, windowingMode, bounds, ::moveTaskToFreeWindowViaShell)) {
            Log.i(TAG, "moveFrontTaskToFreeWindow($taskId) via shell resize succeeded")
            return true
        }
        Log.w(TAG, "moveFrontTaskToFreeWindow($taskId) failed; task stayed out of free window")
        return false
    }

    private fun moveBackgroundTaskToFreeWindow(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        for (mode in candidateWindowingModes(windowingMode)) {
            if (applyAndVerify(taskId, mode, bounds, ::focusTaskViaRecents)) {
                Log.i(TAG, "moveBackgroundTaskToFreeWindow($taskId) via moveTaskToFront mode=$mode succeeded")
                return true
            }
        }
        if (moveFrontTaskToFreeWindow(taskId, windowingMode, bounds)) {
            Log.i(TAG, "moveBackgroundTaskToFreeWindow($taskId) via front-task strategies succeeded")
            return true
        }
        for (mode in candidateWindowingModes(windowingMode)) {
            if (applyAndVerify(taskId, mode, bounds, ::moveTaskToFreeWindowViaSystemApi)) {
                Log.i(TAG, "moveBackgroundTaskToFreeWindow($taskId) via ATM mode=$mode succeeded")
                return true
            }
        }
        if (applyAndVerify(taskId, windowingMode, bounds, ::moveTaskToFreeWindowViaShell)) {
            Log.i(TAG, "moveBackgroundTaskToFreeWindow($taskId) via shell resize succeeded")
            return true
        }
        Log.w(TAG, "moveBackgroundTaskToFreeWindow($taskId) failed; task stayed out of free window")
        return false
    }

    /**
     * Runs one strategy and accepts it only when the task really reports a free window
     * windowingMode afterwards. A strategy that reports failure is never accepted, and the
     * strategy's own result is only trusted when the windowingMode could not be read at all.
     */
    private fun applyAndVerify(
        taskId: Int,
        windowingMode: Int,
        bounds: Rect,
        strategy: (Int, Int, Rect) -> Boolean,
    ): Boolean {
        val applied = try {
            strategy(taskId, windowingMode, bounds)
        } catch (error: Exception) {
            Log.w(TAG, "free window strategy failed taskId=$taskId mode=$windowingMode", error)
            false
        }
        return when (verifier(taskId)) {
            FreeWindowMoveStatus.MOVED -> true
            FreeWindowMoveStatus.UNKNOWN -> {
                if (applied) {
                    Log.i(TAG, "task $taskId windowingMode unreadable; trusting strategy result")
                }
                applied
            }
            FreeWindowMoveStatus.NOT_MOVED -> {
                Log.w(TAG, "task $taskId did not enter free window mode $windowingMode")
                false
            }
        }
    }

    private fun candidateWindowingModes(primary: Int): IntArray =
        linkedSetOf(primary, 11, 5, 100, 102, 106).toIntArray()

    /** Brings the task to the front with the free window launch options applied. */
    private fun moveTaskToFreeWindowViaRecents(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return try {
            invokeStartActivityFromRecents(taskId, launchOptionsBundle(windowingMode, bounds))
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFreeWindowViaRecents failed taskId=$taskId", e)
            false
        }
    }

    /** Focuses the task and applies the free window options on the same call. */
    private fun focusTaskViaRecents(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return try {
            invokeStartActivityFromRecents(
                taskId,
                ActivityOptions.makeBasic().toBundle() ?: Bundle()
            )
        } catch (e: Exception) {
            Log.w(TAG, "focusTaskViaRecents failed taskId=$taskId", e)
            false
        }
    }

    private fun invokeStartActivityFromRecents(taskId: Int, bundle: Bundle): Boolean {
        return try {
            val atm = activityTaskManager()
            val result = SystemReflect.invoke(atm, "startActivityFromRecents", taskId, bundle) as? Number
                ?: return false
            val code = result.toInt()
            code in START_SUCCESS..START_DELIVERED_TO_TOP
        } catch (e: Exception) {
            Log.w(TAG, "invokeStartActivityFromRecents failed taskId=$taskId", e)
            false
        }
    }

    private fun launchOptionsBundle(windowingMode: Int, bounds: Rect): Bundle {
        val options = ActivityOptions.makeBasic()
        applyLaunchWindowingMode(options, windowingMode)
        options.setLaunchBounds(bounds)
        val bundle = options.toBundle() ?: Bundle()
        if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
            bundle.putInt(KEY_WINDOWING_MODE, windowingMode)
        }
        return bundle
    }

    private fun applyLaunchWindowingMode(options: ActivityOptions, mode: Int) {
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, mode)
        } catch (_: Exception) {
        }
    }

    private fun moveTaskToFreeWindowViaSystemApi(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return try {
            val atm = activityTaskManager()
            var windowingApplied = false
            runCatching {
                atm.javaClass.getMethod(
                    "setTaskWindowingMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                ).invoke(atm, taskId, windowingMode, true)
                windowingApplied = true
            }
            runCatching {
                atm.javaClass.getMethod(
                    "resizeTask",
                    Int::class.javaPrimitiveType,
                    Rect::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(atm, taskId, bounds, RESIZE_MODE_SYSTEM)
            }
            val bundle = launchOptionsBundle(windowingMode, bounds)
            val movedToFront = runCatching {
                atm.javaClass.getMethod(
                    "moveTaskToFront",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Bundle::class.java
                ).invoke(atm, taskId, 0, bundle) as? Boolean
            }.getOrNull() == true
            windowingApplied || movedToFront
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFreeWindowViaSystemApi failed taskId=$taskId", e)
            false
        }
    }

    private fun moveTaskToFreeWindowViaShell(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return shell.shellCommand(
            "cmd", "activity", "task", "resize",
            taskId.toString(),
            bounds.left.toString(),
            bounds.top.toString(),
            bounds.right.toString(),
            bounds.bottom.toString()
        )
    }

    @SuppressLint("PrivateApi") // IActivityTaskManager for freeform window ops
    private fun activityTaskManager(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "activity_task") as IBinder
        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IActivityTaskManager is null")
    }

    companion object {
        private const val TAG = "TaskManagerUserService"
        private const val RESIZE_MODE_SYSTEM = 0
        private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
        private const val START_SUCCESS = 0
        private const val START_DELIVERED_TO_TOP = 3
    }
}
