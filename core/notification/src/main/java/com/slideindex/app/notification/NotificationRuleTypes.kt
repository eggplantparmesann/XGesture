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

enum class ScreenMode {
    BOTH,
    ON,
    OFF,
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

object NotificationRuleChargeMask {
    const val BATTERY = 1
    const val AC = 2
    const val USB = 4
    const val WIRED = 6
    const val WIRELESS = 8
    const val ALL = 15
}

object NotificationRuleWeekDays {
    val ALL: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
}
