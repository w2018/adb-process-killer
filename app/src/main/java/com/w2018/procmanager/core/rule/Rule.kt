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
    /** 用户勾选的监控名单；为空时视为“全部用户应用”（开箱即用）。 */
    val monitored: Set<String> = emptySet(),
    /** 单应用独立规则解析（0/未启用时回退全局）。 */
    val perApp: (String) -> SettingsStore.PerAppCfg = { SettingsStore.PerAppCfg() }
) {
    fun isProtected(pkg: String): Boolean =
        protectedPkgs.contains(pkg) || settings.isWhitelisted(pkg)

    fun isMonitored(pkg: String): Boolean = monitored.isEmpty() || monitored.contains(pkg)

    fun isBgStopForced(pkg: String): Boolean = settings.bgStopPkgs.contains(pkg)

    fun perAppEnabled(pkg: String): Boolean = perApp(pkg).enabled

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
