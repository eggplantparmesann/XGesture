package com.slideindex.app.settings

enum class FloatBallTranslateEngine(val storageKey: String) {
    GOOGLE("google"),
    ML_KIT("mlkit"),
    CLOUD_LLM("cloud"),

    /** 把取词文本交给用户指定的本地翻译 App（优先 `ACTION_PROCESS_TEXT`，其次 `ACTION_SEND`）。 */
    LOCAL_APP("local_app"),
    ;

    companion object {
        fun fromStorageKey(key: String?): FloatBallTranslateEngine =
            entries.firstOrNull { it.storageKey == key } ?: GOOGLE
    }
}
