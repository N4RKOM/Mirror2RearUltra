package com.tpkarras.mirror2rearultra

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.function.IntConsumer
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MiuixNavigation(view: ComposeView, selected: Int, onSelect: IntConsumer) {
    private var selectedSection by mutableIntStateOf(selected)
    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            val mode = if (isSystemInDarkTheme()) ColorSchemeMode.Dark else ColorSchemeMode.Light
            val controller = remember(mode) { ThemeController(mode) }
            MiuixTheme(controller = controller) {
                FloatingNavigationBar(defaultWindowInsetsPadding = false, showDivider = true) {
                    NavigationBar(modifier = Modifier.widthIn(max = 360.dp), color = Color.Transparent,
                        showDivider = false, defaultWindowInsetsPadding = false) {
                        val icons = listOf(R.drawable.ic_nav_widgets, R.drawable.ic_nav_mirror, R.drawable.ic_nav_settings)
                        val labels = listOf(R.string.dashboard_widgets_title, R.string.nav_mirror, R.string.nav_settings)
                        icons.forEachIndexed { index, icon ->
                            NavigationBarItem(selected = selectedSection == index,
                                onClick = { onSelect.accept(index) },
                                icon = ImageVector.vectorResource(icon), label = stringResource(labels[index]))
                        }
                    }
                }
            }
        }
    }
    fun select(index: Int) { selectedSection = index }
}
