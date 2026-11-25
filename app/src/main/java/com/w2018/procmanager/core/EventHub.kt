package com.w2018.procmanager.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量事件总线/全局状态：无障碍服务上报前台包名，供看门狗做事件驱动。
 */
object EventHub {
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage = _foregroundPackage.asStateFlow()

    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected = _accessibilityConnected.asStateFlow()

    fun onForeground(pkg: String?) {
        if (pkg.isNullOrBlank()) return
        if (_foregroundPackage.value != pkg) _foregroundPackage.value = pkg
    }

    fun setAccessibilityConnected(v: Boolean) {
        _accessibilityConnected.value = v
    }
}
