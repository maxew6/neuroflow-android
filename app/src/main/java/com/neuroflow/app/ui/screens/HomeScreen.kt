package com.neuroflow.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.data.NeuroFlowState
import com.neuroflow.app.data.stageLabel
import com.neuroflow.app.ui.components.AppCallbacks
import com.neuroflow.app.ui.components.BrainMascot
import com.neuroflow.app.ui.components.DynamicIslandHost
import com.neuroflow.app.ui.components.GhostPillButton
import com.neuroflow.app.ui.components.IslandActions
import com.neuroflow.app.ui.components.IslandController
import com.neuroflow.app.ui.components.OdometerCounter
import com.neuroflow.app.ui.components.PermissionRow
import com.neuroflow.app.ui.components.SectionLabel
import com.neuroflow.app.ui.components.ShimmerHeadline
import com.neuroflow.app.ui.components.buildIslandModel
import com.neuroflow.app.ui.components.speedbumpText
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.auraColorFor
import com.neuroflow.app.ui.theme.glassCard
import com.neuroflow.app.util.PermissionStatus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: NeuroFlowState,
    perms: PermissionStatus,
    callbacks: AppCallbacks,
    onPreviewReelsChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "NEUROFLOW",
            color = NF.Mint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(6.dp))
        ShimmerHeadline(text = "Fewer Reels.\nBrighter Mind.")
        Text(
            text = "Watch your brain in real time. NeuroFlow glides a living Dynamic Island over " +
                "Instagram and nudges you out before the doomscroll takes over.",
            color = NF.Slate,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
        )

        if (perms.accessibility && !state.serviceConnected) {
            SensorWarning()
            Spacer(Modifier.height(14.dp))
        }

        ComparisonRow(state)

        Spacer(Modifier.height(18.dp))
        SectionLabel(if (perms.coreGranted) "System status" else "Wake up your Dynamic Island")
        PermissionSetupCard(perms, callbacks)

        Spacer(Modifier.height(18.dp))
        SectionLabel("Live simulator")
        SimulatorCard(state, callbacks, onPreviewReelsChange)
    }
}

@Composable
private fun SensorWarning() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "The Reel sensor is switched on but not running",
            color = NF.Amber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "The phone has put it to sleep, so nothing is detected. Open Accessibility settings, " +
                "switch NeuroFlow Reel Scroll Sensor off and on again, then set Unrestricted battery below. " +
                "Settings > Sensor health shows what is happening live.",
            color = NF.Slate,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Without vs With NeuroFlow
// ---------------------------------------------------------------------------------------------

@Composable
private fun ComparisonRow(state: NeuroFlowState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CompareCard(
            modifier = Modifier.weight(1f),
            caption = "WITHOUT NEUROFLOW",
            captionColor = NF.Crimson,
            stage = 4
        ) {
            Text(
                text = "500",
                color = NF.Slate,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                style = NumberStyle
            )
            Text(
                text = "reels a day",
                color = NF.Slate.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
        CompareCard(
            modifier = Modifier.weight(1f),
            caption = "WITH NEUROFLOW",
            captionColor = NF.Mint,
            stage = 1
        ) {
            OdometerCounter(
                count = state.reelsToday,
                fontSize = 36.sp,
                color = NF.Amber,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "of ${state.dailyLimit} reels today",
                color = NF.Slate,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CompareCard(
    modifier: Modifier,
    caption: String,
    captionColor: Color,
    stage: Int,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .glassCard(RoundedCornerShape(26.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = caption,
            color = captionColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        Spacer(Modifier.height(6.dp))
        BrainMascot(stage = stage, modifier = Modifier.size(112.dp))
        Spacer(Modifier.height(4.dp))
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Permission onboarding (shown inline and inside the first-run bottom sheet)
// ---------------------------------------------------------------------------------------------

@Composable
fun PermissionSetupCard(perms: PermissionStatus, callbacks: AppCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionRow(
            title = "Display over other apps",
            subtitle = "Lets the Dynamic Island float above Instagram.",
            granted = perms.overlay,
            onClick = callbacks.openOverlaySettings
        )
        PermissionRow(
            title = "Reel Scroll Sensor",
            subtitle = "Accessibility access so NeuroFlow can count Reel swipes. Nothing leaves your device.",
            granted = perms.accessibility,
            onClick = callbacks.openAccessibilitySettings
        )
        PermissionRow(
            title = "Notifications",
            subtitle = "Keeps the island's guard service running smoothly.",
            granted = perms.notifications,
            onClick = callbacks.requestNotifications
        )
        PermissionRow(
            title = "Unrestricted battery",
            subtitle = "Stops the phone from putting the Reel sensor to sleep. Important on Realme, " +
                "Xiaomi, Vivo, OPPO and Samsung.",
            granted = perms.battery,
            onClick = callbacks.requestBatteryUnrestricted
        )
        if (perms.coreGranted) {
            Text(
                text = "All set. Open Instagram and the island will glide in on its own.",
                color = NF.Mint,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dynamic Island simulator
// ---------------------------------------------------------------------------------------------

@Composable
private fun SimulatorCard(
    state: NeuroFlowState,
    callbacks: AppCallbacks,
    onReelsChange: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val controller = remember { IslandController().also { it.visible = true } }
    val clockStart = remember { System.currentTimeMillis() }
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val model = buildIslandModel(state, nowMs, clockStart, forcePreview = true)

    val actions = remember(ctx) {
        object : IslandActions {
            override fun onPause15() {
                Toast.makeText(ctx, "In real use this pauses nudges for 15 minutes.", Toast.LENGTH_SHORT).show()
            }

            override fun onExit() {
                Toast.makeText(ctx, "In real use this sends you home, out of Reels.", Toast.LENGTH_SHORT).show()
            }

            override fun onBonusReels(extra: Int) = Unit
            override fun onClosePreview() = Unit
            override fun tick() = Unit
            override fun pulse(durationMs: Long, amplitude: Int) = Unit

            override fun onBrainResetUnavailable() {
                Toast.makeText(
                    ctx,
                    "Switch on the live preview below to try the full-screen Brain Reset.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Dynamic Island Simulator",
            color = NF.Ice,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tap the pill to expand it. Slide the Reels to watch the brain melt through all five stages.",
            color = NF.Slate,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(246.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF05070C), Color(0xFF0C1019)))
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            DynamicIslandHost(
                model = model,
                controller = controller,
                actions = actions,
                allowIntervention = false,
                autoEvents = false
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Reels: ${state.previewReels}",
                color = NF.Ice,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                style = NumberStyle
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stageLabel(model.stage),
                color = auraColorFor(state.previewReels),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = state.previewReels.toFloat(),
            onValueChange = { onReelsChange(it.roundToInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = NF.Coral,
                activeTrackColor = NF.Coral,
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (st in 1..5) {
                val active = st == model.stage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (active) auraColorFor(state.previewReels) else Color(0x22FFFFFF))
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Launch Live Dynamic Island Preview",
                    color = NF.Ice,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Floats the real island over every app. Drag it, tap it, push the slider past 75 for the full-screen reset.",
                    color = NF.Slate,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp, end = 10.dp)
                )
            }
            Switch(
                checked = state.previewEnabled,
                onCheckedChange = callbacks.setPreview,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NF.Coral,
                    uncheckedThumbColor = NF.Slate,
                    uncheckedTrackColor = Color(0x33FFFFFF),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostPillButton(
                text = "Test the speedbump",
                modifier = Modifier.fillMaxWidth(),
                onClick = { controller.showSpeedbump(speedbumpText(15)) }
            )
            GhostPillButton(
                text = "Test Math Blocker Overlay",
                modifier = Modifier.fillMaxWidth(),
                onClick = callbacks.testBlocker
            )
        }
    }
}
