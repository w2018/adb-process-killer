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
    override fun enabled(s: SettingsStore) = s.aliveStop
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (p.isForeground) continue
            val limitKb = ctx.memLimitKb(p.packageName)
            if (p.rssKb >= limitKb && p.rssKb > 0) {
                out.add(KillCandidate(p.packageName, p.pid, "内存超限", p.rssKb))
            }
        }
        return out
    }
}

/** CPU 超限：后台进程 CPU 占用超过阈值。 */
class CpuRule : Rule {
    override val id = "cpu"
    override val name = "CPU超限"
    override fun enabled(s: SettingsStore) = s.aliveStop
    override fun evaluate(ctx: RuleContext): List<KillCandidate> {
        val out = ArrayList<KillCandidate>()
        for (p in ctx.appProcesses()) {
            if (p.packageName == ctx.foregroundPkg) continue
            if (ctx.isProtected(p.packageName)) continue
            if (p.isForeground) continue
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
                out.add(KillCandidate(p.packageName, p.pid, "关联唤醒", p.rssKb))
            }
        }
        return out
    }
}
