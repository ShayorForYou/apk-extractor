package com.shayor.apkextractor.data

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads app icons off the main thread at the exact pixel size they are drawn at,
 * and keeps them in a memory-bounded LRU cache so scrolling stays smooth.
 */
object AppIcons {

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap) =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    private val dispatcher = Dispatchers.IO.limitedParallelism(4)

    fun cached(key: String, sizePx: Int): ImageBitmap? = cache.get("$key#$sizePx")

    suspend fun load(context: Context, packageName: String, key: String, sizePx: Int): ImageBitmap? {
        val cacheKey = "$key#$sizePx"
        cache.get(cacheKey)?.let { return it }
        return withContext(dispatcher) {
            cache.get(cacheKey) ?: runCatching {
                val pm = context.applicationContext.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                bitmap.prepareToDraw() // upload to GPU ahead of time
                bitmap.asImageBitmap().also { cache.put(cacheKey, it) }
            }.getOrNull()
        }
    }

    private fun cacheSizeKb(): Int {
        val maxKb = Runtime.getRuntime().maxMemory() / 1024
        return (maxKb / 8).coerceIn(4L * 1024, 48L * 1024).toInt()
    }
}
