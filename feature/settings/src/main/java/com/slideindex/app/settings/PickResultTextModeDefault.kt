package com.slideindex.app.settings

/** 取词面板文本区「点词」进入面板时的默认状态。 */
enum class PickResultTextModeDefault(val storageKey: String) {
    /** 记住上次使用状态：下次进入面板 == 上次退出面板时的状态。 */
    REMEMBER_LAST("remember_last"),
    /** 始终开启：每次进入面板都为点词模式（用户仍可在面板内切换，仅本次有效）。 */
    ALWAYS_ON("always_on"),
    /** 始终关闭：每次进入面板都为普通文本（长按拖选），用户仍可在面板内切到点词。 */
    ALWAYS_OFF("always_off"),
    ;

    companion object {
        /** 老用户没有该键（或键值非法）时必须回落默认值 [ALWAYS_ON]，保持原有行为不变。 */
        fun fromStorageKey(key: String?): PickResultTextModeDefault =
            when (key) {
                "remember_last" -> REMEMBER_LAST
                "always_off" -> ALWAYS_OFF
                else -> ALWAYS_ON
            }
    }
}
