package com.w2018.procmanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.ui.theme.C
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 自定义 Toast 控制器（非系统原生 Toast，风格与项目一致） */
class ToastState(private val scope: kotlinx.coroutines.CoroutineScope) {
    var message by mutableStateOf("")
        private set
    var visible by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun show(msg: String, durationMs: Long = 2200) {
        message = msg
        visible = true
        job?.cancel()
        job = scope.launch {
            delay(durationMs)
            visible = false
        }
    }
}

val LocalToast = staticCompositionLocalOf<ToastState> { error("ToastState not provided") }

@Composable
fun rememberToastState(): ToastState {
    val scope = rememberCoroutineScope()
    return remember { ToastState(scope) }
}

@Composable
fun ToastHost(state: ToastState) {
    Box(
        Modifier.fillMaxSize().padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = state.visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
        ) {
            Surface(
                color = Color(0xFF263244),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, C.border),
            ) {
                Text(
                    text = state.message,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
fun ToastProvider(state: ToastState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalToast provides state, content = content)
}
