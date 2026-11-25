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
            SwitchRow("memory", "内存超限清理", "后台应用内存(PSS)超过上方「内存占用阈值」时终止", ui.cfg.memStop) { v -> ui.setCfg { it.copy(memStop = v) } }
            Divider()
            SwitchRow("cpu", "CPU 超限清理", "后台应用 CPU 超过上方「CPU 占用阈值」时终止", ui.cfg.cpuStop) { v -> ui.setCfg { it.copy(cpuStop = v) } }
            Divider()
            SwitchRow("clock", "后台超时清理", "后台停留超过「后台占用时长」后终止", ui.cfg.bgStop) { v -> ui.setCfg { it.copy(bgStop = v) } }
            Divider()
            SwitchRow("lock", "关联唤醒拦截", "清理后短时间内被重新拉起（关联唤醒）则再次终止", ui.cfg.wakeBlock) { v -> ui.setCfg { it.copy(wakeBlock = v) } }
        }
        Spacer(Modifier.height(14.dp))

        // 增强规则（条件触发）
        GroupCard {
            GroupLabel("增强规则")
            SwitchRow("snow", "缓存/空进程闲置清理", "缓存档且闲置超时的进程温和回收，误杀低", ui.cfg.cacheClean) { v -> ui.setCfg { it.copy(cacheClean = v) } }
            if (ui.cfg.cacheClean) {
                Divider()
                SliderRow("闲置超过", ui.cfg.cacheIdle, 5, 60, "分钟") { v -> ui.setCfg { it.copy(cacheIdle = v) } }
            }
            Divider()
            SwitchRow("memory", "内存压力触发清理", "系统可用内存低于阈值时，按占用从高到低回收", ui.cfg.memPressureRule) { v -> ui.setCfg { it.copy(memPressureRule = v) } }
            if (ui.cfg.memPressureRule) {
                Divider()
                SliderRow("可用内存低于", ui.cfg.memPressurePct, 5, 40, "%") { v -> ui.setCfg { it.copy(memPressurePct = v) } }
            }
            Divider()
            SwitchRow("battery", "低电量 / 高温触发", "电量过低或电池高温时更积极地回收后台", ui.cfg.boostRule) { v -> ui.setCfg { it.copy(boostRule = v) } }
            if (ui.cfg.boostRule) {
                Divider()
                SliderRow("电量低于", ui.cfg.boostBattery, 5, 50, "%") { v -> ui.setCfg { it.copy(boostBattery = v) } }
                Divider()
                SliderRow("温度高于", ui.cfg.boostTemp, 35, 60, "℃") { v -> ui.setCfg { it.copy(boostTemp = v) } }
            }
        }
        Spacer(Modifier.height(14.dp))

        // 执行控制
        GroupCard {
            GroupLabel("执行控制")
            SliderRow("常用应用保护时间", ui.cfg.buffer, 0, 30, "分钟") { ui.setCfg { c -> c.copy(buffer = it) } }
            Text("应用进入后台超过该时长才按「内存/CPU 阈值」清理（即该时长内视为“常用应用”受保护，不清理）；回到前台立即刷新计时。",
                color = C.muted, fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 12.dp),
                lineHeight = 15.sp)
            Divider()
            SliderRow("同一应用冷却时间", ui.cfg.cooldown, 5, 300, "秒") { ui.setCfg { c -> c.copy(cooldown = it) } }
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("预设方案", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        val presetIdx = when (ui.cfg.preset) { "save" -> 0; "perf" -> 2; "custom" -> 3; else -> 1 }
        SegControl(listOf("省电", "均衡", "性能", "自定义"), presetIdx) { i ->
            when (i) {
                0 -> { ui.applyPreset("save"); toast.show("已切换：省电") }
                1 -> { ui.applyPreset("balance"); toast.show("已切换：均衡") }
                2 -> { ui.applyPreset("perf"); toast.show("已切换：性能") }
                else -> toast.show("当前为自定义方案，修改任一规则参数即自动归入自定义")
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProcButton("恢复默认", onClick = {
                confirm.show("恢复默认规则?", "将把内存/后台/CPU 阈值与冷却重置为默认值，此操作不可撤销。") {
                    ui.resetRules(); toast.show("已恢复默认规则")
                }
            }, kind = BtnKind.Ghost, modifier = Modifier.weight(1f))
            ProcButton("保存规则", onClick = {
                ui.setCfg { it } // 触发一次预设识别
                val name = when (ui.cfg.preset) { "save" -> "省电"; "balance" -> "均衡"; "perf" -> "性能"; else -> "自定义" }
                toast.show("规则已保存：$name")
            },
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
