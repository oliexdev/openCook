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

package com.food.opencook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.food.opencook.BuildConfig
import com.food.opencook.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.ui.admin.AdminScreen
import com.food.opencook.ui.barcode.BarcodeScanScreen
import com.food.opencook.ui.home.HomeScreen
import com.food.opencook.ui.onboarding.OnboardingScreen
import com.food.opencook.ui.scan.ScanViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.food.opencook.data.notification.JobNotifier
import com.food.opencook.ui.capture.CameraCaptureScreen
import com.food.opencook.ui.mealplan.MealPlanPickScreen
import com.food.opencook.ui.mealplan.MealPlanScreen
import com.food.opencook.ui.navigation.Routes
import com.food.opencook.ui.navigation.TopLevelDestination
import com.food.opencook.ui.recipeimport.ImportViewModel
import com.food.opencook.ui.recipeimport.ShareImportState
import com.food.opencook.ui.recipes.RecipeDetailScreen
import com.food.opencook.ui.recipes.RecipesScreen
import com.food.opencook.ui.review.ReviewScreen
import com.food.opencook.ui.scan.ScanScreen
import com.food.opencook.ui.settings.SettingsScreen
import com.food.opencook.ui.shoppinglist.ShoppingHubScreen
import com.food.opencook.ui.status.ActiveJobsViewModel
import com.food.opencook.ui.status.StatusStrip

/** Debug-only "DEV BUILD" banner so the dev install is unmistakable. Hidden in release.
 *  The ② variant marks the second side-by-side identity (.dev2) used for sync E2E tests. */
@Composable
private fun InstanceBanner() {
    if (!BuildConfig.DEBUG) return
    val isSecond = BuildConfig.APPLICATION_ID.endsWith(".dev2")
    Surface(
        color = if (isSecond) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isSecond) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (isSecond) "● DEV BUILD ②" else "● DEV BUILD",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

/**
 * Root composable. Gates the app on household membership: while not in a household the
 * onboarding flow is shown; once joined (or after leaving), the main app appears.
 */
@Composable
fun OpenCookApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val onboardState by appViewModel.onboardState.collectAsStateWithLifecycle()
    when (onboardState) {
        OnboardState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        OnboardState.NotOnboarded -> OnboardingScreen()
        OnboardState.Onboarded -> MainScaffold()
    }
}

/**
 * The main app: **adaptive** navigation (bottom bar on phones, rail/drawer on tablets)
 * via NavigationSuiteScaffold, hosting the top-level destinations. Focused full-screen
 * flows (scan/review/detail/…) hide the navigation.
 */
@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showShell = currentRoute !in Routes.fullScreenRoutes

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val activeJobsViewModel: ActiveJobsViewModel = hiltViewModel()
    val stripState by activeJobsViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Notify when recipes arrive from the browser-import inbox (drained during sync).
    val appBarViewModel: AppBarViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        appBarViewModel.importedEvents.collect { r ->
            val parts = buildList {
                if (r.imported > 0) {
                    add(context.resources.getQuantityString(R.plurals.import_browser_imported, r.imported, r.imported))
                }
                if (r.duplicates > 0) {
                    add(context.resources.getQuantityString(R.plurals.import_browser_skipped, r.duplicates, r.duplicates))
                }
            }
            if (parts.isNotEmpty()) snackbarHostState.showSnackbar(parts.joinToString(" · "))
        }
    }

    // Recipe shared into the app from a browser ("Teilen → openCook"): import it, then confirm.
    // A URL shared before onboarding is buffered in the bus and picked up here once joined.
    val importViewModel: ImportViewModel = hiltViewModel()
    val pendingShareUrl by importViewModel.pendingShareUrl.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShareUrl) {
        pendingShareUrl?.let { importViewModel.importFromUrl(it) }
    }
    val shareState by importViewModel.shareState.collectAsStateWithLifecycle()
    // Resolve snackbar strings in composable scope (stringResource can't run inside the effect).
    val shareSavedMsg = (shareState as? ShareImportState.Saved)?.let {
        stringResource(R.string.import_share_saved, it.name)
    }
    val shareViewLabel = stringResource(R.string.import_share_view)
    val shareDuplicateMsg = (shareState as? ShareImportState.Duplicate)?.let {
        stringResource(R.string.import_share_duplicate, it.name)
    }
    val shareNoRecipeMsg = stringResource(R.string.import_share_no_recipe)
    LaunchedEffect(shareState) {
        when (val s = shareState) {
            is ShareImportState.Saved -> {
                val result = snackbarHostState.showSnackbar(
                    message = shareSavedMsg.orEmpty(),
                    actionLabel = shareViewLabel,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    navController.navigate(Routes.recipeDetail(s.recipeId))
                }
                importViewModel.resetShare()
            }
            is ShareImportState.Duplicate -> {
                snackbarHostState.showSnackbar(shareDuplicateMsg.orEmpty())
                importViewModel.resetShare()
            }
            ShareImportState.NoRecipe -> {
                snackbarHostState.showSnackbar(shareNoRecipeMsg)
                importViewModel.resetShare()
            }
            is ShareImportState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                importViewModel.resetShare()
            }
            ShareImportState.Fetching, ShareImportState.Idle -> {}
        }
    }

    // Adaptive: bar (compact) / rail (medium) / drawer (expanded); hidden on full-screen flows.
    val layoutType = if (showShell) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        NavigationSuiteScaffold(
            layoutType = layoutType,
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    item(
                        selected = selected,
                        onClick = { navigateToTab(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            },
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                InstanceBanner()
                Box(Modifier.weight(1f)) {
                    AppNavHost(navController, navigateToTab)
                }
                if (showShell) {
                    StatusStrip(
                        state = stripState,
                        onReview = { navController.navigate(Routes.reviewAll()) },
                        onRetry = { activeJobsViewModel.retryFailed() },
                        onCancel = { activeJobsViewModel.cancelActive() },
                        onDismiss = { activeJobsViewModel.acknowledgeFinished() },
                    )
                }
                SnackbarHost(snackbarHostState)
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, navigateToTab: (String) -> Unit) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(
                onOpenRecipes = { navigateToTab(TopLevelDestination.RECIPES.route) },
                onAddRecipe = { navController.navigate(Routes.SCAN) },
                onOpenRecipe = { navController.navigate(Routes.recipeDetail(it)) },
                onOpenPlan = { navigateToTab(TopLevelDestination.PLAN.route) },
                onOpenShopping = { navigateToTab(TopLevelDestination.SHOPPING.route) },
            )
        }
        composable(TopLevelDestination.RECIPES.route) {
            RecipesScreen(
                onRecipeClick = { navController.navigate(Routes.recipeDetail(it)) },
                onAddRecipe = { navController.navigate(Routes.SCAN) },
            )
        }
        composable(TopLevelDestination.PLAN.route) {
            MealPlanScreen(
                onOpenRecipe = { navController.navigate(Routes.recipeDetail(it)) },
                onPickRecipe = { date, slot -> navController.navigate(Routes.planPick(date, slot)) },
            )
        }
        composable(TopLevelDestination.SHOPPING.route) {
            ShoppingHubScreen(
                onScanBarcodeShopping = { navController.navigate(Routes.barcodeScan("shopping")) },
                onScanBarcodePantry = { navController.navigate(Routes.barcodeScan("pantry")) },
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(onOpenAdmin = { navController.navigate(Routes.ADMIN) })
        }
        composable(Routes.ADMIN) {
            AdminScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.PLAN_PICK,
            arguments = listOf(
                navArgument(Routes.ARG_DATE) { type = NavType.StringType },
                navArgument(Routes.ARG_SLOT) { type = NavType.StringType },
            ),
        ) { entry ->
            MealPlanPickScreen(
                date = entry.arguments?.getString(Routes.ARG_DATE).orEmpty(),
                slotKey = entry.arguments?.getString(Routes.ARG_SLOT).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(
                onNavigateToCamera = { navController.navigate(Routes.CAMERA) },
                onNavigateToSettings = { navigateToTab(TopLevelDestination.SETTINGS.route) },
                onCreateManually = { navController.navigate(Routes.reviewNew()) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.BARCODE_SCAN,
            arguments = listOf(navArgument(Routes.ARG_BARCODE_TARGET) { type = NavType.StringType }),
        ) {
            BarcodeScanScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CAMERA) {
            val scanViewModel: ScanViewModel = hiltViewModel()
            CameraCaptureScreen(
                onImageCaptured = { path -> scanViewModel.startScan(path) { navController.popBackStack() } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REVIEW_CAMERA) {
            CameraCaptureScreen(
                onImageCaptured = { path ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(Routes.RESULT_CAPTURED_PATH, path)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.REVIEW,
            arguments = listOf(navArgument(Routes.ARG_JOB_ID) { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "${JobNotifier.DEEP_LINK_REVIEW}/{${Routes.ARG_JOB_ID}}" }),
        ) { entry ->
            val capturedPath by entry.savedStateHandle
                .getStateFlow<String?>(Routes.RESULT_CAPTURED_PATH, null)
                .collectAsStateWithLifecycle()
            ReviewScreen(
                onSaved = {
                    navController.navigate(TopLevelDestination.RECIPES.route) {
                        popUpTo(navController.graph.startDestinationId)
                    }
                },
                onClose = { navController.popBackStack() },
                onTakePhoto = { navController.navigate(Routes.REVIEW_CAMERA) },
                capturedImagePath = capturedPath,
                onCapturedConsumed = { entry.savedStateHandle[Routes.RESULT_CAPTURED_PATH] = null },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument(Routes.ARG_RECIPE_ID) { type = NavType.StringType }),
        ) { entry ->
            val capturedPath by entry.savedStateHandle
                .getStateFlow<String?>(Routes.RESULT_CAPTURED_PATH, null)
                .collectAsStateWithLifecycle()
            ReviewScreen(
                onSaved = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
                onTakePhoto = { navController.navigate(Routes.REVIEW_CAMERA) },
                capturedImagePath = capturedPath,
                onCapturedConsumed = { entry.savedStateHandle[Routes.RESULT_CAPTURED_PATH] = null },
            )
        }
        composable(
            Routes.RECIPE_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_RECIPE_ID) { type = NavType.StringType }),
        ) { entry ->
            val recipeId = entry.arguments?.getString(Routes.ARG_RECIPE_ID).orEmpty()
            RecipeDetailScreen(
                recipeId = recipeId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(recipeId)) },
            )
        }
    }
}
