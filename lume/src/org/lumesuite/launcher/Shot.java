package org.lumesuite.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;

/**
 * Developer aid: `am start ... --es shot name` saves what the screen really shows to
 * Download/lume-shots/shot_name.png. Fire OS blacks out the system screenshot, so this is
 * the only way to check a layout without a photo. Nothing happens without the extra.
 */
final class Shot {
    private Shot() {
    }

    static void maybe(Activity a, Intent i) {
        String name = i == null ? null : i.getStringExtra("shot");
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,40}")) return;
        Handler h = new Handler(Looper.getMainLooper());
        String walk = i.getStringExtra("walk");  // "DPAD_DOWN,DPAD_CENTER,@page2,BACK": keys go to this screen, @name takes a picture
        if (walk == null) {
            h.postDelayed(() -> save(a, name), i.getIntExtra("shot_delay", 1800));
            return;
        }
        long t = i.getIntExtra("shot_delay", 1800);
        for (String step : walk.split(",")) {
            final String st = step.trim();
            t += st.startsWith("@") ? 900 : 700;
            if (st.startsWith("@")) h.postDelayed(() -> save(a, name + "_" + st.substring(1)), t);
            else h.postDelayed(() -> key(a, st), t);
        }
    }

    private static void key(Activity a, String name) {
        int code = android.view.KeyEvent.keyCodeFromString("KEYCODE_" + name);
        if (code == android.view.KeyEvent.KEYCODE_UNKNOWN) return;
        a.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code));
        a.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code));
    }

    private static void save(Activity a, String name) {
        View v = a.getWindow().getDecorView();
        if (v.getWidth() == 0 || v.getHeight() == 0) return;
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(a.getWindow(), b, r -> write(a, name, b, r == PixelCopy.SUCCESS), new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            write(a, name, b, false);
        }
    }

    private static void write(Activity a, String name, Bitmap b, boolean copied) {
        if (!copied) {  // fall back to a software draw of the view tree
            b.eraseColor(0xFF000000);
            a.getWindow().getDecorView().draw(new android.graphics.Canvas(b));
        }
        String file = "shot_" + name + (copied ? "" : "_soft") + ".png";
        try {  // Downloads/lume-shots: readable with adb and needs no storage permission (API 29+)
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file);
            cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png");
            cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/lume-shots");
            android.net.Uri u = a.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) return;
            try (java.io.OutputStream o = a.getContentResolver().openOutputStream(u)) {
                b.compress(Bitmap.CompressFormat.PNG, 90, o);
            }
        } catch (Exception ignored) {
        }
    }
}
