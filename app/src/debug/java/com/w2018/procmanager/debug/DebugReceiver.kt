package com.w2018.procmanager.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.w2018.procmanager.ProcApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 仅 debug 构建可用：通过 adb 广播触发权限通道自检，结果写入 logcat(tag=ProcDbg)。
 * 例：adb shell am broadcast -a com.w2018.procmanager.DEBUG --es cmd "id" -n com.w2018.procmanager/.debug.DebugReceiver
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ProcApp
        val pending = goAsync()
        val cmd = intent.getStringExtra("cmd") ?: "id"
        val mode = intent.getStringExtra("mode") ?: "auto"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (cmd == "CLEAN") {
                    app.channels.autoConnect()
                    val (n, freed) = app.engine.cleanNow()
                    Log.i("ProcDbg", "CLEAN killed=$n freedMB=${freed/1024} stats.blocked=${app.stats.blocked} stats.freed=${app.stats.freedKb/1024}")
                    return@launch
                }
                if (cmd == "RUNRULES") {
                    app.settings.guardian = true
                    com.w2018.procmanager.core.GuardRuntime.requestKick?.invoke()
                    Log.i("ProcDbg", "kick sent; wait for engine")
                    kotlinx.coroutines.delay(4000)
                    Log.i("ProcDbg", "after rules stats.blocked=${app.stats.blocked} freedMB=${app.stats.freedKb/1024}")
                    return@launch
                }
                if (cmd == "START_GUARD") {
                    com.w2018.procmanager.service.GuardService.start(context)
                    Log.i("ProcDbg", "guard started")
                    return@launch
                }
                if (cmd == "STOP_GUARD") {
                    com.w2018.procmanager.service.GuardService.stop(context)
                    Log.i("ProcDbg", "guard stopped")
                    return@launch
                }
                if (cmd == "FG") {
                    Log.i("ProcDbg", "FG pkg=${com.w2018.procmanager.core.EventHub.foregroundPackage.value} a11y=${com.w2018.procmanager.core.EventHub.accessibilityConnected.value} running=${com.w2018.procmanager.core.GuardRuntime.running.value}")
                    return@launch
                }
                if (cmd == "SNAP") {
                    app.channels.autoConnect()
                    val snap = app.repo.snapshot()
                    Log.i("ProcDbg", "SNAP apps=${snap.apps.size} userApps=${snap.apps.count { !it.isSystem }} procs=${snap.processes.size}")
                    Log.i("ProcDbg", "SNAP mem total=${snap.mem.totalKb} avail=${snap.mem.availKb} used=${snap.mem.usedKb} pct=${"%.1f".format(snap.mem.percent)}")
                    Log.i("ProcDbg", "SNAP cpu=${"%.1f".format(snap.cpu.usedPercent)}% load=${snap.cpu.load}")
                    snap.processes.take(6).forEach {
                        Log.i("ProcDbg", "TOP pid=${it.pid} ${it.name} rss=${it.rssKb}KB cpu=${it.cpuPercent}% oom=${it.oomCur} fg=${it.isForeground} sys=${it.isSystem}")
                    }
                    return@launch
                }
                Log.i("ProcDbg", "start mode=$mode cmd=$cmd")
                val st = when (mode) {
                    "shizuku" -> app.channels.connectShizukuOnly()
                    "adb" -> app.channels.connectAdbOnly(
                        intent.getStringExtra("host"),
                        intent.getIntExtra("port", 0).takeIf { it > 0 }
                    )
                    else -> app.channels.autoConnect()
                }
                Log.i("ProcDbg", "channel=${st.type} label=${st.label} ready=${st.ready} detail=${st.detail}")
                val r = app.channels.run(cmd)
                Log.i("ProcDbg", "run exit=${r.exitCode} stdout=[${r.stdout.take(800)}] stderr=[${r.stderr.take(400)}]")
            } catch (t: Throwable) {
                Log.e("ProcDbg", "error: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }
}
