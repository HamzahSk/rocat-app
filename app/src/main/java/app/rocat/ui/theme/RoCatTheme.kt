package app.rocat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B61),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF9A452D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF3A0B00),
    tertiary = Color(0xFF53643E),
    tertiaryContainer = Color(0xFFD6E9B9),
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF181D1B),
    surface = Color(0xFFF7F9F7),
    surfaceVariant = Color(0xFFDAE5E1),
    surfaceContainer = Color(0xFFEBEFED),
    surfaceContainerLow = Color(0xFFF1F4F2),
    surfaceContainerHigh = Color(0xFFE5E9E7),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF9EF2E4),
    secondary = Color(0xFFFFB59F),
    onSecondary = Color(0xFF5C1907),
    secondaryContainer = Color(0xFF7B2E18),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFBACD9F),
    tertiaryContainer = Color(0xFF3C4C29),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE0E3E0),
    surface = Color(0xFF101412),
    surfaceVariant = Color(0xFF3F4946),
    surfaceContainer = Color(0xFF1C211F),
    surfaceContainerLow = Color(0xFF181C1A),
    surfaceContainerHigh = Color(0xFF262B29),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
)

private val RoCatTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    )
}

private val RoCatShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun RoCatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RoCatTypography,
        shapes = RoCatShapes,
        content = content,
    )
}
