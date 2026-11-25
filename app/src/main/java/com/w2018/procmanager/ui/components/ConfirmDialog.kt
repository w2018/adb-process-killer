package com.w2018.procmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w2018.procmanager.ui.theme.C

data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmText: String = "确定",
    val cancelText: String = "取消",
    val danger: Boolean = true,
    val onConfirm: () -> Unit,
)

/** 二次确认控制器：所有破坏性操作统一走这里 */
class ConfirmState {
    var request by mutableStateOf<ConfirmRequest?>(null)
        private set

    fun show(
        title: String,
        message: String,
        confirmText: String = "确定",
        cancelText: String = "取消",
        danger: Boolean = true,
        onConfirm: () -> Unit,
    ) {
        request = ConfirmRequest(title, message, confirmText, cancelText, danger, onConfirm)
    }

    fun dismiss() {
        request = null
    }

    fun confirm() {
        val r = request ?: return
        request = null
        r.onConfirm()
    }
}

val LocalConfirm = staticCompositionLocalOf<ConfirmState> { error("ConfirmState not provided") }

@Composable
fun rememberConfirmState(): ConfirmState = remember { ConfirmState() }

@Composable
fun ConfirmProvider(state: ConfirmState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalConfirm provides state, content = content)
}

@Composable
fun ConfirmHost(state: ConfirmState) {
    val req = state.request ?: return
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = C.card,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, C.border),
            modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    req.title,
                    color = C.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    req.message,
                    color = C.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp),
                    lineHeight = 19.sp,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProcButton(
                        text = req.cancelText,
                        onClick = { state.dismiss() },
                        kind = BtnKind.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                    ProcButton(
                        text = req.confirmText,
                        onClick = { state.confirm() },
                        kind = if (req.danger) BtnKind.Danger else BtnKind.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
