package coredevices.ring.pluskey

/** A local key session; neither a Bluetooth ring nor a PC session is required. */
internal class PlusKeyGestures(
    private val holdMillis: Long = 300,
    private val maximumMillis: Long = 120_000,
) {
    enum class Action { Start, Submit, Cancel }
    private var down = false
    private var started = false
    private var timedOut = false
    private var pressedAt = 0L

    fun edge(pressed: Boolean, now: Long): List<Action> {
        val actions = advance(now).toMutableList()
        if (pressed == down) return actions
        down = pressed
        if (pressed) {
            pressedAt = now
            timedOut = false
        } else {
            if (started) actions += Action.Submit
            started = false
            timedOut = false
        }
        return actions
    }

    fun advance(now: Long): List<Action> {
        if (!down || timedOut) return emptyList()
        if (now - pressedAt >= maximumMillis) {
            timedOut = true
            val actions = if (started) listOf(Action.Submit) else emptyList()
            started = false
            return actions
        }
        if (!started && now - pressedAt >= holdMillis) {
            started = true
            return listOf(Action.Start)
        }
        return emptyList()
    }

    fun cancel(): List<Action> {
        val actions = if (started) listOf(Action.Cancel) else emptyList()
        down = false
        started = false
        timedOut = false
        return actions
    }
}

/** CPH2747 logs duplicate, action-less transitions; discard old/duplicate lines. */
internal class PlusKeyLogParser(private val startedAt: Long) {
    private var lastEdge = Long.MIN_VALUE
    private var held = false
    fun read(line: String, now: Long): Boolean? {
        val match = entry.matchEntire(line) ?: return null
        val timestamp = match.groupValues[1].toLong() * 1000 + match.groupValues[2].toLong()
        if (timestamp < startedAt || timestamp <= lastEdge) return null
        check(now - timestamp in -1000L..1000L) { "Key events delayed. Disable and re-enable with the key released." }
        if (lastEdge != Long.MIN_VALUE && timestamp - lastEdge <= 15) return null
        lastEdge = timestamp
        held = !held
        return held
    }
    companion object {
        private val entry = Regex("""\s*(\d+)\.(\d{3})\s+\d+\s+\d+\s+I\s+KEYLOG_OplusKeyEventUtil:\s+should not notify undefined keys in restrict listen mode\s*""")
    }
}
