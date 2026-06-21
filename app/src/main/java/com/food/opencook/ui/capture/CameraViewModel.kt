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

package com.food.opencook.ui.capture

import androidx.lifecycle.ViewModel
import com.food.opencook.data.image.ImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

/** Minimal VM for the camera screen: just provides a destination file for a capture. */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val imageStore: ImageStore,
) : ViewModel() {
    fun newCaptureFile(): File = imageStore.newCaptureFile()
}
