package com.weenas.castbay.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * What this TV is and what it can afford, read from the device itself. The same three tiers as TV Doctor's
 * "essenziale (up to 2.2 GB) / equilibrato (up to 3.3 GB) / pieno" and Hume's motion default, so every part of the
 * product adapts the same way: calm motion and small caches on a stick or an older set, everything where there is room.
 */
enum class Tier { ESSENTIAL, BALANCED, FULL }

class DeviceProfile private constructor(
    val model: String,
    val system: String,
    val ramMb: Int,
    val tier: Tier,
    val isFire: Boolean
) {
    companion object {
        @Volatile private var cached: DeviceProfile? = null

        fun of(context: Context): DeviceProfile = cached ?: run {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ram = (info.totalMem / 1048576L).toInt()
            val fire = Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
            val tier = when {
                am.isLowRamDevice || ram <= 2200 -> Tier.ESSENTIAL
                ram <= 3300 -> Tier.BALANCED
                else -> Tier.FULL
            }
            val name = (if (Build.MODEL.startsWith(Build.MANUFACTURER, true)) Build.MODEL else "${Build.MANUFACTURER} ${Build.MODEL}")
            DeviceProfile(name, (if (fire) "Fire OS" else "Android TV") + " (Android ${Build.VERSION.RELEASE})", ram, tier, fire).also { cached = it }
        }
    }
}
