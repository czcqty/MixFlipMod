package com.parallelc.mixflipmod.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parallelc.mixflipmod.R
import com.parallelc.mixflipmod.ui.util.RestartResult
import com.parallelc.mixflipmod.ui.util.restartApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun RestartDialog(
    show: Boolean,
    label: String,
    packageName: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    WindowDialog(
        show = show,
        title = stringResource(R.string.restart_title, label),
        summary = stringResource(R.string.restart_confirm_message, label),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    onDismiss()
                    doRestart(scope, ctx, label, packageName)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

private fun doRestart(
    scope: CoroutineScope,
    ctx: android.content.Context,
    label: String,
    packageName: String,
) {
    scope.launch {
        val result = restartApp(packageName)
        if (result == RestartResult.Ok) return@launch
        withContext(Dispatchers.Main) {
            val msg = when (result) {
                RestartResult.NotRunning -> ctx.getString(R.string.restart_not_running, label)
                RestartResult.NoRoot -> ctx.getString(R.string.restart_no_root, label)
                RestartResult.Failed -> ctx.getString(R.string.restart_failed)
            }
            val duration = if (result == RestartResult.NotRunning) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            Toast.makeText(ctx, msg, duration).show()
        }
    }
}
