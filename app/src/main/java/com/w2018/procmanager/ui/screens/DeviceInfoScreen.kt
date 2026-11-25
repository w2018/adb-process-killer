package com.w2018.procmanager.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.ui.AppUi
import com.w2018.procmanager.ui.components.Ic
import com.w2018.procmanager.ui.components.IconBtn
import com.w2018.procmanager.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 设备完整参数（一次性采集）。 */
private data class DevInfo(
    // 概览
    val brand: String = "", val manufacturer: String = "", val model: String = "",
    val device: String = "", val product: String = "", val board: String = "",
    val androidVer: String = "", val sdk: Int = 0, val abi: String = "",
    val kernel: String = "", val uptime: String = "",
    // 屏幕
    val widthPx: Int = 0, val heightPx: Int = 0, val widthDp: Int = 0, val heightDp: Int = 0,
    val densityDpi: Int = 0, val xdpi: Float = 0f, val ydpi: Float = 0f, val diagonalIn: Float = 0f,
    val refreshRate: String = "", val densityBucket: String = "", val fontScale: String = "",
    // CPU
    val cpuCores: Int = 0, val threads: Int = 0, val cpuModel: String = "",
    val freqMinGhz: String = "", val freqMaxGhz: String = "", val arch: String = "",
    val gpu: String = "", val cpuClusters: String = "",
    // RAM
    val ramTotalKb: Long = 0, val ramAvailKb: Long = 0, val swapTotalKb: Long = 0, val swapFreeKb: Long = 0,
    val ramType: String = "",
    // 存储
    val storeTotal: Long = 0, val storeAvail: Long = 0, val storeVendor: String = "",
    val readSpeed: String = "", val writeSpeed: String = "",
    // 网络
    val netType: String = "", val operator: String = "", val ipv4: String = "", val ipv6: String = "",
    val publicIp: String = "", val publicIp6: String = "",
    // 电池
    val batteryPct: Int = 0, val batteryTemp: String = "", val batteryHealth: String = "", val batteryVolt: String = "",
    val batteryHealthPct: String = "", val batteryHealthVerdict: String = "", val batteryCycles: String = "",
    val loading: Boolean = true,
)

private fun gb(bytes: Long): String = String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)

@Composable
fun DeviceInfoScreen(ui: AppUi, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf(DevInfo()) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        info = DevInfo(loading = true)
        info = withContext(Dispatchers.IO) { loadDevice(ctx, ui) }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn("back") { onClose() }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("设备信息", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("完整硬件与系统参数 · 实时采集", color = C.text2, fontSize = 11.sp)
            }
            IconBtn("refresh") { reload++ }
        }
        if (info.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在采集设备参数…", color = C.muted, fontSize = 12.sp)
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 设备概览
            InfoCard("设备概览", "info") {
                kv("品牌", info.brand); kv("厂商", info.manufacturer); kv("型号", info.model)
                kv("设备代号", info.device); kv("产品", info.product); kv("主板", info.board)
                kv("系统版本", "Android ${info.androidVer}（API ${info.sdk}）")
                kv("指令集", info.abi); kv("内核", info.kernel); kv("已开机", info.uptime)
            }
            // 屏幕
            InfoCard("屏幕", "screen") {
                kv("分辨率", "${info.widthPx} × ${info.heightPx} px")
                kv("逻辑尺寸", "${info.widthDp} × ${info.heightDp} dp")
                kv("像素密度", "${info.densityDpi} dpi（xdpi ${info.xdpi.roundToInt()} / ydpi ${info.ydpi.roundToInt()}）")
                kv("屏幕对角线", String.format("%.2f 英寸", info.diagonalIn))
                kv("屏幕比例", String.format("%.2f : 1", if (info.widthDp > 0) info.heightDp.toFloat() / info.widthDp else 0f))
                kv("刷新率", info.refreshRate)
                kv("密度等级", info.densityBucket)
                kv("字体缩放", info.fontScale)
            }
            // CPU
            InfoCard("处理器", "cpu") {
                kv("型号", info.cpuModel); kv("架构", info.arch)
                kv("核心数", "${info.cpuCores} 核"); kv("线程数", "${info.threads} 线程")
                kv("频率区间", "${info.freqMinGhz} ~ ${info.freqMaxGhz}")
                kv("核心频率簇", info.cpuClusters)
                kv("GPU", info.gpu)
            }
            // RAM
            val ramUsedKb = (info.ramTotalKb - info.ramAvailKb).coerceAtLeast(0)
            val ramPct = if (info.ramTotalKb > 0) ramUsedKb.toFloat() / info.ramTotalKb * 100f else 0f
            InfoCard("运行内存（RAM）", "ram") {
                UsageDonut(ramPct, String.format("%.0f%%", ramPct), "已使用", C.accent)
                Spacer(Modifier.height(10.dp))
                kv("总容量", kbToStr(info.ramTotalKb)); kv("已使用", kbToStr(ramUsedKb))
                kv("未使用", kbToStr(info.ramAvailKb))
                kv("Swap", "${kbToStr(info.swapTotalKb)}（空闲 ${kbToStr(info.swapFreeKb)}）")
                kv("内存类型", info.ramType)
            }
            // 存储
            val storeUsed = (info.storeTotal - info.storeAvail).coerceAtLeast(0)
            val storePct = if (info.storeTotal > 0) storeUsed.toFloat() / info.storeTotal * 100f else 0f
            InfoCard("存储空间", "storage") {
                UsageDonut(storePct, String.format("%.0f%%", storePct), "已使用", C.blue)
                Spacer(Modifier.height(10.dp))
                kv("总容量", gb(info.storeTotal)); kv("已用", gb(storeUsed)); kv("剩余", gb(info.storeAvail))
                kv("闪存厂商", info.storeVendor)
                kv("读取速度", info.readSpeed); kv("写入速度", info.writeSpeed)
            }
            // 网络
            InfoCard("网络", "network") {
                kv("网络类型", info.netType); kv("运营商", info.operator)
                kv("内网 IPv4", info.ipv4); kv("内网 IPv6", info.ipv6)
                kv("外网 IPv4", info.publicIp); kv("外网 IPv6", info.publicIp6)
            }
            // 电池与系统
            InfoCard("电池与系统", "battery") {
                kv("电量", "${info.batteryPct}%"); kv("温度", info.batteryTemp)
                kv("电压", info.batteryVolt)
                kv("健康度", info.batteryHealthPct + (if (info.batteryHealthVerdict.isNotBlank()) "（${info.batteryHealthVerdict}）" else ""))
                kv("状态", info.batteryHealth); kv("循环次数", info.batteryCycles)
            }
        }
    }
}

private fun kbToStr(kb: Long): String =
    if (kb >= 1024 * 1024) String.format("%.2f GB", kb / 1024.0 / 1024.0)
    else if (kb >= 1024) String.format("%.0f MB", kb / 1024.0) else "$kb KB"

@Composable
private fun InfoCard(title: String, icon: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 16, C.accent)
            Spacer(Modifier.width(7.dp))
            Text(title, color = C.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun kv(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = C.text2, fontSize = 11.5.sp, modifier = Modifier.width(76.dp))
        Text(value, color = C.text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/** 环形统计图。 */
@Composable
private fun UsageDonut(percent: Float, centerTop: String, centerSub: String, color: Color) {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(90.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = C.card2, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * (percent / 100f).coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(centerSub, color = C.text2, fontSize = 9.5.sp)
        }
    }
}

// ---------- 采集 ----------

private suspend fun loadDevice(ctx: Context, ui: AppUi): DevInfo {
    val dm = ctx.resources.displayMetrics
    val wPx = dm.widthPixels; val hPx = dm.heightPixels
    val xdpi = if (dm.xdpi > 0) dm.xdpi else dm.densityDpi.toFloat()
    val ydpi = if (dm.ydpi > 0) dm.ydpi else dm.densityDpi.toFloat()
    val diag = sqrt((wPx / xdpi).toDouble().pow2() + (hPx / ydpi).toDouble().pow2())

    // RAM
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
    // Swap / cpuinfo / freq / kernel / storage vendor 通过 shell 读取
    val meminfo = runCatching { ui.sh("cat /proc/meminfo") }.getOrDefault("")
    val swapTotal = regexKb(meminfo, "SwapTotal")
    val swapFree = regexKb(meminfo, "SwapFree")
    val cpuinfo = runCatching { ui.sh("cat /proc/cpuinfo") }.getOrDefault("")
    val threads = Regex("(?m)^processor\\s*:").findAll(cpuinfo).count().let { if (it > 0) it else Runtime.getRuntime().availableProcessors() }
    val cpuModel = Regex("Hardware\\s*:\\s*(.+)").find(cpuinfo)?.groupValues?.get(1)?.trim()
        ?: Regex("model name\\s*:\\s*(.+)").find(cpuinfo)?.groupValues?.get(1)?.trim() ?: Build.HARDWARE
    val cpuCores = runCatching {
        File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size
    }.getOrNull()?.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()
    val fMin = runCatching { ui.sh("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq").trim().toLong() }.getOrDefault(0L)
    val fMax = runCatching { ui.sh("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").trim().toLong() }.getOrDefault(0L)
    // 各核心最高频率 → 按频率分组，得到大小核频率簇（如 4×1.80GHz + 4×2.40GHz）
    val freqRaw = runCatching { ui.sh("for f in /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq; do cat \$f 2>/dev/null; echo; done") }.getOrDefault("")
    val freqGroups = freqRaw.lines().mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
        .groupingBy { it }.eachCount().entries.sortedBy { it.key }
    val cpuClusters = if (freqGroups.isNotEmpty())
        freqGroups.joinToString(" + ") { "${it.value}×" + String.format("%.2fGHz", it.key / 1_000_000.0) }
    else "系统未公开"
    // GPU：EGL 厂商/型号 + OpenGL ES 版本（ro.opengles.version 为 0xMMmmrrrr）
    val gpuEgl = runCatching { ui.sh("getprop ro.hardware.egl").trim() }.getOrDefault("")
    val gpuVk = runCatching { ui.sh("getprop ro.hardware.vulkan").trim() }.getOrDefault("")
    // ro.opengles.version 是十进制整数（0xMMmm 编码，major<<16 | minor）
    val glesVal = runCatching { ui.sh("getprop ro.opengles.version").trim().toLongOrNull() }.getOrNull() ?: 0L
    val glesVer = if (glesVal > 0) "OpenGL ES ${(glesVal shr 16) and 0xffff}.${glesVal and 0xffff}" else ""
    val gpu = listOf(gpuEgl.ifBlank { gpuVk }, glesVer).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "系统未公开" }
    val kernel = runCatching { ui.sh("cat /proc/version").trim() }.getOrDefault(System.getProperty("os.version") ?: "")
        .let { if (it.length > 60) it.substring(0, 60) + "…" else it }

    // 屏幕刷新率/密度等级/字体缩放
    val refreshHz = runCatching {
        val dm2 = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        dm2.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f
    }.getOrDefault(0f)
    val refreshRate = if (refreshHz > 0f) String.format("%.0f Hz", refreshHz) else "系统未公开"
    val bucket = when {
        dm.densityDpi >= 640 -> "xxxhdpi 及以上"
        dm.densityDpi >= 480 -> "xxhdpi"
        dm.densityDpi >= 320 -> "xhdpi"
        dm.densityDpi >= 240 -> "hdpi"
        dm.densityDpi >= 160 -> "mdpi"
        else -> "ldpi"
    }
    val fontScale = String.format("%.2f（%.0f%%）", ctx.resources.configuration.fontScale, ctx.resources.configuration.fontScale * 100)

    // 存储
    val stat = StatFs(Environment.getDataDirectory().path)
    val storeTotal = stat.totalBytes; val storeAvail = stat.availableBytes
    val vendorRaw = runCatching {
        ui.sh("for b in mmcblk0 sda sdb dm-0; do v=\$(cat /sys/block/\$b/device/name 2>/dev/null || cat /sys/block/\$b/device/vendor 2>/dev/null); [ -n \"\$v\" ] && echo \"\$b=\$v\"; done")
    }.getOrDefault("").lines().map { it.trim() }.filter { it.contains("=") }.distinct().joinToString(" ; ")
    val storeVendor = vendorRaw.ifBlank { "系统未公开" }

    // 读写速度（快速基准，写读 8MB）
    val (rs, ws) = ioBenchmark(ctx)

    // 网络
    val netType = netTypeOf(ctx)
    val operator = runCatching { (ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkOperatorName }.getOrDefault("")
    val ips = localIps()
    val pubRaw4 = fetchPublicIp("https://api.ipify.org")
    val pubRaw64 = runCatching { fetchPublicIp("https://api64.ipify.org") }.getOrDefault("")
    val pubV6raw = runCatching { fetchPublicIp("https://api6.ipify.org") }.getOrDefault("")
    val pubV4 = when {
        isValidIpv4(pubRaw4) -> pubRaw4
        isValidIpv4(pubRaw64) -> pubRaw64
        else -> ""
    }
    val pubV6 = when {
        isValidIpv6(pubV6raw) -> pubV6raw
        isValidIpv6(pubRaw64) -> pubRaw64
        else -> ""
    }

    // 电池
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val bpct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val battIntent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val tempTenths = battIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val volt = battIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val health = when (battIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        else -> "未知"
    }
    // 电池健康度：charge_full / charge_full_design × 100（部分机型不开放则回退 uevent）
    val battRaw = runCatching { ui.sh("cat /sys/class/power_supply/battery/charge_full_design /sys/class/power_supply/battery/charge_full 2>/dev/null") }.getOrDefault("")
    val uevent = runCatching { ui.sh("cat /sys/class/power_supply/battery/uevent 2>/dev/null") }.getOrDefault("")
    val design = battRaw.lines().getOrNull(0)?.trim()?.toLongOrNull()
        ?: Regex("POWER_SUPPLY_CHARGE_FULL_DESIGN=(\\d+)").find(uevent)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val full = battRaw.lines().getOrNull(1)?.trim()?.toLongOrNull()
        ?: Regex("POWER_SUPPLY_CHARGE_FULL=(\\d+)").find(uevent)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val rawHealthPct = if (design > 0 && full > 0 && full <= design * 2) full * 100.0 / design else 0.0
    val healthPctStr = if (rawHealthPct > 0) String.format("%.0f%%", rawHealthPct) else "无法读取（系统未开放）"
    val verdict = when {
        rawHealthPct <= 0 -> ""
        rawHealthPct >= 90 -> "健康"
        rawHealthPct >= 80 -> "一般，建议关注"
        else -> "低于 80%，已达报废标准，建议更换"
    }
    val cyclesRaw = runCatching { ui.sh("cat /sys/class/power_supply/battery/cycle_count 2>/dev/null") }.getOrDefault("").trim()
    val cycles = cyclesRaw.toIntOrNull()?.let { "$it 次" } ?: "系统未公开"

    val uptimeMs = SystemClock.elapsedRealtime()
    val h = uptimeMs / 3_600_000; val m = (uptimeMs % 3_600_000) / 60_000
    val d = h / 24; val hh = h % 24
    val uptime = if (d > 0) "${d}天 ${hh}小时 ${m}分" else "${h}小时 ${m}分"

    return DevInfo(
        brand = Build.BRAND, manufacturer = Build.MANUFACTURER, model = Build.MODEL,
        device = Build.DEVICE, product = Build.PRODUCT, board = Build.BOARD,
        androidVer = Build.VERSION.RELEASE, sdk = Build.VERSION.SDK_INT,
        abi = Build.SUPPORTED_ABIS.joinToString(", "), kernel = kernel, uptime = uptime,
        widthPx = wPx, heightPx = hPx,
        widthDp = (wPx / dm.density).roundToInt(), heightDp = (hPx / dm.density).roundToInt(),
        densityDpi = dm.densityDpi, xdpi = xdpi, ydpi = ydpi, diagonalIn = diag.toFloat(),
        refreshRate = refreshRate, densityBucket = bucket, fontScale = fontScale,
        cpuCores = cpuCores, threads = threads, cpuModel = cpuModel,
        arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "",
        gpu = gpu, cpuClusters = cpuClusters,
        freqMinGhz = if (fMin > 0) String.format("%.2f GHz", fMin / 1_000_000.0) else "—",
        freqMaxGhz = if (fMax > 0) String.format("%.2f GHz", fMax / 1_000_000.0) else "—",
        ramTotalKb = mi.totalMem / 1024, ramAvailKb = mi.availMem / 1024,
        swapTotalKb = swapTotal, swapFreeKb = swapFree,
        ramType = "系统未公开",
        storeTotal = storeTotal, storeAvail = storeAvail, storeVendor = storeVendor,
        readSpeed = rs, writeSpeed = ws,
        netType = netType, operator = operator.ifBlank { "—" },
        ipv4 = ips.first.ifBlank { "—" }, ipv6 = ips.second.ifBlank { "—" },
        publicIp = pubV4.ifBlank { "获取失败" }, publicIp6 = pubV6.ifBlank { "不可用" },
        batteryVolt = if (volt > 0) String.format("%.2f V", volt / 1000.0) else "—",
        batteryPct = bpct, batteryTemp = String.format("%.1f ℃", tempTenths / 10.0),
        batteryHealth = health, batteryHealthPct = healthPctStr, batteryHealthVerdict = verdict, batteryCycles = cycles,
        loading = false,
    )
}

private fun Double.pow2() = this * this

private fun regexKb(text: String, key: String): Long =
    Regex("$key:\\s*(\\d+)\\s*kB").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

private fun ioBenchmark(ctx: Context): Pair<String, String> {
    return try {
        val size = 8 * 1024 * 1024
        val buf = ByteArray(size) { (it % 251).toByte() }
        val f = File(ctx.cacheDir, "io_bench.tmp")
        // write
        val t0 = System.nanoTime()
        f.outputStream().use { it.write(buf) }
        val ws = size / ((System.nanoTime() - t0) / 1e9) / 1024 / 1024
        // read
        val t1 = System.nanoTime()
        f.inputStream().use { it.read(buf) }
        val rs = size / ((System.nanoTime() - t1) / 1e9) / 1024 / 1024
        f.delete()
        String.format("%.0f MB/s", rs) to String.format("%.0f MB/s", ws)
    } catch (e: Exception) {
        "—" to "—"
    }
}

private fun netTypeOf(ctx: Context): String {
    return try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return "未连接"
        val caps = cm.getNetworkCapabilities(n) ?: return "未连接"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网络"
            else -> "其他"
        }
    } catch (e: Exception) { "未知" }
}

private val IPV4_RE = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")
private fun isValidIpv4(s: String): Boolean = IPV4_RE.matches(s.trim())

private fun isValidIpv6(s: String): Boolean {
    val t = s.trim()
    if (!t.contains(":") || t.contains(" ")) return false
    // 只允许十六进制与冒号，含折叠 :: 的判断交给 InetAddress 再解析一次
    if (!Regex("^[0-9a-fA-F:]+$").matches(t)) return false
    return try { java.net.InetAddress.getByName(t) is java.net.Inet6Address } catch (e: Exception) { false }
}

/** 返回 (内网IPv4, 内网IPv6)，仅接受合法地址，避免把非地址串误当 IP。 */
private fun localIps(): Pair<String, String> {
    var v4 = ""; var v6 = ""
    try {
        val ifaces = NetworkInterface.getNetworkInterfaces()
        for (ni in ifaces) {
            if (!ni.isUp || ni.isLoopback) continue
            for (addr in ni.inetAddresses) {
                if (addr.isLoopbackAddress) continue
                val a = (addr.hostAddress ?: continue).substringBefore("%")
                if (addr is java.net.Inet4Address && addr.isSiteLocalAddress && v4.isBlank() && isValidIpv4(a)) v4 = a
                else if (addr is java.net.Inet6Address && !a.lowercase().startsWith("fe80") && v6.isBlank() && isValidIpv6(a)) v6 = a
            }
        }
    } catch (_: Exception) {}
    return v4 to v6
}

private fun fetchPublicIp(urlStr: String): String {
    return try {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 2500; conn.readTimeout = 2500
        conn.requestMethod = "GET"
        conn.inputStream.bufferedReader().use { it.readText().trim() }
    } catch (e: Exception) { "" }
}
