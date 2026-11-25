package com.w2018.procmanager.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** 当前缩放系数（设计稿 400 宽为 1.0） */
val LocalScale = staticCompositionLocalOf { 1f }

/** 设计基准宽度（dp），来自原型 400px 设计宽 */
const val DESIGN_WIDTH = 400f

/**
 * 比例缩放容器：把可用宽度映射到 400 设计宽度，
 * 通过改写 Density 让设计稿中的 dp/sp 数值整体等比缩放，
 * 从而不同分辨率设备比例一致、不出现固定尺寸导致的错位。
 */
@Composable
fun ScaledContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthDp = maxWidth.value
        val scale = (widthDp / DESIGN_WIDTH).coerceIn(0.80f, 1.70f)
        val base = LocalDensity.current
        val scaled = Density(base.density * scale, base.fontScale)
        CompositionLocalProvider(
            LocalDensity provides scaled,
            LocalScale provides scale,
            content = content,
        )
    }
}

/** 便捷：按缩放后的 dp（设计数值直接传入） */
@Composable
fun dpx(v: Float): androidx.compose.ui.unit.Dp = (v / LocalScale.current).dp
