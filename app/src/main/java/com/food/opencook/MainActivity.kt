package com.food.opencook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
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
            OpenCookTheme(dynamicColor = dynamicColor) {
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
