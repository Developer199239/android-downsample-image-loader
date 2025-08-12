# Downsample Image Loader (Android)

A tiny Kotlin sample that downloads images and decodes them with `BitmapFactory.inSampleSize` to hit a memory target (e.g., 0.1–0.5 MB). It uses coroutines, OkHttp, an in-memory `LruCache`, and a simple disk cache, and displays images in a `RecyclerView`.

- Avoids OOM by sizing the bitmap during decode.
- Logs before/after sizes (file size, estimated RAM, actual decoded size).
- Toggle RGB_565 vs ARGB_8888.

## Features
- Memory-targeted decoding (`targetMemBytes`)
- RGB_565 option (2 B/px) to halve RAM vs ARGB_8888 (4 B/px)
- LRU memory cache + disk cache
- Coroutine-based IO
- RecyclerView demo with placeholders and progress

## How it works
1. Download image to app cache via OkHttp (URL hashed to filename).
2. First decode pass (`inJustDecodeBounds=true`) reads width/height without allocating.
3. Compute `inSampleSize` so `(w/ss) * (h/ss) * bytesPerPixel <= targetMemBytes`.
4. Decode with the chosen sample; retry with a larger sample if OOM.
5. Cache bitmap in memory; reuse disk file for future loads.

Important: Downsampling reduces in-memory size, not the network payload. You still download the original file.

## Quick start
- minSdk: 23
- compileSdk: 34
- Open in Android Studio and Run.

```kotlin
// Tune memory target (e.g., 0.1 MB)
val imageLoader = ImageLoader(
    applicationContext,
    targetMemBytes = 100 * 1024, // ~100 KB
    useRgb565 = true             // halves memory; no alpha
)