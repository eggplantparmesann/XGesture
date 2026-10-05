package com.slideindex.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the advanced filter (JSON) text mode, which previously had no test coverage at all
 * and silently dropped the node `invert` flag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NotificationAdvancedFilterTest {

    // --- matching via NotificationRuleMatcher (no StatusBarNotification available) ---

    @Test
    fun advanced_titleAndTextNodes_mustBothHold_forAllMatch() {
        val rule = advancedRule(
            json = """
                {"match":"ALL","node":[
                  {"field":"title","regex":"^(微信好友1|微信好友2)$"},
                  {"field":"text","regex":"^(?!.*(你好|hello)).*$"}
                ]}
            """.trimIndent(),
        )

        assertTrue(match(rule, title = "微信好友1", text = "在吗"))
        assertFalse(match(rule, title = "微信好友1", text = "你好"))
        assertFalse(match(rule, title = "张三", text = "在吗"))
    }

    @Test
    fun advanced_textNodeSeesOnlyTextField_notTheCombinedText() {
        val rule = advancedRule(
            json = """{"match":"ALL","node":[{"field":"text","regex":"^(?!.*你好).*$"}]}""",
        )

        // "你好" lives in the title; the text field is unaffected, so the node still holds.
        assertTrue(match(rule, title = "你好", text = "在吗"))
        assertFalse(match(rule, title = "张三", text = "你好"))
    }

    @Test
    fun advanced_invertFlagNegatesSingleNode() {
        val rule = advancedRule(
            json = """{"match":"ALL","node":[{"field":"title","regex":"^李四$","invert":true}]}""",
        )

        assertTrue(match(rule, title = "张三", text = "x"))
        assertFalse(match(rule, title = "李四", text = "x"))
    }

    @Test
    fun advanced_invertAppliesToAbsentField() {
        // A missing field yields "", which never matches, so an inverted node holds.
        val rule = advancedRule(
            json = """{"match":"ALL","node":[{"field":"subText","regex":"屏蔽","invert":true}]}""",
        )

        assertTrue(match(rule, title = "标题", text = "正文"))
    }

    @Test
    fun advanced_matchNone_holdsOnlyWhenEveryNodeFails() {
        val rule = advancedRule(
            json = """
                {"match":"NONE","node":[
                  {"field":"title","regex":"^(微信好友1|微信好友2)$"},
                  {"field":"text","regex":"(你好|hello)"}
                ]}
            """.trimIndent(),
        )

        assertTrue(match(rule, title = "张三", text = "在吗"))
        assertFalse(match(rule, title = "微信好友1", text = "在吗"))
        assertFalse(match(rule, title = "张三", text = "你好"))
    }

    @Test
    fun advanced_matchNone_composesWithInvert() {
        // invert is applied per node before the NONE aggregation.
        val rule = advancedRule(
            json = """{"match":"NONE","node":[{"field":"title","regex":"^张三$","invert":true}]}""",
        )

        assertTrue(match(rule, title = "张三", text = "x"))
        assertFalse(match(rule, title = "李四", text = "x"))
    }

    @Test
    fun advanced_matchAll_requiresEveryNodeToHold() {
        val rule = advancedRule(
            json = """{"match":"ALL","node":[
                  {"field":"title","regex":"^张三$"},
                  {"field":"text","regex":"验证码"}
                ]}""",
        )

        assertTrue(match(rule, title = "张三", text = "验证码 1234"))
        assertFalse(match(rule, title = "张三", text = "在吗"))
        assertFalse(match(rule, title = "李四", text = "验证码 1234"))
    }

    @Test
    fun advanced_matchAny_holdsWhenOneNodeHolds() {
        val rule = advancedRule(
            json = """{"match":"ANY","node":[
                  {"field":"title","regex":"^张三$"},
                  {"field":"text","regex":"验证码"}
                ]}""",
        )

        assertTrue(match(rule, title = "张三", text = "x"))
        assertTrue(match(rule, title = "李四", text = "您的验证码是1"))
        assertFalse(match(rule, title = "李四", text = "x"))
    }

    @Test
    fun advanced_regexIsSubstringBasedUnlessAnchored() {
        val rule = advancedRule(
            json = """{"match":"ALL","node":[{"field":"text","regex":"你好"}]}""",
        )

        assertTrue(match(rule, title = "t", text = "前缀你好后缀"))
    }

    @Test
    fun advanced_emptyRegexAlternativeAlwaysMatches() {
        // Documents the trap behind the reported bug: a trailing empty alternative matches
        // every value, so the node stops constraining anything.
        val alwaysTrue = advancedRule(
            json = """{"match":"NONE","node":[
                  {"field":"title","regex":".*(微信好友1微信好友2|).*"},
                  {"field":"text","regex":".*(你好|hello).*"}
                ]}""",
        )

        // Title node always holds -> NONE can never hold, so the text node never decides.
        assertFalse(match(alwaysTrue, title = "张三", text = "在吗"))
        assertFalse(match(alwaysTrue, title = "微信好友1", text = "在吗"))
    }

    @Test
    fun advanced_nodeWithoutInvertDefaultsToFalse() {
        val rule = advancedRule(
            json = """{"match":"ALL","node":[{"field":"title","regex":"^张三$"}]}""",
        )

        assertTrue(match(rule, title = "张三", text = "x"))
        assertFalse(match(rule, title = "李四", text = "x"))
    }

    @Test
    fun advanced_malformedJson_matchesNothing() {
        val rule = advancedRule(json = """{"match":"ALL","node":[""")

        assertFalse(match(rule, title = "张三", text = "在吗"))
    }

    @Test
    fun advanced_emptyJson_matchesNothing() {
        val rule = advancedRule(json = "   ")

        assertFalse(match(rule, title = "张三", text = "在吗"))
    }

    // --- validator ---

    @Test
    fun parser_acceptsDocumentAndKeepsInvert() {
        val parsed = NotificationAdvancedFilterJsonParser.parse(
            """{"match":"none","node":[{"field":"text","regex":"x","invert":true}]}""",
        )

        val filter = (parsed as NotificationAdvancedFilterValidation.Valid).filter
        assertEquals("NONE", filter.matchType)
        assertEquals(1, filter.nodes.size)
        assertTrue(filter.nodes.first().invert)
        assertEquals(NotificationRuleFieldNames.TEXT, filter.nodes.first().field)
    }

    @Test
    fun parser_defaultsMatchTypeToAnyWhenAbsent() {
        val parsed = NotificationAdvancedFilterJsonParser.parse(
            """{"node":[{"field":"title","regex":"x"}]}""",
        )

        val filter = (parsed as NotificationAdvancedFilterValidation.Valid).filter
        assertEquals(NotificationRuleAdvancedMatchTypes.ANY, filter.matchType)
    }

    @Test
    fun parser_acceptsLegacyAllMatchType() {
        // Saved rules predating the validator use "ALL"; rejecting it would silently disable them.
        val parsed = NotificationAdvancedFilterJsonParser.parse(
            """{"match":"ALL","node":[{"field":"title","regex":"x"}]}""",
        )

        val filter = (parsed as NotificationAdvancedFilterValidation.Valid).filter
        assertEquals(NotificationRuleAdvancedMatchTypes.ALL, filter.matchType)
    }

    @Test
    fun parser_rejectsUnknownMatchType() {
        assertViolation(
            """{"match":"SOME","node":[{"field":"title","regex":"x"}]}""",
            NotificationAdvancedFilterError.INVALID_MATCH_TYPE,
        )
        assertViolation(
            """{"match":"alll","node":[{"field":"title","regex":"x"}]}""",
            NotificationAdvancedFilterError.INVALID_MATCH_TYPE,
        )
    }

    @Test
    fun parser_rejectsUnknownNodeKey_insteadOfIgnoringIt() {
        assertViolation(
            """{"match":"ANY","node":[{"field":"title","regex":"x","invert":"yes","negate":true}]}""",
            NotificationAdvancedFilterError.UNKNOWN_KEY,
        )
    }

    @Test
    fun parser_rejectsUnknownTopLevelKey() {
        assertViolation(
            """{"match":"ANY","nodes":[{"field":"title","regex":"x"}]}""",
            NotificationAdvancedFilterError.UNKNOWN_KEY,
        )
    }

    @Test
    fun parser_rejectsUnsupportedField() {
        assertViolation(
            """{"match":"ANY","node":[{"field":"body","regex":"x"}]}""",
            NotificationAdvancedFilterError.UNSUPPORTED_FIELD,
        )
    }

    @Test
    fun parser_rejectsBlankRegex() {
        assertViolation(
            """{"match":"ANY","node":[{"field":"title","regex":"  "}]}""",
            NotificationAdvancedFilterError.EMPTY_REGEX,
        )
    }

    @Test
    fun parser_rejectsBrokenRegex() {
        assertViolation(
            """{"match":"ANY","node":[{"field":"title","regex":"[unclosed"}]}""",
            NotificationAdvancedFilterError.INVALID_REGEX,
        )
    }

    @Test
    fun parser_rejectsEmptyNodes() {
        assertViolation("""{"match":"ANY","node":[]}""", NotificationAdvancedFilterError.EMPTY_NODES)
        assertViolation("""{"match":"ANY"}""", NotificationAdvancedFilterError.EMPTY_NODES)
    }

    @Test
    fun parser_rejectsMalformedAndBlankJson() {
        assertViolation("""{"match":"ANY",""", NotificationAdvancedFilterError.INVALID_JSON)
        assertViolation("", NotificationAdvancedFilterError.INVALID_JSON)
        assertViolation("null", NotificationAdvancedFilterError.INVALID_JSON)
    }

    @Test
    fun parser_acceptsEverySupportedField() {
        NotificationRuleFieldNames.ALL.forEach { field ->
            val parsed = NotificationAdvancedFilterJsonParser.parse(
                """{"match":"ANY","node":[{"field":"$field","regex":"x"}]}""",
            )
            assertTrue("field $field should be accepted", parsed is NotificationAdvancedFilterValidation.Valid)
        }
    }

    // --- helpers ---

    private fun match(rule: NotificationFilterRule, title: String, text: String): Boolean =
        NotificationRuleMatcher.matches(
            rule = rule,
            packageName = "com.tencent.mm",
            channelId = null,
            title = title,
            text = text,
        )

    private fun advancedRule(json: String): NotificationFilterRule = NotificationFilterRule(
        name = "advanced",
        appMode = AppMatchMode.ALL,
        textMode = TextMatchMode.ADVANCED,
        advancedFilterJson = json,
        actionEntries = listOf(RuleActionEntry(NotificationRuleActionType.HIDE)),
    )

    private fun assertViolation(
        json: String,
        expected: NotificationAdvancedFilterError,
    ) {
        val parsed = NotificationAdvancedFilterJsonParser.parse(json)
        val violation = (parsed as? NotificationAdvancedFilterValidation.Invalid)?.violation
        assertTrue("expected a violation for: $json", violation != null)
        assertEquals("unexpected error code for: $json", expected, violation!!.error)
    }
}
