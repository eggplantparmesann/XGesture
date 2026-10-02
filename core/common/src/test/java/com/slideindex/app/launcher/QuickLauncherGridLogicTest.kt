package com.slideindex.app.launcher

import com.slideindex.app.launcher.QuickLauncherGridLogic.moveIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLauncherGridLogicTest {

    @Test
    fun testMoveIndex() {
        val list = listOf("A", "B", "C", "D")
        assertEquals(listOf("B", "A", "C", "D"), list.moveIndex(0, 1))
        assertEquals(listOf("B", "C", "A", "D"), list.moveIndex(0, 2))
        assertEquals(listOf("D", "A", "B", "C"), list.moveIndex(3, 0))
    }

    @Test
    fun testDisplayMappingNormalReorder() {
        // Drag item 0 over slot 2
        val mapping = QuickLauncherGridLogic.displayMapping(
            itemCount = 4,
            dragFrom = 0,
            dragSlotGlobal = 2,
            mappingSize = 4,
        )
        // 0 moved to 2 -> list reordered to [B, C, A, D]
        // slot 0 has B (idx 1), slot 1 has C (idx 2), slot 2 has hole (null), slot 3 has D (idx 3)
        assertEquals(listOf(1, 2, null, 3), mapping)
    }

    @Test
    fun testDisplayMappingMergeTarget() {
        // Drag item 0 over item 1 in merge mode
        val mapping = QuickLauncherGridLogic.displayMapping(
            itemCount = 4,
            dragFrom = 0,
            dragSlotGlobal = 1,
            mappingSize = 4,
            mergeTargetGlobal = 1,
        )
        // In merge mode, slot 0 is hole (dragged item), slot 1 is B (idx 1, target), slot 2 is C (idx 2), slot 3 is D (idx 3)
        assertEquals(listOf(null, 1, 2, 3), mapping)
    }

    @Test
    fun testDisplayMappingMergeTargetFromEnd() {
        // Drag item 3 over item 0 in merge mode
        val mapping = QuickLauncherGridLogic.displayMapping(
            itemCount = 4,
            dragFrom = 3,
            dragSlotGlobal = 0,
            mappingSize = 4,
            mergeTargetGlobal = 0,
        )
        assertEquals(listOf(0, 1, 2, null), mapping)
    }

    /**
     * 守卫测试：合成文件夹的悬停驻留时长被有意定为 900ms。
     *
     * 350ms 会把"把图标对准某一格"这个正常动作也判成建文件夹（见 QuickLauncherDragTiming 的说明）。
     * 想改这个值就必须让本测试失败并重新评估，别悄悄改回去。
     */
    @Test
    fun folderMergeDwellIsDeliberatelyLong() {
        assertTrue(
            "合成文件夹的悬停驻留时长不应低于系统长按阈值（500ms）",
            QuickLauncherDragTiming.FOLDER_MERGE_DWELL_MS >= 500L,
        )
        assertEquals(900L, QuickLauncherDragTiming.FOLDER_MERGE_DWELL_MS)
    }
}
