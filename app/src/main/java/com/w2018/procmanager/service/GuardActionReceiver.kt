package com.w2018.procmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.w2018.procmanager.core.EventLog
import com.w2018.procmanager.core.GuardRuntime
import com.w2018.procmanager.core.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 承载前台常驻通知两个按钮（暂停/恢复守护、一键清理）的广播接收器。
 * 相比从通知 PendingIntent.getService 直接启动服务，广播在各类机型和后台限制下更可靠。
 */
class GuardActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            GuardService.ACTION_TOGGLE -> {
                GuardRuntime.paused.value = !GuardRuntime.paused.value
                EventLog.add(
                    LogType.SYSTEM,
                    if (GuardRuntime.paused.value) "守护已暂停" else "守护已恢复"
                )
                if (!GuardRuntime.paused.value) GuardRuntime.requestKick?.invoke()
            }
            GuardService.ACTION_CLEAN -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val cb = GuardRuntime.requestClean
                        if (cb == null) {
                            EventLog.add(LogType.KILL, "一键清理", "引擎尚未就绪")
                        } else {
                            val (n, freed) = cb()
                            EventLog.add(LogType.KILL, "一键清理", "清理 $n 个进程，释放 ${freed / 1024} MB")
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
