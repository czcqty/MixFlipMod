package com.parallelc.mixflipmod.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parallelc.mixflipmod.R
import com.parallelc.mixflipmod.ui.component.RestartDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SubScreen(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val sb = MiuixScrollBehavior()
    var showRestartDialog by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = title, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), contentDescription = stringResource(R.string.cd_back))
            }
        }, actions = {
            IconButton(onClick = { showRestartDialog = true }) {
                Icon(imageVector = Icons.Filled.Refresh, tint = colorScheme.onSurface, contentDescription = stringResource(R.string.cd_restart))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }, scrollBehavior = sb)
    }) {
        RestartDialog(
            show = showRestartDialog,
            label = title,
            packageName = packageName,
            onDismiss = { showRestartDialog = false },
        )
        LazyColumn(
            modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical()
                .nestedScroll(sb.nestedScrollConnection).padding(horizontal = 12.dp),
            contentPadding = it,
        ) { content() }
    }
}
