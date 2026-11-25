package com.w2018.procmanager.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 日志事件类型。 */
enum class LogType(val key: String, val label: String) {
    KILL("kill", "清理"),
    RULE("rule", "规则"),
    SCAN("scan", "扫描"),
    CHANNEL("channel", "通道"),
    SYSTEM("system", "系统")
}

data class LogEvent(
    val time: Long,
    val type: LogType,
    val title: String,
    val detail: String = ""
)

/** 内存中的最近事件日志（供日志页展示）。 */
object EventLog {
    private const val MAX = 200
    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events = _events.asStateFlow()

    fun add(type: LogType, title: String, detail: String = "") {
        val e = LogEvent(System.currentTimeMillis(), type, title, detail)
        _events.value = (listOf(e) + _events.value).take(MAX)
    }

    fun clear() {
        _events.value = emptyList()
    }
}
