// ImageLoader.kt
package com.murtuzarahman.downsampleimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Log
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

    companion object {
        private const val TAG = "ImageLoader"
    }

    private val client = OkHttpClient()
    private val diskDir = File(context.cacheDir, "img_cache").apply { mkdirs() }

    // 1/8th of available memory for cache
    private val memCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024).toInt() / 8) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(url: String): Bitmap? {
        Log.d(TAG, "====load method calling =${url}")
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
        Log.d(TAG, "====downloadToDisk method calling====")
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
        Log.d(TAG, "====decodeDownsampled method calling====")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) {
            Log.w(TAG, "decodeDownsampled: invalid image bounds for file=${file.absolutePath}")
            return@withContext null
        }

        val preferredConfig = if (useRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val bpp = if (useRgb565) 2 else 4

        // BEFORE: log original info (dimensions, estimated raw mem if decoded at full size, and file size)
        val origPixels = w.toLong() * h.toLong()
        val origMemBytes = origPixels * bpp
        val fileBytes = file.length()
        Log.d(
            TAG,
            "Original: ${w}x${h}, config=$preferredConfig, bpp=$bpp → est RAM ${origMemBytes.prettyBytes()} | file ${fileBytes.prettyBytes()}"
        )

        // Choose inSampleSize so that (w/ss)*(h/ss)*bpp <= targetMemBytes
        var sample = 1
        while ((w / sample).toLong() * (h / sample).toLong() * bpp > targetMemBytes) {
            sample *= 2
        }

        val estW = w / sample
        val estH = h / sample
        val estMem = estW.toLong() * estH.toLong() * bpp
        Log.d(
            TAG,
            "TargetMem=${targetMemBytes.toLong().prettyBytes()} → choose inSampleSize=$sample → est ${estW}x${estH}, est RAM ${estMem.prettyBytes()}"
        )

        // Try decoding with progressive fallback on OOM
        var trySample = max(1, sample)
        while (trySample <= 128) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = trySample
                    inPreferredConfig = preferredConfig
                    inDither = true
                }
                Log.d(TAG, "Decoding with inSampleSize=$trySample ...")
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) {
                    // AFTER: log actual decoded bitmap info
                    Log.d(
                        TAG,
                        "Decoded: ${bmp.width}x${bmp.height}, byteCount=${bmp.byteCount.toLong().prettyBytes()}, config=${bmp.config}, hasAlpha=${bmp.hasAlpha()}, usedSample=$trySample"
                    )
                    return@withContext bmp
                } else {
                    Log.w(TAG, "Decode returned null at sample=$trySample")
                }
            } catch (oom: OutOfMemoryError) {
                Log.w(TAG, "OOM at sample=$trySample; increasing sample and retrying", oom)
                trySample *= 2
                continue
            }
        }
        Log.e(TAG, "Failed to decode bitmap after trying up to inSampleSize=128")
        null
    }

    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Helper to print bytes nicely (B, KB, MB, GB)
    private fun Long.prettyBytes(): String {
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            this >= gb -> String.format("%.2f GB", this / gb.toFloat())
            this >= mb -> String.format("%.2f MB", this / mb.toFloat())
            this >= kb -> String.format("%.2f KB", this / kb.toFloat())
            else -> "$this B"
        }
    }
}