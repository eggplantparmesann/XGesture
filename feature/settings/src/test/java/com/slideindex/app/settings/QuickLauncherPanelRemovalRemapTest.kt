package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelCodec
import com.slideindex.app.overlay.PanelSide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 删除启动面板时，指向它的槽位/动作要跟着改写到存活面板。
 *
 * 覆盖三条真实存储路径：圆环槽位（FV 槽位）、侧边手势槽位（手势规则）、其它启动面板里的条目。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class QuickLauncherPanelRemovalRemapTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(RuntimeEnvironment.getApplication())
    }

    @Test
    fun removingPanel_remapsRingSlotGestureSlotAndPanelItem() = runBlocking {
        val keptPanelId = "test-keep-panel"
        val removedPanelId = "test-remove-panel"
        val initialWrite = repository.setQuickLauncherPanels(
            listOf(
                QuickLauncherPanel(id = keptPanelId, name = "Keep"),
                QuickLauncherPanel(id = removedPanelId, name = "Remove"),
            ),
        )
        assertTrue("写入面板失败: $initialWrite", initialWrite.isSuccess)

        val quickLauncherItem = QuickLauncherItem.action(
            GestureAction.QuickLauncher(removedPanelId),
            "快速启动器",
        )
        val slotWrite = repository.setFvRingLauncherSlot(FvRingLauncherAxis.VERTICAL, 0, quickLauncherItem)
        assertTrue("写入圆环槽位失败: $slotWrite", slotWrite.isSuccess)
        val gestureWrite = repository.setSlotConfig(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.QuickLauncher(removedPanelId),
            triggerMode = GestureTriggerMode.DEFAULT,
        )
        assertTrue("写入手势槽位失败: $gestureWrite", gestureWrite.isSuccess)
        val itemsWrite = repository.updateQuickLauncherPanelItems(keptPanelId, listOf(quickLauncherItem))
        assertTrue("写入面板条目失败: $itemsWrite", itemsWrite.isSuccess)
        assertEquals(
            "面板条目应在删除面板前写入",
            1,
            repository.settings.first().quickLauncherPanels.first { it.id == keptPanelId }.items.size,
        )

        // 真实删除路径是「把剩下的面板原样写回」，这里保持同样语义（存活面板的条目仍在）。
        val survivingPanels = repository.settings.first().quickLauncherPanels
            .filterNot { it.id == removedPanelId }
        val removeWrite = repository.setQuickLauncherPanels(survivingPanels)
        assertTrue("删除面板失败: $removeWrite", removeWrite.isSuccess)

        val expected = GestureAction.QuickLauncher(keptPanelId)
        val settings = repository.settings.first()

        assertEquals(
            expected,
            settings
                .fvRingLauncherFor(FvRingLauncherAxis.VERTICAL)
                .slots[0]
                ?.let { QuickLauncherItemCodec.parseActionPayload(it.payload) },
        )
        assertEquals(expected, settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN))
        assertEquals(
            expected,
            settings.quickLauncherPanels
                .first { it.id == keptPanelId }
                .items
                .first()
                .let { QuickLauncherItemCodec.parseActionPayload(it.payload) },
        )
    }

    @Test
    fun writingRingSlotWithoutPanel_pinsToFirstPanel() = runBlocking {
        val firstPanelId = "test-first-panel"
        val secondPanelId = "test-second-panel"
        repository.setQuickLauncherPanels(
            listOf(
                QuickLauncherPanel(id = firstPanelId, name = "First"),
                QuickLauncherPanel(id = secondPanelId, name = "Second"),
            ),
        )

        repository.setFvRingLauncherSlot(
            FvRingLauncherAxis.VERTICAL,
            1,
            QuickLauncherItem.action(GestureAction.QuickLauncher(""), "快速启动器"),
        )

        val settings = repository.settings.first()
        assertEquals(
            GestureAction.QuickLauncher(firstPanelId),
            settings
                .fvRingLauncherFor(FvRingLauncherAxis.VERTICAL)
                .slots[1]
                ?.let { QuickLauncherItemCodec.parseActionPayload(it.payload) },
        )
    }

    @Test
    fun oneTimeMigration_pinsLegacyRingSlotWithoutPanel() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val editor = SettingsPreferencesEditor(context)
        val panelId = "test-migrated-panel"
        val legacyBlankSlot = QuickLauncherItem.action(GestureAction.QuickLauncher(""), "快速启动器")

        editor.edit { prefs ->
            prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_PANELS] = QuickLauncherPanelCodec.encodeAll(
                listOf(QuickLauncherPanel(id = panelId, name = "Panel")),
            )
            prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_PANEL_REFERENCE_MIGRATED] = false
            FvRingLauncherSettings.writeSlotsAxis(
                prefs,
                FvRingLauncherAxis.VERTICAL,
                FvRingLauncherSettings(slots = mapOf(0 to legacyBlankSlot)),
            )
        }

        OverlaySettingsMutator(editor).migrateFvRingLauncherQuickLauncherPanelsOnce()

        val settings = editor.settings.first()
        assertEquals(
            GestureAction.QuickLauncher(panelId),
            settings
                .fvRingLauncherFor(FvRingLauncherAxis.VERTICAL)
                .slots[0]
                ?.let { QuickLauncherItemCodec.parseActionPayload(it.payload) },
        )
    }
}
