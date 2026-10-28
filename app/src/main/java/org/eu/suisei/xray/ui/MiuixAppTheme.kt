package org.eu.suisei.xray.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun MiuixAppTheme(
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val controller = remember(isDark) {
        ThemeController(ColorSchemeMode.System, isDark = isDark)
    }
    MiuixTheme(
        controller = controller,
        content = content
    )
}
