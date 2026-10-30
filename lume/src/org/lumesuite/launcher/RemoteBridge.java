package org.lumesuite.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What the phone page can ask of the TV. The web server (present.AdminServer) handles pairing and the PIN; this class does
 * the asking: the state to show, and a small set of actions that all go through the Home the person already uses (its keys,
 * its search, its settings), so the phone can never do more than the remote in the hand could.
 */
public final class RemoteBridge {
    private RemoteBridge() {
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static String stateJson(Context c) throws Exception {
        JSONObject j = new JSONObject();
        MainActivity a = MainActivity.activeInstance;
        j.put("name", android.os.Build.MODEL);
        j.put("onHome", a != null && a.resumed);
        j.put("keysEverywhere", PowerService.instance != null);   // Home and Back reach other apps only with the Home-key switch on
        j.put("look", "classic".equals(Profiles.homePrefs(c).getString("home_look", "scene")) ? "classic" : "scene");
        String wall = Profiles.homePrefs(c).getString("wallpaper", "galleria");
        JSONArray walls = new JSONArray();
        for (int i = 0; i < MainActivity.WALL_KEYS.length; i++) {
            walls.put(new JSONObject().put("key", MainActivity.WALL_KEYS[i]).put("name", MainActivity.WALL_NAMES[i])
                    .put("current", MainActivity.WALL_KEYS[i].equals(wall)));
        }
        j.put("wallpapers", walls);
        j.put("apps", apps(c));
        return j.toString();
    }

    /** Every app that can be opened from a TV, by name; Lume itself is left out. */
    private static JSONArray apps(Context c) throws Exception {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<String[]> rows = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || MainActivity.AUTO_HIDE.contains(pkg) || !seen.add(pkg)) continue;   // the same internal Amazon apps the Home hides
            rows.add(new String[]{String.valueOf(ri.loadLabel(pm)), pkg});
        }
        Collections.sort(rows, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        JSONArray out = new JSONArray();
        for (String[] r : rows) out.put(new JSONObject().put("label", r[0]).put("pkg", r[1]));
        return out;
    }

    /** A small PNG of an app's icon for the phone's grid (null for an unknown app). */
    public static byte[] icon(Context c, String pkg) {
        try {
            if (pkg == null || !pkg.matches("[A-Za-z0-9._]{1,200}")) return null;
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            int size = 96;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, size, size);
            d.draw(new Canvas(b));
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 90, o);
            b.recycle();
            return o.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** One action from the phone: {"action": "...", ...}. Always answers, never throws. */
    public static JSONObject act(Context c, JSONObject req) {
        JSONObject res = new JSONObject();
        try {
            String action = req.optString("action");
            boolean ok = false;
            String error = null;
            MainActivity a = MainActivity.activeInstance;
            boolean onHome = a != null && a.resumed;
            switch (action) {
                case "key": {
                    String k = req.optString("key");
                    int code = keyCode(k);
                    if (code == 0) { error = "unknown key"; break; }
                    if (onHome) {
                        MAIN.post(() -> {
                            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
                        });
                        ok = true;
                    } else if (k.equals("back") && PowerService.instance != null) {
                        ok = PowerService.instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    } else {
                        error = "Lume is not on screen";
                    }
                    break;
                }
                case "home": {
                    c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    ok = true;
                    break;
                }
                case "launch": {
                    String pkg = req.optString("pkg");
                    PackageManager pm = c.getPackageManager();
                    Intent i = pkg.matches("[A-Za-z0-9._]{1,200}") ? pm.getLeanbackLaunchIntentForPackage(pkg) : null;
                    if (i == null && pkg.matches("[A-Za-z0-9._]{1,200}")) i = pm.getLaunchIntentForPackage(pkg);
                    if (i == null) { error = "app not found"; break; }
                    RecentAppsOverlay.recordAppUsed(c, pkg);
                    c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    ok = true;
                    break;
                }
                case "search": {
                    String q = req.optString("text").trim();
                    if (q.isEmpty() || q.length() > 200) { error = "empty text"; break; }
                    c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra("panel", "search").putExtra("q", q));
                    ok = true;
                    break;
                }
                case "wallpaper": {
                    String key = req.optString("key");
                    if (!java.util.Arrays.asList(MainActivity.WALL_KEYS).contains(key)) { error = "unknown background"; break; }
                    Profiles.homePrefs(c).edit().putString("wallpaper", key).apply();
                    if (a != null) MAIN.post(a::recreate);
                    ok = true;
                    break;
                }
                case "look": {
                    String v = "classic".equals(req.optString("value")) ? "classic" : "scene";
                    Profiles.homePrefs(c).edit().putString("home_look", v).apply();
                    if (a != null) MAIN.post(a::recreate);
                    ok = true;
                    break;
                }
                case "open": {
                    String what = req.optString("screen");
                    Class<?> k = what.equals("health") ? HealthActivity.class : what.equals("markets") ? MarketsActivity.class
                            : what.equals("news") ? NewsActivity.class : what.equals("weather-cities") ? WeatherCitiesActivity.class
                            : what.equals("settings") ? SettingsActivity.class : what.equals("airplay") ? AirPlayActivity.class : null;
                    if (k != null) {
                        c.startActivity(new Intent(c, k).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        ok = true;
                    } else if (what.equals("cc") || what.equals("recents") || what.equals("weather")) {
                        c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra("panel", what));
                        ok = true;
                    } else {
                        error = "unknown screen";
                    }
                    break;
                }
                case "sleep":
                    ok = SleepReceiver.sleepNow(c);
                    if (!ok) error = "Power Off needs its permission on the TV (Settings > Permissions)";
                    break;
                default:
                    error = "unknown action";
            }
            res.put("ok", ok);
            if (error != null) res.put("error", error);
        } catch (Exception e) {
            try {
                res.put("ok", false).put("error", String.valueOf(e.getMessage()));
            } catch (Exception ignored) {
            }
        }
        return res;
    }

    private static int keyCode(String k) {
        switch (k.toLowerCase(Locale.ROOT)) {
            case "up": return KeyEvent.KEYCODE_DPAD_UP;
            case "down": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "left": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "ok": return KeyEvent.KEYCODE_DPAD_CENTER;
            case "back": return KeyEvent.KEYCODE_BACK;
            case "menu": return KeyEvent.KEYCODE_MENU;
            default: return 0;
        }
    }
}
