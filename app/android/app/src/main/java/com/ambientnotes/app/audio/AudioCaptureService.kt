package com.ambientnotes.app.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ambientnotes.app.MainActivity
import com.ambientnotes.app.R
import com.ambientnotes.app.data.AppDatabase
import com.ambientnotes.app.data.SettingsRepository
import com.ambientnotes.app.data.SongLogEntry
import com.ambientnotes.app.recognition.RecognitionOrchestrator
import com.ambientnotes.app.recognition.RecognitionProviderFactory
import com.ambientnotes.app.recognition.RecognitionResult
import com.ambientnotes.app.targets.TargetPostingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Foreground service that periodically records a short microphone sample,
 * runs it through [RecognitionOrchestrator], logs a positive match to Room,
 * and fans the result out to configured [com.ambientnotes.app.targets.PostTarget]s.
 *
 * Sampling cadence is user-configurable (default 60s) to balance battery
 * life against how quickly a newly-playing song gets caught -- see
 * docs/CONFIGURATION.md#listening-interval.
 */
class AudioCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listenLoopJob: Job? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var orchestrator: RecognitionOrchestrator
    private lateinit var postingCoordinator: TargetPostingCoordinator

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository.getInstance(applicationContext)
        orchestrator = RecognitionOrchestrator(RecognitionProviderFactory(settingsRepository).buildAll())
        postingCoordinator = TargetPostingCoordinator()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (listenLoopJob?.isActive != true) {
            listenLoopJob = serviceScope.launch { listenLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        listenLoopJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun listenLoop() {
        while (true) {
            val intervalSeconds = settingsRepository.listeningIntervalSecondsSnapshot()
            try {
                captureAndRecognizeOnce()
            } catch (t: Throwable) {
                // A single failed cycle (mic busy, transient network error, etc.)
                // must never kill the long-running listening loop.
                android.util.Log.w(TAG, "Listen cycle failed, will retry next interval", t)
            }
            delay(intervalSeconds * 1000L)
        }
    }

    private suspend fun captureAndRecognizeOnce() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val pcm = recordPcm16Sample(SAMPLE_RATE_HZ, SAMPLE_DURATION_MS)
        val enabledOrder = settingsRepository.enabledProviderOrderSnapshot()
        if (enabledOrder.isEmpty()) return

        val outcome = orchestrator.recognize(pcm, SAMPLE_RATE_HZ, enabledOrder)
        if (!outcome.result.matched) return

        logAndPost(outcome.result)
    }

    private suspend fun logAndPost(result: RecognitionResult) {
        val dao = AppDatabase.getInstance(applicationContext).songLogDao()

        // Avoid re-posting the same song repeatedly while it's still playing:
        // skip if we already logged this exact title/artist within the last
        // 3 sampling intervals worth of time (heuristic; see docs for tuning).
        val recent = dao.mostRecentMatching(result.title, result.artist)
        val dedupeCutoffMs = 3 * settingsRepository.listeningIntervalSecondsSnapshot() * 1000L
        if (recent != null && (result.recognizedAtEpochMs - recent.recognizedAtEpochMs) < dedupeCutoffMs) {
            return
        }

        val externalIdsJson = JSONArray().apply {
            result.externalIds.forEach { (k, v) -> put(org.json.JSONObject().put("key", k).put("value", v)) }
        }.toString()

        val entry = SongLogEntry(
            title = result.title,
            artist = result.artist,
            album = result.album,
            releaseDate = result.releaseDate,
            confidence = result.confidence,
            providerName = result.providerName,
            externalIdsJson = externalIdsJson,
            recognizedAtEpochMs = result.recognizedAtEpochMs,
        )
        dao.insert(entry)

        val targetConfigs = settingsRepository.targetConfigsSnapshot()
        if (targetConfigs.isNotEmpty()) {
            postingCoordinator.postToAll(targetConfigs, result)
        }
    }

    /** Records raw 16-bit PCM mono audio for [durationMs] at [sampleRateHz]. */
    private fun recordPcm16Sample(sampleRateHz: Int, durationMs: Int): ByteArray {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBufferSize, sampleRateHz * 2 /* bytes/sample */ )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize,
        )
        val totalBytes = (sampleRateHz * 2 * durationMs) / 1000
        val output = ByteArray(totalBytes)

        try {
            recorder.startRecording()
            var offset = 0
            while (offset < totalBytes) {
                val read = recorder.read(output, offset, totalBytes - offset)
                if (read <= 0) break
                offset += read
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        return output
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.listening_notification_channel_name), NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.listening_notification_title))
            .setContentText(getString(R.string.listening_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AudioCaptureService"
        private const val CHANNEL_ID = "ambient_notes_listening"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_RATE_HZ = 16000
        private const val SAMPLE_DURATION_MS = 8000
    }
}
