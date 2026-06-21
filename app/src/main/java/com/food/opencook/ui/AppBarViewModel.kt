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

package com.food.opencook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.sync.ImportInboxSyncer
import com.food.opencook.sync.SyncManager
import com.food.opencook.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the shared top-bar sync indicator. Reads the singleton [SyncManager]'s
 * status (so every screen observes the same source) and triggers manual syncs.
 */
@HiltViewModel
class AppBarViewModel @Inject constructor(
    private val syncManager: SyncManager,
) : ViewModel() {

    val status: StateFlow<SyncStatus> =
        syncManager.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.NotConfigured)

    /** Browser-import drain outcome (imported + skipped duplicates) — the shell shows a snackbar. */
    val importedEvents: SharedFlow<ImportInboxSyncer.Result> = syncManager.importedEvents

    fun sync() = syncManager.syncNow()
}
