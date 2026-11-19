package com.neuroflow.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.neuroflow.app.data.DiagnosticsLog
import com.neuroflow.app.data.NeuroFlowRepository
import com.neuroflow.app.data.TrackedApp
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private enum class Confidence { None, Weak, Strong }

private enum class Trigger { Window, Interaction, Scroll, Watchdog }

private class ScanResult(
    val confidence: Confidence,
    val reason: String,
    val rootNull: Boolean = false,
    val nodes: Int = 0,
    val idSample: String = ""
)

private class SwipeInfo(val source: AccessibilityNodeInfo?)

/**
 * Watches Instagram Reels, YouTube Shorts and (optionally) TikTok.
 *
 * Design notes (v1.1.1):
 *  - Accessibility tree queries are IPC calls answered on the *target app's* main thread. The earlier
 *    build walked up to 700 nodes on our main thread after every content-change event, which made
 *    Instagram / YouTube stutter. Now every scan runs on a background thread, starts with a handful of
 *    targeted view-id queries, and only falls back to a small tree walk when those ids are unknown.
 *  - Content-change events are no longer requested. A window change, clicks / selections (tab switches),
 *    scrolls and a slow watchdog drive the checks instead.
 *  - Detection has two confidence levels. STRONG = known Reels / Shorts view ids or the selected tab.
 *    WEAK = a full-screen vertical scroller (works even if an app update renames its view ids).
 *    Only STRONG detections, or an actual swipe, may ever eject the user from an app.
 *  - Leaving a Reels / Shorts surface needs two misses in a row so a slow scan never makes the island flicker.
 *
 * Privacy: only package names, view ids, tab labels, scroll deltas and view bounds are read.
 */
class ReelAccessibilityService : AccessibilityService() {

    private val repo: NeuroFlowRepository by lazy { NeuroFlowRepository.get(applicationContext) }
    private val main = Handler(Looper.getMainLooper())
    private var scanner: ExecutorService? = null
    private val scanPending = AtomicBoolean(false)

    @Volatile private var trackedApp: TrackedApp? = null
    @Volatile private var trackedPkg: String? = null
    @Volatile private var lastReelAtMs = 0L
    @Volatile private var lastEjectAtMs = 0L
    @Volatile private var ejectAttempts = 0
    @Volatile private var missStreak = 0
    @Volatile private var lastWalkAtMs = 0L
    @Volatile private var lastResult: ScanResult? = null

    private val watchdog = object : Runnable {
        override fun run() {
            val app = trackedApp
            val pkg = trackedPkg
            if (app != null && pkg != null) {
                requestScan(app, pkg, Trigger.Watchdog, null, 0L)
                main.postDelayed(this, WATCHDOG_MS)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scanner = Executors.newSingleThreadExecutor { r ->
            Thread(r, "neuroflow-scan").apply { isDaemon = true }
        }
        hardenServiceInfo()
        repo.setServiceConnected(true)
        DiagnosticsLog.log("Sensor connected")
        startOverlayIfPossible()
    }

    /** Re-applies the important flags in code in case a ROM mishandles the XML config. */
    private fun hardenServiceInfo() {
        runCatching {
            val info = serviceInfo ?: return@runCatching
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.packageNames = null
            info.notificationTimeout = 100L
            serviceInfo = info
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        repo.setServiceConnected(false)
        repo.onLeftTrackedApp(System.currentTimeMillis())
        DiagnosticsLog.log("Sensor disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        instance = null
        main.removeCallbacks(watchdog)
        trackedApp = null
        trackedPkg = null
        scanner?.shutdownNow()
        scanner = null
    }

    // ---------------------------------------------------------------------------------------
    // Events (main thread, kept very cheap)
    // ---------------------------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || TrackedApp.fromPackage(pkg) != null) {
            DiagnosticsLog.noteEvent(pkg, AccessibilityEvent.eventTypeToString(type))
        }
        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(pkg)
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> onInteraction(pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(pkg, event)
            else -> Unit
        }
    }

    private fun onWindowChanged(pkg: String) {
        if (isIgnorable(pkg)) return
        val app = TrackedApp.fromPackage(pkg)
        val state = repo.state.value
        val now = System.currentTimeMillis()
        if (app != null && state.monitoringEnabled && state.isAppEnabled(app)) {
            if (trackedPkg != pkg) DiagnosticsLog.log("${app.label}: app in foreground")
            beginTracking(app, pkg)
            startOverlayIfPossible()
            // Small delay so the freshly opened window has populated its accessibility tree.
            requestScan(app, pkg, Trigger.Window, null, 300L)
        } else {
            if (app != null && !state.isAppEnabled(app)) {
                DiagnosticsLog.log("${app.label} is switched off in Settings > Apps to watch")
            }
            if (trackedPkg != null) {
                trackedApp = null
                trackedPkg = null
                main.removeCallbacks(watchdog)
                DiagnosticsLog.update { it.copy(trackedPackage = "") }
            }
            missStreak = 0
            ejectAttempts = 0
            if (state.blockerActive && !state.blockerIsTest) repo.setBlocker(false, false)
            if (state.activeApp != null) {
                repo.onLeftTrackedApp(now)
                DiagnosticsLog.log("Left Reels / Shorts (switched to another app)")
            }
        }
    }

    /** Tab switches inside Instagram / YouTube arrive as clicks or selections. */
    private fun onInteraction(pkg: String) {
        val app = TrackedApp.fromPackage(pkg) ?: return
        val state = repo.state.value
        if (!state.monitoringEnabled || !state.isAppEnabled(app)) return
        if (trackedPkg == null) beginTracking(app, pkg)
        requestScan(app, pkg, Trigger.Interaction, null, 450L)
    }

    private fun onScrolled(pkg: String, event: AccessibilityEvent) {
        val app = TrackedApp.fromPackage(pkg) ?: return
        val state = repo.state.value
        if (!state.monitoringEnabled || !state.isAppEnabled(app)) return
        if (!looksLikeVerticalSwipe(event)) return

        // 1,300 ms debounce: one vertical flick == exactly one Reel, even during scroll momentum.
        val now = System.currentTimeMillis()
        if (now - lastReelAtMs < DEBOUNCE_MS) return

        if (trackedPkg == null) beginTracking(app, pkg)
        requestScan(app, pkg, Trigger.Scroll, SwipeInfo(event.source), 0L)
    }

    private fun beginTracking(app: TrackedApp, pkg: String) {
        trackedApp = app
        trackedPkg = pkg
        DiagnosticsLog.update { it.copy(trackedPackage = pkg) }
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun looksLikeVerticalSwipe(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        val dx = abs(event.scrollDeltaX)
        val dy = abs(event.scrollDeltaY)
        if (dx > dy) return false // horizontal carousel, seek bar or story swipe
        val pageChanged = event.fromIndex != event.toIndex
        return pageChanged || dy >= MIN_VERTICAL_DELTA_PX
    }

    // ---------------------------------------------------------------------------------------
    // Scan scheduling (background thread)
    // ---------------------------------------------------------------------------------------

    private fun requestScan(app: TrackedApp, pkg: String, trigger: Trigger, swipe: SwipeInfo?, delayMs: Long) {
        // Non-scroll scans are coalesced; scroll scans are always queued so no counted swipe is lost.
        if (trigger != Trigger.Scroll && !scanPending.compareAndSet(false, true)) return
        val job = Runnable {
            val exec = scanner
            if (exec == null) {
                if (trigger != Trigger.Scroll) scanPending.set(false)
                return@Runnable
            }
            try {
                exec.execute {
                    try {
                        runScan(app, pkg, trigger, swipe)
                    } catch (t: Throwable) {
                        DiagnosticsLog.log("Scan error: ${t.javaClass.simpleName}")
                    } finally {
                        if (trigger != Trigger.Scroll) scanPending.set(false)
                    }
                }
            } catch (e: RejectedExecutionException) {
                if (trigger != Trigger.Scroll) scanPending.set(false)
            }
        }
        if (delayMs > 0L) main.postDelayed(job, delayMs) else main.post(job)
    }

    private fun runScan(app: TrackedApp, pkg: String, trigger: Trigger, swipe: SwipeInfo?) {
        val now = System.currentTimeMillis()
        if (trigger == Trigger.Scroll && now - lastReelAtMs < DEBOUNCE_MS) return
        val result = scanSurface(app, pkg, swipe, trigger, now)
        lastResult = result
        DiagnosticsLog.update {
            it.copy(
                verdict = result.confidence.name.lowercase(),
                reason = "${app.label}: ${result.reason}",
                rootNull = result.rootNull,
                nodesScanned = result.nodes,
                scanCount = it.scanCount + 1,
                scanAtMs = now,
                idSample = if (result.idSample.isNotEmpty()) result.idSample else it.idSample
            )
        }
        applyResult(app, result, trigger, now)
    }

    private fun applyResult(app: TrackedApp, result: ScanResult, trigger: Trigger, now: Long) {
        val inside = result.confidence != Confidence.None
        val state = repo.state.value

        if (!inside) {
            missStreak += 1
            if (missStreak >= MISSES_BEFORE_LEAVE && state.activeApp != null) {
                repo.onLeftTrackedApp(now)
                ejectAttempts = 0
                DiagnosticsLog.log("Left ${app.label}: ${result.reason}")
            }
            return
        }

        missStreak = 0
        if (state.activeApp != app) {
            repo.onTrackedAppForeground(app, now)
            DiagnosticsLog.log("Inside ${app.label} (${result.confidence.name.lowercase()}): ${result.reason}")
            startOverlayIfPossible()
        }

        val fresh = repo.state.value
        if (fresh.shouldBlockReels) {
            // Weak detections may only act on a real swipe, so a false positive on a normal feed
            // can never close the app on its own.
            val mayEject = result.confidence == Confidence.Strong || trigger == Trigger.Scroll
            if (mayEject && now >= graceUntilMs) eject(app, now)
            return
        }

        if (trigger == Trigger.Scroll && now - lastReelAtMs >= DEBOUNCE_MS) {
            lastReelAtMs = now
            repo.registerReel()
            DiagnosticsLog.log("Reel counted (${result.confidence.name.lowercase()})")
        }
    }

    /** Strict blocker: kick the user out of Reels / Shorts and request the Math Brain Refresh lockout. */
    private fun eject(app: TrackedApp, now: Long) {
        if (!repo.state.value.blockerActive) {
            repo.setBlocker(true, false)
            DiagnosticsLog.log("Daily limit reached: strict block is ON")
        }
        if (now - lastEjectAtMs < EJECT_COOLDOWN_MS) return
        lastEjectAtMs = now
        ejectAttempts += 1
        val action = when {
            app == TrackedApp.TIKTOK -> GLOBAL_ACTION_HOME // no safe tab to go back to
            ejectAttempts >= 3 -> GLOBAL_ACTION_HOME // BACK did not leave the tab: escalate
            else -> GLOBAL_ACTION_BACK
        }
        if (ejectAttempts >= 3) ejectAttempts = 0
        DiagnosticsLog.log("Blocking: sending " + if (action == GLOBAL_ACTION_HOME) "HOME" else "BACK")
        main.post { performGlobalAction(action) }
    }

    // ---------------------------------------------------------------------------------------
    // Surface detection
    // ---------------------------------------------------------------------------------------

    private fun scanSurface(
        app: TrackedApp,
        pkg: String,
        swipe: SwipeInfo?,
        trigger: Trigger,
        now: Long
    ): ScanResult {
        if (app == TrackedApp.TIKTOK) return ScanResult(Confidence.Strong, "TikTok is in the foreground")
        val root = rootFor(pkg)
            ?: return ScanResult(Confidence.None, "no accessibility window for the app yet", rootNull = true)
        return try {
            when (app) {
                TrackedApp.INSTAGRAM -> scanInstagram(root, pkg, swipe, trigger, now)
                else -> scanYouTube(root, pkg, swipe, trigger, now)
            }
        } catch (e: Exception) {
            ScanResult(Confidence.None, "scan failed: ${e.javaClass.simpleName}")
        }
    }

    /** The app's own window root, even if our overlay or the keyboard is the "active" window. */
    private fun rootFor(pkg: String): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() == pkg) return active
        return runCatching {
            windows.asSequence()
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString() == pkg }
        }.getOrNull()
    }

    private fun anyVisible(nodes: List<AccessibilityNodeInfo>?): Boolean =
        nodes != null && nodes.any { it.isVisibleToUser }

    private fun anySelected(nodes: List<AccessibilityNodeInfo>?): Boolean =
        nodes != null && nodes.any { it.isSelected }

    private fun scanInstagram(
        root: AccessibilityNodeInfo,
        pkg: String,
        swipe: SwipeInfo?,
        trigger: Trigger,
        now: Long
    ): ScanResult {
        val prefix = "$pkg:id/"

        // 1. Targeted queries for the Reels viewer containers.
        for (id in IG_REEL_VIEW_IDS) {
            if (anyVisible(root.findAccessibilityNodeInfosByViewId(prefix + id))) {
                return ScanResult(Confidence.Strong, "Reels viewer id $id")
            }
        }

        // 2. Which bottom tab is selected?
        var tabsSeen = 0
        var selectedTab: String? = null
        for (tab in IG_TABS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(prefix + tab)
            if (nodes != null && nodes.isNotEmpty()) tabsSeen += 1
            if (anySelected(nodes)) selectedTab = tab
        }
        val chosen = selectedTab
        if (chosen != null) {
            return if (chosen == "clips_tab") {
                ScanResult(Confidence.Strong, "Reels tab is selected")
            } else {
                ScanResult(Confidence.None, "$chosen is selected (not Reels)", nodes = tabsSeen)
            }
        }

        // 3. Ids unknown (app update?): bounded tree walk with label + geometry heuristics.
        return walkFallback(root, TrackedApp.INSTAGRAM, swipe, trigger, now)
    }

    private fun scanYouTube(
        root: AccessibilityNodeInfo,
        pkg: String,
        swipe: SwipeInfo?,
        trigger: Trigger,
        now: Long
    ): ScanResult {
        val prefix = "$pkg:id/"
        for (id in YT_SHORTS_VIEW_IDS) {
            if (anyVisible(root.findAccessibilityNodeInfosByViewId(prefix + id))) {
                return ScanResult(Confidence.Strong, "Shorts player id $id")
            }
        }
        return walkFallback(root, TrackedApp.YOUTUBE, swipe, trigger, now)
    }

    /**
     * Bounded breadth-first walk. Looks for Reels / Shorts nodes by id hint, the selected bottom tab by
     * its label, and a full-screen vertical scroller by geometry. Records the ids it saw so the diagnostics
     * report can show exactly what this app version exposes.
     */
    private fun walkFallback(
        root: AccessibilityNodeInfo,
        app: TrackedApp,
        swipe: SwipeInfo?,
        trigger: Trigger,
        now: Long
    ): ScanResult {
        // The watchdog must not hammer the target app: reuse the previous result for a few seconds.
        val cached = lastResult
        if (trigger == Trigger.Watchdog && cached != null && now - lastWalkAtMs < WALK_MIN_GAP_MS) return cached
        lastWalkAtMs = now

        val rect = Rect()
        root.getBoundsInScreen(rect)
        val winW = rect.width().coerceAtLeast(1)
        val winH = rect.height().coerceAtLeast(1)

        val hints = if (app == TrackedApp.INSTAGRAM) IG_ID_HINTS else YT_ID_HINTS
        var viewerNode = false
        var reelsTab = false
        var shortsTab = false
        var homeSelected = false
        var storiesTray = false
        var fullScroller = swipe?.source?.let { isFullScreenVerticalScroller(it, winW, winH) } ?: false
        val idSample = LinkedHashSet<String>()
        var visited = 0

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        while (queue.isNotEmpty() && visited < FALLBACK_MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1

            val fullId = node.viewIdResourceName
            if (fullId != null) {
                val name = fullId.substringAfter(":id/")
                if (name.isNotEmpty() && idSample.size < 40) idSample.add(name)
                if (node.isVisibleToUser) {
                    if (hints.any { name.contains(it) }) viewerNode = true
                    if (app == TrackedApp.INSTAGRAM && (name.contains("reel_tray") || name.contains("reels_tray"))) {
                        storiesTray = true
                    }
                }
            }
            if (node.isSelected) {
                val label = (node.contentDescription ?: node.text)?.toString()?.trim().orEmpty()
                when {
                    label.equals("Reels", ignoreCase = true) -> reelsTab = true
                    label.equals("Shorts", ignoreCase = true) -> shortsTab = true
                    label.equals("Home", ignoreCase = true) -> homeSelected = true
                }
            }
            if (!fullScroller && node.isScrollable && isFullScreenVerticalScroller(node, winW, winH)) {
                fullScroller = true
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.addLast(child)
            }
        }

        val sample = idSample.joinToString(", ")
        return when (app) {
            TrackedApp.INSTAGRAM -> when {
                viewerNode -> ScanResult(Confidence.Strong, "Reels viewer node", false, visited, sample)
                reelsTab -> ScanResult(Confidence.Strong, "Reels tab label selected", false, visited, sample)
                homeSelected || storiesTray ->
                    ScanResult(Confidence.None, "Home feed (Home tab or Stories tray)", false, visited, sample)
                fullScroller ->
                    ScanResult(Confidence.Weak, "full-screen vertical scroller", false, visited, sample)
                else -> ScanResult(Confidence.None, "no Reels signals found", false, visited, sample)
            }
            else -> when {
                viewerNode -> ScanResult(Confidence.Strong, "Shorts player node", false, visited, sample)
                shortsTab -> ScanResult(Confidence.Strong, "Shorts tab label selected", false, visited, sample)
                homeSelected -> ScanResult(Confidence.None, "Home tab selected", false, visited, sample)
                fullScroller ->
                    ScanResult(Confidence.Weak, "full-screen vertical scroller", false, visited, sample)
                else -> ScanResult(Confidence.None, "no Shorts signals found", false, visited, sample)
            }
        }
    }

    /** A Reels / Shorts pager fills (nearly) the whole app window and scrolls vertically. */
    private fun isFullScreenVerticalScroller(node: AccessibilityNodeInfo, winW: Int, winH: Int): Boolean {
        if (!node.isScrollable) return false
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.height() < winH * 0.93f || r.width() < winW * 0.95f) return false
        return hasVerticalScrollAction(node)
    }

    private fun hasVerticalScrollAction(node: AccessibilityNodeInfo): Boolean {
        val actions = node.actionList ?: return false
        val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        val left = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
        val right = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
        if (actions.any { it.id == up || it.id == down }) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pageUp = AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id
            val pageDown = AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id
            if (actions.any { it.id == pageUp || it.id == pageDown }) return true
        }
        // Pager widgets may only expose forward / backward: accept unless it is clearly horizontal.
        return actions.none { it.id == left || it.id == right }
    }

    /** Overlays, keyboards and system chrome must not count as "leaving" a Reels surface. */
    private fun isIgnorable(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "android") return true
        if (pkg.contains("inputmethod", ignoreCase = true)) return true
        if (pkg.contains("keyboard", ignoreCase = true)) return true
        return false
    }

    private fun startOverlayIfPossible() {
        if (!Settings.canDrawOverlays(this)) return
        if (DynamicIslandOverlayService.isRunning) return
        val started = runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, DynamicIslandOverlayService::class.java)
            )
        }.isSuccess
        if (!started) DiagnosticsLog.log("Could not start the island service from the background")
    }

    companion object {
        private const val DEBOUNCE_MS = 1_300L
        private const val WATCHDOG_MS = 2_000L
        private const val WALK_MIN_GAP_MS = 4_000L
        private const val EJECT_COOLDOWN_MS = 1_500L
        private const val MIN_VERTICAL_DELTA_PX = 80
        private const val FALLBACK_MAX_NODES = 350
        private const val MISSES_BEFORE_LEAVE = 2

        /** Exact view ids of the Instagram Reels viewer containers. */
        private val IG_REEL_VIEW_IDS = listOf(
            "clips_viewer_view_pager",
            "clips_video_container",
            "reel_viewer_root",
            "clips_swiper"
        )

        /** Bottom navigation tabs. Whichever one reports isSelected tells us where the user is. */
        private val IG_TABS = listOf(
            "clips_tab",
            "feed_tab",
            "search_tab",
            "profile_tab",
            "direct_tab",
            "creation_tab"
        )

        private val IG_ID_HINTS = listOf("clips_viewer", "reel_viewer", "clips_swiper", "clips_video_container")

        /** Exact view ids of the YouTube Shorts player. */
        private val YT_SHORTS_VIEW_IDS = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_fragment_root",
            "shorts_container"
        )

        private val YT_ID_HINTS = listOf("reel_recycler", "reel_player", "reel_watch", "shorts_container")

        @Volatile
        var instance: ReelAccessibilityService? = null
            private set

        /** Until this time the blocker leaves the user alone (they just chose "Leave Reels"). */
        @Volatile
        var graceUntilMs: Long = 0L
            private set

        fun beginGrace(durationMs: Long = 3_500L) {
            graceUntilMs = System.currentTimeMillis() + durationMs
        }

        /** "Touch Grass": sends the user to the home screen, which also exits the Reels app. */
        fun goHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }

        /** "Leave Reels": one step back, out of the Reels / Shorts tab. */
        fun goBack(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }
}
