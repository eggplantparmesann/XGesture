package com.slideindex.app.overlay.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.barcode.ZxingBarcodeScanner
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.service.RegionalScreenshotOcr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SmartScreenshotOcr {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun pickTextFromBitmap(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null
    ) {
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        val settings = OverlayDependencyAccess.overlayDependencies(hostContext)
            ?.settingsRepository
            ?.readSnapshot()
        val modelId = settings?.floatBallOcrModelId.orEmpty()
        val isModelInstalled = modelId.isNotBlank() &&
            OcrDependencyAccess.modelRepository(hostContext)?.isInstalled(modelId) == true

        if (!isModelInstalled) {
            Toast.makeText(hostContext, R.string.share_image_ocr_model_required, Toast.LENGTH_LONG).show()
            return
        }

        val panelScreenshot = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        val ocrBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return

        FloatBallPickResultPanel.showResult(
            context = hostContext,
            anchorX = screenRect?.centerX()?.toFloat() ?: (hostContext.resources.displayMetrics.widthPixels / 2f),
            anchorY = screenRect?.centerY()?.toFloat() ?: (hostContext.resources.displayMetrics.heightPixels / 2f),
            result = FloatBallPickResult(
                a11yText = null,
                ocrText = null,
                screenshot = panelScreenshot,
                screenRect = screenRect,
                layoutMeta = layoutMeta,
                activeSource = PickResultTextSource.OCR,
                ocrAvailable = true,
                ocrPending = true,
                ocrPreferSwitchOnComplete = true,
                a11ySourceEnabled = false,
                contentOrigin = PickResultContentOrigin.SCREEN_PICK
            ),
            initialTextMode = PickResultTextMode.WORD_TAP
        )

        scope.launch(Dispatchers.IO) {
            val barcodeResults = runCatching {
                ZxingBarcodeScanner.scanBitmap(panelScreenshot)
            }.getOrDefault(emptyList())
            if (barcodeResults.isNotEmpty()) {
                FloatBallPickResultPanel.updateBarcodeResults(barcodeResults)
            }

            val ocrResult = runCatching {
                RegionalScreenshotOcr.recognizeBitmapPublic(hostContext, modelId, ocrBitmap)
            }.getOrElse {
                com.slideindex.app.ocr.OcrRecognizeResult.Failure(
                    hostContext.getString(
                        R.string.ocr_error_recognition_failed,
                        it.localizedMessage ?: it.message ?: hostContext.getString(R.string.ocr_error_unknown)
                    )
                )
            }
            ocrBitmap.recycle()

            withContext(Dispatchers.Main.immediate) {
                when (ocrResult) {
                    is com.slideindex.app.ocr.OcrRecognizeResult.Success -> {
                        val text = ocrResult.text.trim()
                        FloatBallPickResultPanel.updateOcrText(
                            ocrText = text,
                            switchToOcr = true,
                            initialTextMode = PickResultTextMode.WORD_TAP
                        )
                    }
                    is com.slideindex.app.ocr.OcrRecognizeResult.Failure -> {
                        FloatBallPickResultPanel.finishOcrPending()
                        FloatBallPickResultPanel.showOcrError(hostContext, ocrResult.reason)
                    }
                }
            }
        }
    }
}
