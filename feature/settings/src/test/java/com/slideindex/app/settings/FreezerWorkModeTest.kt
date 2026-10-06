package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezerWorkModeTest {
    @Test
    fun `unknown or missing id falls back to freeze`() {
        assertEquals(FreezerWorkMode.FREEZE, FreezerWorkMode.fromId(null))
        assertEquals(FreezerWorkMode.FREEZE, FreezerWorkMode.fromId(99))
        assertEquals(FreezerWorkMode.FREEZE, FreezerWorkMode.DEFAULT)
    }

    @Test
    fun `ids round trip`() {
        FreezerWorkMode.entries.forEach { mode ->
            assertEquals(mode, FreezerWorkMode.fromId(mode.id))
        }
    }

    @Test
    fun `only pause mode reports pause`() {
        assertTrue(FreezerWorkMode.PAUSE.isPause)
        assertFalse(FreezerWorkMode.FREEZE.isPause)
    }
}
