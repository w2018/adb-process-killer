package com.w2018.procmanager.core.rule

import com.w2018.procmanager.data.SettingsStore
import com.w2018.procmanager.data.model.ProcInfo
import com.w2018.procmanager.data.model.Snapshot

/** 一次候选清理。 */
data class KillCandidate(
    val pkg: String,
    val pid: Int,
    val reason: String,
    val freedKb: Long
)

/** 规则评估上下文。 */
class RuleContext(
    val snapshot: Snapshot,
    val settings: SettingsStore,
    val foregroundPkg: String?,
    val bgSince: Map<String, Long>,
    val lastKilled: Map<String, Long>,
    val now: Long,
    val protectedPkgs: Set<String>,
    /** 用户勾选的监控名单（仅在 monitorAll=false 时生效）。 */
    val monitored: Set<String> = emptySet(),
    /** 单应用独立规则解析（0/未启用时回退全局）。 */
    val perApp: (String) -> SettingsStore.PerAppCfg = { SettingsStore.PerAppCfg() },
    /** 监控范围：true=全部用户应用（默认）；false=仅 monitored 列表。 */
    val monitorAll: Boolean = true,
    /** 「常用应用保护」命中的包（最近使用过），视为受保护不自动清理。 */
    val freqProtected: Set<String> = emptySet(),
    /** 系统可用内存是否处于压力状态（供内存压力规则使用）。 */
    val memPressure: Boolean = false,
    /** 是否低电量（供低电量/高温规则使用）。 */
    val batteryLow: Boolean = false,
    /** 是否电池高温。 */
    val tempHigh: Boolean = false,
    /** 全局后台缓冲时长(ms)：进入后台超过此时长才按内存/CPU 阈值清理。 */
    val bufferMs: Long = settings.bufferMin * 60_000L
) {
    /** 进程占用：优先 PSS，取不到回退 RSS。 */
    fun pssOrRss(p: ProcInfo): Long = if (p.pssKb > 0) p.pssKb else p.rssKb

    /** 某应用已在后台停留毫秒（无记录返回 0）。 */
    fun idleMs(pkg: String): Long = bgSince[pkg]?.let { (now - it).coerceAtLeast(0) } ?: 0L

    fun isProtected(pkg: String): Boolean =
        protectedPkgs.contains(pkg) || settings.isWhitelisted(pkg) || freqProtected.contains(pkg)

    fun isMonitored(pkg: String): Boolean = monitorAll || monitored.contains(pkg)

    fun isBgStopForced(pkg: String): Boolean = settings.bgStopPkgs.contains(pkg)

    fun perAppEnabled(pkg: String): Boolean = perApp(pkg).enabled

    /** 该应用是否启用「单应用独立规则」并因此覆盖全局规则（单应用规则＞全局规则）。 */
    fun perAppOverrides(pkg: String): Boolean = perApp(pkg).enabled

    /** 该应用适用的后台缓冲时长(ms)：单应用配置优先，否则用全局。 */
    fun bufMs(pkg: String): Long {
        val p = perApp(pkg)
        val m = if (p.enabled && p.bufMin > 0) p.bufMin else settings.bufferMin
        return m * 60_000L
    }

    fun isPerAppMonitored(pkg: String): Boolean = isMonitored(pkg) || perAppEnabled(pkg)

    /** 单应用内存上限 KB（未配置则用全局）。 */
    fun memLimitKb(pkg: String): Long {
        val p = perApp(pkg)
        val mb = if (p.enabled && p.memMb > 0) p.memMb else settings.memThreshold
        return mb * 1024L
    }

    /** 单应用后台超时毫秒（未配置则用全局）。 */
    fun bgMinMs(pkg: String): Long {
        val p = perApp(pkg)
        val m = if (p.enabled && p.bgMin > 0) p.bgMin else settings.bgMinutes
        return m * 60_000L
    }

    /** 单应用 CPU 上限百分比（未配置则用全局）。 */
    fun cpuLimit(pkg: String): Float {
        val p = perApp(pkg)
        return (if (p.enabled && p.cpu > 0) p.cpu else settings.cpuThreshold).toFloat()
    }

    /** 全部普通应用进程（uid 以 u0_ 开头），避免误杀系统核心进程。 */
    fun allUserProcesses(): List<ProcInfo> = snapshot.processes.filter {
        it.user.startsWith("u0_") && it.packageName.isNotBlank()
    }

    /** 受监控名单约束的普通应用进程。 */
    fun appProcesses(): List<ProcInfo> = allUserProcesses().filter { isPerAppMonitored(it.packageName) }
}

/** 可插拔规则。 */
interface Rule {
    val id: String
    val name: String
    fun enabled(s: SettingsStore): Boolean
    fun evaluate(ctx: RuleContext): List<KillCandidate>
}
