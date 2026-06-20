import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Shared debug keystore: path read from local.properties (`debug.keystore=…`), kept out
// of VCS. Pinning both CLI Gradle and the Flatpak Android Studio to the same key means
// switching between the two no longer forces an uninstall on every install.
val sharedDebugKeystore: File? = run {
    val propsFile = rootProject.file("local.properties")
    if (!propsFile.exists()) return@run null
    val props = Properties()
    propsFile.inputStream().use { props.load(it) }
    val path = props.getProperty("debug.keystore") ?: return@run null
    File(path).takeIf { it.exists() }
}

android {
    namespace = "com.food.opencook"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.food.opencook"
        minSdk = 30
        targetSdk = 36
        // Bump versionCode on EVERY release that gets published to the server — the
        // in-app updater (AppUpdater) only offers an update when the server's published
        // versionCode is higher than this, and Android only installs over the top of an
        // existing install when the new versionCode is greater. versionName is cosmetic.
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Launcher label as a generated resource so the second test identity can
        // override it (see the debug2 build type).
        resValue("string", "app_name", "openCook")
    }

    signingConfigs {
        // Use the shared keystore when configured (see local.properties); otherwise fall
        // back to Gradle's auto-generated default so a fresh checkout still builds.
        getByName("debug") {
            sharedDebugKeystore?.let {
                storeFile = it
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        create("release") {
            val keystorePropertiesFile = rootProject.file("../openCook.keystore")
            val keystoreProperties = Properties()
            var propertiesLoaded : Boolean

            try {
                FileInputStream(keystorePropertiesFile).use { fis ->
                    keystoreProperties.load(fis)
                }
                propertiesLoaded = true
            } catch (_: FileNotFoundException) {
                project.logger.warn("Keystore properties file not found: ${keystorePropertiesFile.absolutePath}. Release signing might fail if not configured via environment variables.")
                propertiesLoaded = false
            }

            if (propertiesLoaded && keystoreProperties.containsKey("releaseKeyStore")) {
                storeFile = file(rootProject.projectDir.canonicalPath + "/" + keystoreProperties.getProperty("releaseKeyStore"))
                keyAlias = keystoreProperties.getProperty("releaseKeyAlias")
                keyPassword = keystoreProperties.getProperty("releaseKeyPassword")
                storePassword = keystoreProperties.getProperty("releaseStorePassword")
            } else {
                project.logger.warn("Release signing information not fully loaded from properties. Ensure it's set via environment variables or the properties file is correct.")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "openCook DEV")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("debug2") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev2"
            isDebuggable = true
            resValue("string", "app_name", "openCook ②")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Compose BOM aligns all Compose artifact versions
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Core + Compose UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation + Lifecycle
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Local persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Networking (Phase 1): Retrofit + OkHttp + kotlinx.serialization JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Image loading (Phase 1)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Camera capture (Phase 1)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Adaptive navigation (redesign): bottom bar (phone) ↔ rail/drawer (tablet)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.window.size)

    // Barcode decoding (Phase 3): ZXing core decodes CameraX frames (no ML Kit — proprietary)
    implementation(libs.zxing.core)

    // Background work (Phase 1): expedited upload + poll workers, Hilt-injected
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
