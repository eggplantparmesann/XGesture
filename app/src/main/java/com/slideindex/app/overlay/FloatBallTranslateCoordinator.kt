package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateDependencyAccess
import com.slideindex.app.translate.TranslateEngine
import com.slideindex.app.translate.TranslateResult
import com.slideindex.app.translate.TranslateTargetResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FloatBallTranslateCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun translate(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?: return

        // 网页回落那条路也要用同一个目标语言，所以提前解析（原来只服务即时浮窗）。
        val targetLang = TranslateTargetResolver.resolve(
            settings.floatBallTranslateTargetLang,
            settings.appUiLanguageTag,
        )

        // 引擎选「本地 App」：译文只能由那个 App 显示，所以无视「即时翻译」开关。
        if (settings.floatBallTranslateEngine == FloatBallTranslateEngine.LOCAL_APP) {
            FloatBallTextPick.translateToApp(
                context = context,
                text = trimmed,
                targetPackage = settings.floatBallTranslateAppPackage,
                targetLang = targetLang,
            )
            return
        }

        if (!settings.floatBallInstantTranslate) {
            FloatBallTextPick.translateText(context, trimmed, targetLang)
            return
        }

        if (!FloatBallPickResultPanel.isShowing) return

        if (FloatBallPickResultPanel.isShowingTranslation()) {
            FloatBallPickResultPanel.restoreFromTranslation()
            return
        }

        val engine = when (settings.floatBallTranslateEngine) {
            FloatBallTranslateEngine.GOOGLE -> TranslateEngine.GOOGLE
            FloatBallTranslateEngine.ML_KIT -> TranslateEngine.ML_KIT
            FloatBallTranslateEngine.CLOUD_LLM -> TranslateEngine.CLOUD_LLM
            // 上面已针对 LOCAL_APP 提前返回，这里只是让 when 穷尽。
            FloatBallTranslateEngine.LOCAL_APP -> return
        }

        FloatBallPickResultPanel.showTranslateLoading()
        scope.launch {
            val service = TranslateDependencyAccess.translateService(context)
            if (service == null) {
                FloatBallPickResultPanel.showTranslateError(context, "translate_unavailable")
                return@launch
            }
            when (val result = service.translate(trimmed, targetLang, engine)) {
                is TranslateResult.Success -> {
                    FloatBallPickResultPanel.showTranslateResult(result.translatedText)
                }
                is TranslateResult.Failure -> {
                    FloatBallPickResultPanel.showTranslateError(
                        context,
                        mapErrorMessage(result.message)
                    )
                }
            }
        }
    }

    private fun mapErrorMessage(code: String): String = when (code) {
        "target_model_not_installed", "source_model_not_installed", "model_download_required" ->
            "mlkit_model_not_installed"
        "translate_engine_not_installed" -> "translate_engine_not_installed"
        "wifi_required" -> "wifi_required"
        "unsupported_target_language" -> "unsupported_language"
        "api_key_not_configured" -> "cloud_api_key_not_configured"
        "cloud_empty" -> "cloud_translate_empty"
        else -> code
    }
}
