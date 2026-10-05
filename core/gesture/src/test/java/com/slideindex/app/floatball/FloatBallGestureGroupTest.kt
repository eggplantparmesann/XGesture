package com.slideindex.app.floatball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallGestureGroupTest {
    @Test
    fun `every gesture appears exactly once across groups`() {
        val grouped = FloatBallGestureGroup.displayOrder.flatMap { it.types }

        assertEquals(grouped.size, grouped.toSet().size)
        assertEquals(FloatBallGestureType.entries.toSet(), grouped.toSet())
    }

    @Test
    fun `display order is derived from groups`() {
        assertEquals(
            FloatBallGestureGroup.displayOrder.flatMap { it.types },
            FloatBallGestureType.settingsDisplayOrder(),
        )
    }

    @Test
    fun `compound gestures sit next to the direction they start with`() {
        assertTrue(
            FloatBallGestureGroup.DOWN_SWIPE.types.contains(FloatBallGestureType.SWIPE_DOWN_IN),
        )
        assertTrue(
            FloatBallGestureGroup.UP_SWIPE.types.contains(FloatBallGestureType.SWIPE_UP_IN),
        )
        assertTrue(
            FloatBallGestureGroup.SIDE_SWIPE.types.contains(FloatBallGestureType.SWIPE_IN_DOWN),
        )
        assertTrue(
            FloatBallGestureGroup.SIDE_SWIPE.types.contains(FloatBallGestureType.SWIPE_IN_UP),
        )
    }

    @Test
    fun `tap group holds the click style gestures`() {
        assertEquals(
            listOf(
                FloatBallGestureType.SINGLE_TAP,
                FloatBallGestureType.DOUBLE_TAP,
                FloatBallGestureType.LONG_PRESS,
            ),
            FloatBallGestureGroup.TAP.types,
        )
    }
}
