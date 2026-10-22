package com.zuko.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette taken from the ZUKO "Z" mark: a deep navy field and a blue-to-violet gradient.
val Ink = Color(0xFF050813)
val Panel = Color(0xFF0E1426)
val Edge = Color(0xFF1F2A4A)
val Azure = Color(0xFF2B8CFF)
val Violet = Color(0xFF6B46FF)
val TextHi = Color(0xFFEAF0FF)
val TextLo = Color(0xFF9AA6C6)

/** The one signature device: whatever has focus gets this ring. */
val ZukoBrush = Brush.linearGradient(listOf(Azure, Violet))

object ZukoType {
    val heroTitle = TextStyle(
        fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp, color = TextHi,
    )
    val screenTitle = TextStyle(
        fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp, color = TextHi,
    )
    val rowTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextHi)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = TextLo)
    val meta = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextHi)
    val brand = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = TextHi,
    )
    val small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = TextLo)
}

@Composable
fun ZukoTheme(content: @Composable () -> Unit) {
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
