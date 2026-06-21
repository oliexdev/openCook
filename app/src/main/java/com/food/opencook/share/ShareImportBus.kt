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

package com.food.opencook.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-slot hand-off for a recipe URL shared into the app (Android "Share" sheet).
 *
 * [MainActivity] writes the URL the moment the share intent arrives; the Compose layer
 * (only present once a household is joined) reads and clears it. Being a process-wide
 * singleton, it also **buffers** a URL shared before onboarding — it's picked up as soon
 * as the main UI appears.
 */
@Singleton
class ShareImportBus @Inject constructor() {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    fun submit(url: String) { _url.value = url }
    fun clear() { _url.value = null }
}
