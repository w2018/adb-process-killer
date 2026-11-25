package com.w2018.procmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.w2018.procmanager.ui.AppRoot
import com.w2018.procmanager.ui.theme.ProcTheme
import com.w2018.procmanager.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeState.mode = (application as ProcApp).settings.themeMode
        setContent {
            ProcTheme {
                AppRoot()
            }
        }
    }
}
