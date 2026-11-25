package com.w2018.procmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.w2018.procmanager.ui.theme.C

fun icVec(name: String): ImageVector = when (name) {
    "shield" -> Icons.Outlined.Shield
    "activity" -> Icons.Outlined.ShowChart
    "zap" -> Icons.Filled.Bolt
    "refresh" -> Icons.Outlined.Refresh
    "snow" -> Icons.Outlined.AcUnit
    "power" -> Icons.Outlined.PowerSettingsNew
    "cpu" -> Icons.Outlined.DeveloperBoard
    "memory" -> Icons.Outlined.Storage
    "clock" -> Icons.Outlined.Schedule
    "grid" -> Icons.Outlined.DeveloperBoard
    "bell" -> Icons.Outlined.Notifications
    "settings" -> Icons.Outlined.Settings
    "search" -> Icons.Outlined.Search
    "plus" -> Icons.Outlined.Add
    "check" -> Icons.Outlined.Check
    "chevron" -> Icons.Outlined.ChevronRight
    "x" -> Icons.Outlined.Close
    "lock" -> Icons.Outlined.Lock
    "unlock" -> Icons.Outlined.LockOpen
    "moon" -> Icons.Outlined.DarkMode
    "battery" -> Icons.Outlined.BatteryStd
    "trash" -> Icons.Outlined.DeleteOutline
    "star" -> Icons.Outlined.StarBorder
    "info" -> Icons.Outlined.Info
    "accessibility" -> Icons.Outlined.AccessibilityNew
    "cable" -> Icons.Outlined.Cable
    "rank" -> Icons.Outlined.EmojiEvents
    "back" -> Icons.AutoMirrored.Outlined.ArrowBack
    else -> Icons.Outlined.Info
}

@Composable
fun Ic(name: String, size: Int = 18, color: Color = C.text2) {
    Icon(icVec(name), contentDescription = null, tint = color, modifier = Modifier.size(size.dp))
}

/** 顶栏方形图标按钮 */
@Composable
fun IconBtn(name: String, badge: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(12.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Ic(name, 19, C.text2)
        if (badge) Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(C.danger)
        )
    }
}

/** 应用头像（首字母/首字，彩色底） */
@Composable
fun AppAvatar(letter: String, bg: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontSize = (size * 0.42).sp, fontWeight = FontWeight.ExtraBold)
    }
}

fun avatarColor(pkg: String): Color {
    val palette = listOf(
        0xFF07C160, 0xFF161823, 0xFF1677FF, 0xFFFF5000, 0xFF3385FF,
        0xFF12B7F5, 0xFFFF4906, 0xFFE60026, 0xFF1C80F0, 0xFF455A64,
        0xFF5A6C82, 0xFF34A853, 0xFF607D8B, 0xFF9C27B0, 0xFF00BCD4
    )
    val idx = (pkg.hashCode() and 0x7fffffff) % palette.size
    return Color(palette[idx])
}

fun avatarLetter(label: String, pkg: String): String =
    label.firstOrNull()?.toString() ?: pkg.firstOrNull()?.toString()?.uppercase() ?: "?"

/** 应用图标内存缓存：避免滚动时对每个新行在主线程做 PackageManager IPC + Bitmap 解码造成卡顿。 */
private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(160)
    private val missing = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    fun get(pkg: String): ImageBitmap? = cache.get(pkg)
    fun isMissing(pkg: String): Boolean = missing.contains(pkg)
    fun load(ctx: android.content.Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        if (missing.contains(pkg)) return null
        return runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
            val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, w, h)
            d.draw(c)
            b.asImageBitmap()
        }.getOrNull().also { if (it == null) missing.add(pkg) else cache.put(pkg, it) }
    }
}

/** 应用真实图标（PackageManager 读取 launcher icon）。图标在 IO 线程解码并内存缓存，主线程不阻塞滚动。 */
@Composable
fun AppIcon(pkg: String, label: String, size: Int = 40) {
    val ctx = LocalContext.current
    var bmp by remember(pkg) { mutableStateOf(IconCache.get(pkg)) }
    LaunchedEffect(pkg) {
        if (bmp == null && !IconCache.isMissing(pkg)) {
            bmp = withContext(Dispatchers.IO) { IconCache.load(ctx, pkg) }
        }
    }
    val shape = RoundedCornerShape((size / 4).dp)
    if (bmp != null) {
        Image(
            bitmap = bmp!!, contentDescription = label, contentScale = ContentScale.Fit,
            modifier = Modifier.size(size.dp).clip(shape),
        )
    } else {
        AppAvatar(avatarLetter(label, pkg), avatarColor(pkg), size)
    }
}

/** 小圆形状态点 */
@Composable
fun DotM(color: Color, size: Int = 7) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** 快捷操作方块：等宽网格单元，固定高度，文字不换行不缩放。 */
@Composable
fun QuickItem(icon: String, label: String, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "quickScale")
    Column(
        Modifier.fillMaxWidth().height(84.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = LocalIndication.current) { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ic(icon, 22, color)
        Text(
            label, color = C.text2, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 7.dp, start = 4.dp, end = 4.dp),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 指标卡 */
@Composable
fun MetricCard(icon: String, title: String, num: String, unit: String, delta: String, warn: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(14.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 15, C.text2)
            Spacer(Modifier.width(7.dp))
            Text(title, color = C.text2, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Bottom) {
            Text(num, color = if (warn) C.warn else C.accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            Text(" $unit", color = C.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        Text(delta, color = if (warn) C.warn else C.success, fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** 呼吸状态点（运行中状态动画）。 */
@Composable
fun PulseDot(color: Color, size: Int = 7) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha",
    )
    Box(Modifier.size(size.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(color))
}

/** 迷你方形按钮（应用行右侧监控开关） */
@Composable
fun MiniButton(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
            .background(if (on) C.accent.copy(alpha = 0.15f) else C.card2)
            .border(1.dp, if (on) C.accent else C.border, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Ic(if (on) "check" else "plus", 16, if (on) C.accent else C.text2)
    }
}

/** 通用设置行（图标 + 标题 + 描述 + 右侧） */
@Composable
fun SwitchRow(icon: String, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (checked) C.accent.copy(alpha = 0.14f) else C.card2),
            contentAlignment = Alignment.Center,
        ) { Ic(icon, 16, if (checked) C.accent else C.text2) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = C.text2, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp), lineHeight = 14.sp)
        }
        ProcSwitch(checked, onChange)
    }
}

/** 滑块行（存储到设置，实时保存） */
@Composable
fun SliderRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("$value $unit", color = C.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
        androidx.compose.material3.Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = C.accent,
                activeTrackColor = C.accent,
                inactiveTrackColor = C.card2,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$min", color = C.muted, fontSize = 9.5.sp)
            Text("$max", color = C.muted, fontSize = 9.5.sp)
        }
    }
}

/** 分段控件 */
@Composable
fun SegControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(C.card2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selectedIndex
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (on) C.card else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = if (on) C.accent else C.text2, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 筛选 chip */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) C.accent.copy(alpha = 0.14f) else C.card)
            .border(1.dp, if (selected) C.accent else C.border, RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (selected) C.accent else C.text2, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** 小标签 */
@Composable
fun TagC(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
