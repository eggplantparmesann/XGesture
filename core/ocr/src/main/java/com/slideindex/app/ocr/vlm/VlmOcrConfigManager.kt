package com.slideindex.app.ocr.vlm

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.slideindex.app.ocr.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 云端视觉大模型服务商定义。
 *
 * 收录规则：每一项都必须是**自己训练模型的厂商**。中转站 / 聚合器（如 OpenRouter）
 * 请走 [VlmProvider.CUSTOM] + [VlmCustomPreset]，不占用枚举位——这样枚举的语义才统一，
 * 且每家都有独立 API Key 与独立 baseUrl，用户能随时切换。
 *
 * 不变量：[defaultModel] 必须支持图片输入。云端 OCR 会直接拿它发图，
 * 若填了纯文本模型，用户选中后必然失败；纯文本模型只应出现在 [presetTranslateModels]。
 */
enum class VlmProvider(
    val id: String,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val presetModels: List<String>,
    val defaultTranslateModel: String,
    val presetTranslateModels: List<String>,
    @StringRes val websiteHintRes: Int,
) {
    DASHSCOPE(
        id = "dashscope",
        displayNameRes = R.string.vlm_provider_dashscope_name,
        descriptionRes = R.string.vlm_provider_dashscope_desc,
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-vl-plus",
        presetModels = listOf("qwen-vl-plus", "qwen-vl-max", "qwen-vl-ocr"),
        defaultTranslateModel = "qwen-plus",
        presetTranslateModels = listOf("qwen-plus", "qwen-turbo", "qwen3-max"),
        websiteHintRes = R.string.vlm_provider_dashscope_hint,
    ),
    ZHIPU(
        id = "zhipu",
        displayNameRes = R.string.vlm_provider_zhipu_name,
        descriptionRes = R.string.vlm_provider_zhipu_desc,
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        defaultModel = "glm-4v-plus",
        presetModels = listOf("glm-4v-plus", "glm-4v", "glm-4v-flash"),
        defaultTranslateModel = "glm-4-plus",
        presetTranslateModels = listOf("glm-4-plus", "glm-4-flash", "glm-4-air"),
        websiteHintRes = R.string.vlm_provider_zhipu_hint,
    ),
    SILICONFLOW(
        id = "siliconflow",
        displayNameRes = R.string.vlm_provider_siliconflow_name,
        descriptionRes = R.string.vlm_provider_siliconflow_desc,
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "Qwen/Qwen2.5-VL-72B-Instruct",
        presetModels = listOf(
            "Qwen/Qwen2.5-VL-72B-Instruct",
            "Qwen/Qwen2-VL-72B-Instruct",
            "Qwen/Qwen2-VL-7B-Instruct",
            "THUDM/glm-4v-9b",
        ),
        defaultTranslateModel = "Qwen/Qwen3-32B",
        presetTranslateModels = listOf(
            "Qwen/Qwen3-32B",
            "deepseek-ai/DeepSeek-V3",
            "zai-org/GLM-4.5",
        ),
        websiteHintRes = R.string.vlm_provider_siliconflow_hint,
    ),
    GEMINI(
        id = "gemini",
        displayNameRes = R.string.vlm_provider_gemini_name,
        descriptionRes = R.string.vlm_provider_gemini_desc,
        // Google 官方 OpenAI 兼容层，末尾斜杠与官方示例一致。
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        defaultModel = "gemini-3.8-flash",
        presetModels = listOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.5-pro"),
        defaultTranslateModel = "gemini-3.8-flash",
        presetTranslateModels = listOf("gemini-3.8-flash", "gemini-2.5-flash"),
        websiteHintRes = R.string.vlm_provider_gemini_hint,
    ),
    OPENAI(
        id = "openai",
        displayNameRes = R.string.vlm_provider_openai_name,
        descriptionRes = R.string.vlm_provider_openai_desc,
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5-mini",
        presetModels = listOf("gpt-5-mini", "gpt-5", "gpt-6-luna"),
        defaultTranslateModel = "gpt-5-mini",
        presetTranslateModels = listOf("gpt-5-mini", "gpt-5", "gpt-6-luna"),
        websiteHintRes = R.string.vlm_provider_openai_hint,
    ),
    ANTHROPIC(
        id = "anthropic",
        displayNameRes = R.string.vlm_provider_anthropic_name,
        descriptionRes = R.string.vlm_provider_anthropic_desc,
        // Anthropic 官方 OpenAI SDK 兼容层。官方声明该兼容层非长期/生产级方案。
        defaultBaseUrl = "https://api.anthropic.com/v1/",
        defaultModel = "claude-sonnet-5-5",
        presetModels = listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-4-5-20251001"),
        defaultTranslateModel = "claude-haiku-4-5-20251001",
        presetTranslateModels = listOf(
            "claude-haiku-4-5-20251001",
            "claude-sonnet-5-5",
            "claude-opus-5-5",
        ),
        websiteHintRes = R.string.vlm_provider_anthropic_hint,
    ),
    DEEPSEEK(
        id = "deepseek",
        displayNameRes = R.string.vlm_provider_deepseek_name,
        descriptionRes = R.string.vlm_provider_deepseek_desc,
        // 官方 base_url 为 https://api.deepseek.com，后缀拼 /chat/completions。
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash",
        presetModels = listOf("deepseek-flash", "deepseek-v4-pro"),
        defaultTranslateModel = "deepseek-flash",
        presetTranslateModels = listOf("deepseek-flash", "deepseek-v4-pro"),
        websiteHintRes = R.string.vlm_provider_deepseek_hint,
    ),
    GROQ(
        id = "groq",
        displayNameRes = R.string.vlm_provider_groq_name,
        descriptionRes = R.string.vlm_provider_groq_desc,
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        // 注意：Groq 只有 qwen/qwen3.8-27b 支持图片输入，
        // defaultModel 必须是它，否则云端 OCR 会必然失败。
        defaultModel = "qwen/qwen3.8-27b",
        presetModels = listOf("qwen/qwen3.8-27b", "openai/gpt-oss-120b", "openai/gpt-oss-20b"),
        defaultTranslateModel = "openai/gpt-oss-120b",
        presetTranslateModels = listOf(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "llama-3.3-70b-versatile",
        ),
        websiteHintRes = R.string.vlm_provider_groq_hint,
    ),
    XAI(
        id = "xai",
        displayNameRes = R.string.vlm_provider_xai_name,
        descriptionRes = R.string.vlm_provider_xai_desc,
        // xAI API 域名为 api.x.ai，OpenAI 兼容路径为 /v1。
        // 其 Chat Completions 已被官方标为 legacy（转向 Responses API），但目前仍可用。
        defaultBaseUrl = "https://api.x.ai/v1",
        defaultModel = "grok-4.7",
        presetModels = listOf("grok-4.7", "grok-4.6", "grok-4.5"),
        defaultTranslateModel = "grok-4.7",
        presetTranslateModels = listOf("grok-4.7", "grok-4.3"),
        websiteHintRes = R.string.vlm_provider_xai_hint,
    ),
    CUSTOM(
        id = "custom",
        displayNameRes = R.string.vlm_provider_custom_name,
        descriptionRes = R.string.vlm_provider_custom_desc,
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        presetModels = listOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet"),
        defaultTranslateModel = "gpt-4o-mini",
        presetTranslateModels = listOf("gpt-4o-mini", "gpt-4o", "claude-3-5-sonnet"),
        websiteHintRes = R.string.vlm_provider_custom_hint,
    );

    fun displayName(context: Context): String = context.getString(displayNameRes)

    fun description(context: Context): String = context.getString(descriptionRes)

    fun websiteHint(context: Context): String = context.getString(websiteHintRes)

    companion object {
        fun fromId(id: String): VlmProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DASHSCOPE
    }
}

/** 云端大模型调用凭据（翻译等纯文本场景）。 */
data class CloudLlmEndpointCredentials(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()
}

/**
 * 多模态视觉 OCR 引擎配置管理（支持服务商隔离与独立存储）
 */
@Singleton
class VlmOcrConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_NAME = "vlm_ocr_config"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_TRANSLATE_ACTIVE_PROVIDER = "translate_active_provider"
        private const val KEY_PROMPT = "system_prompt"

        private const val PREFIX_API_KEY = "provider_api_key_"
        private const val PREFIX_BASE_URL = "provider_base_url_"
        private const val PREFIX_MODEL = "provider_model_"
        private const val PREFIX_CUSTOM_MODELS = "provider_custom_models_"
        private const val PREFIX_PROMPT = "provider_prompt_"
        private const val PREFIX_PROMPT_ENABLED = "provider_prompt_enabled_"
        private const val PREFIX_TRANSLATE_MODEL = "provider_translate_model_"
        private const val PREFIX_TRANSLATE_CUSTOM_MODELS = "provider_translate_custom_models_"

        // 遗留旧配置 key（用于平滑迁移）
        private const val LEGACY_KEY_API_KEY = "api_key"
        private const val LEGACY_KEY_BASE_URL = "base_url"
        private const val LEGACY_KEY_MODEL = "model"
        private const val LEGACY_PROMPT_PREFIX = "你是一个专业的学术与数学公式 OCR 识别工具"

        /** 第三代简短默认（i18n 误替换）；升级后回退到当前 [defaultPrompt]。 */
        private fun isThirdGenerationStoredPrompt(stored: String): Boolean {
            val normalized = stored.trim().replace("\r\n", "\n")
            if (normalized.contains("普通文本与段落")) return false
            if (normalized.contains("Plain text and paragraphs")) return false
            if (normalized.contains("通常のテキストと段落")) return false
            return normalized.contains("无法辨认时输出空字符串") ||
                normalized.contains("Output an empty string if unreadable") ||
                normalized.contains("読めない場合は空文字を出力")
        }
    }

    /**
     * 配置版本号：本类任何一次写入都会 +1。
     *
     * 本类直接读写 SharedPreferences，不属于 Compose 的观察体系：改 prefs 不会让界面重组。
     * 界面只要 collect 这个 flow，并在重组时重新读取所需的值，就能拿到最新配置，
     * 而不必依赖"别的写操作恰好触发了重组"——那正是"切换服务商后勾号不刷新、要重进页面才对"
     * 这类 bug 的根源。
     */
    private val _configVersion = MutableStateFlow(0L)
    val configVersion: StateFlow<Long> = _configVersion.asStateFlow()

    private fun notifyConfigChanged() {
        _configVersion.update { it + 1 }
    }

    /**
     * 统一写入入口：写入 SharedPreferences 并通知配置已变更。
     *
     * 所有写入都必须走这里，这样"改了 prefs 却忘了通知界面"在结构上就不可能发生。
     * [prefs] 用 `apply()` 落盘：内存中的值会同步更新，因此紧接着的读取能立即拿到新值。
     */
    private fun writeConfig(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().also { it.block() }.apply()
        notifyConfigChanged()
    }

    private fun defaultPrompt(): String = VlmFormulaOcrEngine.defaultSystemPrompt(context)

    private fun shouldUseDefaultPrompt(stored: String?): Boolean {
        if (stored.isNullOrBlank()) return true
        if (stored.startsWith(LEGACY_PROMPT_PREFIX)) return true
        return isThirdGenerationStoredPrompt(stored)
    }

    init {
        migrateLegacyIfNeeded()
        migrateTranslateActiveProviderIfNeeded()
        migrateProviderPromptFlagsIfNeeded()
    }

    private fun migrateTranslateActiveProviderIfNeeded() {
        if (!prefs.contains(KEY_TRANSLATE_ACTIVE_PROVIDER)) {
            prefs.edit()
                .putString(KEY_TRANSLATE_ACTIVE_PROVIDER, activeProviderId)
                .apply()
        }
    }

    private fun migrateLegacyIfNeeded() {
        val legacyKey = prefs.getString(LEGACY_KEY_API_KEY, null)
        val dashscopeKey = prefs.getString(PREFIX_API_KEY + VlmProvider.DASHSCOPE.id, null)
        if (!legacyKey.isNullOrBlank() && dashscopeKey.isNullOrBlank()) {
            val legacyUrl = prefs.getString(LEGACY_KEY_BASE_URL, VlmProvider.DASHSCOPE.defaultBaseUrl)
            val legacyModel = prefs.getString(LEGACY_KEY_MODEL, VlmProvider.DASHSCOPE.defaultModel)
            prefs.edit()
                .putString(PREFIX_API_KEY + VlmProvider.DASHSCOPE.id, legacyKey)
                .putString(PREFIX_BASE_URL + VlmProvider.DASHSCOPE.id, legacyUrl)
                .putString(PREFIX_MODEL + VlmProvider.DASHSCOPE.id, legacyModel)
                .apply()
        }
    }

    // --- 当前激活服务商 ---
    var activeProviderId: String
        get() = prefs.getString(KEY_ACTIVE_PROVIDER, VlmProvider.DASHSCOPE.id) ?: VlmProvider.DASHSCOPE.id
        set(value) = writeConfig { putString(KEY_ACTIVE_PROVIDER, value) }

    val activeProvider: VlmProvider
        get() = VlmProvider.fromId(activeProviderId)

    fun setActiveProvider(provider: VlmProvider) {
        activeProviderId = provider.id
    }

    // --- 翻译专用激活服务商（与 OCR 视觉激活服务商独立） ---
    var translateActiveProviderId: String
        get() = prefs.getString(KEY_TRANSLATE_ACTIVE_PROVIDER, VlmProvider.DASHSCOPE.id)
            ?: VlmProvider.DASHSCOPE.id
        set(value) = writeConfig { putString(KEY_TRANSLATE_ACTIVE_PROVIDER, value) }

    val translateActiveProvider: VlmProvider
        get() = VlmProvider.fromId(translateActiveProviderId)

    fun setTranslateActiveProvider(provider: VlmProvider) {
        translateActiveProviderId = provider.id
    }

    // --- 单个厂商配置存取 ---
    fun getApiKey(provider: VlmProvider): String =
        prefs.getString(PREFIX_API_KEY + provider.id, "") ?: ""

    fun setApiKey(provider: VlmProvider, key: String) {
        writeConfig { putString(PREFIX_API_KEY + provider.id, key.trim()) }
    }

    fun getBaseUrl(provider: VlmProvider): String =
        prefs.getString(PREFIX_BASE_URL + provider.id, provider.defaultBaseUrl)?.ifBlank { null }
            ?: provider.defaultBaseUrl

    fun setBaseUrl(provider: VlmProvider, url: String) {
        writeConfig { putString(PREFIX_BASE_URL + provider.id, url.trim().ifBlank { provider.defaultBaseUrl }) }
    }

    fun getModel(provider: VlmProvider): String =
        prefs.getString(PREFIX_MODEL + provider.id, provider.defaultModel)?.ifBlank { null }
            ?: provider.defaultModel

    fun setModel(provider: VlmProvider, model: String) {
        val trimmed = model.trim().ifBlank { provider.defaultModel }
        writeConfig { putString(PREFIX_MODEL + provider.id, trimmed) }
        if (trimmed !in provider.presetModels) {
            addCustomModel(provider, trimmed)
        }
    }

    fun getCustomModels(provider: VlmProvider): Set<String> =
        prefs.getStringSet(PREFIX_CUSTOM_MODELS + provider.id, emptySet()) ?: emptySet()

    fun addCustomModel(provider: VlmProvider, model: String) {
        val trimmed = model.trim()
        if (trimmed.isNotBlank() && trimmed !in provider.presetModels) {
            val updated = getCustomModels(provider).toMutableSet()
            updated.add(trimmed)
            writeConfig { putStringSet(PREFIX_CUSTOM_MODELS + provider.id, updated) }
        }
    }

    // --- 云端文本翻译模型（与 OCR 视觉模型分离） ---
    fun getTranslateModel(provider: VlmProvider): String =
        prefs.getString(PREFIX_TRANSLATE_MODEL + provider.id, provider.defaultTranslateModel)
            ?.ifBlank { null }
            ?: provider.defaultTranslateModel

    fun setTranslateModel(provider: VlmProvider, model: String) {
        val trimmed = model.trim().ifBlank { provider.defaultTranslateModel }
        writeConfig { putString(PREFIX_TRANSLATE_MODEL + provider.id, trimmed) }
        if (trimmed !in provider.presetTranslateModels) {
            addTranslateCustomModel(provider, trimmed)
        }
    }

    fun getTranslateCustomModels(provider: VlmProvider): Set<String> =
        prefs.getStringSet(PREFIX_TRANSLATE_CUSTOM_MODELS + provider.id, emptySet()) ?: emptySet()

    fun addTranslateCustomModel(provider: VlmProvider, model: String) {
        val trimmed = model.trim()
        if (trimmed.isNotBlank() && trimmed !in provider.presetTranslateModels) {
            val updated = getTranslateCustomModels(provider).toMutableSet()
            updated.add(trimmed)
            writeConfig { putStringSet(PREFIX_TRANSLATE_CUSTOM_MODELS + provider.id, updated) }
        }
    }

    fun activeTranslateCredentials(): CloudLlmEndpointCredentials {
        val provider = translateActiveProvider
        return CloudLlmEndpointCredentials(
            apiKey = getApiKey(provider),
            baseUrl = getBaseUrl(provider),
            model = getTranslateModel(provider),
        )
    }

    // --- 「自定义端点」一键预设 ---
    // 只作用于 VlmProvider.CUSTOM：填入 baseUrl 与推荐模型，API Key 仍需用户自己提供。

    /** 应用预设到云端 OCR：写入 baseUrl 与该厂商的视觉模型。 */
    fun applyCustomVisionPreset(preset: VlmCustomPreset) {
        setBaseUrl(VlmProvider.CUSTOM, preset.baseUrl)
        setModel(VlmProvider.CUSTOM, preset.visionModel)
    }

    /** 应用预设到云端翻译：写入 baseUrl 与该厂商的文本模型。 */
    fun applyCustomTranslatePreset(preset: VlmCustomPreset) {
        setBaseUrl(VlmProvider.CUSTOM, preset.baseUrl)
        setTranslateModel(VlmProvider.CUSTOM, preset.translateModel)
    }

    /**
     * 判断 [preset] 是否为 [provider] 当前生效的自定义预设。
     * 比较时忽略末尾斜杠，因为 [VlmFormulaOcrEngine] 拼接请求路径前也会做同样处理。
     */
    fun matchesCustomPreset(provider: VlmProvider, preset: VlmCustomPreset): Boolean =
        provider == VlmProvider.CUSTOM &&
            getBaseUrl(provider).trim().trimEnd('/') == preset.baseUrl.trimEnd('/')

    private fun migrateProviderPromptFlagsIfNeeded() {
        VlmProvider.entries.forEach { provider ->
            val enabledKey = PREFIX_PROMPT_ENABLED + provider.id
            if (prefs.contains(enabledKey)) return@forEach
            val hasDraft = !prefs.getString(PREFIX_PROMPT + provider.id, null).isNullOrBlank()
            if (hasDraft) {
                prefs.edit().putBoolean(enabledKey, true).apply()
            }
        }
    }

    // --- 服务商专属提示词：enabled 与 draft 分离存储 ---
    fun isProviderPromptEnabled(provider: VlmProvider): Boolean =
        prefs.getBoolean(PREFIX_PROMPT_ENABLED + provider.id, false)

    fun setProviderPromptEnabled(provider: VlmProvider, enabled: Boolean) {
        writeConfig { putBoolean(PREFIX_PROMPT_ENABLED + provider.id, enabled) }
    }

    fun getProviderPromptDraft(provider: VlmProvider): String =
        prefs.getString(PREFIX_PROMPT + provider.id, null).orEmpty()

    fun setProviderPromptDraft(provider: VlmProvider, draft: String) {
        val trimmed = draft.trim()
        if (trimmed.isBlank()) {
            writeConfig { remove(PREFIX_PROMPT + provider.id) }
        } else {
            writeConfig { putString(PREFIX_PROMPT + provider.id, trimmed) }
        }
    }

    fun setProviderPromptConfig(provider: VlmProvider, enabled: Boolean, draft: String) {
        setProviderPromptEnabled(provider, enabled)
        setProviderPromptDraft(provider, draft)
    }

    /** 兼容旧调用：非空 draft 视为启用专属。 */
    fun getProviderPrompt(provider: VlmProvider): String? =
        getProviderPromptDraft(provider).ifBlank { null }

    /** 兼容旧调用。 */
    fun setProviderPrompt(provider: VlmProvider, customPrompt: String?) {
        if (customPrompt.isNullOrBlank()) {
            setProviderPromptEnabled(provider, false)
        } else {
            setProviderPromptConfig(provider, enabled = true, draft = customPrompt)
        }
    }

    fun getEffectivePrompt(provider: VlmProvider): String {
        if (isProviderPromptEnabled(provider)) {
            getProviderPromptDraft(provider).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return commonPrompt
    }

    fun isCommonPromptCustomized(): Boolean =
        commonPrompt.trim() != defaultPrompt().trim()

    fun isProviderConfigured(provider: VlmProvider): Boolean =
        getApiKey(provider).isNotBlank()

    // --- 向后兼容：直接代理当前激活的服务商 ---
    var apiKey: String
        get() = getApiKey(activeProvider)
        set(value) = setApiKey(activeProvider, value)

    var baseUrl: String
        get() = getBaseUrl(activeProvider)
        set(value) = setBaseUrl(activeProvider, value)

    var model: String
        get() = getModel(activeProvider)
        set(value) = setModel(activeProvider, value)

    val isConfigured: Boolean
        get() = isProviderConfigured(activeProvider)

    // --- 全局通用提示词 Common System Prompt ---
    var commonPrompt: String
        get() {
            val stored = prefs.getString(KEY_PROMPT, null)
            if (shouldUseDefaultPrompt(stored)) {
                return defaultPrompt()
            }
            return stored!!
        }
        set(value) = writeConfig { putString(KEY_PROMPT, value.trim().ifBlank { defaultPrompt() }) }

    // --- 当前激活服务商的生效提示词（支持读写兼容） ---
    var prompt: String
        get() = getEffectivePrompt(activeProvider)
        set(value) {
            commonPrompt = value
        }

    fun resetPromptToDefault() {
        commonPrompt = defaultPrompt()
    }

    fun exportRawJson(): String = VlmOcrConfigBackupCodec.encode(prefs)

    fun importRawJson(raw: String, replaceExisting: Boolean = true) {
        if (raw.isBlank()) return
        val document = VlmOcrConfigBackupCodec.decode(raw)
        // 编解码器直接批量写 prefs，走不了 writeConfig，所以在这里显式通知。
        VlmOcrConfigBackupCodec.apply(prefs, document, replaceExisting)
        notifyConfigChanged()
    }
}

