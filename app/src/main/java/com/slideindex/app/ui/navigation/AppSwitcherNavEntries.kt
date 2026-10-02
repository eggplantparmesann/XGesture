package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.ui.AppSwitcherSettingsScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.appSwitcherNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.AppSwitcherSettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        AppSwitcherSettingsScreen(
            appSettings = appSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSettingsChange = { axis, settings ->
                viewModel.setFvAppSwitcherSettings(axis, settings)
            },
            onLinkAppearanceAxesChange = { enabled, axis, mergeDirection ->
                viewModel.setFvAppSwitcherLinkAppearanceAxes(enabled, axis, mergeDirection)
            },
            onLinkSlotAxesChange = { enabled, axis, mergeDirection ->
                viewModel.setFvAppSwitcherLinkSlotAxes(enabled, axis, mergeDirection)
            },
        )
    }
}
