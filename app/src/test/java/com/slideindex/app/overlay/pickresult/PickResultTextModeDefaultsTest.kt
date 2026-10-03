package com.slideindex.app.overlay.pickresult

import com.slideindex.app.settings.PickResultTextModeDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「点词默认状态」设置 → 取词面板进入模式的映射。
 *
 * 设置本身的读写在 feature/settings（`SettingsSnapshotReader` 用
 * `PickResultTextModeDefault.fromStorageKey`，读不到键即回落 ALWAYS_ON），
 * 这里覆盖「三选一 + 记住上次 + 未知值回落」与显式入口优先的映射规则。
 */
class PickResultTextModeDefaultsTest {

    @Test
    fun `always on resolves to word tap`() {
        assertEquals(
            PickResultTextMode.WORD_TAP,
            resolvePickResultEnterTextMode(
                explicit = null,
                defaultState = PickResultTextModeDefault.ALWAYS_ON,
                lastStoredMode = null,
            ),
        )
        // 记录过的上次状态不能影响「始终开启」。
        assertEquals(
            PickResultTextMode.WORD_TAP,
            resolvePickResultEnterTextMode(
                explicit = null,
                defaultState = PickResultTextModeDefault.ALWAYS_ON,
                lastStoredMode = PickResultTextModeStore.SELECT,
            ),
        )
    }

    @Test
    fun `always off resolves to select`() {
        assertEquals(
            PickResultTextMode.SELECT,
            resolvePickResultEnterTextMode(
                explicit = null,
                defaultState = PickResultTextModeDefault.ALWAYS_OFF,
                lastStoredMode = PickResultTextModeStore.WORD_TAP,
            ),
        )
    }

    @Test
    fun `remember last restores the last exited mode`() {
        assertEquals(
            PickResultTextMode.SELECT,
            resolvePickResultEnterTextMode(
                explicit = null,
                defaultState = PickResultTextModeDefault.REMEMBER_LAST,
                lastStoredMode = PickResultTextModeStore.SELECT,
            ),
        )
        assertEquals(
            PickResultTextMode.WORD_TAP,
            resolvePickResultEnterTextMode(
                explicit = null,
                defaultState = PickResultTextModeDefault.REMEMBER_LAST,
                lastStoredMode = PickResultTextModeStore.WORD_TAP,
            ),
        )
    }

    @Test
    fun `remember last falls back to word tap when nothing was stored`() {
        listOf<String?>(null, "", "unknown_mode").forEach { stored ->
            assertEquals(
                "stored=$stored",
                PickResultTextMode.WORD_TAP,
                resolvePickResultEnterTextMode(
                    explicit = null,
                    defaultState = PickResultTextModeDefault.REMEMBER_LAST,
                    lastStoredMode = stored,
                ),
            )
        }
    }

    @Test
    fun `explicit initial text mode wins over the setting`() {
        PickResultTextMode.values().forEach { explicit ->
            PickResultTextModeDefault.values().forEach { state ->
                listOf(null, PickResultTextModeStore.WORD_TAP, PickResultTextModeStore.SELECT).forEach { stored ->
                    assertEquals(
                        "explicit=$explicit state=$state stored=$stored",
                        explicit,
                        resolvePickResultEnterTextMode(
                            explicit = explicit,
                            defaultState = state,
                            lastStoredMode = stored,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `setting storage keys round trip and default to always on`() {
        PickResultTextModeDefault.values().forEach { state ->
            assertEquals(state, PickResultTextModeDefault.fromStorageKey(state.storageKey))
        }
        // 老用户读不到新键 / 键值非法时必须回落 ALWAYS_ON（保持原有行为）。
        assertEquals(PickResultTextModeDefault.ALWAYS_ON, PickResultTextModeDefault.fromStorageKey(null))
        assertEquals(PickResultTextModeDefault.ALWAYS_ON, PickResultTextModeDefault.fromStorageKey(""))
        assertEquals(PickResultTextModeDefault.ALWAYS_ON, PickResultTextModeDefault.fromStorageKey("legacy_value"))
    }

    @Test
    fun `mode storage codec maps edit to word tap`() {
        // EDIT 不作为下次进入的默认值，落盘为 WORD_TAP，再读回来即 WORD_TAP。
        assertEquals(PickResultTextModeStore.WORD_TAP, PickResultTextModeStore.toStorageKey(PickResultTextMode.EDIT))
        assertEquals(
            PickResultTextMode.WORD_TAP,
            PickResultTextModeStore.fromStorageKey(PickResultTextModeStore.toStorageKey(PickResultTextMode.EDIT)),
        )
        assertEquals(
            PickResultTextModeStore.WORD_TAP,
            PickResultTextModeStore.toStorageKey(PickResultTextMode.WORD_TAP),
        )
        assertEquals(PickResultTextModeStore.SELECT, PickResultTextModeStore.toStorageKey(PickResultTextMode.SELECT))
        listOf(null, "", "editor").forEach { stored ->
            assertNull(PickResultTextModeStore.fromStorageKey(stored))
        }
    }
}
