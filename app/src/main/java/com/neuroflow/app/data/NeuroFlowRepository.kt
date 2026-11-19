package com.neuroflow.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

const val DEFAULT_DAILY_LIMIT = 45
const val DEFAULT_SPEEDBUMP_EVERY = 15
const val BENCHMARK_REELS_PER_DAY = 500
const val SECONDS_PER_AVOIDED_REEL = 15

/** Short-form video surfaces NeuroFlow can watch. */
enum class TrackedApp(
    val label: String,
    val packages: Set<String>
) {
    INSTAGRAM(
        label = "Instagram Reels",
        packages = setOf("com.instagram.android")
    ),
    YOUTUBE(
        label = "YouTube Shorts",
        packages = setOf("com.google.android.youtube")
    ),
    TIKTOK(
        label = "TikTok",
        packages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    );

    companion object {
        fun fromPackage(pkg: String?): TrackedApp? {
            if (pkg == null) return null
            return values().firstOrNull { pkg in it.packages }
        }
    }
}

/** Everything the three components (service, overlay, UI) need to stay in sync. */
data class NeuroFlowState(
    // ---- persisted ----
    val dayKey: String = LocalDate.now().toString(),
    val reelsToday: Int = 0,
    val scrollSecondsToday: Long = 0L,
    val bonusReelsRemaining: Int = 0,
    val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    val speedbumpEvery: Int = DEFAULT_SPEEDBUMP_EVERY,
    val instagramEnabled: Boolean = true,
    val youtubeEnabled: Boolean = true,
    val tiktokEnabled: Boolean = false,
    val monitoringEnabled: Boolean = true,
    val streakDays: Int = 0,
    val history: List<Int> = emptyList(),
    val pausedUntilMs: Long = 0L,
    val previewReels: Int = 18,
    val islandOffsetXDp: Int = 0,
    val islandOffsetYDp: Int = 0,
    val mathHard: Boolean = false,
    val strictBlockEnabled: Boolean = true,
    // ---- runtime only ----
    val previewEnabled: Boolean = false,
    val serviceConnected: Boolean = false,
    /** A tracked app whose verified Reels / Shorts surface is on screen right now. */
    val activeApp: TrackedApp? = null,
    /** Strict lockout (math challenge) overlay is requested. */
    val blockerActive: Boolean = false,
    val blockerIsTest: Boolean = false,
    val sessionAccumulatedMs: Long = 0L,
    val sessionActiveSinceMs: Long = 0L,
    val sessionLastLeftMs: Long = 0L,
    val sessionReels: Int = 0
) {
    /** True once today's Reel count has reached the daily limit. */
    val isLimitReached: Boolean get() = reelsToday >= dailyLimit

    /** Reels and Shorts are strictly blocked: limit reached and no unlocked bonus Reels remain. */
    val isLimitBlocked: Boolean get() = reelsToday >= dailyLimit && bonusReelsRemaining == 0

    /** The sensor may eject the user from Reels / Shorts (needs the Strict blocking switch on). */
    val shouldBlockReels: Boolean get() = strictBlockEnabled && isLimitBlocked

    fun isAppEnabled(app: TrackedApp): Boolean = when (app) {
        TrackedApp.INSTAGRAM -> instagramEnabled
        TrackedApp.YOUTUBE -> youtubeEnabled
        TrackedApp.TIKTOK -> tiktokEnabled
    }

    fun isPaused(nowMs: Long): Boolean = pausedUntilMs > nowMs
}

// ---------------------------------------------------------------------------------------------
// Pure helpers shared by UI + services
// ---------------------------------------------------------------------------------------------

/** Brain mascot stage by Reel count: 1 radiant, 2 drift, 3 melting, 4 zombie, 5 brain freeze. */
fun stageForReels(reels: Int): Int = when {
    reels <= 15 -> 1
    reels <= 30 -> 2
    reels <= 45 -> 3
    reels <= 75 -> 4
    else -> 5
}

fun stageLabel(stage: Int): String = when (stage) {
    1 -> "Radiant & Focused"
    2 -> "Dopamine Drift"
    3 -> "Overstimulated"
    4 -> "Doomscroll Zombie"
    else -> "Critical Brain Freeze"
}

fun vitalityFor(reels: Int, limit: Int): Int {
    val safeLimit = limit.coerceAtLeast(1)
    val v = 100f - (reels * 100f) / (safeLimit * 1.5f)
    return v.coerceIn(0f, 100f).roundToInt()
}

fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m) else "${m}m"
}

// ---------------------------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------------------------

class NeuroFlowRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<NeuroFlowState> = _state.asStateFlow()

    // ---- mutations -------------------------------------------------------------------------

    private fun mutate(block: (NeuroFlowState) -> NeuroFlowState) {
        synchronized(this) {
            val next = block(rollover(_state.value))
            _state.value = next
            persist(next)
        }
    }

    fun registerReel() = mutate { s ->
        val overLimit = s.reelsToday >= s.dailyLimit
        s.copy(
            reelsToday = s.reelsToday + 1,
            sessionReels = s.sessionReels + 1,
            // Bonus Reels are only spent once the daily limit has been reached.
            bonusReelsRemaining =
                if (overLimit && s.bonusReelsRemaining > 0) s.bonusReelsRemaining - 1
                else s.bonusReelsRemaining
        )
    }

    fun onTrackedAppForeground(app: TrackedApp, nowMs: Long) = mutate { s ->
        val resumed = s.sessionActiveSinceMs != 0L
        val freshSession = !resumed && (nowMs - s.sessionLastLeftMs) > SESSION_GAP_MS
        s.copy(
            activeApp = app,
            sessionAccumulatedMs = if (freshSession) 0L else s.sessionAccumulatedMs,
            sessionReels = if (freshSession) 0 else s.sessionReels,
            sessionActiveSinceMs = if (resumed) s.sessionActiveSinceMs else nowMs
        )
    }

    fun onLeftTrackedApp(nowMs: Long) = mutate { s ->
        if (s.activeApp == null && s.sessionActiveSinceMs == 0L) {
            s
        } else {
            val elapsed =
                if (s.sessionActiveSinceMs > 0L) (nowMs - s.sessionActiveSinceMs).coerceAtLeast(0L) else 0L
            s.copy(
                activeApp = null,
                sessionAccumulatedMs = s.sessionAccumulatedMs + elapsed,
                sessionActiveSinceMs = 0L,
                sessionLastLeftMs = nowMs,
                scrollSecondsToday = s.scrollSecondsToday + elapsed / 1000L
            )
        }
    }

    /** Math Brain Refresh reward: exactly [extra] more Reels before strict blocking re-engages. */
    fun grantBonusReels(extra: Int) = mutate { it.copy(bonusReelsRemaining = extra.coerceAtLeast(0)) }

    fun setBlocker(active: Boolean, isTest: Boolean) = mutate {
        it.copy(blockerActive = active, blockerIsTest = active && isTest)
    }

    fun setIslandOffsets(xDp: Int, yDp: Int) = mutate {
        it.copy(islandOffsetXDp = xDp.coerceIn(-600, 600), islandOffsetYDp = yDp.coerceIn(-600, 900))
    }

    fun setMathHard(hard: Boolean) = mutate { it.copy(mathHard = hard) }

    fun setStrictBlock(enabled: Boolean) = mutate {
        it.copy(
            strictBlockEnabled = enabled,
            blockerActive = if (enabled) it.blockerActive else false,
            blockerIsTest = if (enabled) it.blockerIsTest else false
        )
    }

    fun pauseFor(minutes: Int, nowMs: Long) = mutate {
        it.copy(pausedUntilMs = nowMs + minutes * 60_000L)
    }

    fun setServiceConnected(connected: Boolean) = mutate { it.copy(serviceConnected = connected) }
    fun setPreviewEnabled(enabled: Boolean) = mutate { it.copy(previewEnabled = enabled) }
    fun setPreviewReels(value: Int) = mutate { it.copy(previewReels = value.coerceIn(0, 100)) }
    fun setDailyLimit(value: Int) = mutate { it.copy(dailyLimit = value.coerceIn(5, 150)) }
    fun setSpeedbumpEvery(value: Int) = mutate { it.copy(speedbumpEvery = value.coerceAtLeast(3)) }
    fun setMonitoringEnabled(value: Boolean) = mutate { it.copy(monitoringEnabled = value) }

    fun setAppEnabled(app: TrackedApp, enabled: Boolean) = mutate {
        when (app) {
            TrackedApp.INSTAGRAM -> it.copy(instagramEnabled = enabled)
            TrackedApp.YOUTUBE -> it.copy(youtubeEnabled = enabled)
            TrackedApp.TIKTOK -> it.copy(tiktokEnabled = enabled)
        }
    }

    fun resetToday() = mutate {
        it.copy(
            reelsToday = 0,
            bonusReelsRemaining = 0,
            scrollSecondsToday = 0L,
            sessionReels = 0,
            pausedUntilMs = 0L,
            blockerActive = false,
            blockerIsTest = false
        )
    }

    // ---- persistence -----------------------------------------------------------------------

    private fun load(): NeuroFlowState {
        val d = NeuroFlowState()
        val hist = (prefs.getString("history", "") ?: "")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
        val loaded = d.copy(
            dayKey = prefs.getString("dayKey", d.dayKey) ?: d.dayKey,
            reelsToday = prefs.getInt("reelsToday", 0),
            scrollSecondsToday = prefs.getLong("scrollSecondsToday", 0L),
            bonusReelsRemaining = prefs.getInt("bonusReelsRemaining", 0),
            dailyLimit = prefs.getInt("dailyLimit", DEFAULT_DAILY_LIMIT),
            speedbumpEvery = prefs.getInt("speedbumpEvery", DEFAULT_SPEEDBUMP_EVERY),
            instagramEnabled = prefs.getBoolean("instagramEnabled", true),
            youtubeEnabled = prefs.getBoolean("youtubeEnabled", true),
            tiktokEnabled = prefs.getBoolean("tiktokEnabled", false),
            monitoringEnabled = prefs.getBoolean("monitoringEnabled", true),
            streakDays = prefs.getInt("streakDays", 0),
            history = hist,
            pausedUntilMs = prefs.getLong("pausedUntilMs", 0L),
            previewReels = prefs.getInt("previewReels", 18),
            islandOffsetXDp = prefs.getInt("islandOffsetXDp", 0),
            islandOffsetYDp = prefs.getInt("islandOffsetYDp", 0),
            mathHard = prefs.getBoolean("mathHard", false),
            strictBlockEnabled = prefs.getBoolean("strictBlockEnabled", true)
        )
        return rollover(migrate(loaded))
    }

    /**
     * One-time clean-up when upgrading from an older build: earlier versions counted every swipe in a
     * watched app (including the Instagram Home feed), so a stale count could trip the strict blocker
     * straight away. YouTube Shorts also used to be switched off by default.
     */
    private fun migrate(s: NeuroFlowState): NeuroFlowState {
        val schema = prefs.getInt("schema", 1)
        if (schema >= SCHEMA_VERSION) return s
        val migrated = s.copy(
            reelsToday = 0,
            bonusReelsRemaining = 0,
            scrollSecondsToday = 0L,
            youtubeEnabled = true
        )
        persist(migrated)
        return migrated
    }

    private fun persist(s: NeuroFlowState) {
        prefs.edit()
            .putInt("schema", SCHEMA_VERSION)
            .putString("dayKey", s.dayKey)
            .putInt("reelsToday", s.reelsToday)
            .putLong("scrollSecondsToday", s.scrollSecondsToday)
            .putInt("bonusReelsRemaining", s.bonusReelsRemaining)
            .putInt("dailyLimit", s.dailyLimit)
            .putInt("speedbumpEvery", s.speedbumpEvery)
            .putBoolean("instagramEnabled", s.instagramEnabled)
            .putBoolean("youtubeEnabled", s.youtubeEnabled)
            .putBoolean("tiktokEnabled", s.tiktokEnabled)
            .putBoolean("monitoringEnabled", s.monitoringEnabled)
            .putInt("streakDays", s.streakDays)
            .putString("history", s.history.joinToString(","))
            .putLong("pausedUntilMs", s.pausedUntilMs)
            .putInt("previewReels", s.previewReels)
            .putInt("islandOffsetXDp", s.islandOffsetXDp)
            .putInt("islandOffsetYDp", s.islandOffsetYDp)
            .putBoolean("mathHard", s.mathHard)
            .putBoolean("strictBlockEnabled", s.strictBlockEnabled)
            .apply()
    }

    /** New calendar day: archive yesterday, update the clean-scrolling streak, zero the counters. */
    private fun rollover(s: NeuroFlowState): NeuroFlowState {
        val today = LocalDate.now()
        val last = runCatching { LocalDate.parse(s.dayKey) }.getOrNull()
            ?: return s.copy(dayKey = today.toString())
        val gap = ChronoUnit.DAYS.between(last, today).toInt()
        if (gap <= 0) return s

        val lastWasClean = s.reelsToday <= s.dailyLimit
        var streak = if (lastWasClean) s.streakDays + 1 else 0
        if (gap > 1) streak += (gap - 1) // untouched days count as clean
        val skipped = List(minOf(gap - 1, 6)) { 0 }
        val newHistory = (s.history + s.reelsToday + skipped).takeLast(6)

        return s.copy(
            dayKey = today.toString(),
            reelsToday = 0,
            scrollSecondsToday = 0L,
            bonusReelsRemaining = 0,
            streakDays = streak,
            history = newHistory,
            sessionReels = 0
        )
    }

    companion object {
        private const val PREFS_NAME = "neuroflow_prefs"
        private const val SESSION_GAP_MS = 90_000L
        private const val SCHEMA_VERSION = 2

        @Volatile
        private var INSTANCE: NeuroFlowRepository? = null

        fun get(context: Context): NeuroFlowRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NeuroFlowRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
