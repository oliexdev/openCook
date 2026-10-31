// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9's built-in Kotlin defaults to KGP 2.2.10; a newer KGP on the classpath wins
// (documented AGP mechanism). Keep in sync with `kotlin` in libs.versions.toml.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}