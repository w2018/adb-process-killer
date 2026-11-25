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
                putLong("freed", 0L)
                putInt("scans", 0)
            }
        }
    }

    val day: String get() { ensureDay(); return today() }

    var blocked: Int
        get() { ensureDay(); return sp.getInt("blocked", 0) }
        private set(v) { sp.edit { putInt("blocked", v) } }

    var freedKb: Long
        get() { ensureDay(); return sp.getLong("freed", 0L) }
        private set(v) { sp.edit { putLong("freed", v) } }

    var scans: Int
        get() { ensureDay(); return sp.getInt("scans", 0) }
        private set(v) { sp.edit { putInt("scans", v) } }

    fun addBlocked(freed: Long) {
        ensureDay()
        blocked = blocked + 1
        if (freed > 0) freedKb = freedKb + freed
    }

    fun addScan() {
        ensureDay()
        scans = scans + 1
    }

    fun resetToday() {
        sp.edit {
            putString("day", today())
            putInt("blocked", 0)
            putLong("freed", 0L)
            putInt("scans", 0)
        }
    }
}
