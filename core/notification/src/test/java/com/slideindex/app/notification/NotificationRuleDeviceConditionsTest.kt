package com.slideindex.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「不限制」的取值约定，供编辑页回显与匹配器共用。
 *
 * 历史版本把「充电状态三项都没勾」保存成三位全选的位值，回显时又逐位判断，于是重进编辑页会把
 * 三项都勾上；屏幕状态同样把「两个都没勾」与「两个都勾」都写成 BOTH。这里固定不限制的规范值，
 * 保证编码、解码、回显与匹配看到的是同一件事。
 */
class NotificationRuleDeviceConditionsTest {

    @Test
    fun chargeMask_isUnrestricted_onlyForZeroOrEveryModeSelected() {
        assertTrue(NotificationRuleChargeMask.isUnrestricted(0))
        assertTrue(NotificationRuleChargeMask.isUnrestricted(NotificationRuleChargeMask.UNRESTRICTED))

        assertFalse(NotificationRuleChargeMask.isUnrestricted(NotificationRuleChargeMask.BATTERY))
        assertFalse(NotificationRuleChargeMask.isUnrestricted(NotificationRuleChargeMask.WIRED))
        assertFalse(NotificationRuleChargeMask.isUnrestricted(NotificationRuleChargeMask.WIRELESS))
        assertFalse(
            NotificationRuleChargeMask.isUnrestricted(
                NotificationRuleChargeMask.BATTERY or NotificationRuleChargeMask.WIRELESS,
            ),
        )
    }

    @Test
    fun chargeMask_canonical_collapsesUnrestrictedWritingsOnly() {
        assertEquals(
            NotificationRuleChargeMask.UNRESTRICTED,
            NotificationRuleChargeMask.canonical(0),
        )
        assertEquals(
            NotificationRuleChargeMask.UNRESTRICTED,
            NotificationRuleChargeMask.canonical(NotificationRuleChargeMask.UNRESTRICTED),
        )
        assertEquals(NotificationRuleChargeMask.BATTERY, NotificationRuleChargeMask.canonical(1))
        assertEquals(NotificationRuleChargeMask.WIRED, NotificationRuleChargeMask.canonical(6))
        assertEquals(NotificationRuleChargeMask.WIRELESS, NotificationRuleChargeMask.canonical(8))
        assertEquals(
            NotificationRuleChargeMask.BATTERY or NotificationRuleChargeMask.WIRED,
            NotificationRuleChargeMask.canonical(7),
        )
    }

    @Test
    fun chargeMask_wiredCoversWiredChargingSources() {
        // 有线充电在系统里可能报 AC 或 USB，两者都必须被「有线充电」勾选项命中。
        assertTrue(NotificationRuleChargeMask.WIRED and NotificationRuleChargeMask.AC != 0)
        assertTrue(NotificationRuleChargeMask.WIRED and NotificationRuleChargeMask.USB != 0)
    }

    @Test
    fun screenMode_fromPersistedName_readsLegacyAndUnknownAsUnrestricted() {
        assertEquals(ScreenMode.ANY, ScreenMode.fromPersistedName("ANY"))
        assertEquals(ScreenMode.ANY, ScreenMode.fromPersistedName("BOTH"))
        assertEquals(ScreenMode.ON, ScreenMode.fromPersistedName("ON"))
        assertEquals(ScreenMode.OFF, ScreenMode.fromPersistedName("OFF"))
        assertEquals(ScreenMode.ANY, ScreenMode.fromPersistedName(""))
        assertEquals(ScreenMode.ANY, ScreenMode.fromPersistedName("NOPE"))
    }

    @Test
    fun rule_defaults_areUnrestricted() {
        val rule = NotificationFilterRule()

        assertEquals(ScreenMode.ANY, rule.screenMode)
        assertEquals(NotificationRuleChargeMask.UNRESTRICTED, rule.chargeMask)
    }
}
