package org.lumesuite.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps a small TV box smooth without being asked. When the Home comes back to the front and free memory is low, apps that are no
 * longer in use are closed in the background (the same normal permission as "Free up memory": nothing is uninstalled and an app opens
 * again when needed). It never runs while music or video is playing, never closes the Home, the Lume apps or the app on screen, and
 * waits at least four minutes between runs. The person can switch it off in Settings > Home.
 */
final class AutoMemory {
    private AutoMemory() {
    }

    private static final long GAP_MS = 4 * 60_000L;
    /** Below this share of the total, memory counts as low. */
    private static final double LOW = 0.30;
    private static long lastRun;

    static boolean enabled(Context c) {
        return Optimizer.prefs(c).getBoolean("auto_memory", true);
    }

    static void set(Context c, boolean on) {
        Optimizer.prefs(c).edit().putBoolean("auto_memory", on).apply();
    }

    /** MB freed by the last automatic run (for the Health page), or -1. */
    static long lastFreedMb(Context c) {
        return Optimizer.prefs(c).getLong("auto_memory_freed", -1);
    }

    static long[] mem() {
        long total = 0, avail = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("MemTotal:")) total = kb(l);
                else if (l.startsWith("MemAvailable:")) avail = kb(l);
            }
        } catch (Exception ignored) {
        }
        return new long[]{total / 1024, avail / 1024};
    }

    private static long kb(String l) {
        return Long.parseLong(l.replaceAll("[^0-9]", ""));
    }

    /** Call when the Home is on screen. Cheap: reads two numbers and usually returns. */
    static void maybeRun(Context c) {
        if (!enabled(c)) return;
        long now = SystemClock.elapsedRealtime();
        if (lastRun != 0 && now - lastRun < GAP_MS) return;
        long[] m = mem();
        if (m[0] <= 0 || m[1] > m[0] * LOW) return;   // plenty free: leave everything alone
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && am.isMusicActive()) return;   // something is playing: never touch it
        lastRun = now;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            long before = mem()[1];
            ActivityManager act = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            Set<String> pkgs = new HashSet<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo ri : app.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    pkgs.add(ri.activityInfo.packageName);
                }
            }
            pkgs.remove(app.getPackageName());
            pkgs.remove("com.amazon.tv.launcher");
            pkgs.remove("org.lumesuite.cast");      // the AirPlay receiver must stay ready
            pkgs.remove("org.lumesuite.gallery");   // the screensaver
            for (String p : pkgs) {
                try {
                    act.killBackgroundProcesses(p);   // background processes only: a foreground app or service is not touched
                } catch (Throwable ignored) {
                }
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            long freed = Math.max(0, mem()[1] - before);
            Optimizer.prefs(app).edit().putLong("auto_memory_freed", freed).apply();
        }, "lume-auto-memory").start();
    }
}
