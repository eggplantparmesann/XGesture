package com.slideindex.app.shizuku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TaskManagerFreeWindowOperationsTest {

    @Test
    fun moveTaskToFreeWindow_neverRelaunchesTaskWithClearTop() {
        val shell = RecordingTaskShellPort()
        // The task really reports a free window mode, so the first strategy is accepted.
        val operations = operations(shell = shell, status = FreeWindowMoveStatus.MOVED)

        assertTrue(operations.moveTaskToFreeWindow("77", 100, 0, 0, 300, 400))

        assertFalse(
            "relaunching the task clears the pages above the launch entry",
            shell.commands.any { it.firstOrNull() == "am" },
        )
        assertFalse(
            "clear-top drops the user back to the app's main page",
            shell.commands.flatten().any { it.contains("clear-top") },
        )
    }

    @Test
    fun moveTaskToFreeWindow_reportsFailureWhenTaskStayedFullscreen() {
        val operations = operations(
            shell = RecordingTaskShellPort(),
            status = FreeWindowMoveStatus.NOT_MOVED,
        )

        assertFalse(operations.moveTaskToFreeWindow("77", 100, 0, 0, 300, 400))
    }

    @Test
    fun moveTaskToFreeWindow_rejectsInvalidTaskId() {
        val operations = operations(
            shell = RecordingTaskShellPort(),
            status = FreeWindowMoveStatus.MOVED,
        )

        assertFalse(operations.moveTaskToFreeWindow(null, 100, 0, 0, 300, 400))
        assertFalse(operations.moveTaskToFreeWindow("0", 100, 0, 0, 300, 400))
        assertFalse(operations.moveTaskToFreeWindow("not-a-task", 100, 0, 0, 300, 400))
    }

    @Test
    fun moveTaskToFreeWindow_trustsStrategyWhenWindowingModeUnreadable() {
        val shell = RecordingTaskShellPort()
        val operations = operations(shell = shell, status = FreeWindowMoveStatus.UNKNOWN)

        assertTrue(operations.moveTaskToFreeWindow("77", 100, 0, 0, 300, 400))
        assertTrue(
            "the shell resize strategy is the readable fallback on an opaque platform",
            shell.commands.any { it.firstOrNull() == "cmd" },
        )
    }

    private fun operations(
        shell: RecordingTaskShellPort,
        status: FreeWindowMoveStatus,
    ): TaskManagerFreeWindowOperations {
        val tasks = TaskManagerTaskOperations(
            shell = ShellCommandRunner { true },
            recents = FreeWindowRecentsReader(),
        )
        return TaskManagerFreeWindowOperations(
            shell = shell,
            tasks = tasks,
            verifier = { status },
        )
    }

    private class FreeWindowRecentsReader : RecentsReader {
        override fun listTasks(): List<SystemRecentsAccess.Task> = emptyList()

        override fun removeTask(taskId: Int): Boolean = false

        override fun frontTask(): SystemRecentsAccess.Task =
            SystemRecentsAccess.Task(
                taskId = 77,
                packageName = "com.example.app",
                component = "com.example.app/.MainActivity",
                title = null,
            )

        override fun findTaskId(identifier: String): Int? = null

        override fun switchToTask(taskId: Int): Boolean = false

        override fun matchesPackage(task: SystemRecentsAccess.Task, packageName: String): Boolean =
            task.packageName == packageName
    }

    private class RecordingTaskShellPort : TaskShellPort {
        val commands = mutableListOf<List<String>>()

        override fun shellCommand(vararg cmd: String): Boolean {
            commands += cmd.toList()
            return true
        }

        override fun shellOutput(vararg cmd: String): String = ""
    }
}
