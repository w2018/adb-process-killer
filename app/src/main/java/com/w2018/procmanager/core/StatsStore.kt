package com.w2018.procmanager.core

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日统计：今日拦截次数、释放内存、扫描次数。跨天自动清零。
 */
class StatsStore(context: Context) {
    private val sp = context.getSharedPreferences("proc_stats", Context.MODE_PRIVATE)
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun today(): String = fmt.format(Date())

    private fun ensureDay() {
        val day = sp.getString("day", "")
        if (day != today()) {
            sp.edit {
                putString("day", today())
                putInt("blocked", 0)
                putInt("batches", 0)
                putLong("freed", 0L)
                putInt("scans", 0)
            }
        }
    }

    val day: String get() { ensureDay(); return today() }

    /** 今日已清理的应用数量（口径=被终止的应用个数，而非批次）。 */
    var blocked: Int
        get() { ensureDay(); return sp.getInt("blocked", 0) }
        private set(v) { sp.edit { putInt("blocked", v) } }

    /** 今日触发的清理批次数量。 */
    var batches: Int
        get() { ensureDay(); return sp.getInt("batches", 0) }
        private set(v) { sp.edit { putInt("batches", v) } }

    var freedKb: Long
        get() { ensureDay(); return sp.getLong("freed", 0L) }
        private set(v) { sp.edit { putLong("freed", v) } }

    var scans: Int
        get() { ensureDay(); return sp.getInt("scans", 0) }
        private set(v) { sp.edit { putInt("scans", v) } }

    /** 最近一次清理释放的内存（跨天保留，代表“上一次释放”，非累计）。 */
    var lastFreedKb: Long
        get() = sp.getLong("last_freed", 0L)
        private set(v) { sp.edit { putLong("last_freed", v) } }

    /** 最近一次清理的时间戳（毫秒，跨天保留），用于通知栏显示「上次清理」精确时间。 */
    var lastCleanAt: Long
        get() = sp.getLong("last_clean_at", 0L)
        private set(v) { sp.edit { putLong("last_clean_at", v) } }

    /** 记录一次清理：appCount=本次终止的应用数，freed=估算释放内存。 */
    fun addBlocked(appCount: Int, freed: Long) {
        ensureDay()
        if (appCount > 0) blocked = blocked + appCount
        batches = batches + 1
        if (freed > 0) { freedKb = freedKb + freed; lastFreedKb = freed }
        if (appCount > 0) lastCleanAt = System.currentTimeMillis()
    }

    fun addScan() {
        ensureDay()
        scans = scans + 1
    }

    /** 每个应用累计被清理的次数（长期保留，用于排行榜）。键前缀 kill_。 */
    fun killsOf(pkg: String): Int = sp.getInt("kill_$pkg", 0)

    fun addKills(pkgs: List<String>) {
        if (pkgs.isEmpty()) return
        sp.edit {
            pkgs.distinct().forEach { p -> putInt("kill_$p", sp.getInt("kill_$p", 0) + 1) }
        }
    }

    /** 读取全部应用清理次数（pkg -> 次数）。 */
    fun killCounts(): Map<String, Int> = sp.all
        .filterKeys { it.startsWith("kill_") && it.length > 5 }
        .mapKeys { it.key.removePrefix("kill_") }
        .mapValues { (it.value as? Int) ?: 0 }

    fun resetToday() {
        sp.edit {
            putString("day", today())
            putInt("blocked", 0)
            putInt("batches", 0)
            putLong("freed", 0L)
            putInt("scans", 0)
        }
    }
}
