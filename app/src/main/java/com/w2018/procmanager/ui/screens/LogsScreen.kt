package com.w2018.procmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.core.EventLog
import com.w2018.procmanager.core.LogEvent
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.Chip
import com.w2018.procmanager.ui.components.Ic
import com.w2018.procmanager.ui.components.IconBtn
import com.w2018.procmanager.ui.components.LocalConfirm
import com.w2018.procmanager.ui.components.LocalToast
import com.w2018.procmanager.ui.theme.C

@Composable
fun LogsScreen(ui: AppUi, initialFilter: String? = null, onFilterConsumed: () -> Unit = {}) {
    var filter by remember { mutableStateOf(initialFilter ?: "all") }
    LaunchedEffect(initialFilter) {
        if (initialFilter != null) { filter = initialFilter; onFilterConsumed() }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(filter) { listState.scrollToItem(0) }
    val toast = LocalToast.current
    val confirm = LocalConfirm.current
    val logs = ui.logs.filter { filter == "all" || it.type.key == filter }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("事件日志", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("共 ${ui.logs.size} 条记录 · 本次运行内记录，最多 200 条", color = C.text2, fontSize = 11.sp)
            }
            IconBtn("trash") {
                confirm.show("清空日志?", "将删除当前所有事件记录。") {
                    EventLog.clear()
                    toast.show("日志已清空")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Chip("全部", filter == "all") { filter = "all" }
            Chip("清理", filter == "kill") { filter = "kill" }
            Chip("规则", filter == "rule") { filter = "rule" }
            Chip("扫描", filter == "scan") { filter = "scan" }
            Chip("通道", filter == "channel") { filter = "channel" }
            Chip("系统", filter == "system") { filter = "system" }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(C.card)
                .border(1.dp, C.border, RoundedCornerShape(16.dp)),
        ) {
            if (logs.isEmpty()) {
                Text("暂无日志", color = C.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), state = listState) {
                    items(logs) { LogItem(it) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LogItem(e: LogEvent) {
    val (bg, ic, fg) = when (e.type.key) {
        "kill" -> Triple(C.danger.copy(alpha = 0.15f), "zap", C.danger)
        "rule" -> Triple(C.warn.copy(alpha = 0.15f), "snow", C.warn)
        "channel" -> Triple(C.blue.copy(alpha = 0.15f), "shield", C.blue)
        else -> Triple(C.text2.copy(alpha = 0.15f), "activity", C.text2)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(bg), contentAlignment = Alignment.Center) {
            Ic(ic, 15, fg)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = C.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            if (e.detail.isNotEmpty()) Text(e.detail, color = C.text2, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
        Text(timeText(e.time), color = C.muted, fontSize = 10.sp)
    }
}
