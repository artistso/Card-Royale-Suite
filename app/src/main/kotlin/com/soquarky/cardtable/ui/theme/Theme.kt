package com.soquarky.cardtable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF68D6B0),
    onPrimary = Color(0xFF00382B),
    secondary = Color(0xFFF2C36B),
    onSecondary = Color(0xFF402D00),
    tertiary = Color(0xFFB8C8FF),
    background = Color(0xFF071A16),
    onBackground = Color(0xFFE2F3EC),
    surface = Color(0xFF102923),
    onSurface = Color(0xFFE2F3EC),
    surfaceVariant = Color(0xFF24463D),
    onSurfaceVariant = Color(0xFFC2D8CF),
    error = Color(0xFFFFB4AB),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF006B52),
    onPrimary = Color.White,
    secondary = Color(0xFF735B17),
    onSecondary = Color.White,
    background = Color(0xFFF4FBF7),
    onBackground = Color(0xFF10201B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10201B),
    surfaceVariant = Color(0xFFD9E9E1),
    onSurfaceVariant = Color(0xFF3F4945),
)

@Composable
fun CardTableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
