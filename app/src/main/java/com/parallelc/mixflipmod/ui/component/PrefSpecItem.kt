package com.parallelc.mixflipmod.ui.component

import android.content.Context
import android.content.SharedPreferences
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.parallelc.mixflipmod.model.OptionValue
import com.parallelc.mixflipmod.model.PrefSpec
import com.parallelc.mixflipmod.ui.util.checkScope
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt

private data class UiOption(val value: OptionValue, val label: String)

@Composable
fun PrefSpecItem(
    spec: PrefSpec,
    prefs: SharedPreferences,
    xposedService: XposedService?,
    scopePackage: String,
) {
    when (spec) {
        is PrefSpec.Switch -> {
            val checked = remember(prefs, spec.prefKey) { mutableStateOf(prefs.getBoolean(spec.prefKey, false)) }
            SwitchPreference(
                title = stringResource(spec.titleRes),
                summary = spec.summaryRes?.let { stringResource(it) },
                checked = checked.value,
                onCheckedChange = {
                    checked.value = it
                    prefs.edit { putBoolean(spec.prefKey, it) }
                    runCatching { checkScope(xposedService, scopePackage, it) }
                },
            )
            if (checked.value) {
                spec.children.forEach {
                    PrefSpecItem(
                        spec = it,
                        prefs = prefs,
                        xposedService = xposedService,
                        scopePackage = scopePackage,
                    )
                }
            }
        }
        is PrefSpec.IntInput -> {
            val stored = remember { mutableIntStateOf(prefs.getInt(spec.prefKey, spec.defaultValue)) }
            val maxValue = spec.maxValue ?: spec.minValue.coerceAtLeast(spec.defaultValue)
            val slider: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Slider(
                        value = stored.intValue.toFloat(),
                        onValueChange = {
                            val value = it.roundToInt().coerceIn(spec.minValue, maxValue)
                            if (stored.intValue != value) {
                                stored.intValue = value
                                prefs.edit { putInt(spec.prefKey, value) }
                            }
                        },
                        valueRange = spec.minValue.toFloat()..maxValue.toFloat(),
                        steps = (maxValue - spec.minValue - 1).coerceAtLeast(0),
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stored.intValue.toString(),
                        modifier = Modifier.width(40.dp),
                        color = colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                    )
                }
            }
            if (spec.titleRes == null) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    slider()
                }
            } else {
                BasicComponent(
                    title = stringResource(spec.titleRes),
                    summary = spec.summaryRes?.let { stringResource(it) } ?: stored.intValue.toString(),
                    bottomAction = { slider() },
                )
            }
        }
        is PrefSpec.StringInput -> {
            val stored = remember { mutableStateOf(prefs.getString(spec.prefKey, spec.defaultValue) ?: spec.defaultValue) }
            BasicComponent(
                title = stringResource(spec.titleRes),
                summary = spec.summaryRes?.let { stringResource(it) },
                bottomAction = {
                    TextField(
                        value = stored.value,
                        onValueChange = {
                            stored.value = it
                            prefs.edit { putString(spec.prefKey, it.trim()) }
                        },
                        label = stringResource(spec.titleRes),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
        is PrefSpec.OptionSelect -> {
            val context = LocalContext.current
            val pm = context.packageManager
            val staticOptions = spec.options.map { UiOption(it.value, stringResource(it.labelRes)) }
            val dynamicOptions = when (spec.source) {
                PrefSpec.OptionSelect.Source.Static -> emptyList()
                PrefSpec.OptionSelect.Source.InputMethods -> {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    remember {
                        imm.inputMethodList
                            .groupBy { it.serviceInfo.packageName }
                            .map { (pkg, methods) -> UiOption(OptionValue.Str(pkg), methods.first().loadLabel(pm).toString()) }
                            .sortedBy { it.label.lowercase() }
                    }
                }
            }
            val options = staticOptions + dynamicOptions
            var stored by remember {
                val initial = when (spec.defaultValue) {
                    is OptionValue.Str -> OptionValue.Str(prefs.getString(spec.prefKey, spec.defaultValue.value) ?: spec.defaultValue.value)
                    is OptionValue.IntVal -> OptionValue.IntVal(prefs.getInt(spec.prefKey, spec.defaultValue.value))
                    is OptionValue.FloatVal -> OptionValue.FloatVal(prefs.getFloat(spec.prefKey, spec.defaultValue.value))
                }
                mutableStateOf(initial)
            }
            var showDropdown by remember { mutableStateOf(false) }
            val selectedOption = options.firstOrNull { it.value == stored }
                ?: options.firstOrNull { it.value == spec.defaultValue }
            BasicComponent(
                title = stringResource(spec.titleRes),
                summary = spec.summaryRes?.let { stringResource(it) },
                endActions = {
                    Box {
                        OverlayListPopup(
                            show = showDropdown,
                            alignment = PopupPositionProvider.Align.End,
                            onDismissRequest = { showDropdown = false },
                        ) {
                            ListPopupColumn {
                                options.forEachIndexed { index, option ->
                                    DropdownImpl(
                                        text = option.label,
                                        optionSize = options.size,
                                        isSelected = stored == option.value,
                                        index = index,
                                        onSelectedIndexChange = {
                                            stored = option.value
                                            when (option.value) {
                                                is OptionValue.Str -> prefs.edit { putString(spec.prefKey, option.value.value) }
                                                is OptionValue.IntVal -> prefs.edit { putInt(spec.prefKey, option.value.value) }
                                                is OptionValue.FloatVal -> prefs.edit { putFloat(spec.prefKey, option.value.value) }
                                            }
                                            showDropdown = false
                                        },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.clickable { showDropdown = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = selectedOption?.label ?: "",
                                color = if (stored == spec.defaultValue) colorScheme.onSurfaceVariantSummary else colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.size(8.dp))
                            DropdownArrowEndAction(actionColor = colorScheme.onSurfaceVariantActions)
                        }
                    }
                },
                holdDownState = showDropdown,
            )
        }
    }
}
