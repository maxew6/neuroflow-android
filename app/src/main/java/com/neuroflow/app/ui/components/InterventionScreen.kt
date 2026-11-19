package com.neuroflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.glassCard

/**
 * Frosted dark-glass takeover that appears at the daily Reel limit or Stage 5 (Critical Brain Freeze).
 * The person must either do a reset challenge or exit the app.
 */
@Composable
fun InterventionScreen(
    model: IslandUiModel,
    controller: IslandController,
    actions: IslandActions,
    modifier: Modifier = Modifier
) {
    var restore by remember { mutableFloatStateOf(0f) }
    val manual = controller.manualIntervention || model.isPreview

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE6090B10), Color(0xF2131722))
                )
            )
            .padding(top = 56.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (controller.page) {
            InterventionPage.Menu -> MenuPage(
                model = model,
                manual = manual,
                onBreathing = { controller.page = InterventionPage.Breathing },
                onSpark = { controller.page = InterventionPage.Spark },
                onExit = { actions.onExit() },
                onClose = {
                    if (model.isPreview) actions.onClosePreview() else controller.closeIntervention()
                }
            )

            InterventionPage.Breathing -> BreathingPage(
                restore = restore,
                onRestore = { restore = it },
                onBack = {
                    restore = 0f
                    controller.page = InterventionPage.Menu
                },
                onExit = { actions.onExit() },
                actions = actions
            )

            InterventionPage.Spark -> SparkPage(
                onSuccess = {
                    actions.onBonusReels(5)
                    controller.closeIntervention()
                },
                onBack = { controller.page = InterventionPage.Menu },
                actions = actions
            )
        }
    }
}

@Composable
private fun MenuPage(
    model: IslandUiModel,
    manual: Boolean,
    onBreathing: () -> Unit,
    onSpark: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit
) {
    BrainMascot(stage = 5, modifier = Modifier.size(136.dp))
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (manual) "Brain Reset" else "Brain Freeze Detected",
        color = NF.Ice,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )
    Text(
        text = "${model.reels} Reels today \u00B7 limit ${model.limit}. " +
            "Your prefrontal cortex is running hot. Pick a reset, or step away.",
        color = NF.Slate,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
    )

    ActionCard(
        title = "Re-Oxygenate Your Cortex",
        subtitle = "4-7-8 breathing \u00B7 about 40 seconds",
        onClick = onBreathing
    )
    Spacer(Modifier.height(10.dp))
    ActionCard(
        title = "Synapse Spark",
        subtitle = "30-second puzzle \u00B7 unlock +5 Reels",
        onClick = onSpark
    )
    Spacer(Modifier.height(18.dp))
    GradientPillButton(
        text = "Touch Grass (Exit)",
        modifier = Modifier.fillMaxWidth(),
        onClick = onExit
    )
    if (manual) {
        Spacer(Modifier.height(10.dp))
        GhostPillButton(
            text = if (model.isPreview) "Close preview" else "Close",
            onClick = onClose
        )
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(22.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            color = NF.Ice,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = NF.Slate,
            fontSize = 12.sp,
            style = NumberStyle,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ColumnScope.BreathingPage(
    restore: Float,
    onRestore: (Float) -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit,
    actions: IslandActions
) {
    var finished by remember { mutableStateOf(false) }
    Text(
        text = "Re-Oxygenate Your Cortex",
        color = NF.Ice,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    BrainMascot(stage = 5, restore = restore, modifier = Modifier.size(92.dp))
    Spacer(Modifier.height(4.dp))
    BreathingOrbChallenge(
        onRestoreProgress = onRestore,
        onComplete = { finished = true },
        onHaptic = { ms, amp -> actions.pulse(ms, amp) }
    )
    Spacer(Modifier.weight(1f))
    if (finished) {
        GradientPillButton(
            text = "Touch Grass (Exit)",
            modifier = Modifier.fillMaxWidth(),
            onClick = onExit
        )
        Spacer(Modifier.height(10.dp))
    }
    GhostPillButton(text = "Back", onClick = onBack)
}

@Composable
private fun ColumnScope.SparkPage(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    actions: IslandActions
) {
    Text(
        text = "Synapse Spark",
        color = NF.Ice,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold
    )
    Text(
        text = "Wake the prefrontal cortex. Finish before the timer runs out.",
        color = NF.Slate,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
    )
    SynapseSparkChallenge(
        modifier = Modifier.fillMaxWidth(),
        onSuccess = onSuccess,
        onFeedback = { correct ->
            if (correct) actions.tick() else actions.pulse(70L, 200)
        }
    )
    Spacer(Modifier.weight(1f))
    GhostPillButton(text = "Back", onClick = onBack)
}
