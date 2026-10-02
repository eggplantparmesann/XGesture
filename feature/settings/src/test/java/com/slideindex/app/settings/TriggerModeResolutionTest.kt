package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 单击 / 双击固定按「松手触发」解析，不跟随槽位存储模式与侧边「默认触发模式」。
 *
 * 回归背景（真实工单）：用户把侧边默认设成「即时触发」后双击彻底失效——
 * - `GestureSession.onTouchUp` 在 IMMEDIATE 分支提前 return，早于双击判定，双击永远不可达；
 * - 单击的「点击穿透」也被 `dispatchMoveTimeGesture` 静默丢弃（不派发、不震动，还把这一击吃掉）。
 *
 * 长按类有意不在此列：IMMEDIATE 下长按在阈值处就地触发，行为可用。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TriggerModeResolutionTest {

    private fun settingsWithSideDefault(side: PanelSide, mode: GestureTriggerMode): AppSettings =
        AppSettings().withDefaultTriggerModeSynced(side, mode, TriggerHandle.DEFAULT_ID)

    @Test
    fun tapTriggers_stayOnRelease_whenSideDefaultIsImmediate() {
        val settings = settingsWithSideDefault(PanelSide.LEFT, GestureTriggerMode.IMMEDIATE)

        assertEquals(
            GestureTriggerMode.ON_RELEASE,
            settings.resolvedTriggerMode(PanelSide.LEFT, GestureTriggerType.SHORT_SINGLE_TAP),
        )
        assertEquals(
            GestureTriggerMode.ON_RELEASE,
            settings.resolvedTriggerMode(PanelSide.LEFT, GestureTriggerType.SHORT_DOUBLE_TAP),
        )
    }

    @Test
    fun swipeAndLongPress_stillFollowSideDefault() {
        val settings = settingsWithSideDefault(PanelSide.LEFT, GestureTriggerMode.IMMEDIATE)

        assertEquals(
            GestureTriggerMode.IMMEDIATE,
            settings.resolvedTriggerMode(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
        assertEquals(
            GestureTriggerMode.IMMEDIATE,
            settings.resolvedTriggerMode(PanelSide.LEFT, GestureTriggerType.SHORT_LONG_PRESS),
        )
    }

    @Test
    fun tapTriggers_ignoreStoredSlotMode() {
        val settings = settingsWithSideDefault(PanelSide.LEFT, GestureTriggerMode.ON_RELEASE)
            .withSlotTriggerMode(
                side = PanelSide.LEFT,
                trigger = GestureTriggerType.SHORT_SINGLE_TAP,
                triggerMode = GestureTriggerMode.CONTINUOUS,
            )

        assertEquals(
            GestureTriggerMode.ON_RELEASE,
            settings.resolvedTriggerMode(PanelSide.LEFT, GestureTriggerType.SHORT_SINGLE_TAP),
        )
    }
}
