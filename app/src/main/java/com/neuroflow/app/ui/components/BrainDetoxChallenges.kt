package com.neuroflow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.glassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI = 6.2831855f

enum class BreathPhase(val label: String, val seconds: Int, val hint: String) {
    Inhale("Inhale", 4, "Breathe in slowly through your nose"),
    Hold("Hold", 7, "Hold gently. Let the shimmer settle"),
    Exhale("Exhale", 8, "Release through your mouth, slow and long")
}

// =============================================================================================
// 1. Fluid 4-7-8 Breathing Orb  ("Re-Oxygenate Your Cortex")
// =============================================================================================

/**
 * Multi-layered concentric glowing orb: expands for 4s, shimmers for 7s, contracts for 8s.
 * Reports progress 0..1 so the mascot can fade from ashy gray back to luminous pink.
 */
@Composable
fun BreathingOrbChallenge(
    modifier: Modifier = Modifier,
    cycles: Int = 2,
    onRestoreProgress: (Float) -> Unit,
    onComplete: () -> Unit,
    onHaptic: (durationMs: Long, amplitude: Int) -> Unit
) {
    val orbScale = remember { Animatable(0.55f) }
    var phase by remember { mutableStateOf(BreathPhase.Inhale) }
    var secondsLeft by remember { mutableIntStateOf(BreathPhase.Inhale.seconds) }
    var progress by remember { mutableFloatStateOf(0f) }
    var cycleIndex by remember { mutableIntStateOf(1) }
    var finished by remember { mutableStateOf(false) }

    val currentOnProgress by rememberUpdatedState(onRestoreProgress)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnHaptic by rememberUpdatedState(onHaptic)

    LaunchedEffect(Unit) {
        val totalMs = cycles * 19 * 1000
        var elapsed = 0
        for (cycle in 1..cycles) {
            cycleIndex = cycle
            for (p in BreathPhase.values()) {
                phase = p
                val durationMs = p.seconds * 1000
                when (p) {
                    BreathPhase.Inhale -> launch {
                        orbScale.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
                    }
                    BreathPhase.Exhale -> launch {
                        orbScale.animateTo(0.55f, tween(durationMs, easing = FastOutSlowInEasing))
                    }
                    BreathPhase.Hold -> Unit
                }
                var t = 0
                while (t < durationMs) {
                    if (t % 1000 == 0) {
                        secondsLeft = (durationMs - t) / 1000
                        val sec = t / 1000
                        when (p) {
                            BreathPhase.Inhale -> currentOnHaptic(30L, 60 + sec * 30)
                            BreathPhase.Hold -> currentOnHaptic(15L, 45)
                            BreathPhase.Exhale -> currentOnHaptic(30L, 150 - sec * 14)
                        }
                    }
                    delay(100)
                    t += 100
                    elapsed += 100
                    progress = (elapsed / totalMs.toFloat()).coerceIn(0f, 1f)
                    currentOnProgress(progress)
                }
            }
        }
        finished = true
        progress = 1f
        currentOnProgress(1f)
        currentOnComplete()
    }

    val infinite = rememberInfiniteTransition(label = "orb")
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmer"
    )

    val orbColor = lerp(NF.Mint, NF.Sakura, progress)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(228.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = center
                val base = size.minDimension / 2f
                val s = orbScale.value
                val shimmerAlpha =
                    if (phase == BreathPhase.Hold && !finished) 0.78f + 0.22f * sin(shimmer * TWO_PI) else 1f
                for (i in 5 downTo 0) {
                    val r = base * s * (0.38f + i * 0.125f)
                    val a = (0.10f + (5 - i) * 0.07f) * shimmerAlpha
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                orbColor.copy(alpha = (a * 1.6f).coerceAtMost(1f)),
                                orbColor.copy(alpha = a * 0.12f)
                            ),
                            center = c,
                            radius = r
                        ),
                        radius = r,
                        center = c
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f * shimmerAlpha),
                    radius = base * s * 0.14f,
                    center = c
                )
                drawCircle(
                    color = orbColor.copy(alpha = 0.9f),
                    radius = base * s * 0.98f,
                    center = c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (finished) "Done" else phase.label,
                    color = NF.Ice,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!finished) {
                    Text(
                        text = secondsLeft.toString(),
                        color = NF.Ice,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = NumberStyle
                    )
                }
            }
        }
        Text(
            text = if (finished) "Cortex re-oxygenated. Your brain is glowing again." else phase.hint,
            color = NF.Slate,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "Cycle ${cycleIndex.coerceAtMost(cycles)} of $cycles  \u00B7  4-7-8",
            color = NF.Slate.copy(alpha = 0.8f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// =============================================================================================
// 2. Synapse Spark: 30-second executive-function puzzle
// =============================================================================================

private const val SPARK_TOTAL_MS = 30_000
private const val SPARK_PENALTY_MS = 2_000

private class MathProblem(val text: String, val options: List<Int>, val answer: Int)

private fun makeProblem(rnd: Random): MathProblem {
    val kind = rnd.nextInt(3)
    val text: String
    val answer: Int
    when (kind) {
        0 -> {
            val a = rnd.nextInt(12, 40)
            val b = rnd.nextInt(7, 30)
            text = "$a + $b"
            answer = a + b
        }
        1 -> {
            val a = rnd.nextInt(30, 80)
            val b = rnd.nextInt(5, 29)
            text = "$a \u2212 $b"
            answer = a - b
        }
        else -> {
            val a = rnd.nextInt(3, 10)
            val b = rnd.nextInt(3, 10)
            text = "$a \u00D7 $b"
            answer = a * b
        }
    }
    val options = mutableSetOf(answer)
    while (options.size < 3) {
        val d = answer + rnd.nextInt(-9, 10)
        if (d != answer && d > 0) options.add(d)
    }
    return MathProblem("$text = ?", options.toList().shuffled(rnd), answer)
}

/**
 * Three quick steps (two mental-math taps, then a 3x3 glowing node pattern memory tap) that wake the
 * prefrontal cortex before unlocking "+5 Bonus Reels" or exiting. Wrong taps cost 2 seconds.
 */
@Composable
fun SynapseSparkChallenge(
    modifier: Modifier = Modifier,
    onSuccess: () -> Unit,
    onFeedback: (correct: Boolean) -> Unit
) {
    var attempt by remember { mutableIntStateOf(0) }
    val rnd = remember(attempt) { Random(System.nanoTime()) }
    val problems = remember(attempt) { List(2) { makeProblem(rnd) } }
    val target = remember(attempt) { (0..8).shuffled(rnd).take(3).toSet() }

    var step by remember(attempt) { mutableIntStateOf(0) }
    var elapsedMs by remember(attempt) { mutableIntStateOf(0) }
    var failed by remember(attempt) { mutableStateOf(false) }
    var done by remember(attempt) { mutableStateOf(false) }
    var showing by remember(attempt) { mutableStateOf(false) }
    var reshow by remember(attempt) { mutableIntStateOf(0) }
    var taps by remember(attempt) { mutableStateOf(setOf<Int>()) }
    var wrongFlash by remember(attempt) { mutableStateOf(false) }

    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnFeedback by rememberUpdatedState(onFeedback)

    LaunchedEffect(attempt) {
        while (elapsedMs < SPARK_TOTAL_MS && !done) {
            delay(100)
            elapsedMs += 100
        }
        if (!done) failed = true
    }

    LaunchedEffect(step, attempt, reshow) {
        if (step == 2 && !done) {
            showing = true
            delay(1500)
            showing = false
        }
    }

    LaunchedEffect(wrongFlash) {
        if (wrongFlash) {
            delay(380)
            wrongFlash = false
        }
    }

    LaunchedEffect(done) {
        if (done) {
            delay(650)
            currentOnSuccess()
        }
    }

    fun mistake() {
        elapsedMs += SPARK_PENALTY_MS
        wrongFlash = true
        currentOnFeedback(false)
    }

    val secondsLeft = (((SPARK_TOTAL_MS - elapsedMs).coerceAtLeast(0)) + 999) / 1000
    val fraction = (1f - elapsedMs / SPARK_TOTAL_MS.toFloat()).coerceIn(0f, 1f)
    val timerColor = lerp(NF.Crimson, NF.Mint, fraction)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val dotColor = when {
                    done || i < step -> NF.Mint
                    i == step -> NF.Coral
                    else -> Color(0xFF2A3145)
                }
                Box(
                    modifier = Modifier
                        .size(width = if (i == step && !done) 28.dp else 10.dp, height = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(timerColor)
            )
        }
        Text(
            text = "${secondsLeft}s",
            color = NF.Slate,
            fontSize = 12.sp,
            style = NumberStyle,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        when {
            failed -> {
                Text(
                    text = "Spark fizzled",
                    color = NF.Crimson,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Time ran out. Give your neurons another shot.",
                    color = NF.Slate,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                GradientPillButton(text = "Try again", onClick = { attempt++ })
            }
            done -> {
                Text(
                    text = "Neurons firing!",
                    color = NF.Mint,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "+5 bonus Reels unlocked",
                    color = NF.Slate,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            step < 2 -> {
                val problem = problems[step]
                Text(
                    text = if (step == 0) "Quick math" else "One more",
                    color = NF.Slate,
                    fontSize = 12.sp
                )
                Text(
                    text = problem.text,
                    color = NF.Ice,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = NumberStyle,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    problem.options.forEach { option ->
                        SparkOption(
                            label = option.toString(),
                            flashWrong = false,
                            onClick = {
                                if (option == problem.answer) {
                                    currentOnFeedback(true)
                                    step++
                                } else {
                                    mistake()
                                }
                            }
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = if (showing) "Memorize the pattern" else "Tap the glowing nodes",
                    color = NF.Slate,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in 0..2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (col in 0..2) {
                                val index = row * 3 + col
                                val lit = if (showing) index in target else index in taps
                                SparkNode(
                                    lit = lit,
                                    wrong = wrongFlash,
                                    enabled = !showing,
                                    onClick = {
                                        if (index in target) {
                                            if (index !in taps) {
                                                taps = taps + index
                                                currentOnFeedback(true)
                                                if (taps.size == target.size) done = true
                                            }
                                        } else {
                                            taps = emptySet()
                                            mistake()
                                            reshow++
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SparkOption(label: String, flashWrong: Boolean, onClick: () -> Unit) {
    val bg = if (flashWrong) NF.Crimson.copy(alpha = 0.35f) else Color(0x22FFFFFF)
    Box(
        modifier = Modifier
            .size(width = 88.dp, height = 56.dp)
            .glassCard(RoundedCornerShape(18.dp))
            .background(bg)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = NF.Ice,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            style = NumberStyle
        )
    }
}

@Composable
private fun SparkNode(lit: Boolean, wrong: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        targetValue = when {
            wrong -> NF.Crimson
            lit -> NF.Mint
            else -> Color(0xFF1E2535)
        },
        animationSpec = tween(160),
        label = "node"
    )
    Box(
        modifier = Modifier
            .size(68.dp)
            .drawBehind {
                if (lit) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NF.Mint.copy(alpha = 0.40f), Color.Transparent),
                            center = center,
                            radius = size.minDimension * 0.85f
                        ),
                        radius = size.minDimension * 0.85f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
            }
            .clip(CircleShape)
            .background(fill)
            .noRippleClickable(enabled = enabled, onClick = onClick)
    )
}

/** Shared coral-to-sunset CTA pill. */
@Composable
fun GradientPillButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(NF.ctaBrush())
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GhostPillButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x1AFFFFFF))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = NF.Ice,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
