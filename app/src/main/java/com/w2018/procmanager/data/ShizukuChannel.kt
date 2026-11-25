package com.w2018.procmanager.data

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * 通过 Shizuku 授权（shell 权限 / ADB 级）执行命令。
 *
 * 说明：Shizuku 13.x 中 `Shizuku.newProcess(String[],String[],String)` 为私有方法，
 * 官方未公开 shell 执行入口，因此这里用反射调用（仅调用自身依赖库的私有静态方法，安全）。
 */
class ShizukuChannel(private val context: Context) : ShellExecutor {

    override val id: String = "shizuku"
    override val label: String = "Shizuku"

    private val newProcess: Method? by lazy {
        try {
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                .apply { isAccessible = true }
        } catch (t: Throwable) {
            null
        }
    }

    /** Shizuku 服务是否存活（已安装并运行） */
    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** 是否已获得 Shizuku 授权 */
    fun hasPermission(): Boolean {
        if (!isBinderAlive()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
    }

    override fun isReady(): Boolean = hasPermission() && newProcess != null

    /** 供 UI 请求授权（需在 Activity 环境调用，结果经 addRequestPermissionResultListener 回调） */
    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Throwable) {
        }
    }

    override suspend fun run(cmd: String, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        val m = newProcess ?: return@withContext ShellResult.error("Shizuku 通道不可用")
        if (!hasPermission()) return@withContext ShellResult.error("Shizuku 未授权")
        try {
            val process = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val outDeferred = async(Dispatchers.IO) {
                runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }
            val errDeferred = async(Dispatchers.IO) {
                runCatching { process.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }
            val finished = runCatching { process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
                .getOrDefault(false)
            if (!finished) {
                runCatching { process.destroy() }
                return@withContext ShellResult(-2, outDeferred.await(), "执行超时")
            }
            ShellResult(process.exitValue(), outDeferred.await(), errDeferred.await())
        } catch (t: Throwable) {
            ShellResult.error("Shizuku 执行失败: ${t.message}")
        }
    }
}
