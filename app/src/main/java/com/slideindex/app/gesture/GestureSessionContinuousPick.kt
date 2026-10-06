package com.slideindex.app.gesture

internal class GestureSessionContinuousPick {
    var taskSwitcher = false
    var quickLauncher = false
    var shell = false
    var honeycomb = false
    var ringLauncher = false
    var appCarouselSwitcher = false
    var fingertipRing = false

    fun taskSwitcherActive(): Boolean = taskSwitcher

    fun quickLauncherActive(): Boolean = quickLauncher

    fun shellActive(): Boolean = shell

    fun honeycombActive(): Boolean = honeycomb

    fun ringLauncherActive(): Boolean = ringLauncher

    fun appCarouselSwitcherActive(): Boolean = appCarouselSwitcher

    fun fingertipRingActive(): Boolean = fingertipRing

    fun clearQuickLauncher() {
        quickLauncher = false
    }

    fun clearShell() {
        shell = false
    }

    fun clearHoneycomb() {
        honeycomb = false
    }

    fun clearRingLauncher() {
        ringLauncher = false
    }

    fun clearAppCarouselSwitcher() {
        appCarouselSwitcher = false
    }

    fun clearFingertipRing() {
        fingertipRing = false
    }

    fun reset() {
        taskSwitcher = false
        quickLauncher = false
        shell = false
        honeycomb = false
        ringLauncher = false
        appCarouselSwitcher = false
        fingertipRing = false
    }
}
