package com.slideindex.app.settings

import com.slideindex.app.gesture.LaunchWindowMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AppLaunchWindowModeTest {

    private fun settings(
        windowModeId: Int = AppLaunchPolicy.ALWAYS_FULLSCREEN.id,
        freeWindowEnabled: Boolean = true,
    ) = AppSettings(
        launcher = LauncherSettings(appLaunchPolicyId = windowModeId),
        freeWindow = FreeWindowSettings(freeWindowEnabled = freeWindowEnabled),
    )

    @Test
    fun followGlobal_defersToGlobalPolicy() {
        val settings = settings(windowModeId = AppLaunchPolicy.ALWAYS_FREE_WINDOW.id)

        assertFalse(settings.shouldLaunchFullscreen(LaunchWindowMode.FOLLOW_GLOBAL, longPressTriggered = false))
    }

    @Test
    fun alwaysFullscreen_overridesGlobalFreeWindowPolicy() {
        val settings = settings(windowModeId = AppLaunchPolicy.ALWAYS_FREE_WINDOW.id)

        assertTrue(settings.shouldLaunchFullscreen(LaunchWindowMode.ALWAYS_FULLSCREEN, longPressTriggered = false))
    }

    @Test
    fun alwaysFreeWindow_ignoresGlobalLongPressBranch() {
        val settings = settings(windowModeId = AppLaunchPolicy.FULLSCREEN_LONG_PRESS_FREE_WINDOW.id)

        assertFalse(settings.shouldLaunchFullscreen(LaunchWindowMode.ALWAYS_FREE_WINDOW, longPressTriggered = false))
        assertFalse(settings.shouldLaunchFullscreen(LaunchWindowMode.ALWAYS_FREE_WINDOW, longPressTriggered = true))
    }

    @Test
    fun freeWindowDisabled_fallsBackToFullscreen() {
        val settings = settings(
            windowModeId = AppLaunchPolicy.ALWAYS_FREE_WINDOW.id,
            freeWindowEnabled = false,
        )

        for (mode in LaunchWindowMode.entries) {
            assertTrue(
                "mode=$mode 应在小窗开关关闭时回落到全屏",
                settings.shouldLaunchFullscreen(mode, longPressTriggered = false),
            )
        }
    }
}
