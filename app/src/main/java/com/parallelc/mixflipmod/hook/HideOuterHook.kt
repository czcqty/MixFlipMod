package com.parallelc.mixflipmod.hook

import android.content.ContentResolver
import android.provider.Settings
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.hook.util.after
import com.parallelc.mixflipmod.hook.util.findClass
import com.parallelc.mixflipmod.hook.util.hook
import com.parallelc.mixflipmod.hook.util.method
import com.parallelc.mixflipmod.model.hideOuterPackages
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

object HideOuterHook : BaseHook() {
    override val targetPackages = hideOuterPackages

    override fun setupHooks(prefKey: String, param: PackageReadyParam) {
        if (prefKey != Prefs.hideOuterKey(param.packageName)) {
            return
        }
        hook(Settings.Global::class.java.method("getInt", ContentResolver::class.java, String::class.java, Int::class.java), after { chain, result ->
            if (chain.args[1] as? String == "device_posture" && result == 1) 3 else result
        })

        val mgr = param.classLoader.findClass("android.hardware.devicestate.DeviceStateManager")
        hook(mgr.method("getCurrentState"), after { _, result ->
            if (result == 0) 3 else result
        })

        val sp = param.classLoader.findClass("miuix.core.util.SystemProperties")
        hook(sp.method("getInt", String::class.java, Int::class.java)) { chain ->
            if (chain.args[0] == "persist.sys.multi_display_type") 1 else chain.proceed()
        }

        if (param.packageName in AppDpiHook.targetPackages) AppDpiHook.hook(param)
    }
}
