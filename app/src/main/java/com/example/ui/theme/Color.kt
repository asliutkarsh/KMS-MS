package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Palette — Crisp Alabaster + Cobalt Teal + Slate Ink
val LightPrimary = Color(0xFF0D6E6E)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFCFF4F0)
val LightOnPrimaryContainer = Color(0xFF002020)

val LightSecondary = Color(0xFF3E5F63)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE0EEEF)
val LightOnSecondaryContainer = Color(0xFF142A2D)

val LightTertiary = Color(0xFFB45309)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFEF3C7)
val LightOnTertiaryContainer = Color(0xFF451A03)

val LightBackground = Color(0xFFF6F8FA)
val LightOnBackground = Color(0xFF0F172A)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0F172A)
val LightSurfaceVariant = Color(0xFFEDF2F7)
val LightOnSurfaceVariant = Color(0xFF475569)
val LightSurfaceContainerLow = Color(0xFFF8FAFC)
val LightSurfaceContainer = Color(0xFFF1F5F9)
val LightSurfaceContainerHigh = Color(0xFFE2E8F0)
val LightOutline = Color(0xFF94A3B8)
val LightOutlineVariant = Color(0xFFCBD5E1)
val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF7F1D1D)

// Dark Palette — Deep Slate Night + Luminous Teal + Crisp Ice
val DarkPrimary = Color(0xFF2DD4BF)
val DarkOnPrimary = Color(0xFF003733)
val DarkPrimaryContainer = Color(0xFF0A4F4B)
val DarkOnPrimaryContainer = Color(0xFFA7F3EC)

val DarkSecondary = Color(0xFF94B4B8)
val DarkOnSecondary = Color(0xFF132D30)
val DarkSecondaryContainer = Color(0xFF243B3F)
val DarkOnSecondaryContainer = Color(0xFFD0E7EA)

val DarkTertiary = Color(0xFFF59E0B)
val DarkOnTertiary = Color(0xFF3E2200)
val DarkTertiaryContainer = Color(0xFF593400)
val DarkOnTertiaryContainer = Color(0xFFFDE68A)

val DarkBackground = Color(0xFF0F1419)
val DarkOnBackground = Color(0xFFECF2F8)
val DarkSurface = Color(0xFF151C24)
val DarkOnSurface = Color(0xFFECF2F8)
val DarkSurfaceVariant = Color(0xFF1E2833)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkSurfaceContainerLow = Color(0xFF141B22)
val DarkSurfaceContainer = Color(0xFF19212A)
val DarkSurfaceContainerHigh = Color(0xFF232E3B)
val DarkOutline = Color(0xFF475569)
val DarkOutlineVariant = Color(0xFF273444)
val DarkError = Color(0xFFF87171)
val DarkOnError = Color(0xFF450A0A)
val DarkErrorContainer = Color(0xFF7F1D1D)
val DarkOnErrorContainer = Color(0xFFFECACA)

// True AMOLED Black Palette — Pure #000000 OLED Canvas + Hairline Borders
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF000000)
val AmoledSurfaceContainerLow = Color(0xFF080B0F)
val AmoledSurfaceContainer = Color(0xFF0C1117)
val AmoledSurfaceContainerHigh = Color(0xFF141B24)
val AmoledSurfaceVariant = Color(0xFF111820)
val AmoledOutlineVariant = Color(0xFF1E293B)

val LightKmsColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

val DarkKmsColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

val AmoledKmsColorScheme = DarkKmsColorScheme.copy(
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceVariant = AmoledSurfaceVariant,
    outlineVariant = AmoledOutlineVariant
)

@Immutable
data class KmsSemanticColors(
    val creditGreen: Color,
    val creditGreenContainer: Color,
    val debitCoral: Color,
    val debitCoralContainer: Color,
    val otpAccent: Color,
    val otpContainer: Color,
    val offerAmber: Color,
    val offerContainer: Color,
    val starGold: Color,
    val cardBorder: Color
)

val LightSemanticColors = KmsSemanticColors(
    creditGreen = Color(0xFF059669),
    creditGreenContainer = Color(0xFFD1FAE5),
    debitCoral = Color(0xFFDC2626),
    debitCoralContainer = Color(0xFFFEE2E2),
    otpAccent = Color(0xFF0284C7),
    otpContainer = Color(0xFFE0F2FE),
    offerAmber = Color(0xFFD97706),
    offerContainer = Color(0xFFFEF3C7),
    starGold = Color(0xFFEAB308),
    cardBorder = Color(0xFFE2E8F0)
)

val DarkSemanticColors = KmsSemanticColors(
    creditGreen = Color(0xFF34D399),
    creditGreenContainer = Color(0xFF064E3B),
    debitCoral = Color(0xFFF87171),
    debitCoralContainer = Color(0xFF4C1111),
    otpAccent = Color(0xFF38BDF8),
    otpContainer = Color(0xFF0C3650),
    offerAmber = Color(0xFFFBBF24),
    offerContainer = Color(0xFF452800),
    starGold = Color(0xFFFACC15),
    cardBorder = Color(0xFF273444)
)

val AmoledSemanticColors = DarkSemanticColors.copy(
    cardBorder = Color(0xFF1E293B)
)

val LocalKmsSemanticColors = staticCompositionLocalOf { LightSemanticColors }
