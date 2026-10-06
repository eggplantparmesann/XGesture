package com.slideindex.app.ui.notificationrule

import com.slideindex.app.notification.NotificationRuleChargeMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 编辑页手机状态区「充电状态」的保存 / 回显往返。
 *
 * 复现并锁定的问题：三项都没勾时保存的是不限制（位值 15，三位全选），旧回显逻辑逐位判断，
 * 于是重新进入编辑页会把三项都勾上。
 */
class ChargeSelectionTest {

    @Test
    fun savedUnrestrictedChargeMask_reopensWithNothingChecked() {
        val saved = ChargeSelection(battery = false, wired = false, wireless = false).toMask()

        assertEquals(NotificationRuleChargeMask.UNRESTRICTED, saved)
        val reopened = ChargeSelection.fromMask(saved)
        assertFalse(reopened.battery)
        assertFalse(reopened.wired)
        assertFalse(reopened.wireless)
    }

    @Test
    fun everyRepresentableSelection_roundTrips() {
        val representable = listOf(
            ChargeSelection(battery = false, wired = false, wireless = false),
            ChargeSelection(battery = true, wired = false, wireless = false),
            ChargeSelection(battery = false, wired = true, wireless = false),
            ChargeSelection(battery = false, wired = false, wireless = true),
            ChargeSelection(battery = true, wired = true, wireless = false),
            ChargeSelection(battery = true, wired = false, wireless = true),
            ChargeSelection(battery = false, wired = true, wireless = true),
        )

        representable.forEach { selection ->
            assertEquals(selection, ChargeSelection.fromMask(selection.toMask()))
        }
    }

    @Test
    fun selectingEveryMode_collapsesToUnrestricted() {
        // 当前充电方式必然是三种之一，因此三项全勾与全不勾等价，都按不限制保存。
        val all = ChargeSelection(battery = true, wired = true, wireless = true)

        assertEquals(NotificationRuleChargeMask.UNRESTRICTED, all.toMask())
        assertEquals(ChargeSelection(battery = false, wired = false, wireless = false), ChargeSelection.fromMask(all.toMask()))
    }

    @Test
    fun fromMask_keepsSingleModeSelection() {
        assertEquals(
            ChargeSelection(battery = true, wired = false, wireless = false),
            ChargeSelection.fromMask(NotificationRuleChargeMask.BATTERY),
        )
        assertEquals(
            ChargeSelection(battery = false, wired = true, wireless = false),
            ChargeSelection.fromMask(NotificationRuleChargeMask.WIRED),
        )
        assertEquals(
            ChargeSelection(battery = false, wired = false, wireless = true),
            ChargeSelection.fromMask(NotificationRuleChargeMask.WIRELESS),
        )
    }

    @Test
    fun fromMask_widensWiredSourcesToTheWiredOption() {
        // 编辑页只有一个「有线充电」勾选项，系统单独上报 AC / USB 时按有线充电回显。
        assertEquals(
            ChargeSelection(battery = false, wired = true, wireless = false),
            ChargeSelection.fromMask(NotificationRuleChargeMask.AC),
        )
        assertEquals(
            ChargeSelection(battery = false, wired = true, wireless = false),
            ChargeSelection.fromMask(NotificationRuleChargeMask.USB),
        )
    }

    @Test
    fun toMask_writesSelectedModesOnly() {
        assertEquals(
            NotificationRuleChargeMask.BATTERY or NotificationRuleChargeMask.WIRELESS,
            ChargeSelection(battery = true, wired = false, wireless = true).toMask(),
        )
    }
}
