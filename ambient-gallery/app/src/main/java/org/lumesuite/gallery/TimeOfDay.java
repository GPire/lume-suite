package org.lumesuite.gallery;

import android.graphics.ColorMatrix;
import java.util.Calendar;

/** Fascia oraria: giorno colori pieni, sera toni caldi e piu' scuri, notte molto scura. */
final class TimeOfDay {
    enum Phase { DAY, EVENING, NIGHT }

    static Phase now() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 8 && h < 18) return Phase.DAY;
        if (h >= 18 && h < 23) return Phase.EVENING;
        return Phase.NIGHT;
    }

    /** Luminosita' e calore applicati all'immagine. */
    static ColorMatrix matrix(Phase p) {
        float b, r, g, bl;
        switch (p) {
            case EVENING: b = 0.70f; r = 1.08f; g = 0.97f; bl = 0.82f; break;
            case NIGHT:   b = 0.30f; r = 1.05f; g = 0.90f; bl = 0.70f; break;
            default:      b = 1.00f; r = 1.00f; g = 1.00f; bl = 1.00f;
        }
        ColorMatrix m = new ColorMatrix();
        m.setScale(r * b, g * b, bl * b, 1f);
        return m;
    }

    /** Termini di ricerca: di giorno luce piena, di sera e notte atmosfere notturne. */
    static String[] artists(Phase p) {
        if (p == Phase.DAY) return new String[]{"Claude Monet", "Pierre-Auguste Renoir", "Camille Pissarro", "Alfred Sisley", "Berthe Morisot", "Mary Cassatt"};
        return new String[]{"Vincent van Gogh", "Georges Seurat", "Edgar Degas", "Claude Monet"};
    }

    /** Secondi di permanenza di ogni quadro: piu' lento di notte. */
    static int holdSeconds(Phase p) { return p == Phase.NIGHT ? 90 : 45; }
}
