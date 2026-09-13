package coredevices.ring.pluskey.setup

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.BindException

internal data class LocalAdbEndpoint(val name: String, val port: Int)

internal object LocalAdbAddress {
    fun belongsToPhone(host: InetAddress?, local: Collection<InetAddress>): Boolean =
        host != null && !host.isAnyLocalAddress && !host.isMulticastAddress &&
            (host.isLoopbackAddress || local.any { it == host })

    // NSD may retain old ADB advertisements after Wireless debugging is restarted.
    // A free local port cannot be a live ADB endpoint; probing by binding avoids a TLS handshake.
    fun portIsListening(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", port)) }
            false
        } catch (_: BindException) { true }
        catch (_: Exception) { false }
    }
}

/** Android NSD discovers ADB; only advertisements from this phone can become endpoints. */
@Suppress("DEPRECATION")
internal class LocalAdbDiscovery(
    context: Context,
    private val type: String,
    private val changed: (LocalAdbEndpoint?) -> Unit,
    private val failed: () -> Unit
) : AutoCloseable {
    companion object {
        const val PAIRING = "_adb-tls-pairing._tcp."
        const val CONNECT = "_adb-tls-connect._tcp."
    }
    private val manager = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var registered = false
    private var resolving = false
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val present = mutableSetOf<String>()
    private val endpoints = linkedMapOf<String, LocalAdbEndpoint>()
    private val attempts = mutableMapOf<String, Int>()
    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) { handler.post {
            registered = true
            if (closed) stopDiscovery()
        } }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { handler.post {
            if (!closed) { failed(); close() }
        } }
        override fun onDiscoveryStopped(serviceType: String) { registered = false }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { registered = false }
        override fun onServiceFound(info: NsdServiceInfo) { handler.post {
            if (!closed && present.add(info.serviceName)) { pending.add(info); resolveNext() }
        } }
        override fun onServiceLost(info: NsdServiceInfo) { handler.post {
            present.remove(info.serviceName)
            attempts.remove(info.serviceName)
            if (endpoints.remove(info.serviceName) != null && !closed) changed(endpoints.values.firstOrNull())
        } }
    }

    fun start() {
        try { manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (_: Exception) { failed(); close() }
    }

    private fun resolveNext() {
        if (closed || resolving) return
        val info = pending.removeFirstOrNull() ?: return
        if (info.serviceName !in present) { resolveNext(); return }
        resolving = true
        try { manager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { handler.post {
                resolving = false
                val count = (attempts[info.serviceName] ?: 0) + 1
                attempts[info.serviceName] = count
                if (!closed && info.serviceName in present && count < 3) handler.postDelayed({
                    if (!closed) { pending.add(info); resolveNext() }
                }, 1000)
                resolveNext()
            } }
            override fun onServiceResolved(resolved: NsdServiceInfo) { handler.post {
                resolving = false
                if (!closed && info.serviceName in present) {
                    val addresses = runCatching {
                        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                    }.getOrDefault(emptyList())
                    if (LocalAdbAddress.belongsToPhone(resolved.host, addresses) && LocalAdbAddress.portIsListening(resolved.port)) {
                        endpoints[info.serviceName] = LocalAdbEndpoint(info.serviceName, resolved.port)
                        changed(endpoints.values.firstOrNull())
                    }
                }
                resolveNext()
            } }
        }) } catch (_: Exception) { resolving = false; failed(); close() }
    }

    private fun stopDiscovery() { runCatching { manager.stopServiceDiscovery(listener) }; registered = false }
    override fun close() {
        if (closed) return
        closed = true
        pending.clear()
        if (registered) stopDiscovery()
    }
}
