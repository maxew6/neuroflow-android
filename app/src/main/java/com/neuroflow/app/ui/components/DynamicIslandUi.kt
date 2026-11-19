package com.neuroflow.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.data.NeuroFlowState
import com.neuroflow.app.data.formatClock
import com.neuroflow.app.data.stageForReels
import com.neuroflow.app.data.stageLabel
import com.neuroflow.app.data.vitalityFor
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.auraColorFor
import kotlinx.coroutines.delay
import kotlin.math.sin

private const val TAU_F = 6.2831855f

/** Compact pill size (v1.1): fixed 186 x 38. */
private val COMPACT_W = 186.dp
private val COMPACT_H = 38.dp

// =============================================================================================
// Shared models / controller
// =============================================================================================

enum class IslandMode { Compact, Expanded, Speedbump, Intervention, Lockout }

enum class InterventionPage { Menu, Breathing, Spark }

@Immutable
data class IslandUiModel(
    val reels: Int,
    val sessionReels: Int,
    val stage: Int,
    val vitality: Int,
    val sessionSeconds: Long,
    val scrollMinutesToday: Int,
    val limit: Int,
    val speedbumpEvery: Int,
    val pausedMinutesLeft: Int,
    val forceIntervention: Boolean,
    val isPreview: Boolean,
    val blockerActive: Boolean,
    val blockerIsTest: Boolean,
    val mathHard: Boolean
)

interface IslandActions {
    fun onPause15()
    fun onExit()
    fun onBonusReels(extra: Int)
    fun onClosePreview()
    fun tick()
    fun pulse(durationMs: Long, amplitude: Int)
    fun onBrainResetUnavailable() {}

    /** Strict lockout: "Leave Reels & Keep Brain Healthy". */
    fun onLeaveReels() {}

    /** Strict lockout: all three math challenges solved, unlock the bonus Reels. */
    fun onMathSolved() {}
}

/** Compose-state holder for the island. The overlay service owns one; the in-app simulator owns another. */
class IslandController {
    var visible by mutableStateOf(false)
    var mode by mutableStateOf(IslandMode.Compact)
    var page by mutableStateOf(InterventionPage.Menu)
    var manualIntervention by mutableStateOf(false)
    var speedbumpMessage by mutableStateOf("")
    var autoExpanded: Boolean = false

    fun toggleExpanded() {
        when (mode) {
            IslandMode.Compact -> {
                autoExpanded = false
                mode = IslandMode.Expanded
            }
            IslandMode.Expanded -> {
                autoExpanded = false
                mode = IslandMode.Compact
            }
            else -> Unit
        }
    }

    fun showSpeedbump(message: String) {
        if (mode == IslandMode.Compact || mode == IslandMode.Expanded) {
            speedbumpMessage = message
            mode = IslandMode.Speedbump
        }
    }

    fun openIntervention(manual: Boolean) {
        manualIntervention = manual
        page = InterventionPage.Menu
        mode = IslandMode.Intervention
    }

    fun closeIntervention() {
        manualIntervention = false
        page = InterventionPage.Menu
        mode = IslandMode.Compact
    }

    fun openLockout() {
        manualIntervention = false
        page = InterventionPage.Menu
        mode = IslandMode.Lockout
    }

    fun closeLockout() {
        if (mode == IslandMode.Lockout) mode = IslandMode.Compact
    }

    fun reset() {
        visible = false
        mode = IslandMode.Compact
        page = InterventionPage.Menu
        manualIntervention = false
        autoExpanded = false
    }
}

fun buildIslandModel(
    s: NeuroFlowState,
    nowMs: Long,
    previewClockStartMs: Long,
    forcePreview: Boolean = false
): IslandUiModel {
    val preview = forcePreview || s.previewEnabled
    val reels = if (preview) s.previewReels else s.reelsToday
    val activeElapsedMs =
        if (s.sessionActiveSinceMs > 0L) (nowMs - s.sessionActiveSinceMs).coerceAtLeast(0L) else 0L
    val sessionSeconds = if (preview) {
        252L + ((nowMs - previewClockStartMs) / 1000L).coerceAtLeast(0L)
    } else {
        (s.sessionAccumulatedMs + activeElapsedMs) / 1000L
    }
    val scrollMinutes = if (preview) {
        38
    } else {
        ((s.scrollSecondsToday + activeElapsedMs / 1000L) / 60L).toInt()
    }
    val limit = s.dailyLimit
    val paused = s.isPaused(nowMs)
    val pausedMinutes = if (paused) (((s.pausedUntilMs - nowMs) + 59_999L) / 60_000L).toInt() else 0
    // Stage 5 = daily limit reached with no unlocked bonus Reels (strict blocking is active).
    val blocked = !preview && s.isLimitBlocked
    val stage = if (blocked) 5 else stageForReels(reels)
    // Real limit enforcement is the strict Math lockout; the Brain Reset takeover is only auto-opened
    // by the simulator at 76+ so every stage can be previewed.
    val force = preview && !paused && reels >= 76
    return IslandUiModel(
        reels = reels,
        sessionReels = if (preview) reels else s.sessionReels,
        stage = stage,
        vitality = vitalityFor(reels, limit),
        sessionSeconds = sessionSeconds,
        scrollMinutesToday = scrollMinutes,
        limit = limit,
        speedbumpEvery = s.speedbumpEvery,
        pausedMinutesLeft = pausedMinutes,
        forceIntervention = force,
        isPreview = preview,
        blockerActive = s.blockerActive,
        blockerIsTest = s.blockerIsTest,
        mathHard = s.mathHard
    )
}

fun speedbumpText(sessionReels: Int): String {
    val meters = sessionReels * 3
    return when ((sessionReels / 5) % 3) {
        0 -> "$sessionReels Reels in a row \u2014 your thumb just scrolled $meters meters. Take a breath?"
        1 -> "$sessionReels Reels deep. That's $meters meters of thumb travel. Your brain deserves a breather."
        else -> "Pause. $sessionReels Reels in one sitting. Look up, unclench your jaw, take a breath?"
    }
}

// =============================================================================================
// Small helpers
// =============================================================================================

internal fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    }

/** Lays the content out at a fixed size, top-centred, so it never reflows while the pill springs open. */
private fun Modifier.fixedTopCenter(width: Dp, height: Dp): Modifier =
    this.layout { measurable, constraints ->
        val pw = width.roundToPx()
        val ph = height.roundToPx()
        val placeable = measurable.measure(Constraints.fixed(pw, ph))
        val lw = if (constraints.hasBoundedWidth) constraints.maxWidth else pw
        val lh = if (constraints.hasBoundedHeight) constraints.maxHeight else ph
        layout(lw, lh) { placeable.place((lw - pw) / 2, 0) }
    }

/** Sweep-gradient brush whose start angle rotates, giving the neon border a travelling highlight. */
private class RotatingSweepBrush(private val color: Color, private val angle: Float) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val faint = color.copy(alpha = 0.08f).toArgb()
        val strong = color.toArgb()
        val shader = android.graphics.SweepGradient(
            cx,
            cy,
            intArrayOf(strong, faint, faint, strong),
            floatArrayOf(0f, 0.35f, 0.75f, 1f)
        )
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle, cx, cy)
        shader.setLocalMatrix(matrix)
        return shader
    }
}

/** Rolling-digit odometer: every digit slides and scales vertically when the count changes. */
@Composable
fun OdometerCounter(
    count: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    color: Color = NF.Ice,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val previous = remember { intArrayOf(count) }
    val goingUp = count >= previous[0]
    SideEffect { previous[0] = count }
    val text = count.coerceAtLeast(0).toString()

    Row(modifier = modifier) {
        text.forEachIndexed { index, ch ->
            val place = text.length - index
            key(place) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        if (goingUp) {
                            (slideInVertically { h -> h } + fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith
                                (slideOutVertically { h -> -h } + fadeOut() + scaleOut(targetScale = 0.7f))
                        } else {
                            (slideInVertically { h -> -h } + fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith
                                (slideOutVertically { h -> h } + fadeOut() + scaleOut(targetScale = 0.7f))
                        }
                    },
                    label = "odometerDigit"
                ) { digit ->
                    Text(
                        text = digit.toString(),
                        color = color,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        style = NumberStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
internal fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(NF.ctaBrush())
                else Modifier
                    .background(Color(0x1FFFFFFF))
                    .border(1.dp, NF.specularRim(), shape)
            )
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun VitalityBar(vitality: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = vitality / 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "vitality"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(NF.vitalityBrush())
        )
    }
}

/** Continuously breathing radial aura: Mint/Sakura when healthy, Amber/Crimson when overstimulated. */
@Composable
private fun LivingGlow(reels: Int, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "mesh")
    val breathe by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(2600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "drift"
    )
    val heat = (reels / 75f).coerceIn(0f, 1f)
    val warm = lerp(NF.Mint, NF.Amber, (heat * 1.6f).coerceIn(0f, 1f))
    val c1 = lerp(warm, NF.Crimson, ((heat - 0.6f) / 0.4f).coerceIn(0f, 1f))
    val c2 = lerp(NF.Sakura, NF.Crimson, heat)

    Canvas(modifier = modifier) {
        val a = 0.5f + 0.5f * sin(drift * TAU_F)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(c1.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * (0.25f + 0.2f * a), size.height * 0.2f),
                radius = size.width * 0.65f * breathe
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(c2.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(size.width * (0.8f - 0.2f * a), size.height * 0.9f),
                radius = size.width * 0.7f * breathe
            )
        )
    }
}

// =============================================================================================
// Host
// =============================================================================================

/**
 * The fluid Dynamic Island. Used both by the WindowManager overlay service and by the in-app simulator.
 *
 * @param allowIntervention false in the in-app inline preview (the full-screen mode needs a real overlay window)
 * @param autoEvents        milestone expansion, speedbumps and haptic ticks (off for the inline preview)
 */
@Composable
fun DynamicIslandHost(
    model: IslandUiModel,
    controller: IslandController,
    actions: IslandActions,
    modifier: Modifier = Modifier,
    allowIntervention: Boolean = true,
    autoEvents: Boolean = true
) {
    val lastStage = remember { intArrayOf(model.stage) }
    val lastSession = remember { intArrayOf(model.sessionReels) }
    val lastCount = remember { intArrayOf(model.reels) }

    // Crisp haptic tick on every +1 Reel.
    LaunchedEffect(model.reels) {
        if (autoEvents && model.reels > lastCount[0]) actions.tick()
        lastCount[0] = model.reels
    }

    // Milestone: the pod springs open for a moment when the brain slips into a worse stage.
    LaunchedEffect(model.stage) {
        if (autoEvents &&
            model.stage > lastStage[0] &&
            model.stage < 5 &&
            controller.mode == IslandMode.Compact
        ) {
            controller.autoExpanded = true
            controller.mode = IslandMode.Expanded
            delay(5000)
            if (controller.mode == IslandMode.Expanded && controller.autoExpanded) {
                controller.mode = IslandMode.Compact
            }
        }
        lastStage[0] = model.stage
    }

    // Dopamine speedbump every N Reels of the current session.
    LaunchedEffect(model.sessionReels) {
        if (autoEvents &&
            model.sessionReels > lastSession[0] &&
            model.speedbumpEvery > 0 &&
            model.sessionReels % model.speedbumpEvery == 0 &&
            model.pausedMinutesLeft == 0 &&
            !model.forceIntervention
        ) {
            controller.showSpeedbump(speedbumpText(model.sessionReels))
        }
        lastSession[0] = model.sessionReels
    }

    // Speedbump auto-collapses after 4 seconds.
    LaunchedEffect(controller.mode, controller.speedbumpMessage) {
        if (controller.mode == IslandMode.Speedbump) {
            delay(4000)
            if (controller.mode == IslandMode.Speedbump) controller.mode = IslandMode.Compact
        }
    }

    // Daily limit / Stage 5 opens the full-screen intervention.
    LaunchedEffect(model.forceIntervention, allowIntervention) {
        if (allowIntervention) {
            if (model.forceIntervention) {
                if (controller.mode != IslandMode.Intervention) controller.openIntervention(manual = false)
            } else if (controller.mode == IslandMode.Intervention && !controller.manualIntervention) {
                controller.closeIntervention()
            }
        }
    }

    // Strict blocker: the accessibility service requests the full-screen Math Brain Refresh lockout.
    LaunchedEffect(model.blockerActive, allowIntervention) {
        if (allowIntervention) {
            if (model.blockerActive) {
                if (controller.mode != IslandMode.Lockout) controller.openLockout()
            } else if (controller.mode == IslandMode.Lockout) {
                controller.mode = IslandMode.Compact
            }
        }
    }

    val visibleState = remember { MutableTransitionState(false) }
    val islandVisible = controller.visible // read in composition so changes recompose the host
    SideEffect { visibleState.targetState = islandVisible }

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(tween(200)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialScale = 0.6f,
            transformOrigin = TransformOrigin(0.5f, 0f)
        ),
        exit = fadeOut(tween(200)) + scaleOut(
            targetScale = 0.6f,
            transformOrigin = TransformOrigin(0.5f, 0f)
        )
    ) {
        IslandSurface(model, controller, actions, allowIntervention)
    }
}

@Composable
private fun IslandSurface(
    model: IslandUiModel,
    controller: IslandController,
    actions: IslandActions,
    allowIntervention: Boolean
) {
    val mode = controller.mode
    val springDp = spring<Dp>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
    val springFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    BoxWithConstraints(modifier = Modifier.wrapContentSize(Alignment.TopCenter)) {
        val fullW = maxWidth
        val fullH = maxHeight
        val podW = minOf(348.dp, fullW - 28.dp)

        val fullScreen = mode == IslandMode.Intervention || mode == IslandMode.Lockout
        val targetW = when (mode) {
            IslandMode.Compact -> COMPACT_W
            IslandMode.Expanded, IslandMode.Speedbump -> podW
            IslandMode.Intervention, IslandMode.Lockout -> fullW
        }
        val targetH = when (mode) {
            IslandMode.Compact -> COMPACT_H
            IslandMode.Expanded -> 205.dp
            IslandMode.Speedbump -> 128.dp
            IslandMode.Intervention, IslandMode.Lockout -> fullH
        }
        val targetCorner = when (mode) {
            IslandMode.Compact -> 50.dp
            IslandMode.Expanded, IslandMode.Speedbump -> 28.dp
            IslandMode.Intervention, IslandMode.Lockout -> 0.dp
        }
        val targetPad = if (fullScreen) 0.dp else 14.dp
        val targetGlass = if (mode == IslandMode.Compact) 0f else 1f

        // Spring-physics morphing between every state.
        val w by animateDpAsState(targetW, springDp, label = "islandWidth")
        val h by animateDpAsState(targetH, springDp, label = "islandHeight")
        val corner by animateDpAsState(targetCorner, springDp, label = "islandCorner")
        val pad by animateDpAsState(targetPad, springDp, label = "islandPad")
        val glass by animateFloatAsState(targetGlass, springFloat, label = "islandGlass")
        val aura by animateColorAsState(
            targetValue = auraColorFor(model.reels),
            animationSpec = tween(700),
            label = "islandAura"
        )

        val infinite = rememberInfiniteTransition(label = "islandFx")
        val rotation by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
            label = "rotation"
        )
        val pulse by infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "pulse"
        )

        val cornerSafe = corner.coerceAtLeast(0.dp)
        val shape = RoundedCornerShape(cornerSafe)
        val glassAlpha = glass.coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .size(width = w + pad * 2, height = h + pad * 2)
                .drawBehind {
                    val p = pad.toPx()
                    val innerW = size.width - 2f * p
                    val innerH = size.height - 2f * p
                    if (p > 1f && innerW > 0f && innerH > 0f) {
                        val r = cornerSafe.toPx()
                        for (i in 1..6) {
                            drawRoundRect(
                                color = aura.copy(alpha = (0.22f * pulse) / i),
                                topLeft = Offset(p, p),
                                size = Size(innerW, innerH),
                                cornerRadius = CornerRadius(r),
                                style = Stroke(width = (i * 3.4f).dp.toPx())
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = w, height = h)
                    .clip(shape)
                    .background(NF.OledBlack)
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            brush = RotatingSweepBrush(aura, rotation),
                            cornerRadius = CornerRadius(cornerSafe.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    .noRippleClickable(
                        enabled = mode == IslandMode.Compact || mode == IslandMode.Expanded
                    ) { controller.toggleExpanded() }
            ) {
                // Elevated glass layer + living radial aura fade in as the pill opens.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(glassAlpha)
                        .background(
                            Brush.verticalGradient(listOf(NF.GlassOverlay, Color(0xEB131722)))
                        )
                )
                if (glassAlpha > 0.02f) {
                    LivingGlow(
                        reels = model.reels,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(glassAlpha)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, NF.specularRim(), shape)
                )

                AnimatedContent(
                    targetState = mode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(240, delayMillis = 120)) togetherWith fadeOut(tween(110))
                    },
                    contentAlignment = Alignment.TopCenter,
                    label = "islandContent"
                ) { m ->
                    when (m) {
                        IslandMode.Compact -> CompactContent(model, aura)
                        IslandMode.Expanded -> ExpandedContent(
                            model = model,
                            aura = aura,
                            podW = podW,
                            onPause = { actions.onPause15() },
                            onBrainReset = {
                                if (allowIntervention) controller.openIntervention(manual = true)
                                else actions.onBrainResetUnavailable()
                            },
                            onExit = { actions.onExit() }
                        )
                        IslandMode.Speedbump -> SpeedbumpContent(
                            model = model,
                            message = controller.speedbumpMessage,
                            podW = podW,
                            aura = aura,
                            onExit = { actions.onExit() },
                            onDismiss = { controller.mode = IslandMode.Compact }
                        )
                        IslandMode.Intervention -> InterventionScreen(
                            model = model,
                            controller = controller,
                            actions = actions,
                            modifier = Modifier.fillMaxSize()
                        )
                        IslandMode.Lockout -> MathBrainRefreshScreen(
                            hard = model.mathHard,
                            isTest = model.blockerIsTest,
                            onLeave = {
                                actions.onLeaveReels()
                                controller.closeLockout()
                            },
                            onSolved = {
                                actions.onMathSolved()
                                controller.closeLockout()
                            },
                            onTick = { actions.tick() },
                            onWrong = { actions.pulse(70L, 200) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================================
// State layouts
// =============================================================================================

/**
 * Compact pill (186 x 38). Brain mascot, monospace session timer and Reel badge all sit on one
 * exact horizontal centerline: SpaceBetween row, 12.dp side padding, every text single-line and clipped.
 */
@Composable
private fun CompactContent(model: IslandUiModel, aura: Color) {
    Row(
        modifier = Modifier
            .fixedTopCenter(COMPACT_W, COMPACT_H)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            BrainMascot(
                stage = model.stage,
                modifier = Modifier.size(24.dp),
                showAura = false
            )
        }
        Text(
            text = formatClock(model.sessionSeconds),
            color = NF.Ice,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(aura.copy(alpha = 0.22f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OdometerCounter(count = model.reels, fontSize = 11.sp, color = NF.Amber)
            Text(
                text = " Reels",
                color = NF.Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

/** Expanded glass pod (348 x 205): vitality, stats and the three pill buttons. */
@Composable
private fun ExpandedContent(
    model: IslandUiModel,
    aura: Color,
    podW: Dp,
    onPause: () -> Unit,
    onBrainReset: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fixedTopCenter(podW, 205.dp)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrainMascot(
                stage = model.stage,
                modifier = Modifier.size(62.dp),
                showAura = false
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BRAIN STATE",
                    color = NF.Slate,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = stageLabel(model.stage),
                    color = NF.Ice,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = if (model.pausedMinutesLeft > 0) {
                        "Paused \u00B7 ${model.pausedMinutesLeft}m left"
                    } else {
                        "${model.reels} / ${model.limit} Reels today"
                    },
                    color = aura,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    style = NumberStyle,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${model.vitality}%",
                    color = NF.Ice,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = NumberStyle
                )
                Text(text = "vitality", color = NF.Slate, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        VitalityBar(vitality = model.vitality)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${model.scrollMinutesToday} min scrolling today",
                color = NF.Slate,
                fontSize = 11.sp,
                style = NumberStyle,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Session ${formatClock(model.sessionSeconds)}",
                color = NF.Slate,
                fontSize = 11.sp,
                style = NumberStyle,
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(text = "Pause 15m", modifier = Modifier.weight(1f), onClick = onPause)
            PillButton(text = "Brain Reset", modifier = Modifier.weight(1f), onClick = onBrainReset)
            PillButton(
                text = "Exit Reels",
                modifier = Modifier.weight(1f),
                primary = true,
                onClick = onExit
            )
        }
    }
}

/** Reality-check drop-down shown for 4 seconds every N Reels. */
@Composable
private fun SpeedbumpContent(
    model: IslandUiModel,
    message: String,
    podW: Dp,
    aura: Color,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(message) {
        progress.snapTo(1f)
        progress.animateTo(0f, tween(4000, easing = LinearEasing))
    }
    Box(modifier = Modifier.fixedTopCenter(podW, 128.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrainMascot(
                    stage = model.stage,
                    modifier = Modifier.size(48.dp),
                    showAura = false
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = message,
                    color = NF.Ice,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    text = "Touch Grass (Exit)",
                    modifier = Modifier.weight(1.6f),
                    primary = true,
                    onClick = onExit
                )
                PillButton(
                    text = "Keep going",
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                .height(2.dp)
                .background(aura)
        )
    }
}
