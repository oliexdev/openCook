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
