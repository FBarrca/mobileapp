package coredevices.ring.pluskey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import coredevices.ring.pluskey.PlusKeyGestures.Action

class PlusKeyGesturesTest {
    @Test fun tapsDoNotRecord() {
        val key = PlusKeyGestures()
        assertEquals(emptyList(), key.edge(true, 0))
        assertEquals(emptyList(), key.edge(false, 299))
        assertEquals(emptyList(), key.advance(1000))
    }
    @Test fun holdSubmitsExactlyOnce() {
        val key = PlusKeyGestures()
        key.edge(true, 0)
        assertEquals(listOf(Action.Start), key.advance(300))
        assertEquals(emptyList(), key.edge(true, 500))
        assertEquals(listOf(Action.Submit), key.edge(false, 800))
        assertEquals(emptyList(), key.edge(false, 900))
    }
    @Test fun timeoutRequiresRelease() {
        val key = PlusKeyGestures()
        key.edge(true, 0)
        key.advance(300)
        assertEquals(listOf(Action.Submit), key.advance(120_000))
        assertEquals(emptyList(), key.advance(240_000))
        assertEquals(emptyList(), key.edge(false, 240_001))
        key.edge(true, 250_000)
        assertEquals(listOf(Action.Start), key.advance(250_300))
    }
    @Test fun failureCancelsWithoutSubmitting() {
        val key = PlusKeyGestures()
        key.edge(true, 0)
        key.advance(300)
        assertEquals(listOf(Action.Cancel), key.cancel())
        assertEquals(emptyList(), key.edge(false, 1000))
    }
    private fun line(ms: Long) = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')} 3934 4920 I KEYLOG_OplusKeyEventUtil: should not notify undefined keys in restrict listen mode"
    @Test fun duplicatesAndOldEventsAreIgnored() {
        val parser = PlusKeyLogParser(100_000)
        assertNull(parser.read(line(99_000), 100_000))
        assertEquals(true, parser.read(line(100_010), 100_010))
        assertNull(parser.read(line(100_012), 100_012))
        assertEquals(false, parser.read(line(101_000), 101_000))
    }
    @Test fun delayedInputFailsClosed() {
        assertFailsWith<IllegalStateException> { PlusKeyLogParser(100_000).read(line(100_010), 103_000) }
    }
}
