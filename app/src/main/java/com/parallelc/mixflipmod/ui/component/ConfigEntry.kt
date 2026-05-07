package com.parallelc.mixflipmod.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parallelc.mixflipmod.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.miuixShape

@Composable
fun ConfigEntry(
    packageName: String,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val appInfo = remember(packageName) {
        runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
    }
    val appLabel = if (packageName == "android") {
        stringResource(R.string.system_framework)
    } else {
        remember(appInfo) {
            appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
        }
    }
    val summary = if (packageName == "android") stringResource(R.string.system_framework_summary) else packageName
    ArrowPreference(
        title = appLabel,
        summary = summary,
        startAction = {
            if (appInfo != null) {
                AppIcon(icon = appInfo, pm = pm, modifier = Modifier.padding(end = 8.dp).size(36.dp))
            } else {
                Box(
                    modifier = Modifier.padding(end = 8.dp).size(36.dp)
                        .clip(miuixShape(12.dp)).background(colorScheme.secondaryContainer)
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
fun ConfigEntry(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Box(
                modifier = Modifier.padding(end = 8.dp).size(36.dp)
                    .clip(miuixShape(12.dp)).background(colorScheme.secondaryContainer),
            ) {
                Icon(
                    imageVector = icon,
                    tint = colorScheme.onSurface,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                )
            }
        },
        onClick = onClick,
    )
}
