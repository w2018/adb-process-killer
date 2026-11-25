package com.w2018.procmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.w2018.procmanager.MainActivity
import com.w2018.procmanager.ProcApp
import com.w2018.procmanager.R
import com.w2018.procmanager.core.EventLog
import com.w2018.procmanager.core.GuardRuntime
import com.w2018.procmanager.core.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台常驻通知服务：展示守护状态/监控数/今日拦截·释放内存，并提供暂停恢复、一键清理快捷操作。
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    private val app: ProcApp get() = application as ProcApp

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTI_ID, buildNotification())
        GuardRuntime.running.value = true
        EventLog.add(LogType.SYSTEM, "守护服务已启动")
        ticker = scope.launch {
            while (isActive) {
                updateNotification()
                delay(3000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                GuardRuntime.paused.value = !GuardRuntime.paused.value
                EventLog.add(
                    LogType.SYSTEM,
                    if (GuardRuntime.paused.value) "守护已暂停" else "守护已恢复"
                )
                if (!GuardRuntime.paused.value) GuardRuntime.requestKick?.invoke()
                updateNotification()
            }
            ACTION_CLEAN -> {
                scope.launch {
                    val cb = GuardRuntime.requestClean
                    if (cb == null) {
                        EventLog.add(LogType.KILL, "一键清理", "引擎尚未就绪")
                    } else {
                        val (n, freed) = cb()
                        EventLog.add(LogType.KILL, "一键清理", "清理 $n 个进程，释放 ${freed / 1024} MB")
                    }
                    updateNotification()
                }
            }
        }
        return START_STICKY
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTI_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val stats = app.stats
        val monitored = app.settings.monitored.size
        val paused = GuardRuntime.paused.value
        val statusText = if (paused) "已暂停" else "守护中"
        val freedMb = stats.freedKb / 1024
        val title = "进程杀手 · $statusText"
        val text = "监控 $monitored 个应用 · 今日拦截 ${stats.blocked} 次 · 释放 $freedMb MB"

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GuardService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cleanIntent = PendingIntent.getService(
            this, 2,
            Intent(this, GuardService::class.java).setAction(ACTION_CLEAN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, if (paused) "恢复守护" else "暂停守护", toggleIntent)
            .addAction(0, "一键清理", cleanIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH_ID) == null) {
                val ch = NotificationChannel(CH_ID, "守护状态", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示后台进程守护运行状态"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    override fun onDestroy() {
        ticker?.cancel()
        GuardRuntime.running.value = false
        EventLog.add(LogType.SYSTEM, "守护服务已停止")
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CH_ID = "guard_status"
        const val NOTI_ID = 1001
        const val ACTION_TOGGLE = "com.w2018.procmanager.action.TOGGLE"
        const val ACTION_CLEAN = "com.w2018.procmanager.action.CLEAN"

        fun start(ctx: Context) {
            val i = Intent(ctx, GuardService::class.java)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, GuardService::class.java))
        }
    }
}
