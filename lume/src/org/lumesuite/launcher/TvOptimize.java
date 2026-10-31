package org.lumesuite.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cleans this TV from the TV: the same lists as TV Doctor (assets/optimize.json, exported from tv-doctor/tvdoctor.py), the same
 * rules (small groups, a health check after each, the last group turned back on at the first doubt, nothing ever uninstalled).
 * Apps are only switched off with `pm disable-user`; the list is kept so Undo turns them on again.
 */
final class TvOptimize {
    private TvOptimize() {
    }

    static final class Plan {
        final List<String> safe = new ArrayList<>();
        String home = "";
    }

    /** The plan the guided wizard made, handed from its "allow" page to its "choose" page. */
    static Plan current;

    /** Which thing a person may want to keep an app for (voice assistant, casting...), or null. */
    static String group(Context c, String pkg) {
        try {
            JSONArray gs = lists(c).getJSONArray("groups");
            for (int i = 0; i < gs.length(); i++) {
                JSONObject g = gs.getJSONObject(i);
                JSONArray w = g.getJSONArray("words");
                for (int k = 0; k < w.length(); k++) if (pkg.toLowerCase(Locale.ROOT).contains(w.getString(k))) return g.getString("id");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static boolean keepByDefault(Context c, String id) {
        try {
            JSONArray gs = lists(c).getJSONArray("groups");
            for (int i = 0; i < gs.length(); i++) if (gs.getJSONObject(i).getString("id").equals(id)) return gs.getJSONObject(i).getBoolean("keep");
        } catch (Exception ignored) {
        }
        return false;
    }

    static final class Result {
        int done;
        boolean rolledBack;
    }

    private static JSONObject lists(Context c) throws Exception {
        try (InputStream in = c.getAssets().open("optimize.json")) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return new JSONObject(b.toString("UTF-8"));
        }
    }

    private static boolean never(JSONObject nv, String p) throws Exception {
        JSONArray ex = nv.getJSONArray("exact"), pre = nv.getJSONArray("prefix"), words = nv.getJSONArray("words");
        for (int i = 0; i < ex.length(); i++) if (ex.getString(i).equals(p)) return true;
        for (int i = 0; i < pre.length(); i++) if (p.startsWith(pre.getString(i))) return true;
        for (int i = 0; i < words.length(); i++) if (p.contains(words.getString(i))) return true;
        return false;
    }

    private static Set<String> packages(String out) {
        Set<String> s = new HashSet<>();
        for (String l : out.split("\n")) if (l.startsWith("package:")) s.add(l.substring(8).trim());
        return s;
    }

    static String homePackage(AdbClient a) throws Exception {
        String h = a.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1");
        int i = h.indexOf('/');
        return i > 0 ? h.substring(0, i).trim() : "";
    }

    /** What could be switched off on this TV, from the curated safe lists for its system and brand. */
    static Plan plan(Context c, AdbClient a) throws Exception {
        JSONObject all = lists(c), nv = all.getJSONObject("never");
        Set<String> system = packages(a.shell("pm list packages -s")), off = packages(a.shell("pm list packages -d"));
        Plan plan = new Plan();
        plan.home = homePackage(a);
        List<JSONObject> sets = new ArrayList<>();
        String maker = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        if (maker.contains("amazon")) sets.add(all.getJSONObject("fire").getJSONObject("safe"));
        else sets.add(all.getJSONObject("google").getJSONObject("safe"));
        JSONObject brand = all.getJSONObject("brand");
        for (java.util.Iterator<String> it = brand.keys(); it.hasNext(); ) {
            String k = it.next();
            if (maker.contains(k)) sets.add(brand.getJSONObject(k).getJSONObject("safe"));
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JSONObject set : sets) {
            for (java.util.Iterator<String> it = set.keys(); it.hasNext(); ) {
                String p = it.next();
                if (system.contains(p) && !off.contains(p) && !p.equals(plan.home) && !never(nv, p)) seen.add(p);
            }
        }
        plan.safe.addAll(seen);
        return plan;
    }

    private static boolean healthy(AdbClient a, String home) {
        try {
            if (!"1".equals(a.shell("getprop sys.boot_completed"))) return false;
            return home.isEmpty() || homePackage(a).equals(home);
        } catch (Exception e) {
            return false;
        }
    }

    /** Four at a time, a health check after each group; the first doubt turns that group back on and stops. */
    static Result apply(Context c, AdbClient a, Plan plan, List<String> pkgs) throws Exception {
        Result r = new Result();
        SharedPreferences sp = c.getSharedPreferences("optimize", Context.MODE_PRIVATE);
        Set<String> saved = new LinkedHashSet<>(sp.getStringSet("disabled", new HashSet<>()));
        for (int i = 0; i < pkgs.size(); i += 4) {
            List<String> group = pkgs.subList(i, Math.min(i + 4, pkgs.size()));
            List<String> done = new ArrayList<>();
            for (String p : group) {
                if (!p.matches("[A-Za-z0-9._]{1,200}")) continue;
                try {
                    String res = a.shell("pm disable-user --user 0 " + p);
                    if (res.contains("new state: disabled")) {
                        done.add(p);
                        saved.add(p);   // written at once: if anything stops here, Undo still knows
                        sp.edit().putStringSet("disabled", new HashSet<>(saved)).apply();
                    }
                } catch (java.io.IOException e) {
                    android.util.Log.w("LumeOptimize", "disable failed: " + p, e);
                    r.rolledBack = true;   // the TV stopped answering in the middle of a group: put the group back
                    break;
                }
            }
            boolean ok = !r.rolledBack && healthy(a, plan.home);
            if (!ok) {
                android.util.Log.w("LumeOptimize", "group rolled back (health check failed or the TV stopped answering)");
                for (String p : done) {
                    try {
                        a.shell("pm enable " + p);
                        saved.remove(p);
                    } catch (java.io.IOException ignored) {
                    }
                }
                sp.edit().putStringSet("disabled", new HashSet<>(saved)).apply();
                r.rolledBack = true;
                return r;
            }
            r.done += done.size();
        }
        a.shell("sync");
        return r;
    }

    /**
     * The three apps working as one (the same steps as TV Doctor's `integrate`): the Gallery as the screensaver, Lume's
     * accessibility service so the Home button opens Lume where the TV keeps its own Home, and a first launch of Lume Cast
     * (an app that was never opened is "stopped" and is not started at boot). The old values are kept for Undo.
     */
    static List<String> integrate(Context c, AdbClient a) throws Exception {
        List<String> did = new ArrayList<>();
        Set<String> have = packages(a.shell("pm list packages"));
        SharedPreferences sp = c.getSharedPreferences("optimize", Context.MODE_PRIVATE);
        JSONObject old = new JSONObject(sp.getString("integration_old", "{}"));
        if (have.contains("org.lumesuite.gallery")) {
            remember(a, old, "screensaver_components");
            remember(a, old, "screensaver_enabled");
            a.shell("settings put secure screensaver_components org.lumesuite.gallery/org.lumesuite.gallery.ArtDream");
            a.shell("settings put secure screensaver_enabled 1");
            did.add("screensaver");
        }
        if (!c.getPackageName().equals(homePackage(a))) {
            String ours = c.getPackageName() + "/org.lumesuite.launcher.PowerService";
            String cur = a.shell("settings get secure enabled_accessibility_services");
            if ("null".equals(cur)) cur = "";
            if (!cur.contains(ours)) {
                remember(a, old, "enabled_accessibility_services");
                remember(a, old, "accessibility_enabled");
                a.shell("settings put secure enabled_accessibility_services " + (cur.isEmpty() ? ours : cur + ":" + ours));
                a.shell("settings put secure accessibility_enabled 1");
                did.add("home-button");
            }
        }
        a.shell("appops set " + c.getPackageName() + " SYSTEM_ALERT_WINDOW allow");   // the phone remote can open apps while another app is showing
        did.add("phone-opens");
        if (have.contains("org.lumesuite.cast")) {
            a.shell("appops set org.lumesuite.cast SYSTEM_ALERT_WINDOW allow");   // lets Lume Cast come to the front when a phone starts casting
            a.shell("monkey -p org.lumesuite.cast -c android.intent.category.LEANBACK_LAUNCHER 1");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            a.shell("am start -n " + c.getPackageName() + "/org.lumesuite.launcher.SetupActivity --activity-reorder-to-front");   // back to where we were
            did.add("airplay");
        }
        sp.edit().putString("integration_old", old.toString()).apply();
        return did;
    }

    private static void remember(AdbClient a, JSONObject old, String key) throws Exception {
        if (!old.has(key)) old.put(key, a.shell("settings get secure " + key));
    }

    static void undoIntegration(Context c, AdbClient a) throws Exception {
        SharedPreferences sp = c.getSharedPreferences("optimize", Context.MODE_PRIVATE);
        JSONObject old = new JSONObject(sp.getString("integration_old", "{}"));
        for (java.util.Iterator<String> it = old.keys(); it.hasNext(); ) {
            String k = it.next(), v = old.getString(k);
            if (!k.matches("[a-z_]{1,60}")) continue;
            a.shell(v.isEmpty() || "null".equals(v) ? "settings delete secure " + k : "settings put secure " + k + " " + v);
        }
        sp.edit().remove("integration_old").apply();
    }

    // ---------------------------------------------------------------- the same cleaning, driven from the phone

    static final class Job {
        volatile String status = "idle";   // idle, waiting_ok, debug_off, ready, tidy, cleaning, done, stopped, failed
        volatile Plan plan;
        volatile int done;
        volatile List<String> linked = new ArrayList<>();
    }

    static final Job job = new Job();

    static synchronized void startCheck(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (job.status.equals("waiting_ok") || job.status.equals("cleaning")) return;
        job.status = "waiting_ok";
        new Thread(() -> {
            try (AdbClient a = AdbClient.connect(c, "127.0.0.1", 5555, 120000)) {
                Plan p = plan(c, a);
                job.plan = p;
                job.status = p.safe.isEmpty() ? "tidy" : "ready";
            } catch (AdbClient.NotReady e) {
                job.status = e.waitingForOk ? "waiting_ok" : "debug_off";
            } catch (Exception e) {
                android.util.Log.w("LumeOptimize", "phone check failed", e);
                job.status = "failed";
            }
        }).start();
    }

    static synchronized void startClean(Context ctx, Set<String> keep) {
        final Context c = ctx.getApplicationContext();
        final Plan p = job.plan;
        if (p == null || job.status.equals("cleaning")) return;
        job.status = "cleaning";
        new Thread(() -> {
            try (AdbClient a = AdbClient.connect(c, "127.0.0.1", 5555, 15000)) {
                List<String> pick = new ArrayList<>();
                for (String pkg : p.safe) {
                    String g = group(c, pkg);
                    if (g == null || !keep.contains(g)) pick.add(pkg);
                }
                Result r = apply(c, a, p, pick);
                job.done = r.done;
                job.linked = r.rolledBack ? new ArrayList<>() : integrate(c, a);
                job.status = r.rolledBack ? "stopped" : "done";
            } catch (Exception e) {
                android.util.Log.w("LumeOptimize", "phone clean failed", e);
                job.status = "failed";
            }
        }).start();
    }

    static synchronized void startUndo(Context ctx) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try (AdbClient a = AdbClient.connect(c, "127.0.0.1", 5555, 15000)) {
                job.done = undo(c, a);
                job.plan = null;
                job.status = "idle";
            } catch (Exception e) {
                job.status = "failed";
            }
        }).start();
    }

    /** What the phone page shows: the state, the groups to keep, and whether the two switches are on. */
    static JSONObject snapshot(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("status", job.status).put("done", job.done).put("saved", saved(c).size());
        o.put("dev", android.provider.Settings.Global.getInt(c.getContentResolver(), android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1);
        o.put("adb", android.provider.Settings.Global.getInt(c.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0) == 1);
        JSONArray gs = new JSONArray();
        Plan p = job.plan;
        if (p != null) {
            java.util.LinkedHashMap<String, Integer> n = new java.util.LinkedHashMap<>();
            for (String pkg : p.safe) {
                String g = group(c, pkg);
                if (g != null) n.put(g, n.containsKey(g) ? n.get(g) + 1 : 1);
            }
            for (java.util.Map.Entry<String, Integer> e : n.entrySet())
                gs.put(new JSONObject().put("id", e.getKey()).put("count", e.getValue()).put("keep", keepByDefault(c, e.getKey())));
            o.put("count", p.safe.size());
        }
        o.put("groups", gs);
        JSONArray lk = new JSONArray();
        for (String x : job.linked) lk.put(x);
        o.put("linked", lk);
        return o;
    }

    static Set<String> saved(Context c) {
        return c.getSharedPreferences("optimize", Context.MODE_PRIVATE).getStringSet("disabled", new HashSet<>());
    }

    static int undo(Context c, AdbClient a) throws Exception {
        int n = 0;
        Set<String> left = new HashSet<>(saved(c));
        for (String p : new ArrayList<>(left)) {
            if (!p.matches("[A-Za-z0-9._]{1,200}")) continue;
            a.shell("pm enable " + p);
            left.remove(p);
            n++;
        }
        undoIntegration(c, a);
        c.getSharedPreferences("optimize", Context.MODE_PRIVATE).edit().putStringSet("disabled", left).apply();
        return n;
    }
}
