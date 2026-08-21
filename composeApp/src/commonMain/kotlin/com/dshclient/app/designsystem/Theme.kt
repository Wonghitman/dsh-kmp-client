package com.dshclient.app.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M3 Expressive 风格主题：柔和色调 + 大圆角（pill 形组件）+ 表现力色彩 Token。
 */
private val ExpressiveLightColors = lightColorScheme(
    primary = Color(0xFF3F5F90),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6F5675),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF9D8FD),
    onTertiaryContainer = Color(0xFF28132F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEDF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE0E2E8),
)

private val ExpressiveDarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF073160),
    primaryContainer = Color(0xFF264777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDCBCE0),
    onTertiary = Color(0xFF3E2845),
    tertiaryContainer = Color(0xFF563E5D),
    onTertiaryContainer = Color(0xFFF9D8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
)

/**
 * 语义扩展色板（用于 Success、Warning、Info、Running、Tool 等状态）
 */
@Immutable
data class ExtendedColorScheme(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val running: Color,
    val onRunning: Color,
    val runningContainer: Color,
    val onRunningContainer: Color,
)

private val LightExtendedColors = ExtendedColorScheme(
    success = Color(0xFF1B6D24),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA3F69E),
    onSuccessContainer = Color(0xFF002204),
    warning = Color(0xFF865300),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB3),
    onWarningContainer = Color(0xFF2B1700),
    info = Color(0xFF006399),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFCEE5FF),
    onInfoContainer = Color(0xFF001D32),
    running = Color(0xFF00677C),
    onRunning = Color(0xFFFFFFFF),
    runningContainer = Color(0xFFB1ECFF),
    onRunningContainer = Color(0xFF001F27),
)

private val DarkExtendedColors = ExtendedColorScheme(
    success = Color(0xFF88D984),
    onSuccess = Color(0xFF00390A),
    successContainer = Color(0xFF005313),
    onSuccessContainer = Color(0xFFA3F69E),
    warning = Color(0xFFFFB951),
    onWarning = Color(0xFF472A00),
    warningContainer = Color(0xFF653E00),
    onWarningContainer = Color(0xFFFFDDB3),
    info = Color(0xFF96CCFF),
    onInfo = Color(0xFF003353),
    infoContainer = Color(0xFF004B75),
    onInfoContainer = Color(0xFFCEE5FF),
    running = Color(0xFF86D2E9),
    onRunning = Color(0xFF003642),
    runningContainer = Color(0xFF004E5E),
    onRunningContainer = Color(0xFFB1ECFF),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColorScheme
    @Composable
    get() = LocalExtendedColors.current

/** Expressive 大圆角形状（pill 形与高曲率） */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** M3 Expressive 排版层次 */
private val ExpressiveTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun DshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ExpressiveDarkColors else ExpressiveLightColors
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content,
        )
    }
}
