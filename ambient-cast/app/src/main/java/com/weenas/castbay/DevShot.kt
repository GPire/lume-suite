package com.weenas.castbay

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy

/**
 * Developer aid: `am start ... --es shot name [--ei shot_delay ms]` saves what the screen really shows to
 * Download/lume-shots/shot_name.png. Fire OS blanks the system screenshot, so this is how a layout is checked
 * without a photo. Does nothing without the extra.
 */
@android.annotation.TargetApi(29)
object DevShot {
    fun maybe(activity: Activity) {
        val name = activity.intent?.getStringExtra("shot")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,40}")) } ?: return
        if (Build.VERSION.SDK_INT < 29) return
        val delay = activity.intent.getIntExtra("shot_delay", 2500).toLong()
        val handler = Handler(Looper.getMainLooper())
        // One picture, or a few over time (--ei shot_count 4 --ei shot_every 1500).
        val count = activity.intent.getIntExtra("shot_count", 1)
        val every = activity.intent.getIntExtra("shot_every", 1500).toLong()
        for (i in 0 until count) {
            handler.postDelayed({ save(activity, if (count == 1) name else "${name}_$i") }, delay + i * every)
        }
    }

    private fun save(activity: Activity, name: String) {
        val view = activity.window.decorView
        if (view.width == 0 || view.height == 0) return
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, bitmap, { result ->
            if (result != PixelCopy.SUCCESS) return@request
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "shot_$name.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/lume-shots")
            }
            val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@request
            // Compressing a full-screen picture takes seconds on a stick: never on the main thread, or the screen stutters under test.
            Thread { activity.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) } }.start()
        }, Handler(Looper.getMainLooper()))
    }
}
