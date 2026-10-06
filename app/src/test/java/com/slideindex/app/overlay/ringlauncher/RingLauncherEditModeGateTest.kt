package com.slideindex.app.overlay.ringlauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆环启动器的编辑模式门控。
 *
 * 回归背景：老逻辑只看「已固定槽位数 == 0」，于是槽位被清空/新装的用户会被判定成新用户，
 * 直接进编辑模式并关掉「最近应用」自动填充 —— 圆环一个图标都不显示，
 * 必须手动钉一个槽位，其余槽位才一起填充。真机已复现。
 */
class RingLauncherEditModeGateTest {

    @Test
    fun emptySlotsWithAppsToFill_showsRingInsteadOfEditMode() {
        assertFalse(
            "槽位为空但有应用可填充时必须正常显示圆环（自动填充），不能进编辑模式",
            shouldStartRingLauncherInEditMode(configuredSlotCount = 0, autoFillCandidateCount = 12),
        )
    }

    @Test
    fun emptySlotsAndNoApps_fallsBackToEditMode() {
        assertTrue(
            "既没有固定槽位也没有任何应用可填时才进编辑模式",
            shouldStartRingLauncherInEditMode(configuredSlotCount = 0, autoFillCandidateCount = 0),
        )
    }

    @Test
    fun configuredSlots_neverStartInEditMode() {
        assertFalse(
            "已经固定过槽位的用户永远不进编辑模式",
            shouldStartRingLauncherInEditMode(configuredSlotCount = 1, autoFillCandidateCount = 0),
        )
        assertFalse(
            shouldStartRingLauncherInEditMode(configuredSlotCount = 5, autoFillCandidateCount = 200),
        )
    }
}
