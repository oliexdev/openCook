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
package com.food.opencook.data.discovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 17's local-network permission (`ACCESS_LOCAL_NETWORK`, "Nearby devices" group).
 *
 * Targeting API 37, *every* LAN packet needs it — outgoing/incoming TCP, UDP, mDNS/NSD —
 * and without it connections don't fail loudly, they just time out. Server, discovery
 * and peer sync are all LAN, so everything that talks to the household checks [isGranted]
 * first and skips instead of burning timeouts; the UI asks for it in onboarding and,
 * for households set up before the update, once at start (see SyncStatus.LocalNetworkDenied).
 * Below Android 17 the permission doesn't exist and this always reads as granted.
 */
@Singleton
class LocalNetworkAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DefaultLifecycleObserver {

    private val _granted = MutableStateFlow(check())

    /** Live state; re-read on every app start/resume, since granting it in the system
     *  settings doesn't restart the process (revoking does). */
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    /** One process-wide automatic prompt at most — the top bar offers it again on tap. */
    var autoPrompted: Boolean = false

    /** Called once from the Application (main thread). */
    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = refresh()

    /** Fresh answer for one-shot callers (workers, a sync round); also updates [granted]. */
    fun isGranted(): Boolean = check().also { _granted.value = it }

    /** Re-read after the permission dialog returns. */
    fun refresh() {
        _granted.value = check()
    }

    private fun check(): Boolean =
        !REQUIRED || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Android 17+: below that the permission doesn't exist and LAN is open. */
        val REQUIRED: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        const val PERMISSION: String = Manifest.permission.ACCESS_LOCAL_NETWORK
    }
}
