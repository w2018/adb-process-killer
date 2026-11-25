package com.w2018.procmanager.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.w2018.procmanager.core.EventHub
import com.w2018.procmanager.core.EventLog
import com.w2018.procmanager.core.LogType

/**
 * 无障碍服务：监听窗口状态变化，上报前台应用包名，供事件驱动省电与唤醒检测。
 * 未开启时看门狗自动降级为纯轮询。
 */
class ProcAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        EventHub.setAccessibilityConnected(true)
        EventLog.add(LogType.SYSTEM, "无障碍服务已连接", "事件驱动已启用")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        android.util.Log.i("ProcA11y", "event=${event.eventType} pkg=${event.packageName}")
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.toString()?.let { EventHub.onForeground(it) }
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        EventHub.setAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        EventHub.setAccessibilityConnected(false)
        EventLog.add(LogType.SYSTEM, "无障碍服务已断开", "降级为轮询模式")
        super.onDestroy()
    }
}
