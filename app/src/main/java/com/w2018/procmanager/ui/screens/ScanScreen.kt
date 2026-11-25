package com.w2018.procmanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.core.rule.KillCandidate
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.AppIcon
import com.w2018.procmanager.ui.components.BtnKind
import com.w2018.procmanager.ui.components.Ic
import com.w2018.procmanager.ui.components.LocalToast
import com.w2018.procmanager.ui.components.ProcButton
import com.w2018.procmanager.ui.components.ToastState
import com.w2018.procmanager.ui.theme.C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private enum class ScanPhase { RUNNING, DONE, EMPTY, ERROR, CANCELLED }

private data class ScanState(
    val phase: ScanPhase = ScanPhase.RUNNING,
    val progress: Float = 0f,
    val scanned: Int = 0,
    val total: Int = 0,
    val current: String = "正在初始化扫描引擎…",
    val results: List<KillCandidate> = emptyList(),
    val error: String = "",
    val cleaning: Boolean = false,
)

/**
 * 扫描控制器：扫描过程与 UI 解耦。状态通过 StateFlow 增量下发，
 * 各子组件分别订阅，避免整屏重绘；动画与数据采集互不阻塞。
 */
private class ScanController(private val ui: AppUi) {
    private val _s = MutableStateFlow(ScanState())
    val s: StateFlow<ScanState> = _s
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        _s.value = ScanState()
        job = scope.launch {
            try {
                if (!ui.channelReady) {
                    _s.update { it.copy(phase = ScanPhase.ERROR, error = "未连接权限通道，请先在「设置」中连接") }
                    return@launch
                }
                _s.update { it.copy(current = "正在读取进程与应用列表…") }
                val prep = ui.prepareScan()
                    ?: run {
                        _s.update { it.copy(phase = ScanPhase.ERROR, error = "扫描失败：无法获取进程快照") }
                        return@launch
                    }
                val targets = prep.targets
                _s.update { it.copy(total = targets.size) }
                if (targets.isEmpty()) {
                    _s.update { it.copy(phase = ScanPhase.EMPTY, progress = 1f) }
                    return@launch
                }
                val hitMap = prep.hits.associateBy { it.pkg }
                val per = when {
                    targets.size > 120 -> 14L
                    targets.size > 60 -> 22L
                    targets.size > 30 -> 32L
                    else -> 55L
                }
                val acc = ArrayList<KillCandidate>()
                for ((idx, t) in targets.withIndex()) {
                    _s.update { it.copy(current = t.label, scanned = idx, progress = idx.toFloat() / targets.size) }
                    delay(per)
                    val h = hitMap[t.pkg]
                    if (h != null && acc.none { it.pkg == h.pkg }) {
                        acc.add(h)
                        _s.update { it.copy(results = acc.toList()) }
                    }
                    _s.update { it.copy(scanned = idx + 1, progress = (idx + 1).toFloat() / targets.size) }
                }
                delay(160)
                _s.update { it.copy(phase = if (acc.isEmpty()) ScanPhase.EMPTY else ScanPhase.DONE, progress = 1f) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _s.update { it.copy(phase = ScanPhase.ERROR, error = e.message ?: "扫描异常") }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _s.update { it.copy(phase = ScanPhase.CANCELLED) }
    }

    fun markCleaned() {
        _s.update { it.copy(cleaning = false, results = emptyList(), phase = ScanPhase.EMPTY) }
    }

    fun setCleaning(v: Boolean) = _s.update { it.copy(cleaning = v) }
}

@Composable
fun ScanScreen(ui: AppUi, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctrl = remember { ScanController(ui) }
    val toast = LocalToast.current
    LaunchedEffect(Unit) { ctrl.start(scope) }
    BackHandler(enabled = true) { ctrl.cancel(); onClose() }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(C.card).clickable { ctrl.cancel(); onClose() },
                    contentAlignment = Alignment.Center,
                ) { Ic("chevron", 18, C.text) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("进程扫描", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("实时扫描后台应用与进程规则", color = C.text2, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            ScanOrb(ctrl)
            Spacer(Modifier.height(8.dp))
            ProgressSection(ctrl)
            Spacer(Modifier.height(10.dp))
            CurrentItemLine(ctrl)
            Spacer(Modifier.height(8.dp))
            StatusLine(ctrl)
            Spacer(Modifier.height(10.dp))
            ResultsHeader(ctrl)
            Spacer(Modifier.height(6.dp))
            ResultsList(ctrl, Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            BottomActions(ctrl, ui, toast, scope, onClose)
        }
    }
}

@Composable
private fun ScanOrb(ctrl: ScanController) {
    val st by ctrl.s.collectAsState()
    val accent = when (st.phase) {
        ScanPhase.RUNNING, ScanPhase.DONE -> C.accent
        ScanPhase.EMPTY -> C.success
        ScanPhase.ERROR -> C.danger
        ScanPhase.CANCELLED -> C.warn
    }
    val infinite = rememberInfiniteTransition(label = "orb")
    val angle by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val running = st.phase == ScanPhase.RUNNING
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size((132 * if (running) pulse else 1f).dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))),
        )
        Canvas(Modifier.size(120.dp)) {
            val stroke = 8f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(color = C.border, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = accent, startAngle = -90f, sweepAngle = 360f * st.progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (running) {
            Canvas(Modifier.size(120.dp).rotate(angle)) {
                val r = size.minDimension / 2f
                drawLine(
                    color = accent.copy(alpha = 0.9f), strokeWidth = 4f,
                    start = Offset(size.width / 2f, size.height / 2f - r + 18f),
                    end = Offset(size.width / 2f, size.height / 2f - r + 42f), cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (st.phase) {
                    ScanPhase.RUNNING -> "${(st.progress * 100).toInt()}%"
                    ScanPhase.DONE -> "已完成"
                    ScanPhase.EMPTY -> "安全"
                    ScanPhase.ERROR -> "异常"
                    ScanPhase.CANCELLED -> "已中断"
                },
                color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                when (st.phase) {
                    ScanPhase.RUNNING -> "扫描中"
                    ScanPhase.DONE -> "发现 ${st.results.size} 项"
                    ScanPhase.EMPTY -> "未发现威胁"
                    ScanPhase.ERROR -> "扫描失败"
                    ScanPhase.CANCELLED -> "扫描中断"
                },
                color = C.text2, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ProgressSection(ctrl: ScanController) {
    val st by ctrl.s.collectAsState()
    val anim by animateFloatAsState(st.progress.coerceIn(0f, 1f), animationSpec = tween(120), label = "prog")
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(C.card2)) {
            Box(Modifier.fillMaxWidth(anim).height(7.dp).clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(C.accent, C.blue))))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                when (st.phase) {
                    ScanPhase.RUNNING -> "正在扫描 ${st.scanned}/${st.total}"
                    ScanPhase.DONE -> "已扫描 ${st.total}/${st.total} 个应用"
                    ScanPhase.EMPTY -> "已扫描 ${st.total} 个应用"
                    ScanPhase.ERROR -> "扫描中断"
                    ScanPhase.CANCELLED -> "已扫描 ${st.scanned}/${st.total}（已中断）"
                },
                color = C.text2, fontSize = 10.5.sp,
            )
            Text("${(st.progress * 100).toInt()}%", color = C.accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CurrentItemLine(ctrl: ScanController) {
    val st by ctrl.s.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (st.phase == ScanPhase.RUNNING) {
            ScanDots()
            Spacer(Modifier.width(8.dp))
            Text("正在扫描：", color = C.text2, fontSize = 11.5.sp)
            Text(st.current.ifBlank { "…" }, color = C.text, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Ic("check", 14, C.accent)
            Spacer(Modifier.width(8.dp))
            Text("扫描对象：${st.total} 个应用", color = C.text2, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun ScanDots() {
    val infinite = rememberInfiniteTransition(label = "dots")
    val a by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val on = ((a * 3).toInt() % 3) == i
            Box(
                Modifier.padding(end = 3.dp).size(if (on) 6.dp else 4.dp).clip(CircleShape)
                    .background(if (on) C.accent else C.muted.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun StatusLine(ctrl: ScanController) {
    val st by ctrl.s.collectAsState()
    val color = when (st.phase) {
        ScanPhase.RUNNING -> C.text2
        ScanPhase.DONE -> C.accent
        ScanPhase.EMPTY -> C.success
        ScanPhase.ERROR -> C.danger
        ScanPhase.CANCELLED -> C.warn
    }
    val text = when (st.phase) {
        ScanPhase.RUNNING -> "扫描进行中，动画与数据实时更新互不阻塞"
        ScanPhase.DONE -> "扫描完成：发现 ${st.results.size} 个可清理项，可一键清理"
        ScanPhase.EMPTY -> "扫描完成：当前很健康，未发现需要清理的后台应用"
        ScanPhase.ERROR -> "扫描异常：${st.error}"
        ScanPhase.CANCELLED -> "扫描已中断（可重新扫描）"
    }
    Text(text, color = color, fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun ResultsHeader(ctrl: ScanController) {
    val st by ctrl.s.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("扫描结果", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (st.results.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text("${st.results.size}", color = C.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(C.danger).padding(horizontal = 7.dp, vertical = 1.dp))
        }
        Spacer(Modifier.weight(1f))
        if (st.results.isNotEmpty()) Text("按可释放内存排序", color = C.muted, fontSize = 10.sp)
    }
}

@Composable
private fun ResultsList(ctrl: ScanController, modifier: Modifier) {
    val st by ctrl.s.collectAsState()
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(14.dp)),
    ) {
        if (st.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when (st.phase) {
                        ScanPhase.RUNNING -> "扫描中，暂无命中项…"
                        ScanPhase.EMPTY -> "未发现可清理项目"
                        ScanPhase.ERROR -> "无法获取结果"
                        ScanPhase.CANCELLED -> "扫描已中断，暂无结果"
                        ScanPhase.DONE -> "无结果"
                    },
                    color = C.muted, fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                items(st.results, key = { it.pkg }) { c -> ResultRow(c) }
            }
        }
    }
}

@Composable
private fun ResultRow(c: KillCandidate) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(c.pkg, c.pkg, 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.pkg, color = C.text, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${c.reason} · 约释放 ${c.freedKb / 1024}MB", color = C.text2, fontSize = 10.sp, maxLines = 1)
        }
        Text("可清理", color = C.danger, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BottomActions(ctrl: ScanController, ui: AppUi, toast: ToastState, scope: CoroutineScope, onClose: () -> Unit) {
    val st by ctrl.s.collectAsState()
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (st.phase == ScanPhase.RUNNING) {
            ProcButton("停止扫描", onClick = { ctrl.cancel() }, kind = BtnKind.Ghost, modifier = Modifier.weight(1f))
            ProcButton("关闭", onClick = { ctrl.cancel(); onClose() }, kind = BtnKind.Primary, modifier = Modifier.weight(1f))
        } else {
            val hasResults = st.phase == ScanPhase.DONE && st.results.isNotEmpty()
            if (hasResults) {
                ProcButton(
                    if (st.cleaning) "清理中…" else "全部清理",
                    onClick = {
                        if (!st.cleaning) scope.launch {
                            ctrl.setCleaning(true)
                            val msg = ui.cleanCandidates(st.results)
                            ctrl.markCleaned()
                            toast.show(msg)
                        }
                    },
                    kind = BtnKind.Primary, modifier = Modifier.weight(1f),
                )
            } else {
                ProcButton("重新扫描", onClick = { ctrl.start(scope) }, kind = BtnKind.Primary, modifier = Modifier.weight(1f))
            }
            ProcButton("完成", onClick = { onClose() }, kind = BtnKind.Ghost, modifier = Modifier.weight(1f))
        }
    }
}
