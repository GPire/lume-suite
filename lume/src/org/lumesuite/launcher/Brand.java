package org.lumesuite.launcher;

import android.graphics.Color;

import java.util.Locale;

/**
 * Hand-picked card colours for the streaming and TV apps people actually use, so each card carries its brand
 * hue instead of whatever the icon happens to average to. Only the hue matters: the card paints a calm dark
 * tint of it, with a soft glow. Anything not listed keeps the colour measured from its own icon.
 */
final class Brand {
    private Brand() {
    }

    private static final String[][] HUES = {
            // package fragment, hue (degrees); "grey" for graphite
            {"netflix", "355"}, {"youtube", "3"}, {"smarttube", "3"},
            {"primevideo", "200"}, {"amazon.avod", "200"}, {"firebat", "200"}, {"amazon.luna", "265"},
            {"disney", "222"}, {"hotstar", "222"}, {"hbo", "268"}, {"max.", "268"}, {"wbd", "268"},
            {"appletv", "grey"}, {"apple.atve", "grey"},
            {"spotify", "141"}, {"twitch", "265"}, {"plex", "40"}, {"kodi", "205"},
            {"rainet", "215"}, {"raiplay", "215"}, {"timvision", "215"}, {"dazn", "52"}, {"sky", "212"},
            {"mediaset", "275"}, {"infinity", "275"}, {"la7", "32"}, {"pluto", "52"}, {"discovery", "212"},
            {"paramount", "216"}, {"peacock", "48"}, {"hulu", "150"}, {"crunchyroll", "28"},
            {"canal", "0"}, {"zdf", "24"}, {"ard", "212"}, {"joyn", "290"}, {"rtl", "355"}, {"tf1", "212"},
            {"molotov", "190"}, {"globo", "200"}, {"movistar", "200"}, {"atresplayer", "212"},
            {"downloader", "28"}, {"vlc", "28"}, {"silk", "190"},
    };

    /** A colour carrying the brand hue, or null when the app is not a known brand. */
    static Integer tint(String pkg) {
        if (pkg == null) return null;
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String[] h : HUES) {
            if (!p.contains(h[0])) continue;
            if (h[1].equals("grey")) return 0xFF808088;
            return Color.HSVToColor(new float[]{Float.parseFloat(h[1]), 0.75f, 0.8f});
        }
        return null;
    }
}
