package com.slideindex.app.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SearchPanelSectionAliasSettings(
    val apps: String = DEFAULT_APPS,
    val contacts: String = DEFAULT_CONTACTS,
    val files: String = DEFAULT_FILES,
    val settings: String = DEFAULT_SETTINGS,
    val shortcuts: String = DEFAULT_SHORTCUTS,
    val clipboard: String = DEFAULT_CLIPBOARD,
) {
    fun normalized(): SearchPanelSectionAliasSettings = copy(
        apps = normalize(apps).ifEmpty { DEFAULT_APPS },
        contacts = normalize(contacts).ifEmpty { DEFAULT_CONTACTS },
        files = normalize(files).ifEmpty { DEFAULT_FILES },
        settings = normalize(settings).ifEmpty { DEFAULT_SETTINGS },
        shortcuts = normalize(shortcuts).ifEmpty { DEFAULT_SHORTCUTS },
        clipboard = normalize(clipboard).ifEmpty { DEFAULT_CLIPBOARD },
    )

    fun toAliasLookup(): Map<String, String> {
        val n = normalized()
        return buildMap {
            put(n.apps, SECTION_APPS)
            put(n.contacts, SECTION_CONTACTS)
            put(n.files, SECTION_FILES)
            put(n.settings, SECTION_SETTINGS)
            put(n.shortcuts, SECTION_SHORTCUTS)
            put(n.clipboard, SECTION_CLIPBOARD)
            // 内置中文/别名兜底，不被自定义主键覆盖时仍可用
            putIfAbsent("apps", SECTION_APPS)
            putIfAbsent("应用", SECTION_APPS)
            putIfAbsent("contacts", SECTION_CONTACTS)
            putIfAbsent("ct", SECTION_CONTACTS)
            putIfAbsent("联系人", SECTION_CONTACTS)
            putIfAbsent("files", SECTION_FILES)
            putIfAbsent("文件", SECTION_FILES)
            putIfAbsent("set", SECTION_SETTINGS)
            putIfAbsent("设置", SECTION_SETTINGS)
            putIfAbsent("shortcut", SECTION_SHORTCUTS)
            putIfAbsent("快捷方式", SECTION_SHORTCUTS)
            putIfAbsent("clip", SECTION_CLIPBOARD)
            putIfAbsent("剪贴板", SECTION_CLIPBOARD)
        }
    }

    companion object {
        const val DEFAULT_APPS = "app"
        const val DEFAULT_CONTACTS = "contact"
        const val DEFAULT_FILES = "file"
        const val DEFAULT_SETTINGS = "settings"
        const val DEFAULT_SHORTCUTS = "sc"
        const val DEFAULT_CLIPBOARD = "cb"

        const val SECTION_APPS = "APPS"
        const val SECTION_CONTACTS = "CONTACTS"
        const val SECTION_FILES = "FILES"
        const val SECTION_SETTINGS = "SETTINGS"
        const val SECTION_SHORTCUTS = "SHORTCUTS"
        const val SECTION_CLIPBOARD = "CLIPBOARD"

        private val json = Json { ignoreUnknownKeys = true }

        fun normalize(raw: String): String =
            raw.trim().lowercase().replace("\\s+".toRegex(), "")

        fun fromJson(raw: String?): SearchPanelSectionAliasSettings {
            if (raw.isNullOrBlank()) return SearchPanelSectionAliasSettings()
            return runCatching { json.decodeFromString<SearchPanelSectionAliasSettings>(raw) }
                .getOrDefault(SearchPanelSectionAliasSettings())
                .normalized()
        }

        fun toJson(value: SearchPanelSectionAliasSettings): String =
            json.encodeToString(value.normalized())
    }
}
