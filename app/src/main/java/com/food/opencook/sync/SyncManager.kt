package com.food.opencook.sync

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
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncManager @Inject constructor(
    private val syncEngine: SyncEngine,
    // Provider breaks the DI cycle: ImportInboxSyncer → RecipeRepository → MessageRecorder
    // → SyncTrigger (this). Resolved lazily on first drain, not at construction.
    private val importInboxSyncer: Provider<ImportInboxSyncer>,
    private val settings: SettingsRepository,
) : SyncTrigger {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val triggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mutex = Mutex() // one sync at a time, so the status reflects a single run
    private var lastSuccessEpochMs: Long? = null

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.NotConfigured)
    /** Observable sync state for the shared top-bar indicator. */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _importedEvents = MutableSharedFlow<ImportInboxSyncer.Result>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Emits the outcome of draining the browser-import inbox (imported + skipped duplicates). */
    val importedEvents: SharedFlow<ImportInboxSyncer.Result> = _importedEvents.asSharedFlow()

    /** Started once from the Application. */
    fun start() {
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
        scope.launch { runSync() }
    }

    private suspend fun runSync() = mutex.withLock {
        if (settings.householdCodeOnce().isNullOrBlank()) {
            _status.value = SyncStatus.NotConfigured
            return@withLock
        }
        _status.value = SyncStatus.Syncing()
        val result = runCatching {
            syncEngine.sync { p ->
                _status.value = SyncStatus.Syncing(
                    phase = p.phase,
                    count = p.count,
                    total = p.total,
                    fraction = p.fraction,
                )
            }
        }.getOrElse { SyncEngine.Result.Failed(it.message ?: "Fehler") }
        _status.value = when (result) {
            is SyncEngine.Result.Ok -> {
                lastSuccessEpochMs = System.currentTimeMillis()
                // Server is reachable — drain any recipes the browser extension pushed.
                // Best-effort: a failure here must not flip the sync status to Failed.
                val result = runCatching { importInboxSyncer.get().drain() }
                    .getOrDefault(ImportInboxSyncer.Result(0, 0))
                if (result.any) _importedEvents.tryEmit(result)
                SyncStatus.Idle(lastSuccessEpochMs)
            }
            SyncEngine.Result.NoHousehold -> SyncStatus.NotConfigured
            SyncEngine.Result.UnknownHousehold -> SyncStatus.HouseholdMissing
            is SyncEngine.Result.Failed -> SyncStatus.Failed(result.message)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
        const val PERIODIC_MS = 30_000L
    }
}
