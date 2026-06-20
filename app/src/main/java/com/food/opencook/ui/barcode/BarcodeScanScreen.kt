package com.food.opencook.ui.barcode

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import java.util.concurrent.Executors

/**
 * Camera screen that decodes a product barcode (ZXing analyzer on CameraX), looks the
 * product up, and shows a confirm dialog with an editable, suggestion-assisted name
 * before adding it to the shopping list or pantry. Manual typing is always possible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarcodeScanScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: BarcodeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Lock orientation to portrait while the scanner is on screen. Frame-rotation
    // and viewfinder geometry are calibrated for upright holding; letting the device
    // flip to landscape would re-flow the cutout and reorient the user's hand mid-aim.
    // Restore the previous orientation on dispose so leaving the screen doesn't trap
    // the rest of the app.
    val activity = context as? Activity
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

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
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_permission_rationale), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onBack) { Text(stringResource(R.string.processing_cancel)) }
        }
        return
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { BarcodeAnalyzer { ean -> viewModel.onScanned(ean) } }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setImageAnalysisAnalyzer(analysisExecutor, analyzer)
            bindToLifecycle(lifecycleOwner)
        }
    }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }
    // Re-arm the analyzer whenever we return to scanning (initial, and after rescan/dismiss):
    // the analyzer fires once then ignores frames, so without this a rescan never detects again.
    LaunchedEffect(state.barcode) { if (state.barcode == null) analyzer.rearm() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
        // Scanner viewfinder: dim everything but a centred, rounded window, with a vertical
        // line sweeping left<->right across it. Purely visual — the whole frame is still
        // analysed. Sized from BOTH dimensions (offscreen-composited for a truly transparent
        // cut-out) so it stays generous and never overflows in portrait or landscape.
        val accent = MaterialTheme.colorScheme.primary
        val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
            label = "scanLine",
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val aspect = 1.6f // barcodes are wider than tall when held upright
            val fw = minOf(size.width * 0.86f, size.height * 0.6f * aspect)
            val fh = fw / aspect
            val topLeft = Offset((size.width - fw) / 2f, (size.height - fh) / 2f)
            val window = Size(fw, fh)
            val radius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.5f))
            drawRoundRect(Color.Black, topLeft, window, radius, blendMode = BlendMode.Clear)
            drawRoundRect(Color.White.copy(alpha = 0.9f), topLeft, window, radius, style = Stroke(width = 3.dp.toPx()))
            // Vertical line sweeping left<->right (more travel since the window is wide).
            val inset = fh * 0.08f
            val lineX = topLeft.x + fw * sweep
            drawLine(
                color = accent,
                start = Offset(lineX, topLeft.y + inset),
                end = Offset(lineX, topLeft.y + fh - inset),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Text(
            stringResource(R.string.barcode_hint),
            color = Color.White,
            // Edge-to-edge: keep the hint below the status bar / camera cutout.
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(24.dp),
        )
        Button(
            onClick = onBack,
            // Edge-to-edge: sit above the system navigation bar, not under it.
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
        ) {
            Text(stringResource(R.string.processing_cancel))
        }
    }

    // A barcode was captured → confirm (and edit) the name before adding.
    if (state.barcode != null) {
        val suggestions = viewModel.suggestions(state.name)
        AlertDialog(
            onDismissRequest = { viewModel.rescan() },
            title = {
                Text(
                    stringResource(if (state.found) R.string.barcode_found else R.string.barcode_not_found),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.looking) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        label = { Text(stringResource(R.string.barcode_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.take(6).forEach { s ->
                                AssistChip(onClick = { viewModel.setName(s) }, label = { Text(s) })
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.barcode_code_label, state.barcode ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirm(onDone) }, enabled = state.name.isNotBlank()) {
                    Text(stringResource(R.string.barcode_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rescan() }) { Text(stringResource(R.string.barcode_rescan)) }
            },
        )
    }
}
