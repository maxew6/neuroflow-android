package com.neuroflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.data.BENCHMARK_REELS_PER_DAY
import com.neuroflow.app.data.NeuroFlowState
import com.neuroflow.app.data.SECONDS_PER_AVOIDED_REEL
import com.neuroflow.app.data.formatDuration
import com.neuroflow.app.data.stageForReels
import com.neuroflow.app.data.stageLabel
import com.neuroflow.app.data.vitalityFor
import com.neuroflow.app.ui.components.BrainMascot
import com.neuroflow.app.ui.components.SectionLabel
import com.neuroflow.app.ui.components.StatCard
import com.neuroflow.app.ui.components.VitalityGauge
import com.neuroflow.app.ui.components.WeeklyBars
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NumberStyle
import com.neuroflow.app.ui.theme.auraColorFor
import com.neuroflow.app.ui.theme.glassCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

@Composable
fun AnalyticsScreen(state: NeuroFlowState) {
    val reels = state.reelsToday
    val limit = state.dailyLimit
    val vitality = vitalityFor(reels, limit)
    val stage = if (state.isLimitBlocked) 5 else stageForReels(reels)
    val aura = auraColorFor(reels)

    val reclaimedSeconds =
        max(0, BENCHMARK_REELS_PER_DAY - reels).toLong() * SECONDS_PER_AVOIDED_REEL
    val cleanStreak = state.streakDays + if (reels <= state.dailyLimit) 1 else 0

    val past = state.history.takeLast(6)
    val values = List(6 - past.size) { 0 } + past + reels
    val today = LocalDate.now()
    val labels = List(7) { i ->
        today.minusDays((6 - i).toLong()).dayOfWeek
            .getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Brain Analytics",
            color = NF.Ice,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "How your mind is holding up today.",
            color = NF.Slate,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(RoundedCornerShape(28.dp))
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VitalityGauge(vitality = vitality)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrainMascot(stage = stage, modifier = Modifier.size(64.dp), showAura = false)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stageLabel(stage),
                        color = NF.Ice,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$reels of $limit Reels today",
                        color = aura,
                        fontSize = 13.sp,
                        style = NumberStyle
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                value = reels.toString(),
                label = "Reels scrolled",
                accent = aura,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = formatDuration(reclaimedSeconds),
                label = "Est. time reclaimed",
                accent = NF.Mint,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "$cleanStreak d",
                label = "Clean streak",
                accent = NF.Amber,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "Time reclaimed is an estimate: Reels you did not watch compared with a " +
                "$BENCHMARK_REELS_PER_DAY-a-day habit, at $SECONDS_PER_AVOIDED_REEL seconds each.",
            color = NF.Slate.copy(alpha = 0.8f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )

        Spacer(Modifier.height(12.dp))
        SectionLabel("Last 7 days")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            WeeklyBars(values = values, limit = state.dailyLimit, dayLabels = labels)
            Text(
                text = "Dashed line = your daily limit (${state.dailyLimit})",
                color = NF.Slate,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("At a glance")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            InfoRow("Time in Reels today", formatDuration(state.scrollSecondsToday))
            InfoRow("Daily limit", "${state.dailyLimit} Reels")
            InfoRow(
                "Reels / Shorts",
                if (state.shouldBlockReels) "Strictly blocked" else if (state.isLimitBlocked) "Limit reached" else "Open"
            )
            InfoRow("Bonus Reels left", state.bonusReelsRemaining.toString())
            InfoRow("Reels in last session", state.sessionReels.toString())
            InfoRow(
                "Sensor",
                if (state.serviceConnected) "Connected" else "Waiting for accessibility access"
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = NF.Slate, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = NF.Ice,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            style = NumberStyle
        )
    }
}
