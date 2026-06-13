package com.familymeal.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SaffronPrimaryDark,
    onPrimary = OnSaffronPrimaryDark,
    primaryContainer = SaffronContainerDark,
    onPrimaryContainer = OnSaffronContainerDark,
    secondary = HerbSecondaryDark,
    onSecondary = OnHerbSecondaryDark,
    secondaryContainer = HerbContainerDark,
    onSecondaryContainer = OnHerbContainerDark,
    tertiary = TerracottaTertiaryDark,
    onTertiary = OnTerracottaTertiaryDark,
    tertiaryContainer = TerracottaContainerDark,
    onTertiaryContainer = OnTerracottaContainerDark,
    background = WarmBackgroundDark,
    onBackground = OnWarmBackgroundDark,
    surface = WarmSurfaceDark,
    onSurface = OnWarmSurfaceDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = OnWarmSurfaceVariantDark,
    outline = WarmOutlineDark,
    surfaceContainer = WarmSurfaceContainerDark,
    surfaceContainerHigh = WarmSurfaceContainerHighDark,
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorContainerRedDark,
    onErrorContainer = OnErrorContainerRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = SaffronPrimary,
    onPrimary = OnSaffronPrimary,
    primaryContainer = SaffronContainer,
    onPrimaryContainer = OnSaffronContainer,
    secondary = HerbSecondary,
    onSecondary = OnHerbSecondary,
    secondaryContainer = HerbContainer,
    onSecondaryContainer = OnHerbContainer,
    tertiary = TerracottaTertiary,
    onTertiary = OnTerracottaTertiary,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    background = WarmBackground,
    onBackground = OnWarmBackground,
    surface = WarmSurface,
    onSurface = OnWarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = OnWarmSurfaceVariant,
    outline = WarmOutline,
    surfaceContainer = WarmSurfaceContainer,
    surfaceContainerHigh = WarmSurfaceContainerHigh,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed
)

@Composable
fun FamilyMealAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Branded warm palette by default; dynamic color opt-in only.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
