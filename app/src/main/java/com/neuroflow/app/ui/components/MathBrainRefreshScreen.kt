package com.neuroflow.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.glassCard
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val PROBLEMS_TO_SOLVE = 3
private const val PROBLEM_TIME_MS = 20_000
private const val MAX_ANSWER_DIGITS = 5

private class MathEquation(val text: String, val answer: Int)

/**
 * Multi-step arithmetic that needs real mental calculation. Every answer is a positive integer.
 * Standard: two operations. Hard: bigger numbers and three operations.
 */
private fun generateEquation(hard: Boolean, rnd: Random): MathEquation {
    val times = "\u00D7"
    val divide = "\u00F7"
    val minus = "\u2212"
    return if (!hard) {
        when (rnd.nextInt(3)) {
            0 -> {
                val a = rnd.nextInt(12, 31)
                val b = rnd.nextInt(3, 10)
                val c = rnd.nextInt(5, 25)
                MathEquation("($a $times $b) $minus $c = ?", a * b - c)
            }
            1 -> {
                val a = rnd.nextInt(12, 36)
                val b = rnd.nextInt(3, 10)
                val c = rnd.nextInt(8, 41)
                MathEquation("($a $times $b) + $c = ?", a * b + c)
            }
            else -> {
                val d = rnd.nextInt(6, 17)
                val q = rnd.nextInt(6, 17)
                val m = rnd.nextInt(3, 10)
                MathEquation("(${d * q} $divide $d) $times $m = ?", q * m)
            }
        }
    } else {
        when (rnd.nextInt(3)) {
            0 -> {
                val a = rnd.nextInt(21, 50)
                val b = rnd.nextInt(4, 10)
                val c = rnd.nextInt(11, 20)
                val d = rnd.nextInt(3, 9)
                val x = a * b
                val y = c * d
                // always subtract the smaller product so the answer stays positive
                if (x >= y) {
                    MathEquation("($a $times $b) $minus ($c $times $d) = ?", x - y)
                } else {
                    MathEquation("($c $times $d) $minus ($a $times $b) = ?", y - x)
                }
            }
            1 -> {
                val d = rnd.nextInt(7, 20)
                val q = rnd.nextInt(7, 20)
                val m = rnd.nextInt(4, 13)
                val c = rnd.nextInt(11, 50)
                MathEquation("(${d * q} $divide $d) $times $m + $c = ?", q * m + c)
            }
            else -> {
                val a = rnd.nextInt(13, 40)
                val b = rnd.nextInt(3, 10)
                val c = rnd.nextInt(7, 20)
                val d = rnd.nextInt(3, 10)
                val e = rnd.nextInt(10, 51)
                MathEquation("($a $times $b) + ($c $times $d) $minus $e = ?", a * b + c * d - e)
            }
        }
    }
}

/**
 * Full-screen Strict Reels Blocker. Shown when the daily limit is reached and Reels are blocked.
 * The person can leave Reels (stay safe) or solve 3 calculations to unlock +5 Reels.
 */
@Composable
fun MathBrainRefreshScreen(
    hard: Boolean,
    isTest: Boolean,
    onLeave: () -> Unit,
    onSolved: () -> Unit,
    onTick: () -> Unit,
    onWrong: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rnd = remember { Random(System.nanoTime()) }
    var started by remember { mutableStateOf(false) }
    var solved by remember { mutableIntStateOf(0) }
    var problemId by remember { mutableIntStateOf(0) }
    var shakeTick by remember { mutableIntStateOf(0) }
    val equation = remember(problemId) { generateEquation(hard, rnd) }
    var input by remember(problemId) { mutableStateOf("") }
    var elapsedMs by remember(problemId) { mutableIntStateOf(0) }

    val restore by animateFloatAsState(
        targetValue = solved / PROBLEMS_TO_SOLVE.toFloat(),
        animationSpec = tween(600),
        label = "brainRecovery"
    )
    val shake = remember { Animatable(0f) }

    // 20-second countdown per problem. Time out = a fresh equation (progress is kept).
    LaunchedEffect(problemId, started, solved) {
        if (started && solved < PROBLEMS_TO_SOLVE) {
            while (elapsedMs < PROBLEM_TIME_MS) {
                delay(100)
                elapsedMs += 100
            }
            onWrong()
            problemId += 1
        }
    }

    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            for (target in floatArrayOf(14f, -14f, 10f, -10f, 5f, 0f)) {
                shake.animateTo(target, tween(45))
            }
        }
    }

    LaunchedEffect(solved) {
        if (solved >= PROBLEMS_TO_SOLVE) {
            delay(1000)
            onSolved()
        }
    }

    fun submit() {
        val answer = input.toIntOrNull() ?: return
        if (answer == equation.answer) {
            onTick()
            solved += 1
            if (solved < PROBLEMS_TO_SOLVE) problemId += 1
        } else {
            shakeTick += 1
            onWrong()
            problemId += 1 // wrong answer: brand-new equation
        }
    }

    fun press(label: String) {
        onTick()
        when (label) {
            "Clear" -> input = ""
            "\u232B" -> input = input.dropLast(1)
            else -> if (input.length < MAX_ANSWER_DIGITS) input += label
        }
    }

    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(Color(0xF2090B10), Color(0xF8131722))))
            .verticalScroll(rememberScrollState())
            .padding(top = 52.dp, bottom = 32.dp, start = 22.dp, end = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrainMascot(
            stage = 5,
            restore = restore,
            modifier = Modifier.size(if (started) 92.dp else 132.dp)
        )
        Spacer(Modifier.height(6.dp))

        if (!started) {
            Text(
                text = "Daily Limit Reached! Reels Are Strictly Blocked.",
                color = NF.Ice,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isTest) {
                    "Test mode: nothing is changed on your phone."
                } else {
                    "Your brain is overheated. Leave Reels, or wake your prefrontal cortex with three " +
                        "real calculations to earn 5 more Reels."
                },
                color = NF.Slate,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 26.dp)
            )
            GradientPillButton(
                text = "Leave Reels & Keep Brain Healthy",
                modifier = Modifier.fillMaxWidth(),
                onClick = onLeave
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(RoundedCornerShape(50))
                    .noRippleClickable { started = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Solve 3 Math Challenges for +5 Reels",
                    color = NF.Ice,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            RecoveryMeter(solved = solved)
            Spacer(Modifier.height(12.dp))

            val fraction = (1f - elapsedMs / PROBLEM_TIME_MS.toFloat()).coerceIn(0f, 1f)
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
                        .background(lerp(NF.Crimson, NF.Mint, fraction))
                )
            }
            Text(
                text = "${(PROBLEM_TIME_MS - elapsedMs).coerceAtLeast(0) / 1000 + 1}s",
                color = NF.Slate,
                fontSize = 12.sp,
                style = NumberStyle,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            if (solved >= PROBLEMS_TO_SOLVE) {
                Text(
                    text = "Brain refreshed!",
                    color = NF.Mint,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "+5 Reels unlocked. Use them wisely.",
                    color = NF.Slate,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = shake.value }
                        .glassCard(RoundedCornerShape(22.dp))
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = equation.text,
                            color = NF.Ice,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = NumberStyle,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (input.isEmpty()) "Type your answer" else input,
                            color = if (input.isEmpty()) NF.Slate else NF.Amber,
                            fontSize = if (input.isEmpty()) 15.sp else 32.sp,
                            fontWeight = FontWeight.Bold,
                            style = NumberStyle
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Clear", "0", "\u232B")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { label ->
                                KeypadKey(
                                    label = label,
                                    modifier = Modifier.weight(1f),
                                    onClick = { press(label) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GradientPillButton(
                    text = "Submit",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { submit() }
                )
                Spacer(Modifier.height(8.dp))
                GhostPillButton(text = "Leave Reels Now", onClick = onLeave)
            }
        }
    }
}

@Composable
private fun RecoveryMeter(solved: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "BRAIN RECOVERY  $solved/$PROBLEMS_TO_SOLVE",
            color = NF.Slate,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(PROBLEMS_TO_SOLVE) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i < solved) NF.Mint else Color(0x26FFFFFF))
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .glassCard(RoundedCornerShape(16.dp))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = NF.Ice,
            fontSize = if (label.length > 1) 14.sp else 22.sp,
            fontWeight = FontWeight.SemiBold,
            style = NumberStyle
        )
    }
}
