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

package com.food.opencook.ui.discover

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.BuildConfig
import com.food.opencook.R
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.recipeimport.ImportViewModel
import com.food.opencook.ui.recipeimport.ShareImportState
import com.food.opencook.ui.theme.Spacing
import java.net.URI
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** How often (and how patiently) we look for a recipe after a page settles. Sites that build
 *  their JSON-LD in JavaScript need a moment; three tries cover them without polling forever. */
private const val RECIPE_CHECKS = 3
private const val RECIPE_CHECK_DELAY_MS = 1200L

/** The "Version/4.0" an Android WebView sends and Chrome does not. */
private val WEBVIEW_VERSION_TOKEN = Regex("""Version/\d+(\.\d+)*\s*""")

/**
 * The in-app browser. The user browses a recipe site exactly as the site intends — its own
 * listing, its own layout — and the button in the bar imports the recipe that is currently open
 * through the same path as a shared link.
 *
 * openCook reads nothing on its own here: no listing pages, no prefetching, no caching of other
 * people's content. The only thing it ever looks at is the structured data of the page the user
 * has open, and only when they ask for it.
 *
 * Cookies survive the visit, so a site does not ask for consent again every single time; they
 * are cleared on demand from Settings. Nothing of the pages themselves is kept: the cache is off
 * and wiped on the way out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImportScreen(
    startUrl: String,
    importViewModel: ImportViewModel,
    onBack: () -> Unit,
) {
    // The system WebView can be absent or switched off — it is a separate, updatable package, and
    // de-Googled ROMs are exactly where that happens. Constructing one then throws, so the screen
    // says what is missing instead of taking the app down with it. Every other way of adding a
    // recipe keeps working.
    val available = remember { runCatching { WebView.getCurrentWebViewPackage() != null }.getOrDefault(false) }
    if (!available) {
        WebViewUnavailable(onBack)
        return
    }
    Browser(startUrl, importViewModel, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewUnavailable(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_title)) },
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
        EmptyState(
            icon = Icons.Outlined.Public,
            title = stringResource(R.string.discover_no_webview_title),
            message = stringResource(R.string.discover_no_webview_body),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled") // No recipe site renders without it; the session is wiped on exit.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Browser(
    startUrl: String,
    importViewModel: ImportViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareState by importViewModel.shareState.collectAsStateWithLifecycle()

    // Plain state holders (not `by`), because the WebViewClient below is built once inside
    // remember and has to write into the very same objects the composition reads.
    val currentUrl = remember { mutableStateOf(startUrl) }
    val loading = remember { mutableStateOf(true) }
    val hasRecipe = remember { mutableStateOf(false) }
    val pageTick = remember { mutableIntStateOf(0) }

    val webView = remember {
        WebView(context).apply {
            // Without explicit layout params AndroidView measures the WebView as WRAP_CONTENT, and
            // then the engine has no fixed viewport height: `100vh` resolves to **0** while
            // window.innerHeight is correct. Sites that size an overlay in vh collapse to nothing —
            // brigitte.de's consent layer did exactly that (height 427x0) while its stylesheet had
            // already frozen the page with `body { position: fixed; overflow: hidden }`. The result
            // is an invisible dialog on a page that cannot be scrolled.
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true      // consent state often lives here, not in a cookie
            settings.saveFormData = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mediaPlaybackRequiresUserGesture = true
            // A WebView ignores the page's <meta viewport> unless told otherwise, and then lays
            // a responsive site out at the wrong scale: consent banners pinned to the bottom end
            // up below the visible area, and a body the page locks until they are dismissed can
            // no longer be scrolled. Honour the viewport like a real browser, and leave pinch
            // zoom (without the old on-screen buttons) as the escape hatch.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Consent dialogs are built out of JS dialogs and window.open; without a chrome
            // client those quietly do nothing and the flow can never finish.
            webChromeClient = WebChromeClient()
            // Debug builds only: lets the page be inspected over adb (chrome://inspect). A site
            // misbehaving in here is otherwise invisible from outside — that is how the vh bug
            // above was found.
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            // Cookies behave as in any browser. Blocking third-party ones sounded right, but the
            // consent layers many sites embed are themselves third-party (consentmanager.net and
            // friends): with cross-site storage denied their script dies behind its own grey
            // backdrop, and the site becomes unusable rather than private. Settings → browsing
            // data wipes the lot whenever the user wants.
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            // Present as an ordinary mobile browser. Two tokens give an embedded WebView away:
            // "; wv" and the "Version/4.0" that Chrome on Android does not send. Sites branch on
            // them — brigitte.de literally passes an `isInApp` flag into its consent tool — and
            // then serve an interstitial, refuse a login, or leave the page half-built. Everything
            // else (Android version, device, Chrome build) stays untouched: we *are* a phone, and
            // nothing is claimed here that is not true.
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(WEBVIEW_VERSION_TOKEN, "")
            webViewClient = object : WebViewClient() {
                /** Ad banners like to hand out `intent://` and `market://` links — swallow
                 *  everything that isn't the web, so no foreign app is launched from here. */
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    loading.value = true
                    hasRecipe.value = false
                    currentUrl.value = url
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading.value = false
                    currentUrl.value = url
                    pageTick.intValue++
                }

                /** Fires for in-page navigations too, which is where the history really grows. */
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    currentUrl.value = url
                }
            }
            loadUrl(startUrl)
        }
    }

    // Is there a recipe on this page? Reads only the page's ld+json blocks, a few KB.
    LaunchedEffect(pageTick.intValue) {
        if (pageTick.intValue == 0) return@LaunchedEffect
        repeat(RECIPE_CHECKS) { attempt ->
            val blocks = PageHtml.unwrapJsString(webView.evaluate(PageHtml.COLLECT_JSON_LD))
            if (PageHtml.looksLikeRecipe(blocks)) {
                hasRecipe.value = true
                return@LaunchedEffect
            }
            if (attempt < RECIPE_CHECKS - 1) delay(RECIPE_CHECK_DELAY_MS)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.clearHistory()
            webView.clearFormData()
            webView.clearCache(true)
            // Writes the first-party cookies to disk rather than dropping them — this is what
            // keeps a site's consent banner from reappearing on every single visit.
            CookieManager.getInstance().flush()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // System back and the arrow in the bar do the same thing; the ✕ leaves the browser.
    BackHandler { if (webView.canGoBack()) webView.goBack() else onBack() }

    val importing = shareState is ShareImportState.Fetching

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        hostOf(currentUrl.value),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    // Where a browser's back arrow lives, and it never dead-ends: one step back
                    // through the site, and once the history is used up the same arrow leaves for
                    // the board. Exactly what the system back gesture does, so both agree.
                    IconButton(onClick = { if (webView.canGoBack()) webView.goBack() else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        ImportAction(active = hasRecipe.value) {
                            scope.launch { importOpenPage(webView, currentUrl.value, importViewModel) }
                        }
                    }
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.discover_close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (loading.value) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The import affordance. While the page holds no recipe it is a quiet, disabled icon; the moment
 * one is found it unfolds into a labelled button — the state change has to be noticed while the
 * eye is on the page, not on the bar, so it announces itself in words instead of a shade of grey.
 *
 * The pop on the icon is the same spring the like and cooked buttons use on the recipe screen.
 */
@Composable
private fun ImportAction(active: Boolean, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }

    AnimatedVisibility(
        visible = !active,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // Present but plainly unavailable — this is what makes a listing page readable as
        // "nothing to take here yet".
        IconButton(onClick = {}, enabled = false) {
            Icon(
                Icons.Outlined.SaveAlt,
                contentDescription = stringResource(R.string.discover_import),
            )
        }
    }

    AnimatedVisibility(
        visible = active,
        enter = expandHorizontally(
            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
            expandFrom = Alignment.End,
        ) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
    ) {
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = Spacing.md),
            modifier = Modifier.padding(end = Spacing.sm),
        ) {
            Icon(
                Icons.Outlined.SaveAlt,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .scale(pop.value),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.discover_import_short))
        }
    }
}

/**
 * Import whatever the open page holds: the rendered page's JSON-LD if it has any, otherwise
 * let the view model re-fetch the URL (a few sites ship it only in the raw HTML).
 */
private suspend fun importOpenPage(
    webView: WebView,
    url: String,
    importViewModel: ImportViewModel,
) {
    val blocks = PageHtml.unwrapJsString(webView.evaluate(PageHtml.COLLECT_JSON_LD))
    if (blocks != null) {
        importViewModel.importFromPage(PageHtml.toHtmlFragment(blocks), url)
    } else {
        importViewModel.importFromUrl(url)
    }
}

/** `evaluateJavascript` as a suspend call — it answers on the main thread via callback. */
private suspend fun WebView.evaluate(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { result -> continuation.resume(result) }
    }

/** "www.chefkoch.de" — the bar shows where you are, never a page title we didn't verify. */
private fun hostOf(url: String): String =
    runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { url }
