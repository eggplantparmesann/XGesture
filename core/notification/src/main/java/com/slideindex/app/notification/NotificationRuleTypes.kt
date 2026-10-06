package com.slideindex.app.notification

enum class AppMatchMode {
    INCLUDE,
    EXCLUDE,
    ALL,
}

enum class TextMatchMode {
    ALL,
    CONTAIN_ANY,
    NOT_CONTAIN_ANY,
    CONTAIN_ALL,
    NOT_CONTAIN_ALL,
    CONTAIN_AND_NOT_CONTAIN,
    REGEX,
    ADVANCED,
}

/**
 * 屏幕状态条件。
 *
 * [ANY] 表示不限制。历史版本用两个勾选框（亮屏时 / 熄屏时）表达，而「两个都勾」与「两个都不勾」
 * 都落到同一个值上，保存后再回显时无法区分，编辑页会凭空勾上未选中的项；因此编辑页改为
 * 「不限 / 亮屏时 / 熄屏时」三选一，[ANY] 成为一个可以明确选中的选项。
 */
enum class ScreenMode {
    ANY,
    ON,
    OFF,
    ;

    companion object {
        /** 历史数据里表示不限制的名称（旧枚举常量 `BOTH`）。 */
        private const val LEGACY_UNRESTRICTED_NAME = "BOTH"

        /** 解析持久化名称；历史 "BOTH" 与未知名称都按不限制处理。 */
        fun fromPersistedName(name: String): ScreenMode = when (name) {
            ANY.name, LEGACY_UNRESTRICTED_NAME -> ANY
            ON.name -> ON
            OFF.name -> OFF
            else -> ANY
        }
    }
}

enum class NotificationRuleActionType {
    HIDE,
    MUTE,
    LATER,
    REPLACE,
    CHANGE_SOUND,
    CALL_NOTIFY,
    TTS,
    CLICK_BUTTON,
    OPEN,
    WEBHOOK,
}

data class AppTarget(
    val packageName: String,
    val userId: Int = 0,
)

data class AdvancedFilterNode(
    val field: String,
    val regex: String,
    /** When true the node is satisfied by the regex NOT matching the field value. */
    val invert: Boolean = false,
)

data class AdvancedFilter(
    val matchType: String = "ALL",
    val nodes: List<AdvancedFilterNode> = emptyList(),
)

/**
 * The single source of truth for the `field` values accepted by advanced filter JSON.
 * [NotificationRuleFieldExtractor.fromSbn] must be able to produce every key listed here,
 * otherwise the editor would accept a field the matcher can never resolve.
 */
object NotificationRuleFieldNames {
    const val PACKAGE_NAME = "packageName"
    const val TITLE = "title"
    const val TEXT = "text"
    const val SUB_TEXT = "subText"
    const val CHANNEL_ID = "channelId"
    const val CATEGORY = "category"
    const val KEY = "key"

    val ALL: List<String> = listOf(
        PACKAGE_NAME,
        TITLE,
        TEXT,
        SUB_TEXT,
        CHANNEL_ID,
        CATEGORY,
        KEY,
    )

    fun isSupported(field: String): Boolean = field in ALL
}

/**
 * `match` values accepted by advanced filter JSON.
 *
 * `ALL` is kept as an explicit value for backward compatibility: the original matcher treated
 * any value other than `ANY`/`NONE` as "every node must hold" and shipped a default of `ALL`,
 * so saved rules use it.
 */
object NotificationRuleAdvancedMatchTypes {
    const val ALL = "ALL"
    const val ANY = "ANY"
    const val NONE = "NONE"

    val ALL_VALUES: List<String> = listOf(ANY, ALL, NONE)

    fun isSupported(matchType: String): Boolean = matchType in ALL_VALUES
}

data class RuleActionEntry(
    val type: NotificationRuleActionType,
    val delayTimeMs: Long = 0,
    val includeOngoing: Boolean = false,
    val laterTimesMs: List<Int> = emptyList(),
    val soundUri: String? = null,
    val replaceTitle: String? = null,
    val replaceMessage: String? = null,
    val buttonNames: List<String> = emptyList(),
    val buttonSemantic: Int = 0,
    val ttsTemplate: String? = null,
    val ttsBypassDnd: Boolean = true,
    val webhookUrl: String? = null,
    val webhookMethod: Int = 1,
    val webhookHeaders: String? = null,
    val webhookBody: String? = null,
    val webhookDistinct: Boolean = true,
    val notifyScreenOn: Int = 0,
    val notifyScreenOff: Int = 0,
)

/**
 * 充电状态位掩码。
 *
 * 当前充电方式必然是电池、有线、无线三者之一，因此三者全选（[UNRESTRICTED]）恒成立，与「不限制」
 * 完全等价；历史数据也用该值表示不限制，两者不再区分。编辑页把 [UNRESTRICTED] 一律回显为三项都
 * 未勾选，保存时未勾选与全勾都写回 [UNRESTRICTED]，从而保证「保存 → 重新进入」不再勾上未选中的项。
 */
object NotificationRuleChargeMask {
    const val BATTERY = 1
    const val AC = 2
    const val USB = 4

    /** 有线充电：AC 或 USB。 */
    const val WIRED = 6
    const val WIRELESS = 8

    /** 不限制充电状态：三种方式全选的位值。 */
    const val UNRESTRICTED = 15

    /** 外部数据里的 0 同样按不限制处理，避免规则静默永不命中。 */
    fun isUnrestricted(mask: Int): Boolean =
        mask == 0 || (mask and UNRESTRICTED) == UNRESTRICTED

    /** 把不限制的两种等价写法（0、三位全选）收敛为 [UNRESTRICTED]，其余位掩码原样保留。 */
    fun canonical(mask: Int): Int = if (isUnrestricted(mask)) UNRESTRICTED else mask
}

object NotificationRuleWeekDays {
    val ALL: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
}
