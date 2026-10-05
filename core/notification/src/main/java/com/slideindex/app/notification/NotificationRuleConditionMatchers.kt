package com.slideindex.app.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

internal object NotificationRuleFieldExtractor {
    fun combinedText(title: String, text: String, subText: String = ""): String {
        return listOf(title, text, subText).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun fromSbn(sbn: StatusBarNotification): Map<String, String> {
        val notification = sbn.notification ?: return emptyMap()
        val extras = notification.extras ?: return emptyMap()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val content = text.ifBlank { bigText }
        return mapOf(
            NotificationRuleFieldNames.PACKAGE_NAME to sbn.packageName,
            NotificationRuleFieldNames.TITLE to title,
            NotificationRuleFieldNames.TEXT to content,
            NotificationRuleFieldNames.SUB_TEXT to subText,
            NotificationRuleFieldNames.CHANNEL_ID to
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else ""),
            NotificationRuleFieldNames.CATEGORY to (notification.category.orEmpty()),
            NotificationRuleFieldNames.KEY to sbn.key,
        )
    }
}

internal object NotificationRuleTextMatcher {
    private const val TAG = "NotificationRule"

    private val regexCache = ConcurrentHashMap<String, Regex>()
    private val patternCache = ConcurrentHashMap<String, Pattern>()

    fun matches(
        rule: NotificationFilterRule,
        combinedText: String,
        sbn: StatusBarNotification?,
        titleField: String = "",
        textField: String = combinedText,
        subTextField: String = "",
    ): Boolean {
        val normalized = rule.normalized()
        val text = combinedText
        val ignoreCase = normalized.ignoreCase
        return when (normalized.textMode) {
            TextMatchMode.ALL -> true
            TextMatchMode.CONTAIN_ANY -> normalized.keywords.any { contains(text, it, ignoreCase) }
            TextMatchMode.NOT_CONTAIN_ANY -> normalized.keywords.none { contains(text, it, ignoreCase) }
            TextMatchMode.CONTAIN_ALL -> normalized.keywords.isNotEmpty() &&
                normalized.keywords.all { contains(text, it, ignoreCase) }
            TextMatchMode.NOT_CONTAIN_ALL -> normalized.keywords.isNotEmpty() &&
                !normalized.keywords.all { contains(text, it, ignoreCase) }
            TextMatchMode.CONTAIN_AND_NOT_CONTAIN -> {
                val include = normalized.keywords.any { contains(text, it, ignoreCase) }
                val exclude = normalized.keywordsExclude.any { contains(text, it, ignoreCase) }
                include && !exclude
            }
            TextMatchMode.REGEX -> {
                val pattern = normalized.regex?.takeIf { it.isNotBlank() } ?: return false
                runCatching {
                    cachedRegex(pattern, ignoreCase).containsMatchIn(text)
                }.getOrDefault(false)
            }
            TextMatchMode.ADVANCED -> matchesAdvanced(
                json = normalized.advancedFilterJson,
                sbn = sbn,
                titleField = titleField,
                textField = textField,
                subTextField = subTextField,
            )
        }
    }

    private fun contains(text: String, keyword: String, ignoreCase: Boolean): Boolean {
        if (keyword.isBlank()) return false
        if (text.isBlank()) return false
        return text.contains(keyword, ignoreCase = ignoreCase)
    }

    private fun matchesAdvanced(
        json: String?,
        sbn: StatusBarNotification?,
        titleField: String,
        textField: String,
        subTextField: String,
    ): Boolean {
        val filter = when (val parsed = NotificationAdvancedFilterJsonParser.parse(json)) {
            is NotificationAdvancedFilterValidation.Invalid -> {
                Log.w(TAG, "advanced filter JSON rejected: ${parsed.violation.error} " +
                    "detail='${parsed.violation.detail}' node=${parsed.violation.nodeIndex}")
                return false
            }
            is NotificationAdvancedFilterValidation.Valid -> parsed.filter
        }

        val fields = if (sbn != null) {
            NotificationRuleFieldExtractor.fromSbn(sbn)
        } else {
            // History items and previews have no StatusBarNotification; locate fields from
            // the already extracted values so ADVANCED behaves the same on both paths.
            mapOf(
                NotificationRuleFieldNames.TITLE to titleField,
                NotificationRuleFieldNames.TEXT to textField,
                NotificationRuleFieldNames.SUB_TEXT to subTextField,
            )
        }

        val results = filter.nodes.map { node ->
            val value = fields[node.field].orEmpty()
            val matched = runCatching {
                cachedPattern(node.regex).matcher(value).find()
            }.getOrDefault(false)
            if (node.invert) !matched else matched
        }
        return when (filter.matchType.uppercase()) {
            NotificationRuleAdvancedMatchTypes.ANY -> results.any { it }
            NotificationRuleAdvancedMatchTypes.NONE -> results.none { it }
            else -> results.all { it }
        }
    }

    private fun cachedRegex(pattern: String, ignoreCase: Boolean): Regex {
        val key = if (ignoreCase) "i:$pattern" else "n:$pattern"
        return regexCache.getOrPut(key) {
            Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }
    }

    private fun cachedPattern(regex: String): Pattern {
        return patternCache.getOrPut(regex) {
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        }
    }
}

internal object NotificationRuleDeviceMatcher {
    fun matchesTime(rule: NotificationFilterRule, timestampMs: Long): Boolean {
        val normalized = rule.normalized()
        if (!matchesTimeRange(normalized.timeStartMs, normalized.timeEndMs, timestampMs)) return false
        return matchesWeekDay(normalized.weekDays, timestampMs)
    }

    fun matchesScreen(context: Context, rule: NotificationFilterRule): Boolean {
        return when (rule.normalized().screenMode) {
            ScreenMode.BOTH -> true
            ScreenMode.ON -> isScreenOn(context)
            ScreenMode.OFF -> !isScreenOn(context)
        }
    }

    fun matchesCharge(context: Context, rule: NotificationFilterRule): Boolean {
        val mask = rule.normalized().chargeMask
        if (mask == NotificationRuleChargeMask.ALL) return true
        val pluggedMask = currentChargeMask(context)
        return pluggedMask and mask != 0
    }

    private fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isInteractive
    }

    private fun currentChargeMask(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return if (plugged != 0) plugged shl 1 else NotificationRuleChargeMask.BATTERY
    }

    private fun matchesTimeRange(startMs: Int, endMs: Int, timestampMs: Long): Boolean {
        if (startMs == endMs) return true
        val offset = ((timestampMs + TimeZone.getDefault().getOffset(timestampMs)) % 86_400_000L).toInt()
        return if (startMs < endMs) {
            offset in startMs..endMs
        } else {
            offset >= startMs || offset <= endMs
        }
    }

    private fun matchesWeekDay(days: Set<Int>, timestampMs: Long): Boolean {
        if (days.isEmpty() || days.size >= 7) return true
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestampMs
        return days.contains(calendar.get(Calendar.DAY_OF_WEEK))
    }
}

internal object NotificationRuleAppMatcher {
    fun matches(rule: NotificationFilterRule, packageName: String, userId: Int): Boolean {
        val normalized = rule.normalized()
        return when (normalized.appMode) {
            AppMatchMode.ALL -> true
            AppMatchMode.INCLUDE -> normalized.appTargets.any {
                it.packageName == packageName && (it.userId == 0 || it.userId == userId)
            }
            AppMatchMode.EXCLUDE -> normalized.appTargets.none {
                it.packageName == packageName && (it.userId == 0 || it.userId == userId)
            }
        }
    }
}
