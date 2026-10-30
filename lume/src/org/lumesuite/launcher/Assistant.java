package org.lumesuite.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * The TV's own voice assistant, whatever it is: Android asks the system which app answers "assist" and Lume opens that one
 * (Alexa on Fire TV, Google Assistant on Google TV and Android TV, the maker's on others). Nothing is bundled or recorded
 * by Lume: it only opens the assistant the TV already has.
 */
final class Assistant {
    private Assistant() {
    }

    private static ResolveInfo find(Context c) {
        return c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_ASSIST), PackageManager.MATCH_DEFAULT_ONLY);
    }

    /** The assistant's name, or null when the TV has none (the system chooser does not count). */
    static String name(Context c) {
        ResolveInfo ri = find(c);
        if (ri == null || ri.activityInfo == null || "android".equals(ri.activityInfo.packageName)) return null;
        String pkg = ri.activityInfo.packageName;
        if (pkg.startsWith("com.amazon.vizzini") || pkg.startsWith("com.amazon.alexa")) return "Alexa";
        if (pkg.equals("com.google.android.katniss") || pkg.equals("com.google.android.googlequicksearchbox")) return "Google Assistant";
        return String.valueOf(ri.loadLabel(c.getPackageManager()));
    }

    static boolean open(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return name(c) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
