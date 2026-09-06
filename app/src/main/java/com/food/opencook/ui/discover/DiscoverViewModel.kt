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

package com.food.opencook.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Keeps the browser's tile board. Adding and removing are plain edits to two device-local
 * lists (see [SettingsRepository.discoverHiddenSites]); the visible order is derived, never
 * stored, so a built-in site added in a later app version still appears.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** The household's recipe language decides which board is offered — the sites are only
     *  useful in a language you can cook in. Falls back to the device language, like the rest
     *  of the app's content localisation. */
    private val language: Flow<String> =
        settings.contentLanguage.map(settings::effectiveContentLanguage)

    val sites: StateFlow<List<DiscoverSite>> =
        combine(
            settings.discoverHiddenSites,
            settings.discoverCustomSites,
            language,
        ) { hidden, custom, lang ->
            DiscoverSites.visible(hidden, custom, lang)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DiscoverSites.defaultsFor(Locale.getDefault().language),
        )

    /** Removed built-in tiles, offered as suggestions in the add field so they are easy to get
     *  back — typing three letters beats remembering the address. */
    val removedDefaults: StateFlow<List<String>> =
        combine(settings.discoverHiddenSites, language) { hidden, lang ->
            DiscoverSites.defaultsFor(lang).map { it.url }.filter { it in hidden }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Put [url] on the board. A built-in address is simply un-hidden, so re-adding one never
     * leaves a second copy behind.
     */
    fun add(url: String) = viewModelScope.launch {
        settings.setDiscoverHiddenSites(settings.discoverHiddenSites.first() - url)
        if (!DiscoverSites.isDefault(url)) {
            val custom = settings.discoverCustomSites.first()
            if (url !in custom) settings.setDiscoverCustomSites(custom + url)
        }
    }

    /** Take [url] off the board: a built-in tile is remembered as hidden, a typed one is dropped. */
    fun remove(url: String) = viewModelScope.launch {
        settings.setDiscoverCustomSites(settings.discoverCustomSites.first() - url)
        if (DiscoverSites.isDefault(url)) {
            settings.setDiscoverHiddenSites(settings.discoverHiddenSites.first() + url)
        }
    }
}
