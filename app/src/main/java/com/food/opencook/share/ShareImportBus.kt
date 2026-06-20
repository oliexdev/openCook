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
