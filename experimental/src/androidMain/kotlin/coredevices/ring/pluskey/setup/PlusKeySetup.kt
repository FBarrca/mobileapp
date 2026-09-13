package coredevices.ring.pluskey.setup

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object PlusKeySetupInput {
    fun port(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..65535 }
    fun codeValid(value: String) = value.length == 6 && value.all { it in '0'..'9' }
    fun grantCommand(user: Int, packageName: String): String {
        require(user >= 0)
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
        return "pm grant --user $user $packageName android.permission.READ_LOGS"
    }
}

enum class SetupPhase { Idle, SearchingPairing, PairingCode, Pairing, Paired, SearchingConnection, Enabling, Complete, Error }

data class PlusKeySetupState(
    val granted: Boolean = false,
    val paired: Boolean = false,
    val phase: SetupPhase = SetupPhase.Idle,
    val message: String = ""
) {
    val busy get() = phase in setOf(SetupPhase.Pairing, SetupPhase.SearchingConnection, SetupPhase.Enabling)
    val searching get() = phase == SetupPhase.SearchingPairing || phase == SetupPhase.PairingCode
}

/** Shared with the foreground setup service so leaving the activity never interrupts pairing. */
internal object PlusKeySetupSession {
    private val mutable = MutableStateFlow(PlusKeySetupState())
    val state = mutable.asStateFlow()
    fun update(change: (PlusKeySetupState) -> PlusKeySetupState) { mutable.value = change(mutable.value) }
    fun refresh(context: android.content.Context) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
        val paired = context.getSharedPreferences("plus-key-setup", 0).getBoolean("paired", false)
        update { it.copy(granted=granted, paired=paired) }
    }
    fun paired(context: android.content.Context) {
        context.getSharedPreferences("plus-key-setup", 0).edit().putBoolean("paired", true).apply()
        update { it.copy(paired=true, phase=SetupPhase.Paired, message="Return to Index to enable the Plus Key.") }
    }
}

class PlusKeySetupModel(application: Application) : AndroidViewModel(application) {
    val state = PlusKeySetupSession.state
    init { refresh() }
    fun refresh() = PlusKeySetupSession.refresh(getApplication())
    fun startPairing() = start(PlusKeyPairingService.SEARCH)
    fun grant() = start(PlusKeyPairingService.ENABLE)
    fun stop() {
        getApplication<Application>().stopService(android.content.Intent(getApplication(), PlusKeyPairingService::class.java))
    }
    private fun start(action: String) {
        if (Build.VERSION.SDK_INT < 30 || state.value.busy) return
        runCatching {
            ContextCompat.startForegroundService(getApplication(),
                android.content.Intent(getApplication(), PlusKeyPairingService::class.java).setAction(action))
        }.onFailure {
            PlusKeySetupSession.update { it.copy(phase=SetupPhase.Error, message="Could not start setup. Keep Index open and try again.") }
        }
    }
}

/** Runs only the existing, narrowly scoped local ADB operations. No arbitrary shell input. */
internal object PlusKeyAdb {
    @Synchronized
    fun pair(app: Application, port: Int, code: String) {
        require(port in 1..65535 && PlusKeySetupInput.codeValid(code))
        SetupAdb(app).use { adb ->
            LocalAdbTunnel(port).use { tunnel ->
                check(adb.pair("127.0.0.1", tunnel.port, code)) { "Pairing rejected" }
            }
        }
    }
    @Synchronized
    fun grant(app: Application, port: Int): String {
        require(port in 1..65535)
        return SetupAdb(app).use { adb ->
            LocalAdbTunnel(port).use { tunnel ->
                check(adb.connect("127.0.0.1", tunnel.port)) { "Connection timed out" }
                adb.openStream("shell:" + PlusKeySetupInput.grantCommand(Process.myUid() / 100000, app.packageName)).use { stream ->
                    stream.openInputStream().bufferedReader().use { it.readText().take(8192) }
                }
            }
        }
    }
}

/** Setup identity stays in private, non-backed-up storage; no debugging connection survives an action. */
private class SetupAdb(app: Application) : AbsAdbConnectionManager() {
    private val identity = KeyStore.getInstance("PKCS12")
    init {
        val file = File(app.noBackupFilesDir, "plus-key-adb.p12")
        if (file.exists()) file.inputStream().use { identity.load(it, CharArray(0)) }
        else {
            identity.load(null)
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=Index Plus Key setup")
            val now = System.currentTimeMillis()
            val certificate = JcaX509CertificateConverter().getCertificate(
                JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 60000),
                    Date(now + TimeUnit.DAYS.toMillis(3650)), name, pair.public)
                    .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)))
            identity.setKeyEntry("adb", pair.private, CharArray(0), arrayOf(certificate))
            val temporary = File(app.noBackupFilesDir, "plus-key-adb.tmp")
            temporary.outputStream().use { identity.store(it, CharArray(0)) }
            check(temporary.renameTo(file)) { "Cannot save setup identity" }
        }
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }
    override fun getPrivateKey() = identity.getKey("adb", CharArray(0)) as PrivateKey
    override fun getCertificate(): Certificate = identity.getCertificate("adb")
    override fun getDeviceName() = "Index-PlusKey"
    // LibADB caches its TLS context and key manager across setup actions.
    override fun close() { disconnect() }
}

/** Confines setup to this phone and bounds even the library's blocking TLS/pairing reads. */
internal class LocalAdbTunnel(targetPort: Int, timeoutMillis: Long = 25000) : AutoCloseable {
    private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = listener.localPort
    private val workers = Executors.newScheduledThreadPool(3)
    private val sockets = java.util.concurrent.CopyOnWriteArrayList<Socket>()
    @Volatile private var closed = false
    init {
        workers.schedule({ close() }, timeoutMillis, TimeUnit.MILLISECONDS)
        workers.execute {
            try {
                val client = listener.accept()
                sockets.add(client)
                if (closed) { client.close(); return@execute }
                val device = Socket()
                sockets.add(device)
                if (closed) { device.close(); return@execute }
                device.connect(java.net.InetSocketAddress("127.0.0.1", targetPort), 5000)
                workers.execute { copy(client, device) }
                copy(device, client)
            } catch (_: Exception) { close() }
        }
    }
    private fun copy(from: Socket, to: Socket) {
        try { from.getInputStream().copyTo(to.getOutputStream()) } catch (_: Exception) { }
        finally { close() }
    }
    override fun close() {
        closed = true
        runCatching { listener.close() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}
