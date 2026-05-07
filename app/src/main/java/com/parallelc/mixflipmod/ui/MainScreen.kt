package com.parallelc.mixflipmod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.parallelc.mixflipmod.BuildConfig
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.R
import com.parallelc.mixflipmod.model.AppConfig
import com.parallelc.mixflipmod.model.ConfigNode
import com.parallelc.mixflipmod.model.configNodes
import com.parallelc.mixflipmod.ui.component.AppIcon
import com.parallelc.mixflipmod.ui.component.ConfigEntry
import com.parallelc.mixflipmod.ui.component.PrefSpecItem
import com.parallelc.mixflipmod.ui.component.ServiceStatusCard
import com.parallelc.mixflipmod.ui.screen.FlipScreenModeScreen
import com.parallelc.mixflipmod.ui.screen.GroupSwitchScreen
import com.parallelc.mixflipmod.ui.screen.SubScreen
import com.parallelc.mixflipmod.ui.util.appLabel
import com.parallelc.mixflipmod.ui.util.scopePackage
import com.parallelc.mixflipmod.ui.util.XposedServiceState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private sealed interface Screen {
    data object Main : Screen
    data class PackageConfig(val config: AppConfig) : Screen
    data class Group(val node: ConfigNode.Group) : Screen
    data class FlipScreenMode(val parent: AppConfig) : Screen
}

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val prefs = XposedServiceState.prefs
    val xposedService = XposedServiceState.service
    val serviceConnected = XposedServiceState.isConnected
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val mainSB = MiuixScrollBehavior()
    val mainLS = rememberLazyListState()
    val pm = ctx.packageManager
    val appInfo = remember(ctx) { ctx.applicationInfo }

    DisposableEffect(Unit) {
        ctx.deleteSharedPreferences(Prefs.NAME)
        XposedServiceState.ensureRegistered()
        onDispose { }
    }

    LaunchedEffect(serviceConnected) {
        if (!serviceConnected) {
            screen = Screen.Main
        }
    }

    val isConfigVisible: (AppConfig) -> Boolean = { config ->
        config.packageName == "android" || runCatching {
            pm.getApplicationInfo(config.packageName, 0)
        }.isSuccess
    }

    val visibleNodes = configNodes.filter { node ->
        when (node) {
            is ConfigNode.Package -> isConfigVisible(node.config)
            is ConfigNode.Group -> node.packages.any(isConfigVisible)
        }
    }

    AnimatedContent(targetState = screen, transitionSpec = {
        val dir = if (screenOrder(targetState) > screenOrder(initialState)) 1 else -1
        slideInHorizontally { it * dir } togetherWith slideOutHorizontally { -it * dir }
    }, label = "screen") { s ->
        when (s) {
            is Screen.FlipScreenMode -> {
                val remotePrefs = prefs ?: return@AnimatedContent
                BackHandler { screen = Screen.PackageConfig(s.parent) }
                FlipScreenModeScreen(
                    prefs = remotePrefs,
                    onBack = { screen = Screen.PackageConfig(s.parent) },
                )
            }
            is Screen.Group -> {
                val remotePrefs = prefs ?: return@AnimatedContent
                BackHandler { screen = Screen.Main }
                GroupSwitchScreen(
                    prefs = remotePrefs,
                    titleRes = s.node.titleRes,
                    configs = s.node.packages,
                    onBack = { screen = Screen.Main },
                )
            }
            Screen.Main -> {
                Scaffold(topBar = {
                    TopAppBar(
                        title = stringResource(R.string.app_name),
                        scrollBehavior = mainSB,
                        actions = {
                            Box {
                                OverlayListPopup(
                                    show = showMainMenu,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showMainMenu = false },
                                ) {
                                    ListPopupColumn {
                                        DropdownImpl(
                                            text = stringResource(R.string.about),
                                            optionSize = 1,
                                            isSelected = false,
                                            index = 0,
                                            onSelectedIndexChange = {
                                                showMainMenu = false
                                                showAboutDialog = true
                                            },
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showMainMenu = true },
                                    holdDownState = showMainMenu,
                                ) {
                                    Icon(imageVector = Icons.Filled.MoreVert, tint = colorScheme.onSurface, contentDescription = null)
                                }
                            }
                        },
                    )
                }) {
                    LazyColumn(
                        state = mainLS,
                        modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical()
                            .nestedScroll(mainSB.nestedScrollConnection),
                        contentPadding = it,
                    ) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ServiceStatusCard(serviceConnected = serviceConnected)
                                prefs?.takeIf { serviceConnected }?.let {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        visibleNodes.forEach { node ->
                                            when (node) {
                                                is ConfigNode.Package -> {
                                                    ConfigEntry(
                                                        packageName = node.config.packageName,
                                                        onClick = { screen = Screen.PackageConfig(node.config) },
                                                    )
                                                }
                                                is ConfigNode.Group -> {
                                                    ConfigEntry(
                                                        title = stringResource(node.titleRes),
                                                        summary = node.summaryRes?.let { stringResource(it) },
                                                        icon = Icons.Filled.VisibilityOff,
                                                        onClick = { screen = Screen.Group(node) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AboutDialog(
                        show = showAboutDialog,
                        appInfo = appInfo,
                        onDismiss = { showAboutDialog = false },
                    )
                }
            }
            is Screen.PackageConfig -> {
                val cfg = s.config
                val remotePrefs = prefs ?: return@AnimatedContent
                val title = if (cfg.packageName == "android") stringResource(R.string.system_framework)
                else remember(cfg.packageName) { pm.appLabel(cfg.packageName, cfg.packageName) }
                BackHandler { screen = Screen.Main }
                SubScreen(
                    title = title,
                    packageName = cfg.packageName,
                    onBack = { screen = Screen.Main },
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)) {
                            cfg.prefs.forEach { spec ->
                                PrefSpecItem(
                                    spec = spec,
                                    prefs = remotePrefs,
                                    xposedService = xposedService,
                                    scopePackage = cfg.scopePackage,
                                )
                            }
                            if (cfg.packageName == "android") {
                                ArrowPreference(
                                    title = stringResource(R.string.pref_flip_screen_mode),
                                    onClick = { screen = Screen.FlipScreenMode(cfg) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

private fun screenOrder(screen: Screen): Int {
    return when (screen) {
        Screen.Main -> 0
        is Screen.PackageConfig,
        is Screen.Group -> 1
        is Screen.FlipScreenMode -> 2
    }
}

@Composable
private fun AboutDialog(
    show: Boolean,
    appInfo: android.content.pm.ApplicationInfo,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val projectUrl = stringResource(R.string.project_url)
    val authorUrl = stringResource(R.string.author_url)

    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.about),
    ) {
        Column {
            Row {
                AppIcon(
                    icon = appInfo,
                    pm = ctx.packageManager,
                    modifier = Modifier.size(50.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.app_name))
                    Text(
                        text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = buildAnnotatedString {
                        withLink(LinkAnnotation.Url(url = projectUrl)) {
                            withStyle(style = SpanStyle(color = colorScheme.primary)) {
                                append(stringResource(R.string.project_link))
                            }
                        }
                    },
                    style = MiuixTheme.textStyles.body1,
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.author_prefix))
                        withLink(LinkAnnotation.Url(url = authorUrl)) {
                            withStyle(style = SpanStyle(color = colorScheme.primary)) {
                                append(stringResource(R.string.author_name))
                            }
                        }
                    },
                    style = MiuixTheme.textStyles.body1,
                )
            }
        }
    }
}
