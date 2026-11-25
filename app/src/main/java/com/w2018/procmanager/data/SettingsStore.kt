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
        )
    }

    fun setPerApp(pkg: String, c: PerAppCfg) {
        if (!c.enabled && c.memMb == 0 && c.bgMin == 0 && c.cpu == 0) {
            sp.edit { remove("pa_$pkg") }
        } else {
            sp.edit { putString("pa_$pkg", "${if (c.enabled) 1 else 0};${c.memMb};${c.bgMin};${c.cpu}") }
        }
    }

    fun hasPerAppRule(pkg: String): Boolean = getPerApp(pkg).enabled
}
