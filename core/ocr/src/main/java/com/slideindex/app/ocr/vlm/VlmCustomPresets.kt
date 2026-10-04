package com.slideindex.app.ocr.vlm

/**
 * 「自定义端点」([VlmProvider.CUSTOM]) 的一键预设。
 *
 * 这里只放**中转 / 聚合 / 自建**类端点：它们转卖别家的模型，并非模型厂商本身，
 * 因此不占用 [VlmProvider] 的枚举位。想要"每家独立 API Key、可随时切换"的正式厂商，
 * 请直接使用 [VlmProvider] 里的枚举项。
 *
 * 品牌名是专有名词，各语言写法一致，不做本地化；只需要维护地址与推荐模型。
 *
 * 所有 baseUrl 都是 OpenAI 兼容端点，请求路径由 [VlmFormulaOcrEngine] 拼接为
 * `"$baseUrl/chat/completions"`（拼接前会自动去掉末尾斜杠），所以末尾斜杠写不写都可以。
 *
 * 注意：模型名由各厂商随时更新，这里的值只是「推荐起始值」。UI 会提示用户以从厂商实时拉取的
 * 模型列表为准，所以即便某个名字过期也不会让功能失效——用户仍可手填或从列表中选择。
 */
enum class VlmCustomPreset(
    /** 专有名词，直接展示，不做本地化。 */
    val displayName: String,
    val baseUrl: String,
    /** 云端 OCR 使用的视觉模型。 */
    val visionModel: String,
    /** 云端翻译使用的纯文本模型。 */
    val translateModel: String,
) {
    OPENROUTER(
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        visionModel = "openai/gpt-5-mini",
        translateModel = "openai/gpt-5-mini",
    ),
    ;

    companion object {
        /**
         * 云端 OCR 可用预设。
         * 聚合器可以路由到视觉模型，因此与翻译共用同一份列表。
         */
        fun visionPresets(): List<VlmCustomPreset> = entries.toList()

        /** 云端翻译可用预设。 */
        fun translatePresets(): List<VlmCustomPreset> = entries.toList()
    }
}
