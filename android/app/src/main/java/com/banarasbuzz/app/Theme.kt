package com.banarasbuzz.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Warm saffron-and-cream palette: ghats at sunrise. */
object BB {
    val Saffron = Color(0xFFF26A1B)
    val SaffronDeep = Color(0xFFD4520C)
    val Gold = Color(0xFFF6B341)
    val Cream = Color(0xFFFFF8EF)
    val Tile = Color(0xFFFFEFD9)
    val TileStrong = Color(0xFFFFE3BF)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF2B1A0F)
    val InkSoft = Color(0xFF4A3526)
    val Muted = Color(0xFF8A7563)
    val Line = Color(0xFFF1E3D0)
    val Heart = Color(0xFFE5383B)
}

@Composable
fun BanarasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BB.Saffron,
            onPrimary = Color.White,
            primaryContainer = BB.TileStrong,
            onPrimaryContainer = BB.Ink,
            secondaryContainer = BB.Tile,
            onSecondaryContainer = BB.SaffronDeep,
            background = BB.Cream,
            onBackground = BB.Ink,
            surface = BB.Cream,
            onSurface = BB.Ink,
            surfaceVariant = BB.Tile,
            onSurfaceVariant = BB.Muted,
            outline = BB.Line,
        ),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
