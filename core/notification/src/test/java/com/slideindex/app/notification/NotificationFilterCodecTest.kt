package com.slideindex.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NotificationFilterCodecTest {

    @Test
    fun encodeDecode_roundTrip_preservesV2Rule() {
        val original = listOf(
            NotificationFilterRule(
                id = "rule-abc",
                name = "Hide OTP",
                enabled = true,
                userCreated = true,
                createdAtMs = 1_700_000_000_000L,
                channelId = "sms",
                appMode = AppMatchMode.INCLUDE,
                appTargets = listOf(AppTarget("com.bank.app", userId = 0)),
                textMode = TextMatchMode.CONTAIN_ANY,
                keywords = listOf("验证码", "OTP"),
                keywordsExclude = listOf("广告"),
                regex = null,
                ignoreCase = true,
                timeStartMs = 8 * 60 * 60 * 1000,
                timeEndMs = 22 * 60 * 60 * 1000,
                weekDays = setOf(1, 2, 3, 4, 5),
                screenMode = ScreenMode.ANY,
                chargeMask = NotificationRuleChargeMask.UNRESTRICTED,
                actionEntries = listOf(
                    RuleActionEntry(
                        type = NotificationRuleActionType.HIDE,
                        ttsTemplate = "收到 {{title}}",
                    ),
                ),
            ),
        )

        val decoded = NotificationFilterCodec.decode(NotificationFilterCodec.encode(original))

        assertEquals(1, decoded.size)
        val rule = decoded.first().normalized()
        assertEquals("rule-abc", rule.id)
        assertEquals("Hide OTP", rule.name)
        assertEquals("sms", rule.channelId)
        assertEquals(AppMatchMode.INCLUDE, rule.appMode)
        assertEquals(listOf(AppTarget("com.bank.app", 0)), rule.appTargets)
        assertEquals(TextMatchMode.CONTAIN_ANY, rule.textMode)
        assertEquals(listOf("验证码", "OTP"), rule.keywords)
        assertEquals(listOf("广告"), rule.keywordsExclude)
        assertEquals(NotificationRuleActionType.HIDE, rule.actionEntries.first().type)
        assertEquals("收到 {{title}}", rule.actionEntries.first().ttsTemplate)
        assertEquals(ScreenMode.ANY, rule.screenMode)
        assertEquals(NotificationRuleChargeMask.UNRESTRICTED, rule.chargeMask)
    }

    @Test
    fun decode_legacyBothScreenMode_readsAsUnrestricted() {
        val decoded = NotificationFilterCodec.decode(v2Rule(""""screenMode":"BOTH""""))

        assertEquals(ScreenMode.ANY, decoded.single().screenMode)
    }

    @Test
    fun decode_unknownScreenMode_readsAsUnrestricted() {
        val decoded = NotificationFilterCodec.decode(v2Rule(""""screenMode":"SOMETHING_ELSE""""))

        assertEquals(ScreenMode.ANY, decoded.single().screenMode)
    }

    @Test
    fun decode_chargeMask_keepsSelectedModesAndCanonicalizesUnrestricted() {
        // 0 与三位全选（历史版本表示「不限制」的写法）都收敛到不限制；勾选结果原样保留。
        assertEquals(
            NotificationRuleChargeMask.UNRESTRICTED,
            NotificationFilterCodec.decode(v2Rule(""""chargeMask":15""")).single().chargeMask,
        )
        assertEquals(
            NotificationRuleChargeMask.UNRESTRICTED,
            NotificationFilterCodec.decode(v2Rule(""""chargeMask":0""")).single().chargeMask,
        )
        assertEquals(
            NotificationRuleChargeMask.BATTERY,
            NotificationFilterCodec.decode(v2Rule(""""chargeMask":1""")).single().chargeMask,
        )
        assertEquals(
            NotificationRuleChargeMask.WIRED,
            NotificationFilterCodec.decode(v2Rule(""""chargeMask":6""")).single().chargeMask,
        )
    }

    @Test
    fun encode_writesUnrestrictedScreenModeAndChargeMask() {
        val encoded = NotificationFilterCodec.encode(listOf(NotificationFilterRule()))

        assertTrue(encoded.contains("\"screenMode\":\"ANY\""))
        assertTrue(encoded.contains("\"chargeMask\":${NotificationRuleChargeMask.UNRESTRICTED}"))
    }

    /** v2 信封（顶层数组会被当成 v1 旧格式，条件字段根本不会被读取）。 */
    private fun v2Rule(fields: String): String =
        """{"schemaVersion":2,"rules":[{"id":"rule-1","userCreated":true,$fields}]}"""

    @Test
    fun decode_blank_returnsEmpty() {
        assertTrue(NotificationFilterCodec.decode("").isEmpty())
        assertTrue(NotificationFilterCodec.decode("   ").isEmpty())
    }

    @Test
    fun decode_legacyArray_normalizesToV2Fields() {
        val legacy = """
            [
              {
                "id": "legacy-1",
                "name": "Legacy",
                "packageName": "com.legacy.app",
                "titlePattern": "验证码",
                "enabled": true,
                "actions": ["HIDE"]
              }
            ]
        """.trimIndent()

        val decoded = NotificationFilterCodec.decode(legacy)
        assertEquals(1, decoded.size)
        val rule = decoded.first().normalized()
        assertEquals(AppMatchMode.INCLUDE, rule.appMode)
        assertEquals(setOf("com.legacy.app"), rule.packageNames())
        assertEquals(TextMatchMode.CONTAIN_ANY, rule.textMode)
        assertTrue(rule.keywords.contains("验证码"))
    }
}
