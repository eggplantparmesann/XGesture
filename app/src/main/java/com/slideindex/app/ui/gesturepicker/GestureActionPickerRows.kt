package com.slideindex.app.ui.gesturepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerAppShortcutLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.pickerSegmentCount
import com.slideindex.app.ui.pickerSegmentIndex

internal enum class ActionPickerTab {
    ACTIONS,
    APPS,
    SHORTCUTS,
}

@Composable
internal fun ActionPickerOpenLinkRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    subtitle: String?,
    onOpenConfig: () -> Unit,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        onClick = onOpenConfig,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = false,
            )
        },
        trailingMode = PickerTrailingMode.None,
    )
}

@Composable
internal fun ActionPickerExecuteShellCommandRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    subtitle: String?,
    onOpenConfig: () -> Unit,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        onClick = onOpenConfig,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = false,
            )
        },
        trailingMode = PickerTrailingMode.None,
    )
}

@Composable
internal fun ActionPickerSimulateKeyEventRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    subtitle: String?,
    onOpenConfig: () -> Unit,
) {
    val label = androidx.compose.ui.res.stringResource(com.slideindex.app.R.string.gesture_action_simulate_key_event)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        onClick = onOpenConfig,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = false,
            )
        },
        trailingMode = PickerTrailingMode.None,
    )
}

@Composable
internal fun ActionPickerActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = gestureActionDescription(action),
        selected = selected,
        onClick = onClick,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = selected,
            )
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

@Composable
internal fun ActionPickerAppRow(
    app: AppInfo,
    segmentIndex: Int,
    segmentCount: Int,
    selected: Boolean,
    windowMode: LaunchWindowMode,
    onSelect: (AppInfo) -> Unit,
) {
    val modeLabel = if (selected && !windowMode.followsGlobalPolicy) {
        stringResource(launchWindowModeTitleRes(windowMode))
    } else {
        null
    }
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = app.label,
        subtitle = modeLabel ?: app.packageName,
        selected = selected,
        onClick = { onSelect(app) },
        leadingContent = { Md3PickerAppLeading(app) },
        trailingMode = PickerTrailingMode.Radio,
    )
}

internal fun launchWindowModeTitleRes(mode: LaunchWindowMode): Int = when (mode) {
    LaunchWindowMode.FOLLOW_GLOBAL -> R.string.launch_window_mode_follow_global
    LaunchWindowMode.ALWAYS_FULLSCREEN -> R.string.launch_window_mode_fullscreen
    LaunchWindowMode.ALWAYS_FREE_WINDOW -> R.string.launch_window_mode_free_window
}

private val LaunchWindowMode.descriptionRes: Int?
    get() = when (this) {
        LaunchWindowMode.FOLLOW_GLOBAL -> R.string.launch_window_mode_follow_global_desc
        LaunchWindowMode.ALWAYS_FULLSCREEN -> null
        LaunchWindowMode.ALWAYS_FREE_WINDOW -> R.string.launch_window_mode_free_window_desc
    }

/**
 * 选择某个已绑定应用的启动形态（跟随全局 / 全屏 / 小窗）。
 *
 * 选中只改本地态，点「确定」才回调落库；取消即放弃本次选择。
 */
@Composable
internal fun GestureLaunchWindowModeDialog(
    app: AppInfo,
    initialMode: LaunchWindowMode,
    onDismiss: () -> Unit,
    onConfirm: (LaunchWindowMode) -> Unit,
) {
    var selected by remember(app.packageName, initialMode) { mutableStateOf(initialMode) }
    val options = remember { LaunchWindowMode.entries.toList() }

    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.launch_window_mode_title),
        onConfirm = { onConfirm(selected) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            options.forEachIndexed { index, mode ->
                Md3PickerListRow(
                    segmentIndex = pickerSegmentIndex(index, options.size),
                    segmentCount = pickerSegmentCount(options.size),
                    title = stringResource(launchWindowModeTitleRes(mode)),
                    subtitle = mode.descriptionRes?.let { stringResource(it) },
                    selected = selected == mode,
                    onClick = { selected = mode },
                    leadingContent = { Md3PickerAppLeading(app) },
                    trailingMode = PickerTrailingMode.Radio,
                )
            }
        }
    }
}

@Composable
internal fun ActionPickerShortcutRow(
    shortcut: TaskSwitcherMenuItem,
    packageName: String,
    segmentIndex: Int,
    segmentCount: Int,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
) {
    val action = shortcutToLaunchShortcut(shortcut, packageName)
    val selected = current is GestureAction.LaunchShortcut && current.payloadKey == action.payloadKey
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = selected,
        onClick = { onSelect(action) },
        leadingContent = {
            Md3PickerAppShortcutLeading(
                packageName = packageName,
                selected = selected,
                contentDescription = shortcut.label,
            )
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

private fun shortcutToLaunchShortcut(
    shortcut: TaskSwitcherMenuItem,
    packageName: String,
): GestureAction.LaunchShortcut {
    val uris = shortcut.intentUris
    if (!uris.isNullOrEmpty()) {
        return if (uris.size == 1) {
            GestureAction.LaunchShortcut.intent(uris[0], shortcut.label)
        } else {
            GestureAction.LaunchShortcut.intents(uris, shortcut.label)
        }
    }
    val component = shortcut.targetComponent?.takeIf { it.isNotBlank() }
    if (component != null) {
        return GestureAction.LaunchShortcut.component(component, shortcut.label)
    }
    val shortcutId = shortcut.shortcutId?.takeIf { it.isNotBlank() } ?: shortcut.label
    return GestureAction.LaunchShortcut.dynamic(packageName, shortcutId, shortcut.label)
}
