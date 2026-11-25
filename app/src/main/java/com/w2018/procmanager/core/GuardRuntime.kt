package com.w2018.procmanager.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 守护运行时状态与引擎桥接回调（由 M4 引擎注册）。
 */
object GuardRuntime {
    /** 前台/后台监控方式：EVENT=无障碍事件驱动（低功耗，优先）；POLL=轮询（兜底）。二者互斥，仅一种生效。 */
    enum class MonitorMode { EVENT, POLL }

    val paused = MutableStateFlow(false)
    val running = MutableStateFlow(false)
    val monitorMode = MutableStateFlow(MonitorMode.POLL)

    /** M4 引擎注册：执行一次全量清理，返回(拦截数, 释放KB) */
    @Volatile
    var requestClean: (suspend () -> Pair<Int, Long>)? = null

    /** M4 引擎注册：立即触发一次扫描（事件驱动/手动） */
    @Volatile
    var requestKick: (() -> Unit)? = null
}
