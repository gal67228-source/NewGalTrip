
package com.gal.familytrips

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF17324D)
val Sky = Color(0xFF3B82F6)
val Aqua = Color(0xFF25B6C8)
val Coral = Color(0xFFFF7A66)
val Sun = Color(0xFFFFC857)
val Mint = Color(0xFF74C69D)
val Lavender = Color(0xFF9B8AFB)
val SoftBlue = Color(0xFFEAF3FF)
val SoftAqua = Color(0xFFE8FAFC)
val SoftCoral = Color(0xFFFFEFEC)
val SoftSun = Color(0xFFFFF7DC)
val SoftMint = Color(0xFFEAF8F0)
val SoftLavender = Color(0xFFF1EEFF)
val Background = Color(0xFFF7F9FC)
val CardWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1D2733)
val TextSecondary = Color(0xFF68778A)

private val AppColors = lightColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    secondary = Aqua,
    onSecondary = Color.White,
    tertiary = Coral,
    background = Background,
    surface = CardWhite,
    onSurface = TextPrimary,
    onBackground = TextPrimary,
    outline = Color(0xFFD7E0EA)
)

@Composable
fun GalTripsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = Typography(),
        content = content
    )
}
