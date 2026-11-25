package com.w2018.procmanager.core.rule

import com.w2018.procmanager.data.SettingsStore

/** 单应用「后台即停」：用户在应用详情开启后，该应用切入后台即刻终止。 */
class PerAppRule : Rule {
    override val id = "per_app"
    override val name = "单应用后台即停"
    override fun enabled(s: SettingsStore) = s.bgStopPkgs.isNotEmpty()
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val out = ArrayList<KillCandidate>()
        for (p in ctx.allUserProcesses()) {
            if (!ctx.isBgStopForced(p.packageName)) continue
            if (p.packageName == ctx.foregroundPkg) continue
            if (p.isForeground) continue
            if (ctx.isProtected(p.packageName)) continue
            out.add(KillCandidate(p.packageName, p.pid, "单应用后台即停", p.rssKb))
        }
        return out
    }
}

/**
 * 单应用独立规则（阈值版）：对已启用「单应用独立规则」的应用，按各自的内存/CPU/后台时长阈值清理，
 * 并受该应用缓冲时间约束（回到前台即刷新）。启用后该应用不再套用全局内存/CPU/后台超时规则。
 */
class PerAppThresholdRule : Rule {
    override val id = "per_app_threshold"
    override val name = "单应用独立规则"
    override fun enabled(s: SettingsStore) = true
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val out = ArrayList<KillCandidate>()
        for (p in ctx.allUserProcesses()) {
            val cfg = ctx.perApp(p.packageName)
            if (!cfg.enabled) continue
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (p.isForeground) continue
            val idle = ctx.idleMs(p.packageName)
            val buf = ctx.bufMs(p.packageName)
            val used = ctx.pssOrRss(p)
            val reason = when {
                cfg.memMb > 0 && used >= cfg.memMb.toLong() * 1024 && idle >= buf -> "单应用·内存超限"
                cfg.cpu > 0 && p.cpuPercent >= cfg.cpu && idle >= buf -> "单应用·CPU超限"
                cfg.bgMin > 0 && idle >= cfg.bgMin.toLong() * 60_000 -> "单应用·后台超时"
                else -> null
            }
            if (reason != null) out.add(KillCandidate(p.packageName, p.pid, reason, used))
        }
        return out
    }
}

/** 后台超时：后台停留超过阈值分钟数则清理。 */
class BackgroundTimeRule : Rule {
    override val id = "bg_time"
    override val name = "后台超时"
    override fun enabled(s: SettingsStore) = s.bgStop
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (ctx.perAppOverrides(p.packageName)) continue // 单应用独立规则优先，全局规则不介入
            if (p.isForeground) continue
            val minMs = ctx.bgMinMs(p.packageName)
            if (minMs <= 0) continue
            val since = ctx.bgSince[p.packageName] ?: continue
            if (ctx.now - since >= minMs) {
                out.add(KillCandidate(p.packageName, p.pid, "后台超时", p.rssKb))
            }
        }
        return out
    }
}

/** 内存超限：单进程常驻内存超过阈值(MB)且处于后台。 */
class MemoryRule : Rule {
    override val id = "mem"
    override val name = "内存超限"
    override fun enabled(s: SettingsStore) = s.memStop
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (ctx.perAppOverrides(p.packageName)) continue // 单应用独立规则优先
            if (p.isForeground) continue
            if (ctx.idleMs(p.packageName) < ctx.bufMs(p.packageName)) continue // 后台缓冲时间未到，避免误杀临时切后台
            val used = ctx.pssOrRss(p)
            val limitKb = ctx.memLimitKb(p.packageName)
            if (used >= limitKb && used > 0) {
                out.add(KillCandidate(p.packageName, p.pid, "内存超限", used))
            }
        }
        return out
    }
}

/** CPU 超限：后台进程 CPU 占用超过阈值。 */
class CpuRule : Rule {
    override val id = "cpu"
    override val name = "CPU超限"
    override fun enabled(s: SettingsStore) = s.cpuStop
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (ctx.perAppOverrides(p.packageName)) continue // 单应用独立规则优先
            if (p.isForeground) continue
            if (ctx.idleMs(p.packageName) < ctx.bufMs(p.packageName)) continue // 后台缓冲时间未到
            val limit = ctx.cpuLimit(p.packageName)
            if (p.cpuPercent >= limit && p.cpuPercent > 0f) {
                out.add(KillCandidate(p.packageName, p.pid, "CPU超限", p.rssKb))
            }
        }
        return out
    }
}

/** 关联唤醒拦截（检测+立即终止近似）：刚被清理又快速出现，判定为被唤醒。 */
class WakeBlockRule : Rule {
    override val id = "wake"
    override val name = "关联唤醒"
    // 仅由「关联唤醒拦截」开关控制；「常用应用保护」单独在 RuleContext 里作为保护生效。
    override fun enabled(s: SettingsStore) = s.wakeBlock
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val windowMs = 120_000L
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            val last = ctx.lastKilled[p.packageName] ?: continue
            if (ctx.now - last in 1..windowMs) {
                out.add(KillCandidate(p.packageName, p.pid, "关联唤醒", ctx.pssOrRss(p)))
            }
        }
        return out
    }
}

/** 缓存/空进程闲置清理：oom 处于缓存档且后台闲置超过阈值的进程，温和回收。 */
class CacheIdleRule : Rule {
    override val id = "cache"
    override val name = "缓存闲置清理"
    override fun enabled(s: SettingsStore) = s.cacheClean
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val idleMs = ctx.settings.cacheIdleMin * 60_000L
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (p.isForeground) continue
            if (p.oomCur < 700) continue // 仅缓存档及更空
            if (ctx.idleMs(p.packageName) < idleMs) continue
            out.add(KillCandidate(p.packageName, p.pid, "缓存闲置", ctx.pssOrRss(p)))
        }
        return out
    }
}

/** 内存压力触发：仅当系统可用内存低于阈值时，按占用从高到低清理较重的后台应用。 */
class MemPressureRule : Rule {
    override val id = "mem_pressure"
    override val name = "内存压力清理"
    override fun enabled(s: SettingsStore) = s.memPressureRule
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        if (!ctx.memPressure) return emptyList()
        return ctx.appProcesses()
            .filter { it.packageName != ctx.foregroundPkg && !ctx.isProtected(it.packageName) && !it.isForeground }
            .filter { ctx.pssOrRss(it) >= 30L * 1024 }
            .sortedByDescending { ctx.pssOrRss(it) }
            .take(8)
            .map { KillCandidate(it.packageName, it.pid, "内存压力", ctx.pssOrRss(it)) }
    }
}

/** 低电量 / 高温触发：电量过低或电池温度过高时，回收闲置较久的后台应用以降温省电。 */
class LowBatteryRule : Rule {
    override val id = "boost"
    override val name = "低电量高温"
    override fun enabled(s: SettingsStore) = s.boostRule
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        if (!(ctx.batteryLow || ctx.tempHigh)) return emptyList()
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (p.isForeground) continue
            if (ctx.idleMs(p.packageName) < 5 * 60_000L) continue
            out.add(KillCandidate(p.packageName, p.pid, "低电量/高温", ctx.pssOrRss(p)))
        }
        return out
    }
}
