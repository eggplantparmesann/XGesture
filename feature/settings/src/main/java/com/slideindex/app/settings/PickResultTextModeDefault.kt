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
        /**
         * 老用户没有该键（或键值非法）时回落默认值 [REMEMBER_LAST]。
         *
         * 注意：选 [REMEMBER_LAST] 并不改变老用户的初始观感 —— 没有任何"上次状态"记录时，
         * `resolvePickResultEnterTextMode` 会回落为 WORD_TAP（点词开启）；只有用户手动切换过之后
         * 才会真正"记住"。
         */
        fun fromStorageKey(key: String?): PickResultTextModeDefault =
            when (key) {
                // 用 storageKey 常量而非字面量：避免将来改键值/加选项时漏掉某个分支
                // （曾踩过：ALWAYS_ON 原本靠 else 命中，把 else 改成 REMEMBER_LAST 后
                //  "always_on" 失去归属，往返测试立刻失败）。
                REMEMBER_LAST.storageKey -> REMEMBER_LAST
                ALWAYS_ON.storageKey -> ALWAYS_ON
                ALWAYS_OFF.storageKey -> ALWAYS_OFF
                else -> REMEMBER_LAST
            }
    }
}
