package com.w2018.procmanager.data

/** 一次 shell 命令执行结果。exitCode < 0 表示通道层失败。 */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val ok: Boolean get() = exitCode == 0
    val output: String get() = if (stdout.isNotEmpty()) stdout else stderr

    companion object {
        fun error(msg: String) = ShellResult(-1, "", msg)
    }
}

/**
 * 统一 shell 执行通道抽象。实现有 [ShizukuChannel] 与 [AdbChannel]。
 */
interface ShellExecutor {
    /** 稳定标识：shizuku / adb */
    val id: String
    /** 展示名 */
    val label: String
    /** 当前是否已就绪（可立即执行命令） */
    fun isReady(): Boolean
    /** 执行命令（阻塞逻辑放到 IO 线程） */
    suspend fun run(cmd: String, timeoutMs: Long = 20000): ShellResult
    fun close() {}
}
