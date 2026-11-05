package com.banarasbuzz.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Mood of the illustrated riverfront. */
enum class Scene { DAWN, DUSK, NIGHT }

private class Palette(
    val sky: List<Color>,
    val sun: Color,
    val skyline: Color,
    val skylineNear: Color,
    val water: List<Color>,
    val glow: Color,
    val lamps: Boolean,
)

private fun palette(scene: Scene) = when (scene) {
    Scene.DAWN -> Palette(
        sky = listOf(Color(0xFF6E5A9E), Color(0xFFE77A63), Color(0xFFF7B267), Color(0xFFFCD9A0)),
        sun = Color(0xFFFFE08A),
        skyline = Color(0xFF8A4B3A),
        skylineNear = Color(0xFF5B2E24),
        water = listOf(Color(0xFFE59A6A), Color(0xFF7D4E5C)),
        glow = Color(0xFFFFD27A),
        lamps = false,
    )
    Scene.DUSK -> Palette(
        sky = listOf(Color(0xFF3E3B73), Color(0xFFC0546A), Color(0xFFF08A4B), Color(0xFFF9C46B)),
        sun = Color(0xFFFFC857),
        skyline = Color(0xFF6B3226),
        skylineNear = Color(0xFF3D1C17),
        water = listOf(Color(0xFFD9774A), Color(0xFF4B2A3D)),
        glow = Color(0xFFFFB347),
        lamps = true,
    )
    Scene.NIGHT -> Palette(
        sky = listOf(Color(0xFF0E1630), Color(0xFF1F2752), Color(0xFF4A3260), Color(0xFF8C4A3C)),
        sun = Color(0xFFFFF3C4),
        skyline = Color(0xFF9C5A22),
        skylineNear = Color(0xFF3A2015),
        water = listOf(Color(0xFF3A2A3E), Color(0xFF0E1226)),
        glow = Color(0xFFFFB13B),
        lamps = true,
    )
}

// Fixed skyline so every render looks the same: (x fraction, width fraction, height fraction).
private val buildings = listOf(
    Triple(0.00f, 0.07f, 0.10f), Triple(0.06f, 0.05f, 0.14f), Triple(0.13f, 0.06f, 0.09f),
    Triple(0.27f, 0.05f, 0.12f), Triple(0.33f, 0.06f, 0.08f), Triple(0.47f, 0.05f, 0.13f),
    Triple(0.53f, 0.07f, 0.10f), Triple(0.70f, 0.06f, 0.11f), Triple(0.77f, 0.05f, 0.15f),
    Triple(0.83f, 0.07f, 0.09f), Triple(0.92f, 0.08f, 0.12f),
)

// Temple spires: (center x fraction, width fraction, height fraction).
private val spires = listOf(
    Triple(0.22f, 0.07f, 0.30f), Triple(0.41f, 0.10f, 0.42f), Triple(0.64f, 0.08f, 0.34f),
    Triple(0.88f, 0.05f, 0.22f),
)

private fun DrawScope.shikhara(cx: Float, base: Float, w: Float, h: Float, color: Color) {
    val half = w / 2f
    val p = Path().apply {
        moveTo(cx - half, base)
        cubicTo(cx - half * 1.05f, base - h * 0.55f, cx - half * 0.35f, base - h * 0.85f, cx, base - h)
        cubicTo(cx + half * 0.35f, base - h * 0.85f, cx + half * 1.05f, base - h * 0.55f, cx + half, base)
        close()
    }
    drawPath(p, color)
    // Kalash and flag on top.
    drawCircle(color, radius = w * 0.07f, center = Offset(cx, base - h - w * 0.05f))
    drawLine(color, Offset(cx, base - h), Offset(cx, base - h - w * 0.45f), strokeWidth = w * 0.03f)
    val flag = Path().apply {
        moveTo(cx, base - h - w * 0.45f)
        lineTo(cx + w * 0.28f, base - h - w * 0.36f)
        lineTo(cx, base - h - w * 0.27f)
        close()
    }
    drawPath(flag, Color(0xFFFF7A1A))
}

private fun DrawScope.boat(x: Float, y: Float, w: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y)
        quadraticTo(x + w / 2f, y + w * 0.22f, x + w, y)
        lineTo(x + w * 0.85f, y + w * 0.06f)
        lineTo(x + w * 0.15f, y + w * 0.06f)
        close()
    }
    drawPath(p, color)
}

/** An illustrated Banaras riverfront: sky, sun, ghat skyline with temple spires, the Ganga. */
@Composable
fun BanarasScene(scene: Scene, modifier: Modifier = Modifier) {
    val pal = palette(scene)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.66f

        drawRect(Brush.verticalGradient(pal.sky, endY = horizon))

        // Sun / moon with a soft glow.
        val sunC = Offset(w * 0.78f, horizon * (if (scene == Scene.NIGHT) 0.25f else 0.55f))
        val r = minOf(w, h) * 0.09f
        drawCircle(
            Brush.radialGradient(listOf(pal.sun.copy(alpha = 0.55f), Color.Transparent), sunC, r * 3.2f),
            radius = r * 3.2f, center = sunC,
        )
        drawCircle(pal.sun, radius = r, center = sunC)

        // Far skyline then temple spires.
        for ((x, bw, bh) in buildings) {
            drawRect(pal.skyline, Offset(x * w, horizon - bh * h), Size(bw * w + 1f, bh * h))
        }
        for ((cx, sw, sh) in spires) {
            shikhara(cx * w, horizon, sw * w, sh * h, pal.skyline)
        }
        // Lit windows / diyas along the ghat for evening scenes.
        if (pal.lamps) {
            var x = w * 0.03f
            while (x < w) {
                drawCircle(pal.glow, radius = h * 0.008f, center = Offset(x, horizon - h * 0.035f))
                x += w * 0.045f
            }
        }

        // Ghat steps.
        val stepH = h * 0.018f
        for (i in 0 until 3) {
            drawRect(
                pal.skylineNear.copy(alpha = 0.85f - i * 0.15f),
                Offset(0f, horizon + i * stepH),
                Size(w, stepH * 0.7f),
            )
        }

        // River with a shimmering reflection.
        val riverTop = horizon + 3 * stepH
        drawRect(
            Brush.verticalGradient(pal.water, startY = riverTop, endY = h),
            Offset(0f, riverTop), Size(w, h - riverTop),
        )
        var y = riverTop + h * 0.03f
        var k = 0
        while (y < h) {
            val len = w * (0.10f - k * 0.008f).coerceAtLeast(0.03f)
            drawLine(
                pal.glow.copy(alpha = 0.55f),
                Offset(sunC.x - len / 2, y), Offset(sunC.x + len / 2, y),
                strokeWidth = h * 0.008f,
            )
            y += h * 0.035f
            k++
        }
        boat(w * 0.12f, riverTop + h * 0.12f, w * 0.16f, pal.skylineNear)
        boat(w * 0.42f, riverTop + h * 0.2f, w * 0.12f, pal.skylineNear)
    }
}

/** A warm gradient tile with a large emoji, used where we have no photo. */
@Composable
fun EmojiArt(
    emoji: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: TextUnit = 34.sp,
) {
    Box(
        modifier.background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = size)
    }
}

@Composable
fun SceneBox(scene: Scene, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier) {
        BanarasScene(scene, Modifier.fillMaxSize())
        content()
    }
}
