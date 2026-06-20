package com.food.opencook.sync

import kotlin.math.max

/** Raised when the logical counter overflows — indicates excessive clock drift. */
class ClockDriftException(message: String) : RuntimeException(message)

/**
 * A node's mutable Hybrid Logical Clock. [send] stamps a local event; [recv]
 * advances the clock past a received timestamp so causality is preserved even
 * when devices' wall clocks disagree. Not thread-safe; callers serialise access.
 *
 * Standard HLC per Kulkarni et al. / "CRDTs for Mortals" (ActualBudget).
 */
class HlcClock(
    val node: String,
    initial: Hlc? = null,
) {
    var last: Hlc = initial ?: Hlc(0L, 0, node)
        private set

    /** Stamp a local mutation at wall-clock time [now] (millis). */
    fun send(now: Long): Hlc {
        val millis = max(last.millis, now)
        val counter = if (millis == last.millis) last.counter + 1 else 0
        guard(counter)
        return Hlc(millis, counter, node).also { last = it }
    }

    /** Advance past a [remote] timestamp seen at wall-clock time [now] (millis). */
    fun recv(remote: Hlc, now: Long): Hlc {
        val millis = max(max(last.millis, remote.millis), now)
        val counter = when {
            millis == last.millis && millis == remote.millis -> max(last.counter, remote.counter) + 1
            millis == last.millis -> last.counter + 1
            millis == remote.millis -> remote.counter + 1
            else -> 0
        }
        guard(counter)
        return Hlc(millis, counter, node).also { last = it }
    }

    private fun guard(counter: Int) {
        if (counter > Hlc.MAX_COUNTER) {
            throw ClockDriftException("HLC counter overflow ($counter) — clock drift too large")
        }
    }
}
