package com.slideindex.app.freezer

import org.junit.Assert.assertEquals
import org.junit.Test

class FreezerAppStateTest {
    @Test
    fun `enabled and not suspended is active`() {
        assertEquals(FreezerAppState.ACTIVE, FreezerAppState.of(enabled = true, suspended = false))
    }

    @Test
    fun `enabled and suspended is paused`() {
        assertEquals(FreezerAppState.PAUSED, FreezerAppState.of(enabled = true, suspended = true))
    }

    @Test
    fun `disabled is frozen even when suspended`() {
        assertEquals(FreezerAppState.FROZEN, FreezerAppState.of(enabled = false, suspended = false))
        assertEquals(FreezerAppState.FROZEN, FreezerAppState.of(enabled = false, suspended = true))
    }

    @Test
    fun `state flags are mutually exclusive`() {
        val active = FreezerAppState.of(enabled = true, suspended = false)
        val frozen = FreezerAppState.of(enabled = false, suspended = false)
        val paused = FreezerAppState.of(enabled = true, suspended = true)
        assertEquals(listOf(true, false, false), listOf(active.isActive, active.isFrozen, active.isPaused))
        assertEquals(listOf(false, true, false), listOf(frozen.isActive, frozen.isFrozen, frozen.isPaused))
        assertEquals(listOf(false, false, true), listOf(paused.isActive, paused.isFrozen, paused.isPaused))
    }
}
