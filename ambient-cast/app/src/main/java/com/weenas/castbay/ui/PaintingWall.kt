package com.weenas.castbay.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The wall behind every menu screen: famous public-domain paintings from the bundled catalog
 * (assets/catalog.json), one at a time, changing every two minutes with a slow dissolve, under an even [dim]
 * veil. Images come from Wikimedia Commons thumbnails, are cached on disk and decoded no larger than the
 * screen needs; without a network the screen simply stays dark.
 */
@Composable
fun PaintingWall(dim: Float = 0f) {
    val context = LocalContext.current
    val works = remember { IdleArt.catalog(context).shuffled() }
    var index by remember { mutableIntStateOf(0) }
    val image by produceState<ImageBitmap?>(null, index) {
        value = withContext(Dispatchers.IO) {
            works.getOrNull(index % works.size.coerceAtLeast(1))?.let { IdleArt.load(context, it.url) }
        }?.asImageBitmap() ?: value
    }
    LaunchedEffect(works) { while (works.isNotEmpty()) { delay(120_000); index++ } }
    Crossfade(targetState = image, animationSpec = tween(if (com.weenas.castbay.ui.Perf.light(context)) 600 else 2500), label = "wall") { shown ->
        if (shown != null) {
            Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        }
    }
    if (dim > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
}

/** The bundled catalog and a disk cache for the paintings of [IdleWall]. */
internal object IdleArt {
    class Work(val title: String, val artist: String, val year: String, val url: String)

    fun catalog(context: android.content.Context): List<Work> = runCatching {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val array = org.json.JSONArray(text)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Work(o.optString("title"), o.optString("artist"), o.optString("year"), o.optString("url"))
        }.filter { it.url.isNotEmpty() }
    }.getOrDefault(emptyList())

    /** The painting at [url]: from the cache, else downloaded once; at most about the screen's size. */
    fun load(context: android.content.Context, url: String): android.graphics.Bitmap? = runCatching {
        val file = java.io.File(context.cacheDir, "wall_" + Integer.toHexString(url.hashCode()) + ".img")
        if (!file.exists()) {
            val tmp = java.io.File(file.path + ".part")
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 40_000
            connection.setRequestProperty("User-Agent", "AmbientCast/1.0 (personal Fire TV receiver)")
            try {
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            if (!tmp.renameTo(file)) return@runCatching null
            trim(context)
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2
        android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Keeps the cache near 100 MB: these devices have little storage. */
    private fun trim(context: android.content.Context) {
        val files = context.cacheDir.listFiles { _, n -> n.startsWith("wall_") && n.endsWith(".img") } ?: return
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }.dropLast(1)) {
            if (total <= 100L * 1024 * 1024) break
            total -= f.length(); f.delete()
        }
    }
}

