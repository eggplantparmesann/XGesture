package com.slideindex.app.gesture

import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 触钮等「手势槽位」把动作落在 [GestureRuleCodec] 里，其启动形态后缀必须与快速启动器同源。
 *
 * 回归：`GestureRuleCodec.encode` 曾直接写 `action.payload`，`LaunchWindowMode` 被静默丢弃，
 * 读回时回落 [LaunchWindowMode.FOLLOW_GLOBAL] —— 用户在槽位上选了「小窗」，
 * 实际仍按全局策略（默认全屏）启动。
 */
class GestureRuleCodecLaunchWindowModeTest {

    private fun rule(action: GestureAction): GestureRule = GestureRule.slot(
        side = PanelSide.LEFT,
        trigger = GestureTriggerType.SHORT_SWIPE_IN,
        action = action,
    )

    @Test
    fun encode_decodeAll_preservesLaunchWindowMode() {
        for (mode in LaunchWindowMode.entries) {
            val action = GestureAction.LaunchApp("com.example.app", mode)

            val decoded = GestureRuleCodec.decodeAll(setOf(GestureRuleCodec.encode(rule(action)))).single()

            assertEquals("mode=$mode 应原样往返", action, decoded.action)
        }
    }

    @Test
    fun encode_decode_preservesLaunchWindowMode() {
        val action = GestureAction.LaunchApp("com.example.app", LaunchWindowMode.ALWAYS_FREE_WINDOW)

        val decoded = GestureRuleCodec.decode(GestureRuleCodec.encode(rule(action)))

        assertEquals(action, decoded?.action)
    }

    @Test
    fun encode_followGlobal_omitsModeSuffix() {
        val encoded = GestureRuleCodec.encode(rule(GestureAction.LaunchApp("com.example.app")))

        assertTrue(
            "跟随全局不应写后缀，旧库读法要能原样识别",
            QuickLauncherItemCodec.LAUNCH_WINDOW_MODE_SEP !in encoded,
        )
    }

    @Test
    fun decode_legacyEncodedRule_withoutModeSuffix_followsGlobal() {
        // 修复前落库的形态：动作字段只有包名。
        val legacy = listOf(
            "slot-left-default-0",
            PanelSide.LEFT.ordinal.toString(),
            TriggerHandle.DEFAULT_ID,
            GestureTriggerType.SHORT_SWIPE_IN.id.toString(),
            GestureActionType.LAUNCH_APP.id.toString(),
            "com.example.app",
            "0",
            "1",
            GestureTriggerMode.DEFAULT.id.toString(),
        ).joinToString("\u001F")

        val decoded = GestureRuleCodec.decode(legacy)

        assertEquals(
            GestureAction.LaunchApp("com.example.app", LaunchWindowMode.FOLLOW_GLOBAL),
            decoded?.action,
        )
    }

    @Test
    fun decode_legacyEightFieldRule_preservesLaunchWindowMode() {
        // 无 handleId 的旧格式（8 段）也要走同一套正文解析。
        val legacy = listOf(
            "slot-left-0",
            PanelSide.LEFT.ordinal.toString(),
            GestureTriggerType.SHORT_SWIPE_IN.id.toString(),
            GestureActionType.LAUNCH_APP.id.toString(),
            "com.example.app${QuickLauncherItemCodec.LAUNCH_WINDOW_MODE_SEP}" +
                LaunchWindowMode.ALWAYS_FREE_WINDOW.id,
            "0",
            "1",
            GestureTriggerMode.DEFAULT.id.toString(),
        ).joinToString("\u001F")

        val decoded = GestureRuleCodec.decode(legacy)

        assertEquals(
            GestureAction.LaunchApp("com.example.app", LaunchWindowMode.ALWAYS_FREE_WINDOW),
            decoded?.action,
        )
    }
}
