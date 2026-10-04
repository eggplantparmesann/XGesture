package com.slideindex.app.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslateLaunchPlannerTest {
    private val googleTranslate = TranslateLaunchPlanner.GOOGLE_TRANSLATE_PACKAGE

    @Test
    fun processTextOnly_prefersAppOverWeb() {
        assertEquals(
            listOf(TranslateLaunchChannel.PROCESS_TEXT, TranslateLaunchChannel.WEB),
            TranslateLaunchPlanner.plan(
                processTextPackages = setOf(googleTranslate),
                sendPackages = emptySet(),
            ),
        )
    }

    @Test
    fun bothChannelsDeclared_keepsSendAsSecondChance() {
        assertEquals(
            listOf(
                TranslateLaunchChannel.PROCESS_TEXT,
                TranslateLaunchChannel.SEND,
                TranslateLaunchChannel.WEB,
            ),
            TranslateLaunchPlanner.plan(
                processTextPackages = setOf(googleTranslate),
                sendPackages = setOf(googleTranslate),
            ),
        )
    }

    @Test
    fun sendOnly_fallsBackToSendThenWeb() {
        assertEquals(
            listOf(TranslateLaunchChannel.SEND, TranslateLaunchChannel.WEB),
            TranslateLaunchPlanner.plan(
                processTextPackages = emptySet(),
                sendPackages = setOf(googleTranslate),
            ),
        )
    }

    @Test
    fun translateAppAbsent_goesStraightToWeb() {
        assertEquals(
            listOf(TranslateLaunchChannel.WEB),
            TranslateLaunchPlanner.plan(
                processTextPackages = emptySet(),
                sendPackages = emptySet(),
            ),
        )
    }

    @Test
    fun otherAppsHandlingText_doNotCount() {
        assertEquals(
            listOf(TranslateLaunchChannel.WEB),
            TranslateLaunchPlanner.plan(
                processTextPackages = setOf("com.example.other.dict"),
                sendPackages = setOf("com.example.other.dict"),
            ),
        )
    }

    @Test
    fun customTargetPackage_isSupported() {
        assertEquals(
            listOf(TranslateLaunchChannel.PROCESS_TEXT, TranslateLaunchChannel.WEB),
            TranslateLaunchPlanner.plan(
                processTextPackages = setOf("com.qianyan.eudic"),
                sendPackages = emptySet(),
                targetPackage = "com.qianyan.eudic",
            ),
        )
    }

    @Test
    fun webIsAlwaysTheLastResort() {
        val plan = TranslateLaunchPlanner.plan(
            processTextPackages = setOf(googleTranslate),
            sendPackages = setOf(googleTranslate),
        )
        assertEquals(TranslateLaunchChannel.WEB, plan.last())
    }
}
