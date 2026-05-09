package com.parallelc.mixflipmod.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.net.toUri
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.hook.util.after
import com.parallelc.mixflipmod.hook.util.callMethod
import com.parallelc.mixflipmod.hook.util.findClass
import com.parallelc.mixflipmod.hook.util.getField
import com.parallelc.mixflipmod.hook.util.hook
import com.parallelc.mixflipmod.hook.util.method
import com.parallelc.mixflipmod.hook.util.prefInt
import com.parallelc.mixflipmod.hook.util.replaceResult
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs

object FlipHomeHook : BaseHook() {
    override val targetPackages = listOf("com.miui.fliphome")
    private const val SYSTEM_HOME_PACKAGE = "com.miui.home"
    private const val RECENTS_CONTAINER_CLASS = "com.miui.fliphome.recents.views.RecentsContainer"
    private const val TASK_STACK_VIEW_CLASS = "com.miui.fliphome.recents.views.TaskStackView"
    private const val APP_DETAILS_ICON = "shortcut_menu_app_details_icon"
    private const val RECENTS_MENU_BACKGROUND = "recent_menu_bg"
    private const val APP_SHORTCUT_EXTRA_MENU_TAG = "mixflipmod_app_shortcut_extra_menu"
    private const val APP_SHORTCUT_ORIGINAL_MENU_TAG = "mixflipmod_app_shortcut_original_menu"
    private val recentsTaskMenus = WeakHashMap<View, RecentsTaskMenuHandle>()

    private data class TaskViewState(
        val view: View,
        val visibility: Int,
        val alpha: Float,
        val translationZ: Float,
        val importantForAccessibility: Int,
    )

    private data class RecentsMenuState(
        val taskStackView: Any?,
        val taskViews: List<TaskViewState>,
    )

    private data class RecentsTaskMenuHandle(
        val popup: PopupWindow,
        val dismiss: () -> Unit,
    )

    private data class MenuItemPosition(
        val x: Int,
        val y: Int,
        val pivotX: Int,
        val pivotY: Int,
    )

    private data class RecentsMenuAnchor(
        val taskLeft: Int,
        val taskTop: Int,
        val taskRight: Int,
        val taskBottom: Int,
        val taskCenterY: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val itemSize: Int,
        val minMargin: Int,
        val showAtRight: Boolean,
    )

    override fun setupHooks(prefKey: String, param: PackageReadyParam) {
        when (prefKey) {
            Prefs.FLIPHOME_NO_START_PAGE -> hookNoStartPage(param)
            Prefs.FLIPHOME_RECENTS_STYLE -> hookRecentsStyle(param)
            Prefs.FLIPHOME_RECENTS_LONG_PRESS_MENU -> hookRecentsLongPressMenu(param)
            Prefs.FLIPHOME_APP_LONG_PRESS_MENU -> hookAppLongPressMenu(param)
        }
    }

    private fun hookNoStartPage(param: PackageReadyParam) {
        val cls = param.classLoader.findClass("com.miui.fliphome.utils.PerformLaunchAction")
        hook(
            cls.method("onStartIntercept", UserHandle::class.java, Intent::class.java, Bundle::class.java, View::class.java),
            replaceResult(false)
        )
    }

    private fun hookRecentsStyle(param: PackageReadyParam) {
        val layoutStyleClass = param.classLoader.findClass("com.miui.fliphome.recents.TaskStackViewLayoutStyle")
        val horizontalClass = param.classLoader.findClass("com.miui.fliphome.recents.TaskStackViewLayoutStyleHorizontal")
        val horizontalConstructor = horizontalClass.getDeclaredConstructor(Context::class.java).also {
            it.isAccessible = true
        }
        val verticalClass = param.classLoader.findClass("com.miui.fliphome.recents.TaskStackViewLayoutStyleVertical")
        val verticalConstructor = verticalClass.getDeclaredConstructor(Context::class.java).also {
            it.isAccessible = true
        }
        hook(layoutStyleClass.method("create", Int::class.java, Context::class.java), Hooker { chain ->
            val context = chain.args[1] as? Context ?: return@Hooker chain.proceed()
            when (prefInt(Prefs.FLIPHOME_RECENTS_STYLE, Prefs.RecentsLayoutStyle.DEFAULT.prefValue)) {
                Prefs.RecentsLayoutStyle.HORIZONTAL.prefValue -> horizontalConstructor.newInstance(context)
                Prefs.RecentsLayoutStyle.VERTICAL.prefValue -> verticalConstructor.newInstance(context)
                else -> chain.proceed()
            }
        })

        setupRecentsLayoutDetection(param.classLoader)
        hookRecentsHorizontalTargetTaskIndex(param)
        hookRecentsHorizontalSwipeDismissDirection(param)
    }

    private fun hookRecentsHorizontalTargetTaskIndex(param: PackageReadyParam) {
        val algorithmClass = param.classLoader.findClass("com.miui.fliphome.recents.views.TaskStackViewsAlgorithmHorizontal")
        hook(
            algorithmClass.method(
                "getTargetTaskViewIndex",
                Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ),
            Hooker { chain ->
                if (recentsLayoutStyle() != Prefs.RecentsLayoutStyle.HORIZONTAL) return@Hooker chain.proceed()
                val quickSwitch = chain.args.getOrNull(0) as? Boolean
                val target = chain.args.getOrNull(1) as? Int
                val running = chain.args.getOrNull(2) as? Int
                if (quickSwitch == false && target == -1 && running != null && running >= 0) {
                    running
                } else {
                    chain.proceed()
                }
            },
        )
    }

    private fun hookRecentsHorizontalSwipeDismissDirection(param: PackageReadyParam) {
        val verticalSwipeClass = param.classLoader.findClass("com.miui.fliphome.recents.views.VerticalSwipe")
        hook(verticalSwipeClass.method("getCurTransY"), Hooker { chain ->
            val result = chain.proceed()
            if (recentsLayoutStyle() != Prefs.RecentsLayoutStyle.HORIZONTAL) return@Hooker result
            val transY = result as? Float ?: return@Hooker result
            val scale = chain.thisObject.getField("mCurScale") as? Float ?: return@Hooker result
            if (scale < 0.999f) {
                -abs(transY)
            } else {
                result
            }
        })
    }

    private fun hookRecentsLongPressMenu(param: PackageReadyParam) {
        setupRecentsLayoutDetection(param.classLoader)
        val taskViewClass = param.classLoader.findClass("com.miui.fliphome.recents.views.TaskView")
        val taskStackViewClass = param.classLoader.findClass(TASK_STACK_VIEW_CLASS)
        val recentsContainerClass = param.classLoader.findClass(RECENTS_CONTAINER_CLASS)
        val taskClass = param.classLoader.findClass("com.android.systemui.shared.recents.model.Task")
        val utilsClass = param.classLoader.findClass("com.miui.fliphome.RecentsAndFSGestureUtils")
        val lockOrUnlockApp = utilsClass.method(
            "lockOrUnlockApp",
            taskClass,
            Boolean::class.javaPrimitiveType!!,
            Runnable::class.java,
        )

        hook(taskViewClass.method("onFinishInflate"), after { chain, result ->
            val taskView = chain.thisObject as? View ?: return@after result
            taskView.isLongClickable = true
            taskView.setOnLongClickListener {
                val task = runCatching { taskView.callMethod("getTask") }.getOrNull()
                    ?: return@setOnLongClickListener false
                runCatching {
                    val packageName = task.packageName() ?: return@runCatching
                    if (recentsLayoutStyle() == Prefs.RecentsLayoutStyle.HORIZONTAL) {
                        taskView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        openApplicationInfo(taskView.context, packageName)
                    } else {
                        showRecentsTaskMenu(taskView, task, packageName, lockOrUnlockApp)
                    }
                }
                true
            }
            result
        })

        hook(taskViewClass.method("onDetachedFromWindow"), after { chain, result ->
            (chain.thisObject as? View)?.let { dismissRecentsTaskMenu(it) }
            result
        })

        hook(taskStackViewClass.method("setVisibility", Int::class.javaPrimitiveType!!), after { chain, result ->
            if (chain.args.firstOrNull() != View.VISIBLE) {
                dismissAllRecentsTaskMenus()
            }
            result
        })

        hook(recentsContainerClass.method("onBackPressed")) { chain ->
            if (hasRecentsTaskMenu()) {
                dismissAllRecentsTaskMenus()
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookAppLongPressMenu(param: PackageReadyParam) {
        val fragmentClass = param.classLoader.findClass("com.miui.FlipLauncherFragment")
        val viewHolderClass = param.classLoader.findClass($$"com.miui.fliphome.adapter.FlipLauncherAdapter$FlipLauncherViewHolder")
        hook(fragmentClass.method("showShortcutMenu", viewHolderClass), after { chain, result ->
            val fragment = chain.thisObject
            val holder = chain.args.firstOrNull() ?: return@after result
            val shortcutInfo = holder.getField("mShortcutInfo") ?: return@after result
            val packageName = shortcutInfo.callString("getPackageName") ?: return@after result
            val componentName = shortcutInfo.callMethod("getComponentName") as? ComponentName
            val user = shortcutInfo.callMethod("getUser") as? UserHandle
            val shortcutMenu = fragment.getField("mShortcutMenu") as? LinearLayout ?: return@after result
            extendNativeAppShortcutMenu(shortcutMenu, fragment, packageName, componentName, user)
            result
        })
    }

    private fun extendNativeAppShortcutMenu(
        shortcutMenu: LinearLayout,
        fragment: Any,
        packageName: String,
        componentName: ComponentName?,
        user: UserHandle?,
    ) {
        val context = shortcutMenu.context
        val originalRow = prepareNativeAppShortcutMenu(shortcutMenu)
        shortcutMenu.setOnClickListener(null)
        shortcutMenu.isClickable = false
        originalRow?.apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                removeNativeAppShortcut(shortcutMenu, fragment)
            }
        }
        shortcutMenu.removeTaggedChildren(APP_SHORTCUT_EXTRA_MENU_TAG)
        shortcutMenu.addView(createNativeAppShortcutMenuRow(
            context = context,
            referenceRow = originalRow,
            title = "应用信息",
            onClick = {
                openApplicationInfo(context, packageName, componentName, user)
                runCatching { fragment.callMethod("hideShortcutMenu") }
            },
        ))
    }

    private fun removeNativeAppShortcut(shortcutMenu: View, fragment: Any) {
        runCatching {
            val shortcut = shortcutMenu.callMethod("getCurrentShortcut") ?: return@runCatching
            val holderPosition = shortcutMenu.callMethod("getHolderPosition") as? Int ?: -1
            fragment.getField("mAppPresenter")?.callMethod("removeApp", shortcut)
            fragment.getField("mAdapter")?.callMethod("onDeleteAppItem", holderPosition, shortcut)
        }
        runCatching { fragment.callMethod("hideShortcutMenu") }
    }

    private fun prepareNativeAppShortcutMenu(shortcutMenu: LinearLayout): LinearLayout? {
        (0 until shortcutMenu.childCount)
            .map { shortcutMenu.getChildAt(it) }
            .filterIsInstance<LinearLayout>()
            .firstOrNull { it.tag == APP_SHORTCUT_ORIGINAL_MENU_TAG }
            ?.let {
                shortcutMenu.orientation = LinearLayout.VERTICAL
                return it
            }
        if ((0 until shortcutMenu.childCount).any { shortcutMenu.getChildAt(it).tag == APP_SHORTCUT_ORIGINAL_MENU_TAG }) {
            shortcutMenu.orientation = LinearLayout.VERTICAL
            return null
        }
        val originalChildren = (0 until shortcutMenu.childCount).map { shortcutMenu.getChildAt(it) }
        if (originalChildren.isEmpty()) {
            shortcutMenu.orientation = LinearLayout.VERTICAL
            return null
        }
        shortcutMenu.removeAllViews()
        shortcutMenu.orientation = LinearLayout.VERTICAL
        val originalRow = LinearLayout(shortcutMenu.context).apply {
            tag = APP_SHORTCUT_ORIGINAL_MENU_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            originalChildren.forEach { child ->
                addView(child, child.layoutParams ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }
        }
        shortcutMenu.addView(originalRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        return originalRow
    }

    private fun ViewGroup.removeTaggedChildren(tag: String) {
        for (index in childCount - 1 downTo 0) {
            if (getChildAt(index).tag == tag) {
                removeViewAt(index)
            }
        }
    }

    private inline fun <reified T : View> ViewGroup.firstChildOfType(): T? {
        return (0 until childCount).firstNotNullOfOrNull { getChildAt(it) as? T }
    }

    private fun ViewGroup.LayoutParams.copyLayoutParams(): LinearLayout.LayoutParams {
        return when (this) {
            is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(this)
            is ViewGroup.MarginLayoutParams -> LinearLayout.LayoutParams(width, height).also {
                it.setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
            }
            else -> LinearLayout.LayoutParams(width, height)
        }
    }

    private fun createNativeAppShortcutMenuRow(
        context: Context,
        referenceRow: LinearLayout?,
        title: String,
        onClick: () -> Unit,
    ): View {
        val referenceIcon = referenceRow?.firstChildOfType<ImageView>()
        val referenceText = referenceRow?.firstChildOfType<TextView>()
        return LinearLayout(context).apply {
            tag = APP_SHORTCUT_EXTRA_MENU_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = referenceRow?.gravity ?: Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(
                referenceRow?.paddingLeft ?: 0,
                referenceRow?.paddingTop ?: 0,
                referenceRow?.paddingRight ?: 0,
                referenceRow?.paddingBottom ?: 0,
            )
            setOnClickListener { onClick() }

            addView(ImageView(context).apply {
                setImageDrawable(context.systemHomeDrawable(APP_DETAILS_ICON))
                scaleType = referenceIcon?.scaleType ?: ImageView.ScaleType.CENTER_INSIDE
                imageTintList = referenceIcon?.imageTintList
                setPadding(
                    referenceIcon?.paddingLeft ?: 0,
                    referenceIcon?.paddingTop ?: 0,
                    referenceIcon?.paddingRight ?: 0,
                    referenceIcon?.paddingBottom ?: 0,
                )
            }, referenceIcon?.layoutParams?.copyLayoutParams() ?: LinearLayout.LayoutParams(24.dp(context), 24.dp(context)))
            addView(TextView(context).apply {
                text = title
                if (referenceText != null) {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, referenceText.textSize)
                    setTextColor(referenceText.textColors)
                    typeface = referenceText.typeface
                    includeFontPadding = referenceText.includeFontPadding
                    gravity = referenceText.gravity
                    maxLines = referenceText.maxLines
                    setPadding(
                        referenceText.paddingLeft,
                        referenceText.paddingTop,
                        referenceText.paddingRight,
                        referenceText.paddingBottom,
                    )
                } else {
                    textSize = 14f
                    setTextColor(context.getColorId("shortcut_menu_text_color", Color.rgb(32, 32, 32)))
                    maxLines = 1
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                }
            }, referenceText?.layoutParams?.copyLayoutParams() ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                leftMargin = 8.dp(context)
            })
            layoutParams = (referenceRow?.layoutParams?.copyLayoutParams()
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
                topMargin = 16.dp(context)
            }
        }
    }

    private fun showRecentsTaskMenu(
        taskView: View,
        task: Any,
        packageName: String,
        lockOrUnlockApp: Method,
    ) {
        val context = taskView.context
        dismissRecentsTaskMenu(taskView)
        val menuState = enterRecentsMenuMode(taskView)
        val cleanupAutoDismiss = installRecentsTaskMenuAutoDismiss(taskView)

        lateinit var popup: PopupWindow
        var isDismissing = false
        val dismissWithAnimation = {
            if (!isDismissing) {
                isDismissing = true
                animateMenuContainerDismiss(popup.contentView) {
                    popup.dismiss()
                }
            }
        }
        val contentView = createRecentsTaskMenuView(
            context = context,
            task = task,
            packageName = packageName,
            taskView = taskView,
            lockOrUnlockApp = lockOrUnlockApp,
            dismissWithAnimation = dismissWithAnimation,
        )
        popup = PopupWindow(context).apply {
            this.contentView = contentView
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            isFocusable = false
            isOutsideTouchable = true
            elevation = 12.dp(context).toFloat()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            setOnDismissListener {
                cleanupAutoDismiss()
                restoreRecentsMenuMode(menuState)
                if (recentsTaskMenus[taskView]?.popup === popup) {
                    recentsTaskMenus.remove(taskView)
                }
            }
        }
        recentsTaskMenus[taskView] = RecentsTaskMenuHandle(popup, dismissWithAnimation)
        popup.showAtLocation(taskView.rootView, Gravity.TOP or Gravity.START, 0, 0)
        taskView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun installRecentsTaskMenuAutoDismiss(taskView: View): () -> Unit {
        val rootView = taskView.rootView
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                dismissRecentsTaskMenu(taskView)
            }
        }
        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!taskView.isAttachedToWindow || !taskView.isShown || !rootView.isShown) {
                dismissRecentsTaskMenu(taskView)
            }
        }
        taskView.addOnAttachStateChangeListener(attachListener)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        return {
            taskView.removeOnAttachStateChangeListener(attachListener)
            rootView.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(globalLayoutListener)
        }
    }

    private fun dismissRecentsTaskMenu(taskView: View) {
        recentsTaskMenus[taskView]?.dismiss()
    }

    private fun dismissAllRecentsTaskMenus() {
        recentsTaskMenus.keys.toList().forEach { dismissRecentsTaskMenu(it) }
    }

    private fun hasRecentsTaskMenu(): Boolean {
        return recentsTaskMenus.values.any { it.popup.isShowing }
    }

    private fun createRecentsTaskMenuView(
        context: Context,
        task: Any,
        packageName: String,
        taskView: View,
        lockOrUnlockApp: Method,
        dismissWithAnimation: () -> Unit,
    ): View {
        val isLocked = task.isLocked()
        return FrameLayout(context).apply {
            isClickable = true
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismissWithAnimation() }

            val itemSize = 56.dp(context)
            val minMargin = 8.dp(context)
            val positions = calculateRecentsMenuPositions(context, taskView, itemSize, minMargin)
            addView(
                createMenuItem(
                    context = context,
                    iconRes = context.drawableId(
                        if (isLocked) "ic_task_unlock" else "ic_task_lock",
                        if (isLocked) android.R.drawable.ic_menu_revert else android.R.drawable.ic_lock_idle_lock,
                    ),
                    contentDescription = if (isLocked) "解锁" else "锁定",
                    itemSize = itemSize,
                    pivotX = positions[0].pivotX,
                    pivotY = positions[0].pivotY,
                    onClick = {
                        toggleTaskLock(taskView, task, lockOrUnlockApp, !isLocked)
                        dismissWithAnimation()
                    },
                ),
                FrameLayout.LayoutParams(itemSize, itemSize).apply {
                    leftMargin = positions[0].x
                    topMargin = positions[0].y
                },
            )
            addView(
                createMenuItem(
                    context = context,
                    iconRes = context.drawableId("ic_task_setting", android.R.drawable.ic_menu_info_details),
                    contentDescription = "应用信息",
                    itemSize = itemSize,
                    pivotX = positions[1].pivotX,
                    pivotY = positions[1].pivotY,
                    onClick = {
                        openApplicationInfo(context, packageName)
                        dismissRecentsToHome(taskView)
                        dismissWithAnimation()
                    },
                ),
                FrameLayout.LayoutParams(itemSize, itemSize).apply {
                    leftMargin = positions[1].x
                    topMargin = positions[1].y
                },
            )
        }
    }

    private fun calculateRecentsMenuPositions(
        context: Context,
        taskView: View,
        itemSize: Int,
        minMargin: Int,
    ): List<MenuItemPosition> {
        val anchor = createRecentsMenuAnchor(context, taskView, itemSize, minMargin)
        val centerBaseY = (anchor.taskCenterY + anchor.itemSize * 0.14f).toInt()
        val centerFirstY = (centerBaseY - anchor.itemSize * 1.2f).toInt()
        val rawPositions = when {
            centerFirstY < anchor.minMargin -> {
                val y1 = (anchor.taskBottom - anchor.itemSize * 0.3f).toInt()
                val y2 = (y1 + anchor.itemSize * 0.8f).toInt()
                if (anchor.showAtRight) {
                    listOf(
                        anchor.positionNearRight(y1, 0.5f, anchor.taskBottom - y1 - anchor.itemSize * 2),
                        anchor.positionNearRight(y2, -0.5f, anchor.taskBottom - y2 - anchor.itemSize * 2),
                    )
                } else {
                    listOf(
                        anchor.positionNearLeft(y1, -1.5f, anchor.taskBottom - y1 - anchor.itemSize * 2),
                        anchor.positionNearLeft(y2, -0.5f, anchor.taskBottom - y2 - anchor.itemSize * 2),
                    )
                }
            }
            centerBaseY + anchor.itemSize > anchor.screenHeight - anchor.minMargin -> {
                val y2 = (anchor.taskTop - anchor.itemSize * 0.7f).toInt()
                val y1 = (y2 - anchor.itemSize * 0.8f).toInt()
                if (anchor.showAtRight) {
                    listOf(
                        anchor.positionNearRight(y1, -0.5f, anchor.taskTop - y1 + anchor.itemSize * 2),
                        anchor.positionNearRight(y2, 0.5f, anchor.taskTop - y2 + anchor.itemSize * 2),
                    )
                } else {
                    listOf(
                        anchor.positionNearLeft(y1, -0.5f, anchor.taskTop - y1 + anchor.itemSize * 2),
                        anchor.positionNearLeft(y2, -1.5f, anchor.taskTop - y2 + anchor.itemSize * 2),
                    )
                }
            }
            else -> {
                val x = if (anchor.showAtRight) {
                    (anchor.taskRight + anchor.itemSize * 0.8f).toInt()
                } else {
                    (anchor.taskLeft - anchor.itemSize * 1.8f).toInt()
                }
                listOf(
                    anchor.positionCentered(x, centerFirstY),
                    anchor.positionCentered(x, centerBaseY),
                )
            }
        }
        val maxX = (anchor.screenWidth - anchor.itemSize - anchor.minMargin).coerceAtLeast(anchor.minMargin)
        val maxY = (anchor.screenHeight - anchor.itemSize - anchor.minMargin).coerceAtLeast(anchor.minMargin)
        return rawPositions.map { position ->
            position.copy(
                x = position.x.coerceIn(anchor.minMargin, maxX),
                y = position.y.coerceIn(anchor.minMargin, maxY),
            )
        }
    }

    private fun createRecentsMenuAnchor(
        context: Context,
        taskView: View,
        itemSize: Int,
        minMargin: Int,
    ): RecentsMenuAnchor {
        val rootView = taskView.rootView
        val rootGroup = rootView as? ViewGroup
        val taskBounds = Rect(0, 0, taskView.width, taskView.height)
        rootGroup?.offsetDescendantRectToMyCoords(taskView, taskBounds)
        val screenWidth = rootView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val screenHeight = rootView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val stackBounds = (taskView.findTaskStackView() as? View)?.let { stackView ->
            Rect(0, 0, stackView.width, stackView.height).also {
                rootGroup?.offsetDescendantRectToMyCoords(stackView, it)
            }
        }
        val taskCenterX = taskBounds.left + taskView.width / 2
        return RecentsMenuAnchor(
            taskLeft = taskBounds.left,
            taskTop = taskBounds.top,
            taskRight = taskBounds.right,
            taskBottom = taskBounds.bottom,
            taskCenterY = taskBounds.top + taskView.height / 2,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            itemSize = itemSize,
            minMargin = minMargin,
            showAtRight = taskCenterX <= (stackBounds?.centerX() ?: (screenWidth / 2)),
        )
    }

    private fun RecentsMenuAnchor.positionNearRight(y: Int, xOffsetItems: Float, pivotY: Int): MenuItemPosition {
        val x = (taskRight + itemSize * xOffsetItems).toInt()
        return MenuItemPosition(
            x = x,
            y = y,
            pivotX = taskRight - itemSize * 2 - x,
            pivotY = pivotY,
        )
    }

    private fun RecentsMenuAnchor.positionNearLeft(y: Int, xOffsetItems: Float, pivotY: Int): MenuItemPosition {
        val x = (taskLeft + itemSize * xOffsetItems).toInt()
        return MenuItemPosition(
            x = x,
            y = y,
            pivotX = taskLeft + itemSize * 2 - x,
            pivotY = pivotY,
        )
    }

    private fun RecentsMenuAnchor.positionCentered(x: Int, y: Int): MenuItemPosition {
        return MenuItemPosition(
            x = x,
            y = y,
            pivotX = if (showAtRight) taskRight - itemSize - x else taskLeft + itemSize - x,
            pivotY = taskCenterY - y,
        )
    }

    private fun animateMenuContainerDismiss(contentView: View?, endAction: () -> Unit) {
        val container = contentView as? ViewGroup ?: return endAction()
        val menuItems = (0 until container.childCount).map { container.getChildAt(it) }
            .filter { it is ImageView || it is LinearLayout }
        if (menuItems.isEmpty()) {
            endAction()
            return
        }
        var remaining = menuItems.size
        menuItems.forEach { item ->
            item.animate().cancel()
            item.animate()
                .alpha(0f)
                .scaleX(0.6f)
                .scaleY(0.6f)
                .setDuration(125L)
                .withEndAction {
                    remaining -= 1
                    if (remaining == 0) {
                        endAction()
                    }
                }
                .start()
        }
    }

    private fun openApplicationInfo(
        context: Context,
        packageName: String,
        componentName: ComponentName? = null,
        user: UserHandle? = null,
    ) {
        if (componentName != null && user != null) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val started = runCatching {
                launcherApps?.startAppDetailsActivity(componentName, user, null, null)
            }.isSuccess
            if (started) return
        }
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun enterRecentsMenuMode(taskView: View): RecentsMenuState {
        val taskStackView = taskView.findTaskStackView()
        val taskViews = taskStackView?.taskViews().orEmpty().map { view ->
            TaskViewState(
                view = view,
                visibility = view.visibility,
                alpha = view.alpha,
                translationZ = view.translationZ,
                importantForAccessibility = view.importantForAccessibility,
            )
        }
        runCatching { taskStackView?.callMethod("setIsShowingMenu", true) }
        taskViews.forEach { state ->
            state.view.animate().cancel()
            if (state.view === taskView) {
                state.view.translationZ = 10f
            } else {
                state.view.alpha = 0f
                state.view.visibility = View.INVISIBLE
                state.view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }
        return RecentsMenuState(taskStackView, taskViews)
    }

    private fun restoreRecentsMenuMode(state: RecentsMenuState) {
        runCatching { state.taskStackView?.callMethod("setIsShowingMenu", false) }
        state.taskViews.forEach { viewState ->
            viewState.view.animate().cancel()
            viewState.view.visibility = viewState.visibility
            viewState.view.alpha = viewState.alpha
            viewState.view.translationZ = viewState.translationZ
            viewState.view.importantForAccessibility = viewState.importantForAccessibility
        }
    }

    private fun createMenuItem(
        context: Context,
        iconRes: Int,
        contentDescription: String,
        itemSize: Int,
        pivotX: Int,
        pivotY: Int,
        onClick: () -> Unit,
    ): View {
        return ImageView(context).apply {
            isClickable = true
            isFocusable = true
            this.contentDescription = contentDescription
            setPadding(14.dp(context), 14.dp(context), 14.dp(context), 14.dp(context))
            background = context.drawableOrCircle(RECENTS_MENU_BACKGROUND, itemSize)
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { onClick() }
            this.pivotX = pivotX.toFloat()
            this.pivotY = pivotY.toFloat()
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
        }
    }

    private fun toggleTaskLock(
        taskView: View,
        task: Any,
        lockOrUnlockApp: Method,
        toLock: Boolean,
    ) {
        runCatching {
            lockOrUnlockApp.invoke(null, task, toLock, Runnable {
                runCatching {
                    taskView.callMethod("getHeaderView")?.callMethod("showOrHideLockImageView", toLock)
                    taskView.performHapticFeedback(1)
                }
            })
        }
    }

    private fun Any.packageName(): String? {
        return listOf("key", "cti1Key", "cti2Key").firstNotNullOfOrNull { fieldName ->
            val key = runCatching { getField(fieldName) }.getOrNull()
            (runCatching { key?.callMethod("getComponent") }.getOrNull() as? ComponentName)?.packageName
        }
    }

    private fun Any.isLocked(): Boolean = getField("isLocked") as? Boolean ?: false

    private fun Any.callString(methodName: String): String? {
        return runCatching { callMethod(methodName) as? String }.getOrNull()
    }

    private var getFlipAppInstance: Method? = null
    private var getBaseGesture: Method? = null
    private var getLayoutStyle: Method? = null
    private var horizontalStyleClass: Class<*>? = null
    private var verticalStyleClass: Class<*>? = null
    private var recentsLayoutDetectionReady = false

    private fun setupRecentsLayoutDetection(cl: ClassLoader) {
        if (recentsLayoutDetectionReady) return

        val appClass = cl.loadClass("com.miui.fliphome.FlipApplication")
        getFlipAppInstance = appClass.getMethod("getInstance")
        getBaseGesture = appClass.getMethod("getBaseGestureImpl")
        getLayoutStyle = cl.loadClass("com.miui.fliphome.gesture.BaseGestureImpl")
            .getMethod("getTaskStackViewLayoutStyle")
        horizontalStyleClass = cl.loadClass("com.miui.fliphome.recents.TaskStackViewLayoutStyleHorizontal")
        verticalStyleClass = cl.loadClass("com.miui.fliphome.recents.TaskStackViewLayoutStyleVertical")
        recentsLayoutDetectionReady = true
    }

    private fun recentsLayoutStyle(): Prefs.RecentsLayoutStyle {
        if (!recentsLayoutDetectionReady) return Prefs.RecentsLayoutStyle.DEFAULT
        return runCatching {
            val app = getFlipAppInstance?.invoke(null) ?: return Prefs.RecentsLayoutStyle.DEFAULT
            val gesture = getBaseGesture?.invoke(app) ?: return Prefs.RecentsLayoutStyle.DEFAULT
            val style = getLayoutStyle?.invoke(gesture)
            if (horizontalStyleClass?.isInstance(style) == true) Prefs.RecentsLayoutStyle.HORIZONTAL
            else if (verticalStyleClass?.isInstance(style) == true) Prefs.RecentsLayoutStyle.VERTICAL
            else Prefs.RecentsLayoutStyle.DEFAULT
        }.getOrDefault(Prefs.RecentsLayoutStyle.DEFAULT)
    }

    private fun View.findTaskStackView(): Any? {
        return findParentByClassName(TASK_STACK_VIEW_CLASS)
    }

    private fun View.findRecentsContainer(): Any? {
        return findParentByClassName(RECENTS_CONTAINER_CLASS)
    }

    private fun View.findParentByClassName(className: String): Any? {
        var currentParent = this.parent
        while (currentParent != null) {
            if (currentParent.javaClass.name == className) {
                return currentParent
            }
            currentParent = currentParent.parent
        }
        return null
    }

    private fun dismissRecentsToHome(taskView: View) {
        runCatching {
            taskView.findRecentsContainer()?.callMethod("dismissRecentsToHome", true)
        }
    }

    private fun Any.taskViews(): List<View> {
        return (runCatching { callMethod("getTaskViews") }.getOrNull() as? Iterable<*>)
            ?.filterIsInstance<View>()
            .orEmpty()
    }

    private fun Context.drawableId(name: String, fallback: Int): Int {
        return resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: fallback
    }

    private fun Context.systemHomeDrawable(name: String): android.graphics.drawable.Drawable? {
        return runCatching {
            val homeContext = createPackageContext(SYSTEM_HOME_PACKAGE, 0)
            val resId = homeContext.resources.getIdentifier(name, "drawable", SYSTEM_HOME_PACKAGE)
            if (resId != 0) homeContext.getDrawable(resId) else null
        }.getOrNull()
    }

    private fun Context.getColorId(name: String, fallback: Int): Int {
        val resId = resources.getIdentifier(name, "color", packageName)
        return if (resId != 0) getColor(resId) else fallback
    }

    private fun Context.drawableOrCircle(name: String, itemSize: Int): android.graphics.drawable.Drawable {
        val resId = drawableId(name, 0)
        if (resId != 0) {
            return getDrawable(resId)!!
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = itemSize / 2f
            setColor(Color.argb(235, 245, 245, 245))
        }
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
