package com.w2018.procmanager.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.core.SystemApps
import com.w2018.procmanager.data.model.AppInfo
import com.w2018.procmanager.data.model.ProcInfo
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.AppIcon
import com.w2018.procmanager.ui.components.AppAvatar
import com.w2018.procmanager.ui.components.BtnKind
import com.w2018.procmanager.ui.components.Chip
import com.w2018.procmanager.ui.components.DotM
import com.w2018.procmanager.ui.components.Ic
import com.w2018.procmanager.ui.components.LocalConfirm
import com.w2018.procmanager.ui.components.LocalToast
import com.w2018.procmanager.ui.components.MiniButton
import com.w2018.procmanager.ui.components.ProcButton
import com.w2018.procmanager.ui.components.ProcSwitch
import com.w2018.procmanager.ui.components.SliderRow
import com.w2018.procmanager.ui.components.SwitchRow
import com.w2018.procmanager.ui.components.TagC
import com.w2018.procmanager.ui.components.avatarColor
import com.w2018.procmanager.ui.components.avatarLetter
import com.w2018.procmanager.ui.theme.C
import kotlinx.coroutines.launch

private data class ProcAgg(
    val memKb: Long,
    val cpu: Float,
    val foreground: Boolean,
    val running: Boolean,
    val elapsedSec: Long,
)

private fun fmtElapsed(sec: Long): String {
    if (sec <= 0) return "—"
    val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60
    return when {
        d > 0 -> "${d}d${h}h"
        h > 0 -> "${h}h${m}m"
        m > 0 -> "${m}m"
        else -> "${sec}s"
    }
}

@Composable
fun AppsScreen(ui: AppUi, filter: String = "all", onFilterChange: (String) -> Unit = {}, onOpenApp: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("default") }
    val scope = rememberCoroutineScope()
    val snap = ui.snapshot
    val apps = snap?.apps ?: emptyList()
    val procs = snap?.processes ?: emptyList()

    val agg = remember(procs) {
        val m = HashMap<String, ProcAgg>()
        procs.forEach { p ->
            val cur = m[p.packageName]
            val use = if (p.pssKb > 0) p.pssKb else p.rssKb
            if (cur == null) m[p.packageName] = ProcAgg(use, p.cpuPercent, p.isForeground, true, p.elapsedSec)
            else m[p.packageName] = ProcAgg(
                cur.memKb + use, maxOf(cur.cpu, p.cpuPercent), cur.foreground || p.isForeground, true,
                maxOf(cur.elapsedSec, p.elapsedSec)
            )
        }
        m
    }

    val list = apps.filter { a ->
        when (filter) {
            "sys" -> a.isSystem
            "user" -> !a.isSystem
            "mon" -> if (ui.cfg.monitorMode == "all") !a.isSystem else ui.monitored.contains(a.packageName)
            "run" -> agg.containsKey(a.packageName)
            "frozen" -> ui.isFrozen(a.packageName)
            "auto" -> a.autoStart
            "rule" -> ui.app.settings.hasPerAppRule(a.packageName) || ui.bgStopSet.contains(a.packageName)
            else -> true
        } && (search.isBlank() || a.label.contains(search, true) || a.packageName.contains(search, true))
    }

    // 排序：默认把「已冻结 / 有规则 / 有设置」的应用优先靠前，其余保持原序；也可按内存/CPU 降序
    val sorted = remember(list, sort, agg, ui.frozen, ui.monitored, ui.bgStopSet, ui.ruleRev, ui.cfg.monitorMode) {
        when (sort) {
            "mem" -> list.sortedByDescending { agg[it.packageName]?.memKb ?: -1L }
            "cpu" -> list.sortedByDescending { agg[it.packageName]?.cpu ?: -1f }
            else -> list.sortedBy { a ->
                when {
                    ui.isFrozen(a.packageName) -> 0
                    ui.app.settings.hasPerAppRule(a.packageName) || ui.bgStopSet.contains(a.packageName) -> 1
                    ui.cfg.monitorMode == "selected" && ui.monitored.contains(a.packageName) -> 1
                    a.autoStart -> 2
                    else -> 3
                }
            }
        }
    }
    // 切换筛选/排序/搜索时回到列表顶部
    val listState = rememberLazyListState()
    var lastQueryKey by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(filter, sort, search) {
        val k = "$filter|$sort|$search"
        if (k != lastQueryKey) { lastQueryKey = k; listState.scrollToItem(0) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("应用管理", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("共 ${apps.size} 个应用 · 点击查看进程详情与规则", color = C.text2, fontSize = 11.sp)
            }
        }
        TextField(
            value = search,
            onValueChange = { search = it },
            singleLine = true,
            placeholder = { Text("搜索应用名称或包名", color = C.muted, fontSize = 12.5.sp) },
            leadingIcon = { Ic("search", 17, C.muted) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    Box(Modifier.clip(RoundedCornerShape(50)).clickable { search = "" }.padding(6.dp)) {
                        Ic("x", 15, C.muted)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = C.card, unfocusedContainerColor = C.card,
                focusedTextColor = C.text, unfocusedTextColor = C.text,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = C.accent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
        RefreshIndicator(ui.refreshing, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        // 排序按钮（靠前）：按内存占用 / CPU 占用
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("排序", color = C.muted, fontSize = 11.sp)
            Chip("默认", sort == "default") { sort = "default" }
            Chip("内存占用", sort == "mem") { sort = "mem" }
            Chip("CPU占用", sort == "cpu") { sort = "cpu" }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cUser = apps.count { !it.isSystem }
            val cSys = apps.count { it.isSystem }
            val cRun = apps.count { agg.containsKey(it.packageName) }
            val cFrozen = apps.count { ui.isFrozen(it.packageName) }
            val cAuto = apps.count { it.autoStart }
            val cRule = apps.count { ui.app.settings.hasPerAppRule(it.packageName) || ui.bgStopSet.contains(it.packageName) }
            Chip("全部 ${apps.size}", filter == "all") { onFilterChange("all") }
            Chip("用户应用 $cUser", filter == "user") { onFilterChange("user") }
            Chip("系统应用 $cSys", filter == "sys") { onFilterChange("sys") }
            Chip("运行中 $cRun", filter == "run") { onFilterChange("run") }
            Chip("已冻结 $cFrozen", filter == "frozen") { onFilterChange("frozen") }
            Chip("自启动 $cAuto", filter == "auto") { onFilterChange("auto") }
            Chip("有规则 $cRule", filter == "rule") { onFilterChange("rule") }
        }
        Spacer(Modifier.height(10.dp))
        val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (list.isEmpty()) {
                Text("没有符合条件的应用", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sorted, key = { it.packageName }, contentType = { "app" }) { a ->
                        AppRow(ui, a, agg[a.packageName], onOpenApp)
                    }
                    item {
                        Text("只读进程信息 · 冻结/终止均为无风险操作，系统关键应用已禁用冻结",
                            color = C.muted, fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            // 右下角「回到顶部」浮动按钮（向量绘制图标，无文字）
            if (showFab) {
                BackToTopButton(
                    Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 18.dp),
                ) { scope.launch { listState.animateScrollToItem(0) } }
            }
        }
    }
}

/** 右下角圆形的「回到顶部」按钮，图标用 Canvas 矢量绘制（非文字）。 */
@Composable
private fun BackToTopButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(46.dp).clip(RoundedCornerShape(50)).background(C.accent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width; val h = size.height
            val sw = w * 0.13f
            val c = C.onAccent
            drawLine(c, start = Offset(w * 0.5f, h * 0.84f), end = Offset(w * 0.5f, h * 0.2f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, start = Offset(w * 0.5f, h * 0.2f), end = Offset(w * 0.24f, h * 0.46f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, start = Offset(w * 0.5f, h * 0.2f), end = Offset(w * 0.76f, h * 0.46f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AppRow(ui: AppUi, a: AppInfo, p: ProcAgg?, onOpen: (String) -> Unit) {
    val frozen = ui.isFrozen(a.packageName)
    val monitored = ui.monitored.contains(a.packageName)
    val status = when {
        frozen -> "已冻结"
        p?.foreground == true -> "前台"
        p?.running == true -> "后台运行"
        else -> "未运行"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(14.dp))
            .clickable { onOpen(a.packageName) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(a.packageName, a.label, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, color = C.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                TagC(if (a.isSystem) "系统" else "用户", if (a.isSystem) C.purple else C.blue)
                if (ui.cfg.monitorMode == "selected" && monitored) { Spacer(Modifier.width(4.dp)); TagC("监控中", C.accent) }
                if (frozen) { Spacer(Modifier.width(4.dp)); TagC("已冻结", C.warn) }
                // 优化标签（最多 1 个，保持单行不换行）
                val memMb = (p?.memKb ?: 0) / 1024
                val killN = ui.killCounts[a.packageName] ?: 0
                val blocked = ui.isAutoStartBlocked(a.packageName)
                val opt = when {
                    memMb >= 200 -> "高占用" to C.danger
                    a.autoStart && !blocked && !frozen -> "可自启" to C.warn
                    killN >= 3 -> "常清理" to C.danger
                    else -> null
                }
                if (opt != null) { Spacer(Modifier.width(4.dp)); TagC(opt.first, opt.second) }
            }
            Text(a.packageName, color = C.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DotM(if (status == "后台运行") C.success else if (status == "前台") C.accent else C.muted, 6)
                Spacer(Modifier.width(5.dp))
                Text(status, fontSize = 10.5.sp, color = when (status) {
                    "前台" -> C.accent
                    "后台运行" -> C.success
                    "已冻结" -> C.warn
                    else -> C.muted
                })
                Spacer(Modifier.width(10.dp))
                MetricIcon("memory", "${(p?.memKb ?: 0) / 1024}M")
                Spacer(Modifier.width(9.dp))
                MetricIcon("cpu", String.format("%.1f%%", p?.cpu ?: 0f))
                Spacer(Modifier.width(9.dp))
                MetricIcon("clock", fmtElapsed(p?.elapsedSec ?: 0))
                if (a.autoStart) {
                    Spacer(Modifier.width(9.dp))
                    Ic("power", 12, C.warn)
                }
            }
        }
        if (ui.cfg.monitorMode == "selected") MiniButton(monitored) { ui.toggleMonitor(a.packageName) }
    }
}

/** 小图标 + 数值（进程指标），用图标替代文字标签。 */
@Composable
private fun MetricIcon(icon: String, value: String) {
    val c = when (icon) { "memory" -> C.blue; "cpu" -> C.warn; "clock" -> C.text2; else -> C.text2 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, 12, c.copy(alpha = 0.85f))
        Spacer(Modifier.width(2.dp))
        Text(value, color = c, fontSize = 10.sp)
    }
}

/** 列表刷新动效：仅刷新时顶部一条流动光带（非刷新态不启动动画，避免常驻占用 CPU）。 */
@Composable
private fun RefreshIndicator(active: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(C.card2)) {
        if (active) {
            val inf = rememberInfiniteTransition(label = "refresh")
            val x by inf.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Restart),
                label = "x",
            )
            Box(
                Modifier.fillMaxWidth(0.35f).fillMaxSize()
                    .graphicsLayer { translationX = x * (size.width / 0.35f) }
                    .background(Brush.horizontalGradient(listOf(C.accent.copy(alpha = 0f), C.accent, C.accent.copy(alpha = 0f))))
            )
        }
    }
}

@Composable
fun AppDetailSheet(ui: AppUi, pkg: String, onClose: () -> Unit) {
    val toast = LocalToast.current
    val confirm = LocalConfirm.current
    val scope = rememberCoroutineScope()
    val snap = ui.snapshot
    val a = snap?.apps?.find { it.packageName == pkg } ?: return
    val procs = snap.processes.filter { it.packageName == pkg }
    val memKb = procs.sumOf { if (it.pssKb > 0) it.pssKb else it.rssKb }
    val cpu = procs.maxOfOrNull { it.cpuPercent } ?: 0f
    val elapsed = procs.maxOfOrNull { it.elapsedSec } ?: 0L
    val fg = procs.any { it.isForeground }
    val frozen = ui.isFrozen(pkg)
    val monitored = ui.monitored.contains(pkg)
    val bgStop = ui.isBgStop(pkg)
    val autoBlocked = ui.isAutoStartBlocked(pkg)
    val critical = SystemApps.isCritical(pkg)
    val status = when { frozen -> "已冻结"; fg -> "前台"; procs.isNotEmpty() -> "后台运行"; else -> "未运行" }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { onClose() }) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(C.bg2)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .padding(bottom = 24.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(C.border))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a.packageName, a.label, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        TagC(if (a.isSystem) "系统应用" else "用户应用", if (a.isSystem) C.purple else C.blue)
                        if (ui.cfg.monitorMode == "selected" && monitored) { Spacer(Modifier.width(4.dp)); TagC("监控中", C.accent) }
                    }
                    Text(a.packageName, color = C.muted, fontSize = 11.sp)
                }
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(C.card).clickable { onClose() },
                    contentAlignment = Alignment.Center) { Ic("x", 18, C.text2) }
            }

            // 进程指标（图标 + 值）
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
                .border(1.dp, C.border, RoundedCornerShape(16.dp)).padding(vertical = 14.dp)) {
                SheetMetric("activity", status, "状态", Modifier.weight(1f))
                SheetMetric("memory", if (memKb > 0) "${memKb / 1024} MB" else "0 MB", "运行内存", Modifier.weight(1f))
                SheetMetric("cpu", String.format("%.1f%%", cpu), "CPU 占用", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
                .border(1.dp, C.border, RoundedCornerShape(16.dp)).padding(vertical = 14.dp)) {
                SheetMetric("clock", fmtElapsed(elapsed), "运行时间", Modifier.weight(1f))
                SheetMetric("power", if (a.autoStart) "是" else "否", "开机自启", Modifier.weight(1f))
                SheetMetric("shield", "${procs.size} 个", "进程数", Modifier.weight(1f))
            }

            // 系统应用 作用/风险 说明
            if (a.isSystem) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(C.warn.copy(alpha = 0.08f)).border(1.dp, C.warn.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic("info", 15, C.warn)
                        Spacer(Modifier.width(6.dp))
                        Text("系统应用说明", color = C.warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("作用：" + SystemApps.purposeOf(pkg, a.label), color = C.text2, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp), lineHeight = 16.sp)
                    Text("风险：" + SystemApps.riskOf(pkg), color = C.text2, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp), lineHeight = 16.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            // 监控
            val monitorAll = ui.cfg.monitorMode == "all"
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                    Ic("shield", 16, C.text2)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (monitorAll) "全量监控中" else "加入监控列表", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (monitorAll) "当前管理所有用户应用，可在设置切换为「仅勾选」"
                        else "加入后按全局规则自动管理", color = C.text2, fontSize = 10.5.sp)
                }
                if (!monitorAll) ProcSwitch(monitored) { ui.toggleMonitor(pkg) }
            }
            // 单应用 后台即停
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(C.card).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("后台运行立即停止", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("该应用切入后台即终止（仅本应用）", color = C.text2, fontSize = 10.5.sp)
                }
                ProcSwitch(bgStop) { ui.toggleBgStop(pkg) }
            }

            // 单应用独立规则（更细粒度）
            val per = ui.getPerApp(pkg)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                    .border(1.dp, C.border, RoundedCornerShape(14.dp)),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                        Ic("grid", 16, C.text2)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("单应用独立规则", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("为该应用单独设置阈值，优先级高于全局规则", color = C.text2, fontSize = 10.5.sp)
                    }
                    ProcSwitch(per.enabled) { ui.setPerApp(pkg, per.copy(enabled = it)) }
                }
                if (per.enabled) {
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(C.border.copy(alpha = 0.6f)))
                    SwitchRow("memory", "内存超限清理", "该应用内存超限时终止（忽略全局规则）", per.memStop) { ui.setPerApp(pkg, per.copy(memStop = it)) }
                    if (per.memStop) {
                        SliderRow("内存上限", per.memMb, 0, 1024, if (per.memMb == 0) "MB·跟随全局阈值" else "MB") { ui.setPerApp(pkg, per.copy(memMb = it)) }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(C.border.copy(alpha = 0.6f)))
                    SwitchRow("cpu", "CPU 超限清理", "该应用 CPU 超限时终止（忽略全局规则）", per.cpuStop) { ui.setPerApp(pkg, per.copy(cpuStop = it)) }
                    if (per.cpuStop) {
                        SliderRow("CPU 上限", per.cpu, 0, 100, if (per.cpu == 0) "%·跟随全局阈值" else "%") { ui.setPerApp(pkg, per.copy(cpu = it)) }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(C.border.copy(alpha = 0.6f)))
                    SwitchRow("clock", "后台超时清理", "切后台超过「保护时间」后终止", per.bgStop) { ui.setPerApp(pkg, per.copy(bgStop = it)) }
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(C.border.copy(alpha = 0.6f)))
                    SwitchRow("lock", "关联唤醒拦截", "清理后短时间内被重新拉起则再次终止", per.wakeBlock) { ui.setPerApp(pkg, per.copy(wakeBlock = it)) }
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(C.border.copy(alpha = 0.6f)))
                    SliderRow("保护时间", per.bufMin, 0, 30, if (per.bufMin == 0) "分·跟随全局" else "分") { ui.setPerApp(pkg, per.copy(bufMin = it)) }
                    Text("开启后该应用完全按本组规则执行、忽略全局规则；关闭则回落全局规则。阈值为 0 时沿用全局阈值；保护时间＝切后台超过此时长才进入后台超时/内存/CPU 清理评估。",
                        color = C.muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), lineHeight = 15.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            DashSectionTitle("快捷操作")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProcButton(
                    text = if (frozen) "恢复应用" else "冻结/停用",
                    onClick = {
                        if (critical) { toast.show("系统关键应用，已禁止冻结"); return@ProcButton }
                        val risk = SystemApps.riskLevel(pkg)
                        val warn = when {
                            risk >= 2 -> "【禁止】关键系统应用，已禁止冻结。\n"
                            risk == 1 -> "【高风险】该应用为系统/框架组件，停用可能影响推送、同步、硬件或通话等功能，请确认后继续。\n"
                            else -> ""
                        }
                        confirm.show(if (frozen) "恢复应用?" else "冻结应用?",
                            warn + "通过 pm ${if (frozen) "enable" else "disable-user --user 0"} 执行。\n" + SystemApps.riskOf(pkg)) {
                            scope.launch {
                                val ok = ui.freezeApp(pkg, !frozen)
                                toast.show(if (ok) (if (frozen) "已恢复 " else "已冻结 ") + a.label else "操作失败，请检查权限通道")
                            }
                        }
                    },
                    kind = BtnKind.Ghost,
                    modifier = Modifier.weight(1f),
                    leading = { Ic("snow", 17, if (critical) C.muted else C.text) },
                )
                ProcButton(
                    text = "强制终止",
                    onClick = {
                        confirm.show("强制终止 ${a.label}?", "通过 am force-stop 终止其后端进程，操作立即生效（不卸载、不清数据）。") {
                            scope.launch {
                                val ok = ui.killApp(pkg, a.label)
                                toast.show(if (ok) "已强制终止 ${a.label}" else "操作失败，请检查权限通道")
                                if (ok) onClose()
                            }
                        }
                    },
                    kind = BtnKind.Danger,
                    modifier = Modifier.weight(1f),
                    leading = { Ic("zap", 17, C.danger) },
                )
            }
            // 仅当应用具备自启能力（声明了开机自启接收器）或当前已被禁止自启时，才显示自启动管理按钮
            if (a.autoStart || autoBlocked) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcButton(
                        text = if (autoBlocked) "恢复开机自启" else "禁止开机自启",
                        onClick = {
                            confirm.show(
                                if (autoBlocked) "恢复 ${a.label} 开机自启?" else "禁止 ${a.label} 开机自启?",
                                "通过系统 AppOps（RUN_ANY_IN_BACKGROUND）限制其开机/后台自启，不改动应用、不冻结，可随时恢复。"
                            ) {
                                scope.launch {
                                    val ok = ui.setAutoStartBlocked(pkg, !autoBlocked)
                                    toast.show(if (ok) (if (autoBlocked) "已恢复开机自启" else "已禁止开机自启") else "操作失败，请检查权限通道")
                                }
                            }
                        },
                        kind = if (autoBlocked) BtnKind.Primary else BtnKind.Danger,
                        modifier = Modifier.weight(1f),
                        leading = { Ic("power", 17, if (autoBlocked) C.accent else C.danger) },
                    )
                }
            }
            Text("冻结=pm disable-user，终止=am force-stop，禁止自启=AppOps；本应用不会卸载应用或清除数据，均需 Shizuku 或 ADB 授权。",
                color = C.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 14.dp), lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SheetMetric(icon: String, value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Ic(icon, 16, C.text2)
        val vc = when (icon) { "memory" -> C.blue; "cpu" -> C.warn; "clock" -> C.accent; "activity" -> C.success; else -> C.accent }
        Text(value, color = vc, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text(label, color = C.text2, fontSize = 9.5.sp, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun DashSectionTitle(t: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
