package com.weenas.castbay.ui

import android.content.Context
import android.net.wifi.WifiManager

/**
 * How good the TV's Wi-Fi link is for AirPlay, from the radio itself (band and signal level; neither needs a
 * location permission). Read now and then, never listened to: nothing runs at rest.
 */
data class WifiQuality(val band: Band, val level: Level) {
    enum class Band { GHZ_2, GHZ_5, GHZ_6 }
    enum class Level { GREAT, GOOD, WEAK }

    companion object {
        @Suppress("DEPRECATION")
        fun read(context: Context): WifiQuality? {
            val info = context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo ?: return null
            val mhz = info.frequency
            if (mhz <= 0 || info.rssi <= -127) return null
            val band = when { mhz < 3000 -> Band.GHZ_2; mhz < 5900 -> Band.GHZ_5; else -> Band.GHZ_6 }
            val level = when { info.rssi >= -60 -> Level.GREAT; info.rssi >= -72 -> Level.GOOD; else -> Level.WEAK }
            return WifiQuality(band, level)
        }
    }
}
