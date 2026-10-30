package org.lumesuite.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

/**
 * Measures the TV box and picks the settings that suit it: how much motion, how big the artwork is
 * decoded, how large the picture cache may grow. Everything here is something an app may do on its own
 * (no system settings are touched): a small stick gets a lean profile, a strong box gets the full show.
 */
final class Optimizer {
    static final int ESSENTIAL = 0, BALANCED = 1, FULL = 2;
    private static final String PREFS = "optimizer";

    private Optimizer() {
    }

    static final class Report {
        long ramMb, freeRamMb, storageFreeMb;
        int cores, tier;
        String hardware = "";
        boolean ethernet, wifi;
        int rssi, linkMbps, freqMhz;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Report measure(Context c) {
        Report r = new Report();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        r.ramMb = mi.totalMem / (1024 * 1024);
        r.freeRamMb = mi.availMem / (1024 * 1024);
        try {
            r.storageFreeMb = new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes() / (1024 * 1024);
        } catch (Exception ignored) {
            // leave 0: the cache then stays at its minimum
        }
        r.cores = Runtime.getRuntime().availableProcessors();
        r.hardware = Build.MANUFACTURER + " " + Build.MODEL;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni != null && ni.getType() == ConnectivityManager.TYPE_ETHERNET) r.ethernet = true;
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            // Fire OS 11 reports no network id to an app without location access; a real link has a frequency and a level.
            android.net.NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            boolean onWifi = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            if (wi != null && (wi.getNetworkId() != -1 || onWifi || (wi.getFrequency() > 0 && wi.getRssi() > -127))) {
                r.wifi = true;
                r.rssi = wi.getRssi();
                r.linkMbps = wi.getLinkSpeed();
                r.freqMhz = Build.VERSION.SDK_INT >= 21 ? wi.getFrequency() : 0;
            }
        } catch (Exception ignored) {
            // no network information: the page just says so
        }
        r.tier = r.ramMb <= 2200 ? ESSENTIAL : r.ramMb <= 3300 ? BALANCED : FULL;
        return r;
    }

    /** Applies the profile: motion level, decoded artwork size, cache cap. Safe to run again. */
    static void apply(Context c, Report r) {
        int cacheMb = (int) Math.max(40, Math.min(240, r.storageFreeMb / 12));   // about 8% of what is free
        prefs(c).edit().putBoolean("auto", true).putInt("tier", r.tier).putInt("cache_mb", cacheMb)
                .putInt("hero_px", r.tier == ESSENTIAL ? 960 : 1280).apply();
        Motion.set(c, r.tier == FULL ? Motion.FULL : Motion.REDUCED);
    }

    /** Back to the generous defaults (the person switched the automatic profile off). */
    static void release(Context c) {
        prefs(c).edit().putBoolean("auto", false).putInt("cache_mb", 120).putInt("hero_px", 1280).apply();
        Motion.set(c, Motion.FULL);
    }

    static boolean auto(Context c) {
        return prefs(c).getBoolean("auto", true);
    }

    static long cacheLimit(Context c) {
        return prefs(c).getInt("cache_mb", 120) * 1024L * 1024L;
    }

    static int heroPx(Context c) {
        return prefs(c).getInt("hero_px", 1280);
    }

    /** Amazon's promotional apps still switched on, for a Fire TV (-1 elsewhere). TV Doctor on a computer turns them off. */
    static int fireExtrasOn(Context c) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("Amazon")) return -1;
        String[] extras = {"com.amazon.shoptv.firetv.client", "com.amazon.shoptv.client", "com.amazon.ods.kindleconnect",
                "com.amazon.bueller.music", "com.amazon.dummy.music", "com.amazon.bueller.photos", "com.amazon.minitv.android.app",
                "com.amazon.tahoe", "com.amazon.gamehub", "com.amazon.tv.website_launcher", "com.amazon.smarthomemapviewapp",
                "com.amazon.storm.lightning.tutorial", "com.amazon.tmm.tutorial", "com.amazon.tv.releasenotes", "com.amazon.sneakpeek"};
        int on = 0;
        for (String pkg : extras) {
            try {
                if (c.getPackageManager().getApplicationInfo(pkg, 0).enabled) on++;
            } catch (Exception notInstalled) {
                // not on this TV
            }
        }
        return on;
    }

    static String tierName(int tier) {
        return It.t(tier == ESSENTIAL ? "Essential" : tier == BALANCED ? "Balanced" : "Full");
    }

    static String gb(long mb) {
        return String.format(java.util.Locale.getDefault(), "%.1f GB", mb / 1024.0);
    }

    /** One honest line about the connection, with advice only when there is something to improve. */
    static String link(Report r) {
        if (r.ethernet) return It.t("Ethernet cable: the most stable choice");
        if (!r.wifi) return It.t("No connection detected");
        String band = r.freqMhz >= 4900 ? "5 GHz" : "2.4 GHz";
        String q = It.t(r.rssi >= -55 ? "excellent" : r.rssi >= -67 ? "good" : r.rssi >= -75 ? "weak" : "very weak");
        String speed = r.linkMbps > 0 ? ", " + r.linkMbps + " Mbps" : "";
        String s = It.f("Wi-Fi %s%s, signal %s", band, speed, q);
        if (r.freqMhz > 0 && r.freqMhz < 4900) s += It.t(". If your router has a 5 GHz network, use it");
        else if (r.rssi < -67) s += It.t(". Move the router closer or use an extender");
        return s;
    }
}
