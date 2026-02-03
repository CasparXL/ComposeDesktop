package com.caspar.compose.entry

import kotlinx.serialization.Serializable

@Serializable
data class UserPrefs(val theme: String = "light", val currentPage: String = "home")