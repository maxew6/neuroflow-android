package com.neuroflow.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.neuroflow.app.ui.theme.NF
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = 6.2831855f

private class BrainPalette(val body: Color, val dark: Color, val aura: Color)

private fun paletteFor(stage: Int, restore: Float): BrainPalette {
    val base = when (stage) {
        1 -> BrainPalette(Color(0xFFFF6B9D), Color(0xFFC9467A), NF.Mint)
        2 -> BrainPalette(Color(0xFFFF9A76), Color(0xFFD96B4B), NF.Amber)
        3 -> BrainPalette(Color(0xFFA79BBE), Color(0xFF766B93), NF.Tangerine)
        4 -> BrainPalette(NF.Ash, Color(0xFF454A5A), NF.Crimson)
        else -> BrainPalette(Color(0xFF7D8191), Color(0xFF4A4E5D), NF.Crimson)
    }
    if (restore <= 0f) return base
    val live = BrainPalette(Color(0xFFFF6B9D), Color(0xFFC9467A), NF.Mint)
    val f = restore.coerceIn(0f, 1f)
    return BrainPalette(
        body = lerp(base.body, live.body, f),
        dark = lerp(base.dark, live.dark, f),
        aura = lerp(base.aura, live.aura, f)
    )
}

/**
 * The NeuroFlow brain. Drawn entirely with Canvas paths in a 100x100 design space.
 *
 * @param stage   1 Radiant, 2 Dopamine Drift, 3 Overstimulated & Melting, 4 Doomscroll Zombie, 5 Brain Freeze
 * @param restore 0f = stage colours, 1f = fully revived luminous pink (used by the breathing orb)
 */
@Composable
fun BrainMascot(
    stage: Int,
    modifier: Modifier = Modifier,
    restore: Float = 0f,
    showAura: Boolean = true
) {
    val st = stage.coerceIn(1, 5)
    val infinite = rememberInfiniteTransition(label = "brain")
    val slow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "slow"
    )
    val fast by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "fast"
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "spin"
    )
    val palette = paletteFor(st, restore)

    Canvas(modifier = modifier) {
        val unit = size.minDimension / 100f
        translate(
            left = (size.width - 100f * unit) / 2f,
            top = (size.height - 100f * unit) / 2f
        ) {
            scale(scale = unit, pivot = Offset.Zero) {
                drawBrain(st, palette, slow, fast, spin, showAura, restore)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawBrain(
    stage: Int,
    p: BrainPalette,
    slow: Float,
    fast: Float,
    spin: Float,
    showAura: Boolean,
    restore: Float
) {
    if (showAura) drawAura(stage, p.aura, slow)

    var dx = 0f
    var dy = 0f
    var rot = 0f
    when (stage) {
        1 -> dy = sin(TAU * slow) * 3.2f
        2 -> {
            dy = sin(TAU * slow) * 1.5f
            rot = sin(TAU * slow) * 3f
        }
        3 -> dy = sin(TAU * slow) * 1f
        4 -> {
            dx = sin(TAU * fast * 3f) * 0.9f
            dy = cos(TAU * fast * 4f) * 0.7f
        }
        else -> {
            dx = sin(TAU * fast * 2f) * 0.5f
            dy = sin(TAU * slow) * 0.8f
        }
    }

    translate(dx, dy) {
        rotate(rot, pivot = Offset(50f, 58f)) {
            // brain stem
            drawRoundRect(
                color = p.dark,
                topLeft = Offset(45f, 74f),
                size = Size(10f, 15f),
                cornerRadius = CornerRadius(4f)
            )

            val body = brainPath(stage, slow)
            drawPath(
                path = body,
                brush = Brush.radialGradient(
                    colors = listOf(lerp(p.body, Color.White, 0.28f), p.body, p.dark),
                    center = Offset(42f, 40f),
                    radius = 62f
                )
            )

            if (stage == 5) {
                val heat = 0.5f + 0.5f * sin(TAU * slow)
                drawPath(
                    path = body,
                    color = NF.Crimson.copy(alpha = (0.10f + 0.10f * heat) * (1f - restore.coerceIn(0f, 1f)))
                )
            }

            drawFolds(stage, p)

            when (stage) {
                1 -> drawFaceRadiant(p)
                2 -> drawFaceDrift(p, spin)
                3 -> drawFaceMelting(p, slow)
                4 -> drawFaceZombie(p)
                else -> drawFaceFreeze(p, slow)
            }
        }
    }
}

private fun brainPath(stage: Int, slow: Float): Path {
    val path = Path()
    fun blob(cx: Float, cy: Float, r: Float) {
        path.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }
    blob(33f, 52f, 17f)
    blob(50f, 42f, 18f)
    blob(67f, 52f, 17f)
    blob(37f, 67f, 16f)
    blob(63f, 67f, 16f)
    blob(50f, 60f, 19f)
    if (stage == 3) {
        // wavy melting bottom contour
        for (k in 0..2) {
            val x = 36f + 14f * k
            val len = 6f + 5f * sin(TAU * (slow + k / 3f))
            path.addOval(Rect(x - 5f, 72f, x + 5f, 80f + len))
        }
    }
    return path
}

private fun DrawScope.drawFolds(stage: Int, p: BrainPalette) {
    val fold = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val foldColor = p.dark.copy(alpha = 0.75f)

    val fissure = Path().apply {
        moveTo(50f, 24f)
        cubicTo(54f, 30f, 46f, 36f, 50f, 42f)
        cubicTo(53f, 46f, 47f, 50f, 50f, 52f)
    }
    drawPath(fissure, foldColor, style = fold)

    val gyri = Path().apply {
        moveTo(24f, 50f); quadraticBezierTo(30f, 42f, 36f, 48f)
        moveTo(30f, 36f); quadraticBezierTo(37f, 30f, 43f, 36f)
        moveTo(76f, 50f); quadraticBezierTo(70f, 42f, 64f, 48f)
        moveTo(70f, 36f); quadraticBezierTo(63f, 30f, 57f, 36f)
        moveTo(26f, 66f); quadraticBezierTo(29f, 72f, 34f, 74f)
        moveTo(74f, 66f); quadraticBezierTo(71f, 72f, 66f, 74f)
    }
    drawPath(gyri, foldColor.copy(alpha = 0.55f), style = fold)

    if (stage == 4) {
        // extra wrinkles for the exhausted brain
        val wrinkles = Path().apply {
            moveTo(36f, 28f); quadraticBezierTo(41f, 32f, 38f, 40f)
            moveTo(64f, 28f); quadraticBezierTo(59f, 32f, 62f, 40f)
            moveTo(22f, 58f); quadraticBezierTo(27f, 56f, 28f, 62f)
            moveTo(78f, 58f); quadraticBezierTo(73f, 56f, 72f, 62f)
            moveTo(44f, 82f); quadraticBezierTo(50f, 79f, 56f, 82f)
        }
        drawPath(wrinkles, foldColor.copy(alpha = 0.6f), style = fold)
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val i = r * 0.32f
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        lineTo(c.x + i, c.y - i)
        lineTo(c.x + r, c.y)
        lineTo(c.x + i, c.y + i)
        lineTo(c.x, c.y + r)
        lineTo(c.x - i, c.y + i)
        lineTo(c.x - r, c.y)
        lineTo(c.x - i, c.y - i)
        close()
    }
    drawPath(path, color)
}

private const val EYE_Y = 58f
private const val EYE_L = 40f
private const val EYE_R = 60f

/** Stage 1: glossy star-highlight eyes, cheerful smile, rosy cheeks. */
private fun DrawScope.drawFaceRadiant(p: BrainPalette) {
    for (ex in floatArrayOf(EYE_L, EYE_R)) {
        drawCircle(Color(0xFF2A1233), 6.2f, Offset(ex, EYE_Y))
        drawCircle(Color.White, 2.1f, Offset(ex - 2f, EYE_Y - 2.2f))
        drawStar(Offset(ex + 2.2f, EYE_Y + 2f), 1.9f, Color.White)
    }
    drawCircle(Color(0xFFFFB3CF).copy(alpha = 0.55f), 3.4f, Offset(31f, 67f))
    drawCircle(Color(0xFFFFB3CF).copy(alpha = 0.55f), 3.4f, Offset(69f, 67f))
    drawArc(
        color = p.dark,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(43f, 64f),
        size = Size(14f, 10f),
        style = Stroke(width = 2f, cap = StrokeCap.Round)
    )
}

/** Stage 2: rotating spiral eyes, neutral mouth. */
private fun DrawScope.drawFaceDrift(p: BrainPalette, spin: Float) {
    for (ex in floatArrayOf(EYE_L, EYE_R)) {
        drawCircle(Color.White, 6.8f, Offset(ex, EYE_Y))
        val spiral = Path()
        for (i in 0..48) {
            val a = i * 0.42f
            val rr = 0.9f + i * 0.095f
            val x = ex + cos(a) * rr
            val y = EYE_Y + sin(a) * rr
            if (i == 0) spiral.moveTo(x, y) else spiral.lineTo(x, y)
        }
        rotate(spin, pivot = Offset(ex, EYE_Y)) {
            drawPath(spiral, Color(0xFF3B2330), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
        }
    }
    drawLine(p.dark, Offset(44f, 72f), Offset(56f, 72f), strokeWidth = 2f, cap = StrokeCap.Round)
}

/** Stage 3: heavy half-closed eyelids, frown, falling sweat drop. */
private fun DrawScope.drawFaceMelting(p: BrainPalette, slow: Float) {
    for (ex in floatArrayOf(EYE_L, EYE_R)) {
        drawCircle(Color.White, 6.4f, Offset(ex, EYE_Y))
        drawCircle(Color(0xFF3B2F52), 3f, Offset(ex, EYE_Y + 1.5f))
        drawRect(
            color = p.body,
            topLeft = Offset(ex - 6.8f, EYE_Y - 7f),
            size = Size(13.6f, 8.4f)
        )
        drawLine(
            p.dark,
            Offset(ex - 6.8f, EYE_Y + 1.4f),
            Offset(ex + 6.8f, EYE_Y + 1.4f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )
    }
    val frown = Path().apply {
        moveTo(43f, 74f)
        quadraticBezierTo(50f, 69f, 57f, 74f)
    }
    drawPath(frown, p.dark, style = Stroke(width = 2f, cap = StrokeCap.Round))

    val y = 34f + 26f * slow
    val alpha = (1f - slow).coerceIn(0f, 1f)
    val drop = Path().apply {
        moveTo(76f, y - 6f)
        quadraticBezierTo(71f, y + 1f, 76f, y + 2.5f)
        quadraticBezierTo(81f, y + 1f, 76f, y - 6f)
        close()
    }
    drawPath(drop, Color(0xFF7DD3FC).copy(alpha = alpha))
}

/** Stage 4: dark eye bags, tiny glowing smartphone, slack jaw. */
private fun DrawScope.drawFaceZombie(p: BrainPalette) {
    val bag = Color(0xFF2B2F3B)
    for (ex in floatArrayOf(EYE_L, EYE_R)) {
        drawCircle(Color(0xFFE5E7EB), 5.6f, Offset(ex, EYE_Y))
        drawCircle(Color(0xFF1F2330), 2.2f, Offset(ex, EYE_Y + 2f))
        drawArc(
            color = bag,
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(ex - 7f, EYE_Y + 1.5f),
            size = Size(14f, 9f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round)
        )
    }
    drawOval(bag, topLeft = Offset(46f, 68f), size = Size(8f, 5f))

    // glowing phone held in front
    val glowCenter = Offset(50f, 84f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF7DF9FF).copy(alpha = 0.45f), Color.Transparent),
            center = glowCenter,
            radius = 22f
        ),
        radius = 22f,
        center = glowCenter
    )
    rotate(-6f, pivot = Offset(50f, 86f)) {
        drawRoundRect(
            color = Color(0xFF10131A),
            topLeft = Offset(41f, 76f),
            size = Size(18f, 20f),
            cornerRadius = CornerRadius(3f)
        )
        drawRoundRect(
            color = Color(0xFF7DF9FF).copy(alpha = 0.85f),
            topLeft = Offset(42.8f, 78f),
            size = Size(14.4f, 15.5f),
            cornerRadius = CornerRadius(2f)
        )
    }
    drawCircle(p.dark, 3.2f, Offset(40f, 90f))
    drawCircle(p.dark, 3.2f, Offset(60f, 90f))
}

/** Stage 5: X eyes, zig-zag mouth, rising steam particles. */
private fun DrawScope.drawFaceFreeze(p: BrainPalette, slow: Float) {
    for (ex in floatArrayOf(EYE_L, EYE_R)) {
        drawLine(
            p.dark,
            Offset(ex - 4f, EYE_Y - 4f),
            Offset(ex + 4f, EYE_Y + 4f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
        drawLine(
            p.dark,
            Offset(ex - 4f, EYE_Y + 4f),
            Offset(ex + 4f, EYE_Y - 4f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
    }
    val mouth = Path().apply {
        moveTo(42f, 72f)
        lineTo(46f, 69f)
        lineTo(50f, 72f)
        lineTo(54f, 69f)
        lineTo(58f, 72f)
    }
    drawPath(mouth, p.dark, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    for (k in 0..2) {
        val phase = (slow + k / 3f) % 1f
        val sx = 38f + 12f * k + 3f * sin(TAU * phase)
        val sy = 24f - 22f * phase
        drawCircle(
            color = Color.White.copy(alpha = (1f - phase) * 0.55f),
            radius = 2.6f + 3.2f * phase,
            center = Offset(sx, sy)
        )
    }
}

private fun DrawScope.drawAura(stage: Int, color: Color, slow: Float) {
    val c = Offset(50f, 56f)
    val pulse = 0.5f + 0.5f * sin(TAU * slow)
    val strength = when (stage) {
        1 -> 0.55f
        2 -> 0.6f + 0.2f * pulse
        3 -> 0.6f + 0.3f * pulse
        4 -> 0.7f
        else -> 0.8f + 0.2f * pulse
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.32f * strength), Color.Transparent),
            center = c,
            radius = 48f
        ),
        radius = 48f,
        center = c
    )
    for (i in 0..2) {
        val r = 37f + i * 3.2f + 2f * pulse
        drawCircle(
            color = color.copy(alpha = ((0.5f - i * 0.14f) * strength).coerceAtLeast(0f)),
            radius = r,
            center = c,
            style = Stroke(width = 1.4f)
        )
    }
}
