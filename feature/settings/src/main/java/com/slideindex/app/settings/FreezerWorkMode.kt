package com.slideindex.app.settings

/**
 * 冰箱工作模式：决定冰箱面板底部按钮默认执行哪一种批量动作。
 *
 * - [FREEZE] 冻结：停用应用，桌面图标从桌面消失。
 * - [PAUSE] 暂停：应用挂起，桌面图标保留但变灰。
 *
 * 模式只决定「默认动词」，不会改变列表中已有应用的状态。
 */
enum class FreezerWorkMode(val id: Int) {
    FREEZE(0),
    PAUSE(1),
    ;

    val isPause: Boolean get() = this == PAUSE

    companion object {
        val DEFAULT: FreezerWorkMode = FREEZE

        fun fromId(id: Int?): FreezerWorkMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
