package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

val KmsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KmsMsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val colorScheme = when (themeMode) {
        ThemeMode.AMOLED -> AmoledKmsColorScheme
        ThemeMode.DARK -> DarkKmsColorScheme
        ThemeMode.LIGHT -> LightKmsColorScheme
        ThemeMode.SYSTEM -> if (systemDark) DarkKmsColorScheme else LightKmsColorScheme
    }

    val semanticColors = when (themeMode) {
        ThemeMode.AMOLED -> AmoledSemanticColors
        ThemeMode.DARK -> DarkSemanticColors
        ThemeMode.LIGHT -> LightSemanticColors
        ThemeMode.SYSTEM -> if (systemDark) DarkSemanticColors else LightSemanticColors
    }

    val typography = remember(fontScale) { buildKmsTypography(fontScale) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
            }
        }
    }

    CompositionLocalProvider(LocalKmsSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = KmsShapes,
            content = content
        )
    }
}
