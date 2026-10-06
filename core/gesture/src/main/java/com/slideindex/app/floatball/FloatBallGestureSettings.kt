package com.slideindex.app.floatball

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.launcher.QuickLauncherItemCodec

/** 悬浮球可配置手势类型（参考 FooView）。 */
enum class FloatBallGestureType(val id: Int) {
    SWIPE_UP_SHORT(0),
    SWIPE_DOWN_SHORT(1),
    SWIPE_DOWN_LONG(2),
    SWIPE_SIDE_SHORT(3),
    SWIPE_SIDE_LONG(4),
    SINGLE_TAP(5),
    DOUBLE_TAP(6),
    LONG_PRESS(7),
    SWIPE_UP_LONG(8),
    SWIPE_SIDE_RETURN(9),
    SWIPE_UP_RETURN(10),
    SWIPE_DOWN_RETURN(11),
    /** 首段下滑达标后，第二段再朝屏幕内侧滑出（混合手势）。 */
    SWIPE_DOWN_IN(12),
    /** 首段上滑达标后，第二段再朝屏幕内侧滑出（混合手势）。 */
    SWIPE_UP_IN(13),
    /** 首段朝屏幕内侧滑出后，第二段再下滑（混合手势）。 */
    SWIPE_IN_DOWN(14),
    /** 首段朝屏幕内侧滑出后，第二段再上滑（混合手势）。 */
    SWIPE_IN_UP(15),
    ;

    val isReturnGesture: Boolean
        get() = this == SWIPE_SIDE_RETURN || this == SWIPE_UP_RETURN || this == SWIPE_DOWN_RETURN

    companion object {
        fun fromId(id: Int): FloatBallGestureType? = entries.firstOrNull { it.id == id }

        /** 设置页展示顺序（由 [FloatBallGestureGroup] 分组展平而来，避免两处顺序各写一份）。 */
        fun settingsDisplayOrder(): List<FloatBallGestureType> =
            FloatBallGestureGroup.displayOrder.flatMap { it.types }
    }
}

/** 悬浮球手势设置页的分组（枚举顺序即展示顺序）。 */
enum class FloatBallGestureGroup(val types: List<FloatBallGestureType>) {
    /** 下滑起手：短/长/后返回/再向内滑。 */
    DOWN_SWIPE(
        listOf(
            FloatBallGestureType.SWIPE_DOWN_SHORT,
            FloatBallGestureType.SWIPE_DOWN_LONG,
            FloatBallGestureType.SWIPE_DOWN_RETURN,
            FloatBallGestureType.SWIPE_DOWN_IN,
        ),
    ),
    /** 上滑起手：短/长/后返回/再向内滑。 */
    UP_SWIPE(
        listOf(
            FloatBallGestureType.SWIPE_UP_SHORT,
            FloatBallGestureType.SWIPE_UP_LONG,
            FloatBallGestureType.SWIPE_UP_RETURN,
            FloatBallGestureType.SWIPE_UP_IN,
        ),
    ),
    /** 侧滑起手：短/长/后返回，以及先向内再上/下滑。 */
    SIDE_SWIPE(
        listOf(
            FloatBallGestureType.SWIPE_SIDE_SHORT,
            FloatBallGestureType.SWIPE_SIDE_LONG,
            FloatBallGestureType.SWIPE_SIDE_RETURN,
            FloatBallGestureType.SWIPE_IN_DOWN,
            FloatBallGestureType.SWIPE_IN_UP,
        ),
    ),
    /** 点击类：单击/双击/长按。 */
    TAP(
        listOf(
            FloatBallGestureType.SINGLE_TAP,
            FloatBallGestureType.DOUBLE_TAP,
            FloatBallGestureType.LONG_PRESS,
        ),
    ),
    ;

    companion object {
        val displayOrder: List<FloatBallGestureGroup> = entries.toList()
    }
}

object FloatBallGestureCodec {
    private const val SEP = "\u001E"

    fun encode(type: FloatBallGestureType, action: GestureAction): String =
        "${type.id}$SEP${QuickLauncherItemCodec.encodeActionPayload(sanitizeAction(action))}"

    fun decode(raw: String): Pair<FloatBallGestureType, GestureAction>? {
        val index = raw.indexOf(SEP)
        if (index <= 0) return null
        val type = FloatBallGestureType.fromId(raw.substring(0, index).toIntOrNull() ?: return null)
            ?: return null
        val action = QuickLauncherItemCodec.parseActionPayload(raw.substring(index + 1))
            ?.let(::sanitizeAction)
            ?: return null
        return type to action
    }

    fun encodeAll(actions: Map<FloatBallGestureType, GestureAction>): Set<String> =
        actions.map { (type, action) -> encode(type, sanitizeAction(action)) }.toSet()

    private fun sanitizeAction(action: GestureAction): GestureAction =
        action.sanitizeForSlotPicker(SlotPickerKind.OverlayTap)

    fun decodeAll(raw: Set<String>): Map<FloatBallGestureType, GestureAction> =
        raw.mapNotNull { decode(it) }.toMap()

    fun defaultActions(): Map<FloatBallGestureType, GestureAction> = mapOf(
        FloatBallGestureType.SWIPE_UP_SHORT to GestureAction.None,
        FloatBallGestureType.SWIPE_DOWN_SHORT to GestureAction.Recents,
        FloatBallGestureType.SWIPE_DOWN_LONG to GestureAction.OpenNotifications,
        FloatBallGestureType.SWIPE_DOWN_RETURN to GestureAction.None,
        FloatBallGestureType.SWIPE_UP_LONG to GestureAction.StashPanel,
        FloatBallGestureType.SWIPE_UP_RETURN to GestureAction.None,
        FloatBallGestureType.SWIPE_DOWN_IN to GestureAction.None,
        FloatBallGestureType.SWIPE_UP_IN to GestureAction.None,
        FloatBallGestureType.SWIPE_IN_DOWN to GestureAction.None,
        FloatBallGestureType.SWIPE_IN_UP to GestureAction.None,
        FloatBallGestureType.SWIPE_SIDE_SHORT to GestureAction.Back,
        FloatBallGestureType.SWIPE_SIDE_LONG to GestureAction.Back,
        FloatBallGestureType.SWIPE_SIDE_RETURN to GestureAction.None,
        FloatBallGestureType.SINGLE_TAP to GestureAction.ClickPassthrough,
        FloatBallGestureType.DOUBLE_TAP to GestureAction.None,
        FloatBallGestureType.LONG_PRESS to GestureAction.RingLauncher,
    )
}
