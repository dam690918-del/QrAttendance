package com.college.qrattendance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Success = Color(0xFF2E7D4F)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BD3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0B1F5C),
    secondary = Color(0xFF4E6A8F),
    background = Color(0xFFF5F6FA),
    surface = Color(0xFFF5F6FA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC2FF),
    onPrimary = Color(0xFF0B2A80),
    primaryContainer = Color(0xFF223F99),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFB4C8E8)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
