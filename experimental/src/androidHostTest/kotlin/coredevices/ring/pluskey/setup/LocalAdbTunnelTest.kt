package coredevices.ring.pluskey.setup

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class LocalAdbTunnelTest {
    @Test fun forwardsBothDirectionsAndClosesConnections() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            LocalAdbTunnel(server.localPort).use { tunnel ->
                Socket("127.0.0.1", tunnel.port).use { client ->
                    server.accept().use { device ->
                        client.soTimeout = 3000
                        device.soTimeout = 3000
                        client.getOutputStream().write(42)
                        assertEquals(42, device.getInputStream().read())
                        device.getOutputStream().write(73)
                        assertEquals(73, client.getInputStream().read())
                        tunnel.close()
                        assertEquals(-1, client.getInputStream().read())
                        assertEquals(-1, device.getInputStream().read())
                    }
                }
            }
        }
    }
    @Test fun timeoutUnblocksAStalledPairingRead() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            LocalAdbTunnel(server.localPort, 1000).use { tunnel ->
                Socket("127.0.0.1", tunnel.port).use { client ->
                    server.accept().use { device ->
                        client.soTimeout = 5000
                        device.soTimeout = 5000
                        assertEquals(-1, client.getInputStream().read())
                        assertEquals(-1, device.getInputStream().read())
                    }
                }
            }
        }
    }
}
