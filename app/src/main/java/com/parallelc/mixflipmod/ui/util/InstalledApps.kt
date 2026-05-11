package com.parallelc.mixflipmod.ui.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.parallelc.mixflipmod.ui.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

@Stable
class InstalledAppsState internal constructor(
    private val packageManager: PackageManager,
    private val selfPackageName: String,
    private val needsPermission: () -> Boolean,
    private val requestPermission: () -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    var apps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var error by mutableStateOf<Throwable?>(null)
        private set

    private fun fail(error: Throwable) {
        this.error = error
        isLoading = false
        isRefreshing = false
    }

    fun load(refresh: Boolean = false) {
        if (isLoading || isRefreshing) return
        if (needsPermission()) {
            if (refresh) isRefreshing = true else isLoading = true
            requestPermission()
            return
        }

        coroutineScope.launch {
            loadInstalledApps(refresh)
        }
    }

    private suspend fun loadInstalledApps(refresh: Boolean = false) {
        if (refresh) isRefreshing = true else isLoading = true
        runCatching { loadInstalledApps(packageManager, selfPackageName) }
            .onSuccess {
                apps = it
                error = null
            }
            .onFailure {
                if (it !is CancellationException) error = it
            }
            .also { if (refresh) isRefreshing = false else isLoading = false }
    }

    fun onPermissionResult(granted: Boolean) {
        val shouldRefresh = isRefreshing
        isLoading = false
        isRefreshing = false

        if (granted) {
            load(shouldRefresh)
        } else {
            fail(SecurityException(GET_INSTALLED_APPS_PERMISSION))
        }
    }
}

@Composable
fun rememberInstalledAppsState(): InstalledAppsState {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateRef = remember { mutableStateOf<InstalledAppsState?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        stateRef.value?.onPermissionResult(granted)
    }
    return remember(ctx, permissionLauncher) {
        InstalledAppsState(
            packageManager = ctx.packageManager,
            selfPackageName = ctx.packageName,
            needsPermission = { ctx.needsInstalledAppsPermission() },
            requestPermission = { permissionLauncher.launch(GET_INSTALLED_APPS_PERMISSION) },
            coroutineScope = scope,
        )
    }.also { state ->
        stateRef.value = state
    }
}

fun List<InstalledApp>.filterByQuery(query: String): List<InstalledApp> {
    val text = query.trim()
    if (text.isEmpty()) return this
    return filter { app ->
        app.searchableText.contains(text, ignoreCase = true)
    }
}

fun Context.needsInstalledAppsPermission(): Boolean {
    return runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPermissionInfo(GET_INSTALLED_APPS_PERMISSION, 0)
        checkSelfPermission(GET_INSTALLED_APPS_PERMISSION) != PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
}

const val GET_INSTALLED_APPS_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

private suspend fun loadInstalledApps(
    packageManager: PackageManager,
    selfPackageName: String,
): List<InstalledApp> = withContext(Dispatchers.IO) {
    val collator = Collator.getInstance(Locale.getDefault())
    packageManager.getInstalledPackages(
        PackageManager.PackageInfoFlags.of(
            PackageManager.MATCH_ALL.toLong()
        )
    )
        .mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            if (info.packageName == selfPackageName) return@mapNotNull null
            InstalledApp(
                packageName = info.packageName,
                label = appInfo.loadLabel(packageManager).toString(),
                appInfo = appInfo,
                uid = appInfo.uid,
                isSystem = appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                hasLaunchIntent = packageManager.getLaunchIntentForPackage(info.packageName) != null,
            )
        }
        .sortedWith(compareBy(collator, InstalledApp::label).thenBy { it.packageName })
}
