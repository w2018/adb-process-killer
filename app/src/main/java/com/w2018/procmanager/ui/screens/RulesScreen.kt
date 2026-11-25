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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.Cfg
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

    // 规则改为「草稿 + 保存生效」：所有修改先落到草稿，点“保存规则”才真正生效。
    val d: Cfg = ui.rulesDraft ?: ui.cfg
    fun edit(t: (Cfg) -> Cfg) {
        val nd = t(d)
        ui.rulesDraft = nd
        ui.rulesDirty = ui.rulesDiffer(nd)
    }
    // 首次进入时按当前已保存规则初始化草稿的脏状态
    LaunchedEffect(Unit) { ui.rulesDirty = ui.rulesDiffer(d) }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("触发规则", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("修改后需点「保存规则」才生效 · 带冷却防抖", color = C.text2, fontSize = 11.sp)
            }
            if (ui.rulesDirty) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(C.warn.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("未保存", color = C.warn, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // 触发阈值
        GroupCard {
            GroupLabel("触发阈值")
            SliderRow("内存占用阈值", d.mem, 50, 500, "MB") { v -> edit { it.copy(mem = v) } }
            Divider()
            SliderRow("CPU 占用阈值", d.cpu, 5, 80, "%") { v -> edit { it.copy(cpu = v) } }
        }
        Spacer(Modifier.height(14.dp))

        // 清理规则
        GroupCard {
            GroupLabel("清理规则")
            SwitchRow("memory", "内存超限清理", "后台应用内存(PSS)超过上方「内存占用阈值」时终止", d.memStop) { v -> edit { it.copy(memStop = v) } }
            Divider()
            SwitchRow("cpu", "CPU 超限清理", "后台应用 CPU 超过上方「CPU 占用阈值」时终止", d.cpuStop) { v -> edit { it.copy(cpuStop = v) } }
            Divider()
            SwitchRow("clock", "后台超时清理", "后台停留超过「常用应用保护时间」后终止", d.bgStop) { v -> edit { it.copy(bgStop = v) } }
            Divider()
            SwitchRow("lock", "关联唤醒拦截", "清理后短时间内被重新拉起（关联唤醒）则再次终止", d.wakeBlock) { v -> edit { it.copy(wakeBlock = v) } }
        }
        Spacer(Modifier.height(14.dp))

        // 增强规则
        GroupCard {
            GroupLabel("增强规则")
            SwitchRow("snow", "缓存/空进程闲置清理", "缓存档且闲置超时的进程温和回收，误杀低", d.cacheClean) { v -> edit { it.copy(cacheClean = v) } }
            if (d.cacheClean) {
                Divider()
                SliderRow("闲置超过", d.cacheIdle, 5, 60, "分钟") { v -> edit { it.copy(cacheIdle = v) } }
            }
            Divider()
            SwitchRow("memory", "内存压力触发清理", "系统可用内存低于阈值时，按占用从高到低回收", d.memPressureRule) { v -> edit { it.copy(memPressureRule = v) } }
            if (d.memPressureRule) {
                Divider()
                SliderRow("可用内存低于", d.memPressurePct, 5, 40, "%") { v -> edit { it.copy(memPressurePct = v) } }
            }
            Divider()
            SwitchRow("battery", "低电量 / 高温触发", "电量过低或电池高温时更积极地回收后台", d.boostRule) { v -> edit { it.copy(boostRule = v) } }
            if (d.boostRule) {
                Divider()
                SliderRow("电量低于", d.boostBattery, 5, 50, "%") { v -> edit { it.copy(boostBattery = v) } }
                Divider()
                SliderRow("温度高于", d.boostTemp, 35, 60, "℃") { v -> edit { it.copy(boostTemp = v) } }
            }
        }
        Spacer(Modifier.height(14.dp))

        // 执行控制
        GroupCard {
            GroupLabel("执行控制")
            SliderRow("常用应用保护时间", d.buffer, 0, 30, "分钟") { v -> edit { it.copy(buffer = v) } }
            Text("统一后台门限：应用切入后台超过该时长后，才会进入「后台超时 / 内存 / CPU」清理评估；该时长内视为常用应用受保护；回到前台立即刷新计时。",
                color = C.muted, fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 12.dp),
                lineHeight = 15.sp)
            Divider()
            SliderRow("同一应用冷却时间", d.cooldown, 5, 300, "秒") { v -> edit { it.copy(cooldown = v) } }
        }
        Spacer(Modifier.height(16.dp))

        Text("预设方案", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val presetIdx = when (d.preset) { "save" -> 0; "perf" -> 2; "custom" -> 3; else -> 1 }
        SegControl(listOf("省电", "均衡", "性能", "自定义"), presetIdx) { i ->
            when (i) {
                0 -> { edit { ui.presetOf(it, "save") }; toast.show("已套用：省电（点保存生效）") }
                1 -> { edit { ui.presetOf(it, "balance") }; toast.show("已套用：均衡（点保存生效）") }
                2 -> { edit { ui.presetOf(it, "perf") }; toast.show("已套用：性能（点保存生效）") }
                else -> toast.show("当前为自定义方案，修改任一规则参数即自动归入自定义")
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProcButton("恢复默认", onClick = {
                confirm.show("恢复默认规则?", "将把内存/后台/CPU 阈值与冷却重置为默认值（点保存后生效）。") {
                    edit { ui.resetOf(it) }; toast.show("已恢复默认，点保存生效")
                }
            }, kind = BtnKind.Ghost, modifier = Modifier.weight(1f))
            ProcButton("保存规则", onClick = {
                ui.commitRules(d)
                val name = when (ui.cfg.preset) { "save" -> "省电"; "balance" -> "均衡"; "perf" -> "性能"; else -> "自定义" }
                toast.show("规则已保存：$name")
            },
                kind = BtnKind.Primary, modifier = Modifier.weight(1f), leading = { com.w2018.procmanager.ui.components.Ic("check", 16, C.onAccent) })
        }
        Text("未点「保存规则」前，改动仅保存在草稿、不会生效；单应用规则可在应用详情中设置，优先级高于全局。", color = C.muted, fontSize = 10.sp,
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
