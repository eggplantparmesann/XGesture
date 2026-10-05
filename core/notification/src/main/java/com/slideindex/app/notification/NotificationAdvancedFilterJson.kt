package com.slideindex.app.notification

import org.json.JSONArray
import org.json.JSONObject

/** Why an advanced filter JSON document was rejected. */
enum class NotificationAdvancedFilterError {
    /** Not valid JSON, or not a JSON object. */
    INVALID_JSON,

    /** `node` is missing, empty, or not an array of objects. */
    EMPTY_NODES,

    /** `match` is present but is not one of `ANY`, `ALL`, or `NONE`. */
    INVALID_MATCH_TYPE,

    /** An object carries a key the schema does not define; typos would otherwise be ignored. */
    UNKNOWN_KEY,

    /** A node is missing `field`, or names a field the matcher cannot resolve. */
    UNSUPPORTED_FIELD,

    /** A node is missing `regex`, or it is blank. */
    EMPTY_REGEX,

    /** A node's `regex` is not a valid regular expression. */
    INVALID_REGEX,
}

/**
 * A rejected advanced filter document.
 *
 * @param error machine-readable reason, mapped to a localized message by the UI layer.
 * @param detail offending value (field name, match type, or regex), for the error message.
 * @param nodeIndex zero-based index of the offending node, or null when not node-specific.
 */
data class NotificationAdvancedFilterViolation(
    val error: NotificationAdvancedFilterError,
    val detail: String = "",
    val nodeIndex: Int? = null,
) {
    /** A document-level problem rather than a problem with one node. */
    fun isDocumentLevel(): Boolean = nodeIndex == null &&
        (error == NotificationAdvancedFilterError.INVALID_JSON ||
            error == NotificationAdvancedFilterError.EMPTY_NODES ||
            error == NotificationAdvancedFilterError.INVALID_MATCH_TYPE ||
            error == NotificationAdvancedFilterError.UNKNOWN_KEY)
}

/** Result of validating one advanced filter JSON document. */
sealed interface NotificationAdvancedFilterValidation {
    data class Valid(val filter: AdvancedFilter) : NotificationAdvancedFilterValidation

    data class Invalid(
        val violation: NotificationAdvancedFilterViolation,
    ) : NotificationAdvancedFilterValidation
}

/**
 * Strict parser for the advanced filter (JSON) text mode.
 *
 * Parsing is deliberately strict instead of silently degrading: an unknown `field`, an
 * unknown `match` value, or a broken regex previously produced a rule that could never
 * match anything, with no feedback anywhere in the UI. Every rejection carries a
 * [NotificationAdvancedFilterError] so the editor can tell the user what to fix.
 *
 * Semantics of an accepted document:
 * - Each node's regex is evaluated against one notification field (empty when absent).
 * - `invert: true` negates that single node's result.
 * - `match` is `ANY` (at least one node holds), `ALL` (every node holds), or `NONE` (no node
 *   holds). `invert` is applied per node before the `match` aggregation.
 * - Matching is substring-based (`find`), case-insensitive, and DOTALL by default.
 */
object NotificationAdvancedFilterJsonParser {

    private val TOP_LEVEL_KEYS = setOf("match", "node")
    private val NODE_KEYS = setOf("field", "regex", "invert")

    fun parse(raw: String?): NotificationAdvancedFilterValidation {
        if (raw.isNullOrBlank()) {
            return invalid(NotificationAdvancedFilterError.INVALID_JSON)
        }

        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return invalid(NotificationAdvancedFilterError.INVALID_JSON)

        val unknownRootKey = root.keys().asSequence()
            .firstOrNull { it !in TOP_LEVEL_KEYS }
        if (unknownRootKey != null) {
            return invalid(NotificationAdvancedFilterError.UNKNOWN_KEY, detail = unknownRootKey)
        }

        val rawMatchType = root.optString("match", NotificationRuleAdvancedMatchTypes.ANY)
        val matchType = rawMatchType.trim().ifBlank { NotificationRuleAdvancedMatchTypes.ANY }
        if (!NotificationRuleAdvancedMatchTypes.isSupported(matchType.uppercase())) {
            return invalid(
                NotificationAdvancedFilterError.INVALID_MATCH_TYPE,
                detail = rawMatchType,
            )
        }

        val nodesArray: JSONArray = root.optJSONArray("node")
            ?: return invalid(NotificationAdvancedFilterError.EMPTY_NODES)
        if (nodesArray.length() == 0) {
            return invalid(NotificationAdvancedFilterError.EMPTY_NODES)
        }

        val nodes = ArrayList<AdvancedFilterNode>(nodesArray.length())
        for (index in 0 until nodesArray.length()) {
            val node = nodesArray.optJSONObject(index)
                ?: return invalid(NotificationAdvancedFilterError.EMPTY_NODES)

            val unknownNodeKey = node.keys().asSequence()
                .firstOrNull { it !in NODE_KEYS }
            if (unknownNodeKey != null) {
                return invalid(
                    NotificationAdvancedFilterError.UNKNOWN_KEY,
                    detail = unknownNodeKey,
                    nodeIndex = index,
                )
            }

            val field = node.optString("field").trim()
            if (field.isEmpty() || !NotificationRuleFieldNames.isSupported(field)) {
                return invalid(
                    NotificationAdvancedFilterError.UNSUPPORTED_FIELD,
                    detail = field,
                    nodeIndex = index,
                )
            }

            val regex = node.optString("regex")
            if (regex.isBlank()) {
                return invalid(
                    NotificationAdvancedFilterError.EMPTY_REGEX,
                    nodeIndex = index,
                )
            }

            if (!isValidRegex(regex)) {
                return invalid(
                    NotificationAdvancedFilterError.INVALID_REGEX,
                    detail = regex,
                    nodeIndex = index,
                )
            }

            nodes.add(
                AdvancedFilterNode(
                    field = field,
                    regex = regex,
                    invert = node.optBoolean("invert", false),
                ),
            )
        }

        return NotificationAdvancedFilterValidation.Valid(
            AdvancedFilter(matchType = matchType.uppercase(), nodes = nodes),
        )
    }

    private fun isValidRegex(regex: String): Boolean = runCatching { Regex(regex) }.isSuccess

    private fun invalid(
        error: NotificationAdvancedFilterError,
        detail: String = "",
        nodeIndex: Int? = null,
    ): NotificationAdvancedFilterValidation.Invalid =
        NotificationAdvancedFilterValidation.Invalid(
            NotificationAdvancedFilterViolation(error = error, detail = detail, nodeIndex = nodeIndex),
        )
}
