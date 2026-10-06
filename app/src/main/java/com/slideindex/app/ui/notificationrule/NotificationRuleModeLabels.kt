package com.slideindex.app.ui.notificationrule

import androidx.annotation.StringRes
import com.slideindex.app.R
import com.slideindex.app.notification.AppMatchMode
import com.slideindex.app.notification.ScreenMode
import com.slideindex.app.notification.TextMatchMode

/**
 * Display order and labels for the app / text / screen condition dropdowns.
 *
 * The dropdowns in [NotificationRuleConditionEditor] index a list of labels and map the index
 * back to an enum constant. Building that list by hand and assuming it lines up with the enum
 * declaration order is what caused a real bug: `AppMatchMode` declares `INCLUDE, EXCLUDE, ALL`
 * while the labels were written as 所有应用 / 包含 / 不包含, so every selection silently applied
 * the neighbouring rule — "所有应用" stored `INCLUDE`, "包含" stored `EXCLUDE`.
 *
 * Pairing each constant with its own label here removes the positional assumption, and the
 * functions are pure so [NotificationRuleModeLabelsTest] can assert the contract.
 */
object NotificationRuleModeLabels {

    /** App condition options, in display order. */
    val appModes: List<Pair<AppMatchMode, Int>> = listOf(
        AppMatchMode.ALL to R.string.notification_rule_app_mode_all,
        AppMatchMode.INCLUDE to R.string.notification_rule_app_mode_include,
        AppMatchMode.EXCLUDE to R.string.notification_rule_app_mode_exclude,
    )

    /** Text condition options, in display order. */
    val textModes: List<Pair<TextMatchMode, Int>> = listOf(
        TextMatchMode.ALL to R.string.notification_rule_text_mode_all,
        TextMatchMode.CONTAIN_ANY to R.string.notification_rule_text_mode_contain_any,
        TextMatchMode.NOT_CONTAIN_ANY to R.string.notification_rule_text_mode_not_contain_any,
        TextMatchMode.CONTAIN_ALL to R.string.notification_rule_text_mode_contain_all,
        TextMatchMode.NOT_CONTAIN_ALL to R.string.notification_rule_text_mode_not_contain_all,
        TextMatchMode.CONTAIN_AND_NOT_CONTAIN to R.string.notification_rule_text_mode_contain_and_not,
        TextMatchMode.REGEX to R.string.notification_rule_text_mode_regex,
        TextMatchMode.ADVANCED to R.string.notification_rule_text_mode_advanced,
    )

    /** 屏幕状态条件选项，按显示顺序；首项为「不限」，对应 [ScreenMode.ANY]。 */
    val screenModes: List<Pair<ScreenMode, Int>> = listOf(
        ScreenMode.ANY to R.string.notification_rule_screen_any,
        ScreenMode.ON to R.string.notification_rule_screen_on,
        ScreenMode.OFF to R.string.notification_rule_screen_off,
    )

    @StringRes
    fun appModeLabelRes(mode: AppMatchMode): Int = appModes.first { it.first == mode }.second

    @StringRes
    fun textModeLabelRes(mode: TextMatchMode): Int = textModes.first { it.first == mode }.second

    @StringRes
    fun screenModeLabelRes(mode: ScreenMode): Int = screenModes.first { it.first == mode }.second
}
