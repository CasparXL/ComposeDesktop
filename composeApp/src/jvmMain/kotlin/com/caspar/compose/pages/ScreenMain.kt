package com.caspar.compose.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.caspar.compose.pages.home.HomeScreen
import com.caspar.compose.pages.profile.ProfileScreen
import com.caspar.compose.pages.settings.SettingsScreen

@Composable
fun SideBarApp() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: MenuItem.Home.router

    Row(Modifier.fillMaxSize()) {
        SideBar(
            items = listOf(MenuItem.Home, MenuItem.Profile, MenuItem.Settings),
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )

        Surface(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = MenuItem.Home.router
            ) {
                composable(MenuItem.Home.router) { HomeScreen() }
                composable(MenuItem.Profile.router) { ProfileScreen() }
                composable(MenuItem.Settings.router) { SettingsScreen() }
            }
        }
    }
}

@Composable
fun SideBar(
    items: List<MenuItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(50.dp, 150.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colors.surface)
    ) {
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            MenuItemButton(
                label = item.title,
                icon = item.icon,
                isSelected = currentRoute == item.router,
                onClick = { onNavigate(item.router) }
            )
        }
    }
}

@Composable
fun MenuItemButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
        )
    }
}