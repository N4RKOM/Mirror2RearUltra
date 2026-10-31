package com.tpkarras.mirror2rearultra

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.CompoundButton
import android.view.ViewTreeObserver
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Compose interoperability for existing navigation rows; the original listener remains authoritative. */
object MiuixRows {
    @JvmStatic fun installSwitches(activity: Activity, vararg ids: Int) {
        ids.forEach { id ->
            val original = activity.findViewById<CompoundButton>(id) ?: return@forEach
            val parent = original.parent as? ViewGroup ?: return@forEach
            val index = parent.indexOfChild(original)
            var checked by mutableStateOf(original.isChecked)
            var enabled by mutableStateOf(original.isEnabled)
            val label = original.text.toString()
            val row = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val mode = if (isSystemInDarkTheme()) ColorSchemeMode.Dark else ColorSchemeMode.Light
                    val controller = remember(mode) { ThemeController(mode) }
                    MiuixTheme(controller = controller) {
                        SwitchPreference(title = label, checked = checked, enabled = enabled,
                            onCheckedChange = { value ->
                                original.isChecked = value
                                checked = original.isChecked
                                enabled = original.isEnabled
                            })
                    }
                }
            }
            original.visibility = View.GONE
            parent.addView(row, index, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val observer = ViewTreeObserver.OnPreDrawListener {
                if (checked != original.isChecked) checked = original.isChecked
                if (enabled != original.isEnabled) enabled = original.isEnabled
                true
            }
            row.viewTreeObserver.addOnPreDrawListener(observer)
            row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) {
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(observer)
                }
            })
        }
    }
    @JvmStatic fun install(activity: Activity, vararg ids: Int) {
        ids.forEach { id ->
            val original = activity.findViewById<TextView>(id) ?: return@forEach
            val parent = original.parent as? ViewGroup ?: return@forEach
            val index = parent.indexOfChild(original)
            val label = original.text.toString()
            val row = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val mode = if (isSystemInDarkTheme()) ColorSchemeMode.Dark else ColorSchemeMode.Light
                    val controller = remember(mode) { ThemeController(mode) }
                    MiuixTheme(controller = controller) {
                        ArrowPreference(title = label, onClick = { original.performClick() })
                    }
                }
            }
            original.visibility = View.GONE
            parent.addView(row, index, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
