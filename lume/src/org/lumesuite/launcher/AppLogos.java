package org.lumesuite.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Clean 256 px brand squares for the few streaming apps whose own icon is small or busy, so their cards stay sharp on a big
 * screen (the idea and the artwork come from tvOS Launcher, Apache-2.0). They only identify the app already installed on
 * this TV; they are the owners' trademarks and artwork. To ship without them, delete assets/app-logos: every card then falls
 * back to the app's own icon.
 */
final class AppLogos {
    private AppLogos() {
    }

    private static final Map<String, String> FILE = new HashMap<>();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(12);

    static {
        put("netflix.png", "com.netflix.ninja", "com.netflix.mediaclient");
        // SmartTube is not YouTube's own app and keeps its own icon, so the two never look like the same tile.
        put("youtube.png", "com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube.tvkids");
        put("primevideo.png", "com.amazon.firebat", "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient");
        put("disneyplus.png", "com.disney.disneyplus");
        put("appletv.png", "com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv", "com.apple.atve.sony.appletv");
        put("spotify.png", "com.spotify.tv.android", "com.spotify.music");
        put("twitch.png", "tv.twitch.android.app", "tv.twitch.android.viewer");
        put("max.png", "com.wbd.stream", "com.hbo.hbonow");
    }

    private static void put(String file, String... packages) {
        for (String p : packages) FILE.put(p, file);
    }

    /** The logo for this package, or null (also when the folder was left out of the build). */
    static Bitmap get(Context c, String pkg) {
        String file = pkg == null ? null : FILE.get(pkg);
        if (file == null) return null;
        Bitmap b = CACHE.get(file);
        if (b != null) return b;
        try (InputStream in = c.getAssets().open("app-logos/" + file)) {
            b = BitmapFactory.decodeStream(in);
            if (b != null) CACHE.put(file, b);
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
