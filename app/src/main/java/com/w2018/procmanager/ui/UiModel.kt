package com.w2018.procmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.w2018.procmanager.ProcApp
import com.w2018.procmanager.core.GuardEngine
import com.w2018.procmanager.core.ScanPrep
import com.w2018.procmanager.core.EventHub
import com.w2018.procmanager.core.EventLog
import com.w2018.procmanager.core.LogEvent
import com.w2018.procmanager.core.rule.KillCandidate
import com.w2018.procmanager.data.BootReceiverInfo
import com.w2018.procmanager.data.model.AppInfo
import com.w2018.procmanager.data.SettingsStore
import com.w2018.procmanager.data.model.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** 设置快照（不可变，变更即产生新对象驱动重组）。 */
data class Cfg(
    val guardian: Boolean,
    val watchdog: Boolean,
    val autoStart: Boolean,
    val notify: Boolean,
    val notifyKillList: Boolean = true,
    val aliveStop: Boolean,
    val bgStop: Boolean,
    val wakeBlock: Boolean,
    val freqProtect: Boolean,
    val lockLowPower: Boolean,
    val screenOffSave: Boolean,
    val chargeExempt: Boolean,
    val nightMode: Boolean,
    val mem: Int,
    val bgTime: Int,
    val cpu: Int,
    val cooldown: Int,
    val scan: Int,
    val adbFreq: Int,
    val preset: String,
    val monitorMode: String,
    val memStop: Boolean = true,
    val cpuStop: Boolean = true,
    val cacheClean: Boolean = false,
    val cacheIdle: Int = 10,
    val memPressureRule: Boolean = false,
    val memPressurePct: Int = 15,
    val boostRule: Boolean = false,
    val boostBattery: Int = 20,
    val boostTemp: Int = 45,
    val buffer: Int = 3,
) {
    companion object {
        fun from(s: SettingsStore) = Cfg(
            s.guardian, s.watchdog, s.autoStart, s.notify, s.notifyKillList,
            s.aliveStop, s.bgStop, s.wakeBlock, s.freqProtect,
            s.lockLowPower, s.screenOffSave, s.chargeExempt, s.nightMode,
            s.memThreshold, s.bgMinutes, s.cpuThreshold, s.cooldownSec,
            s.scanInterval, s.adbFreqLimit, s.preset, s.monitorMode,
            s.memStop, s.cpuStop, s.cacheClean, s.cacheIdleMin,
            s.memPressureRule, s.memPressurePct, s.boostRule, s.boostBatteryPct, s.boostTempC,
            s.bufferMin,
        )

        fun write(s: SettingsStore, c: Cfg) {
            s.guardian = c.guardian; s.watchdog = c.watchdog
            s.autoStart = c.autoStart; s.notify = c.notify; s.notifyKillList = c.notifyKillList
            s.aliveStop = c.aliveStop; s.bgStop = c.bgStop
            s.wakeBlock = c.wakeBlock; s.freqProtect = c.freqProtect
            s.lockLowPower = c.lockLowPower; s.screenOffSave = c.screenOffSave
            s.chargeExempt = c.chargeExempt; s.nightMode = c.nightMode
            s.memThreshold = c.mem; s.bgMinutes = c.bgTime
            s.cpuThreshold = c.cpu; s.cooldownSec = c.cooldown
            s.scanInterval = c.scan; s.adbFreqLimit = c.adbFreq
            s.preset = c.preset; s.monitorMode = c.monitorMode
            s.memStop = c.memStop; s.cpuStop = c.cpuStop
            s.cacheClean = c.cacheClean; s.cacheIdleMin = c.cacheIdle
            s.memPressureRule = c.memPressureRule; s.memPressurePct = c.memPressurePct
            s.boostRule = c.boostRule; s.boostBatteryPct = c.boostBattery; s.boostTempC = c.boostTemp
            s.bufferMin = c.buffer
        }
    }
}

/** 全局 UI 状态与动作。 */
class AppUi(val app: ProcApp) {
    var cfg by mutableStateOf(Cfg.from(app.settings)); private set
    var snapshot by mutableStateOf<Snapshot?>(null); private set

    // ---- 权限通道 ----
    var channelLabel by mutableStateOf("未连接"); private set
    var channelDetail by mutableStateOf("尚未连接权限通道"); private set
    var channelReady by mutableStateOf(false); private set
    var channelType by mutableStateOf("none"); private set
    var shizukuText by mutableStateOf(""); private set
    var scanning by mutableStateOf(false); private set
    var scanPorts by mutableStateOf<List<Int>>(emptyList()); private set
    var adbTarget by mutableStateOf(
        runCatching {
            val h = app.settings.adbHost.ifBlank { "127.0.0.1" }
            "$h:${app.settings.adbPort}"
        }.getOrDefault("127.0.0.1:5555")
    )

    var monitored by mutableStateOf(app.settings.monitored); private set
    var whitelist by mutableStateOf(app.settings.whitelist); private set
    var bgStopSet by mutableStateOf(app.settings.bgStopPkgs); private set
    var frozen by mutableStateOf<Set<String>>(app.settings.frozenPkgs); private set
    /** 被系统「停用」的应用（applicationInfo.enabled=false），用于冻结管理展示与启用。 */
    var disabledApps by mutableStateOf<List<com.w2018.procmanager.data.model.AppInfo>>(emptyList()); private set
    val disabledSet: Set<String> get() = disabledApps.map { it.packageName }.toSet()
    /** 开机自启广播接收器（仅用于列出「自启动治理」候选应用）。 */
    var bootReceivers by mutableStateOf<List<BootReceiverInfo>>(emptyList()); private set
    /** 已被 AppOps 限制后台运行（禁止自启）的包名集合。 */
    var bgRestricted by mutableStateOf<Set<String>>(emptySet()); private set
    var logs by mutableStateOf<List<LogEvent>>(emptyList()); private set
    var blocked by mutableStateOf(0); private set
    var freedKb by mutableStateOf(0L); private set
    /** 最近一次清理释放的内存（非累计）。 */
    var lastFreedKb by mutableStateOf(0L); private set
    /** 每个应用累计被清理次数（pkg -> 次数），用于排行榜。 */
    var killCounts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var refreshing by mutableStateOf(false); private set
    var a11y by mutableStateOf(false); private set
    /** 内存使用率历史（近 12 次刷新），用于趋势图真实数据。 */
    var memHistory by mutableStateOf<List<Float>>(emptyList()); private set
    /** 一次性提示消息（由 AppRoot 转成自绘 Toast）。 */
    var message by mutableStateOf<String?>(null)
    /** 单应用规则变更版本号，驱动详情页重组。 */
    var ruleRev by mutableStateOf(0); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener { _, _ ->
                shizukuText = runCatching { app.channels.shizukuStatusText() }.getOrDefault("")
                autoConnect()
            }
        }
    }

    fun reloadStats() {
        blocked = app.stats.blocked; freedKb = app.stats.freedKb; lastFreedKb = app.stats.lastFreedKb
        killCounts = app.stats.killCounts()
    }

    suspend fun refresh(lite: Boolean = false) {
        refreshing = true
        try {
            snapshot = app.repo.snapshot(lite = lite)
            frozen = app.settings.frozenPkgs
            if (!lite) disabledApps = runCatching { app.repo.disabledApps() }.getOrDefault(emptyList())
        } catch (_: Throwable) {}
        shizukuText = runCatching { app.channels.shizukuStatusText() }.getOrDefault("")
        monitored = app.settings.monitored
        whitelist = app.settings.whitelist
        bgStopSet = app.settings.bgStopPkgs
        reloadStats()
        val pct = snapshot?.mem?.percent ?: 0f
        if (pct > 0f) {
            memHistory = (memHistory + pct).takeLast(12)
        }
        refreshing = false
    }

    /** 应用启动时调用：Shizuku 已安装未授权则申请；已授权则直接连上。 */
    private var askedShizuku = false
    fun ensureShizuku() = scope.launch {
        val ch = app.channels
        runCatching {
            android.util.Log.i("ProcKiller",
                "ensureShizuku installed=${ch.shizukuInstalled()} auth=${ch.shizukuAuthorized()} ready=${ch.shizukuReady()}")
            when {
                ch.shizukuReady() -> connectShizuku()
                ch.shizukuInstalled() && !ch.shizukuAuthorized() && !askedShizuku -> {
                    askedShizuku = true
                    android.util.Log.i("ProcKiller", "request Shizuku permission")
                    ch.requestShizukuPermission(0)
                }
            }
        }
    }

    fun getPerApp(pkg: String): SettingsStore.PerAppCfg {
        ruleRev // 读取以建立依赖
        return app.settings.getPerApp(pkg)
    }

    fun setPerApp(pkg: String, c: SettingsStore.PerAppCfg) {
        app.settings.setPerApp(pkg, c)
        ruleRev++
    }

    fun setCfg(t: (Cfg) -> Cfg) {
        val base = t(cfg)
        // 规则参数变化后自动识别预设：完全匹配某个预设则归属该预设，否则标记为「自定义」
        val n = base.copy(preset = detectPreset(base))
        cfg = n; Cfg.write(app.settings, n)
    }

    /** 判断当前规则参数是否与某个预设完全一致；都不匹配返回 "custom"。 */
    private fun detectPreset(c: Cfg): String = when {
        c.mem == 250 && c.bgTime == 10 && c.cpu == 25 && c.cooldown == 30 && c.buffer == 5 &&
            c.memStop && !c.cpuStop && !c.cacheClean && c.cacheIdle == 15 &&
            !c.memPressureRule && !c.boostRule -> "save"
        c.mem == 180 && c.bgTime == 6 && c.cpu == 18 && c.cooldown == 30 && c.buffer == 3 &&
            c.memStop && c.cpuStop && c.cacheClean && c.cacheIdle == 10 &&
            c.memPressureRule && c.memPressurePct == 15 && c.boostRule && c.boostBattery == 20 && c.boostTemp == 45 -> "balance"
        c.mem == 120 && c.bgTime == 3 && c.cpu == 12 && c.cooldown == 15 && c.buffer == 1 &&
            c.memStop && c.cpuStop && c.cacheClean && c.cacheIdle == 5 &&
            c.memPressureRule && c.memPressurePct == 20 && c.boostRule && c.boostBattery == 25 && c.boostTemp == 42 -> "perf"
        else -> "custom"
    }

    fun setScan(sec: Int) = setCfg { it.copy(scan = sec) }

    fun setMonitorMode(mode: String) = setCfg { it.copy(monitorMode = mode) }

    fun setGuardian(v: Boolean) {
        setCfg { it.copy(guardian = v, watchdog = v) }
        if (v) {
            com.w2018.procmanager.service.GuardService.start(app)
            com.w2018.procmanager.core.GuardRuntime.requestKick?.invoke()
        } else {
            com.w2018.procmanager.service.GuardService.stop(app)
        }
    }

    fun applyPreset(p: String) = setCfg {
        when (p) {
            // 省电：阈值更宽松、少打扰、低频清理，关闭激进的缓存放行规则
            "save" -> it.copy(
                preset = "save", mem = 250, bgTime = 10, cpu = 25, cooldown = 30, buffer = 5,
                memStop = true, cpuStop = false, cacheClean = false, cacheIdle = 15,
                memPressureRule = false, memPressurePct = 15, boostRule = false, boostBattery = 20, boostTemp = 45,
            )
            // 性能：阈值更紧、更积极清理保流畅与降温，开启缓存/内存压力/低电高温加速清理
            "perf" -> it.copy(
                preset = "perf", mem = 120, bgTime = 3, cpu = 12, cooldown = 15, buffer = 1,
                memStop = true, cpuStop = true, cacheClean = true, cacheIdle = 5,
                memPressureRule = true, memPressurePct = 20, boostRule = true, boostBattery = 25, boostTemp = 42,
            )
            // 均衡：默认推荐，各规则适度开启
            else -> it.copy(
                preset = "balance", mem = 180, bgTime = 6, cpu = 18, cooldown = 30, buffer = 3,
                memStop = true, cpuStop = true, cacheClean = true, cacheIdle = 10,
                memPressureRule = true, memPressurePct = 15, boostRule = true, boostBattery = 20, boostTemp = 45,
            )
        }
    }

    fun resetRules() = setCfg {
        it.copy(
            mem = 180, bgTime = 6, cpu = 18, cooldown = 30, buffer = 3, preset = "balance",
            memStop = true, cpuStop = true, cacheClean = true, cacheIdle = 10,
            memPressureRule = true, memPressurePct = 15, boostRule = true, boostBattery = 20, boostTemp = 45,
        )
    }

    fun toggleMonitor(pkg: String) {
        app.settings.toggleMonitored(pkg)
        monitored = app.settings.monitored
    }

    fun toggleWhite(pkg: String) {
        app.settings.toggleWhitelist(pkg)
        whitelist = app.settings.whitelist
    }

    fun toggleBgStop(pkg: String) {
        app.settings.toggleBgStop(pkg)
        bgStopSet = app.settings.bgStopPkgs
    }

    fun isBgStop(pkg: String) = bgStopSet.contains(pkg)

    fun isFrozen(pkg: String) = frozen.contains(pkg)

    suspend fun cleanNow(): String {
        if (!channelReady) return "未连接权限通道，请先在设置中连接"
        val (n, freed) = app.engine.cleanNow()
        reloadStats()
        return if (n == 0) "当前没有可清理的后台应用" else "已清理 $n 个后台应用，释放 ${freed / 1024} MB"
    }

    /** 立即扫描：执行一遍规则，返回可清理候选（不直接清理）。 */
    suspend fun scanCandidates(): List<KillCandidate> {
        if (!channelReady) return emptyList()
        return app.engine.previewCandidates()
    }

    /** 扫描过程动画用的准备数据（目标清单 + 命中项）。 */
    suspend fun prepareScan(): ScanPrep? {
        if (!channelReady) return null
        return runCatching { app.engine.prepareScan() }.getOrNull()
    }

    /** 清理扫描结果中选中的候选。 */
    suspend fun cleanCandidates(list: List<KillCandidate>): String {
        if (!channelReady) return "未连接权限通道，请先在设置中连接"
        val (n, freed) = app.engine.killCandidates(list)
        reloadStats()
        return if (n == 0) "未清理任何应用" else "已清理 $n 个应用，释放 ${freed / 1024} MB"
    }

    /** 已冻结应用列表。 */
    fun frozenApps(): List<AppInfo> =
        (snapshot?.apps ?: emptyList()).filter { frozen.contains(it.packageName) }

    /** 刷新开机自启接收器列表与后台限制状态。 */
    fun refreshBoot() = scope.launch {
        bootReceivers = runCatching { app.repo.bootReceivers() }.getOrDefault(emptyList())
        bgRestricted = runCatching { app.repo.bgRestrictedPkgs() }.getOrDefault(emptySet())
    }

    /** 该应用是否已禁止开机自启（其后台运行已被 AppOps 限制）。 */
    fun isAutoStartBlocked(pkg: String): Boolean = bgRestricted.contains(pkg)

    /** 禁止/恢复开机自启：通过 AppOps 限制/恢复该应用后台运行（不冻结、不改组件）。 */
    suspend fun setAutoStartBlocked(pkg: String, blocked: Boolean): Boolean {
        if (!channelReady) return false
        val ok = app.repo.setBgRestricted(pkg, blocked)
        refreshBoot()
        return ok
    }

    suspend fun killApp(pkg: String, label: String): Boolean {
        if (!channelReady) return false
        val r = app.channels.run("am force-stop $pkg")
        return if (r.ok) {
            EventLog.add(com.w2018.procmanager.core.LogType.KILL, "$label 已被终止", "手动强制终止 · am force-stop")
            reloadStats(); true
        } else false
    }

    suspend fun freezeApp(pkg: String, freeze: Boolean): Boolean {
        if (!channelReady) return false
        val r = app.channels.run(if (freeze) "pm disable-user --user 0 $pkg" else "pm enable $pkg")
        val ok = r.ok
        if (ok) {
            app.settings.setFrozen(pkg, freeze)
            frozen = app.settings.frozenPkgs
            disabledApps = runCatching { app.repo.disabledApps() }.getOrDefault(emptyList())
            EventLog.add(com.w2018.procmanager.core.LogType.RULE, (if (freeze) "冻结 " else "恢复 ") + pkg,
                if (freeze) "pm disable-user" else "pm enable")
        }
        return ok
    }

    // ---- 权限通道动作 ----
    private fun apply(st: com.w2018.procmanager.data.ChannelState?) {
        if (st != null) {
            channelReady = st.ready
            channelLabel = st.label
            channelDetail = st.detail
            channelType = st.type.name.lowercase()
        }
    }

    fun autoConnect() = scope.launch {
        channelLabel = "正在连接…"; channelReady = false
        channelDetail = "优先 Shizuku，其次本机无线 ADB"
        android.util.Log.i("ProcKiller", "autoConnect start shizukuInstalled=${app.channels.shizukuInstalled()} auth=${app.channels.shizukuAuthorized()} ready=${app.channels.shizukuReady()}")
        val st = runCatching { app.channels.autoConnect() }.getOrNull()
        apply(st)
        android.util.Log.i("ProcKiller", "autoConnect result type=${st?.type} ready=${st?.ready} detail=${st?.detail}")
        shizukuText = runCatching { app.channels.shizukuStatusText() }.getOrDefault("")
        message = st?.detail ?: "连接失败"
        if (channelReady) runCatching { refresh() }
    }

    fun connectShizuku() = scope.launch {
        if (!app.channels.shizukuAuthorized()) {
            runCatching { app.channels.requestShizukuPermission(1001) }
            message = "已请求 Shizuku 授权，请在弹窗中允许"
            return@launch
        }
        channelLabel = "正在连接…"; channelReady = false
        val st = runCatching { app.channels.connectShizukuOnly() }.getOrNull()
        apply(st)
        android.util.Log.i("ProcKiller", "connectShizuku result ready=${st?.ready} detail=${st?.detail}")
        message = st?.detail ?: "Shizuku 连接失败"
        if (channelReady) runCatching { refresh() }
    }

    fun reconnect() = autoConnect()

    fun scanAdb() = scope.launch {
        scanning = true
        channelDetail = "正在扫描本机 ADB 端口…"
        scanPorts = runCatching { app.channels.scanAdbPorts() }.getOrDefault(emptyList())
        channelDetail = if (scanPorts.isEmpty()) "未发现可连接的 ADB 端口（请先开启无线调试）"
        else "发现可用端口：${scanPorts.joinToString()}"
        message = channelDetail
        scanning = false
    }

    fun connectAdb(host: String, port: Int) = scope.launch {
        channelLabel = "正在连接 $host:$port"; channelReady = false
        channelDetail = "正在与 $host:$port 建立 ADB 连接…"
        val st = runCatching { app.channels.connectAdbOnly(host, port) }.getOrNull()
        apply(st)
        android.util.Log.i("ProcKiller", "connectAdb $host:$port ready=${st?.ready} detail=${st?.detail} lastError=${app.channels.adb.lastError}")
        message = st?.detail ?: "连接失败"
        if (channelReady) { adbTarget = "$host:$port"; runCatching { refresh() } }
    }

    fun connectAdbTarget() {
        val t = adbTarget.trim()
        val host = t.substringBefore(':').ifBlank { "127.0.0.1" }
        val port = t.substringAfter(':', "5555").toIntOrNull() ?: 5555
        connectAdb(host, port)
    }

    fun requestShizuku() {
        runCatching { app.channels.requestShizukuPermission(1001) }
        shizukuText = runCatching { app.channels.shizukuStatusText() }.getOrDefault("")
        message = "已请求 Shizuku 授权，请在弹窗中允许"
    }

    fun observe(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            app.channels.state.collect {
                channelLabel = it.label; channelDetail = it.detail
                channelReady = it.ready; channelType = it.type.name.lowercase()
            }
        }
        scope.launch { EventLog.events.collect { logs = it } }
        scope.launch { EventHub.accessibilityConnected.collect { a11y = it } }
    }
}
