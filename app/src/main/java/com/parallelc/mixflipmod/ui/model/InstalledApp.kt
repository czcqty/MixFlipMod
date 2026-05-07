package com.parallelc.mixflipmod.ui.model

import android.content.pm.ApplicationInfo

data class InstalledApp(
    val packageName: String,
    val label: String,
    val appInfo: ApplicationInfo,
    val uid: Int,
    val isSystem: Boolean,
    val hasLaunchIntent: Boolean,
) {
    val searchableText: String get() = "$label $packageName"
}
