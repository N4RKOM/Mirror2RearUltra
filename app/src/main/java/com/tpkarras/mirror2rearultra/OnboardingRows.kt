package com.tpkarras.mirror2rearultra

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** The rows of the setup screen: a title, what it is for, and how it stands at the end. */
object OnboardingRows {
    const val TONE_GOOD = 0
    const val TONE_BAD = 1
    const val TONE_QUIET = 2

    /**
     * One line of the checklist.
     *
     * @param status the word at the end of the row; empty for a row that is only a button
     * @param tone [TONE_GOOD], [TONE_BAD] or [TONE_QUIET], the colour of [status]
     * @param onClick what tapping does, or null for a row that only reports
     */
    @JvmStatic
    fun row(
        activity: Activity, title: String, summary: String?, icon: Int,
        status: String, tone: Int, onClick: Runnable?,
    ): View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val mode = miuixMode(LocalContext.current, isSystemInDarkTheme())
            val controller = remember(mode) { ThemeController(mode) }
            MiuixTheme(controller = controller) {
                val statusColor = when (tone) {
                    TONE_GOOD -> colorResource(R.color.hyper_success)
                    TONE_BAD -> colorResource(R.color.hyper_error)
                    else -> MiuixTheme.colorScheme.onSurfaceVariantActions
                }
                BasicComponent(
                    title = title,
                    summary = summary,
                    startAction = {
                        Image(painterResource(icon), contentDescription = null,
                            modifier = Modifier.size(30.dp))
                    },
                    endActions = {
                        if (status.isNotEmpty()) Text(status, color = statusColor)
                    },
                    onClick = onClick?.let { action -> { action.run() } },
                )
            }
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
