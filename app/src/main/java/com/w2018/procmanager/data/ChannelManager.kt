package com.w2018.procmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChannelType { NONE, SHIZUKU, ADB }

/** 权限通道状态，供 UI 展示。 */
data class ChannelState(
    val type: ChannelType = ChannelType.NONE,
    val label: String = "未连接",
    val detail: String = "尚未连接权限通道",
    val ready: Boolean = false
)

/**
 * 权限通道管理器：优先 Shizuku，失败自动降级无线 ADB。
 * 统一对外提供 [run] 执行 shell。
 */
class ChannelManager(
    private val context: Context,
    private val settings: SettingsStore
) {
    val shizuku = ShizukuChannel(context)
    val adb = AdbChannel(context, settings)

    private val _state = MutableStateFlow(ChannelState())
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    @Volatile
    private var active: ShellExecutor? = null

    fun current(): ShellExecutor? = active

    private fun publish(type: ChannelType, label: String, detail: String, ready: Boolean): ChannelState {
        val s = ChannelState(type, label, detail, ready)
        _state.value = s
        if (ready) settings.lastChannel = type.name.lowercase()
        return s
    }

    // ---- Shizuku 状态 ----
    fun shizukuInstalled(): Boolean = shizuku.isBinderAlive()
    fun shizukuAuthorized(): Boolean = shizuku.hasPermission()
    fun shizukuReady(): Boolean = shizuku.isReady()
    fun requestShizukuPermission(requestCode: Int) = shizuku.requestPermission(requestCode)

    fun shizukuStatusText(): String = when {
        !shizukuInstalled() -> "未安装或未运行 Shizuku"
        !shizukuAuthorized() -> "Shizuku 等待授权"
        else -> "Shizuku 已授权"
    }

    /** 记录 Shizuku 授权状态（供 UI 展示），不切换 active */
    fun shizukuStatus(): ChannelState = when {
        !shizukuInstalled() -> publish(ChannelType.NONE, "Shizuku", "未安装或未运行 Shizuku", false)
        !shizukuAuthorized() -> publish(ChannelType.NONE, "Shizuku", "Shizuku 等待授权", false)
        else -> publish(ChannelType.SHIZUKU, "Shizuku", "Shizuku 已授权", true)
    }

    /** 自动连接：优先 Shizuku，失败降级 ADB。会先发布「连接中」状态。 */
    suspend fun autoConnect(): ChannelState {
        publish(ChannelType.NONE, "连接中…", "正在查找权限通道", false)
        android.util.Log.i("ProcKiller", "autoConnect: shizukuInstalled=${shizuku.isBinderAlive()} auth=${shizuku.hasPermission()} ready=${shizuku.isReady()}")
        if (shizuku.isReady()) {
            active = shizuku
            return publish(ChannelType.SHIZUKU, "Shizuku", "已通过 Shizuku 获得 shell 权限", true)
        }
        // Shizuku 已安装但未授权：先引导授权，不盲目去连 ADB（避免无谓的认证失败与卡顿）
        if (shizuku.isBinderAlive() && !shizuku.hasPermission()) {
            active = null
            return publish(ChannelType.NONE, "Shizuku 待授权", "请在 Shizuku 中允许本应用使用，或到设置点『授权』", false)
        }
        val adbOk = adb.connectAuto()
        android.util.Log.i("ProcKiller", "autoConnect: adb connectAuto=$adbOk lastError=${adb.lastError}")
        if (adbOk) {
            active = adb
            return publish(ChannelType.ADB, "无线 ADB", "已连接 ${settings.adbHost}:${settings.adbPort}", true)
        }
        active = null
        val detail = when {
            !shizuku.isBinderAlive() -> "无 Shizuku，且未发现可连接的无线 ADB（${adb.lastError}）"
            !shizuku.hasPermission() -> "Shizuku 未授权，且无线 ADB 失败（${adb.lastError}）"
            else -> "未发现可连接的无线 ADB（${adb.lastError}）"
        }
        return publish(ChannelType.NONE, "未连接", detail, false)
    }

    /** 仅用 Shizuku */
    fun connectShizukuOnly(): ChannelState {
        if (shizuku.isReady()) {
            active = shizuku
            return publish(ChannelType.SHIZUKU, "Shizuku", "已通过 Shizuku 获得 shell 权限", true)
        }
        active = null
        return shizukuStatus()
    }

    /** 扫描本机开放中的 ADB 端口（快速 TCP 探测）。 */
    suspend fun scanAdbPorts(): List<Int> {
        publish(ChannelType.NONE, "扫描中…", "正在扫描本机 ADB 端口", false)
        return adb.scanLocal()
    }

    /** 仅用无线 ADB：不指定则扫描本机端口 */
    suspend fun connectAdbOnly(host: String? = null, port: Int? = null): ChannelState {
        publish(ChannelType.NONE, "连接中…", "正在连接无线 ADB", false)
        val ok = if (host != null && port != null && port > 0) adb.connect(host, port) else adb.connectAuto()
        return if (ok) {
            active = adb
            publish(ChannelType.ADB, "无线 ADB", "已连接 ${settings.adbHost}:${settings.adbPort}", true)
        } else {
            active = null
            val reason = adb.lastError.ifBlank { "未发现可连接的无线 ADB" }
            publish(ChannelType.NONE, "无线 ADB", reason, false)
        }
    }

    fun disconnect() {
        adb.close()
        active = null
        publish(ChannelType.NONE, "未连接", "已断开权限通道", false)
    }

    /** 统一执行入口。未连接直接返回错误，不再被动触发全量扫描（避免卡顿）。 */
    suspend fun run(cmd: String, timeoutMs: Long = 20000): ShellResult {
        val ex = active ?: return ShellResult.error("未连接权限通道，请先在设置中连接")
        // 注意：单条命令失败（超时/非零退出）不代表通道断开，不能在此重置通道状态，
        // 否则周期刷新时的偶发失败会让 UI 反复从「已连接」跳回「未连接」。
        return ex.run(cmd, timeoutMs)
    }
}
