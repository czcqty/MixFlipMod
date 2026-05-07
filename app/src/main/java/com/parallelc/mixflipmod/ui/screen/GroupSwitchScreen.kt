package com.parallelc.mixflipmod.ui.screen

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.parallelc.mixflipmod.R
import com.parallelc.mixflipmod.model.AppConfig
import com.parallelc.mixflipmod.ui.component.AppIcon
import com.parallelc.mixflipmod.ui.util.XposedServiceState
import com.parallelc.mixflipmod.ui.util.appLabel
import com.parallelc.mixflipmod.ui.util.checkScope
import com.parallelc.mixflipmod.ui.util.scopePackage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun GroupSwitchScreen(
    prefs: SharedPreferences,
    @StringRes titleRes: Int,
    configs: List<AppConfig>,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val scrollBehavior = MiuixScrollBehavior()
    val visibleConfigs = remember(configs) {
        configs.filter {
            runCatching { pm.getApplicationInfo(it.packageName, 0) }.isSuccess
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = stringResource(titleRes),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = padding,
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)) {
                    visibleConfigs.forEach { config ->
                        val prefKey = config.prefs.first().prefKey
                        val checked = remember(prefs, prefKey) {
                            mutableStateOf(prefs.getBoolean(prefKey, false))
                        }
                        val appInfo = remember(config.packageName) {
                            runCatching { pm.getApplicationInfo(config.packageName, 0) }.getOrNull()
                        }
                        SwitchPreference(
                            title = remember(config.packageName) {
                                pm.appLabel(config.packageName, config.packageName)
                            },
                            summary = config.packageName,
                            checked = checked.value,
                            startAction = {
                                appInfo?.let {
                                    AppIcon(
                                        icon = it,
                                        pm = pm,
                                        modifier = Modifier.padding(end = 8.dp).size(36.dp),
                                    )
                                }
                            },
                            onCheckedChange = {
                                checked.value = it
                                prefs.edit { putBoolean(prefKey, it) }
                                runCatching {
                                    checkScope(XposedServiceState.service, config.scopePackage, it)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
