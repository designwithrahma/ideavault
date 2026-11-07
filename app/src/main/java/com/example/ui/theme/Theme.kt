package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.VaultCategory

enum class AppThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

@Immutable
data class IdeaVaultColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val secondarySurface: Color,
    val elevatedSurface: Color,
    val navigationSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val border: Color,
    val coral: Color,
    val lime: Color,
    val yellow: Color,
    val lavender: Color,
    val pink: Color,
    val actionYellow: Color,
    val referenceGray: Color,
    val darkInk: Color,
    val darkInkSecondary: Color
) {
    val subtleBorder: Color
        get() = border

    fun categoryCardColor(category: VaultCategory): Color = when (category) {
        VaultCategory.IDEA -> coral
        VaultCategory.METHOD -> lime
        VaultCategory.PROMPT -> yellow
        VaultCategory.KNOWLEDGE -> lavender
        VaultCategory.REFERENCE -> lavender
        VaultCategory.INBOX -> pink
    }

    fun categoryContentColor(category: VaultCategory): Color = darkInk

    fun categorySecondaryContentColor(category: VaultCategory): Color = darkInkSecondary
}

val DarkIdeaVaultColors = IdeaVaultColors(
    isDark = true,
    background = VaultBackground,
    surface = VaultSurface,
    secondarySurface = VaultSecondarySurface,
    elevatedSurface = VaultElevatedSurface,
    navigationSurface = VaultNavigationSurface,
    primaryText = VaultPrimaryText,
    secondaryText = VaultSecondaryText,
    mutedText = VaultMutedText,
    border = VaultBorder,
    coral = VaultCoral,
    lime = VaultLime,
    yellow = VaultYellow,
    lavender = VaultLavender,
    pink = VaultPink,
    actionYellow = VaultActionYellow,
    referenceGray = VaultReferenceGray,
    darkInk = VaultDarkInk,
    darkInkSecondary = VaultDarkInkSecondary
)

val LightIdeaVaultColors = IdeaVaultColors(
    isDark = false,
    background = VaultLightBackground,
    surface = VaultLightSurface,
    secondarySurface = VaultLightSecondarySurface,
    elevatedSurface = VaultLightElevatedSurface,
    navigationSurface = VaultLightNavigationSurface,
    primaryText = VaultLightPrimaryText,
    secondaryText = VaultLightSecondaryText,
    mutedText = VaultLightMutedText,
    border = VaultLightBorder,
    coral = VaultLightCoral,
    lime = VaultLightLime,
    yellow = VaultLightYellow,
    lavender = VaultLightLavender,
    pink = VaultLightPink,
    actionYellow = VaultActionYellow,
    referenceGray = VaultLightReferenceGray,
    darkInk = VaultDarkInk,
    darkInkSecondary = VaultDarkInkSecondary
)

val IdeaVaultDarkColors = DarkIdeaVaultColors
val IdeaVaultLightColors = LightIdeaVaultColors

val LocalIdeaVaultColors = staticCompositionLocalOf { DarkIdeaVaultColors }

object IdeaVaultTheme {
    val colors: IdeaVaultColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIdeaVaultColors.current
}

private val VaultDarkColorScheme = darkColorScheme(
    primary = VaultActionYellow,
    onPrimary = VaultDarkInk,
    primaryContainer = VaultSecondarySurface,
    onPrimaryContainer = VaultPrimaryText,
    secondary = VaultLime,
    onSecondary = VaultDarkInk,
    secondaryContainer = VaultSecondarySurface,
    onSecondaryContainer = VaultPrimaryText,
    tertiary = VaultLavender,
    onTertiary = VaultDarkInk,
    tertiaryContainer = VaultSecondarySurface,
    onTertiaryContainer = VaultPrimaryText,
    background = VaultBackground,
    onBackground = VaultPrimaryText,
    surface = VaultSurface,
    onSurface = VaultPrimaryText,
    surfaceVariant = VaultSecondarySurface,
    onSurfaceVariant = VaultSecondaryText,
    outline = VaultBorder,
    outlineVariant = VaultBorder,
    error = Color(0xFFFF6B6B),
    onError = VaultDarkInk
)

private val VaultLightColorScheme = lightColorScheme(
    primary = VaultActionYellow,
    onPrimary = VaultDarkInk,
    primaryContainer = VaultLightSecondarySurface,
    onPrimaryContainer = VaultLightPrimaryText,
    secondary = VaultLightLime,
    onSecondary = VaultDarkInk,
    secondaryContainer = VaultLightSecondarySurface,
    onSecondaryContainer = VaultLightPrimaryText,
    tertiary = VaultLightLavender,
    onTertiary = VaultDarkInk,
    tertiaryContainer = VaultLightSecondarySurface,
    onTertiaryContainer = VaultLightPrimaryText,
    background = VaultLightBackground,
    onBackground = VaultLightPrimaryText,
    surface = VaultLightSurface,
    onSurface = VaultLightPrimaryText,
    surfaceVariant = VaultLightSecondarySurface,
    onSurfaceVariant = VaultLightSecondaryText,
    outline = VaultLightBorder,
    outlineVariant = VaultLightBorder,
    error = Color(0xFFD9383A),
    onError = Color.White
)

@Composable
fun IdeaVaultTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = if (useDark) VaultDarkColorScheme else VaultLightColorScheme
    val vaultColors = if (useDark) DarkIdeaVaultColors else LightIdeaVaultColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
            }
        }
    }

    CompositionLocalProvider(LocalIdeaVaultColors provides vaultColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
