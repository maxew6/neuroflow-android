package com.neuroflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.neuroflow.app.MainActivity
import com.neuroflow.app.R
import com.neuroflow.app.data.DiagnosticsLog
import com.neuroflow.app.data.NeuroFlowRepository
import com.neuroflow.app.data.NeuroFlowState
import com.neuroflow.app.ui.components.DynamicIslandHost
import com.neuroflow.app.ui.components.IslandActions
import com.neuroflow.app.ui.components.IslandController
import com.neuroflow.app.ui.components.IslandMode
import com.neuroflow.app.ui.components.buildIslandModel
import com.neuroflow.app.ui.theme.NeuroFlowTheme
import com.neuroflow.app.util.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Foreground service that attaches a [ComposeView] to the system [WindowManager]
 * (TYPE_APPLICATION_OVERLAY, top-centre) and shows the fluid Dynamic Island whenever a tracked
 * short-form app is in the foreground (or the in-app simulator preview is switched on).
 */
class DynamicIslandOverlayService :
    LifecycleService(),
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    // --- owners required so a ComposeView can live inside a Service window ---
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = vmStore

    // --- state ---
    private lateinit var repo: NeuroFlowRepository
    private lateinit var windowManager: WindowManager
    private val controller = IslandController()

    private var rootView: DragFrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hideJob: Job? = null
    private var shrinkJob: Job? = null
    private var isFullScreen = false
    private var previewClockStartMs = System.currentTimeMillis()

    private var offsetX = 0f
    private var offsetY = 0f
    private var snapCounter = 0
    private var snapRequest by mutableStateOf<SnapRequest?>(null)

    /** True from the first drag event until the settled position has been saved. */
    private var userDragging = false

    private val density: Float
        get() = resources.displayMetrics.density

    /** System status bar height, so the pill never covers the clock, alarm, network and battery icons. */
    private val statusBarHeightPx: Int by lazy {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) resources.getDimensionPixelSize(id) else (32f * density).roundToInt()
    }

    /** Transparent padding around the pill that hosts the neon glow (see IslandSurface). */
    private val glowPadPx: Int
        get() = (14f * density).roundToInt()

    /** Window y so the visible pill floats 6.dp just BELOW the status bar (glow padding compensated). */
    private val baseY: Int
        get() = statusBarHeightPx + (6f * density).roundToInt() - glowPadPx

    private class SnapRequest(val id: Int, val vx: Float, val vy: Float)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            repo.onLeftTrackedApp(System.currentTimeMillis())
        }
    }

    private val actions = object : IslandActions {
        override fun onPause15() {
            repo.pauseFor(15, System.currentTimeMillis())
        }

        override fun onExit() {
            exitToHome()
        }

        override fun onBonusReels(extra: Int) {
            val s = repo.state.value
            // Synapse Spark bonus only matters once the daily limit has actually been reached.
            if (!s.previewEnabled && s.isLimitReached) repo.grantBonusReels(extra)
        }

        override fun onLeaveReels() {
            val s = repo.state.value
            if (!s.blockerIsTest) {
                // Give the blocker a moment so it does not re-trigger while Back is still animating.
                ReelAccessibilityService.beginGrace()
                ReelAccessibilityService.goBack()
            }
            repo.setBlocker(false, false)
        }

        override fun onMathSolved() {
            val s = repo.state.value
            if (!s.blockerIsTest) repo.grantBonusReels(5)
            repo.setBlocker(false, false)
        }

        override fun onClosePreview() {
            repo.setPreviewEnabled(false)
        }

        override fun tick() {
            Haptics.tick(applicationContext)
        }

        override fun pulse(durationMs: Long, amplitude: Int) {
            Haptics.pulse(applicationContext, durationMs, amplitude)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        isRunning = true
        repo = NeuroFlowRepository.get(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        lifecycleScope.launch {
            repo.state
                .map { shouldShow(it) }
                .distinctUntilChanged()
                .collect { show -> if (show) showOverlay() else hideOverlay() }
        }

        // X/Y offset calibration sliders in Settings move the pill live.
        lifecycleScope.launch {
            repo.state
                .map { it.islandOffsetXDp to it.islandOffsetYDp }
                .distinctUntilChanged()
                .collect { (xDp, yDp) ->
                    if (!userDragging) {
                        offsetX = xDp * density
                        offsetY = yDp * density
                        applyOffset()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        hideJob?.cancel()
        shrinkJob?.cancel()
        removeOverlayNow()
        runCatching { unregisterReceiver(screenOffReceiver) }
        vmStore.clear()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------
    // Foreground notification
    // ---------------------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN
        )
        channel.setShowBadge(false)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_neuroflow)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Visibility
    // ---------------------------------------------------------------------------------------

    private fun shouldShow(s: NeuroFlowState): Boolean {
        if (s.previewEnabled || s.blockerActive) return true
        val app = s.activeApp ?: return false
        return s.monitoringEnabled && s.isAppEnabled(app)
    }

    private fun showOverlay() {
        hideJob?.cancel()
        if (rootView != null) {
            controller.visible = true
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            DiagnosticsLog.setOverlayState("cannot show: Display over other apps is off")
            return
        }

        previewClockStartMs = System.currentTimeMillis()
        val saved = repo.state.value
        offsetX = saved.islandOffsetXDp * density
        offsetY = saved.islandOffsetYDp * density
        userDragging = false
        isFullScreen = false

        val params = buildLayoutParams()
        val root = DragFrameLayout(this)
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.onDragStart = {
            snapRequest = null
            userDragging = true
        }
        root.onDragBy = { dx, dy ->
            offsetX += dx
            offsetY += dy
            applyOffset()
        }
        root.onDragEnd = { vx, vy ->
            snapCounter += 1
            snapRequest = SnapRequest(snapCounter, vx, vy)
        }

        val composeView = ComposeView(this)
        composeView.setContent {
            NeuroFlowTheme {
                OverlayContent()
            }
        }
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        val added = runCatching { windowManager.addView(root, params) }
        if (added.isFailure) {
            DiagnosticsLog.setOverlayState(
                "addView failed: " + (added.exceptionOrNull()?.javaClass?.simpleName ?: "unknown")
            )
            return
        }
        rootView = root
        layoutParams = params
        controller.visible = true
        DiagnosticsLog.setOverlayState("visible")
    }

    private fun hideOverlay() {
        if (rootView == null) return
        controller.visible = false
        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(450)
            removeOverlayNow()
        }
    }

    private fun removeOverlayNow() {
        val root = rootView
        if (root != null) {
            runCatching { windowManager.removeViewImmediate(root) }
        }
        if (root != null) DiagnosticsLog.setOverlayState("hidden")
        rootView = null
        layoutParams = null
        isFullScreen = false
        snapRequest = null
        offsetX = 0f
        offsetY = 0f
        userDragging = false
        controller.reset()
    }

    // ---------------------------------------------------------------------------------------
    // Window geometry
    // ---------------------------------------------------------------------------------------

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = baseY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params.title = "NeuroFlow Island"
        return params
    }

    private fun updateWindow() {
        val root = rootView ?: return
        val params = layoutParams ?: return
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun applyOffset() {
        val params = layoutParams ?: return
        if (isFullScreen) return
        params.x = offsetX.roundToInt()
        params.y = baseY + offsetY.roundToInt()
        updateWindow()
    }

    private fun setBlur(params: WindowManager.LayoutParams, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = 40
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                params.blurBehindRadius = 0
            }
        }
    }

    /** Full-screen intervention needs a MATCH_PARENT window; every other mode wraps the pill. */
    private fun applyModeToWindow(mode: IslandMode) {
        val root = rootView ?: return
        val params = layoutParams ?: return
        val fullMode = mode == IslandMode.Intervention || mode == IslandMode.Lockout
        root.dragEnabled = !fullMode

        if (fullMode) {
            shrinkJob?.cancel()
            snapRequest = null
            if (!isFullScreen) {
                isFullScreen = true
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.x = 0
                params.y = 0
                setBlur(params, true)
                updateWindow()
            }
        } else if (isFullScreen) {
            shrinkJob?.cancel()
            shrinkJob = lifecycleScope.launch {
                // Let the spring finish collapsing before the window itself shrinks.
                delay(800)
                val stillFull = controller.mode == IslandMode.Intervention ||
                    controller.mode == IslandMode.Lockout
                if (!stillFull && isFullScreen) {
                    isFullScreen = false
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.x = offsetX.roundToInt()
                    params.y = baseY + offsetY.roundToInt()
                    setBlur(params, false)
                    updateWindow()
                }
            }
        }
    }

    private fun exitToHome() {
        if (repo.state.value.previewEnabled) {
            repo.setPreviewEnabled(false)
            return
        }
        if (!ReelAccessibilityService.goHome()) {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Drag bounds (keep the whole pill on screen)
    // ---------------------------------------------------------------------------------------

    private fun minOffsetX(): Float {
        val half = resources.displayMetrics.widthPixels / 2f - (186f / 2f + 14f) * density
        return -half.coerceAtLeast(0f)
    }

    private fun maxOffsetX(): Float = -minOffsetX()

    private fun minOffsetY(): Float = -(baseY + glowPadPx).toFloat()

    private fun maxOffsetY(): Float {
        val windowHeight = (38f + 28f) * density
        return (resources.displayMetrics.heightPixels - baseY - windowHeight).coerceAtLeast(0f)
    }

    // ---------------------------------------------------------------------------------------
    // Compose content
    // ---------------------------------------------------------------------------------------

    @Composable
    private fun OverlayContent() {
        val state by repo.state.collectAsState()
        val nowMs by produceState(initialValue = System.currentTimeMillis()) {
            while (true) {
                delay(1000)
                value = System.currentTimeMillis()
            }
        }
        val model = buildIslandModel(state, nowMs, previewClockStartMs)

        // Keep the window geometry in sync with the island's morph state.
        LaunchedEffect(controller.mode) {
            applyModeToWindow(controller.mode)
        }

        // Drag release: spring (bouncy) to the nearest on-screen spot, then remember it as the new
        // X/Y offset so the sliders in Settings stay in sync.
        val request = snapRequest
        LaunchedEffect(request?.id) {
            if (request != null) {
                val spec = spring<Float>(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
                val targetX = offsetX.coerceIn(minOffsetX(), maxOffsetX())
                val targetY = offsetY.coerceIn(minOffsetY(), maxOffsetY())
                coroutineScope {
                    launch {
                        Animatable(offsetX).animateTo(targetX, spec, request.vx) {
                            offsetX = value
                            applyOffset()
                        }
                    }
                    launch {
                        Animatable(offsetY).animateTo(targetY, spec, request.vy) {
                            offsetY = value
                            applyOffset()
                        }
                    }
                }
                val xDp = (targetX / density).roundToInt()
                val yDp = (targetY / density).roundToInt()
                offsetX = xDp * density
                offsetY = yDp * density
                applyOffset()
                repo.setIslandOffsets(xDp, yDp)
                userDragging = false
            }
        }

        DynamicIslandHost(
            model = model,
            controller = controller,
            actions = actions
        )
    }

    // ---------------------------------------------------------------------------------------
    // Drag handling (window moves with the finger, velocity is tracked for the spring)
    // ---------------------------------------------------------------------------------------

    private class DragFrameLayout(context: Context) : FrameLayout(context) {
        var dragEnabled: Boolean = true
        var onDragStart: (() -> Unit)? = null
        var onDragBy: ((Float, Float) -> Unit)? = null
        var onDragEnd: ((Float, Float) -> Unit)? = null

        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false
        private var tracker: VelocityTracker? = null

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (!dragEnabled) return false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    lastX = ev.rawX
                    lastY = ev.rawY
                    dragging = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    track(ev)
                }
                MotionEvent.ACTION_MOVE -> {
                    track(ev)
                    if (!dragging &&
                        (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)
                    ) {
                        dragging = true
                        lastX = ev.rawX
                        lastY = ev.rawY
                        onDragStart?.invoke()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) releaseTracker()
                }
            }
            return dragging
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked != MotionEvent.ACTION_DOWN) track(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        val dx = ev.rawX - lastX
                        val dy = ev.rawY - lastY
                        lastX = ev.rawX
                        lastY = ev.rawY
                        onDragBy?.invoke(dx, dy)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        var vx = 0f
                        var vy = 0f
                        tracker?.let {
                            it.computeCurrentVelocity(1000)
                            vx = it.xVelocity
                            vy = it.yVelocity
                        }
                        dragging = false
                        onDragEnd?.invoke(vx, vy)
                    }
                    releaseTracker()
                }
            }
            return true
        }

        /** Feeds raw screen coordinates so velocity stays correct while the window itself moves. */
        private fun track(ev: MotionEvent) {
            val t = tracker ?: VelocityTracker.obtain().also { tracker = it }
            val copy = MotionEvent.obtain(ev)
            copy.setLocation(ev.rawX, ev.rawY)
            t.addMovement(copy)
            copy.recycle()
        }

        private fun releaseTracker() {
            tracker?.recycle()
            tracker = null
        }
    }

    companion object {
        private const val CHANNEL_ID = "neuroflow_island"
        private const val NOTIFICATION_ID = 4107
        const val ACTION_STOP = "com.neuroflow.app.action.STOP_ISLAND"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
