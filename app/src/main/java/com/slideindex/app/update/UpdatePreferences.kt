package com.slideindex.app.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateState(
    val latestVersion: String = "",
    val notes: String = "",
    /** 英文更新说明（发版脚本生成）；非中文语言优先用它，缺失时回落 [notes]。 */
    val notesEn: String = "",
    /** [notes] 的语言：`zh`（默认，兼容旧缓存）/ `en`。读取时由选择逻辑结合系统语言决定。 */
    val notesLang: String = UpdateManifest.DEFAULT_NOTES_LANG,
    val apkUrl: String = "",
    val apkSize: Long = 0L,
    val lastCheckSuccessTime: Long = 0L,
    val lastCheckAttemptTime: Long = 0L,
    val nextRetryTime: Long = 0L,
)

@Serializable
data class UpdatePreferences(
    val state: UpdateState = UpdateState(),
    val ignoredUpdateVersion: String = "",
    val autoCheckUpdate: Boolean = true,
    val notificationPermissionRequested: Boolean = false,
)
