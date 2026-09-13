package coredevices.ring.pluskey

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.NotificationCompat
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.buffered
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Armed while the user is in our activity; microphone capture happens only during a hold. */
class IndexPlusKeyService : Service() {
    companion object {
        const val STOP = "coredevices.ring.pluskey.STOP"
        private const val CHANNEL = "index_plus_key"
        private const val NOTIFICATION = 7632
        private val mutable = MutableStateFlow(Status())
        val status = mutable.asStateFlow()
    }
    data class Status(val armed: Boolean = false, val recording: Boolean = false,
        val message: String = "Index key is off", val queued: Int = 0, val lastFile: String? = null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage: RecordingStorage by inject()
    private val queue: RecordingProcessingQueue by inject()
    private val sandboxes: coredevices.ring.database.room.repository.McpSandboxRepository by inject()
    private val events = Channel<Pair<Boolean, Long>>(Channel.UNLIMITED)
    private val gestures = PlusKeyGestures()
    @Volatile private var reader: java.lang.Process? = null
    private var session: Job? = null
    private var capture: Capture? = null
    private var wake: PowerManager.WakeLock? = null

    private class Capture(val id: String, val record: AudioRecord) {
        val running = AtomicBoolean(true)
        lateinit var writer: Job
        var bytes = 0L
        var failure: Throwable? = null
        fun stop() {
            if (running.getAndSet(false)) runCatching { record.stop() }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (session != null) return START_NOT_STICKY
        if (!IndexPlusKeySetup.state.value.ready || IndexPlusKeySetup.state.value.busy) {
            mutable.value = mutable.value.copy(message = "Open Index Plus Key and prepare the on-device models")
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            mutable.value = mutable.value.copy(message = "Open Index Plus Key and finish permission setup")
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Index Plus Key", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification("Ready — hold the Plus Key"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION, notification("Ready — hold the Plus Key"))
        mutable.value = mutable.value.copy(armed = true, recording = false, message = "Ready — hold the Plus Key")
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Index:PlusKey").apply { acquire(60 * 60 * 1000L) }
        session = scope.launch {
            val logJob = launch(Dispatchers.IO) {
                try {
                    val start = System.currentTimeMillis()
                    val parser = PlusKeyLogParser(start)
                    val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(start))
                    val process = ProcessBuilder("logcat", "-b", "system", "--uid=1000", "-v", "epoch", "-T", since,
                        "KEYLOG_OplusKeyEventUtil:I", "*:S").redirectErrorStream(true).start()
                    reader = process
                    if (!isActive) { process.destroy(); return@launch }
                    process.inputStream.bufferedReader().use { lines ->
                        while (isActive) {
                            val line = lines.readLine() ?: error("Key listener stopped. Re-enable with the key released.")
                            check(!line.contains("Permission denied", true) && !line.contains("not permitted", true)) { "Android denied key log access" }
                            parser.read(line, System.currentTimeMillis())?.let { events.send(it to SystemClock.elapsedRealtime()) }
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) withContext(Dispatchers.Main) {
                        mutable.value = mutable.value.copy(message = e.message ?: "Key listener failed")
                        stopSelf()
                    }
                }
            }
            try {
                sandboxes.seedDatabase()
                queue.resumePendingTasks()
                while (isActive) {
                    val edge = withTimeoutOrNull(10) { events.receive() }
                    val now = SystemClock.elapsedRealtime()
                    if (edge != null) check(now - edge.second <= 1000) { "Key processing delayed; re-enable with the key released" }
                    capture?.let { active -> if (active.writer.isCompleted) active.failure?.let { throw it } }
                    val actions = if (edge != null) gestures.edge(edge.first, edge.second) else gestures.advance(now)
                    for (action in actions) when (action) {
                        PlusKeyGestures.Action.Start -> startCapture()
                        PlusKeyGestures.Action.Submit -> finishCapture(true)
                        PlusKeyGestures.Action.Cancel -> finishCapture(false)
                    }
                    if (wake?.isHeld == false) wake?.acquire(60 * 60 * 1000L)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    mutable.value = mutable.value.copy(message = e.message ?: "Recording failed")
                    stopSelf()
                }
            } finally {
                reader?.destroy()
                logJob.cancel()
                withContext(NonCancellable) { finishCapture(false) }
            }
        }
        return START_NOT_STICKY
    }

    @Suppress("MissingPermission")
    private fun startCapture() {
        check(capture == null)
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "16 kHz microphone capture is unavailable" }
        val record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum, 4096)).build()
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); error("Microphone could not initialize") }
        val current = Capture("plus-key-${UUID.randomUUID()}", record)
        capture = current
        current.writer = scope.launch(Dispatchers.IO) {
            try {
                storage.openOriginalRecordingSink(current.id, 16000, "audio/raw").buffered().use { sink ->
                    if (current.running.get()) record.startRecording()
                    val pcm = ByteArray(640)
                    while (current.running.get() && isActive) {
                        val count = record.read(pcm, 0, pcm.size)
                        if (!current.running.get()) break
                        check(count > 0) { "Microphone interrupted ($count)" }
                        sink.write(pcm, 0, count)
                        current.bytes += count
                    }
                }
            } catch (e: Exception) {
                current.failure = e
            } finally { current.stop(); record.release() }
        }
        mutable.value = mutable.value.copy(recording = true, message = "Recording — release to process")
        android.util.Log.i("IndexPlusKey", "Capture started ${current.id}")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(mutable.value.message))
    }

    private suspend fun finishCapture(submit: Boolean) {
        val current = capture ?: return
        capture = null
        current.stop()
        current.writer.join()
        android.util.Log.i("IndexPlusKey", "Capture stopped ${current.id} submit=$submit bytes=${current.bytes}")
        mutable.value = mutable.value.copy(recording = false)
        if (!submit || current.bytes < 3200 || current.failure != null) {
            withContext(Dispatchers.IO) {
                for (id in listOf(current.id, "${current.id}-original")) runCatching { storage.deleteRecordingFromCache(id) }
            }
            if (submit) mutable.value = mutable.value.copy(message = current.failure?.message ?: "Recording too short — hold and speak")
        } else {
            withContext(Dispatchers.IO) {
                val (source, _) = storage.openRecordingSource(current.id, useOriginalAudio = true)
                source.use { src -> storage.openRecordingSink(current.id, 16000, "audio/raw").buffered().use { src.transferTo(it) } }
                // Match the phone recorder: the queue owns processing and later upload.
                queue.queueLocalAudioProcessing(current.id, buttonSequence = "long")
            }
            mutable.value = mutable.value.copy(message = "Sent to Index — hold again to record", queued = mutable.value.queued + 1, lastFile = current.id)
            android.util.Log.i("IndexPlusKey", "Queued ${current.id} bytes=${current.bytes} gesture=long")
        }
        if (mutable.value.armed) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(mutable.value.message))
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, IndexPlusKeyActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, IndexPlusKeyService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Index Plus Key").setContentText(message).setContentIntent(open).setOngoing(true)
            .setOnlyAlertOnce(true).addAction(0, "Disable", stop).build()
    }

    override fun onDestroy() {
        capture?.stop()
        reader?.destroy()
        gestures.cancel()
        mutable.value = mutable.value.copy(armed = false, recording = false, message = "Index key is off")
        scope.cancel()
        if (wake?.isHeld == true) wake?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
