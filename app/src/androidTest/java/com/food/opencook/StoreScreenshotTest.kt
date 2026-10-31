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

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.WindowInsets
import androidx.annotation.StringRes
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import com.food.opencook.data.local.OpenCookDatabase
import com.food.opencook.data.local.RoomTransactor
import com.food.opencook.data.local.Transactor
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.localization.LocalizedLists
import com.food.opencook.data.settings.ContentLanguageProvider
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.di.DatabaseModule
import com.food.opencook.di.SettingsModule
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.ui.mealplan.MealPlanSlots
import com.food.opencook.ui.showInstanceBanner
import com.food.opencook.util.MealTypes
import com.food.opencook.util.RecipeCategories
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Renders the store / README screenshots with a synthetic household on every connected phone
 * and tablet in parallel (foldables are skipped). Run the shared "Store Screenshots" run
 * configuration, or:
 *
 * ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.food.opencook.StoreScreenshotTest
 *
 * Output: app/build/outputs/connected_android_test_additional_output/…/<device>/ mirrors the
 * repository layout (fastlane/…); copy its content into the repository root.
 * The test uses its own in-memory database and settings file, so data on the device stays
 * untouched. The recipe photos (androidTest assets) are CC0 from Wikimedia Commons.
 */
@HiltAndroidTest
class StoreScreenshotTest {

    private val hiltRule = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(compose)

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var recipes: RecipeRepository
    @Inject lateinit var shopping: ShoppingRepository
    @Inject lateinit var pantry: PantryRepository
    @Inject lateinit var mealPlan: MealPlanRepository
    @Inject lateinit var localizedLists: LocalizedLists

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var kind: String
    private var originalNightMode = ""
    private var originalLocales = LocaleList.getEmptyLocaleList()
    private val photoDir by lazy { File(context.filesDir, "store_screenshots") }

    @Before
    fun setUp() {
        val metrics = context.resources.displayMetrics
        val ratio = max(metrics.widthPixels, metrics.heightPixels).toFloat() /
            minOf(metrics.widthPixels, metrics.heightPixels)
        assumeTrue("Foldables are not part of the store listing", ratio > 1.4f)
        kind = if (context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"

        hiltRule.inject()
        showInstanceBanner = false
        originalNightMode = shell("cmd uimode night").substringAfter(":").trim()
        originalLocales = localeManager().applicationLocales
        nightMode(true)
        localeManager().applicationLocales = LocaleList.forLanguageTags("en")

        runBlocking { seedData() }
        scenario = ActivityScenario.launch(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    @After
    fun tearDown() {
        if (!::scenario.isInitialized) return
        scenario.close()
        shell("cmd uimode night $originalNightMode")
        localeManager().applicationLocales = originalLocales
        showInstanceBanner = BuildConfig.DEBUG
        photoDir.deleteRecursively()
    }

    @Test
    fun captureStoreScreenshots() {
        // Dark theme throughout; the recipe detail shows the light one.
        open(R.string.nav_recipes)
        capture(1)

        nightMode(false)
        click("Prawn Risotto")
        capture(2)
        pressBack()
        nightMode(true)

        // From the plan on, something keeps recomposing (the plan's housekeeping waits for a sync
        // round that never comes here), so Compose never reports idle: drive the clock by hand.
        compose.mainClock.autoAdvance = false
        open(R.string.nav_plan)
        repeat(6) { settle() }
        capture(3)

        open(R.string.nav_shopping_short)
        repeat(4) { settle() }
        capture(4)
    }

    // --- Navigation --------------------------------------------------------------------------

    private fun string(@StringRes id: Int): String {
        var text = ""
        scenario.onActivity { text = it.getString(id) }
        return text
    }

    private fun click(text: String) {
        settle()
        val nodes = compose.onAllNodesWithText(text)
        // Rows and cards are clickable containers around the text; tapping the text hits them too.
        val clickable = nodes.filter(hasClickAction())
        (if (clickable.fetchSemanticsNodes().isNotEmpty()) clickable else nodes).onFirst().performClick()
        settle()
    }

    /** Top-level screens: bottom bar on phones, navigation rail on tablets — same labels. */
    private fun open(@StringRes route: Int) = click(string(route))

    private fun pressBack() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
    }

    /** With a hand-driven clock (see the plan) one step = half a second of real and UI time. */
    private fun settle() {
        instrumentation.waitForIdleSync()
        if (compose.mainClock.autoAdvance) {
            compose.waitForIdle()
        } else {
            Thread.sleep(500)
            compose.mainClock.advanceTimeBy(500)
        }
    }

    private fun shell(cmd: String): String =
        instrumentation.uiAutomation.executeShellCommand(cmd).use { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()
        }

    /** System dark mode (the app follows it); recreating the activity takes a moment. */
    private fun nightMode(on: Boolean) {
        shell("cmd uimode night ${if (on) "yes" else "no"}")
        Thread.sleep(1500)
        settle()
    }

    private fun localeManager(): LocaleManager = context.getSystemService(LocaleManager::class.java)

    // --- Capture & store layout --------------------------------------------------------------

    /** Store screenshot n, written with the repository layout so the output can be copied as-is. */
    private fun capture(n: Int) {
        val store = if (kind == "phone") "phoneScreenshots" else "tenInchScreenshots"
        save(framed(shoot()), "fastlane/metadata/android/en-US/images/$store/${n}_${SCREENS.getValue(n)}.png")
    }

    private class Screen(val bitmap: Bitmap, val statusBar: Int)

    private fun shoot(): Screen {
        settle()
        Thread.sleep(1500) // image loading and list animations
        settle()
        val screen = instrumentation.uiAutomation.takeScreenshot().copy(Bitmap.Config.ARGB_8888, true)
        var statusBar = 0
        scenario.onActivity {
            statusBar = it.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.statusBars()).top
        }
        drawStatusBar(screen, statusBar)
        return Screen(screen, statusBar)
    }

    private fun save(image: Bitmap, path: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: context.getExternalFilesDir("store_screenshots")!!
        val file = File(dir, path).apply { parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Replaces the real status bar (notification icons, carrier state) with a clean one. */
    private fun drawStatusBar(screen: Bitmap, height: Int) {
        val dp = context.resources.displayMetrics.density
        val canvas = Canvas(screen)
        val bg = screen.getPixel(screen.width / 2, height + 2)
        canvas.drawRect(0f, 0f, screen.width.toFloat(), height.toFloat(), Paint().apply { color = bg })
        val dark = Color.luminance(bg) < 0.5f
        val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.WHITE else Color.rgb(31, 31, 31)
            textSize = 14 * dp
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val cy = height * 0.55f
        val margin = 24 * dp
        canvas.drawText("12:00", margin, cy - (fg.ascent() + fg.descent()) / 2, fg)

        val u = 1.1f * dp
        var x = screen.width - margin
        // battery
        val battery = RectF(x - 11 * u, cy - 3 * u, x - u, cy + 3 * u)
        canvas.drawRoundRect(battery, 1.5f * u, 1.5f * u, fg)
        canvas.drawRoundRect(RectF(x - u, cy - 1.3f * u, x, cy + 1.3f * u), 0.5f * u, 0.5f * u, fg)
        x = battery.left - 2.5f * u
        // cellular signal
        for (i in 0 until 4) {
            val barLeft = x - (4 - i) * 2.2f * u
            canvas.drawRoundRect(RectF(barLeft, cy + 3.5f * u - (i + 1) * 1.75f * u, barLeft + 1.5f * u, cy + 3.5f * u), 0.4f * u, 0.4f * u, fg)
        }
        x -= 4 * 2.2f * u + 2.5f * u
        // wifi
        val wifi = Path().apply {
            moveTo(x - 4.5f * u, cy + 3.5f * u)
            arcTo(RectF(x - 9 * u, cy - 5 * u, x, cy + 4 * u), 225f, 90f, false)
            close()
        }
        canvas.drawPath(wifi, fg)
    }

    /** The screenshot inside a drawn device frame on a transparent canvas — no caption. */
    private fun framed(screen: Screen): Bitmap {
        val landscape = screen.bitmap.width > screen.bitmap.height
        val screenWidth = if (landscape) 2060f else 1130f
        val k = screenWidth / if (landscape) 2060f else 1130f
        val bezel = (if (landscape) 44f else 26f) * k
        val margin = 64f * k // room for the side buttons and the drop shadow
        val screenHeight = screen.bitmap.height * screenWidth / screen.bitmap.width
        val out = Bitmap.createBitmap(
            (screenWidth + 2 * (bezel + margin)).toInt(),
            (screenHeight + 2 * (bezel + margin)).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        drawDevice(Canvas(out), screen, bezel + margin, bezel + margin, screenWidth)
        return out
    }

    /** Device frame around [screen]; all measures scale with [screenWidth] (display left/top given). */
    private fun drawDevice(canvas: Canvas, screen: Screen, left: Float, top: Float, screenWidth: Float) {
        val landscape = screen.bitmap.width > screen.bitmap.height
        val k = screenWidth / if (landscape) 2060f else 1130f
        val bezel = (if (landscape) 44f else 26f) * k
        val screenRadius = (if (landscape) 36f else 96f) * k
        val scale = screenWidth / screen.bitmap.width
        val display = RectF(left, top, left + screenWidth, top + screen.bitmap.height * scale)
        val body = RectF(display).apply { inset(-bezel, -bezel) }
        val bodyRadius = screenRadius + bezel

        if (!landscape) { // side buttons
            val button = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 74, 80) }
            canvas.drawRoundRect(RectF(body.right - 4 * k, body.top + 330 * k, body.right + 7 * k, body.top + 450 * k), 6 * k, 6 * k, button)
            canvas.drawRoundRect(RectF(body.right - 4 * k, body.top + 520 * k, body.right + 7 * k, body.top + 760 * k), 6 * k, 6 * k, button)
        }
        canvas.drawRoundRect(body, bodyRadius, bodyRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 18, 20)
            setShadowLayer(48f * k, 0f, 16f * k, Color.argb(100, 0, 0, 0))
        })
        val edge = maxOf(7f * k, 1.5f)
        canvas.drawRoundRect(RectF(body).apply { inset(edge / 2, edge / 2) }, bodyRadius - edge / 2, bodyRadius - edge / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = edge
            shader = LinearGradient(
                0f, body.top, 0f, body.bottom,
                intArrayOf(Color.rgb(160, 166, 176), Color.rgb(78, 82, 90), Color.rgb(150, 156, 166)),
                null, Shader.TileMode.CLAMP,
            )
        })

        // halve step by step before the final scale; a single bilinear step leaves text jagged
        var bitmap = screen.bitmap
        while (bitmap.width / 2 >= screenWidth) {
            bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
        }
        val bitmapScale = screenWidth / bitmap.width
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(bitmapScale, bitmapScale); postTranslate(left, top) })
        }
        canvas.drawRoundRect(display, screenRadius, screenRadius, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        })

        val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 8, 10) }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(44, 46, 52) }
        val cx = display.centerX()
        if (landscape) {
            canvas.drawCircle(cx, body.top + bezel / 2, 9f * k, ring)
            canvas.drawCircle(cx, body.top + bezel / 2, 5f * k, lens)
        } else {
            val r = 11 * context.resources.displayMetrics.density * scale
            canvas.drawCircle(cx, top + screen.statusBar * scale * 0.55f, r + 3 * k, ring)
            canvas.drawCircle(cx, top + screen.statusBar * scale * 0.55f, r, lens)
        }
    }

    // --- Synthetic household -----------------------------------------------------------------

    private class Seed(
        val name: String,
        val photo: String,
        val categories: List<String>,
        val meals: List<String>,
        val servings: Int,
        val minutes: Int,
        val cookbook: String?,
        val ingredients: List<Triple<Double?, String?, String>>,
        val steps: List<String>,
    )

    private suspend fun seedData() {
        // Only what OpenCookApplication.onCreate would have set up and the screens rely on.
        localizedLists.loadStatic()
        settings.setContentLanguage("en")
        localizedLists.reload()
        settings.setLocalOnly(true)
        settings.setHouseholdSize(2)
        settings.setDynamicColor(false)
        settings.setSwipeHintSeenShopping()
        settings.setSwipeHintSeenPantry()
        settings.setSwipeHintSeenPlan()

        photoDir.mkdirs()
        val now = System.currentTimeMillis()
        // Newest first in the list: the last seeded recipe opens it.
        val ids = RECIPES.reversed().mapIndexed { i, r ->
            r.name to saveRecipe(r, createdAt = now - (RECIPES.size - i) * 60_000L)
        }.toMap()

        // The coming days, planned and flagged as done so the auto-planner leaves them as they are.
        val today = LocalDate.now()
        PLAN.forEachIndexed { day, name ->
            val date = today.plusDays(day.toLong()).toString()
            mealPlan.addEntry(date, ids.getValue(name), MealPlanSlots.PRIMARY)
            mealPlan.markAutoPlanned(date)
        }

        listOf("Olive oil", "Salt", "Pepper", "Flour", "Rice").forEach { pantry.addItem(it) }

        SHOPPING.forEach { (qty, unit, name) -> shopping.addItem(name, qty, unit, manual = true) }
        val checked = setOf("Bananas", "Milk")
        shopping.observeItems().first().filter { it.text in checked }.forEach { shopping.setChecked(it.id, true) }
    }

    private suspend fun saveRecipe(r: Seed, createdAt: Long): String {
        val id = UUID.randomUUID().toString()
        val photo = File(photoDir, "${r.photo}.jpg")
        instrumentation.context.assets.open("screenshots/${r.photo}.jpg").use { input ->
            photo.outputStream().use { input.copyTo(it) }
        }
        recipes.saveRecipe(
            recipe = RecipeEntity(
                id = id,
                name = r.name,
                servings = r.servings,
                category = RecipeCategories.toStored(r.categories),
                mealTypes = MealTypes.toStored(r.meals),
                cookbook = r.cookbook,
                totalTime = "PT${r.minutes}M",
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
            ingredients = r.ingredients.mapIndexed { i, (qty, unit, name) ->
                IngredientEntity(UUID.randomUUID().toString(), id, i, qty, unit, name)
            },
            instructions = r.steps.mapIndexed { i, text -> InstructionEntity(UUID.randomUUID().toString(), id, i, text) },
            nutrition = null,
            images = listOf(ImageEntity(UUID.randomUUID().toString(), id, 0, localPath = photo.absolutePath, isPrimary = true)),
        )
        return id
    }

    companion object {
        /** Screen number to fastlane file slug (the README links these names). */
        private val SCREENS = mapOf(
            1 to "recipes",
            2 to "recipe_detail",
            3 to "meal_planner",
            4 to "shopping_list",
        )

        /** Classic dishes in our own words; photos are CC0 (Wikimedia Commons). */
        private val RECIPES = listOf(
            Seed(
                "Prawn Risotto", "prawn_risotto", listOf("fish"), listOf("lunch", "dinner"), 2, 35, "Weeknight favourites",
                listOf(
                    Triple(200.0, "g", "risotto rice"), Triple(250.0, "g", "prawns"), Triple(1.0, null, "shallot"),
                    Triple(1.0, "clove", "garlic"), Triple(100.0, "ml", "dry white wine"), Triple(700.0, "ml", "fish stock"),
                    Triple(30.0, "g", "butter"), Triple(30.0, "g", "parmesan"), Triple(null, null, "chives"),
                ),
                listOf(
                    "Soften the shallot and garlic in half the butter, then stir in the rice until glossy.",
                    "Deglaze with the wine and add the hot stock a ladle at a time, stirring, for about 18 minutes.",
                    "Sear the prawns in a hot pan for 2 minutes per side.",
                    "Beat the remaining butter and the parmesan into the rice, top with the prawns and chives.",
                ),
            ),
            Seed(
                "Avocado Toast with Poached Egg", "avocado_toast", listOf("vegetarian"), listOf("breakfast", "lunch"), 2, 15, null,
                listOf(
                    Triple(2.0, "slices", "sourdough bread"), Triple(1.0, null, "ripe avocado"), Triple(2.0, null, "eggs"),
                    Triple(0.5, null, "lime"), Triple(null, null, "chili flakes"), Triple(null, null, "cress"),
                ),
                listOf(
                    "Toast the bread until golden.",
                    "Mash the avocado with lime juice, salt and pepper and spread it on the toast.",
                    "Poach the eggs in gently simmering water for 3 minutes.",
                    "Top each toast with an egg, chili flakes and cress.",
                ),
            ),
            Seed(
                "Tofu Poke Bowl", "tofu_poke_bowl", listOf("vegetarian"), listOf("lunch", "dinner"), 2, 30, null,
                listOf(
                    Triple(150.0, "g", "sushi rice"), Triple(200.0, "g", "firm tofu"), Triple(1.0, null, "avocado"),
                    Triple(0.5, null, "cucumber"), Triple(100.0, "g", "edamame"), Triple(50.0, "g", "wakame salad"),
                    Triple(2.0, "tbsp", "soy sauce"), Triple(1.0, "tbsp", "sesame seeds"), Triple(1.0, "sheet", "nori"),
                ),
                listOf(
                    "Cook the rice and season it with a little rice vinegar.",
                    "Cube the tofu, fry until golden and glaze with the soy sauce.",
                    "Slice the cucumber and avocado; warm the edamame.",
                    "Fill bowls with rice, arrange the toppings and finish with sesame and nori strips.",
                ),
            ),
            Seed(
                "Lasagna", "lasagna", listOf("pasta", "meat"), listOf("dinner"), 6, 90, "Sunday cooking",
                listOf(
                    Triple(12.0, "sheets", "lasagna"), Triple(500.0, "g", "ground beef"), Triple(800.0, "g", "chopped tomatoes"),
                    Triple(50.0, "g", "butter"), Triple(50.0, "g", "flour"), Triple(600.0, "ml", "milk"),
                    Triple(150.0, "g", "mozzarella"),
                ),
                listOf(
                    "Make a meat sauce with the beef and tomatoes and let it simmer for 30 minutes.",
                    "Melt the butter, stir in the flour, then whisk in the milk to a smooth béchamel.",
                    "Layer sauce, pasta sheets and béchamel in a baking dish, finishing with béchamel.",
                    "Top with mozzarella and bake at 180 °C for 40 minutes.",
                ),
            ),
            Seed(
                "Chicken Curry with Rice", "chicken_curry", listOf("meat"), listOf("lunch", "dinner"), 4, 40, "Weeknight favourites",
                listOf(
                    Triple(600.0, "g", "chicken thighs"), Triple(1.0, null, "onion"), Triple(2.0, "tbsp", "curry paste"),
                    Triple(400.0, "ml", "coconut milk"), Triple(250.0, "g", "rice"), Triple(null, null, "fresh coriander"),
                ),
                listOf(
                    "Cook the rice.",
                    "Fry the onion until soft, add the curry paste and cook for a minute.",
                    "Add the chicken, brown it, then pour in the coconut milk and simmer for 20 minutes.",
                    "Serve with the rice and a handful of coriander.",
                ),
            ),
            Seed(
                "Fluffy Pancakes", "pancakes", listOf("dessert"), listOf("breakfast"), 4, 20, "Sunday cooking",
                listOf(
                    Triple(250.0, "g", "flour"), Triple(2.0, "tsp", "baking powder"), Triple(2.0, null, "eggs"),
                    Triple(300.0, "ml", "milk"), Triple(2.0, "tbsp", "sugar"), Triple(null, null, "maple syrup"),
                ),
                listOf(
                    "Whisk flour, baking powder and sugar; beat in the eggs and milk to a thick batter.",
                    "Cook small ladlefuls in a buttered pan until bubbles appear, then flip.",
                    "Stack and serve warm with maple syrup.",
                ),
            ),
            Seed(
                "Red Lentil Soup", "lentil_soup", listOf("soup", "vegetarian"), listOf("lunch", "dinner"), 4, 35, null,
                listOf(
                    Triple(250.0, "g", "red lentils"), Triple(1.0, null, "onion"), Triple(1.0, null, "carrot"),
                    Triple(1.0, "tsp", "ground cumin"), Triple(1.0, "l", "vegetable stock"), Triple(1.0, null, "lemon"),
                ),
                listOf(
                    "Soften onion and carrot in a little oil, then add the cumin.",
                    "Add lentils and stock and simmer for 20 minutes until the lentils fall apart.",
                    "Blend roughly and finish with a squeeze of lemon.",
                ),
            ),
            Seed(
                "Banana Bread", "banana_bread", listOf("dessert"), listOf("breakfast", "snack"), 10, 70, "Sunday cooking",
                listOf(
                    Triple(3.0, null, "ripe bananas"), Triple(100.0, "g", "butter"), Triple(120.0, "g", "sugar"),
                    Triple(2.0, null, "eggs"), Triple(250.0, "g", "flour"), Triple(1.0, "tsp", "baking soda"),
                ),
                listOf(
                    "Mash the bananas and mix with the melted butter, sugar and eggs.",
                    "Fold in flour and baking soda until just combined.",
                    "Bake in a lined loaf tin at 175 °C for about 55 minutes.",
                ),
            ),
            Seed(
                "Wild Garlic Pesto Pasta", "wild_garlic_pesto_pasta", listOf("pasta", "vegetarian"), listOf("lunch", "dinner"), 4, 20, null,
                listOf(
                    Triple(400.0, "g", "pasta"), Triple(100.0, "g", "wild garlic"), Triple(50.0, "g", "pine nuts"),
                    Triple(50.0, "g", "parmesan"), Triple(100.0, "ml", "olive oil"), Triple(1.0, null, "cherry tomato"),
                ),
                listOf(
                    "Toast the pine nuts and blend them with wild garlic, parmesan and oil.",
                    "Cook the pasta and keep a cup of the cooking water.",
                    "Toss pasta and pesto, loosening with a little cooking water.",
                ),
            ),
        )

        /** Today and the following week, one main meal a day. */
        private val PLAN = listOf(
            "Prawn Risotto", "Avocado Toast with Poached Egg", "Tofu Poke Bowl", "Chicken Curry with Rice",
            "Wild Garlic Pesto Pasta", "Red Lentil Soup", "Lasagna", "Fluffy Pancakes",
        )

        /** Shopping list across several aisles; quantities as a household would add them. */
        private val SHOPPING = listOf(
            Triple(6.0, null, "Eggs"),
            Triple(800.0, "g", "Chopped tomatoes"),
            Triple(200.0, "g", "Feta"),
            Triple(250.0, "g", "Prawns"),
            Triple(2.0, null, "Avocados"),
            Triple(500.0, "g", "Ground beef"),
            Triple(3.0, null, "Onions"),
            Triple(1.0, null, "Garlic"),
            Triple(3.0, null, "Bananas"),
            Triple(1.0, "l", "Milk"),
            Triple(250.0, "g", "Butter"),
            Triple(1.0, null, "Sourdough bread"),
            Triple(400.0, "ml", "Coconut milk"),
            Triple(null, null, "Toothpaste"),
        )
    }
}

// --- Test wiring ---------------------------------------------------------------------------------

open class ScreenshotTestAppBase : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}

@CustomTestApplication(ScreenshotTestAppBase::class)
interface ScreenshotTestApp

class ScreenshotTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, ScreenshotTestApp_Application::class.java.name, context)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object ScreenshotDatabaseModule {
    @Provides
    @Singleton
    fun provideTransactor(impl: RoomTransactor): Transactor = impl

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): OpenCookDatabase =
        Room.inMemoryDatabaseBuilder(ctx, OpenCookDatabase::class.java).build()

    @Provides fun provideRecipeDao(db: OpenCookDatabase) = db.recipeDao()
    @Provides fun provideJobDao(db: OpenCookDatabase) = db.jobDao()
    @Provides fun provideMessageDao(db: OpenCookDatabase) = db.messageDao()
    @Provides fun provideShoppingDao(db: OpenCookDatabase) = db.shoppingDao()
    @Provides fun providePantryDao(db: OpenCookDatabase) = db.pantryDao()
    @Provides fun provideMealPlanDao(db: OpenCookDatabase) = db.mealPlanDao()
    @Provides fun provideMealDayDao(db: OpenCookDatabase) = db.mealDayDao()
    @Provides fun provideRecipeLikeDao(db: OpenCookDatabase) = db.recipeLikeDao()
    @Provides fun provideProductCacheDao(db: OpenCookDatabase) = db.productCacheDao()
    @Provides fun provideGroceryOverrideDao(db: OpenCookDatabase) = db.groceryOverrideDao()
    @Provides fun provideIngredientLinkDao(db: OpenCookDatabase) = db.ingredientLinkDao()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SettingsModule::class])
object ScreenshotSettingsModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> {
        val file = ctx.preferencesDataStoreFile("store_screenshots")
        file.delete()
        return PreferenceDataStoreFactory.create { file }
    }

    @Provides
    @Singleton
    fun provideContentLanguageProvider(settings: SettingsRepository): ContentLanguageProvider =
        ContentLanguageProvider { settings.effectiveContentLanguage(settings.contentLanguageOnce()) }
}
