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

import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.data.remote.dto.HouseholdSettings
import com.food.opencook.data.remote.dto.MerkleDto
import com.food.opencook.data.remote.dto.SyncMessageDto
import com.food.opencook.data.remote.dto.SyncRequestDto
import com.food.opencook.data.remote.dto.SyncResponseDto
import com.food.opencook.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The responder half of the sync exchange — a faithful Kotlin port of the server's
 * POST /sync handler (server/app/api/sync.py), so a foregrounded phone can answer
 * another phone exactly like the Python server would:
 *
 *  1. ingest the requester's pushed messages (idempotent, shared [MessageApplier]),
 *  2. build the Merkle trie over the now-updated local log,
 *  3. diff against the requester's trie and return only the messages from the
 *     earliest divergent minute on (or none when the tries already match),
 *  4. piggyback this device's household meta so members converge on name/settings.
 *
 * No caching of the trie: phone logs are small compared to a long-lived server DB,
 * and a foreground exchange every 30 s doesn't warrant the invalidation complexity.
 */
@Singleton
class SyncResponder @Inject constructor(
    private val applier: MessageApplier,
    private val messageDao: MessageDao,
    private val settings: SettingsRepository,
) {
    suspend fun respond(request: SyncRequestDto): SyncResponseDto {
        applier.apply(request.messages)

        val local = messageDao.all()
        val trie = MerkleTrie.build(local.map { it.timestamp })
        val missing = missingSince(local, trie, request.merkle)

        return SyncResponseDto(
            messages = missing.map { SyncMessageDto(it.timestamp, it.dataset, it.rowId, it.column, it.value) },
            merkle = trie.toDto(),
            householdName = settings.householdNameOnce(),
            householdSettings = HouseholdSettings(
                householdSize = settings.householdSizeOnce(),
                contentLanguage = settings.contentLanguageOnce(),
            ),
            householdHlc = settings.householdMetaHlcOnce(),
            householdPin = settings.householdPinOnce(),
        )
    }

    companion object {
        /**
         * The pure selection rule of the exchange (kept static so it's unit-testable
         * without Room): everything from the earliest minute at which the two logs
         * diverge, or nothing when the tries already match. Same `millis >= cursor`
         * semantics as server/app/api/sync.py.
         */
        fun missingSince(
            local: List<MessageEntity>,
            localTrie: Merkle,
            requestMerkle: MerkleDto,
        ): List<MessageEntity> {
            val cursor = MerkleTrie.diff(localTrie, requestMerkle.toMerkle()) ?: return emptyList()
            return local.filter { Hlc.parse(it.timestamp).millis >= cursor }
        }
    }
}
