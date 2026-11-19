package com.neuroflow.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.neuroflow.app.data.NeuroFlowRepository

/**
 * Restarts the island guard service after a reboot or an app update so the Dynamic Island works
 * without having to open NeuroFlow first. Boot and package-replaced broadcasts are allowed to start
 * a foreground service from the background.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!NeuroFlowRepository.get(context).state.value.monitoringEnabled) return
        if (!Settings.canDrawOverlays(context)) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DynamicIslandOverlayService::class.java)
            )
        }
    }
}
