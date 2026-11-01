package com.tpkarras.mirror2rearultra

import android.app.Activity
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.CompoundButton
import android.view.ViewTreeObserver
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** The Miuix palette for the Compose rows: the stock one, or the wallpaper's when Monet is on. */
internal fun miuixMode(context: android.content.Context, dark: Boolean): ColorSchemeMode =
    if (AppLook.monet(context)) { if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight }
    else { if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light }

/** The one way a row draws its leading icon: a coloured tile with a white glyph, as in HyperOS Settings. */
@androidx.compose.runtime.Composable
private fun RowIcon(icon: Int) {
    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(30.dp))
}

/** Compose interoperability for existing navigation rows; the original listener remains authoritative. */
object MiuixRows {
    @JvmStatic fun installSwitches(activity: Activity, vararg ids: Int) {
        ids.forEach { id -> installSwitch(activity, id, 0) }
    }

    /** Like [installSwitches] for one switch, with a drawing at the start of the row. */
    @JvmStatic fun installSwitchWithIcon(activity: Activity, id: Int, icon: Int) = installSwitch(activity, id, icon)

    private fun installSwitch(activity: Activity, id: Int, icon: Int) {
        run {
            val original = activity.findViewById<CompoundButton>(id) ?: return
            val parent = original.parent as? ViewGroup ?: return
            val index = parent.indexOfChild(original)
            var checked by mutableStateOf(original.isChecked)
            var enabled by mutableStateOf(original.isEnabled)
            val label = original.text.toString()
            // A quieter line under the title, when the layout or the activity gave one.
            val hint = original.tag as? String
            val row = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
                    val controller = remember(mode) { ThemeController(mode) }
                    MiuixTheme(controller = controller) {
                        SwitchPreference(title = label, summary = hint, checked = checked, enabled = enabled,
                            startAction = if (icon == 0) null else ({ RowIcon(icon) }),
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
    /**
     * A choice shown as a row with its current value at the end. The list that
     * opens is the [HyperValueRow]'s own, which stays in the layout, hidden, as
     * the holder of the entries and the listener.
     */
    @JvmStatic fun installChoiceRow(activity: Activity, id: Int, icon: Int) {
        val original = activity.findViewById<HyperValueRow>(id) ?: return
        val parent = original.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(original)
        val label = original.title.toString()
        var value by mutableStateOf(original.valueText.toString())
        var enabled by mutableStateOf(original.isEnabled)
        lateinit var row: ComposeView
        row = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
                val controller = remember(mode) { ThemeController(mode) }
                MiuixTheme(controller = controller) {
                    ArrowPreference(title = label, enabled = enabled, onClick = { original.showChooserFrom(row) },
                        startAction = if (icon == 0) null else ({ RowIcon(icon) }),
                        endActions = {
                            Text(value, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        })
                }
            }
        }
        // The original keeps its place at no height: code that shows or hides it
        // (a section that folds open, say) still decides whether the row is seen.
        original.layoutParams = original.layoutParams.apply { height = 0 }
        original.alpha = 0f
        original.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        row.visibility = if (original.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        parent.addView(row, index, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val observer = ViewTreeObserver.OnPreDrawListener {
            val shown = original.valueText.toString()
            if (value != shown) value = shown
            if (enabled != original.isEnabled) enabled = original.isEnabled
            val wanted = if (original.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            if (row.visibility != wanted) row.visibility = wanted
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

    /** A label row with a drawing at its start, for the title of a group of controls. */
    @JvmStatic fun installTitleWithIcon(activity: Activity, id: Int, icon: Int) {
        val original = activity.findViewById<TextView>(id) ?: return
        val parent = original.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(original)
        val label = original.text.toString()
        val row = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
                val controller = remember(mode) { ThemeController(mode) }
                MiuixTheme(controller = controller) {
                    BasicComponent(title = label, startAction = { RowIcon(icon) })
                }
            }
        }
        original.visibility = View.GONE
        parent.addView(row, index, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * A switch that also folds the rows under it away: tapping the row opens or
     * closes them, tapping the switch turns the feature on or off. The rows start
     * folded, so what only matters once the feature is on stays out of the way.
     */
    @JvmStatic fun installExpandableSwitch(activity: Activity, id: Int, icon: Int, vararg childIds: Int) {
        val original = activity.findViewById<CompoundButton>(id) ?: return
        val parent = original.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(original)
        val children = childIds.toList().mapNotNull { activity.findViewById<View>(it) }
        var checked by mutableStateOf(original.isChecked)
        var enabled by mutableStateOf(original.isEnabled)
        var expanded by mutableStateOf(false)
        children.forEach { it.visibility = View.GONE }
        val label = original.text.toString()
        val row = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
                val controller = remember(mode) { ThemeController(mode) }
                MiuixTheme(controller = controller) {
                    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "options")
                    BasicComponent(
                        title = label,
                        startAction = if (icon == 0) null else ({ RowIcon(icon) }),
                        endActions = {
                            Image(painterResource(R.drawable.ic_hyper_expand), contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp).size(24.dp).rotate(turn),
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions))
                            Switch(checked = checked, enabled = enabled, onCheckedChange = { value ->
                                original.isChecked = value
                                checked = original.isChecked
                                enabled = original.isEnabled
                            })
                        },
                        onClick = {
                            expanded = !expanded
                            children.forEach { it.visibility = if (expanded) View.VISIBLE else View.GONE }
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

    /**
     * A row that opens a section in place instead of another screen: tapping it
     * shows or hides the view [sectionId], and the arrow turns to say which.
     */
    @JvmStatic fun installExpandableRow(activity: Activity, id: Int, icon: Int, sectionId: Int) {
        val original = activity.findViewById<TextView>(id) ?: return
        val section = activity.findViewById<View>(sectionId) ?: return
        val parent = original.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(original)
        val label = original.text.toString()
        // The options belong to the row's own card, not to a card of their own.
        (section.parent as? ViewGroup)?.removeView(section)
        (section as? ViewGroup)?.let { holder ->
            for (i in 0 until holder.childCount) {
                holder.getChildAt(i).apply { background = null; clipToOutline = false }
            }
        }
        section.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val summary = activity.resources.getIdentifier(
            activity.resources.getResourceEntryName(id).removeSuffix("_button") + "_summary",
            "id", activity.packageName).let { activity.findViewById<View>(it) }
        // Several rows may share one card, so the options go right under their own row.
        val after = if (summary != null && summary.parent === parent) parent.indexOfChild(summary) else index
        parent.addView(section, after + 1)
        var expanded by mutableStateOf(section.visibility == View.VISIBLE)
        summary?.visibility = if (expanded) View.GONE else View.VISIBLE
        val row = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
                val controller = remember(mode) { ThemeController(mode) }
                MiuixTheme(controller = controller) {
                    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "section")
                    BasicComponent(
                        title = label,
                        startAction = {
                            RowIcon(icon)
                        },
                        endActions = {
                            Image(painterResource(R.drawable.ic_hyper_expand), contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp).size(24.dp).rotate(turn),
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions))
                        },
                        onClick = {
                            expanded = !expanded
                            // The cards below slide to the new height while the options fade.
                            TransitionManager.beginDelayedTransition(
                                (parent.parent as? ViewGroup) ?: parent,
                                TransitionSet().addTransition(Fade()).addTransition(ChangeBounds())
                                    .setDuration(240))
                            section.visibility = if (expanded) View.VISIBLE else View.GONE
                            summary?.visibility = if (expanded) View.GONE else View.VISIBLE
                        })
                }
            }
        }
        original.visibility = View.GONE
        parent.addView(row, index, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** A row built in code, for lists that follow the data: a tile, a title and a chevron. */
    @JvmStatic fun arrowRow(activity: Activity, title: String, icon: Int, onClick: Runnable): View =
        ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
                val controller = remember(mode) { ThemeController(mode) }
                MiuixTheme(controller = controller) {
                    ArrowPreference(title = title, onClick = { onClick.run() },
                        startAction = { RowIcon(icon) })
                }
            }
        }

    @JvmStatic fun install(activity: Activity, vararg ids: Int) {
        ids.forEach { id -> installRow(activity, id, 0) }
    }

    /** Like [install], with a drawing at the start of the row. */
    @JvmStatic fun installWithIcon(activity: Activity, id: Int, icon: Int) = installRow(activity, id, icon)

    private fun installRow(activity: Activity, id: Int, icon: Int) {
        run {
            val original = activity.findViewById<TextView>(id) ?: return
            val parent = original.parent as? ViewGroup ?: return
            val index = parent.indexOfChild(original)
            val label = original.text.toString()
            val row = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
                    val controller = remember(mode) { ThemeController(mode) }
                    MiuixTheme(controller = controller) {
                        ArrowPreference(title = label, onClick = { original.performClick() },
                            startAction = if (icon == 0) null else ({
                                RowIcon(icon)
                            }))
                    }
                }
            }
            original.visibility = View.GONE
            parent.addView(row, index, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
