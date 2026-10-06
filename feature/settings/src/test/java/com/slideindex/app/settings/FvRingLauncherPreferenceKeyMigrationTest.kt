package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 圆环启动器偏好键由 `fv_ring_launcher_*` 改名为 `fv_ring_launcher_*` 的一次性搬运。
 *
 * 存量用户的配置写在旧键名下，搬迁必须无损；新键已有值时以新键为准。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FvRingLauncherPreferenceKeyMigrationTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private lateinit var editor: SettingsPreferencesEditor
    private lateinit var mutator: OverlaySettingsMutator
    private lateinit var pairs: List<Pair<Preferences.Key<*>, Preferences.Key<*>>>

    /** 取旧键 → 新键映射，直接读生产代码用的搬运清单，避免测试自己写一份字面值。 */
    private fun legacyKeyFor(currentKey: Preferences.Key<*>): Preferences.Key<*> =
        pairs.first { (_, current) -> current == currentKey }.first

    @Before
    fun setUp() {
        editor = SettingsPreferencesEditor(context)
        mutator = OverlaySettingsMutator(editor)
        pairs = SettingsPreferenceKeys.legacyFvRingLauncherRenamePairs()
    }

    /**
     * 清空迁移相关的键。
     *
     * 设置存储是进程级单例、底层同一个 DataStore 文件，测试之间会互相看见彼此写入的键
     * （已由首轮失败暴露：前一个用例把圈数写成 4，后一个用例就搬到了 4）。
     * 所以每个用例先显式清场，不依赖「全新安装」的假设。
     */
    private suspend fun clearMigrationKeys() {
        editor.edit { prefs ->
            pairs.forEach { (legacy, current) ->
                prefs.remove(legacy)
                prefs.remove(current)
            }
            prefs.remove(SettingsPreferenceKeys.FV_RING_LAUNCHER_KEYS_RENAMED)
        }.getOrThrow()
    }

    @Test
    fun renamePairs_coverLegacyRingLauncherLiterals() {
        assertTrue("搬运清单不应为空", pairs.isNotEmpty())
        pairs.forEach { (legacy, current) ->
            assertEquals(
                "旧键名必须是已落盘的 fv_app_switcher_ 字面值，不能被改名脚本一起改掉",
                "fv_app_switcher_",
                legacy.name.substring(0, "fv_app_switcher_".length),
            )
            assertEquals(
                "新键名必须使用 fv_ring_launcher_ 前缀",
                "fv_ring_launcher_",
                current.name.substring(0, "fv_ring_launcher_".length),
            )
            assertNotEquals(
                "旧键与新键绝不能是同一个键：否则搬运会「读新键又删新键」，直接清空用户数据",
                legacy.name,
                current.name,
            )
        }
        assertEquals(
            "每个旧键只能映射到一个新键",
            pairs.size,
            pairs.map { it.first.name }.toSet().size,
        )
    }

    @Test
    fun migration_movesExistingUserDataToNewKeys() = runBlocking {
        val legacyCircleCount = legacyKeyFor(SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT)
        val legacyShowToolbar = legacyKeyFor(SettingsPreferenceKeys.FV_RING_LAUNCHER_SHOW_TOOLBAR)
        clearMigrationKeys()

        editor.edit { prefs ->
            @Suppress("UNCHECKED_CAST")
            prefs[legacyCircleCount as Preferences.Key<Any>] = 3
            @Suppress("UNCHECKED_CAST")
            prefs[legacyShowToolbar as Preferences.Key<Any>] = false
        }.getOrThrow()

        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val stored = editor.readRawPreferences()
        assertEquals(
            "圈数应搬到新键",
            3,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT],
        )
        assertEquals(
            "工具栏开关（显式 false）也要搬，不能被当成没设置",
            false,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_SHOW_TOOLBAR],
        )
        assertNull("旧键应被清掉，避免旧值回灌", stored[legacyCircleCount])
        assertNull("旧键应被清掉", stored[legacyShowToolbar])
        assertEquals(
            "搬运标记应落盘",
            true,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_KEYS_RENAMED],
        )
    }

    @Test
    fun migration_keepsNewerValueWhenBothKeysExist() = runBlocking {
        val legacyCircleCount = legacyKeyFor(SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT)
        clearMigrationKeys()

        editor.edit { prefs ->
            @Suppress("UNCHECKED_CAST")
            prefs[legacyCircleCount as Preferences.Key<Any>] = 2
            prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT] = 4
        }.getOrThrow()

        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val stored = editor.readRawPreferences()
        assertEquals(
            "新键已有值时不能被旧值覆盖",
            4,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT],
        )
        assertNull("旧的重复键应被清掉", stored[legacyCircleCount])
    }

    @Test
    fun secondPassWithoutLegacyKeys_changesNothing() = runBlocking {
        val legacyCircleCount = legacyKeyFor(SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT)
        clearMigrationKeys()

        editor.edit { prefs ->
            @Suppress("UNCHECKED_CAST")
            prefs[legacyCircleCount as Preferences.Key<Any>] = 2
        }.getOrThrow()
        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val afterFirst = editor.readRawPreferences()
        assertEquals(
            "第一次迁移应把旧值搬过来",
            2,
            afterFirst[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT],
        )

        // 旧键已被清掉，再跑一遍必须是空操作（标记不参与判断）。
        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val stored = editor.readRawPreferences()
        assertEquals(
            "第二次迁移不能再改新键",
            2,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT],
        )
        assertEquals(
            "搬运标记应保持为 true",
            true,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_KEYS_RENAMED],
        )
    }

    @Test
    fun importedLegacyBackup_isHealedEvenWhenMarkerIsSet() = runBlocking {
        val legacyCircleCount = legacyKeyFor(SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT)
        clearMigrationKeys()

        // 模拟「先跑过一次搬运（标记已置位），随后又导入了带旧键的设置备份」。
        editor.edit { prefs ->
            prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_KEYS_RENAMED] = true
            @Suppress("UNCHECKED_CAST")
            prefs[legacyCircleCount as Preferences.Key<Any>] = 5
        }.getOrThrow()

        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val stored = editor.readRawPreferences()
        assertEquals(
            "标记已置位也要把备份里带回来的旧值搬过去",
            5,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT],
        )
        assertNull("搬完应清掉旧键", stored[legacyCircleCount])
    }

    @Test
    fun migration_movesLegacySharedLinkAxesFlag() = runBlocking {
        // 旧版本还有一个更早的共享开关 fv_ring_launcher_link_axes：没写过新开关时以它兜底，
        // 且语义是「关 = 外观不联动、槽位仍联动（false, true）」，改键后这个语义必须原样保留。
        val legacyLinkAxes = legacyKeyFor(SettingsPreferenceKeys.FV_RING_LAUNCHER_LINK_AXES)
        clearMigrationKeys()

        editor.edit { prefs ->
            @Suppress("UNCHECKED_CAST")
            prefs[legacyLinkAxes as Preferences.Key<Any>] = false
        }.getOrThrow()

        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val stored = editor.readRawPreferences()
        assertEquals(
            "旧共享开关应改键而不改值",
            false,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_LINK_AXES],
        )
        val flags = FvRingLauncherSettings.linkFlagsFromPreferences(stored)
        assertEquals("外观联动应为关", false, flags.linkAppearanceAxes)
        assertEquals("槽位联动应为开", true, flags.linkSlotAxes)
    }

    @Test
    fun noLegacyKeys_migrationIsNoOp() = runBlocking {
        clearMigrationKeys()

        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val stored = editor.readRawPreferences()
        assertNull(
            "没有任何旧键时，不应凭空写入圈数",
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT],
        )
        pairs.forEach { (legacy) ->
            assertNull("旧键 ${legacy.name} 不应被重新写出来", stored[legacy])
        }
        assertEquals(
            "搬运标记仍应落盘",
            true,
            stored[SettingsPreferenceKeys.FV_RING_LAUNCHER_KEYS_RENAMED],
        )
    }
}
