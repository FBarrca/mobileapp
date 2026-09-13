package coredevices.ring.pluskey.setup

import org.junit.Assert.*
import org.junit.Test

class PlusKeySetupInputTest {
    @Test fun portsRejectCommandsAndOutOfRangeValues() {
        listOf("", "0", "65536", "-1", "1;reboot", "192.168.1.2:1234", " 1234").forEach {
            assertNull(PlusKeySetupInput.port(it))
        }
        assertEquals(1, PlusKeySetupInput.port("1"))
        assertEquals(65535, PlusKeySetupInput.port("65535"))
    }
    @Test fun pairingCodesPreserveLeadingZeroAndRequireSixAsciiDigits() {
        assertTrue(PlusKeySetupInput.codeValid("012345"))
        listOf("12345", "1234567", "12 456", "abcdef", "１２３４５６").forEach {
            assertFalse(PlusKeySetupInput.codeValid(it))
        }
    }
    @Test fun grantTargetsTheAppAndTheCurrentAndroidUser() {
        assertEquals("pm grant --user 10 coredevices.coreapp.pluskey android.permission.READ_LOGS", PlusKeySetupInput.grantCommand(10, "coredevices.coreapp.pluskey"))
    }
    @Test fun commandRejectsPackageShellSyntax() {
        for (name in listOf("", "coredevices.coreapp;reboot", "x y", "a.b\nc.d")) {
            try { PlusKeySetupInput.grantCommand(0, name); fail("Invalid package accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test(expected = IllegalArgumentException::class) fun invalidUserIsRejected() {
        PlusKeySetupInput.grantCommand(-1, "coredevices.coreapp.pluskey")
    }
}
