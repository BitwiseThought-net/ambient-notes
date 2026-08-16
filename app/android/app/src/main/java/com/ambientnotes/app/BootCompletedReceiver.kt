package com.ambientnotes.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ambientnotes.app.audio.AudioCaptureService
import com.ambientnotes.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts the listening foreground service after device reboot, but only
 * if the user had previously enabled listening -- this is opt-in, not a
 * silent auto-start every user gets by default. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsRepository.getInstance(context).observeListeningEnabled()
                    .let { flow -> kotlinx.coroutines.flow.first(flow) }
                if (enabled) {
                    ContextCompat.startForegroundService(context, Intent(context, AudioCaptureService::class.java))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
