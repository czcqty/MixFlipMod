package com.parallelc.mixflipmod.hook

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.hook.util.after
import com.parallelc.mixflipmod.hook.util.hook
import com.parallelc.mixflipmod.hook.util.method
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

object AppDpiHook : BaseHook() {
    override val targetPackages = listOf(
        "com.android.calendar",
        "com.android.contacts",
        "com.android.deskclock",
        "com.miui.calculator",
        "com.miui.gallery",
    )

    private val recreatedActivities = WeakHashMap<Activity, Boolean>()

    @Suppress("DEPRECATION")
    override fun hook(param: PackageReadyParam) {
        val dpi = Prefs.HIDE_OUTER_DPI
        hook(Resources::class.java.method("getDisplayMetrics"), after { _, result ->
            val dm = result as? DisplayMetrics ?: return@after result
            val width = min(dm.widthPixels, dm.heightPixels)
            val height = max(dm.widthPixels, dm.heightPixels)
            if (width < 1050 && height < 1800) {
                val scaleFactor = dm.scaledDensity / dm.density
                val densityRatio = dpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT.toFloat()
                dm.density = densityRatio
                dm.densityDpi = dpi
                dm.scaledDensity = densityRatio * scaleFactor
                dm.xdpi = dpi.toFloat()
                dm.ydpi = dpi.toFloat()
            }
            result
        })
        hook(Activity::class.java.method("onConfigurationChanged", Configuration::class.java)) { chain ->
            val result = chain.proceed()
            (chain.thisObject as? Activity)
                ?.takeIf { !it.isChangingConfigurations && recreatedActivities[it] != true }
                ?.let {
                    recreatedActivities[it] = true
                    it.recreate()
                }
            result
        }
    }
}
