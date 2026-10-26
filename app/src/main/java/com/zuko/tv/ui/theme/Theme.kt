package com.zuko.tv.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Live theme blend: 1f = fully dark, 0f = fully light. [ZukoTheme] animates it when the
 * user switches mode, so every colour below cross-fades instead of snapping.
 * All palette values are read through getters, so reading them inside a composable
 * automatically subscribes that composable to theme changes.
 */
object ZukoPalette {
    var darkness by mutableFloatStateOf(1f)
}

private fun mix(dark: Long, light: Long): Color =
    lerp(Color(light), Color(dark), ZukoPalette.darkness)

// Palette taken from the ZUKO "Z" mark: a deep navy field and a blue-to-violet gradient.
val Ink: Color get() = mix(0xFF050813, 0xFFF3F6FD)
val Panel: Color get() = mix(0xFF0E1426, 0xFFFFFFFF)
val Edge: Color get() = mix(0xFF1F2A4A, 0xFFD3DAEE)
val TextHi: Color get() = mix(0xFFEAF0FF, 0xFF0B1226)
val TextLo: Color get() = mix(0xFF9AA6C6, 0xFF4F5A7D)
val BodySoft: Color get() = mix(0xFFD3DBF0, 0xFF2A3354)
val Outline: Color get() = mix(0x33FFFFFF, 0x330B1226)
val Ok: Color get() = mix(0xFF7DFFB3, 0xFF0E8A4F)
val Err: Color get() = mix(0xFFFF8A80, 0xFFC62828)

val Azure = Color(0xFF2B8CFF)
val Violet = Color(0xFF6B46FF)

/** The one signature device: whatever has focus gets this ring. */
val ZukoBrush = Brush.linearGradient(listOf(Azure, Violet))

object ZukoType {
    val title: TextStyle get() = screenTitle
    val heroTitle: TextStyle get() = TextStyle(
        fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp, color = TextHi,
    )
    val screenTitle: TextStyle get() = TextStyle(
        fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp, color = TextHi,
    )
    val rowTitle: TextStyle get() = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextHi)
    val body: TextStyle get() = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = TextLo)
    val meta: TextStyle get() = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextHi)
    val brand: TextStyle get() = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = TextHi,
    )
    val small: TextStyle get() = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = TextLo)
}

@Composable
fun ZukoTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    LaunchedEffect(darkTheme) {
        val from = ZukoPalette.darkness
        val to = if (darkTheme) 1f else 0f
        if (from != to) {
            // Frame-time based so it still fades when system animations are switched off.
            val start = withFrameNanos { it }
            var f = 0f
            while (f < 1f) {
                val now = withFrameNanos { it }
                f = ((now - start) / 1_000_000f / 320f).coerceIn(0f, 1f)
                ZukoPalette.darkness = from + (to - from) * FastOutSlowInEasing.transform(f)
            }
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Azure,
            secondary = Violet,
            background = Ink,
            surface = Panel,
            onBackground = TextHi,
            onSurface = TextHi,
        ),
        content = content,
    )
}
