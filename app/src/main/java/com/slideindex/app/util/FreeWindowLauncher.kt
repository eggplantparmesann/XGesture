package com.slideindex.app.util

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.isLandscapeConfiguration
import com.slideindex.app.settings.resolvedFreeWindowLayout
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.usesNubiaFreeformIdentifier

import android.content.ComponentName
import com.slideindex.app.service.FreeWindowShareProxyActivity
import com.slideindex.app.settings.FreeWindowMode
import kotlin.math.roundToInt

object FreeWindowLauncher {
    private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
    private const val NUBIA_FREEFORM_INTENT_IDENTIFIER = "_WindowReply"
    private const val HUAWEI_FREEFORM_STACK_ID = 2
    private const val MIUI_PORTRAIT_SCALE = 0.7f
    private const val MIUI_LANDSCAPE_SCALE = 0.555f

    fun launch(context: Context, intent: Intent, settings: AppSettings, fullscreen: Boolean) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!fullscreen && settings.freeWindowEnabled) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (fullscreen || !settings.freeWindowEnabled) {
            context.startActivity(intent)
            return
        }

        val mode = settings.resolvedFreeWindowMode()
        if (mode.usesNubiaFreeformIdentifier()) {
            launchNubiaFreeform(context, intent)
            return
        }

        if (mode == FreeWindowMode.ORIGINOS) {
            if (launchOriginOsShareProxy(context, intent)) {
                return
            }
        }

        val bundle = launchOptionsBundle(context, settings) ?: Bundle()
        runCatching {
            context.startActivity(intent, bundle)
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "startActivity failed", error)
        }
    }

    private fun launchOriginOsShareProxy(context: Context, targetIntent: Intent): Boolean {
        return runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                component = ComponentName(context, FreeWindowShareProxyActivity::class.java)
                type = "text/plain"
                putExtra(Intent.EXTRA_INTENT, Intent(targetIntent))
                val pkgName = targetIntent.`package` ?: targetIntent.component?.packageName.orEmpty()
                putExtra(Intent.EXTRA_TEXT, pkgName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrElse { e ->
            android.util.Log.e("FreeWindowLauncher", "OriginOS share proxy launch failed", e)
            false
        }
    }

    private fun launchNubiaFreeform(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier = NUBIA_FREEFORM_INTENT_IDENTIFIER
        }
        runCatching {
            context.startActivity(intent, Bundle())
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "nubia freeform startActivity failed", error)
        }
    }

    fun launchOptionsBundle(context: Context, settings: AppSettings): Bundle? {
        if (!settings.freeWindowEnabled) return null
        val options = ActivityOptions.makeBasic()
        val mode = settings.resolvedFreeWindowMode()
        applyWindowingMode(options, mode.windowingMode)
        if (mode == FreeWindowMode.MAGICOS) {
            applyStackId(options, HUAWEI_FREEFORM_STACK_ID)
        }
        options.setLaunchBounds(launchBounds(context, settings))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val backgroundStartMode = when {
                Build.VERSION.SDK_INT >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                else -> @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            options.pendingIntentBackgroundActivityStartMode = backgroundStartMode
        }
        val bundle = options.toBundle() ?: Bundle()
        if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
            bundle.putInt(KEY_WINDOWING_MODE, mode.windowingMode)
        }
        return bundle
    }

    fun launchBounds(context: Context, settings: AppSettings): Rect {
        val metrics = context.resources.displayMetrics
        val isLandscape = context.isLandscapeConfiguration()
        val layout = settings.resolvedFreeWindowLayout(isLandscape)
        val baseWidthPx = (metrics.widthPixels * layout.widthFraction).toInt().coerceAtLeast(1)
        val baseHeightPx = (metrics.heightPixels * layout.heightFraction).toInt().coerceAtLeast(1)

        val scale = resolveScaleCompensation(settings, isLandscape)
        val widthPx = (baseWidthPx / scale).roundToInt().coerceAtLeast(1)
        val heightPx = (baseHeightPx / scale).roundToInt().coerceAtLeast(1)

        val leftPx = (metrics.widthPixels * layout.leftFraction).toInt()
            .coerceIn(0, (metrics.widthPixels - widthPx).coerceAtLeast(0))
        val topPx = (metrics.heightPixels * layout.topFraction).toInt()
            .coerceIn(0, (metrics.heightPixels - heightPx).coerceAtLeast(0))
        return Rect(leftPx, topPx, leftPx + widthPx, topPx + heightPx)
    }

    private fun resolveScaleCompensation(settings: AppSettings, isLandscape: Boolean): Float {
        val mode = settings.resolvedFreeWindowMode()
        val isXiaomi = mode == FreeWindowMode.STANDARD &&
            (Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
             Build.BRAND.contains("xiaomi", ignoreCase = true) ||
             Build.BRAND.contains("redmi", ignoreCase = true))
        return if (isXiaomi) {
            if (isLandscape) MIUI_LANDSCAPE_SCALE else MIUI_PORTRAIT_SCALE
        } else {
            1f
        }
    }

    private fun applyWindowingMode(options: ActivityOptions, mode: Int) {
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, mode)
        } catch (_: Exception) {
            // Hidden API unavailable; bundle fallback applied in launch().
        }
    }

    private fun applyStackId(options: ActivityOptions, stackId: Int) {
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchStackId",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, stackId)
        } catch (_: Exception) {
            // Ignored on platforms without setLaunchStackId
        }
    }
}
