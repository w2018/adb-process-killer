package com.w2018.procmanager.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.BtnKind
import com.w2018.procmanager.ui.components.Ic
import com.w2018.procmanager.ui.components.ProcButton
import com.w2018.procmanager.ui.components.ProcSwitch
import com.w2018.procmanager.ui.components.SegControl
import com.w2018.procmanager.ui.components.SliderRow
import com.w2018.procmanager.ui.components.SwitchRow
import com.w2018.procmanager.ui.components.TagC
import com.w2018.procmanager.ui.theme.C
import com.w2018.procmanager.ui.theme.ThemeState

@Composable
fun SettingsScreen(ui: AppUi) {
    val scroll = rememberScrollState()
    var showWhite by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showAutoStart by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Column(Modifier.padding(top = 20.dp, bottom = 14.dp)) {
            Text("设置", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text("看门狗与省电策略 · 所有改动即时生效", color = C.text2, fontSize = 11.sp)
        }

        // ---------------- 守护服务 ----------------
        SetGroupLabel("守护服务")
        SetGroupCard {
            SwitchRow("shield", "前台服务保活", "常驻通知栏，维持后台运行权限", ui.cfg.guardian) { v ->
                ui.setGuardian(v)
            }
            SetDivider()
            SwitchRow("activity", "启用看门狗", "后台实时监控进程，按规则自动处理", ui.cfg.watchdog) { v ->
                ui.setCfg { it.copy(watchdog = v) }
            }
            SetDivider()
            SwitchRow("unlock", "开机自启动", "设备重启后自动恢复守护", ui.cfg.autoStart) { v ->
                ui.setCfg { it.copy(autoStart = v) }
            }
            SetDivider()
            SwitchRow("bell", "状态通知", "清理/冻结事件提醒；常驻通知用于前台保活，不可关闭", ui.cfg.notify) { v ->
                ui.setCfg { it.copy(notify = v) }
            }
        }

        // ---------------- 监控范围 ----------------
        SetGroupLabel("监控范围")
        SetGroupCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text("监控哪些应用", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("全部用户应用=默认管理所有用户应用；仅勾选=只管理应用详情中手动加入的应用",
                    color = C.text2, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp, bottom = 8.dp))
                SegControl(listOf("全部用户应用", "仅勾选"), if (ui.cfg.monitorMode == "all") 0 else 1) { i ->
                    ui.setMonitorMode(if (i == 0) "all" else "selected")
                }
            }
        }

        // ---------------- 看门狗 / 省电 ----------------
        SetGroupLabel("看门狗 / 省电")
        SetGroupCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text("扫描间隔", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("间隔越大越省电，发现越延迟", color = C.text2, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp, bottom = 8.dp))
                val opts = listOf("15s", "30s", "60s", "120s")
                val vals = listOf(15, 30, 60, 120)
                SegControl(opts, vals.indexOf(ui.cfg.scan).coerceAtLeast(0)) { i -> ui.setScan(vals[i]) }
            }
            SetDivider()
            SliderRow("扫描频率上限", ui.cfg.adbFreq, 5, 60, "次/分") { v -> ui.setCfg { it.copy(adbFreq = v) } }
            SetDivider()
            SwitchRow("lock", "CPU 锁按需持有", "仅扫描瞬间短时持锁并超时释放", ui.cfg.lockLowPower) { v -> ui.setCfg { it.copy(lockLowPower = v) } }
            SetDivider()
            SwitchRow("moon", "锁屏深度省电", "锁屏后自动降低扫描频率", ui.cfg.screenOffSave) { v -> ui.setCfg { it.copy(screenOffSave = v) } }
            SetDivider()
            SwitchRow("battery", "充电豁免", "充电时不执行清理，避免干扰", ui.cfg.chargeExempt) { v -> ui.setCfg { it.copy(chargeExempt = v) } }
            SetDivider()
            SwitchRow("clock", "夜间严格模式", "23:00–07:00 更积极清理后台", ui.cfg.nightMode) { v -> ui.setCfg { it.copy(nightMode = v) } }
        }

        // ---------------- 外观 ----------------
        SetGroupLabel("外观")
        SetGroupCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text("主题模式", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("跟随系统，或固定使用深色 / 浅色", color = C.text2, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp, bottom = 8.dp))
                val modes = listOf("system", "dark", "light")
                val labels = listOf("跟随系统", "深色", "浅色")
                SegControl(labels, modes.indexOf(ThemeState.mode).coerceAtLeast(0)) { i ->
                    ThemeState.mode = modes[i]
                    ui.app.settings.themeMode = modes[i]
                }
            }
        }

        // ---------------- 权限通道 ----------------
        SetGroupLabel("权限通道")
        SetGroupCard {
            // Shizuku
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                    Ic("shield", 16, C.text2)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shizuku（推荐）", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        TagC(ui.shizukuText.ifBlank { "检测中" }, C.accent)
                    }
                    Text("通过 Shizuku 获得 shell 权限，无需反复授权", color = C.text2, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
                }
                val shzAuth = ui.app.channels.shizukuAuthorized()
                MiniAction(if (shzAuth) "连接" else "授权") {
                    if (shzAuth) ui.connectShizuku() else ui.requestShizuku()
                }
            }
            SetDivider()
            // 无线 ADB
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                        Ic("cable", 16, C.text2)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("无线 ADB（本机自连）", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("直连本机 adbd（默认扫描 5555/5557 等端口）", color = C.text2, fontSize = 10.5.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = ui.adbTarget,
                    onValueChange = { ui.adbTarget = it },
                    singleLine = true,
                    label = { Text("地址 host:port", color = C.muted, fontSize = 11.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.text, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                        focusedContainerColor = C.card2, unfocusedContainerColor = C.card2,
                        cursorColor = C.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcButton("扫描端口", onClick = { ui.scanAdb() }, kind = BtnKind.Ghost, modifier = Modifier.weight(1f),
                        leading = { Ic("search", 16, C.text) })
                    ProcButton("连接", onClick = { ui.connectAdbTarget() }, kind = BtnKind.Primary, modifier = Modifier.weight(1f),
                        leading = { Ic("cable", 16, C.onAccent) })
                }
                if (ui.scanning) {
                    Text("正在扫描…", color = C.text2, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (ui.scanPorts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.scanPorts.forEach { p ->
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(C.accent.copy(alpha = 0.12f))
                                    .border(1.dp, C.accent, RoundedCornerShape(20.dp))
                                    .clickable { ui.connectAdb("127.0.0.1", p) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text("127.0.0.1:$p", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(ui.channelDetail, color = if (ui.channelReady) C.success else C.text2, fontSize = 10.5.sp, lineHeight = 15.sp)
            }
            SetDivider()
            // 自动连接
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动连接", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("优先 Shizuku，失败自动降级本机无线 ADB", color = C.text2, fontSize = 10.5.sp)
                }
                MiniAction(if (ui.channelReady) "已连接" else "连接") { ui.autoConnect() }
            }
            SetDivider()
            // 无障碍服务
            Row(
                Modifier.fillMaxWidth().clickable {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                    Ic("accessibility", 16, C.text2)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("无障碍服务", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("用于前台切换检测，事件驱动更省电（可选）", color = C.text2, fontSize = 10.5.sp)
                }
                TagC(if (ui.a11y) "已开启" else "未开启", if (ui.a11y) C.accent else C.muted)
                Spacer(Modifier.width(6.dp))
                Ic("chevron", 16, C.text2)
            }
        }
        Text("提示：无线 ADB 需设备已开启「无线调试」或 adb tcpip；真机首次连接需在系统弹窗中允许该调试密钥。",
            color = C.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp), lineHeight = 15.sp)

        // ---------------- 其他 ----------------
        SetGroupLabel("其他")
        SetGroupCard {
            RowItem("star", "白名单管理", "永不清理的应用", "${ui.whitelist.size} 个") { showWhite = true }
            SetDivider()
            RowItem("power", "自启动治理", "开机自启应用，可冻结不常用项", "") { showAutoStart = true }
            SetDivider()
            RowItem("info", "关于进程杀手", "版本 0.1.5 · Shizuku / ADB", "") { showAbout = true }
        }
        Text("省电设计：非精确闹钟周期唤醒 · 短时持锁及时释放 · 调用频率上限 · 无变化跳过处理",
            color = C.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp), lineHeight = 15.sp)
    }

    if (showWhite) WhitelistDialog(ui) { showWhite = false }
    if (showAbout) AboutDialog { showAbout = false }
    if (showAutoStart) AutoStartDialog(ui) { showAutoStart = false }
}

@Composable
private fun MiniAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(C.accent.copy(alpha = 0.14f))
            .border(1.dp, C.accent, RoundedCornerShape(9.dp)).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(text, color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SetGroupLabel(t: String) {
    Text(t, color = C.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
private fun SetGroupCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(16.dp)),
    ) { content() }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SetDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(C.border.copy(alpha = 0.6f)))
}

@Composable
private fun RowItem(icon: String, title: String, desc: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.card2), contentAlignment = Alignment.Center) {
            Ic(icon, 16, C.text2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = C.text2, fontSize = 10.5.sp)
        }
        if (value.isNotEmpty()) { Text(value, color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(4.dp)) }
        Ic("chevron", 16, C.text2)
    }
}

@Composable
private fun WhitelistDialog(ui: AppUi, onClose: () -> Unit) {
    val apps = ui.snapshot?.apps ?: emptyList()
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(18.dp)).background(C.bg2).padding(16.dp)) {
            Text("白名单管理", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("白名单中的应用永不清理、永不冻结", color = C.text2, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(apps, key = { it.packageName }) { a ->
                    if (a.isSystem) return@items
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.label, color = C.text, fontSize = 13.sp, maxLines = 1)
                            Text(a.packageName, color = C.muted, fontSize = 9.5.sp, maxLines = 1)
                        }
                        ProcSwitch(ui.whitelist.contains(a.packageName)) { ui.toggleWhite(a.packageName) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ProcButton("完成", onClick = onClose, kind = BtnKind.Primary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AutoStartDialog(ui: AppUi, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snap = ui.snapshot
    val memMap = remember(snap) {
        val m = HashMap<String, Long>()
        snap?.processes?.forEach { p ->
            m[p.packageName] = (m[p.packageName] ?: 0L) + (if (p.pssKb > 0) p.pssKb else p.rssKb)
        }
        m
    }
    val autoApps = (snap?.apps ?: emptyList())
        .filter { it.autoStart && !it.isSystem && it.packageName != ui.app.packageName }
        .sortedByDescending { memMap[it.packageName] ?: 0L }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(18.dp)).background(C.bg2).padding(16.dp)) {
            Text("自启动治理", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("监听开机自启的应用共 ${autoApps.size} 个 · 按内存排序；冻结不常用项可减少自启与后台占用", color = C.text2, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(autoApps, key = { it.packageName }) { a ->
                    val mem = (memMap[a.packageName] ?: 0L) / 1024
                    val frozen = ui.isFrozen(a.packageName)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.label, color = C.text, fontSize = 13.sp, maxLines = 1)
                            Text("${a.packageName} · ${mem}MB", color = C.muted, fontSize = 9.5.sp, maxLines = 1)
                        }
                        MiniAction(if (frozen) "恢复" else "冻结") {
                            scope.launch { ui.freezeApp(a.packageName, !frozen) }
                        }
                    }
                }
            }
            if (autoApps.isEmpty()) {
                Text("暂无自启动应用", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
            Spacer(Modifier.height(8.dp))
            ProcButton("完成", onClick = onClose, kind = BtnKind.Primary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.bg2).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Ic("shield", 22, C.accent)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("进程杀手", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("版本 0.1.5", color = C.text2, fontSize = 11.sp)
                }
            }
            Text("面向旧设备的后台进程治理工具：通过 Shizuku / 无线 ADB 获得 shell 权限，读取并管理（含系统应用）后台进程，按规则自动清理以降低发热与卡顿。所有操作仅限「读取进程 / 冻结停用 / 终止运行」，不会卸载应用或清除数据。",
                color = C.text2, fontSize = 11.5.sp, modifier = Modifier.padding(top = 14.dp), lineHeight = 17.sp)
            Spacer(Modifier.height(16.dp))
            ProcButton("知道了", onClick = onClose, kind = BtnKind.Primary, modifier = Modifier.fillMaxWidth())
        }
    }
}
