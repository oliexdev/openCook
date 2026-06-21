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

package com.food.opencook.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(
    onSaved: () -> Unit,
    onClose: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    capturedImagePath: String? = null,
    onCapturedConsumed: () -> Unit = {},
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val list = recipes

    if (list == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nameRequiredMsg = stringResource(R.string.wizard_name_required)
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    val pagerState = rememberPagerState(pageCount = { list.size.coerceAtLeast(1) })
    val stepByPage by viewModel.stepByPage.collectAsStateWithLifecycle()

    // A photo captured via the review camera comes back here; attach it to the
    // recipe currently being viewed, then clear the one-shot result.
    LaunchedEffect(capturedImagePath) {
        capturedImagePath?.let {
            viewModel.attachLocalImage(pagerState.currentPage, it)
            onCapturedConsumed()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                }
                Text(
                    if (list.isEmpty()) stringResource(R.string.review_title)
                    else stringResource(R.string.review_recipe_index, pagerState.currentPage + 1, list.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Quick-save shortcut for power users / edits: always available, doesn't
                // require walking through the wizard's last step.
                TextButton(onClick = { viewModel.save(onSaved) }, enabled = list.isNotEmpty()) {
                    Text(stringResource(R.string.review_save))
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page < list.size) {
                    val recipe = list[page]
                    val currentStep = stepByPage[page] ?: WizardStep.BASICS
                    val steps = WizardStep.values()
                    val canAdvance = when (currentStep) {
                        WizardStep.BASICS -> recipe.name.isNotBlank()
                        else -> true
                    }
                    WizardScaffold(
                        step = currentStep,
                        isFirst = currentStep.ordinal == 0,
                        isLast = currentStep.ordinal == steps.lastIndex,
                        canAdvance = canAdvance,
                        saveLabel = stringResource(R.string.review_save),
                        onBack = {
                            val idx = currentStep.ordinal
                            if (idx > 0) viewModel.setStep(page, steps[idx - 1])
                        },
                        onNext = {
                            val idx = currentStep.ordinal
                            if (idx < steps.lastIndex) viewModel.setStep(page, steps[idx + 1])
                            else viewModel.save(onSaved)
                        },
                        onSave = { viewModel.save(onSaved) },
                    ) {
                        when (currentStep) {
                            WizardStep.BASICS -> BasicsStep(recipe, viewModel, page, onTakePhoto)
                            WizardStep.INGREDIENTS -> IngredientsStep(recipe, viewModel, page)
                            WizardStep.STEPS -> StepsStep(recipe, viewModel, page)
                            WizardStep.DETAILS -> DetailsStep(recipe, viewModel, page)
                            WizardStep.SUMMARY -> SummaryStep(recipe, viewModel)
                        }
                    }
                    // Soft warning when arriving past Basics without a name (defensive — the
                    // Next button is gated, but the user might still land here via pager swipe).
                    if (currentStep != WizardStep.BASICS && recipe.name.isBlank()) {
                        LaunchedEffect(currentStep) {
                            scope.launch { snackbarHostState.showSnackbar(nameRequiredMsg) }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
