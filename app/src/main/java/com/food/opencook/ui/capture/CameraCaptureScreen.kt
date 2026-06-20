package com.food.opencook.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.food.opencook.R

/**
 * Full-screen CameraX preview with a single capture button. On capture the photo
 * is written to the cache and its path reported via [onImageCaptured]; the caller
 * decides what to do with it (start a scan, or attach it to a recipe in review).
 * If the camera permission is denied the user can go back and use the gallery
 * picker instead (capture is never the only path).
 */
@Composable
fun CameraCaptureScreen(
    onImageCaptured: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        LaunchedRequest { permissionLauncher.launch(Manifest.permission.CAMERA) }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.camera_permission_rationale),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onBack) { Text(stringResource(R.string.processing_cancel)) }
        }
        return
    }

    val controller = remember {
        LifecycleCameraController(context).apply { bindToLifecycle(lifecycleOwner) }
    }
    var capturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = {
                if (capturing) return@Button
                capturing = true
                val file = viewModel.newCaptureFile()
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                controller.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                            onImageCaptured(file.absolutePath)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            capturing = false
                        }
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
        ) {
            Text(stringResource(R.string.camera_capture))
        }
    }
}

@Composable
private fun LaunchedRequest(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
