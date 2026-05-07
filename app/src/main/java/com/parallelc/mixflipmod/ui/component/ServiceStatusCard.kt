package com.parallelc.mixflipmod.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parallelc.mixflipmod.BuildConfig
import com.parallelc.mixflipmod.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun ServiceStatusCard(serviceConnected: Boolean) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = when {
        serviceConnected && isDark -> Color(0xFF1A3825)
        serviceConnected -> Color(0xFFDFFAE4)
        isDark -> Color(0xFF3A1E22)
        else -> Color(0xFFFFE3E6)
    }
    val markColor = if (serviceConnected) Color(0xFF36D167) else colorScheme.error
    val title = stringResource(
        if (serviceConnected) R.string.xposed_status_active else R.string.xposed_status_inactive
    )
    val summary = if (serviceConnected) {
        stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    } else {
        stringResource(R.string.xposed_service_disconnected_summary)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(backgroundColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(36.dp, 34.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(150.dp),
                    imageVector = if (serviceConnected) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                    tint = markColor.copy(alpha = 0.6f),
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    text = summary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
