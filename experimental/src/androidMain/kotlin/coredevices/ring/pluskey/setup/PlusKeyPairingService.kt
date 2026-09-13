package coredevices.ring.pluskey.setup

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.*
import java.util.UUID

/** Owns discovery and notification replies while the user is in Android Settings. */
class PlusKeyPairingService : Service() {
    companion object {
        const val CHANNEL = "plus-key-setup"
        const val NOTIFICATION = 43
        const val SEARCH = "coredevices.ring.pluskey.setup.SEARCH"
        const val ENABLE = "coredevices.ring.pluskey.setup.ENABLE"
        const val OPEN_SETUP = "coredevices.ring.pluskey.setup.OPEN"
        internal const val REPLY = "coredevices.ring.pluskey.setup.REPLY"
        internal const val STOP = "coredevices.ring.pluskey.setup.STOP"
        internal const val CODE = "pairing-code"
        private const val TOKEN = "endpoint-token"

        fun notificationsEnabled(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var discovery: LocalAdbDiscovery? = null
    private var endpoint: LocalAdbEndpoint? = null
    private var token = UUID.randomUUID().toString()
    private var actionJob: Job? = null
    private var deadline: Job? = null
    private var finished = false
    private var foreground = false
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Plus Key setup", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Find this phone and enter its pairing code from Android Settings"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
        PlusKeySetupSession.refresh(this)
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent == null || intent.action !in setOf(SEARCH, ENABLE, REPLY) || Build.VERSION.SDK_INT < 30) {
            stopSelf(); return START_NOT_STICKY
        }
        if (!notificationsEnabled(this)) {
            finished = true
            PlusKeySetupSession.update { it.copy(phase=SetupPhase.Error, message="Allow setup notifications to pair from Android Settings.") }
            stopSelf(); return START_NOT_STICKY
        }
        finished = false
        try {
            if (!foreground) {
                val initial = notification("Key setup", "Preparing setup…")
                if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                else startForeground(NOTIFICATION, initial)
                foreground = true
            }
        } catch (_: Exception) {
            finished = true
            PlusKeySetupSession.update { it.copy(phase=SetupPhase.Error, message="Open Index and start setup again.") }
            stopSelf(); return START_NOT_STICKY
        }
        when (intent.action) {
            SEARCH -> if (actionJob?.isActive != true) search(pairing=true)
            ENABLE -> if (actionJob?.isActive != true) search(pairing=false)
            REPLY -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CODE)?.toString()?.trim().orEmpty()
                // Never persist or redeliver a pairing code. A reply belongs only to the discovered endpoint.
                receiveCode(code, intent.getStringExtra(TOKEN))
                intent.removeExtra(CODE)
                intent.clipData = null
            }
        }
        return START_NOT_STICKY
    }

    private fun search(pairing: Boolean) {
        discovery?.close()
        endpoint = null
        token = UUID.randomUUID().toString()
        deadline?.cancel()
        val phase = if (pairing) SetupPhase.SearchingPairing else SetupPhase.SearchingConnection
        val title = if (pairing) "Searching for pairing service" else "Finding wireless debugging"
        val body = if (pairing) "Open Wireless debugging, then Pair device with pairing code." else "Keep Wi-Fi and Wireless debugging on."
        PlusKeySetupSession.update { it.copy(phase=phase, message=body) }
        notifications.notify(NOTIFICATION, notification(title, body, canStop=true))
        deadline = scope.launch {
            delay(if (pairing) 10 * 60_000L else 30_000L)
            finishError(if (pairing) "Pairing search stopped. Return to Index to try again." else "Wireless debugging was not found. Turn it on in Developer options, then try again.")
        }
        discovery = LocalAdbDiscovery(this, if (pairing) LocalAdbDiscovery.PAIRING else LocalAdbDiscovery.CONNECT,
            changed = { found ->
                if (endpoint != found) token = UUID.randomUUID().toString()
                endpoint = found
                if (actionJob?.isActive != true) {
                    if (pairing) {
                        PlusKeySetupSession.update { it.copy(phase=if(found==null) SetupPhase.SearchingPairing else SetupPhase.PairingCode, message=if(found==null) body else "Enter the six-digit code in the Index notification.") }
                        notifications.notify(NOTIFICATION, if(found==null) notification(title,body,canStop=true) else codeNotification())
                    } else if (found != null) enable(found)
                }
            }, failed = { finishError("Could not search this phone. Check local network access and Wi-Fi, then try again.") })
            .also { it.start() }
    }

    private fun receiveCode(code: String, replyToken: String?) {
        if (actionJob?.isActive == true) return
        val target = endpoint
        if (target == null || replyToken != token) { search(pairing=true); return }
        if (!PlusKeySetupInput.codeValid(code)) {
            val message = "Enter all six digits from Android’s pairing dialog."
            PlusKeySetupSession.update { it.copy(phase=SetupPhase.PairingCode, message=message) }
            notifications.notify(NOTIFICATION, codeNotification(message))
            return
        }
        PlusKeySetupSession.update { it.copy(phase=SetupPhase.Pairing, message="Pairing with this phone…") }
        notifications.notify(NOTIFICATION, notification("Pairing…", "Keep Android’s pairing dialog open."))
        actionJob = scope.launch {
            try {
                withContext(Dispatchers.IO) { PlusKeyAdb.pair(application, target.port, code) }
                PlusKeySetupSession.paired(this@PlusKeyPairingService)
                finish("Pairing successful", "Return to Index to enable the Plus Key.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (endpoint != null) {
                    PlusKeySetupSession.update { it.copy(phase=SetupPhase.PairingCode, message="Pairing failed. Keep the dialog open and enter its current code in the notification.") }
                    notifications.notify(NOTIFICATION, codeNotification("Pairing failed. Enter the current six-digit code and try again."))
                } else search(pairing=true)
            }
        }
    }

    private fun enable(target: LocalAdbEndpoint) {
        deadline?.cancel()
        discovery?.close()
        PlusKeySetupSession.update { it.copy(phase=SetupPhase.Enabling, message="Enabling the Plus Key…") }
        notifications.notify(NOTIFICATION, notification("Enabling the Plus Key…", "Connecting to this phone."))
        actionJob = scope.launch {
            try {
                val output = withContext(Dispatchers.IO) { PlusKeyAdb.grant(application, target.port) }
                PlusKeySetupSession.refresh(this@PlusKeyPairingService)
                if (PlusKeySetupSession.state.value.granted) {
                    PlusKeySetupSession.update { it.copy(phase=SetupPhase.Complete, message="Plus Key is ready.") }
                    finish("Plus Key is ready", "Return to Index to finish setup.")
                } else finishError(if (output.contains("GRANT_RUNTIME_PERMISSIONS"))
                    "Android blocked the permission. Check the System optimization setting in step 1, then try again."
                    else "Android did not enable log access. Check Developer options and try again.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { finishError("Could not connect. Keep Wireless debugging on. If this phone is no longer paired, go back and pair again.") }
        }
    }

    private fun codeNotification(body: String = "Enter the code shown in Android’s pairing dialog."): Notification =
        notification("Pairing service found", body, canStop=true, reply=true)

    private fun notification(title: String, body: String, canStop: Boolean = false, reply: Boolean = false, terminal: Boolean = false): Notification {
        val open = PendingIntent.getActivity(this, NOTIFICATION,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("pebble://navbar/index")).setClassName(packageName, "coredevices.coreapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setOngoing(!terminal).setAutoCancel(terminal)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(!reply)
            .setAllowSystemGeneratedContextualActions(false).setColor(0xFF246347.toInt())
        if (reply) {
            val intent = Intent(this, PlusKeyPairingService::class.java).setAction(REPLY).putExtra(TOKEN, token)
                .setData(android.net.Uri.parse("indexpluskey://setup/$token"))
            val pending = PendingIntent.getForegroundService(this, NOTIFICATION, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31) PendingIntent.FLAG_MUTABLE else 0)
            builder.addAction(NotificationCompat.Action.Builder(0, "Enter pairing code", pending)
                .addRemoteInput(RemoteInput.Builder(CODE).setLabel("Six-digit pairing code").build())
                .setAllowGeneratedReplies(false).build())
        }
        if (canStop) builder.addAction(0, "Stop searching", PendingIntent.getService(this, NOTIFICATION+1,
            Intent(this, PlusKeyPairingService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
        return builder.build()
    }

    private fun finishError(message: String) {
        PlusKeySetupSession.update { it.copy(phase=SetupPhase.Error, message=message) }
        finish("Setup needs attention", message)
    }
    private fun finish(title: String, message: String) {
        finished = true
        deadline?.cancel()
        discovery?.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        notifications.notify(NOTIFICATION, notification(title,message,terminal=true))
        stopSelf()
    }
    override fun onDestroy() {
        discovery?.close()
        scope.cancel()
        if (!finished) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            PlusKeySetupSession.update { it.copy(phase=SetupPhase.Idle, message="Setup stopped. You can start again when ready.") }
        }
        super.onDestroy()
    }
}
