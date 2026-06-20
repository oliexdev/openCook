package com.food.opencook.di

import com.food.opencook.BuildConfig
import com.food.opencook.data.remote.AdminApi
import com.food.opencook.data.remote.BaseUrlInterceptor
import com.food.opencook.data.remote.ImportApi
import com.food.opencook.data.remote.JobsApi
import com.food.opencook.data.remote.OpenFoodFactsApi
import com.food.opencook.data.remote.SyncApi
import com.food.opencook.data.remote.UpdateApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(baseUrlInterceptor: BaseUrlInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            // Uploads block until the server returns 201; allow generous timeouts.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            // Placeholder host — always overridden by BaseUrlInterceptor.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideJobsApi(retrofit: Retrofit): JobsApi = retrofit.create(JobsApi::class.java)

    @Provides
    @Singleton
    fun provideSyncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)

    @Provides
    @Singleton
    fun provideImportApi(retrofit: Retrofit): ImportApi = retrofit.create(ImportApi::class.java)

    @Provides
    @Singleton
    fun provideAdminApi(retrofit: Retrofit): AdminApi = retrofit.create(AdminApi::class.java)

    @Provides
    @Singleton
    fun provideUpdateApi(retrofit: Retrofit): UpdateApi = retrofit.create(UpdateApi::class.java)

    // --- Open Food Facts: a SEPARATE client that bypasses BaseUrlInterceptor so the
    // request actually goes to the public API instead of the self-hosted server. ---

    @Provides
    @Singleton
    @Named("openfoodfacts")
    fun provideOpenFoodFactsRetrofit(json: Json): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Open Food Facts asks clients to identify themselves via User-Agent.
                val req = chain.request().newBuilder()
                    .header("User-Agent", "openCook/${BuildConfig.VERSION_NAME} (self-hosted; github.com/opencook)")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(@Named("openfoodfacts") retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.create(OpenFoodFactsApi::class.java)
}
