package com.w2018.procmanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.DotM
import com.w2018.procmanager.ui.components.Ic
import com.w2018.procmanager.ui.components.IconBtn
import com.w2018.procmanager.ui.components.LocalConfirm
import com.w2018.procmanager.ui.components.LocalToast
import com.w2018.procmanager.core.rule.KillCandidate
import com.w2018.procmanager.data.model.AppInfo
import com.w2018.procmanager.ui.components.AppIcon
import com.w2018.procmanager.ui.components.BtnKind
import com.w2018.procmanager.ui.components.Chip
import com.w2018.procmanager.ui.components.MetricCard
import com.w2018.procmanager.ui.components.MiniButton
import com.w2018.procmanager.ui.components.ProcButton
import com.w2018.procmanager.ui.components.PulseDot
import com.w2018.procmanager.ui.components.QuickItem
import com.w2018.procmanager.ui.theme.C
import kotlinx.coroutines.launch

@Composable
fun DashScreen(ui: AppUi, onOpenSettings: () -> Unit = {}, onOpenLogs: () -> Unit = {}) {
    val toast = LocalToast.current
    val confirm = LocalConfirm.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val appVer = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    val snap = ui.snapshot
    val scroll = rememberScrollState()
    var scanResults by remember { mutableStateOf<List<KillCandidate>?>(null) }
    var showFreeze by remember { mutableStateOf(false) }

    val mem = snap?.mem
    val availGb = (mem?.availKb ?: 0L) / 1024f / 1024f
    val memPct = mem?.percent ?: 0f
    val bgProcs = snap?.processes?.count { it.user.startsWith("u0_") && !it.isForeground } ?: 0
    val cpuPct = snap?.cpu?.usedPercent ?: 0f
    val monCount = ui.monitored.size

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 16.dp),
    ) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(C.accent, C.blue))),
                        contentAlignment = Alignment.Center,
                    ) { Ic("shield", 18, C.onAccent) }
                    Spacer(Modifier.width(8.dp))
                    Text("进程杀手", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(6.dp))
                    Text("v$appVer", color = C.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(C.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ui.cfg.guardian) PulseDot(C.success, 7) else DotM(C.danger, 7)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (ui.cfg.guardian) "守护运行中" else "守护已暂停") +
                            " · " + (if (ui.channelReady) ui.channelLabel else "未连接"),
                        color = C.text2, fontSize = 12.sp,
                    )
                }
            }
            IconBtn("bell", badge = true) { toast.show("暂无新通知") }
        }

        // 未连接提示条
        if (!ui.channelReady) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(C.danger.copy(alpha = 0.10f))
                    .border(1.dp, C.danger.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                    .clickable { onOpenSettings() }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic("cable", 16, C.danger)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("未连接权限通道", color = C.danger, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("点击前往「设置」连接 Shizuku 或无线 ADB", color = C.text2, fontSize = 10.5.sp)
                }
                Ic("chevron", 16, C.danger)
            }
            Spacer(Modifier.height(10.dp))
        }

        // 状态三卡
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusCard("shield", "前台服务", if (ui.cfg.guardian) "运行中" else "已停止", ui.cfg.guardian, Modifier.weight(1f))
            StatusCard("activity", "看门狗", if (ui.cfg.watchdog) "监控中" else "已暂停", ui.cfg.watchdog, Modifier.weight(1f))
            StatusCard("zap", "权限通道", if (ui.channelReady) ui.channelLabel else "未连接", ui.channelReady, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        // Hero 今日报告
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        if (C.dark) listOf(Color(0xFF12332E), Color(0xFF112436), C.card)
                        else listOf(Color(0xFFE8F7F1), Color(0xFFEAF1FB), C.card)
                    )
                )
                .border(1.dp, C.accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Column {
                Text("今日守护报告", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${ui.blocked}", color = C.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" 次清理", color = C.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("已自动清理后台进程并释放内存", color = C.text2, fontSize = 11.5.sp)
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HeroStat("${ui.blocked}", "次", "今日清理次数", C.warn)
                    HeroStat("${ui.freedKb / 1024}", "MB", "释放内存", C.accent)
                    HeroStat("$monCount", "", "监控应用", C.blue)
                    HeroStat("${snap?.processes?.size ?: 0}", "", "进程总数", C.text)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("快捷操作")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { QuickItem("zap", "一键清理", C.accent) {
                if (!ui.channelReady) { toast.show("未连接权限通道，请先在设置中连接"); return@QuickItem }
                confirm.show("确认清理后台?", "将终止当前所有后台应用进程，操作立即生效。") {
                    scope.launch { toast.show(ui.cleanNow()) }
                }
            } }
            Box(Modifier.weight(1f)) { QuickItem("refresh", "立即扫描", C.blue) {
                if (!ui.channelReady) { toast.show("未连接权限通道，请先在设置中连接"); return@QuickItem }
                scope.launch {
                    toast.show("正在扫描…")
                    val list = runCatching { ui.scanCandidates() }.getOrDefault(emptyList())
                    if (list.isEmpty()) toast.show("未发现符合规则的后台应用") else scanResults = list
                }
            } }
            Box(Modifier.weight(1f)) { QuickItem("snow", "冻结后台", C.warn) {
                if (!ui.channelReady) { toast.show("未连接权限通道，请先在设置中连接"); return@QuickItem }
                showFreeze = true
            } }
            Box(Modifier.weight(1f)) { QuickItem("power", if (ui.cfg.guardian) "暂停守护" else "恢复守护", C.danger) {
                ui.setGuardian(!ui.cfg.guardian)
                toast.show(if (ui.cfg.guardian) "守护已恢复" else "守护已暂停")
            } }
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("实时指标", action = {
            Row(Modifier.clickable { onOpenSettings() }, verticalAlignment = Alignment.CenterVertically) {
                Text("省电策略", color = C.accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Ic("chevron", 14, C.accent)
            }
        })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { MetricCard("memory", "可用内存", String.format("%.1f", availGb), "GB", "系统 MemAvailable", false) }
            Box(Modifier.weight(1f)) { MetricCard("cpu", "CPU 占用", String.format("%.1f", cpuPct), "%", "近 5 分钟均值", false) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { MetricCard("grid", "后台进程", "$bgProcs", "个", "非前台应用进程", false) }
            Box(Modifier.weight(1f)) { MetricCard("clock", "扫描间隔", "${ui.cfg.scan}", "秒", "下次 ${ui.cfg.scan}s 后", true) }
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("内存占用趋势", action = {
            Text("近 1 小时", color = C.muted, fontSize = 11.sp)
        })
        MemoryChart(memPct, ui.memHistory)
        Spacer(Modifier.height(16.dp))

        SectionTitle("最近事件", action = {
            Row(Modifier.clickable { onOpenLogs() }, verticalAlignment = Alignment.CenterVertically) {
                Text("全部", color = C.accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Ic("chevron", 14, C.accent)
            }
        })
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
                .border(1.dp, C.border, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Column {
                if (ui.logs.isEmpty()) {
                    Text("暂无事件", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    ui.logs.take(3).forEach { EventRow(it.type.key, it.title, it.detail, timeText(it.time)) }
                }
            }
        }
        Text("数据实时采集 · 尺寸按比例缩放适配", color = C.muted, fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    scanResults?.let { list ->
        ScanResultDialog(ui = ui, list = list, onClose = { scanResults = null }, onCleaned = { scanResults = null })
    }
    if (showFreeze) {
        FreezeManageDialog(ui = ui, ctx = ctx, onClose = { showFreeze = false })
    }
}

@Composable
private fun ScanResultDialog(
    ui: AppUi,
    list: List<KillCandidate>,
    onClose: () -> Unit,
    onCleaned: () -> Unit,
) {
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val apps = ui.snapshot?.apps ?: emptyList()
    fun labelOf(pkg: String) = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    var remaining by remember { mutableStateOf(list) }
    Dialog(onDismissRequest = onClose) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card)
                .border(1.dp, C.border, RoundedCornerShape(18.dp)).padding(16.dp),
        ) {
            Column {
                Text("扫描结果", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("符合规则的后台应用 ${remaining.size} 个，可逐项或全部清理",
                    color = C.text2, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(remaining, key = { it.pkg }) { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(labelOf(c.pkg), color = C.text, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${c.reason} · ${c.freedKb / 1024}MB", color = C.text2, fontSize = 10.5.sp, maxLines = 1)
                            }
                            ProcButton("清理", kind = BtnKind.Danger, onClick = {
                                scope.launch {
                                    toast.show(ui.cleanCandidates(listOf(c)))
                                    remaining = remaining.filter { it.pkg != c.pkg }
                                    if (remaining.isEmpty()) onCleaned()
                                }
                            })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcButton("关闭", kind = BtnKind.Ghost, modifier = Modifier.weight(1f), onClick = onClose)
                    ProcButton("全部清理", kind = BtnKind.Primary, modifier = Modifier.weight(1f), onClick = {
                        scope.launch { toast.show(ui.cleanCandidates(remaining)); onCleaned() }
                    })
                }
            }
        }
    }
}

@Composable
private fun FreezeManageDialog(ui: AppUi, ctx: android.content.Context, onClose: () -> Unit) {
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var onlyFrozen by remember { mutableStateOf(true) }
    val apps = ui.snapshot?.apps ?: emptyList()
    val shown = if (onlyFrozen) apps.filter { ui.isFrozen(it.packageName) } else apps
    Dialog(onDismissRequest = onClose) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card)
                .border(1.dp, C.border, RoundedCornerShape(18.dp)).padding(16.dp),
        ) {
            Column {
                Text("冻结应用", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("点击应用跳转系统设置；右侧可冻结 / 解除冻结",
                    color = C.text2, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("已冻结", onlyFrozen) { onlyFrozen = true }
                    Chip("全部应用", !onlyFrozen) { onlyFrozen = false }
                }
                Spacer(Modifier.height(10.dp))
                if (shown.isEmpty()) {
                    Text(if (onlyFrozen) "当前没有已冻结的应用" else "无应用", color = C.muted, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 20.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shown, key = { it.packageName }) { a ->
                            val frozen = ui.isFrozen(a.packageName)
                            Row(Modifier.fillMaxWidth().clickable {
                                runCatching {
                                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${a.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }, verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(a.packageName, a.label, 34)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.label, color = C.text, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(if (frozen) "已冻结" else if (a.isSystem) "系统应用" else "未冻结",
                                        color = if (frozen) C.warn else C.text2, fontSize = 10.5.sp)
                                }
                                ProcButton(if (frozen) "解除" else "冻结",
                                    kind = if (frozen) BtnKind.Ghost else BtnKind.Danger, onClick = {
                                        scope.launch {
                                            val ok = ui.freezeApp(a.packageName, !frozen)
                                            toast.show(if (ok) (if (frozen) "已解除 " else "已冻结 ") + a.label else "操作失败，请检查权限通道")
                                        }
                                    })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ProcButton("关闭", kind = BtnKind.Ghost, modifier = Modifier.fillMaxWidth(), onClick = onClose)
            }
        }
    }
}

@Composable
private fun StatusCard(icon: String, label: String, value: String, on: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(14.dp)).padding(horizontal = 11.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 14, C.text2)
            Spacer(Modifier.width(5.dp))
            Text(label, color = C.text2, fontSize = 10.5.sp)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (on) PulseDot(C.success, 6) else DotM(C.danger, 6)
            Spacer(Modifier.width(5.dp))
            Text(value, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun HeroStat(num: String, unit: String, label: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (unit.isNotEmpty()) Text(" $unit", color = color, fontSize = 11.sp)
        }
        Text(label, color = C.text2, fontSize = 10.5.sp)
    }
}

@Composable
private fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        action?.invoke()
    }
}

@Composable
private fun MemoryChart(percent: Float, history: List<Float>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("系统内存使用", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(C.accent))
                Spacer(Modifier.width(5.dp))
                Text("使用率 ${percent.toInt()}%", color = C.text2, fontSize = 10.5.sp)
            }
        }
        // 真实历史：不足 12 个采样点时用当前值补齐（不做任何演示/伪造数据）
        val filled: List<Float> = when {
            history.isEmpty() -> List(12) { percent }
            history.size >= 12 -> history.takeLast(12)
            else -> List(12 - history.size) { history.first() } + history
        }
        val bars = filled.map { (it / 100f).coerceIn(0.04f, 1f) }
        Row(Modifier.fillMaxWidth().height(84.dp).padding(top = 14.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bars.forEach { h ->
                val hot = h > 0.85f
                Box(
                    Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (hot) Brush.verticalGradient(listOf(C.warn, C.warn.copy(alpha = 0.2f)))
                            else Brush.verticalGradient(listOf(C.accent, C.accent.copy(alpha = 0.25f)))
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60min前", color = C.muted, fontSize = 9.5.sp)
            Text("30min前", color = C.muted, fontSize = 9.5.sp)
            Text("现在", color = C.muted, fontSize = 9.5.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    // 两个圆环必须使用相同的圆心与半径，否则会「漂移/错位」。
                    val inset = stroke / 2f
                    val arcSize = Size(
                        (size.width - stroke).coerceAtLeast(1f),
                        (size.height - stroke).coerceAtLeast(1f)
                    )
                    drawArc(color = C.card2, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = stroke), size = arcSize,
                        topLeft = Offset(inset, inset))
                    drawArc(color = C.accent, startAngle = -90f,
                        sweepAngle = 360f * percent.coerceIn(0f, 100f) / 100f, useCenter = false,
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        size = arcSize, topLeft = Offset(inset, inset))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${percent.toInt()}%", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("内存占用", color = C.text2, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem(C.accent, "已用", "${percent.toInt()}%")
                LegendItem(C.card2, "可用", "${100 - percent.toInt()}%")
                LegendItem(C.warn, "缓存", "-")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, color = C.text2, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = C.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EventRow(typeKey: String, title: String, sub: String, time: String) {
    val (bg, ic) = when (typeKey) {
        "kill" -> C.danger.copy(alpha = 0.15f) to "zap"
        "rule" -> C.warn.copy(alpha = 0.15f) to "snow"
        "channel" -> C.blue.copy(alpha = 0.15f) to "shield"
        else -> C.text2.copy(alpha = 0.15f) to "activity"
    }
    val fg = when (typeKey) {
        "kill" -> C.danger
        "rule" -> C.warn
        "channel" -> C.blue
        else -> C.text2
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(bg), contentAlignment = Alignment.Center) {
            Ic(ic, 16, fg)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, color = C.text2, fontSize = 10.5.sp, maxLines = 1)
        }
        Text(time, color = C.muted, fontSize = 10.sp)
    }
}

fun timeText(ms: Long): String {
    val d = java.util.Date(ms)
    val f = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return f.format(d)
}
