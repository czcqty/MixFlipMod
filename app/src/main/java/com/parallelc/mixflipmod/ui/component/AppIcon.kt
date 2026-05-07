package com.parallelc.mixflipmod.ui.component

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.miuixShape

@Composable
fun AppIcon(icon: ApplicationInfo, pm: PackageManager, modifier: Modifier) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(icon.packageName) {
        val bm = withContext(Dispatchers.IO) {
            runCatching {
                val d = pm.getApplicationIcon(icon)
                if (d is BitmapDrawable) d.bitmap
                else {
                    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 48
                    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 48
                    val b = createBitmap(w, h)
                    d.setBounds(0, 0, w, h)
                    d.draw(Canvas(b))
                    b
                }
            }.getOrNull()?.asImageBitmap()
        }
        bitmap = bm
    }
    bitmap?.let {
        Image(it, "", modifier = modifier.clip(miuixShape(12.dp)))
    } ?: Box(modifier = modifier.clip(miuixShape(12.dp)).background(colorScheme.secondaryContainer))
}
