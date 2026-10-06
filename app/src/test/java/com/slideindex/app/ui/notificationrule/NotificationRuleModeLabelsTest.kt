package com.slideindex.app.ui.notificationrule

import com.slideindex.app.R
import com.slideindex.app.notification.AppMatchMode
import com.slideindex.app.notification.ScreenMode
import com.slideindex.app.notification.TextMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the app / text / screen condition dropdowns against the positional-mapping bug that
 * shipped: the label list was ordered 所有应用 / 包含 / 不包含 while `AppMatchMode` declares
 * `INCLUDE, EXCLUDE, ALL`, so selecting any option applied the neighbouring mode.
 */
class NotificationRuleModeLabelsTest {

    @Test
    fun appModes_coverEveryConstantExactlyOnce() {
        val modes = NotificationRuleModeLabels.appModes.map { it.first }
        assertEquals(AppMatchMode.entries.size, modes.size)
        assertEquals(AppMatchMode.entries.toSet(), modes.toSet())
    }

    @Test
    fun appModes_pairEachConstantWithItsOwnLabel() {
        assertEquals(
            R.string.notification_rule_app_mode_all,
            NotificationRuleModeLabels.appModeLabelRes(AppMatchMode.ALL),
        )
        assertEquals(
            R.string.notification_rule_app_mode_include,
            NotificationRuleModeLabels.appModeLabelRes(AppMatchMode.INCLUDE),
        )
        assertEquals(
            R.string.notification_rule_app_mode_exclude,
            NotificationRuleModeLabels.appModeLabelRes(AppMatchMode.EXCLUDE),
        )
    }

    @Test
    fun appModes_firstOptionIsUnrestricted() {
        // The list is indexed by the dropdown, so a stale order would put a restricted mode
        // first while still rendering the 所有应用 label.
        assertEquals(AppMatchMode.ALL, NotificationRuleModeLabels.appModes.first().first)
        assertEquals(
            R.string.notification_rule_app_mode_all,
            NotificationRuleModeLabels.appModes.first().second,
        )
    }

    @Test
    fun appModes_labelsAreDistinct() {
        val labels = NotificationRuleModeLabels.appModes.map { it.second }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun textModes_coverEveryConstantExactlyOnce() {
        val modes = NotificationRuleModeLabels.textModes.map { it.first }
        assertEquals(TextMatchMode.entries.size, modes.size)
        assertEquals(TextMatchMode.entries.toSet(), modes.toSet())
    }

    @Test
    fun textModes_pairEachConstantWithItsOwnLabel() {
        assertEquals(
            R.string.notification_rule_text_mode_all,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.ALL),
        )
        assertEquals(
            R.string.notification_rule_text_mode_contain_any,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.CONTAIN_ANY),
        )
        assertEquals(
            R.string.notification_rule_text_mode_not_contain_any,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.NOT_CONTAIN_ANY),
        )
        assertEquals(
            R.string.notification_rule_text_mode_contain_all,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.CONTAIN_ALL),
        )
        assertEquals(
            R.string.notification_rule_text_mode_not_contain_all,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.NOT_CONTAIN_ALL),
        )
        assertEquals(
            R.string.notification_rule_text_mode_contain_and_not,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.CONTAIN_AND_NOT_CONTAIN),
        )
        assertEquals(
            R.string.notification_rule_text_mode_regex,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.REGEX),
        )
        assertEquals(
            R.string.notification_rule_text_mode_advanced,
            NotificationRuleModeLabels.textModeLabelRes(TextMatchMode.ADVANCED),
        )
    }

    @Test
    fun textModes_labelsAreDistinct() {
        val labels = NotificationRuleModeLabels.textModes.map { it.second }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun screenModes_coverEveryConstantExactlyOnce() {
        val modes = NotificationRuleModeLabels.screenModes.map { it.first }
        assertEquals(ScreenMode.entries.size, modes.size)
        assertEquals(ScreenMode.entries.toSet(), modes.toSet())
    }

    @Test
    fun screenModes_pairEachConstantWithItsOwnLabel() {
        assertEquals(
            R.string.notification_rule_screen_any,
            NotificationRuleModeLabels.screenModeLabelRes(ScreenMode.ANY),
        )
        assertEquals(
            R.string.notification_rule_screen_on,
            NotificationRuleModeLabels.screenModeLabelRes(ScreenMode.ON),
        )
        assertEquals(
            R.string.notification_rule_screen_off,
            NotificationRuleModeLabels.screenModeLabelRes(ScreenMode.OFF),
        )
    }

    @Test
    fun screenModes_firstOptionIsUnrestricted() {
        // 不限制必须是一个能明确选中的选项，而不是由「亮屏、熄屏都勾上」推导。
        assertEquals(ScreenMode.ANY, NotificationRuleModeLabels.screenModes.first().first)
        assertEquals(
            R.string.notification_rule_screen_any,
            NotificationRuleModeLabels.screenModes.first().second,
        )
    }

    @Test
    fun screenModes_labelsAreDistinct() {
        val labels = NotificationRuleModeLabels.screenModes.map { it.second }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun everyOptionExposesExactlyOneLabel() {
        assertTrue(NotificationRuleModeLabels.appModes.all { it.second != 0 })
        assertTrue(NotificationRuleModeLabels.textModes.all { it.second != 0 })
        assertTrue(NotificationRuleModeLabels.screenModes.all { it.second != 0 })
    }
}
