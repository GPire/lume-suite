package com.weenas.castbay.ui

import android.app.ActivityManager
import android.content.Context

/**
 * What this TV can afford, decided once from the device itself (no setting to find): old 1 GB sets and
 * anything the system calls low-RAM get calmer motion; everything else gets the full set. Same rule as the
 * launcher's Essential / Balanced / Full profiles.
 */
object Perf {
    @Volatile private var cached: Boolean? = null

    fun light(context: Context): Boolean = cached ?: run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        (am.isLowRamDevice || am.memoryClass <= 128).also { cached = it }
    }
}
