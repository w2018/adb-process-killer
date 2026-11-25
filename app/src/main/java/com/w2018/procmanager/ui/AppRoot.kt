package com.w2018.procmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import com.w2018.procmanager.ui.screens.DeviceInfoScreen
import com.w2018.procmanager.ui.screens.LogsScreen
import com.w2018.procmanager.ui.screens.RankScreen
import com.w2018.procmanager.ui.screens.RulesScreen
import com.w2018.procmanager.ui.screens.ScanScreen
import com.w2018.procmanager.ui.screens.SettingsScreen
import com.w2018.procmanager.ui.theme.C
import com.w2018.procmanager.ui.theme.ScaledContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASH("概览", Icons.Filled.Home),
    APPS("应用", Icons.Filled.Apps),
    RULES("规则", Icons.Filled.Tune),
    RANK("排行榜", Icons.Filled.Star),
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

    // 底部可切换的 4 个主页（设置页改为从首页右上角进入，不再占底部 Tab）
    val pages = remember { listOf(Tab.DASH, Tab.APPS, Tab.RULES, Tab.RANK) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    var tab by remember { mutableStateOf(Tab.DASH) }
    var appsFilter by rememberSaveable { mutableStateOf("all") }
    var rankCat by rememberSaveable { mutableIntStateOf(0) }
    var rankPeriod by rememberSaveable { mutableStateOf("today") }
    var sheetPkg by remember { mutableStateOf<String?>(null) }
    var logFilterReq by remember { mutableStateOf<String?>(null) }
    var showLogs by remember { mutableStateOf(false) }
    var showDevice by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    fun selectTab(t: Tab) {
        val i = pages.indexOf(t)
        if (i >= 0) scope.launch {
            pagerState.animateScrollToPage(i, animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
        }
    }

    // 层级滑动：优先切换当前页的「子分类」，滑到子分类边界后再切换主 Tab
    val appsFilters = remember { listOf("all", "user", "sys", "run", "frozen", "auto", "rule") }
    val rankCatCount = 3
    fun movePage(dir: Int) {
        val t = pagerState.currentPage + dir
        if (t in pages.indices) scope.launch { pagerState.animateScrollToPage(t) }
    }
    val pagerSwipe = Modifier.pointerInput(tab) {
        val thr = viewConfiguration.touchSlop * 2f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var acc = 0f
            var done = false
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                acc += ch.positionChange().x
                if (!done && abs(acc) > thr) {
                    val dir = if (acc < 0f) 1 else -1
                    when (tab) {
                        Tab.APPS -> {
                            val i = appsFilters.indexOf(appsFilter).coerceAtLeast(0)
                            val n = i + dir
                            if (n in appsFilters.indices) appsFilter = appsFilters[n] else movePage(dir)
                        }
                        Tab.RANK -> {
                            if (rankCat == 1) {
                                // 自启排行页：先切换时间段子分类，到边界再切类别，再到边界切主 Tab
                                val periods = listOf("today", "yesterday", "week", "month")
                                val i = periods.indexOf(rankPeriod).coerceAtLeast(0)
                                val n = i + dir
                                if (n in periods.indices) rankPeriod = periods[n]
                                else {
                                    val nc = rankCat + dir
                                    if (nc in 0 until rankCatCount) rankCat = nc else movePage(dir)
                                }
                            } else {
                                val n = rankCat + dir
                                if (n in 0 until rankCatCount) rankCat = n else movePage(dir)
                            }
                        }
                        else -> movePage(dir)
                    }
                    done = true
                    ch.consume()
                }
                if (done) ch.consume()
            }
        }
    }

    // 左右滑动切换：Pager 负责手势（子页面内横向滚动会优先消费手势），滑动到哪页 Tab 同步到哪页
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { p -> tab = pages.getOrElse(p) { Tab.DASH } }
    }

    LaunchedEffect(Unit) {
        ui.observe(scope)
        if (app.settings.guardian) runCatching { GuardService.start(context) }
        ui.ensureShizuku()
        ui.autoConnect()
        ui.refresh()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                activity?.let { androidx.core.app.ActivityCompat.requestPermissions(it, arrayOf(perm), 101) }
            }
        }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let { m -> toast.show(m); ui.message = null }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
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
            showLogs -> showLogs = false
            showDevice -> showDevice = false
            showSettings -> showSettings = false
            sheetPkg != null -> sheetPkg = null
            tab != Tab.DASH -> selectTab(Tab.DASH)
            else -> activity?.finish()
        }
    }

    ToastProvider(toast) {
        ConfirmProvider(confirm) {
            ScaledContent {
                Box(Modifier.fillMaxSize().background(C.bg)) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.weight(1f).fillMaxWidth().then(pagerSwipe)) { page ->
                            Box(
                                Modifier.fillMaxSize().graphicsLayer {
                                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    val f = abs(offset).coerceIn(0f, 1f)
                                    alpha = 1f - f * 0.35f
                                    translationX = size.width * offset * 0.14f
                                }
                            ) {
                                when (pages[page]) {
                                    Tab.DASH -> DashScreen(ui, onOpenSettings = { showSettings = true }, onOpenLogs = { f -> logFilterReq = f; showLogs = true }, onOpenScan = { scanning = true }, onOpenDevice = { showDevice = true })
                                    Tab.APPS -> AppsScreen(ui, appsFilter, { appsFilter = it }) { sheetPkg = it }
                                    Tab.RULES -> RulesScreen(ui)
                                    Tab.RANK -> RankScreen(ui, rankCat, { rankCat = it }, rankPeriod, { rankPeriod = it }) { sheetPkg = it }
                                    else -> {}
                                }
                            }
                        }
                        ProcTabBar(current = tab, rulesDirty = ui.rulesDirty, onSelect = { selectTab(it) })
                    }
                    // 设置页（全屏覆盖，从首页右上角进入）
                    if (showSettings) {
                        Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
                            SettingsScreen(ui)
                        }
                    }
                    if (showLogs) {
                        Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
                            LogsScreen(ui, logFilterReq, onClose = { showLogs = false }) { logFilterReq = null }
                        }
                    }
                    if (showDevice) {
                        Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
                            DeviceInfoScreen(ui) { showDevice = false }
                        }
                    }
                    sheetPkg?.let { AppDetailSheet(ui, it) { sheetPkg = null } }
                    if (scanning) ScanScreen(ui) { scanning = false }
                    ToastHost(toast)
                    ConfirmHost(confirm)
                }
            }
        }
    }
}

@Composable
private fun ProcTabBar(current: Tab, rulesDirty: Boolean = false, onSelect: (Tab) -> Unit) {
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
                Box {
                    Icon(
                        t.icon, contentDescription = t.label, tint = tint,
                        modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                    if (t == Tab.RULES && rulesDirty) {
                        Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(C.danger, RoundedCornerShape(50)))
                    }
                }
                Text(
                    t.label, color = tint, fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
