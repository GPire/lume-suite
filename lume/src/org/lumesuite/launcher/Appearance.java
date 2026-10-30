package org.lumesuite.launcher;

import android.content.Context;
import android.graphics.Color;

/**
 * The look of the surfaces, in one place. Everything is dark with white type (a TV is watched in a dim room), and the person
 * picks how the surfaces are made:
 * <ul>
 * <li><b>Liquid Glass</b> (default): translucent glass that picks up the wallpaper behind it.</li>
 * <li><b>Graphite</b>: solid dark surfaces, no transparency and no blur behind them: calmer, and lighter on a weak GPU.</li>
 * <li><b>High contrast</b>: pure black with white outlines and a stronger focus, for low vision or a bright room.</li>
 * </ul>
 * A light appearance is left out on purpose: type is white everywhere and a pale screen in a dark room is worse, not better.
 */
final class Appearance {
    private Appearance() {
    }

    static final String GLASS = "glass", GRAPHITE = "graphite", CONTRAST = "contrast";
    static final String[] KEYS = {GLASS, GRAPHITE, CONTRAST};
    static final String[] NAMES = {"Liquid Glass", "Graphite", "High contrast"};

    private static volatile String mode = GLASS;

    /** Reads the saved choice; call when a screen starts and after it changes. */
    static void load(Context c) {
        String m = Profiles.homePrefs(c).getString("theme", GLASS);
        mode = (GRAPHITE.equals(m) || CONTRAST.equals(m)) ? m : GLASS;
    }

    static String mode() {
        return mode;
    }

    static void set(Context c, String key) {
        Profiles.homePrefs(c).edit().putString("theme", key).apply();
        load(c);
    }

    /** True when surfaces are solid (Graphite and High contrast). */
    static boolean flat() {
        return !GLASS.equals(mode);
    }

    static boolean contrast() {
        return CONTRAST.equals(mode);
    }

    /** Fill of a glass panel (a full-screen page's card). */
    static int panelFill() {
        return contrast() ? 0xFF000000 : flat() ? 0xF21A1A1E : 0x73000000;
    }

    /** Outline of a panel. */
    static int panelStroke() {
        return contrast() ? 0xE6FFFFFF : 0x33FFFFFF;
    }

    /** How much the wallpaper is dimmed behind a page: Graphite hides most of it, High contrast all of it. */
    static int pageDim() {
        return contrast() ? 0xFF000000 : flat() ? 0xE6000000 : 0x99000000;
    }

    /** A surface for a small control (tile, pill): resting and in focus. */
    static int controlFill(boolean lit) {
        if (contrast()) return lit ? 0xFFFFFFFF : 0xFF000000;
        return lit ? 0xFA3A3A40 : 0xF5232327;
    }

    static int controlStroke(boolean lit) {
        return contrast() ? 0xFFFFFFFF : lit ? 0x66FFFFFF : 0x26FFFFFF;
    }

    static String nameOf(Context c, String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return It.t(NAMES[i]);
        return It.t(NAMES[0]);
    }

    static int white() {
        return Color.WHITE;
    }
}
