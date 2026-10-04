package com.slideindex.app.ui.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.translate.TranslateAppTarget
import com.slideindex.app.translate.TranslateAppTargetResolver
import com.slideindex.app.ui.Md3PickerDrawableLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「选择翻译 App」：只列能接收取词文本的 App（文本直送 ∪ 分享）。
 *
 * 比"列出全部已安装 App"更稳：用户不可能选到一个收不了文本的 App。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranslateAppPickScreen(
    selectedPackageName: String = "",
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var targets by remember { mutableStateOf<List<TranslateAppTarget>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        targets = withContext(Dispatchers.IO) { TranslateAppTargetResolver.listTargets(context) }
        loading = false
    }

    val filtered = remember(targets, query) {
        TranslateAppTargetResolver.searchTargets(targets, query)
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.float_ball_translate_app_pick_title),
        pageHint = stringResource(R.string.float_ball_translate_app_pick_hint),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        hintResId = R.string.float_ball_translate_app_pick_search_hint,
    ) {
        when {
            loading -> {
                item(key = "translate-app-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            filtered.isEmpty() -> {
                item(key = "translate-app-empty") {
                    Text(
                        text = stringResource(R.string.float_ball_translate_app_pick_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            else -> {
                items(items = filtered, key = { it.packageName }) { target ->
                    val index = filtered.indexOf(target)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = PickerListHorizontalPadding,
                                end = PickerListHorizontalPadding,
                                bottom = if (index == filtered.lastIndex) 8.dp else 0.dp,
                            ),
                    ) {
                        Md3PickerListRow(
                            segmentIndex = index,
                            segmentCount = filtered.size,
                            title = target.label,
                            subtitle = target.packageName,
                            selected = target.packageName == selectedPackageName,
                            onClick = { onSelect(target.packageName) },
                            leadingContent = {
                                target.icon?.let { drawable ->
                                    Md3PickerDrawableLeading(
                                        drawable = drawable,
                                        contentDescription = target.label,
                                        cacheKey = target.packageName,
                                    )
                                }
                            },
                            trailingMode = PickerTrailingMode.Radio,
                        )
                    }
                }
            }
        }
    }
}
