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

/** Which counterpart a successful sync ran against. */
sealed interface SyncVia {
    data object Server : SyncVia

    /** A peer phone, identified by its advertised mDNS service name. */
    data class Peer(val name: String) : SyncVia
}

/** Observable state of background sync, shown in the shared top bar. */
sealed interface SyncStatus {
    /** No household/server configured yet — nothing to sync. */
    data object NotConfigured : SyncStatus

    /** Idle after a (possibly past) successful sync; [lastSuccessEpochMs] null if never.
     *  [via] tells the UI whether the server or a peer phone answered (null = unknown/never). */
    data class Idle(val lastSuccessEpochMs: Long?, val via: SyncVia? = null) : SyncStatus

    /** Which step of the sync is reporting progress right now. */
    enum class Phase { APPLY, IMAGES }

    /**
     * A sync is in progress. [phase] tells the UI which message to render:
     *  - [Phase.APPLY] = projecting the message log into Room. [count] is the
     *    number of recipes materialised so far, [fraction] the overall apply
     *    progress (0..1).
     *  - [Phase.IMAGES] = downloading recipe photos that arrived via sync but
     *    aren't on disk yet. [count] is files downloaded so far, [total] the
     *    total this round, [fraction] = count/total.
     * Small syncs leave the numeric fields null so the icon just spins (no flicker).
     */
    data class Syncing(
        val phase: Phase = Phase.APPLY,
        val count: Int? = null,
        val total: Int? = null,
        val fraction: Float? = null,
    ) : SyncStatus

    /** The last sync attempt failed (server down / offline). */
    data class Failed(val reason: String) : SyncStatus

    /**
     * The server rejected our household credential (HTTP 404): the household no
     * longer exists there — typically the server DB was reset/reinstalled. Unlike
     * [Failed] this won't fix itself by retrying; the user must re-join or create a
     * household. Surfaced prominently (not the calm "offline" state).
     */
    data object HouseholdMissing : SyncStatus
}
