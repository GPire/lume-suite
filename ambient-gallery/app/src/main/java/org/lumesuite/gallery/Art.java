package org.lumesuite.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Catalogo incorporato di dipinti di pubblico dominio e scaricamento con cache su disco. */
final class Art {
    static final String UA = "AmbientArt/0.3 (personal Fire TV screensaver)";

    static final class Work {
        final String title, artist, year, theme, url;
        Work(JSONObject o) {
            title = o.optString("title"); artist = o.optString("artist"); year = o.optString("year");
            theme = o.optString("theme"); url = o.optString("url");
        }
    }

    static List<Work> catalog(Context c) {
        List<Work> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("catalog.json")) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            JSONArray a = new JSONArray(o.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) out.add(new Work(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        Collections.shuffle(out);
        return out;
    }

    /** Immagine in piena qualita' (ARGB_8888), ridotta solo quanto serve allo schermo. Cache su disco. */
    static Bitmap load(Context c, Work w, int screenW, int screenH) {
        try {
            File f = new File(c.getCacheDir(), "art_" + Integer.toHexString(w.url.hashCode()) + ".img");
            if (!f.exists()) {
                HttpURLConnection h = (HttpURLConnection) new URL(w.url).openConnection();
                h.setConnectTimeout(10000); h.setReadTimeout(40000);
                h.setRequestProperty("User-Agent", UA);
                File tmp = new File(f.getPath() + ".part");
                try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] b = new byte[16384]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                } finally { h.disconnect(); }
                if (!tmp.renameTo(f)) return null;
                trimCache(c);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = new FileInputStream(f)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (o.outWidth / (s * 2) >= screenW && o.outHeight / (s * 2) >= screenH) s *= 2;
            o.inJustDecodeBounds = false; o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = new FileInputStream(f)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Throwable e) { return null; }
    }

    /** Tiene la cache sotto circa 120 MB: la stick ha poco spazio. */
    private static void trimCache(Context c) {
        File[] fs = c.getCacheDir().listFiles((d, n) -> n.startsWith("art_") && n.endsWith(".img"));
        if (fs == null) return;
        long total = 0; for (File x : fs) total += x.length();
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; total > 120L * 1024 * 1024 && i < fs.length - 1; i++) { total -= fs[i].length(); fs[i].delete(); }
    }
}
