package com.w2018.procmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.data.model.AppInfo
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.AppIcon
import com.w2018.procmanager.ui.components.Chip
import com.w2018.procmanager.ui.components.Ic
import com.w2018.procmanager.ui.components.TagC
import com.w2018.procmanager.ui.theme.C

/**
 * 排行榜：统计每个应用被清理的次数，并针对自启动 / 高频被清理 / 高内存占用给出优化与设置建议。
 * 入口由底部 Tab「排行榜」进入；日志入口已移至首页「最近事件-全部」。
 */
@Composable
fun RankScreen(ui: AppUi, onOpenApp: (String) -> Unit = {}) {
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableStateOf("today") }
    val cats = listOf("清理排行", "自启排行", "优化建议")

    val apps = ui.snapshot?.apps ?: emptyList()
    val appMap = remember(apps) { apps.associateBy { it.packageName } }
    // 每个应用的内存占用（PSS 优先，回退 RSS）
    val memMap = remember(ui.snapshot) {
        val m = HashMap<String, Long>()
        ui.snapshot?.processes?.forEach { p ->
            val v = if (p.pssKb > 0) p.pssKb else p.rssKb
            m[p.packageName] = (m[p.packageName] ?: 0L) + v
        }
        m
    }
    val killCounts = ui.killCounts

    // 清理排行：按累计清理次数降序
    val killSorted = remember(killCounts, appMap) {
        killCounts.entries.filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { e -> e.key to e.value }
    }

    // 自启排行：统计各应用在所选时间段内的自启动次数
    val autoCounts = remember(ui.snapshot, period, ui.blocked, ui.killCounts) {
        runCatching { ui.autoStartCounts(period) }.getOrDefault(emptyMap())
    }
    // 自启排行列表：出现过的自启应用（次数>0）或具备自启能力的应用，按自启次数降序，其次内存
    val autoSorted = remember(apps, autoCounts, memMap) {
        apps.filter { (autoCounts[it.packageName] ?: 0) > 0 || it.autoStart }
            .sortedWith(
                compareByDescending<AppInfo> { autoCounts[it.packageName] ?: 0 }
                    .thenByDescending { memMap[it.packageName] ?: 0L }
            )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp)) {
            Text("排行榜", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text("后台清理统计 · 自启与清理频率分析 · 优化建议", color = C.text2, fontSize = 11.sp)
        }

        // 概览小卡
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("${killCounts.values.sum()}", "累计清理(次)", C.danger, Modifier.weight(1f))
            MiniStat("${killCounts.values.count { it > 0 }}", "涉及应用(个)", C.accent, Modifier.weight(1f))
            MiniStat("${autoSorted.size}", "自启动应用", C.warn, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEachIndexed { i, s -> Chip(s, i == cat) { cat = i } }
        }
        Spacer(Modifier.height(10.dp))

        when (cat) {
            0 -> KillRankList(killSorted, appMap, killCounts, ui, onOpenApp)
            1 -> {
                // 时间段快捷筛选
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val periods = listOf("today" to "今日", "yesterday" to "昨日", "week" to "近7天", "month" to "当月")
                    periods.forEach { (k, label) -> Chip(label, period == k) { period = k } }
                }
                Spacer(Modifier.height(10.dp))
                AutoRankList(autoSorted, autoCounts, memMap, ui, onOpenApp)
            }
            else -> SuggestionList(killCounts, appMap, memMap, autoSorted, ui, onOpenApp)
        }
    }
}

@Composable
private fun MiniStat(num: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(num, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = C.text2, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
    }
}

@Composable
private fun RankRow(rank: Int, app: AppInfo, onClick: () -> Unit, tail: @Composable () -> Unit) {
    val rankColor = when (rank) {
        1 -> C.warn; 2 -> C.text2; 3 -> C.blue; else -> C.muted
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            Text("$rank", color = rankColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(4.dp))
        AppIcon(app.packageName, app.label, 38)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TagC(if (app.autoStart) "可自启" else "无自启", if (app.autoStart) C.warn else C.muted)
                Spacer(Modifier.width(6.dp))
                TagC(if (app.isSystem) "系统" else "用户", if (app.isSystem) C.purple else C.blue)
            }
        }
        tail()
        Spacer(Modifier.width(4.dp))
        Ic("chevron", 15, C.muted)
    }
}

@Composable
private fun KillRankList(
    list: List<Pair<String, Int>>,
    appMap: Map<String, AppInfo>,
    killCounts: Map<String, Int>,
    ui: AppUi,
    onOpenApp: (String) -> Unit,
) {
    if (list.isEmpty()) { Empty("暂无清理记录，守护运行并触发清理后这里会生成排行") ; return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.first }) { (pkg, cnt) ->
            val app = appMap[pkg] ?: AppInfo(pkg, pkg, false, 0)
            val rank = list.indexOfFirst { it.first == pkg } + 1
            RankRow(rank, app, { onOpenApp(pkg) }) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("$cnt 次", color = C.danger, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("被清理", color = C.text2, fontSize = 9.5.sp)
                }
            }
        }
    }
}

@Composable
private fun AutoRankList(
    list: List<AppInfo>,
    autoCounts: Map<String, Int>,
    memMap: Map<String, Long>,
    ui: AppUi,
    onOpenApp: (String) -> Unit,
) {
    if (list.isEmpty()) { Empty("所选时间段暂无自启动记录；开启守护并保持无障碍/看门狗运行后会自动统计") ; return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.packageName }) { app ->
            val rank = list.indexOf(app) + 1
            val blocked = ui.bgRestricted.contains(app.packageName)
            val cnt = autoCounts[app.packageName] ?: 0
            RankRow(rank, app, { onOpenApp(app.packageName) }) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("自启 $cnt 次", color = if (cnt > 0) C.warn else C.muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (blocked) "已禁止自启" else "允许自启", color = if (blocked) C.success else C.text2, fontSize = 9.5.sp,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

private data class Suggestion(val app: AppInfo, val text: String, val color: Color, val tag: String)

@Composable
private fun SuggestionList(
    killCounts: Map<String, Int>,
    appMap: Map<String, AppInfo>,
    memMap: Map<String, Long>,
    autoApps: List<AppInfo>,
    ui: AppUi,
    onOpenApp: (String) -> Unit,
) {
    val monitorAll = ui.cfg.monitorMode == "all"
    val out = ArrayList<Suggestion>()
    // 1) 频繁被清理 + 可自启 → 建议禁止开机自启
    killCounts.entries.sortedByDescending { it.value }.forEach { (pkg, cnt) ->
        val app = appMap[pkg] ?: return@forEach
        if (cnt >= 2 && app.autoStart && !ui.bgRestricted.contains(pkg)) {
            out.add(Suggestion(app, "近段时间被清理 $cnt 次且支持开机自启，建议在「自启动治理」中禁止其自启，从源头减少后台唤醒。", C.warn, "禁止自启"))
        }
    }
    // 2) 频繁被清理但不在监控范围 → 建议纳入监控/单应用规则
    if (!monitorAll) {
        killCounts.entries.sortedByDescending { it.value }.forEach { (pkg, cnt) ->
            val app = appMap[pkg] ?: return@forEach
            if (cnt >= 3 && !ui.monitored.contains(pkg) && out.none { it.app.packageName == pkg }) {
                out.add(Suggestion(app, "被清理 $cnt 次却未纳入监控，建议加入监控或设置单应用规则，实现更精准的自动清理。", C.blue, "加入监控"))
            }
        }
    }
    // 3) 高内存占用 → 建议下调内存阈值 / 后台即停
    memMap.entries.sortedByDescending { it.value }.take(6).forEach { (pkg, kb) ->
        val app = appMap[pkg] ?: return@forEach
        val mb = kb / 1024
        if (mb >= 200 && out.none { it.app.packageName == pkg }) {
            out.add(Suggestion(app, "内存占用约 $mb MB 偏高，建议为其实施「后台即停」或下调内存阈值，及时回收内存。", C.danger, "占用高"))
        }
    }
    // 4) 已禁止自启 / 无风险 → 正向反馈（最多 3 条）
    autoApps.filter { ui.bgRestricted.contains(it.packageName) }.take(3).forEach { app ->
        if (out.none { it.app.packageName == app.packageName })
            out.add(Suggestion(app, "已禁止开机自启，后台占用可控，保持现状即可。", C.success, "已优化"))
    }

    if (out.isEmpty()) {
        Empty("暂无优化建议。保持守护服务运行，应用被多次清理/自启时将自动给出针对性建议。")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(out, key = { it.app.packageName }) { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                    .border(1.dp, C.border, RoundedCornerShape(14.dp))
                    .clickable { onOpenApp(s.app.packageName) }.padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AppIcon(s.app.packageName, s.app.label, 38)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.app.label, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        TagC(s.tag, s.color)
                    }
                    Text(s.text, color = C.text2, fontSize = 10.5.sp, lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Empty(msg: String) {
    Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = C.muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
