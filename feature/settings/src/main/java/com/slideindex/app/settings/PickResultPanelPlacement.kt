package com.slideindex.app.settings

/** 取词面板在屏幕上的落位方式（与面板内容样式 [PickResultPanelStyle] 正交）。 */
enum class PickResultPanelPlacement(val storageKey: String) {
    /** 贴屏幕底部滑入（默认）。 */
    BOTTOM_DOCKED("bottom_docked"),
    /** 屏幕中间的卡片。 */
    CENTER("center"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PickResultPanelPlacement =
            when (key) {
                "center" -> CENTER
                else -> BOTTOM_DOCKED
            }
    }
}
