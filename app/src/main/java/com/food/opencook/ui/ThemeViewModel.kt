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
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.data.settings.TextScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the theme settings the Activity needs at setContent (Material You toggle, text size). */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val dynamicColor: StateFlow<Boolean> =
        settings.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val textScale: StateFlow<TextScale> =
        settings.textScale.stateIn(viewModelScope, SharingStarted.Eagerly, TextScale.NORMAL)
}
