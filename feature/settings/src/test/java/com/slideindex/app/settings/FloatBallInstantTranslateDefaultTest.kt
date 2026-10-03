package com.slideindex.app.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 取词面板翻译的「即时翻译」开关默认开启（浮窗显示），关闭时才是跳网页翻译。
 *
 * 存量用户里从没碰过这个开关的，由一次性迁移补写成新默认值；
 * 已经显式关掉的必须保持关闭。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FloatBallInstantTranslateDefaultTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(context)
    }

    @Test
    fun freshInstall_instantTranslateEnabledByDefault() {
        val settings = SettingsSnapshotReader.read(emptyPreferences(), context)

        assertTrue(settings.floatBallInstantTranslate)
    }

    @Test
    fun explicitOff_isPreserved() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.FLOAT_BALL_INSTANT_TRANSLATE to false,
        )

        assertFalse(SettingsSnapshotReader.read(prefs, context).floatBallInstantTranslate)
    }

    @Test
    fun repositoryMigration_enablesInstantTranslateForExistingInstall() = runBlocking {
        // 仓库 init 会跑一次性迁移：存量用户（没写过该键）补写成开。
        val settings = withTimeout(5_000) {
            repository.settings.first { it.floatBallInstantTranslate }
        }

        assertTrue(settings.floatBallInstantTranslate)
    }

    @Test
    fun migrationPass_keepsUserExplicitOff() = runBlocking {
        val write = repository.setFloatBallInstantTranslate(false)
        assertTrue("写入即时翻译开关失败: $write", write.isSuccess)

        // 模拟旧版本用户升级：迁移再跑一遍，显式关掉的不能被改写。
        val mutator = OverlaySettingsMutator(SettingsPreferencesEditor(context))
        mutator.migrateFloatBallInstantTranslateDefaultOnOnce()

        val settings = withTimeout(5_000) { repository.settings.first() }
        assertFalse(settings.floatBallInstantTranslate)
    }
}
