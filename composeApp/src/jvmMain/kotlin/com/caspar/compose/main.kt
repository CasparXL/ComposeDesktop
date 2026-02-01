package com.caspar.compose

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import caspardesktop.composeapp.generated.resources.Res
import caspardesktop.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource


fun main() {
    // 修改渲染模式解决界面抖动问题(一般发生在高分辨率的情况下)
    System.setProperty("skiko.renderApi", "OPENGL")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ComposeDesktop",
            icon = painterResource(Res.drawable.compose_multiplatform)
        ) {
            App()
        }
    }
}