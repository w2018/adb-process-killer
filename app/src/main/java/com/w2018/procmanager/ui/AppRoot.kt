package com.w2018.procmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.w2018.procmanager.ProcApp
import com.w2018.procmanager.service.GuardService
import com.w2018.procmanager.ui.components.ConfirmHost
import com.w2018.procmanager.ui.components.ConfirmProvider
import com.w2018.procmanager.ui.components.ToastHost
import com.w2018.procmanager.ui.components.ToastProvider
import com.w2018.procmanager.ui.components.rememberConfirmState
import com.w2018.procmanager.ui.components.rememberToastState
import com.w2018.procmanager.ui.screens.AppDetailSheet
import com.w2018.procmanager.ui.screens.AppsScreen
import com.w2018.procmanager.ui.screens.DashScreen
import com.w2018.procmanager.ui.screens.LogsScreen
import com.w2018.procmanager.ui.screens.RulesScreen
import com.w2018.procmanager.ui.screens.ScanScreen
import com.w2018.procmanager.ui.screens.SettingsScreen
import com.w2018.procmanager.ui.theme.C
import com.w2018.procmanager.ui.theme.ScaledContent
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASH("概览", Icons.Filled.Home),
    APPS("应用", Icons.Filled.Apps),
    RULES("规则", Icons.Filled.Tune),
    LOGS("日志", Icons.Filled.List),
    SET("设置", Icons.Filled.Settings),
}

@Composable
fun AppRoot() {
    val toast = rememberToastState()
    val confirm = rememberConfirmState()
    val context = LocalContext.current
    val app = context.applicationContext as ProcApp
    val ui = remember { AppUi(app) }
    val scope = rememberCoroutineScope()
    val activity = context as? android.app.Activity

    var tab by remember { mutableStateOf(Tab.DASH) }
    var sheetPkg by remember { mutableStateOf<String?>(null) }
    var logFilterReq by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ui.observe(scope)
        if (app.settings.guardian) runCatching { GuardService.start(context) }
        ui.ensureShizuku()
        ui.autoConnect()
        ui.refresh()
        // Android 13+ 运行时申请通知权限（前台保活通知与清理提醒依赖它）
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                activity?.let { androidx.core.app.ActivityCompat.requestPermissions(it, arrayOf(perm), 101) }
            }
        }
    }
    // 通道动作结果 → 自绘 Toast
    LaunchedEffect(ui.message) {
        ui.message?.let { m -> toast.show(m); ui.message = null }
    }
    // 前台实时刷新：界面处于前台(RESUMED)时每 2 秒采集一次，实时查看设备性能指标；
    // 退到后台自动停止，避免无谓耗电。
    val lifecycleOwner = LocalLifecycleOwner.current
    // 实时采集：仅在界面处于前台(RESUMED)时进行。概览页用 lite 快照 1.2s 高频刷新保证“实时感”；
    // 其余页面用完整快照 3.5s 刷新，避免高频重算导致列表滚动卡顿与设备额外负载。
    LaunchedEffect(tab) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val onDash = tab == Tab.DASH
                delay(if (onDash) 1200 else 3500)
                if (!ui.refreshing) runCatching { ui.refresh(lite = onDash) }
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            scanning -> scanning = false
            sheetPkg != null -> sheetPkg = null
            tab != Tab.DASH -> tab = Tab.DASH
            else -> activity?.finish()
        }
    }

    ToastProvider(toast) {
        ConfirmProvider(confirm) {
            ScaledContent {
                Box(Modifier.fillMaxSize().background(C.bg)) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                                label = "tabContent",
                            ) { t ->
                                when (t) {
                                    Tab.DASH -> DashScreen(ui, onOpenSettings = { tab = Tab.SET }, onOpenLogs = { f -> logFilterReq = f; tab = Tab.LOGS }, onOpenScan = { scanning = true })
                                    Tab.APPS -> AppsScreen(ui) { sheetPkg = it }
                                    Tab.RULES -> RulesScreen(ui)
                                    Tab.LOGS -> LogsScreen(ui, logFilterReq) { logFilterReq = null }
                                    Tab.SET -> SettingsScreen(ui)
                                }
                            }
                        }
                        ProcTabBar(current = tab, onSelect = { tab = it })
                    }
                    // 应用详情底部弹层（覆盖整个界面，包括底部导航）
                    sheetPkg?.let { AppDetailSheet(ui, it) { sheetPkg = null } }
                    // 扫描过程全屏界面（覆盖包括底部导航）
                    if (scanning) ScanScreen(ui) { scanning = false }
                    ToastHost(toast)
                    ConfirmHost(confirm)
                }
            }
        }
    }
}

@Composable
private fun ProcTabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).background(C.bg2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.filter { it != Tab.SET }.forEach { t ->
            val on = t == current
            val tint by animateColorAsState(if (on) C.accent else C.muted, animationSpec = tween(180), label = "tabTint")
            val iconScale by animateFloatAsState(if (on) 1.12f else 1f, animationSpec = tween(180), label = "tabScale")
            Column(
                Modifier.weight(1f).fillMaxSize().clickable { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    t.icon, contentDescription = t.label, tint = tint,
                    modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                )
                Text(
                    t.label, color = tint, fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
