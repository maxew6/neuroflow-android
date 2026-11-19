package com.neuroflow.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.data.DiagnosticsLog
import com.neuroflow.app.data.NeuroFlowState
import com.neuroflow.app.service.DynamicIslandOverlayService
import com.neuroflow.app.ui.components.AppCallbacks
import com.neuroflow.app.ui.components.GhostPillButton
import com.neuroflow.app.ui.components.GradientPillButton
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.glassCard
import com.neuroflow.app.util.PermissionStatus
import kotlinx.coroutines.delay

/**
 * Live view of what the accessibility sensor sees. If detection ever misbehaves on a specific
 * Instagram / YouTube version, "Copy diagnostic report" captures the exact view ids and verdicts.
 */
@Composable
fun SensorHealthCard(
    state: NeuroFlowState,
    perms: PermissionStatus,
    callbacks: AppCallbacks
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snap by DiagnosticsLog.snapshot.collectAsState()
    val lines by DiagnosticsLog.lines.collectAsState()
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val overlayRunning = DynamicIslandOverlayService.isRunning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        HealthRow(
            label = "Accessibility switched on",
            level = if (perms.accessibility) Level.Good else Level.Bad,
            detail = if (perms.accessibility) "On in system settings"
            else "Off. Turn on \"NeuroFlow Reel Scroll Sensor\" in Accessibility settings."
        )
        HealthRow(
            label = "Sensor connected",
            level = when {
                state.serviceConnected -> Level.Good
                perms.accessibility -> Level.Warn
                else -> Level.Bad
            },
            detail = when {
                state.serviceConnected -> "Running and receiving events"
                perms.accessibility ->
                    "Switched on but not running. The phone put it to sleep: switch it off and on again, " +
                        "and set battery to Unrestricted."
                else -> "Not running"
            }
        )
        HealthRow(
            label = "Island guard service",
            level = if (overlayRunning) Level.Good else Level.Warn,
            detail = if (overlayRunning) "Foreground service running"
            else "Not running yet. Open NeuroFlow once with Display over other apps allowed."
        )
        HealthRow(
            label = "Display over other apps",
            level = if (perms.overlay) Level.Good else Level.Bad,
            detail = if (perms.overlay) "Allowed" else "Not allowed, so the island cannot be drawn"
        )
        HealthRow(
            label = "Battery unrestricted",
            level = if (perms.battery) Level.Good else Level.Warn,
            detail = if (perms.battery) "Not restricted"
            else "Restricted. Some phones stop the sensor in the background. Tap Enable on the Home tab."
        )
        HealthRow(
            label = "Apps being watched",
            level = Level.Info,
            detail = buildString {
                append("Instagram ").append(if (state.instagramEnabled) "on" else "OFF")
                append("  \u00B7  YouTube ").append(if (state.youtubeEnabled) "on" else "OFF")
                append("  \u00B7  TikTok ").append(if (state.tiktokEnabled) "on" else "off")
            }
        )

        Spacer(Modifier.height(8.dp))
        val ago = if (snap.lastEventAtMs > 0L) ((nowMs - snap.lastEventAtMs) / 1000L).coerceAtLeast(0L) else -1L
        Text(
            text = if (snap.eventsSeen == 0L) {
                "No events received yet. Open Instagram or YouTube and use it for a few seconds."
            } else {
                "${snap.eventsSeen} events \u00B7 last ${snap.lastEventType.removePrefix("TYPE_").lowercase()} " +
                    "from ${snap.lastPackage.ifEmpty { "-" }} ${if (ago >= 0) "${ago}s ago" else ""}"
            },
            color = NF.Slate,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Text(
            text = "Right now: ${snap.verdict.uppercase()}  (${snap.reason})",
            color = NF.Ice,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = "Island window: ${snap.overlayState}",
            color = NF.Slate,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33000000))
                    .padding(10.dp)
            ) {
                lines.takeLast(7).reversed().forEach { line ->
                    Text(
                        text = line,
                        color = NF.Slate,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GradientPillButton(
            text = "Copy diagnostic report",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val report = DiagnosticsLog.buildReport(
                    state = state,
                    overlayGranted = perms.overlay,
                    accessibilityEnabled = perms.accessibility,
                    batteryUnrestricted = perms.battery,
                    appVersions = appVersions(ctx)
                )
                clipboard.setText(AnnotatedString(report))
                Toast.makeText(ctx, "Report copied. Paste it into the chat.", Toast.LENGTH_SHORT).show()
            }
        )
        Spacer(Modifier.height(8.dp))
        GhostPillButton(
            text = "Open Accessibility settings",
            modifier = Modifier.fillMaxWidth(),
            onClick = callbacks.openAccessibilitySettings
        )
    }
}

private enum class Level { Good, Warn, Bad, Info }

@Composable
private fun HealthRow(label: String, level: Level, detail: String) {
    val dot = when (level) {
        Level.Good -> NF.Mint
        Level.Warn -> NF.Amber
        Level.Bad -> NF.Crimson
        Level.Info -> NF.Slate
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = NF.Ice,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                color = NF.Slate,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun appVersions(ctx: Context): String {
    val packages = listOf("com.instagram.android", "com.google.android.youtube", "com.zhiliaoapp.musically")
    return packages.joinToString("; ") { pkg ->
        val version = runCatching { ctx.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
        "$pkg=${version ?: "not installed or not visible"}"
    }
}
