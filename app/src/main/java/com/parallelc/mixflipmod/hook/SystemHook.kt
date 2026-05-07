package com.parallelc.mixflipmod.hook

import android.content.ComponentName
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.hook.util.after
import com.parallelc.mixflipmod.hook.util.findClass
import com.parallelc.mixflipmod.hook.util.hook
import com.parallelc.mixflipmod.hook.util.method
import com.parallelc.mixflipmod.hook.util.callMethod
import com.parallelc.mixflipmod.hook.util.getField
import com.parallelc.mixflipmod.hook.util.prefEnabled
import com.parallelc.mixflipmod.hook.util.prefInt
import com.parallelc.mixflipmod.hook.util.prefString
import com.parallelc.mixflipmod.hook.util.safeHook
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

object SystemHook {

    fun hook(param: SystemServerStartingParam) {
        safeHook("[android] ${Prefs.SYSTEM_COMPAT_CONFIG}") { hookCompatConfig(param) }
        safeHook("[android] ${Prefs.SYSTEM_FLIP_CONTINUITY}") { hookFlipContinuity(param) }
        safeHook("[android] ${Prefs.SYSTEM_FLIP_SCREEN_MODE}") { hookFlipScreenMode(param) }
        safeHook("[android] ${Prefs.SYSTEM_FLIP_IME_PKG}") { hookFlipInputMethod(param) }
    }

    private fun hookCompatConfig(param: SystemServerStartingParam) {
        val props = arrayOf("miui.continuity.policy", "android.window.PROPERTY_COMPAT_ALLOW_SMALL_COVER_SCREEN")
        val mgr = param.classLoader.findClass("com.android.server.wm.ApplicationCompatManager")
        val propertyIntHook = Hooker { chain ->
            if (!prefEnabled(Prefs.SYSTEM_COMPAT_CONFIG)) return@Hooker chain.proceed()
            when (chain.args[0]) {
                "miui.continuity.policy" -> 5
                "android.window.PROPERTY_COMPAT_ALLOW_SMALL_COVER_SCREEN" -> 1
                else -> chain.proceed()
            }
        }
        hook(mgr.method("getPropertyIntByApplication", String::class.java, String::class.java), propertyIntHook)
        hook(mgr.method("getPropertyIntByActivity", String::class.java, ComponentName::class.java), propertyIntHook)

        val hasPropertyHook = Hooker { chain ->
            if (!prefEnabled(Prefs.SYSTEM_COMPAT_CONFIG)) return@Hooker chain.proceed()
            if (chain.args[0] in props) true else chain.proceed()
        }
        hook(mgr.method("hasPropertyByApplication", String::class.java, String::class.java), hasPropertyHook)
        hook(mgr.method("hasPropertyByActivity", String::class.java, ComponentName::class.java), hasPropertyHook)
    }

    private fun hookFlipContinuity(param: SystemServerStartingParam) {
        val c = param.classLoader.findClass("com.android.server.wm.InterceptActivityController")
        hook(c.method("isFlipContinuityEnabledFromSetting", String::class.java, Int::class.java, String::class.java)) { chain ->
            if (prefEnabled(Prefs.SYSTEM_FLIP_CONTINUITY)) true else chain.proceed()
        }
    }

    private fun hookFlipScreenMode(param: SystemServerStartingParam) {
        val ipm = param.classLoader.findClass("com.android.server.pm.IPackageManagerBase")
        hook(ipm.method("getActivityInfo", ComponentName::class.java, Long::class.java, Int::class.java), after { _, result ->
            (result as? ActivityInfo)?.applyFlipScreenMode()
            result
        })
        hook(ipm.method("getApplicationInfo", String::class.java, Long::class.java, Int::class.java), after { _, result ->
            (result as? ApplicationInfo)?.applyFlipScreenMode()
            result
        })
    }

    private fun hookFlipInputMethod(param: SystemServerStartingParam) {
        val switcher = param.classLoader.findClass("com.android.server.inputmethod.SogouInputMethodSwitcher")
        // isSogouMethodLocked(String) — userId from this.mService.mCurrentImeUserId
        hook(switcher.method("isSogouMethodLocked", String::class.java)) { chain ->
            val methodId = chain.args[0] as? String ?: return@hook chain.proceed()
            val userId = chain.thisObject?.getField("mService")?.getField("mCurrentImeUserId") as? Int
                ?: return@hook chain.proceed()
            inputMethodPackage(param, methodId, userId)?.let { it == flipInputMethodPackage() }
                ?: chain.proceed()
        }

        val immsClass = param.classLoader.findClass("com.android.server.inputmethod.InputMethodManagerService")
        val serviceImpl = param.classLoader.findClass("com.android.server.inputmethod.InputMethodManagerServiceImpl")
        // isSogouMethodLocked(InputMethodManagerService, String) — userId from args[0].mCurrentImeUserId
        hook(serviceImpl.method("isSogouMethodLocked", immsClass, String::class.java)) { chain ->
            val service = chain.args[0] ?: return@hook chain.proceed()
            val methodId = chain.args[1] as? String ?: return@hook chain.proceed()
            val userId = service.getField("mCurrentImeUserId") as? Int ?: return@hook chain.proceed()
            inputMethodPackage(param, methodId, userId)?.let { it == flipInputMethodPackage() }
                ?: chain.proceed()
        }
    }

    private fun inputMethodPackage(param: SystemServerStartingParam, methodId: String, userId: Int): String? {
        val repository = param.classLoader.findClass("com.android.server.inputmethod.InputMethodSettingsRepository")
        val settings = repository.method("get", Int::class.java).invoke(null, userId) ?: return null
        val imi = settings.callMethod("getMethodMap")?.callMethod("get", methodId) ?: return null
        return imi.callMethod("getPackageName") as? String
    }

    private fun ActivityInfo.applyFlipScreenMode() {
        val mode = flipScreenModeFor(packageName)
        if (mode == Prefs.FLIP_SCREEN_MODE_DEFAULT) return
        val data = metaData ?: Bundle().also { metaData = it }
        data.putInt(FLIP_SCREEN_META_DATA, mode)
    }

    private fun ApplicationInfo.applyFlipScreenMode() {
        val mode = flipScreenModeFor(packageName)
        if (mode == Prefs.FLIP_SCREEN_MODE_DEFAULT) return
        val data = metaData ?: Bundle().also { metaData = it }
        data.putInt(FLIP_SCREEN_META_DATA, mode)
    }

    private fun flipScreenModeFor(packageName: String): Int {
        return prefInt(Prefs.flipScreenModeKey(packageName), Prefs.FLIP_SCREEN_MODE_DEFAULT)
    }

    private fun flipInputMethodPackage(): String {
        return prefString(Prefs.SYSTEM_FLIP_IME_PKG, Prefs.DEFAULT_FLIP_IME_PKG)
            .trim()
            .ifEmpty { Prefs.DEFAULT_FLIP_IME_PKG }
    }

    private const val FLIP_SCREEN_META_DATA = "miui.supportFlipFullScreen"
}
