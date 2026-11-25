package com.w2018.procmanager.data.model

/** 已安装应用基础信息（来自 PackageManager）。 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val uid: Int,
    /** 是否开机自启（声明了监听 BOOT_COMPLETED 的接收器）。 */
    val autoStart: Boolean = false
)

/** 单个进程快照。 */
data class ProcInfo(
    val pid: Int,
    val ppid: Int,
    val user: String,
    val name: String,
    val packageName: String,
    val rssKb: Long,
    val stat: String,
    val cpuPercent: Float,
    val oomCur: Int,
    val isForeground: Boolean,
    val isSystem: Boolean,
    /** 进程已运行时长（秒），来自 ps ELAPSED。 */
    val elapsedSec: Long = 0
)

/** 系统内存信息（单位 KB）。 */
data class MemInfo(
    val totalKb: Long,
    val availKb: Long,
    val freeKb: Long
) {
    val usedKb: Long get() = (totalKb - availKb).coerceAtLeast(0)
    val percent: Float get() = if (totalKb <= 0) 0f else (usedKb.toFloat() / totalKb * 100f)

    companion object {
        val EMPTY = MemInfo(0, 0, 0)
    }
}

/** CPU 总体信息。 */
data class CpuInfo(
    val usedPercent: Float,
    val load: String
) {
    companion object {
        val EMPTY = CpuInfo(0f, "0.0 / 0.0 / 0.0")
    }
}

/** 一次完整采集的结果。 */
data class Snapshot(
    val apps: List<AppInfo>,
    val processes: List<ProcInfo>,
    val mem: MemInfo,
    val cpu: CpuInfo,
    val timestamp: Long
)
