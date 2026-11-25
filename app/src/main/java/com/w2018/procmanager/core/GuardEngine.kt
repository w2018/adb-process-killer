package com.w2018.procmanager.core

import android.content.Intent
import android.content.IntentFilter
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import com.w2018.procmanager.ProcApp
import com.w2018.procmanager.core.rule.BackgroundTimeRule
import com.w2018.procmanager.core.rule.CacheIdleRule
import com.w2018.procmanager.core.rule.CpuRule
import com.w2018.procmanager.core.rule.KillCandidate
import com.w2018.procmanager.core.rule.LowBatteryRule
import com.w2018.procmanager.core.rule.MemPressureRule
import com.w2018.procmanager.core.rule.MemoryRule
import com.w2018.procmanager.core.rule.PerAppRule
import com.w2018.procmanager.core.rule.PerAppThresholdRule
import com.w2018.procmanager.core.rule.Rule
import com.w2018.procmanager.core.rule.RuleContext
import com.w2018.procmanager.core.rule.WakeBlockRule
import com.w2018.procmanager.data.model.MemInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 扫描过程中的一个扫描目标（应用）。 */
data class ScanTarget(
    val pkg: String,
    val label: String,
    val memKb: Long,
    val isSystem: Boolean,
)

/** 扫描准备数据：逐个扫描的目标 + 规则命中的可清理候选。 */
data class ScanPrep(
    val targets: List<ScanTarget>,
    val hits: List<KillCandidate>,
)

/**
 * 守护引擎：事件驱动 + 轮询兜底 + 省电策略。
 * - 事件驱动：无障碍上报前台切换时立即触发一次扫描。
 * - 轮询兜底：按 scanInterval 秒（非精确）扫描。
 * - 省电：仅扫描瞬间持短时 WakeLock 并超时释放；锁屏/非充电暂停；每包冷却；批量合并命令。
 */
class GuardEngine(private val app: ProcApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val kick = Channel<Unit>(Channel.CONFLATED)

    private val bgSince = HashMap<String, Long>()
    private var lastFg: String? = null
    private val lastKilled = HashMap<String, Long>()
    private val rules: List<Rule> = listOf(
        PerAppRule(), PerAppThresholdRule(), BackgroundTimeRule(), MemoryRule(), CpuRule(), WakeBlockRule(),
        CacheIdleRule(), MemPressureRule(), LowBatteryRule()
    )
    private val scanTimes = ArrayDeque<Long>()
    /** 最近使用（前台出现过）的应用及时间戳，用于「常用应用保护」。 */
    private val recentFg = HashMap<String, Long>()

    private var protected: Set<String> = emptySet()
    private var lastSkipLog = 0L
    private var lastEventLog = 0L

    fun start() {
        if (job != null) return
        protected = computeProtected()
        GuardRuntime.requestClean = { cleanNow() }
        GuardRuntime.requestKick = { scope.launch { kick.trySend(Unit) } }
        // 事件驱动：前台应用变化即触发扫描，并用事件精确维护「后台起始时间」（供缓冲时间规则）
        scope.launch {
            EventHub.foregroundPackage.collect { pkg ->
                if (!pkg.isNullOrBlank()) {
                    val now = System.currentTimeMillis()
                    val prev = lastFg
                    // 上一个前台应用此刻进入后台 → 记录其后台起始时间；回到前台的应用重置计时
                    if (prev != null && prev != pkg) bgSince.putIfAbsent(prev, now)
                    bgSince.remove(pkg)
                    lastFg = pkg
                    recentFg[pkg] = now
                    kick.trySend(Unit)
                }
            }
        }
        // 监控方式互斥：无障碍可用→事件驱动(低功耗)；被关闭/未开启→自动降级轮询(兜底)
        GuardRuntime.monitorMode.value =
            if (EventHub.accessibilityConnected.value) GuardRuntime.MonitorMode.EVENT else GuardRuntime.MonitorMode.POLL
        scope.launch {
            EventHub.accessibilityConnected.collect { on ->
                GuardRuntime.monitorMode.value = if (on) GuardRuntime.MonitorMode.EVENT else GuardRuntime.MonitorMode.POLL
                if (on) kick.trySend(Unit)
                EventLog.add(LogType.SYSTEM,
                    if (on) "监控方式：无障碍事件（低功耗）" else "监控方式：轮询（兜底）")
            }
        }
        job = scope.launch { loop() }
        EventLog.add(LogType.SYSTEM, "守护引擎已就绪")
    }

    fun stop() {
        job?.cancel()
        job = null
        GuardRuntime.requestClean = null
        GuardRuntime.requestKick = null
    }

    private fun computeProtected(): Set<String> {
        val s = HashSet<String>()
        s.add(app.packageName)
        s.addAll(
            listOf(
                "android", "com.android.systemui", "com.android.settings",
                "com.android.phone", "com.android.providers.settings",
                "com.android.networkstack.process"
            )
        )
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = app.packageManager.resolveActivity(home, 0)
            val homePkg: String? = resolved?.activityInfo?.packageName
            if (homePkg != null) s.add(homePkg)
        }
        runCatching {
            val imm = app.getSystemService(InputMethodManager::class.java)
            val imePkg: String? = imm?.enabledInputMethodList?.firstOrNull()?.packageName
            if (imePkg != null) s.add(imePkg)
        }
        return s
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            // 事件驱动模式下放宽兜底轮询间隔（事件已即时触发，轮询仅作安全网）以省电；轮询模式按设定间隔
            val base = app.settings.scanInterval.coerceIn(15, 120)
            val interval = if (GuardRuntime.monitorMode.value == GuardRuntime.MonitorMode.EVENT) maxOf(base, 60) else base
            val ev = withTimeoutOrNull(interval * 1000L) { kick.receive() }
            if (!app.settings.guardian) continue
            if (!app.settings.watchdog) continue
            if (GuardRuntime.paused.value) continue
            if (!shouldScan()) continue
            if (!allowByFreq()) continue
            runCatching { scanOnce(ev != null) }
        }
    }

    private fun isCharging(): Boolean = runCatching {
        val bm = app.getSystemService(BatteryManager::class.java)
        bm?.isCharging == true
    }.getOrDefault(false)

    private fun shouldScan(): Boolean {
        val pm = app.getSystemService(PowerManager::class.java) ?: return true
        val screenOn = pm.isInteractive
        val charging = isCharging()
        // 夜间严格模式：23:00–07:00 更积极，忽略锁屏降频，持续清理后台
        if (app.settings.nightMode && isNight()) return true
        if (!screenOn && app.settings.screenOffSave && !(charging && app.settings.chargeExempt)) {
            return false
        }
        if (app.settings.lockLowPower && !screenOn && !charging) {
            return false
        }
        return true
    }

    private fun isNight(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 23 || h < 7
    }

    /** ADB 调用频率上限（次/分钟）节流，避免高频调用耗电。 */
    private fun allowByFreq(): Boolean {
        val now = System.currentTimeMillis()
        while (scanTimes.isNotEmpty() && now - scanTimes.first() > 60_000L) scanTimes.removeFirst()
        val limit = app.settings.adbFreqLimit.coerceIn(1, 120)
        if (scanTimes.size >= limit) return false
        scanTimes.addLast(now)
        return true
    }

    /** 系统可用内存是否低于「内存压力阈值」（仅当开启内存压力规则时返回 true）。 */
    private fun memPressureOf(mem: MemInfo): Boolean {
        if (!app.settings.memPressureRule) return false
        val availPct = if (mem.totalKb > 0) mem.availKb.toFloat() * 100f / mem.totalKb else 100f
        return availPct < app.settings.memPressurePct
    }

    /** 读取电池电量与温度，返回 (是否低电量, 是否高温)。 */
    private fun batteryState(): Pair<Boolean, Boolean> = runCatching {
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val tempC = temp / 10.0
        (pct <= app.settings.boostBatteryPct) to (tempC >= app.settings.boostTempC)
    }.getOrDefault(false to false)

    /** 「常用应用保护」：最近 10 分钟内使用过的应用集合（未开启则返回空）。 */
    private fun recentlyUsed(): Set<String> {
        if (!app.settings.freqProtect) return emptySet()
        val now = System.currentTimeMillis()
        return recentFg.filterValues { now - it < 10 * 60_000L }.keys.toHashSet()
    }

    private suspend fun scanOnce(eventDriven: Boolean) {
        val pm = app.getSystemService(PowerManager::class.java)
        val lock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "proc:scan")
        lock?.acquire(12_000L)
        try {
            val snap = app.repo.snapshot()
            app.stats.addScan()
            val now = System.currentTimeMillis()
            val fg = EventHub.foregroundPackage.value
            val procs = snap.processes.filter { it.user.startsWith("u0_") && it.packageName.isNotBlank() }
            val present = procs.map { it.packageName }.toHashSet()
            bgSince.keys.retainAll { it in present }
            for (p in procs) {
                if (p.isForeground || p.packageName == fg) bgSince.remove(p.packageName)
                else bgSince.putIfAbsent(p.packageName, now)
            }
            val recent = recentlyUsed()
            val (bLow, tHigh) = batteryState()
            val ctx = RuleContext(snap, app.settings, fg, bgSince, lastKilled, now, protected, app.settings.monitored,
                perApp = { app.settings.getPerApp(it) },
                monitorAll = app.settings.monitorMode == "all",
                freqProtected = recent,
                memPressure = memPressureOf(snap.mem),
                batteryLow = bLow, tempHigh = tHigh)
            val candidates = rules.filter { it.enabled(app.settings) }.flatMap { it.evaluate(ctx) }
            val killed = execute(candidates, fg, now, recent)
            if (killed == 0 && eventDriven && now - lastEventLog > 8_000L) {
                lastEventLog = now
                EventLog.add(LogType.SCAN, "事件触发扫描", "前台切换 · 待机省电")
            }
            if (killed == 0 && !eventDriven && now - lastSkipLog > 5 * 60_000L) {
                lastSkipLog = now
                EventLog.add(LogType.SCAN, "定时扫描", "后台无异常，跳过处理")
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    private suspend fun execute(candidates: List<KillCandidate>, fg: String?, now: Long, recent: Set<String> = emptySet()): Int {
        if (candidates.isEmpty()) return 0
        val chosen = LinkedHashMap<String, KillCandidate>()
        val cooldownMs = app.settings.cooldownSec * 1000L
        for (c in candidates) {
            if (c.pkg == fg) continue
            if (c.pkg in protected || app.settings.isWhitelisted(c.pkg) || c.pkg in recent) continue
            if (!autoKillable(c.pkg)) continue
            val lk = lastKilled[c.pkg]
            if (lk != null && now - lk < cooldownMs) continue
            chosen.putIfAbsent(c.pkg, c)
        }
        if (chosen.isEmpty()) return 0
        val cmd = chosen.keys.joinToString("; ") { "am force-stop $it" }
        val r = app.channels.run(cmd, 20_000)
        if (r.ok || r.exitCode == 0) {
            var freed = 0L
            for ((pkg, c) in chosen) {
                lastKilled[pkg] = now
                freed += c.freedKb
                EventLog.add(
                    LogType.KILL,
                    "已清理 ${label(pkg)}",
                    "${c.reason} · 约释放 ${c.freedKb / 1024}MB"
                )
            }
            app.stats.addBlocked(chosen.size, freed)
            if (app.settings.notify) notifyAction("已清理 ${chosen.size} 个后台应用", "约释放 ${freed / 1024}MB · 看门狗自动执行")
            return chosen.size
        }
        return 0
    }

    /**
     * 为“扫描过程动画”准备数据：返回本次需要逐个扫描的目标清单(targets)，
     * 以及规则命中的可清理候选(hits)。UI 侧按 targets 逐项播放扫描动画，
     * 扫描到命中项时即时把 hits 里的对应项加入结果列表。
     */
    suspend fun prepareScan(): ScanPrep {
        val snap = app.repo.snapshot(lite = true)
        val now = System.currentTimeMillis()
        val fg = EventHub.foregroundPackage.value
        val procs = snap.processes.filter { it.user.startsWith("u0_") && it.packageName.isNotBlank() }
        val present = procs.map { it.packageName }.toHashSet()
        bgSince.keys.retainAll { it in present }
        for (p in procs) {
            if (p.isForeground || p.packageName == fg) bgSince.remove(p.packageName)
            else bgSince.putIfAbsent(p.packageName, now)
        }
        val recent = recentlyUsed()
        val (bLow, tHigh) = batteryState()
        val ctx = RuleContext(
            snap, app.settings, fg, bgSince, lastKilled, now, protected, app.settings.monitored,
            perApp = { app.settings.getPerApp(it) },
            monitorAll = app.settings.monitorMode == "all",
            freqProtected = recent,
            memPressure = memPressureOf(snap.mem),
            batteryLow = bLow, tempHigh = tHigh
        )
        val candidates = rules.filter { it.enabled(app.settings) }.flatMap { it.evaluate(ctx) }
        val cooldownMs = app.settings.cooldownSec * 1000L
        val hits = candidates
            .filter { c ->
                c.pkg != fg && c.pkg !in protected && !app.settings.isWhitelisted(c.pkg) && c.pkg !in recent &&
                    autoKillable(c.pkg) &&
                    (lastKilled[c.pkg]?.let { now - it < cooldownMs } != true)
            }
            .distinctBy { it.pkg }
            .sortedByDescending { it.freedKb }
        // 每个包的内存（PSS 优先，回退 RSS）
        val memMap = HashMap<String, Long>()
        for (p in procs) memMap[p.packageName] = (memMap[p.packageName] ?: 0L) + (if (p.pssKb > 0) p.pssKb else p.rssKb)
        val targets = snap.apps
            .map { ScanTarget(it.packageName, it.label, memMap[it.packageName] ?: 0L, it.isSystem) }
            .distinctBy { it.pkg }
            .sortedByDescending { it.memKb }
        return ScanPrep(targets, hits)
    }

    /**
     * 立即扫描：执行一遍规则，不直接清理，返回“符合规则、可清理”的候选列表，供 UI 弹窗展示。
     */
    suspend fun previewCandidates(): List<KillCandidate> {
        val snap = app.repo.snapshot()
        val now = System.currentTimeMillis()
        val fg = EventHub.foregroundPackage.value
        val procs = snap.processes.filter { it.user.startsWith("u0_") && it.packageName.isNotBlank() }
        val present = procs.map { it.packageName }.toHashSet()
        bgSince.keys.retainAll { it in present }
        for (p in procs) {
            if (p.isForeground || p.packageName == fg) bgSince.remove(p.packageName)
            else bgSince.putIfAbsent(p.packageName, now)
        }
        val recent = recentlyUsed()
        val (bLow, tHigh) = batteryState()
        val ctx = RuleContext(
            snap, app.settings, fg, bgSince, lastKilled, now, protected, app.settings.monitored,
            perApp = { app.settings.getPerApp(it) },
            monitorAll = app.settings.monitorMode == "all",
            freqProtected = recent,
            memPressure = memPressureOf(snap.mem),
            batteryLow = bLow, tempHigh = tHigh
        )
        val candidates = rules.filter { it.enabled(app.settings) }.flatMap { it.evaluate(ctx) }
        val cooldownMs = app.settings.cooldownSec * 1000L
        return candidates
            .filter { c ->
                c.pkg != fg && c.pkg !in protected && !app.settings.isWhitelisted(c.pkg) && c.pkg !in recent &&
                    autoKillable(c.pkg) &&
                    (lastKilled[c.pkg]?.let { now - it < cooldownMs } != true)
            }
            .distinctBy { it.pkg }
            .sortedByDescending { it.freedKb }
    }

    /** 对指定候选逐项执行清理（来自“立即扫描”弹窗选择）。 */
    suspend fun killCandidates(list: List<KillCandidate>): Pair<Int, Long> {
        val chosen = list.distinctBy { it.pkg }
        if (chosen.isEmpty()) return 0 to 0L
        val now = System.currentTimeMillis()
        val pkgs = chosen.map { it.pkg }
        val r = app.channels.run(pkgs.joinToString("; ") { "am force-stop $it" }, 20_000)
        if (!(r.ok || r.exitCode == 0)) return 0 to 0L
        var freed = 0L
        for (c in chosen) {
            lastKilled[c.pkg] = now
            freed += c.freedKb
            EventLog.add(LogType.KILL, "已清理 ${label(c.pkg)}", "${c.reason} · 约释放 ${c.freedKb / 1024}MB")
        }
        app.stats.addBlocked(chosen.size, freed)
        return chosen.size to freed
    }

    /** 手动/一键清理：清空当前所有非前台、非白名单、非保护的后台应用。 */
    suspend fun cleanNow(): Pair<Int, Long> {
        val pm = app.getSystemService(PowerManager::class.java)
        val lock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "proc:clean")
        lock?.acquire(15_000L)
        try {
            val snap = app.repo.snapshot()
            val now = System.currentTimeMillis()
            val fg = EventHub.foregroundPackage.value
            val recent = recentlyUsed()
            val chosen = snap.processes.filter {
                it.user.startsWith("u0_") && it.packageName.isNotBlank() &&
                    !it.isForeground && it.packageName != fg &&
                    it.packageName !in protected && !app.settings.isWhitelisted(it.packageName) &&
                    it.packageName !in recent &&
                    autoKillable(it.packageName)
            }
            if (chosen.isEmpty()) return 0 to 0L
            val pkgs = chosen.map { it.packageName }.distinct()
            val r = app.channels.run(pkgs.joinToString("; ") { "am force-stop $it" }, 25_000)
            var freed = 0L
            val seen = HashSet<String>()
            for (p in chosen) {
                if (seen.add(p.packageName)) {
                    freed += p.rssKb
                    lastKilled[p.packageName] = now
                }
            }
            if (r.ok || r.exitCode == 0) {
                app.stats.addBlocked(seen.size, freed)
                EventLog.add(LogType.KILL, "一键清理", "清理 ${seen.size} 个进程，约释放 ${freed / 1024}MB")
            }
            return seen.size to freed
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    private fun label(pkg: String): String = app.repo.appMap()[pkg]?.label ?: pkg

    /**
     * 自动/批量清理的可执行判定：避免误杀系统关键进程。
     * - android.process.* 等系统 provider 进程一律不自动清理（会不断重启导致循环）。
     * - 系统应用默认不自动清理，除非用户显式加入监控。
     * - 未知进程（非已安装包）默认不自动清理，除非已监控。
     */
    private fun autoKillable(pkg: String): Boolean {
        if (pkg.startsWith("android.process")) return false
        val ai = app.repo.appMap()[pkg]
        val explicitlyMonitored = app.settings.monitored.contains(pkg)
        val perAppEnabled = app.settings.getPerApp(pkg).enabled
        // 系统应用必须显式加入监控才可处理；单应用规则不解除系统应用保护。
        if (ai != null && ai.isSystem) return explicitlyMonitored
        if (ai == null) return explicitlyMonitored || perAppEnabled
        // 用户应用：全量模式默认全管，或显式勾选 / 启用单应用规则。
        return app.settings.monitorMode == "all" || explicitlyMonitored || perAppEnabled
    }

    /** 执行动作后的轻量通知（受「状态通知」开关控制）。 */
    private fun notifyAction(title: String, text: String) {
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            val chId = "guard_events"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(chId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(chId, "清理提醒", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    }
                )
            }
            val n = NotificationCompat.Builder(app, chId)
                .setSmallIcon(com.w2018.procmanager.R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            nm.notify(2000 + (System.currentTimeMillis() % 1000).toInt(), n)
        }
    }
}
