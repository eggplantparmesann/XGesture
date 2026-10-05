package com.slideindex.app.update

import kotlinx.serialization.Serializable

/** 仓库根目录 [update.json]，发版时由脚本更新，App 通过 CDN/raw 拉取（不走 GitHub API）。 */
@Serializable
data class UpdateManifest(
    val version: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val apkSize: Long = 0L,
    val notes: String = "",
    /** 与 [notes] 同版的英文文案；缺省或为空时非中文语言回落到 [notes]。 */
    val notesEn: String = "",
    /** [notes] 的语言：`zh`（默认，兼容旧 manifest）/ `en`。 */
    val notesLang: String = DEFAULT_NOTES_LANG,
) {
    companion object {
        const val DEFAULT_NOTES_LANG = "zh"
    }
}
