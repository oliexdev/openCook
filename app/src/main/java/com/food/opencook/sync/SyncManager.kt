/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.sync

import com.food.opencook.data.discovery.LanMonitor
import com.food.opencook.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Lets the rest of the app ask for a sync without depending on the engine. */
interface SyncTrigger {
    fun requestSync()
}

/**
 * Runs sync automatically whenever the server is reachable: once on start, after
 * every local change (debounced), and periodically while the app is in the
 * foreground. All runs are best-effort — if the server is down, sync simply
 * retries on the next trigger; nothing is lost (it's all in the local log).
 *
 * While the phone is off the home network (mobile data, Wi-Fi off) every run is
 * skipped outright: server and peers are LAN addresses, so an attempt could only
 * time out. The status then says exactly that instead of blaming the server.
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncManager @Inject constructor(
    private val syncEngine: SyncEngine,
    // Provider breaks the DI cycle: ImportInboxSyncer → RecipeRepository → MessageRecorder
    // → SyncTrigger (this). Resolved lazily on first drain, not at construction.
    private val importInboxSyncer: Provider<ImportInboxSyncer>,
    private val settings: SettingsRepository,
    private val lanMonitor: LanMonitor,
) : SyncTrigger {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val triggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mutex = Mutex() // one sync at a time, so the status reflects a single run
    private var lastSuccessEpochMs: Long? = null

    /** Assume reachable until the monitor reports otherwise, so nothing is blocked on a guess. */
    @Volatile
    private var onHomeNetwork = true

    /** The first round of a process is shown even when it moves nothing: until it has
     *  answered, the icon has nothing truthful to say about the household's state. */
    private var hasCompletedRound = false

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.NotConfigured)
    /** Observable sync state for the shared top-bar indicator. */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _serverReachable = MutableStateFlow<Boolean?>(null)
    /**
     * Whether the last completed round actually reached the *server* (not just a peer)
     * — `null` until the first round finishes. Screens that need the server itself (the
     * AI scan) use this to explain why their action is queued rather than running.
     */
    val serverReachable: StateFlow<Boolean?> = _serverReachable.asStateFlow()

    private val _importedEvents = MutableSharedFlow<ImportInboxSyncer.Result>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Emits the outcome of draining the browser-import inbox (imported + skipped duplicates). */
    val importedEvents: SharedFlow<ImportInboxSyncer.Result> = _importedEvents.asSharedFlow()

    /** Started once from the Application. */
    fun start() {
        // Snapshot first: the flow's first emission may land after the initial run below,
        // which on mobile data would be exactly the pointless timeout round we avoid here.
        onHomeNetwork = lanMonitor.isOnHomeNetwork()
        scope.launch {
            lanMonitor.onHomeNetwork().collect { available ->
                val resumed = available && !onHomeNetwork
                onHomeNetwork = available
                // Back on Wi-Fi: catch up immediately instead of waiting out the tick.
                if (resumed) runSync()
                if (!available) markPaused()
            }
        }
        scope.launch { triggers.debounce(DEBOUNCE_MS).collect { runSync() } }
        scope.launch {
            while (isActive) {
                runSync() // initial run, then on each interval
                delay(PERIODIC_MS)
            }
        }
    }

    override fun requestSync() {
        triggers.tryEmit(Unit)
    }

    /** Manual sync from the top-bar icon — runs immediately (no debounce). */
    fun syncNow() {
        scope.launch { runSync(manual = true) }
    }

    /** Show the paused state right away when Wi-Fi drops, without waiting for a run. */
    private suspend fun markPaused() {
        if (settings.householdCodeOnce().isNullOrBlank()) return
        _serverReachable.value = false
        _status.value = SyncStatus.OffHomeNetwork
    }

    /**
     * @param manual the user tapped the sync icon. Automatic rounds stay invisible
     *   unless they actually move data — one every 30 s, and a spinner flashing that
     *   often only makes people wait for something that never comes. A tap, though,
     *   gets visible feedback even when there was nothing to do.
     */
    private suspend fun runSync(manual: Boolean = false) = mutex.withLock {
        if (settings.householdCodeOnce().isNullOrBlank()) {
            _status.value = SyncStatus.NotConfigured
            return@withLock
        }
        if (!onHomeNetwork) {
            // Nothing to try: both targets are LAN addresses. Tapping the icon lands
            // here too — the user gets the explanation, not a 20-second timeout.
            _serverReachable.value = false
            _status.value = SyncStatus.OffHomeNetwork
            return@withLock
        }
        val startedAt = System.currentTimeMillis()
        val visible = manual || !hasCompletedRound
        if (visible) _status.value = SyncStatus.Syncing()
        val result = runCatching {
            syncEngine.sync(
                onProgress = { p ->
                    _status.value = SyncStatus.Syncing(
                        phase = p.phase,
                        count = p.count,
                        total = p.total,
                        fraction = p.fraction,
                    )
                },
                // Real work found — now the icon may start moving.
                onTransfer = {
                    if (_status.value !is SyncStatus.Syncing) _status.value = SyncStatus.Syncing()
                },
            )
        }.getOrElse { SyncEngine.Result.Failed(it.message ?: "error") }
        hasCompletedRound = true
        // A tap answered in 40 ms reads as "nothing happened", so hold the animation
        // just long enough to be seen as an answer.
        if (manual) {
            val shown = System.currentTimeMillis() - startedAt
            if (shown < MIN_MANUAL_FEEDBACK_MS) delay(MIN_MANUAL_FEEDBACK_MS - shown)
        }
        // The engine always tries the server first, so "answered by a peer" means the
        // server did not answer. A 404 is an answer — the server is up, it just lost us.
        _serverReachable.value = when (result) {
            is SyncEngine.Result.Ok -> result.via == SyncVia.Server
            SyncEngine.Result.UnknownHousehold -> true
            is SyncEngine.Result.Failed -> false
            SyncEngine.Result.NoHousehold -> null
        }
        _status.value = when (result) {
            is SyncEngine.Result.Ok -> {
                lastSuccessEpochMs = System.currentTimeMillis()
                // Server is reachable — drain any recipes the browser extension pushed.
                // Peers have no import inbox, so only drain after a *server* sync.
                // Best-effort: a failure here must not flip the sync status to Failed.
                if (result.via == SyncVia.Server) {
                    val drained = runCatching { importInboxSyncer.get().drain() }
                        .getOrDefault(ImportInboxSyncer.Result(0, 0))
                    if (drained.any) _importedEvents.tryEmit(drained)
                }
                SyncStatus.Idle(lastSuccessEpochMs, result.via)
            }
            SyncEngine.Result.NoHousehold -> SyncStatus.NotConfigured
            SyncEngine.Result.UnknownHousehold -> SyncStatus.HouseholdMissing
            is SyncEngine.Result.Failed -> SyncStatus.Failed(result.message)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
        const val PERIODIC_MS = 30_000L
        const val MIN_MANUAL_FEEDBACK_MS = 600L
    }
}
