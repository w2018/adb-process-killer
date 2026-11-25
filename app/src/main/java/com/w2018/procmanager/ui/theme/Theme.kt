package com.w2018.procmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 全局主题模式（system / dark / light），由设置页修改，ProcTheme 实时响应。
 */
object ThemeState {
    var mode by mutableStateOf("system")
}

/**
 * 设计色板（来自 ui-prototype.html :root 变量）。
 * 支持深色/浅色两套主题：dark 标记当前模式，getter 按模式返回对应色值。
 * 主题切换通过 ProcTheme 中的 key(dark) 强制整棵 UI 重组，保证所有静态读取的颜色即时更新。
 */
object C {
    /** 当前是否深色主题（由 ProcTheme 在组合前设置）。 */
    var dark: Boolean = true

    val bg get() = if (dark) Color(0xFF0B0F16) else Color(0xFFF3F5F9)
    val bg2 get() = if (dark) Color(0xFF111722) else Color(0xFFFFFFFF)
    val card get() = if (dark) Color(0xFF161E2B) else Color(0xFFFFFFFF)
    val card2 get() = if (dark) Color(0xFF1D2736) else Color(0xFFEEF2F7)
    val border get() = if (dark) Color(0xFF273245) else Color(0xFFE3E8EF)
    val text get() = if (dark) Color(0xFFE8EEF7) else Color(0xFF101B2D)
    val text2 get() = if (dark) Color(0xFF8A99AD) else Color(0xFF5A6B80)
    val muted get() = if (dark) Color(0xFF5D6B7F) else Color(0xFF94A3B8)
    val accent get() = if (dark) Color(0xFF00E5B0) else Color(0xFF00A57C)
    val accentD get() = if (dark) Color(0xFF00B88D) else Color(0xFF008A68)
    val blue get() = if (dark) Color(0xFF5B9DFF) else Color(0xFF2F6BFF)
    val danger get() = if (dark) Color(0xFFFF5F6D) else Color(0xFFE5484D)
    val warn get() = if (dark) Color(0xFFFFB84D) else Color(0xFFD98A00)
    val purple get() = if (dark) Color(0xFFA78BFA) else Color(0xFF7C5CFC)
    val success get() = if (dark) Color(0xFF3DDC84) else Color(0xFF12A150)
    val onAccent get() = if (dark) Color(0xFF04140F) else Color(0xFFFFFFFF)
}

private fun scheme(dark: Boolean) = if (dark) darkColorScheme(
    primary = C.accent, onPrimary = C.onAccent, secondary = C.blue,
    background = C.bg, onBackground = C.text, surface = C.card, onSurface = C.text,
    surfaceVariant = C.card2, onSurfaceVariant = C.text2,
    error = C.danger, outline = C.border,
) else lightColorScheme(
    primary = C.accent, onPrimary = C.onAccent, secondary = C.blue,
    background = C.bg, onBackground = C.text, surface = C.card, onSurface = C.text,
    surfaceVariant = C.card2, onSurfaceVariant = C.text2,
    error = C.danger, outline = C.border,
)

@Composable
fun ProcTheme(content: @Composable () -> Unit) {
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (ThemeState.mode) {
        "dark" -> true
        "light" -> false
        else -> sysDark
    }
    C.dark = dark
    key(dark) {
        MaterialTheme(colorScheme = scheme(dark), content = content)
    }
}
