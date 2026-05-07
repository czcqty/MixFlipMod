package com.parallelc.mixflipmod.ui.screen

import android.content.SharedPreferences
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.Prefs.FlipScreenMode
import com.parallelc.mixflipmod.R
import com.parallelc.mixflipmod.ui.component.AppIcon
import com.parallelc.mixflipmod.ui.model.InstalledApp
import com.parallelc.mixflipmod.ui.util.XposedServiceState
import com.parallelc.mixflipmod.ui.util.checkScope
import com.parallelc.mixflipmod.ui.util.filterByQuery
import com.parallelc.mixflipmod.ui.util.rememberInstalledAppsState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun FlipScreenModeScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit,
) {
    val appsState = rememberInstalledAppsState()
    var query by remember { mutableStateOf("") }
    var modeVersion by remember { mutableIntStateOf(0) }
    var showSystemApps by remember { mutableStateOf(false) }
    var showTopPopup by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val refreshTexts = listOf(
        stringResource(R.string.refresh_pulling),
        stringResource(R.string.refresh_release),
        stringResource(R.string.refresh_refreshing),
        stringResource(R.string.refresh_complete),
    )
    LaunchedEffect(appsState) {
        appsState.load()
    }

    val visibleApps = remember(appsState.apps, query, showSystemApps, modeVersion) {
        appsState.apps
            .asSequence()
            .filter { it.hasLaunchIntent }
            .filter { showSystemApps || !it.isSystem }
            .toList()
            .filterByQuery(query)
            .sortedWith(
                compareByDescending<InstalledApp> {
                    prefs.getInt(Prefs.flipScreenModeKey(it.packageName), FlipScreenMode.DEFAULT.prefValue) != FlipScreenMode.DEFAULT.prefValue
                }.thenBy { it.label.lowercase() }.thenBy { it.packageName }
            )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = stringResource(R.string.pref_flip_screen_mode),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                Box {
                    OverlayListPopup(
                        show = showTopPopup,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        onDismissRequest = { showTopPopup = false },
                    ) {
                        ListPopupColumn {
                            DropdownImpl(
                                text = stringResource(R.string.show_system_apps),
                                optionSize = 1,
                                isSelected = showSystemApps,
                                index = 0,
                                onSelectedIndexChange = {
                                    showSystemApps = !showSystemApps
                                    showTopPopup = false
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = { showTopPopup = true },
                        holdDownState = showTopPopup,
                    ) {
                        Icon(imageVector = Icons.Filled.MoreVert, tint = colorScheme.onSurface, contentDescription = null)
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding() + 12.dp, bottom = 12.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.search_apps),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PullToRefresh(
                isRefreshing = appsState.isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = { appsState.load(refresh = true) },
                refreshTexts = refreshTexts,
                contentPadding = PaddingValues(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    when {
                        appsState.isLoading -> item { LoadingItem() }
                        appsState.error != null -> item { MessageItem(text = stringResource(R.string.app_list_load_failed)) }
                        visibleApps.isEmpty() -> item { MessageItem(text = stringResource(R.string.app_list_empty)) }
                        else -> items(visibleApps) { app ->
                            FlipScreenModeAppItem(
                                app = app,
                                mode = FlipScreenMode.fromPref(prefs.getInt(Prefs.flipScreenModeKey(app.packageName), FlipScreenMode.DEFAULT.prefValue)) ?: FlipScreenMode.DEFAULT,
                                onModeSelected = { mode ->
                                    prefs.edit {
                                        val key = Prefs.flipScreenModeKey(app.packageName)
                                        if (mode == FlipScreenMode.DEFAULT) remove(key) else putInt(key, mode.prefValue)
                                    }
                                    runCatching { checkScope(XposedServiceState.service, "system", true) }
                                    modeVersion++
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipScreenModeAppItem(
    app: InstalledApp,
    mode: FlipScreenMode,
    onModeSelected: (FlipScreenMode) -> Unit,
) {
    val pm = LocalContext.current.packageManager
    var showDropdown by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        BasicComponent(
            startAction = {
                AppIcon(icon = app.appInfo, pm = pm, modifier = Modifier.padding(end = 8.dp).size(36.dp))
            },
            endActions = {
                Box {
                    OverlayListPopup(
                        show = showDropdown,
                        alignment = PopupPositionProvider.Align.End,
                        onDismissRequest = { showDropdown = false },
                    ) {
                        ListPopupColumn {
                            flipScreenModeOptions().forEachIndexed { index, (optionMode, label) ->
                                DropdownImpl(
                                    text = label,
                                    optionSize = FLIP_SCREEN_MODE_OPTION_COUNT,
                                    isSelected = optionMode == mode,
                                    index = index,
                                    onSelectedIndexChange = {
                                        onModeSelected(optionMode)
                                        showDropdown = false
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.clickable { showDropdown = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = flipScreenModeLabel(mode),
                            color = if (mode == FlipScreenMode.DEFAULT) {
                                colorScheme.onSurfaceVariantSummary
                            } else {
                                colorScheme.primary
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.size(8.dp))
                        DropdownArrowEndAction(actionColor = colorScheme.onSurfaceVariantActions)
                    }
                }
            },
            holdDownState = showDropdown,
        ) {
            Text(
                text = app.label,
                modifier = Modifier.basicMarquee(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = app.packageName,
                modifier = Modifier.basicMarquee(),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun LoadingItem() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InfiniteProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text = stringResource(R.string.app_list_loading), color = colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun MessageItem(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun flipScreenModeOptions(): List<Pair<FlipScreenMode, String>> {
    return FlipScreenMode.entries.map { it to flipScreenModeLabel(it) }
}

@Composable
private fun flipScreenModeLabel(mode: FlipScreenMode): String {
    return when (mode) {
        FlipScreenMode.NO_SCALE -> stringResource(R.string.flip_screen_mode_no_scale)
        FlipScreenMode.SCALE -> stringResource(R.string.flip_screen_mode_scale)
        else -> stringResource(R.string.flip_screen_mode_default)
    }
}

private const val FLIP_SCREEN_MODE_OPTION_COUNT = 4
