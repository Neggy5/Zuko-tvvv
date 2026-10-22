package com.zuko.tv.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val TOTAL_MS = 3200

// The intro is always dark/cinematic, whichever theme the user picked.
private val NavyDeep = Color(0xFF050813)
private val SplashBlue = Color(0xFF2B8CFF)
private val SplashViolet = Color(0xFF6B46FF)
private val Ice = Color(0xFFEAF0FF)
private val Mist = Color(0xFF9AA6C6)

private val BackOut = Easing { x ->
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = x - 1f
    1f + c3 * u * u * u + c1 * u * u
}
private val Out = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val Smooth = FastOutSlowInEasing

private fun seg(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun Easing.at(t: Float, from: Float, to: Float): Float = transform(seg(t, from, to))

// The "Z" mark traced from the brand artwork (256-unit grid).
private val MarkPoints = listOf(
    51f to 71f, 191f to 70f, 191f to 93f, 159f to 119f, 143f to 110f, 143f to 132f,
    109f to 161f, 191f to 162f, 191f to 185f, 52f to 186f, 51f to 164f, 133f to 95f, 52f to 94f,
)
private const val MARK_CX = 121f
private const val MARK_CY = 128f
private const val MARK_W = 140f
private const val MARK_H = 116f

private fun markPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    MarkPoints.forEachIndexed { i, (x, y) ->
        val px = cx + (x - MARK_CX) * s
        val py = cy + (y - MARK_CY) * s
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

private class Dot(
    val x: Float,
    val y: Float,
    val r: Float,
    val speed: Float,
    val phase: Float,
    val bright: Float,
)

/**
 * ZUKO TV opening sequence (~3.2s): glow + particles, light streak, shockwave rings, the vector
 * Z pops in with a light sweep, the wordmark types in, then the Z zooms through the screen
 * into the app. Drawn as vectors, so it is razor sharp at 720p/1080p/4K. Any key skips it.
 */
@Composable
fun SplashIntro(onFinished: () -> Unit) {
    val t = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    val dots = remember {
        val rnd = Random(7)
        List(46) {
            Dot(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                r = 0.6f + rnd.nextFloat() * 1.4f,
                speed = 0.04f + rnd.nextFloat() * 0.10f,
                phase = rnd.nextFloat() * 6.28f,
                bright = 0.3f + rnd.nextFloat() * 0.7f,
            )
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        t.animateTo(1f, tween(durationMillis = TOTAL_MS, easing = LinearEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .graphicsLayer {
                val e = Smooth.transform(seg(t.value, 0.86f, 1f))
                alpha = 1f - e
                scaleX = 1f + 0.12f * e
                scaleY = 1f + 0.12f * e
            }
            .background(NavyDeep),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = t.value
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val markH = 150.dp.toPx()
            val cy = h / 2f - 34.dp.toPx()
            val baseScale = markH / MARK_H
            val center = Offset(cx, cy)

            // 1. Ambient glow
            val glow = Out.at(p, 0f, 0.35f) * (0.85f + 0.15f * sin(p * 14f))
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SplashViolet.copy(alpha = 0.34f * glow),
                        SplashBlue.copy(alpha = 0.16f * glow),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = max(w, h) * 0.62f,
                ),
            )

            // 2. Drifting, twinkling particles
            val dotIn = seg(p, 0.02f, 0.30f)
            for (d in dots) {
                val tw = 0.5f + 0.5f * sin(p * 18f + d.phase)
                val x = d.x * w + sin(p * 4f + d.phase) * 6f
                val y = (((d.y - p * d.speed) % 1f) + 1f) % 1f * h
                drawCircle(
                    color = Ice.copy(alpha = ((0.15f + 0.55f * tw * d.bright) * dotIn).coerceIn(0f, 1f)),
                    radius = d.r.dp.toPx(),
                    center = Offset(x, y),
                )
            }

            // 3. Anamorphic light streak
            val streakIn = Out.at(p, 0.10f, 0.42f)
            val streakA = 1f - seg(p, 0.40f, 0.70f)
            val half = w * 0.5f * streakIn
            if (streakA > 0f && half > 1f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SplashBlue.copy(alpha = 0.9f * streakA),
                            Color.White.copy(alpha = streakA),
                            SplashViolet.copy(alpha = 0.9f * streakA),
                            Color.Transparent,
                        ),
                        startX = cx - half,
                        endX = cx + half,
                    ),
                    topLeft = Offset(cx - half, cy - 1.5.dp.toPx()),
                    size = Size(half * 2f, 3.dp.toPx()),
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SplashBlue.copy(alpha = 0.22f * streakA),
                            Color.Transparent,
                        ),
                        startX = cx - half,
                        endX = cx + half,
                    ),
                    topLeft = Offset(cx - half, cy - 9.dp.toPx()),
                    size = Size(half * 2f, 18.dp.toPx()),
                )
            }

            // 4. Shockwave rings
            val ringBrush = Brush.sweepGradient(
                colors = listOf(SplashBlue, SplashViolet, SplashBlue),
                center = center,
            )
            listOf(0.30f, 0.40f).forEachIndexed { i, start ->
                val r = seg(p, start, start + 0.38f)
                if (r > 0f && r < 1f) {
                    val radius = markH * 0.55f + Out.transform(r) * max(w, h) * 0.55f
                    drawCircle(
                        brush = ringBrush,
                        radius = radius,
                        center = center,
                        alpha = (1f - r) * 0.55f,
                        style = Stroke(width = (3f - i).dp.toPx()),
                    )
                }
            }

            // 5. The Z
            val appear = Out.at(p, 0.16f, 0.50f)
            val pop = BackOut.at(p, 0.16f, 0.60f)
            val exit = seg(p, 0.84f, 1f)
            val zoom = 1f + 7f * exit * exit * exit
            val sc = (0.5f + 0.5f * pop) * zoom * baseScale
            val halfW = MARK_W / 2f * sc
            val halfH = MARK_H / 2f * sc

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SplashBlue.copy(alpha = 0.45f * appear), Color.Transparent),
                    center = center,
                    radius = markH * 1.1f,
                ),
                radius = markH * 1.1f,
                center = center,
            )

            val path = markPath(cx, cy, sc)
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(SplashBlue, SplashViolet),
                    start = Offset(cx - halfW, cy - halfH),
                    end = Offset(cx + halfW, cy + halfH),
                ),
                alpha = appear,
            )

            // Light sweep across the Z
            val sweep = Smooth.at(p, 0.60f, 0.88f)
            if (sweep > 0f && sweep < 1f) {
                val band = halfW * 1.2f
                val x0 = cx - halfW - band + (2f * halfW + 2f * band) * sweep
                clipPath(path) {
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.85f),
                            1f to Color.Transparent,
                            start = Offset(x0, cy - halfH),
                            end = Offset(x0 + band, cy + halfH * 0.2f),
                        ),
                    )
                }
            }

            // Accent bar between the Z and the wordmark
            val bar = Out.at(p, 0.56f, 0.80f)
            if (bar > 0f) {
                val barW = 150.dp.toPx() * bar
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(SplashBlue, SplashViolet),
                        startX = cx - barW / 2f,
                        endX = cx + barW / 2f,
                    ),
                    topLeft = Offset(cx - barW / 2f, cy + markH / 2f + 14.dp.toPx()),
                    size = Size(barW, 3.dp.toPx()),
                    alpha = 1f - exit,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                "ZUKO TV".forEachIndexed { i, ch ->
                    if (ch == ' ') {
                        Spacer(Modifier.width(18.dp))
                    } else {
                        val style = if (i >= 5) {
                            TextStyle(
                                brush = Brush.linearGradient(listOf(SplashBlue, SplashViolet)),
                                fontSize = 46.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 8.sp,
                            )
                        } else {
                            TextStyle(
                                color = Ice,
                                fontSize = 46.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 8.sp,
                            )
                        }
                        Text(
                            text = ch.toString(),
                            style = style,
                            modifier = Modifier.graphicsLayer {
                                val a = Out.transform(seg(t.value, 0.44f + i * 0.035f, 0.64f + i * 0.035f))
                                alpha = a
                                translationY = (1f - a) * 28.dp.toPx()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "STREAM  \u2022  WATCH  \u2022  DOWNLOAD",
                style = TextStyle(
                    color = Mist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp,
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = Out.transform(seg(t.value, 0.66f, 0.84f))
                },
            )
        }
    }
}
