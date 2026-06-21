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

package com.food.opencook.ui.scan

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.recipeimport.ImportState
import com.food.opencook.ui.recipeimport.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onCreateManually: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val serverConfigured by viewModel.serverConfigured.collectAsStateWithLifecycle()

    // Ask for notification permission once (API 33+) so the background "recipe
    // ready" notification can be shown if the user leaves the app mid-scan.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — notifications are a non-critical enhancement */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Non-blocking: starting a scan keeps the user here (progress shows in the
    // status strip), so they can scan several pages in a row.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.startScanFromUri(uri) {}
    }

    // Import recipes from a JSON file (schema.org/Recipe). Opens the system file picker.
    val importViewModel: ImportViewModel = hiltViewModel()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importViewModel.importFromUri(uri, limit = null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.scan_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!serverConfigured) {
            Text(
                stringResource(R.string.scan_no_server),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onNavigateToSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_go_to_settings))
            }
        } else {
            Button(onClick = onNavigateToCamera, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Text(
                    stringResource(R.string.scan_take_photo),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Text(
                    stringResource(R.string.scan_pick_gallery),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        // Create a recipe by hand — opens the editor with a blank draft. Always available,
        // independent of server reachability since no AI is involved.
        OutlinedButton(
            onClick = onCreateManually,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.EditNote, contentDescription = null)
            Text(stringResource(R.string.scan_manual), modifier = Modifier.padding(start = 8.dp))
        }

        // Import from a JSON file or a .zip bundle (recipes.json + images/).
        OutlinedButton(
            onClick = { jsonLauncher.launch(arrayOf("application/json", "application/zip", "text/plain", "*/*")) },
            enabled = importState !is ImportState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.FileOpen, contentDescription = null)
            Text(stringResource(R.string.import_from_file), modifier = Modifier.padding(start = 8.dp))
        }

        when (val s = importState) {
            is ImportState.Running -> {
                Text(stringResource(R.string.import_running, s.done, s.total))
                if (s.total > 0) LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
            }
            is ImportState.Done -> Text(
                stringResource(R.string.import_done, s.imported, s.skipped),
                color = MaterialTheme.colorScheme.primary,
            )
            is ImportState.Error -> Text(
                stringResource(R.string.import_error, s.message),
                color = MaterialTheme.colorScheme.error,
            )
            ImportState.Idle -> Unit
        }
    }
    }
}
