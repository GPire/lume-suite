package org.lumesuite.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Is there a newer Lume? One small file on the project page says which version is current; Lume asks for it only when the
 * About page is open (never in the background, never on its own) and tells the person. It installs nothing: Android wants
 * the person to approve every install, and a launcher that updates itself unasked is exactly what this project argues against.
 */
final class UpdateCheck {
    private UpdateCheck() {
    }

    static final String URL_LATEST = "https://gpire.github.io/lume-suite/latest.json";
    static final String PAGE = "gpire.github.io/lume-suite/tv";

    /** null: not asked yet; "" up to date; "?" could not ask; otherwise the newer version's name. */
    static volatile String newer;
    private static volatile boolean busy;

    static void check(Context c, Runnable done) {
        if (busy) return;
        busy = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            String result = "?";
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(URL_LATEST).openConnection();
                h.setConnectTimeout(6000);
                h.setReadTimeout(6000);
                h.setRequestProperty("User-Agent", "Lume");
                try (InputStream in = h.getInputStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                    JSONObject j = new JSONObject(sc.hasNext() ? sc.next() : "{}");
                    JSONObject lume = j.getJSONObject("lume");
                    int code = lume.getInt("code");
                    int mine = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionCode;
                    result = code > mine ? lume.getString("version") : "";
                }
            } catch (Exception ignored) {
            }
            newer = result;
            busy = false;
            new Handler(Looper.getMainLooper()).post(done);
        }, "lume-update-check").start();
    }

    static String describe() {
        String n = newer;
        if (n == null) return It.t("Checking…");
        if (n.isEmpty()) return It.t("Up to date");
        if (n.equals("?")) return It.t("Could not check");
        return It.t("New version") + " " + n;
    }
}
