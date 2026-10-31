package com.tpkarras.mirror2rearultra

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.color.MaterialColors
import androidx.core.graphics.ColorUtils
import java.util.function.IntConsumer
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The bottom bar: a pill of three icons where the chosen one is held in a capsule and
 * carries its name. It slides away while the list scrolls down and returns when it scrolls
 * up. With [frosted] the pill is see-through and [backdrop] draws the blurred list behind
 * it; otherwise it is Miuix's solid pill.
 */
class MiuixNavigation(
    private val view: ComposeView, selected: Int, onSelect: IntConsumer,
    private val backdrop: FrostedBackdropView?, private val list: View?, frosted: Boolean,
) {
    private var selectedSection by mutableIntStateOf(selected)
    private var frostedOn by mutableStateOf(frosted)
    private var hidden = false

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            val context = LocalContext.current
            val mode = miuixMode(context, isSystemInDarkTheme())
            val controller = remember(mode) { ThemeController(mode) }
            MiuixTheme(controller = controller) {
                val items: @Composable () -> Unit = {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val icons = listOf(R.drawable.ic_nav_widgets, R.drawable.ic_nav_mirror, R.drawable.ic_nav_settings)
                        val labels = listOf(R.string.dashboard_widgets_title, R.string.nav_mirror, R.string.nav_settings)
                        icons.forEachIndexed { index, icon ->
                            NavItem(selectedSection == index, ImageVector.vectorResource(icon),
                                stringResource(labels[index])) { onSelect.accept(index) }
                        }
                    }
                }
                if (frostedOn && backdrop != null && list != null) {
                    val tint = ColorUtils.setAlphaComponent(
                        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0), 170)
                    backdrop.bind(list,
                        MaterialColors.getColor(context, android.R.attr.colorBackground, 0), tint,
                        MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0))
                    backdrop.visibility = View.VISIBLE
                    FloatingNavigationBar(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            // The bar's slide is applied by the backdrop's own shift, so the
                            // pill is recorded where it rests.
                            val slide = view.translationY
                            backdrop.setPill(bounds.left, bounds.top - slide, bounds.right, bounds.bottom - slide)
                        },
                        color = Color.Transparent, shadowElevation = 0.dp,
                        defaultWindowInsetsPadding = false, showDivider = false,
                    ) { items() }
                } else {
                    backdrop?.visibility = View.GONE
                    FloatingNavigationBar(defaultWindowInsetsPadding = false, showDivider = true) { items() }
                }
            }
        }
    }

    fun select(index: Int) { selectedSection = index }

    fun setFrosted(on: Boolean) { frostedOn = on }

    /**
     * Slides the bar off the bottom of the screen, or back. The frosted backdrop follows it
     * so the glass does not stay behind.
     */
    fun setHidden(hide: Boolean) {
        if (hidden == hide) return
        hidden = hide
        val parent = view.parent as? View ?: return
        val target = if (hide) (parent.height - view.top).toFloat() else 0f
        view.animate().translationY(target).setDuration(220)
            .setUpdateListener { backdrop?.setShift(view.translationY) }
            .start()
    }

    /**
     * Hides the bar while [scroll] moves down and shows it again as soon as it moves up, at
     * the top of the list, or at its very end.
     */
    fun followScroll(scroll: androidx.core.widget.NestedScrollView) {
        var last = scroll.scrollY
        val threshold = 6 * scroll.resources.displayMetrics.density
        scroll.viewTreeObserver.addOnScrollChangedListener {
            val y = scroll.scrollY
            val delta = y - last
            val atEnd = y >= (scroll.getChildAt(0)?.height ?: 0) - scroll.height - 1
            when {
                y <= 0 || atEnd || delta < -threshold -> setHidden(false)
                delta > threshold -> setHidden(true)
            }
            if (kotlin.math.abs(delta) > threshold || y <= 0) last = y
        }
    }
}

@Composable
private fun NavItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent)
    val content by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurfaceVariantActions)
    Row(
        Modifier.clip(CircleShape)
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(24.dp))
        // The name grows out of the icon: the width eases open while the text fades in a
        // beat later, and on the way out it fades first so it is never cut off mid-letter.
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(tween(300, easing = FastOutSlowInEasing), Alignment.Start) +
                fadeIn(tween(200, delayMillis = 90)),
            exit = shrinkHorizontally(tween(260, easing = FastOutSlowInEasing), Alignment.Start) +
                fadeOut(tween(100)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(label, color = content, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}
