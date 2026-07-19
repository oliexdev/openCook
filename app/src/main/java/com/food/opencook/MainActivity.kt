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

package com.food.opencook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.share.ShareImportBus
import com.food.opencook.ui.OpenCookApp
import com.food.opencook.ui.ThemeViewModel
import com.food.opencook.ui.theme.OpenCookTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shareImportBus: ShareImportBus

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val dynamicColor by themeViewModel.dynamicColor.collectAsStateWithLifecycle()
            val textScale by themeViewModel.textScale.collectAsStateWithLifecycle()
            OpenCookTheme(dynamicColor = dynamicColor, textScale = textScale) {
                OpenCookApp()
            }
        }
    }

    // singleTop: a fresh share while we're already running arrives here, not via onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /** Pull the recipe URL out of a "share text" intent and hand it to the import flow. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        // EXTRA_TEXT may be "Title https://…" — take the first http(s) URL.
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        shareImportBus.submit(url)
    }
}
