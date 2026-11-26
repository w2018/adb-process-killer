package com.w2018.procmanager.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import com.w2018.procmanager.data.model.AppInfo
import com.w2018.procmanager.data.model.CpuInfo
import com.w2018.procmanager.data.model.MemInfo
import com.w2018.procmanager.data.model.ProcInfo
import com.w2018.procmanager.data.model.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用/进程数据仓库。通过 PackageManager 取应用元数据，通过 shell(ps/top/dumpsys/meminfo)
 * 取进程、CPU、内存。所有采集在 IO 线程执行。
 */
/** 一个开机自启广播接收器组件。 */
data class BootReceiverInfo(
    val pkg: String,
    /** 组件全名：pkg/className */
    val component: String,
    val enabled: Boolean
)

/** 进程杀手自身进程的资源占用。 */
data class SelfUsage(
    val memPssKb: Long,
    val cpuPercent: Float
)

class AppRepository(
    private val context: Context,
    private val channels: ChannelManager
) {
    private var appCache: List<AppInfo>? = null

    // ---------------- 应用列表（PackageManager，无需 shell） ----------------

    suspend fun apps(force: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val cached = appCache
        if (!force && cached != null) return@withContext cached
        val pm = context.packageManager
        val list = ArrayList<AppInfo>()
        val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        for (p in installed) {
            if (p.packageName == context.packageName) continue // 默认排除「进程杀手」自身
            val ai = p.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = runCatching { pm.getApplicationLabel(ai).toString() }
                .getOrDefault(p.packageName)
            val autoStart = runCatching {
                pm.queryBroadcastReceivers(
                    Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(p.packageName),
                    PackageManager.MATCH_DISABLED_COMPONENTS
                ).isNotEmpty()
            }.getOrDefault(false)
            list.add(AppInfo(p.packageName, label, isSystem, ai.uid, autoStart))
        }
        list.sortWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
        appCache = list
        list
    }

    fun appMap(): Map<String, AppInfo> =
        appCache?.associateBy { it.packageName } ?: emptyMap()

    /** 已被系统「停用」的应用（applicationInfo.enabled=false，含用户与系统应用）。用于冻结管理中展示与一键启用。 */
    suspend fun disabledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val out = ArrayList<AppInfo>()
        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrDefault(emptyList())
        for (ai in installed) {
            if (ai.enabled) continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName)
            out.add(AppInfo(ai.packageName, label, isSystem, ai.uid, false))
        }
        out.sortWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
        out
    }

    /**
     * 列出所有声明了 BOOT_COMPLETED 的广播接收器组件及其启用状态（含被禁用的）。
     * 用于「自启动治理」：禁止/恢复开机自启 = 禁用/启用这些接收器组件（不冻结应用）。
     */
    suspend fun bootReceivers(): List<BootReceiverInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val list = runCatching {
            pm.queryBroadcastReceivers(
                Intent(Intent.ACTION_BOOT_COMPLETED),
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
        }.getOrDefault(emptyList())
        val out = ArrayList<BootReceiverInfo>()
        for (ri in list) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName ?: continue
            val cls = ai.name ?: continue
            if (cls.isBlank()) continue
            val st = runCatching {
                pm.getComponentEnabledSetting(ComponentName(pkg, cls))
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            val enabled = st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            out.add(BootReceiverInfo(pkg, "$pkg/$cls", enabled))
        }
        out
    }

    /** 已被 AppOps 限制后台运行（RUN_ANY_IN_BACKGROUND=ignore）的包名集合。 */
    suspend fun bgRestrictedPkgs(): Set<String> {
        val r = channels.run("cmd appops query-op RUN_ANY_IN_BACKGROUND ignore", 8000)
        if (!(r.ok || r.exitCode == 0)) return emptySet()
        return r.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(' ') }
            .toSet()
    }

    /** 通过 AppOps 限制/恢复某应用后台运行（禁止开机自启的效果，但不冻结、不改组件）。 */
    suspend fun setBgRestricted(pkg: String, restricted: Boolean): Boolean {
        val mode = if (restricted) "ignore" else "default"
        val r = channels.run("cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode", 8000)
        return r.ok || r.exitCode == 0
    }

    /** 深度限制后台能力：一次限制后台运行 / 唤醒锁 / 后台拉起前台服务（AppOps，均免 root）。 */
    suspend fun setBgLimit(pkg: String, limited: Boolean): Boolean {
        val mode = if (limited) "ignore" else "default"
        val cmd = "cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode; " +
            "cmd appops set $pkg WAKE_LOCK $mode; " +
            "cmd appops set $pkg START_FOREGROUND $mode"
        val r = channels.run(cmd, 8000)
        return r.ok || r.exitCode == 0
    }

    /** 当前被冻结（disabled）的包名集合，用于恢复冻结状态展示。 */
    suspend fun disabledPackages(): Set<String> = withContext(Dispatchers.IO) {
        val r = channels.run("pm list packages -d")
        if (!r.ok) return@withContext emptySet()
        r.stdout.lineSequence()
            .mapNotNull { line -> line.trim().removePrefix("package:").takeIf { it.isNotBlank() } }
            .toSet()
    }

    // ---------------- 内存 ----------------

    suspend fun memInfo(): MemInfo = withContext(Dispatchers.IO) {
        val r = channels.run("cat /proc/meminfo")
        if (r.ok) parseMemInfo(r.stdout) else amMem()
    }

    /** 无 shell 权限时通过 ActivityManager 读取系统内存（无需任何权限）。 */
    private fun amMem(): MemInfo = runCatching {
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        MemInfo(mi.totalMem / 1024, mi.availMem / 1024, mi.availMem / 1024)
    }.getOrDefault(MemInfo.EMPTY)

    private fun parseMemInfo(text: String): MemInfo {
        var total = 0L; var avail = 0L; var free = 0L
        for (line in text.lineSequence()) {
            val parts = line.split(':', limit = 2)
            if (parts.size < 2) continue
            val key = parts[0].trim()
            val kb = parts[1].trim().substringBefore(' ').toLongOrNull() ?: continue
            when (key) {
                "MemTotal" -> total = kb
                "MemAvailable" -> avail = kb
                "MemFree" -> free = kb
            }
        }
        return MemInfo(total, avail, free)
    }

    // ---------------- CPU ----------------

    private object TopCmd {
        // 3 帧取均值，降低瞬时抖动（CPU 规则更稳定）
        const val CMD = "top -b -n 3 -d 1"
    }

    /** 无需 shell 的系统内存（ActivityManager），用于首页高频轻量刷新。 */
    fun memInfoCheap(): MemInfo = amMem()

    private var lastCpuStat: LongArray? = null
    private var lastShellStatAt = 0L
    private var lastShellCpu = CpuInfo.EMPTY
    private var loggedStatFallback = false

    /**
     * 整机 CPU 占用（0~100%，所有核心合计）。优先直读 /proc/stat（免 shell、免 fork）；
     * 个别机型对普通应用屏蔽 /proc/stat 时，自动回退到 shell `cat /proc/stat`（限频 4s）。
     * 与上次采样的时间差计算滚动窗口，避免瞬时抖动。
     */
    suspend fun cpuInfoCheap(): CpuInfo {
        val text = runCatching { File("/proc/stat").readText() }.getOrNull()
        if (text != null) return parseCpuStat(text)
        if (!loggedStatFallback) {
            loggedStatFallback = true
            android.util.Log.w("ProcKiller", "/proc/stat 不可读，改用 shell 回退读取 CPU")
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastShellStatAt >= 4000) {
            lastShellStatAt = now
            val r = runCatching { channels.run("cat /proc/stat", 4000) }.getOrNull()
            if (r != null && r.ok) lastShellCpu = parseCpuStat(r.stdout)
        }
        return lastShellCpu
    }

    /** 解析 /proc/stat 的 "cpu " 汇总行，用与上次的差值算整机 CPU 使用率。 */
    private fun parseCpuStat(text: String): CpuInfo {
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return CpuInfo.EMPTY
        val p = line.trim().split(Regex("\\s+"))
        fun v(i: Int) = p.getOrNull(i)?.toLongOrNull() ?: 0L
        val total = (1..8).sumOf { v(it) }        // user..steal
        val idle = v(4) + v(5)                     // idle + iowait
        val prev = lastCpuStat
        lastCpuStat = longArrayOf(total, idle)
        val used = if (prev != null) {
            val dt = total - prev[0]
            val di = idle - prev[1]
            if (dt > 0) ((dt - di).toFloat() / dt * 100f).coerceIn(0f, 100f) else 0f
        } else 0f
        return CpuInfo(used, "—")
    }

    // ---------------- 进程杀手自身占用 ----------------

    private var lastSelfTicks = -1L
    private var lastSelfTime = 0L
    private val clkTck: Long =
        runCatching { Os.sysconf(OsConstants._SC_CLK_TCK).toLong() }.getOrDefault(100L)

    /**
     * 读取本应用自身进程的 PSS 内存与 CPU 占用。全部通过 ActivityManager + /proc 读取，无需 shell。
     * CPU 用最近一次采样到本次的时间差计算（滚动），首次返回 0。
     */
    fun selfUsage(): SelfUsage {
        val pid = Process.myPid()
        val pss = runCatching {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            am.getProcessMemoryInfo(intArrayOf(pid))[0].totalPss.toLong()
        }.getOrElse { android.util.Log.w("ProcKiller", "selfUsage pss failed", it); 0L }
        val ticks = runCatching { readCpuTicks(pid) }.getOrElse {
            android.util.Log.w("ProcKiller", "selfUsage ticks failed", it); -1L
        }
        val now = SystemClock.elapsedRealtime()
        var pct = 0f
        if (ticks >= 0 && lastSelfTicks >= 0 && now > lastSelfTime) {
            val d = ticks - lastSelfTicks
            val dt = now - lastSelfTime
            if (d >= 0 && dt > 0) pct = (d.toFloat() / clkTck) / (dt / 1000f) * 100f
        }
        if (ticks >= 0) { lastSelfTicks = ticks; lastSelfTime = now }
        return SelfUsage(pss, pct.coerceIn(0f, 100f))
    }

    /** 读取指定 pid 的 utime+stime（时钟滴答数）。 */
    private fun readCpuTicks(pid: Int): Long {
        val s = File("/proc/$pid/stat").readText()
        val idx = s.lastIndexOf(')')
        if (idx < 0) return -1L
        val parts = s.substring(idx + 2).trim().split(Regex("\\s+"))
        val ut = parts.getOrNull(11)?.toLongOrNull() ?: return -1L
        val st = parts.getOrNull(12)?.toLongOrNull() ?: return -1L
        return ut + st
    }

    suspend fun cpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val r = channels.run(TopCmd.CMD, timeoutMs = 20000)
        parseCpu(r.stdout)
    }

    private fun parseCpu(text: String): CpuInfo {
        var used = 0f
        var load = "0.0 / 0.0 / 0.0"
        for (line in text.lineSequence()) {
            if (line.startsWith("Load:")) load = line.removePrefix("Load:").trim()
            if (line.contains("%cpu")) {
                val total = Regex("(\\d+)%cpu").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                val idle = Regex("(\\d+)%idle").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                if (total != null && idle != null && total > 0f) {
                    used = (total - idle) / total * 100f
                }
            }
        }
        return CpuInfo(used, load)
    }

    // ---------------- 进程 ----------------

    suspend fun processes(topText: String? = null, lite: Boolean = false): List<ProcInfo> = withContext(Dispatchers.IO) {
        val psResult = channels.run("ps -A -o PID,PPID,USER,NAME,RSS,STAT,ELAPSED")
        if (psResult.stdout.isBlank()) return@withContext emptyList()

        val cpuMap = HashMap<Int, Float>()
        val top = topText ?: channels.run(TopCmd.CMD, timeoutMs = 20000).stdout
        parseTopCpu(top, cpuMap)

        val oomMap = HashMap<Int, Int>()
        val actResult = channels.run("dumpsys activity processes")
        if (actResult.ok) parseOom(actResult.stdout, oomMap)

        // 进程 PSS（比 RSS 更接近真实占用，dumpsys meminfo 一次获取全部进程）；lite 模式跳过以加速扫描
        val pssMap = HashMap<Int, Long>()
        if (!lite) {
            val memRes = channels.run("dumpsys meminfo")
            if (memRes.ok) parsePss(memRes.stdout, pssMap)
        }

        val appMap = appMap()
        val out = ArrayList<ProcInfo>()
        var headerSkipped = false
        for (raw in psResult.stdout.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!headerSkipped && line.startsWith("PID")) { headerSkipped = true; continue }
            val t = line.split(Regex("\\s+"))
            if (t.size < 7) continue
            val pid = t[0].toIntOrNull() ?: continue
            val ppid = t[1].toIntOrNull() ?: 0
            val user = t[2]
            val name = t[3]
            val rss = t[4].toLongOrNull() ?: 0L
            val stat = t[5]
            val elapsed = parseElapsed(t[6])
            val pkg = name.substringBefore(':')
            val info = appMap[pkg]
            val oom = oomMap[pid] ?: Int.MIN_VALUE
            val isSystem = info?.isSystem
                ?: (user == "root" || user == "system" || name.startsWith("["))
            val isForeground = when {
                oom == Int.MIN_VALUE -> false
                else -> oom <= 200
            }
            out.add(
                ProcInfo(
                    pid = pid, ppid = ppid, user = user, name = name, packageName = pkg,
                    rssKb = rss, pssKb = pssMap[pid] ?: 0L, stat = stat, cpuPercent = cpuMap[pid] ?: 0f,
                    oomCur = if (oom == Int.MIN_VALUE) -1000 else oom,
                    isForeground = isForeground, isSystem = isSystem, elapsedSec = elapsed
                )
            )
        }
        out.sortByDescending { it.rssKb }
        out
    }

    /** 解析 ps ELAPSED：MM:SS / HH:MM:SS / D-HH:MM:SS，返回秒。 */
    private fun parseElapsed(s: String): Long {
        if (s.isBlank()) return 0
        var f = s
        var days = 0L
        if (f.contains('-')) {
            val idx = f.indexOf('-')
            days = f.substring(0, idx).toLongOrNull() ?: 0
            f = f.substring(idx + 1)
        }
        val parts = f.split(':').map { it.toLongOrNull() ?: 0L }
        return when (parts.size) {
            3 -> days * 86400 + parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> days * 86400 + parts[0] * 60 + parts[1]
            1 -> days * 86400 + parts[0]
            else -> 0
        }
    }

    private fun parseTopCpu(text: String, into: MutableMap<Int, Float>) {
        // top 多帧中同一 PID 会出现多次，累加后取均值以降低瞬时抖动
        val sum = HashMap<Int, Float>()
        val cnt = HashMap<Int, Int>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || !line[0].isDigit()) continue
            val t = line.split(Regex("\\s+"))
            if (t.size < 9) continue
            val pid = t[0].toIntOrNull() ?: continue
            val cpu = t[8].toFloatOrNull() ?: continue
            sum[pid] = (sum[pid] ?: 0f) + cpu
            cnt[pid] = (cnt[pid] ?: 0) + 1
        }
        for ((pid, s) in sum) into[pid] = s / (cnt[pid] ?: 1)
    }

    /** 解析 dumpsys meminfo 的「Total PSS by process」小节，按 pid 汇总 PSS(KB)。 */
    private fun parsePss(text: String, into: MutableMap<Int, Long>) {
        var inSection = false
        val re = Regex("^\\s*([\\d,]+)K:\\s+.*\\(pid (\\d+)")
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("Total PSS by process")) { inSection = true; continue }
            if (!inSection) continue
            if (line.startsWith("Total PSS by")) { inSection = false; continue }
            if (line.isEmpty()) continue
            re.find(raw)?.let {
                val pss = it.groupValues[1].replace(",", "").toLongOrNull() ?: return@let
                val pid = it.groupValues[2].toIntOrNull() ?: return@let
                into[pid] = (into[pid] ?: 0L) + pss
            }
        }
    }

    private fun parseOom(text: String, into: MutableMap<Int, Int>) {
        var currentPid = -1
        val headRe = Regex("ProcessRecord\\{[0-9a-f]+ (\\d+):")
        val oomRe = Regex("oom adj:.*cur=(-?\\d+)")
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            headRe.find(line)?.let { currentPid = it.groupValues[1].toIntOrNull() ?: -1 }
            if (currentPid > 0) {
                oomRe.find(line)?.let {
                    it.groupValues[1].toIntOrNull()?.let { v -> into[currentPid] = v }
                    currentPid = -1
                }
            }
        }
    }

    // ---------------- 汇总采集 ----------------

    suspend fun snapshot(lite: Boolean = false): Snapshot = withContext(Dispatchers.IO) {
        val apps = apps()
        val ready = channels.current()?.isReady() == true
        if (!ready) {
            // 未连接权限通道：仍展示已安装应用（用户/系统）与系统内存（ActivityManager 读取），
            // 进程/CPU 需要 shell 才能获取，此处留空而不报错，避免界面卡顿。
            return@withContext Snapshot(apps, emptyList(), memInfoCheap(), CpuInfo.EMPTY, System.currentTimeMillis())
        }
        // lite：仅 2 帧 top + 跳过 dumpsys meminfo，用于「扫描」过程快速取快照（省 1~2 秒）
        // 采集任一步失败都不让整体抛异常：进程为空也照常返回内存/CPU，避免界面全部归零。
        val top = runCatching {
            channels.run(if (lite) "top -b -n 2 -d 1" else TopCmd.CMD, timeoutMs = 20000).stdout
        }.getOrDefault("")
        val procs = runCatching { processes(top, lite) }.getOrDefault(emptyList())
        val mem = memInfoCheap()
        val cpu = cpuInfoCheap()
        Snapshot(apps, procs, mem, cpu, System.currentTimeMillis())
    }
}
