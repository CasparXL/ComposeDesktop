package com.caspar.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import caspardesktop.composeapp.generated.resources.Res
import caspardesktop.composeapp.generated.resources.compose_multiplatform
import com.caspar.compose.pages.SideBarApp
import org.jetbrains.compose.resources.painterResource

fun main() {
    // 修改渲染模式解决界面抖动问题(一般发生在高分辨率的情况下)
    System.setProperty("skiko.renderApi", "OPENGL")
    application {
        MaterialTheme {
            Window(
                onCloseRequest = ::exitApplication,
                title = "ComposeDesktop",
                icon = painterResource(Res.drawable.compose_multiplatform),
                state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center))
            ) {
                SideBarApp()
            }
        }
    }
}