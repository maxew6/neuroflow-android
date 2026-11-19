package com.neuroflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.data.NeuroFlowRepository
import com.neuroflow.app.data.NeuroFlowState
import com.neuroflow.app.data.TrackedApp
import com.neuroflow.app.ui.components.AppCallbacks
import com.neuroflow.app.ui.components.GhostPillButton
import com.neuroflow.app.ui.components.SectionLabel
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.glassCard
import com.neuroflow.app.ui.components.noRippleClickable
import com.neuroflow.app.util.PermissionStatus
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    repo: NeuroFlowRepository,
    state: NeuroFlowState,
    perms: PermissionStatus,
    callbacks: AppCallbacks
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Settings",
            color = NF.Ice,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Tune how firmly NeuroFlow guards your focus.",
            color = NF.Slate,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        SectionLabel("Sensor health")
        SensorHealthCard(state = state, perms = perms, callbacks = callbacks)

        Spacer(Modifier.height(10.dp))
        SectionLabel("Guard")
        SettingsCard {
            ToggleRow(
                title = "NeuroFlow monitoring",
                subtitle = "Master switch for the Reel counter and Dynamic Island.",
                checked = state.monitoringEnabled,
                onChange = callbacks.setMonitoring
            )
            Divider()
            ToggleRow(
                title = "Strict blocking at the limit",
                subtitle = "At the daily limit, Reels and Shorts are blocked until you solve the math " +
                    "challenge. Turn off to only count and nudge.",
                checked = state.strictBlockEnabled,
                onChange = { repo.setStrictBlock(it) }
            )
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Reel limit",
                        color = NF.Ice,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "The full-screen Brain Reset appears when you reach it.",
                        color = NF.Slate,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                Text(
                    text = "${state.dailyLimit}",
                    color = NF.Amber,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = NumberStyle
                )
            }
            Slider(
                value = state.dailyLimit.toFloat(),
                onValueChange = { repo.setDailyLimit(it.roundToInt()) },
                valueRange = 5f..150f,
                colors = sliderColors()
            )
            Divider()
            Text(
                text = "Speedbump every",
                color = NF.Ice,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "A reality-check drops down after this many Reels in one sitting.",
                color = NF.Slate,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 25).forEach { option ->
                    ChoiceChip(
                        label = "$option Reels",
                        selected = state.speedbumpEvery == option,
                        onClick = { repo.setSpeedbumpEvery(option) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("Apps to watch")
        SettingsCard {
            TrackedApp.values().forEachIndexed { index, app ->
                if (index > 0) Divider()
                ToggleRow(
                    title = app.label,
                    subtitle = when (app) {
                        TrackedApp.INSTAGRAM -> "Counts swipes only inside the Reels tab, never the Home feed."
                        TrackedApp.YOUTUBE -> "Counts swipes only inside Shorts, never regular videos."
                        TrackedApp.TIKTOK -> "Counts For You swipes in TikTok."
                    },
                    checked = state.isAppEnabled(app),
                    onChange = { repo.setAppEnabled(app, it) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("Math Brain Refresh")
        SettingsCard {
            Text(
                text = "Math difficulty",
                color = NF.Ice,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "At the daily limit, Reels and Shorts are blocked until you solve 3 calculations " +
                    "for +5 Reels.",
                color = NF.Slate,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    label = "Standard",
                    selected = !state.mathHard,
                    onClick = { repo.setMathHard(false) }
                )
                ChoiceChip(
                    label = "Hard",
                    selected = state.mathHard,
                    onClick = { repo.setMathHard(true) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("Dynamic Island position")
        SettingsCard {
            OffsetSlider(
                title = "Vertical offset",
                subtitle = "Moves the pill up or down from just below the status bar.",
                value = state.islandOffsetYDp,
                range = -50f..150f,
                onChange = { repo.setIslandOffsets(state.islandOffsetXDp, it) }
            )
            Divider()
            OffsetSlider(
                title = "Horizontal offset",
                subtitle = "Moves the pill left or right of the centre.",
                value = state.islandOffsetXDp,
                range = -80f..80f,
                onChange = { repo.setIslandOffsets(it, state.islandOffsetYDp) }
            )
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "You can also drag the pill anywhere on screen; it remembers where you drop it.",
                    color = NF.Slate,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                )
                GhostPillButton(
                    text = "Reset",
                    onClick = { repo.setIslandOffsets(0, 0) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("Maintenance")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reel Scroll Sensor",
                        color = NF.Ice,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (perms.accessibility) "Enabled" else "Off. Turn it on to count Reels.",
                        color = if (perms.accessibility) NF.Mint else NF.Coral,
                        fontSize = 12.sp
                    )
                }
                GhostPillButton(
                    text = "Open settings",
                    onClick = callbacks.openAccessibilitySettings
                )
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset today's counter",
                        color = NF.Ice,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sets Reels, bonus Reels and pauses back to zero.",
                        color = NF.Slate,
                        fontSize = 12.sp
                    )
                }
                GhostPillButton(text = "Reset", onClick = { repo.resetToday() })
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("About")
        SettingsCard {
            Text(
                text = "NeuroFlow 1.1.1",
                color = NF.Ice,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Fewer Reels. Brighter Mind.\n\nPrivacy: the sensor only reads app names, view ids " +
                    "and scroll events to count swipes. No screen content, no accounts, no network. " +
                    "Everything stays on this phone.",
                color = NF.Slate,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun OffsetSlider(
    title: String,
    subtitle: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        Text(
            text = "$value dp",
            color = NF.Amber,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            style = NumberStyle
        )
    }
    Slider(
        value = value.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = range,
        colors = sliderColors()
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x14FFFFFF))
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.padding(top = 2.dp, end = 12.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NF.Coral,
                uncheckedThumbColor = NF.Slate,
                uncheckedTrackColor = Color(0x33FFFFFF),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) NF.Coral.copy(alpha = 0.26f) else Color(0x14FFFFFF))
            .border(1.dp, if (selected) NF.Coral else Color(0x22FFFFFF), shape)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) NF.Ice else NF.Slate,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = NF.Coral,
    activeTrackColor = NF.Coral,
    inactiveTrackColor = Color(0x33FFFFFF)
)
