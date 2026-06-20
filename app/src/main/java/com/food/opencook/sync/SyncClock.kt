package com.food.opencook.sync

import com.food.opencook.data.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device-wide Hybrid Logical Clock, persisted so it stays monotonic across
 * process restarts. Lazily initialised from the stored node id + last timestamp;
 * every stamp is persisted. Serialised by a mutex.
 */
@Singleton
class SyncClock @Inject constructor(
    private val settings: SettingsRepository,
) : Stamper {
    private val mutex = Mutex()
    private var clock: HlcClock? = null

    /** Stamp a local mutation. */
    override suspend fun stamp(): Hlc = mutex.withLock {
        val c = ensureClock()
        c.send(System.currentTimeMillis()).also { settings.setLastHlc(it.pack()) }
    }

    /** Advance past a received remote timestamp (used when applying remote messages). */
    suspend fun observe(remote: Hlc): Hlc = mutex.withLock {
        val c = ensureClock()
        c.recv(remote, System.currentTimeMillis()).also { settings.setLastHlc(it.pack()) }
    }

    suspend fun node(): String = mutex.withLock { ensureClock().node }

    private suspend fun ensureClock(): HlcClock {
        clock?.let { return it }
        val node = settings.ensureNodeId()
        val last = settings.lastHlc()?.let(Hlc::parse)
        return HlcClock(node, last).also { clock = it }
    }
}
