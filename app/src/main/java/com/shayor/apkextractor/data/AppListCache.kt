package com.shayor.apkextractor.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The app list from the last launch, so the next launch can draw it immediately while the
 * fresh list (label loading is slow) is built in the background. Only a display cache: the
 * exporter always re-reads an app's real file paths right before copying.
 */
class AppListCache(context: Context) {

    private val file = File(context.applicationContext.filesDir, "app_list_v1.json")

    suspend fun read(): List<AppInfo>? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        try {
            val array = JSONArray(file.readText())
            List(array.length()) { i -> array.getJSONObject(i).toApp() }
        } catch (e: Exception) {
            Log.w(TAG, "Discarding unreadable app list cache", e)
            file.delete()
            null
        }
    }

    suspend fun write(apps: List<AppInfo>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        apps.forEach { array.put(it.toJson()) }
        // Write-then-rename so a crash mid-write never leaves a truncated cache behind.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(array.toString())
            if (!tmp.renameTo(file)) tmp.delete()
        }.onFailure { Log.w(TAG, "Couldn't write app list cache", it) }
    }

    private fun AppInfo.toJson() = JSONObject()
        .put("p", packageName)
        .put("l", label)
        .put("vn", versionName)
        .put("vc", versionCode)
        .put("sys", isSystem)
        .put("en", isEnabled)
        .put("src", sourceDir)
        .put("splits", JSONArray(splitSourceDirs))
        .put("bs", baseSizeBytes)
        .put("s", sizeBytes)
        .put("fi", firstInstallTime)
        .put("lu", lastUpdateTime)
        .put("min", minSdk)
        .put("tgt", targetSdk)

    private fun JSONObject.toApp(): AppInfo {
        val splits = getJSONArray("splits")
        return AppInfo(
            packageName = getString("p"),
            label = getString("l"),
            versionName = getString("vn"),
            versionCode = getLong("vc"),
            isSystem = getBoolean("sys"),
            isEnabled = getBoolean("en"),
            sourceDir = getString("src"),
            splitSourceDirs = List(splits.length()) { splits.getString(it) },
            baseSizeBytes = getLong("bs"),
            sizeBytes = getLong("s"),
            firstInstallTime = getLong("fi"),
            lastUpdateTime = getLong("lu"),
            minSdk = getInt("min"),
            targetSdk = getInt("tgt"),
        )
    }

    private companion object {
        const val TAG = "AppListCache"
    }
}
