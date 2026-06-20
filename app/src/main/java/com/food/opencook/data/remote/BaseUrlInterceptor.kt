package com.food.opencook.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites every request's scheme/host/port to the user-configured server URL,
 * which can change at runtime via Settings. This lets us keep a single singleton
 * Retrofit/OkHttp (built against a throwaway base URL) instead of rebuilding it
 * whenever the address changes.
 *
 * [baseUrl] is kept fresh by a collector started in OpenCookApplication; reads
 * from another thread see the latest value via @Volatile.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor() : Interceptor {

    @Volatile
    private var baseUrl: HttpUrl? = null

    /** Update from the persisted setting. Invalid/blank URLs clear the override. */
    fun setBaseUrl(url: String?) {
        baseUrl = url?.trim()?.takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val base = baseUrl
            ?: throw IOException("Server URL not configured")
        val newUrl = chain.request().url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        val request = chain.request().newBuilder().url(newUrl).build()
        return chain.proceed(request)
    }
}
