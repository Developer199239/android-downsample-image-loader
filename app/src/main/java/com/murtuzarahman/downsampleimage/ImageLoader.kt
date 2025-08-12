// ImageLoader.kt
package com.murtuzarahman.downsampleimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.math.max

class ImageLoader(
    context: Context,
    private val targetMemBytes: Int = 512 * 1024,     // ~0.5 MB target in-memory size
    private val useRgb565: Boolean = true             // saves memory vs ARGB_8888
) {
    private val client = OkHttpClient()
    private val diskDir = File(context.cacheDir, "img_cache").apply { mkdirs() }

    // 1/8th of available memory for cache
    private val memCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024).toInt() / 8) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(url: String): Bitmap? {
        // memory cache
        memCache.get(url)?.let { return it }

        // disk
        val file = downloadToDisk(url) ?: return null

        // decode downsampled
        val bitmap = decodeDownsampled(file) ?: return null

        memCache.put(url, bitmap)
        return bitmap
    }

    private suspend fun downloadToDisk(url: String): File? = withContext(Dispatchers.IO) {
        val key = url.md5() + ".img"
        val outFile = File(diskDir, key)
        if (outFile.exists() && outFile.length() > 0) return@withContext outFile

        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body ?: return@withContext null
            outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        outFile
    }

    private suspend fun decodeDownsampled(file: File): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return@withContext null

        val preferredConfig = if (useRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val bpp = if (useRgb565) 2 else 4

        // Choose inSampleSize so that (w/ss)*(h/ss)*bpp <= targetMemBytes
        var sample = 1
        while ((w / sample).toLong() * (h / sample).toLong() * bpp > targetMemBytes) {
            sample *= 2
        }

        // If you want roughly "1/10 size", uncomment:
        // sample = 8 /* or 16 */  // powers of two are safest across devices

        // Try decoding with progressive fallback on OOM
        var trySample = max(1, sample)
        while (trySample <= 128) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = trySample
                    inPreferredConfig = preferredConfig
                    inDither = true
                }
                return@withContext BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (oom: OutOfMemoryError) {
                trySample *= 2
            }
        }
        null
    }

    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}