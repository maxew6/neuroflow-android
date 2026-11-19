package com.neuroflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.glassCard
import kotlin.math.max

/** Hooks the dashboard needs from the Activity (system settings, permission prompts, overlay preview). */
class AppCallbacks(
    val openOverlaySettings: () -> Unit,
    val openAccessibilitySettings: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestBatteryUnrestricted: () -> Unit,
    val setPreview: (Boolean) -> Unit,
    val setMonitoring: (Boolean) -> Unit,
    val testBlocker: () -> Unit
)

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

/** Headline with a slow, shimmering aurora gradient sweeping through the glyphs. */
@Composable
fun ShimmerHeadline(text: String, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "headline")
    val x by infinite.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = listOf(NF.Ice, NF.Sakura, NF.Peach, NF.Mint, NF.Ice),
        start = Offset(x, 0f),
        end = Offset(x + 600f, 200f)
    )
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = brush,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        )
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = NF.Slate,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 6.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Permission onboarding
// ---------------------------------------------------------------------------------------------

/** Circle whose checkmark draws itself (path trimming) when the permission turns on. */
@Composable
fun AnimatedStatusCheck(granted: Boolean, modifier: Modifier = Modifier.size(30.dp)) {
    val progress by animateFloatAsState(
        targetValue = if (granted) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "checkProgress"
    )
    val ring by animateColorAsState(
        targetValue = if (granted) NF.Mint else NF.Slate.copy(alpha = 0.55f),
        animationSpec = tween(300),
        label = "checkRing"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color = ring.copy(alpha = 0.16f), radius = w / 2f)
        drawCircle(
            color = ring,
            radius = w / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.5.dp.toPx())
        )
        if (progress > 0.001f) {
            val path = Path().apply {
                moveTo(w * 0.28f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.34f)
            }
            val measure = PathMeasure()
            measure.setPath(path, false)
            val partial = Path()
            measure.getSegment(0f, measure.length * progress, partial, true)
            drawPath(
                path = partial,
                color = NF.Mint,
                style = Stroke(
                    width = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            drawCircle(color = ring, radius = 2.5.dp.toPx())
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .noRippleClickable(enabled = !granted, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedStatusCheck(granted = granted)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NF.Ice,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = NF.Slate,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (granted) "On" else "Enable",
            color = if (granted) NF.Mint else NF.Coral,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------------------------

@Composable
fun StatCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard(RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            style = NumberStyle,
            maxLines = 1
        )
        Text(
            text = label,
            color = NF.Slate,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Floating bottom navigation
// ---------------------------------------------------------------------------------------------

@Composable
fun FloatingNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf("Home", "Analytics", "Settings")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(32.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) NF.Coral.copy(alpha = 0.24f) else Color.Transparent,
                animationSpec = tween(250),
                label = "navBg"
            )
            val tint by animateColorAsState(
                targetValue = if (isSelected) NF.Ice else NF.Slate,
                animationSpec = tween(250),
                label = "navTint"
            )
            val itemWeight by animateFloatAsState(
                targetValue = if (isSelected) 1.7f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "navWeight"
            )
            Row(
                modifier = Modifier
                    .weight(itemWeight.coerceAtLeast(0.2f))
                    .clip(RoundedCornerShape(26.dp))
                    .background(bg)
                    .noRippleClickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavIcon(kind = index, tint = tint, modifier = Modifier.size(22.dp))
                AnimatedVisibility(visible = isSelected) {
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavIcon(kind: Int, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        when (kind) {
            0 -> {
                val house = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.9f, h * 0.48f)
                    lineTo(w * 0.8f, h * 0.48f)
                    lineTo(w * 0.8f, h * 0.86f)
                    lineTo(w * 0.2f, h * 0.86f)
                    lineTo(w * 0.2f, h * 0.48f)
                    lineTo(w * 0.1f, h * 0.48f)
                    close()
                }
                drawPath(
                    path = house,
                    color = tint,
                    style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            1 -> {
                drawRoundRect(tint, Offset(w * 0.12f, h * 0.52f), Size(w * 0.18f, h * 0.36f), CornerRadius(sw))
                drawRoundRect(tint, Offset(w * 0.41f, h * 0.28f), Size(w * 0.18f, h * 0.60f), CornerRadius(sw))
                drawRoundRect(tint, Offset(w * 0.70f, h * 0.12f), Size(w * 0.18f, h * 0.76f), CornerRadius(sw))
            }
            else -> {
                for (i in 0..2) {
                    val y = h * (0.24f + 0.26f * i)
                    drawLine(tint, Offset(w * 0.1f, y), Offset(w * 0.9f, y), sw, StrokeCap.Round)
                    val knobX = w * (0.30f + 0.22f * ((i * 2) % 3))
                    drawCircle(NF.Midnight, sw * 2.1f, Offset(knobX, y))
                    drawCircle(tint, sw * 1.5f, Offset(knobX, y), style = Stroke(width = sw * 0.9f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Analytics: vitality gauge + weekly bars
// ---------------------------------------------------------------------------------------------

/** Circular 270-degree gauge with a Crimson -> Amber -> Mint sweep and a springy fill. */
@Composable
fun VitalityGauge(vitality: Int, modifier: Modifier = Modifier, diameter: Dp = 220.dp) {
    val fraction by animateFloatAsState(
        targetValue = vitality / 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gauge"
    )
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2f + 4.dp.toPx()
            val arcSize = Size(size.width - 2f * inset, size.height - 2f * inset)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val sweep = 270f * fraction.coerceIn(0f, 1f)
            if (sweep > 0.5f) {
                rotate(135f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to NF.Crimson,
                            0.40f to NF.Amber,
                            0.75f to NF.Mint,
                            1f to NF.Mint
                        ),
                        startAngle = 0f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = vitality.toString(),
                color = NF.Ice,
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                style = NumberStyle
            )
            Text(
                text = "BRAIN VITALITY",
                color = NF.Slate,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

/**
 * Seven-day Reel bars. [values] holds up to seven counts, oldest first, today last.
 * Bars at or under [limit] glow Mint, bars over it glow Crimson. A dashed line marks the limit.
 */
@Composable
fun WeeklyBars(
    values: List<Int>,
    limit: Int,
    dayLabels: List<String>,
    modifier: Modifier = Modifier
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val ceiling = max(max(limit * 1.35f, (values.maxOrNull() ?: 0) * 1.15f), 10f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.46f
            values.forEachIndexed { i, v ->
                val ratio = (v / ceiling).coerceIn(0f, 1f) * grow.value
                val barH = size.height * ratio
                val x = slot * i + (slot - barW) / 2f
                val over = v > limit
                val top = if (over) NF.Crimson else NF.Mint
                val bottom = if (over) NF.Tangerine else NF.Sakura
                drawRoundRect(
                    color = Color(0x14FFFFFF),
                    topLeft = Offset(x, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = CornerRadius(barW / 2f)
                )
                if (barH > 1f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(top, bottom),
                            startY = size.height - barH,
                            endY = size.height
                        ),
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f)
                    )
                }
            }
            val limitY = size.height * (1f - (limit / ceiling).coerceIn(0f, 1f))
            drawLine(
                color = NF.Amber.copy(alpha = 0.8f),
                start = Offset(0f, limitY),
                end = Offset(size.width, limitY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    color = if (index == dayLabels.lastIndex) NF.Ice else NF.Slate,
                    fontSize = 11.sp,
                    fontWeight = if (index == dayLabels.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

