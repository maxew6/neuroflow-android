package com.neuroflow.app.data

import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** What the accessibility sensor last saw and decided. Runtime only, never persisted. */
data class SensorSnapshot(
    val eventsSeen: Long = 0L,
    val lastEventAtMs: Long = 0L,
    val lastPackage: String = "",
    val lastEventType: String = "",
    val trackedPackage: String = "",
    val verdict: String = "none",
    val reason: String = "no scan yet",
    val rootNull: Boolean = false,
    val nodesScanned: Int = 0,
    val scanCount: Long = 0L,
    val scanAtMs: Long = 0L,
    val idSample: String = "",
    val overlayState: String = "not shown yet"
)

/**
 * Tiny in-memory diagnostics hub. The accessibility service and overlay service write to it; the
 * Settings screen shows it and can copy a full report so a mismatch with a specific Instagram /
 * YouTube version can be pinpointed from real data instead of guessed.
 */
object DiagnosticsLog {

    private val _snapshot = MutableStateFlow(SensorSnapshot())
    val snapshot: StateFlow<SensorSnapshot> = _snapshot.asStateFlow()

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val events = AtomicLong(0L)
    @Volatile private var lastEmitMs = 0L
    @Volatile private var lastPkg = ""
    @Volatile private var lastType = ""
    @Volatile private var lastEventMs = 0L

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Called for every accessibility event; emits to the UI at most about twice per second. */
    fun noteEvent(pkg: String, type: String) {
        val now = System.currentTimeMillis()
        val count = events.incrementAndGet()
        lastPkg = pkg
        lastType = type
        lastEventMs = now
        if (now - lastEmitMs < 600L) return
        lastEmitMs = now
        update { it.copy(eventsSeen = count, lastEventAtMs = now, lastPackage = pkg, lastEventType = type) }
    }

    @Synchronized
    fun update(block: (SensorSnapshot) -> SensorSnapshot) {
        val current = _snapshot.value
        val patched = current.copy(
            eventsSeen = events.get(),
            lastEventAtMs = lastEventMs,
            lastPackage = lastPkg,
            lastEventType = lastType
        )
        _snapshot.value = block(patched)
    }

    @Synchronized
    fun log(message: String) {
        val line = clock.format(Date()) + "  " + message
        _lines.value = (_lines.value + line).takeLast(60)
    }

    fun setOverlayState(text: String) {
        update { it.copy(overlayState = text) }
        log("Island window: $text")
    }

    /** Plain-text report for pasting into a chat. */
    fun buildReport(
        state: NeuroFlowState,
        overlayGranted: Boolean,
        accessibilityEnabled: Boolean,
        batteryUnrestricted: Boolean,
        appVersions: String
    ): String {
        val s = _snapshot.value
        val sb = StringBuilder()
        sb.appendLine("NeuroFlow 1.1.1 diagnostic report")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Apps: $appVersions")
        sb.appendLine()
        sb.appendLine("Overlay permission: $overlayGranted")
        sb.appendLine("Accessibility enabled in system: $accessibilityEnabled")
        sb.appendLine("Sensor connected in process: ${state.serviceConnected}")
        sb.appendLine("Battery unrestricted: $batteryUnrestricted")
        sb.appendLine("Monitoring: ${state.monitoringEnabled}; strict block: ${state.strictBlockEnabled}")
        sb.appendLine("Watching: Instagram=${state.instagramEnabled} YouTube=${state.youtubeEnabled} TikTok=${state.tiktokEnabled}")
        sb.appendLine("Reels today: ${state.reelsToday} / limit ${state.dailyLimit}; bonus left ${state.bonusReelsRemaining}; blocked=${state.isLimitBlocked}")
        sb.appendLine("Active surface: ${state.activeApp ?: "none"}; blockerActive=${state.blockerActive}")
        sb.appendLine()
        sb.appendLine("Events seen: ${s.eventsSeen}; last ${s.lastEventType} from ${s.lastPackage.ifEmpty { "-" }}")
        sb.appendLine("Tracked package: ${s.trackedPackage.ifEmpty { "-" }}")
        sb.appendLine("Last verdict: ${s.verdict} (${s.reason}); root null: ${s.rootNull}; nodes scanned: ${s.nodesScanned}; scans: ${s.scanCount}")
        sb.appendLine("View ids seen: ${s.idSample.ifEmpty { "-" }}")
        sb.appendLine("Island window: ${s.overlayState}")
        sb.appendLine()
        sb.appendLine("Recent activity:")
        _lines.value.takeLast(30).forEach { sb.appendLine(it) }
        return sb.toString()
    }
}
