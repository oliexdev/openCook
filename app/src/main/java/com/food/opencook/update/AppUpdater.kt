package com.food.opencook.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.food.opencook.BuildConfig
import com.food.opencook.data.remote.UpdateApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app updater for the self-hosted distribution flow: ask our own server for the
 * latest published APK, and — if it's newer than this build — download it and hand it
 * to the system installer. No store, no USB. Same-key release builds install cleanly
 * over the top, as long as the published [versionCode] is higher than this one.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateApi: UpdateApi,
) {
    sealed interface Check {
        data object UpToDate : Check
        data class Available(val versionName: String, val url: String, val notes: String?) : Check
        data class Error(val message: String?) : Check
    }

    /** Compare the server's latest release against this build's [BuildConfig.VERSION_CODE]. */
    suspend fun check(): Check = withContext(Dispatchers.IO) {
        runCatching { updateApi.latest() }.fold(
            onSuccess = { rel ->
                if (rel.versionCode > BuildConfig.VERSION_CODE) {
                    Check.Available(rel.versionName, rel.url, rel.notes)
                } else {
                    Check.UpToDate
                }
            },
            onFailure = { Check.Error(it.message) },
        )
    }

    /**
     * Download the APK into the cache and launch the installer. If the app may not yet
     * install unknown apps, sends the user to that system setting instead (they re-tap
     * "Install" afterwards). Returns false only when the download itself failed.
     */
    suspend fun downloadAndInstall(url: String): Boolean {
        val file = withContext(Dispatchers.IO) {
            val target = File(context.cacheDir, "updates/opencook-update.apk")
            target.parentFile?.mkdirs()
            runCatching {
                updateApi.download(url).byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }.getOrNull()
        } ?: return false
        launchInstaller(file)
        return true
    }

    private fun launchInstaller(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // First run: the user must allow "install unknown apps" for openCook once.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
