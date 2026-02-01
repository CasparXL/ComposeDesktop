package com.caspar.compose.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 定义主页菜单项
 */
sealed class MenuItem(val router: String, val title: String, val icon: ImageVector) {
    object Home : MenuItem(router = "home", title = "主页", icon = Icons.Default.Dashboard)
    object Profile : MenuItem(router = "profile", title = "个人信息", icon = Icons.Default.Person)
    object Settings : MenuItem(router = "settings", title = "设置", icon = Icons.Default.Settings)
}