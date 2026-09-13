package coredevices.ring.pluskey.setup

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class LocalAdbAddressTest {
    private fun address(value: String) = InetAddress.getByName(value)
    private val phone = listOf(address("192.168.1.190"), address("fe80::1234"))
    @Test fun acceptsOnlyThisPhoneAndLoopback() {
        assertTrue(LocalAdbAddress.belongsToPhone(address("192.168.1.190"), phone))
        assertTrue(LocalAdbAddress.belongsToPhone(address("fe80::1234"), phone))
        assertTrue(LocalAdbAddress.belongsToPhone(address("127.0.0.1"), phone))
        assertTrue(LocalAdbAddress.belongsToPhone(address("::1"), phone))
        assertFalse(LocalAdbAddress.belongsToPhone(address("192.168.1.191"), phone))
        assertFalse(LocalAdbAddress.belongsToPhone(address("fe80::5678"), phone))
    }
    @Test fun rejectsUnresolvedWildcardAndMulticastAdvertisements() {
        assertFalse(LocalAdbAddress.belongsToPhone(null, phone))
        assertFalse(LocalAdbAddress.belongsToPhone(address("0.0.0.0"), phone))
        assertFalse(LocalAdbAddress.belongsToPhone(address("224.0.0.251"), phone))
        assertFalse(LocalAdbAddress.belongsToPhone(address("::"), phone))
    }
    @Test fun ignoresStaleAdvertisementsWhosePortIsNoLongerListening() {
        val listener = ServerSocket(0, 1, address("127.0.0.1"))
        val port = listener.localPort
        assertTrue(LocalAdbAddress.portIsListening(port))
        listener.close()
        assertFalse(LocalAdbAddress.portIsListening(port))
        assertFalse(LocalAdbAddress.portIsListening(0))
        assertFalse(LocalAdbAddress.portIsListening(65536))
    }
}
