package com.w2018.procmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.w2018.procmanager.ProcApp
import com.w2018.procmanager.core.EventLog
import com.w2018.procmanager.core.LogType

/** 开机自启动：设备重启后按设置恢复守护前台服务。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val app = context.applicationContext as? ProcApp ?: return
        if (app.settings.autoStart && app.settings.guardian) {
            runCatching { GuardService.start(context) }
            EventLog.add(LogType.SYSTEM, "开机自启动", "已恢复守护服务")
        }
    }
}
