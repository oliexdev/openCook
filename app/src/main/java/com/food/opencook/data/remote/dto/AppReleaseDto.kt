package com.food.opencook.data.remote.dto

import kotlinx.serialization.Serializable

/** Metadata for the latest published app release (GET /app/latest). */
@Serializable
data class AppReleaseDto(
    val versionCode: Int,
    val versionName: String,
    /** Relative download path on the same server, e.g. "/app/download/opencook-1.1-2.apk". */
    val url: String,
    val notes: String? = null,
)
