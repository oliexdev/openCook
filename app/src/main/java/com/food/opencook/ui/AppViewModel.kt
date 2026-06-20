package com.food.opencook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Whether the user has joined a household, which gates the whole app. */
sealed interface OnboardState {
    /** DataStore not read yet — show a splash to avoid flashing onboarding. */
    data object Loading : OnboardState
    data object NotOnboarded : OnboardState
    data object Onboarded : OnboardState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {

    /**
     * Drives the top-level branch in [OpenCookApp]. Reacting to the DataStore flows
     * makes the transition automatic and bidirectional: joining/creating a household
     * flips this to [OnboardState.Onboarded]; leaving it flips back to onboarding —
     * no imperative navigation needed.
     */
    val onboardState: StateFlow<OnboardState> =
        combine(settings.householdId, settings.householdCode) { id, code ->
            if (!id.isNullOrBlank() && !code.isNullOrBlank()) OnboardState.Onboarded
            else OnboardState.NotOnboarded
        }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardState.Loading)
}
