package com.neuroflow.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.neuroflow.app.data.NeuroFlowRepository
import com.neuroflow.app.service.DynamicIslandOverlayService
import com.neuroflow.app.ui.components.AppCallbacks
import com.neuroflow.app.ui.components.FloatingNavBar
import com.neuroflow.app.ui.components.GhostPillButton
import com.neuroflow.app.ui.screens.AnalyticsScreen
import com.neuroflow.app.ui.screens.HomeScreen
import com.neuroflow.app.ui.screens.PermissionSetupCard
import com.neuroflow.app.ui.screens.SettingsScreen
import com.neuroflow.app.ui.theme.NF
import com.neuroflow.app.ui.theme.NeuroFlowTheme
import com.neuroflow.app.ui.theme.auroraBackdrop
import com.neuroflow.app.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val repo: NeuroFlowRepository by lazy { NeuroFlowRepository.get(applicationContext) }

    /** Bumped on every resume / permission result so the UI re-reads system permission state. */
    private var permissionTick by mutableIntStateOf(0)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionTick++
            ensureOverlayServiceRunning(force = false)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val callbacks = AppCallbacks(
            openOverlaySettings = {
                runCatching { startActivity(PermissionUtils.overlaySettingsIntent(this)) }
            },
            openAccessibilitySettings = {
                runCatching { startActivity(PermissionUtils.accessibilitySettingsIntent()) }
                Toast.makeText(
                    this,
                    "Find \"NeuroFlow Reel Scroll Sensor\" and switch it on.",
                    Toast.LENGTH_LONG
                ).show()
            },
            requestNotifications = { requestNotifications() },
            requestBatteryUnrestricted = { requestBatteryUnrestricted() },
            setPreview = { enabled -> setPreview(enabled) },
            setMonitoring = { enabled -> setMonitoring(enabled) },
            testBlocker = { testBlocker() }
        )

        setContent {
            NeuroFlowTheme {
                NeuroFlowApp(
                    repo = repo,
                    permissionTick = permissionTick,
                    callbacks = callbacks
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionTick++
        ensureOverlayServiceRunning(force = false)
    }

    // ---------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { startActivity(PermissionUtils.notificationSettingsIntent(this)) }
        }
    }

    private fun requestBatteryUnrestricted() {
        val direct = runCatching { startActivity(PermissionUtils.batterySettingsIntent(this)) }
        if (direct.isFailure) {
            runCatching { startActivity(PermissionUtils.batteryFallbackIntent()) }
        }
    }

    private fun setPreview(enabled: Boolean) {
        if (!enabled) {
            repo.setPreviewEnabled(false)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Allow \"Display over other apps\" first, then switch the preview on again.",
                Toast.LENGTH_LONG
            ).show()
            runCatching { startActivity(PermissionUtils.overlaySettingsIntent(this)) }
            return
        }
        ensureOverlayServiceRunning(force = true)
        repo.setPreviewEnabled(true)
    }

    /** Home tab: shows the real full-screen Strict Reels Blocker so the math challenge can be tried. */
    private fun testBlocker() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Allow \"Display over other apps\" first, then tap the test again.",
                Toast.LENGTH_LONG
            ).show()
            runCatching { startActivity(PermissionUtils.overlaySettingsIntent(this)) }
            return
        }
        ensureOverlayServiceRunning(force = true)
        repo.setBlocker(true, true)
    }

    private fun setMonitoring(enabled: Boolean) {
        repo.setMonitoringEnabled(enabled)
        if (enabled) {
            ensureOverlayServiceRunning(force = false)
        } else if (!repo.state.value.previewEnabled) {
            stopService(Intent(this, DynamicIslandOverlayService::class.java))
        }
    }

    /** Starts the overlay guard service from the foreground, where Android always allows it. */
    private fun ensureOverlayServiceRunning(force: Boolean) {
        if (!Settings.canDrawOverlays(this)) return
        if (!force && !repo.state.value.monitoringEnabled) return
        if (DynamicIslandOverlayService.isRunning) return
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, DynamicIslandOverlayService::class.java)
            )
        }
    }
}

// =============================================================================================
// Root composable
// =============================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuroFlowApp(
    repo: NeuroFlowRepository,
    permissionTick: Int,
    callbacks: AppCallbacks
) {
    val ctx = LocalContext.current
    val state by repo.state.collectAsState()
    val perms = remember(permissionTick) { PermissionUtils.status(ctx) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var sheetDismissed by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackdrop()
    ) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "tabs"
        ) { current ->
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(top = 14.dp, bottom = 120.dp)
            ) {
                when (current) {
                    0 -> HomeScreen(
                        state = state,
                        perms = perms,
                        callbacks = callbacks,
                        onPreviewReelsChange = { repo.setPreviewReels(it) }
                    )
                    1 -> AnalyticsScreen(state = state)
                    else -> SettingsScreen(
                        repo = repo,
                        state = state,
                        perms = perms,
                        callbacks = callbacks
                    )
                }
            }
        }

        FloatingNavBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
        )
    }

    // First-run onboarding sheet: shown until the two core permissions are granted or dismissed.
    if (!perms.coreGranted && !sheetDismissed) {
        ModalBottomSheet(
            onDismissRequest = { sheetDismissed = true },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = NF.GlassSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Wake up your Dynamic Island",
                    color = NF.Ice,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Two quick switches let NeuroFlow float over Instagram and count your Reels.",
                    color = NF.Slate,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                PermissionSetupCard(perms, callbacks)
                Spacer(Modifier.height(16.dp))
                GhostPillButton(
                    text = "Maybe later",
                    onClick = { sheetDismissed = true }
                )
            }
        }
    }
}
