package com.parallelc.mixflipmod.ui.util

import android.content.pm.PackageManager

fun PackageManager.appLabel(packageName: String, fallback: String): String {
    val info = runCatching { getApplicationInfo(packageName, 0) }.getOrNull()
    return info?.let { getApplicationLabel(it).toString() } ?: fallback
}
