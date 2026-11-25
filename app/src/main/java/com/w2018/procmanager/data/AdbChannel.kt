package com.w2018.procmanager.data

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 直连无线 ADB（adbd）执行 shell。使用 dadb 库实现 ADB 协议与 RSA 认证。
 * 密钥持久化到应用私有目录，首次连接需在设备端确认 RSA 指纹。
 * 默认扫描本机 127.0.0.1 的常用 ADB 端口（无线 adb 本机自连），也支持手动指定主机地址。
 */
class AdbChannel(
    private val context: Context,
    private val settings: SettingsStore
) : ShellExecutor {

    override val id: String = "adb"
    override val label: String = "无线 ADB"

    @Volatile
    private var dadb: Dadb? = null

    /** 最近一次连接失败原因，供 UI 展示。 */
    @Volatile
    var lastError: String = ""

    private val keyPair: AdbKeyPair by lazy { ensureKeyPair() }

    private fun ensureKeyPair(): AdbKeyPair {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) {
            runCatching { AdbKeyPair.generate(priv, pub) }
        }
        return AdbKeyPair.read(priv, pub)
    }

    companion object {
        /** 常用 adb 端口（无线 adb / adb tcpip 默认 5555、5557 等）。 */
        val COMMON_PORTS = intArrayOf(5555, 5557, 5556, 5558, 5560, 5580, 5590, 5600, 5610, 5620)
    }

    /** 快速探测本机 127.0.0.1 上开放的 adb 端口（纯 TCP 连接，300ms 超时，不发起认证）。 */
    suspend fun scanLocal(): List<Int> = withContext(Dispatchers.IO) {
        COMMON_PORTS.filter { p -> probeTcp("127.0.0.1", p, 300) }
    }

    private fun probeTcp(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (t: Throwable) {
        false
    }

    /** 尝试连接任意候选地址，成功则记录并返回 true。整体有时间上限，避免长时间卡「连接中」。 */
    suspend fun connectAuto(): Boolean = withContext(Dispatchers.IO) {
        // 1) 已保存的地址优先
        val savedHost = settings.adbHost
        if (savedHost.isNotBlank() && tryConnect(savedHost, settings.adbPort)) {
            settings.adbHost = savedHost
            return@withContext true
        }
        // 2) 扫描本机开放端口，逐个尝试认证
        val open = COMMON_PORTS.filter { probeTcp("127.0.0.1", it, 300) }
        for (p in open) {
            if (tryConnect("127.0.0.1", p, 4000, 2000)) {
                settings.adbHost = "127.0.0.1"
                settings.adbPort = p
                return@withContext true
            }
        }
        if (open.isEmpty()) lastError = "未发现本机开放的 ADB 端口"
        else if (lastError.isBlank()) lastError = "端口已开放但 ADB 认证未通过"
        false
    }

    /** 连接指定地址。手动连接时给足时间，允许用户在手机上点击『允许 USB 调试』授权。 */
    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        if (tryConnect(host, port, 16000, 14000)) {
            settings.adbHost = host
            settings.adbPort = port
            true
        } else false
    }

    private val exec = java.util.concurrent.Executors.newCachedThreadPool()

    private fun tryConnect(host: String, port: Int, timeoutMs: Long = 4000, socketTimeoutMs: Int = 2000): Boolean {
        // Dadb.create 在设备未授权该密钥时会阻塞等待认证（并在手机弹出授权框），
        // 放到独立线程并加超时，避免「一直连接中/点击无反应」。
        val fut = exec.submit(java.util.concurrent.Callable { doConnect(host, port, socketTimeoutMs) })
        return try {
            fut.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            fut.cancel(true)
            lastError = "连接超时：若手机弹出『允许 USB 调试 / 调试授权』，请点“允许”后重试；或改用 Shizuku"
            android.util.Log.w("ProcKiller", "dadb connect timeout $host:$port (需设备端授权?)")
            false
        } catch (e: Throwable) {
            lastError = "连接失败：${e.cause?.message ?: e.message}"
            android.util.Log.w("ProcKiller", "dadb connect failed $host:$port: ${e.message}")
            false
        }
    }

    private fun doConnect(host: String, port: Int, socketTimeoutMs: Int): Boolean {
        return try {
            android.util.Log.i("ProcKiller", "dadb connect try $host:$port")
            val d = Dadb.create(
                host = host,
                port = port,
                keyPair = keyPair,
                connectTimeout = 1500,
                socketTimeout = socketTimeoutMs
            )
            val ok = try {
                d.shell("echo __adb_ok__").allOutput.contains("__adb_ok__")
            } catch (t: Throwable) {
                android.util.Log.w("ProcKiller", "dadb shell failed $host:$port: ${t.message}")
                false
            }
            if (ok) {
                runCatching { dadb?.close() }
                dadb = d
                lastError = ""
                android.util.Log.i("ProcKiller", "dadb connect OK $host:$port")
                true
            } else {
                runCatching { d.close() }
                lastError = "ADB 认证未通过：请在弹出的『允许 USB 调试』点允许；若无弹窗，到开发者选项『撤销 USB 调试授权』后重连；或改用 Shizuku（推荐）"
                android.util.Log.w("ProcKiller", "dadb auth failed $host:$port")
                false
            }
        } catch (t: Throwable) {
            lastError = "连接失败：${t.message ?: t.javaClass.simpleName}"
            android.util.Log.w("ProcKiller", "dadb connect error $host:$port: ${t.message}")
            false
        }
    }

    override fun isReady(): Boolean = dadb != null

    override suspend fun run(cmd: String, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        var d = dadb
        if (d == null) {
            // 不在每次命令时做全量扫描，避免卡顿；仅尝试已保存地址快速重连一次
            val h = settings.adbHost
            val p = settings.adbPort
            if (h.isNotBlank() && tryConnect(h, p)) d = dadb
            else return@withContext ShellResult.error("无线 ADB 未连接")
        }
        try {
            val r = d!!.shell(cmd)
            ShellResult(r.exitCode, r.output, r.errorOutput)
        } catch (t: Throwable) {
            runCatching { dadb?.close() }
            dadb = null
            ShellResult.error("ADB 连接已断开: ${t.message}")
        }
    }

    override fun close() {
        runCatching { dadb?.close() }
        dadb = null
    }
}
