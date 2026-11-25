package com.w2018.procmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.BtnKind
import com.w2018.procmanager.ui.components.LocalConfirm
import com.w2018.procmanager.ui.components.LocalToast
import com.w2018.procmanager.ui.components.ProcButton
import com.w2018.procmanager.ui.components.SegControl
import com.w2018.procmanager.ui.components.SliderRow
import com.w2018.procmanager.ui.components.SwitchRow
import com.w2018.procmanager.ui.theme.C

@Composable
fun RulesScreen(ui: AppUi) {
    val toast = LocalToast.current
    val confirm = LocalConfirm.current
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("触发规则", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("命中规则后由看门狗自动执行 · 带冷却防抖", color = C.text2, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))

        // 触发阈值
        GroupCard {
            GroupLabel("触发阈值")
            SliderRow("内存占用阈值", ui.cfg.mem, 50, 500, "MB") { ui.setCfg { c -> c.copy(mem = it) } }
            Divider()
            SliderRow("后台占用时长", ui.cfg.bgTime, 1, 60, "分钟") { ui.setCfg { c -> c.copy(bgTime = it) } }
            Divider()
            SliderRow("CPU 占用阈值", ui.cfg.cpu, 5, 80, "%") { ui.setCfg { c -> c.copy(cpu = it) } }
        }
        Spacer(Modifier.height(14.dp))

        // 立即停止规则
        GroupCard {
            GroupLabel("清理规则")
            SwitchRow("cpu", "内存/CPU 超限清理", "后台应用内存或 CPU 超过上方阈值时自动终止", ui.cfg.aliveStop) { v -> ui.setCfg { it.copy(aliveStop = v) } }
            Divider()
            SwitchRow("clock", "后台超时清理", "后台停留超过「后台占用时长」后终止", ui.cfg.bgStop) { v -> ui.setCfg { it.copy(bgStop = v) } }
            Divider()
            SwitchRow("lock", "关联唤醒拦截", "清理后短时间内被重新拉起（关联唤醒）则再次终止", ui.cfg.wakeBlock) { v -> ui.setCfg { it.copy(wakeBlock = v) } }
            Divider()
            SwitchRow("star", "常用应用保护", "最近 10 分钟内使用过的应用不自动清理，避免误杀", ui.cfg.freqProtect) { v -> ui.setCfg { it.copy(freqProtect = v) } }
        }
        Spacer(Modifier.height(14.dp))

        // 执行控制
        GroupCard {
            GroupLabel("执行控制")
            SliderRow("同一应用冷却时间", ui.cfg.cooldown, 5, 300, "秒") { ui.setCfg { c -> c.copy(cooldown = it) } }
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("预设方案", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        val presetIdx = when (ui.cfg.preset) { "save" -> 0; "perf" -> 2; else -> 1 }
        SegControl(listOf("省电优先", "均衡", "性能优先"), presetIdx) { i ->
            ui.applyPreset(when (i) { 0 -> "save"; 2 -> "perf"; else -> "balance" })
            toast.show("已切换预设方案")
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProcButton("恢复默认", onClick = {
                confirm.show("恢复默认规则?", "将把内存/后台/CPU 阈值与冷却重置为默认值，此操作不可撤销。") {
                    ui.resetRules(); toast.show("已恢复默认规则")
                }
            }, kind = BtnKind.Ghost, modifier = Modifier.weight(1f))
            ProcButton("保存规则", onClick = { toast.show("规则已保存") },
                kind = BtnKind.Primary, modifier = Modifier.weight(1f), leading = { com.w2018.procmanager.ui.components.Ic("check", 16, C.onAccent) })
        }
        Text("规则修改实时保存并生效；单应用可在应用详情中覆盖全局规则", color = C.muted, fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun GroupCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
fun GroupLabel(text: String) {
    Text(text, color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 13.dp, bottom = 3.dp))
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
}
