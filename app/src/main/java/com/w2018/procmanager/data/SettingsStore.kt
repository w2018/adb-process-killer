package com.w2018.procmanager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 设置存储：SharedPreferences 封装。所有 setter 立即写入（实时保存）。
 */
class SettingsStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("proc_settings", Context.MODE_PRIVATE)

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun ib(key: String, value: Boolean) = sp.edit { putBoolean(key, value) }
    private fun i(key: String, def: Int) = sp.getInt(key, def)
    private fun ii(key: String, value: Int) = sp.edit { putInt(key, value) }

    // ---- 守护服务 ----
    var guardian: Boolean
        get() = b("sw_guardian", true); set(v) = ib("sw_guardian", v)
    var watchdog: Boolean
        get() = b("sw_watchdog", true); set(v) = ib("sw_watchdog", v)
    var autoStart: Boolean
        get() = b("sw_autostart", true); set(v) = ib("sw_autostart", v)
    var notify: Boolean
        get() = b("sw_notify", true); set(v) = ib("sw_notify", v)

    // ---- 立即停止规则 ----
    var aliveStop: Boolean
        get() = b("sw_aliveStop", true); set(v) = ib("sw_aliveStop", v)
    /** 内存超限清理（原「内存/CPU 超限清理」拆分，默认跟随 aliveStop）。 */
    var memStop: Boolean
        get() = b("sw_memStop", aliveStop); set(v) = ib("sw_memStop", v)
    /** CPU 超限清理（默认跟随 aliveStop）。 */
    var cpuStop: Boolean
        get() = b("sw_cpuStop", aliveStop); set(v) = ib("sw_cpuStop", v)
    /** 缓存/空进程闲置清理。 */
    var cacheClean: Boolean
        get() = b("sw_cacheClean", false); set(v) = ib("sw_cacheClean", v)
    var cacheIdleMin: Int
        get() = i("cfg_cacheIdle", 10); set(v) = ii("cfg_cacheIdle", v)
    /** 内存压力触发清理（系统可用内存低于阈值时）。 */
    var memPressureRule: Boolean
        get() = b("sw_memPressure", false); set(v) = ib("sw_memPressure", v)
    var memPressurePct: Int
        get() = i("cfg_memPressurePct", 15); set(v) = ii("cfg_memPressurePct", v)
    /** 低电量 / 高温触发更积极清理。 */
    var boostRule: Boolean
        get() = b("sw_boost", false); set(v) = ib("sw_boost", v)
    var boostBatteryPct: Int
        get() = i("cfg_boostBattery", 20); set(v) = ii("cfg_boostBattery", v)
    var boostTempC: Int
        get() = i("cfg_boostTemp", 45); set(v) = ii("cfg_boostTemp", v)
    var bgStop: Boolean
        get() = b("sw_bgStop", false); set(v) = ib("sw_bgStop", v)
    var wakeBlock: Boolean
        get() = b("sw_wakeBlock", true); set(v) = ib("sw_wakeBlock", v)
    var freqProtect: Boolean
        get() = b("sw_freqProtect", true); set(v) = ib("sw_freqProtect", v)

    // ---- 省电 ----
    var lockLowPower: Boolean
        get() = b("sw_lockLowPower", true); set(v) = ib("sw_lockLowPower", v)
    var screenOffSave: Boolean
        get() = b("sw_screenOffSave", true); set(v) = ib("sw_screenOffSave", v)
    var chargeExempt: Boolean
        get() = b("sw_chargeExempt", true); set(v) = ib("sw_chargeExempt", v)
    var nightMode: Boolean
        get() = b("sw_nightMode", false); set(v) = ib("sw_nightMode", v)

    /** 主题模式：system / dark / light */
    var themeMode: String
        get() = sp.getString("cfg_themeMode", "system") ?: "system"
        set(v) = sp.edit { putString("cfg_themeMode", v) }

    // ---- 阈值 ----
    var memThreshold: Int
        get() = i("cfg_mem", 150); set(v) = ii("cfg_mem", v)
    var bgMinutes: Int
        get() = i("cfg_bgTime", 5); set(v) = ii("cfg_bgTime", v)
    var cpuThreshold: Int
        get() = i("cfg_cpu", 15); set(v) = ii("cfg_cpu", v)
    var cooldownSec: Int
        get() = i("cfg_cooldown", 30); set(v) = ii("cfg_cooldown", v)
    /** 全局「后台缓冲时间」(分钟)：进入后台超过该时长才允许按内存/CPU 阈值清理，回到前台即刷新，避免误杀临时切后台的应用。 */
    var bufferMin: Int
        get() = i("cfg_bufferMin", 3); set(v) = ii("cfg_bufferMin", v)
    var scanInterval: Int
        get() = i("cfg_scan", 30); set(v) = ii("cfg_scan", v)
    var adbFreqLimit: Int
        get() = i("cfg_adbFreq", 20); set(v) = ii("cfg_adbFreq", v)
    var preset: String
        get() = sp.getString("cfg_preset", "balance") ?: "balance"
        set(v) = sp.edit { putString("cfg_preset", v) }

    // ---- 权限通道（无线 ADB） ----
    var adbHost: String
        get() = sp.getString("cfg_adbHost", "") ?: ""
        set(v) = sp.edit { putString("cfg_adbHost", v) }
    var adbPort: Int
        get() = i("cfg_adbPort", 5555); set(v) = ii("cfg_adbPort", v)
    /** 通道偏好：auto / shizuku / adb */
    var channelMode: String
        get() = sp.getString("cfg_channelMode", "auto") ?: "auto"
        set(v) = sp.edit { putString("cfg_channelMode", v) }
    /** 上次成功使用的通道（仅记录展示用） */
    var lastChannel: String
        get() = sp.getString("cfg_lastChannel", "") ?: ""
        set(v) = sp.edit { putString("cfg_lastChannel", v) }

    // ---- 白名单 ----
    var whitelist: Set<String>
        get() = sp.getStringSet("whitelist", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("whitelist", v) }

    // ---- 自定义监控名单（用户手动加入监控） ----
    var monitored: Set<String>
        get() = sp.getStringSet("monitored", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("monitored", v) }

    /** 监控范围：all=全部用户应用（默认，开箱即用）；selected=仅勾选的应用 */
    var monitorMode: String
        get() = sp.getString("cfg_monitorMode", "all") ?: "all"
        set(v) = sp.edit { putString("cfg_monitorMode", v) }

    /** 本应用主动冻结（pm disable-user）的包，用于与用户原有停用区分 */
    var frozenPkgs: Set<String>
        get() = sp.getStringSet("frozenPkgs", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("frozenPkgs", v) }

    fun setFrozen(pkg: String, frozen: Boolean) {
        val s = frozenPkgs.toMutableSet()
        if (frozen) s.add(pkg) else s.remove(pkg)
        frozenPkgs = s
    }

    // ---- 单应用「后台即停」覆盖 ----
    var bgStopPkgs: Set<String>
        get() = sp.getStringSet("bgStopPkgs", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("bgStopPkgs", v) }

    fun isWhitelisted(pkg: String) = whitelist.contains(pkg)
    fun toggleWhitelist(pkg: String) {
        val s = whitelist.toMutableSet()
        if (!s.add(pkg)) s.remove(pkg)
        whitelist = s
    }
    fun toggleMonitored(pkg: String) {
        val s = monitored.toMutableSet()
        if (!s.add(pkg)) s.remove(pkg)
        monitored = s
    }
    fun toggleBgStop(pkg: String) {
        val s = bgStopPkgs.toMutableSet()
        if (!s.add(pkg)) s.remove(pkg)
        bgStopPkgs = s
    }

    // ---- 单应用独立规则（0 表示跟随全局） ----
    data class PerAppCfg(
        val enabled: Boolean = false,
        val memMb: Int = 0,
        val bgMin: Int = 0,
        val cpu: Int = 0,
        /** 单应用缓冲时间(分钟)，0=跟随全局。进入后台超过该时长才按阈值清理。 */
        val bufMin: Int = 0,
    )

    fun getPerApp(pkg: String): PerAppCfg {
        val raw = sp.getString("pa_$pkg", "") ?: ""
        if (raw.isBlank()) return PerAppCfg()
        val p = raw.split(';')
        return PerAppCfg(
            enabled = p.getOrNull(0)?.toIntOrNull() == 1,
            memMb = p.getOrNull(1)?.toIntOrNull() ?: 0,
            bgMin = p.getOrNull(2)?.toIntOrNull() ?: 0,
            cpu = p.getOrNull(3)?.toIntOrNull() ?: 0,
            bufMin = p.getOrNull(4)?.toIntOrNull() ?: 0,
        )
    }

    fun setPerApp(pkg: String, c: PerAppCfg) {
        if (!c.enabled && c.memMb == 0 && c.bgMin == 0 && c.cpu == 0 && c.bufMin == 0) {
            sp.edit { remove("pa_$pkg") }
        } else {
            sp.edit { putString("pa_$pkg", "${if (c.enabled) 1 else 0};${c.memMb};${c.bgMin};${c.cpu};${c.bufMin}") }
        }
    }

    fun hasPerAppRule(pkg: String): Boolean = getPerApp(pkg).enabled
}
