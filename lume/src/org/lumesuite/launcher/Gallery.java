package org.lumesuite.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Wallpaper "Galleria d'arte": one famous public-domain painting at a time, picked by time of day and
 * changed every hour. The catalog ships in assets (no API calls on the TV); images come from Wikimedia
 * Commons thumbnails and are cached on disk. Nothing here touches the network on the main thread.
 */
final class Gallery {
    static final String KEY = "galleria";
    private static final String UA = "Lume/1.0 (TV launcher)";

    private static Context app;
    private static volatile boolean fetching;

    private Gallery() {
    }

    static void init(Context c) {
        app = c.getApplicationContext();
    }

    static final class Work {
        final String title, artist, year, theme, url;

        Work(JSONObject o) {
            title = o.optString("title");
            artist = o.optString("artist");
            year = o.optString("year");
            theme = o.optString("theme");
            url = o.optString("url");
        }
    }

    private static List<Work> catalog;

    private static synchronized List<Work> catalog() {
        if (catalog != null) return catalog;
        List<Work> out = new ArrayList<>();
        try (InputStream in = app.getAssets().open("catalog.json")) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            JSONArray a = new JSONArray(o.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) out.add(new Work(a.getJSONObject(i)));
        } catch (Exception ignored) {
            // an empty catalog just means the wallpaper falls back to Aurora
        }
        catalog = out;
        return out;
    }

    /** Carousel items for the top shelf: this hour's mood first, then the rest of the collection. */
    static List<Suggestions.Item> items() {
        List<Suggestions.Item> out = new ArrayList<>();
        if (app == null) return out;
        List<Work> all = catalog();
        String[][] themes = {{"paesaggio", "natura", "mare"}, {"mare", "citta", "paesaggio"}, {"citta", "ritratto", "paesaggio"}};
        List<Work> ordered = new ArrayList<>(), rest = new ArrayList<>();
        for (Work w : all) {
            boolean mood = false;
            for (String t : themes[phase()]) if (w.theme.equals(t)) mood = true;
            (mood ? ordered : rest).add(w);
        }
        // rotate within the mood by the hour so the shelf does not always start with the same painting
        if (!ordered.isEmpty()) java.util.Collections.rotate(ordered, -(slot() % ordered.size()));
        ordered.addAll(rest);
        String[] label = {"Landscape", "Sea", "City", "Nature", "Portrait"};
        String[] keys = {"paesaggio", "mare", "citta", "natura", "ritratto"};
        for (Work w : ordered) {
            Suggestions.Item it = new Suggestions.Item();
            it.id = w.url.hashCode();
            it.providerId = Suggestions.ART_ID;
            it.type = "art";
            it.title = w.title;
            it.overview = w.artist;
            it.backdrop = w.url;
            it.logo = "";
            it.year = w.year;
            it.genres = "";
            for (int i = 0; i < keys.length; i++) if (keys[i].equals(w.theme)) it.genres = It.t(label[i]);
            out.add(it);
        }
        return out;
    }

    /** A number that changes every hour: the wallpaper rotates on it. */
    static int slot() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.DAY_OF_YEAR) * 24 + c.get(Calendar.HOUR_OF_DAY);
    }

    /** 0 = day (8-18), 1 = evening (18-23), 2 = night. */
    static int phase() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 8 && h < 18 ? 0 : h >= 18 && h < 23 ? 1 : 2;
    }

    /** The painting for this hour: day favours landscapes, evening sea and cities, night calm scenes. */
    static Work current() {
        if (app == null) return null;
        List<Work> all = catalog();
        if (all.isEmpty()) return null;
        String[][] themes = {{"paesaggio", "natura", "mare"}, {"mare", "citta", "paesaggio"}, {"citta", "ritratto", "paesaggio"}};
        List<Work> pool = new ArrayList<>();
        for (Work w : all) for (String t : themes[phase()]) if (w.theme.equals(t)) {
            pool.add(w);
            break;
        }
        if (pool.isEmpty()) pool = all;
        return pool.get(Math.abs(slot() * 31 + phase()) % pool.size());
    }

    private static File fileOf(Work w) {
        return new File(app.getCacheDir(), "gal_" + Integer.toHexString(w.url.hashCode()) + ".img");
    }

    static boolean ready() {
        Work w = current();
        return w != null && fileOf(w).exists();
    }

    /** Wallpaper bitmap (16:9, ~1280x720, warmed and dimmed by the hour) or null if not cached yet. */
    static Bitmap wallpaper() {
        Work w = current();
        if (w == null) return null;
        File f = fileOf(w);
        if (!f.exists()) {
            fetchAsync(null);
            return null;
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            int px = Optimizer.heroPx(app);
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px * 9 / 16) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            if (src == null) {
                f.delete();
                return null;
            }
            // centre-crop to 16:9, biased slightly upward where paintings keep their subject
            int cw = src.getWidth(), ch = src.getHeight();
            int tw = cw, th = cw * 9 / 16;
            if (th > ch) {
                th = ch;
                tw = ch * 16 / 9;
            }
            int x = (cw - tw) / 2, y = Math.round((ch - th) * 0.35f);
            Bitmap out = Bitmap.createBitmap(src, x, y, tw, th);
            if (out != src) src.recycle();
            Bitmap m = out.isMutable() ? out : out.copy(Bitmap.Config.ARGB_8888, true);
            int p = phase();
            if (p > 0) {
                Paint paint = new Paint();
                paint.setColor(p == 1 ? 0x38301000 : 0x66000000);   // warm and softer in the evening, darker at night
                new Canvas(m).drawRect(0, 0, m.getWidth(), m.getHeight(), paint);
            }
            return m;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Downloads this hour's painting in the background; calls back on a worker thread on success. */
    static void fetchAsync(Runnable onReady) {
        final Work w = current();
        if (w == null || fetching) return;
        final File f = fileOf(w);
        if (f.exists()) {
            if (onReady != null) onReady.run();
            return;
        }
        fetching = true;
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(w.url).openConnection();
                h.setConnectTimeout(10000);
                h.setReadTimeout(40000);
                h.setRequestProperty("User-Agent", UA);
                File tmp = new File(f.getPath() + ".part");
                try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] b = new byte[16384];
                    int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                } finally {
                    h.disconnect();
                }
                if (tmp.renameTo(f)) {
                    trim();
                    if (onReady != null) onReady.run();
                }
            } catch (Throwable ignored) {
                // offline: keep the current wallpaper and try again at the next hour
            } finally {
                fetching = false;
            }
        }, "lume-gallery").start();
    }

    /** Keeps the cache near 120 MB: these sticks have little storage. */
    private static void trim() {
        File[] fs = app.getCacheDir().listFiles((d, n) -> n.startsWith("gal_") && n.endsWith(".img"));
        if (fs == null) return;
        long total = 0;
        for (File x : fs) total += x.length();
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long limit = Optimizer.cacheLimit(app);
        for (int i = 0; i < fs.length - 1 && total > limit; i++) {
            total -= fs[i].length();
            fs[i].delete();
        }
    }
}
