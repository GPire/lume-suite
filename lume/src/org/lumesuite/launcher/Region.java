package org.lumesuite.launcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Where this TV is, read from its own language and region settings: news sources, the streaming catalogue and the
 * language of titles all follow it, so the same build feels local in Rome, Austin, Madrid, Munich, Lyon, Sao Paulo or
 * Mumbai. A country with no pack of its own gets the international one (BBC, Guardian), never somebody else's.
 */
final class Region {
    private Region() {
    }

    /** Countries JustWatch and TMDB know; anything else is asked for as the United States. */
    private static final Set<String> CATALOGUE = new HashSet<>(Arrays.asList(
            "AR", "AU", "AT", "BE", "BR", "CA", "CH", "CL", "CO", "CZ", "DE", "DK", "ES", "FI", "FR", "GB", "GR", "HU", "IE",
            "IN", "IT", "JP", "MX", "NL", "NO", "NZ", "PE", "PL", "PT", "RO", "SE", "SG", "TR", "US", "ZA"));

    /** Upper-case ISO country of the TV's locale ("" when it has none). */
    static String country() {
        String cc = Locale.getDefault().getCountry();
        return cc == null ? "" : cc.toUpperCase(Locale.ROOT);
    }

    static boolean india() {
        return "IN".equals(country());
    }

    /** The country asked of the catalogue services. */
    static String catalogue() {
        String cc = country();
        return CATALOGUE.contains(cc) ? cc : "US";
    }

    /** The language asked of the catalogue services: one of the product's six, else English. */
    static String lang() {
        String l = Locale.getDefault().getLanguage();
        return Arrays.asList("it", "es", "de", "fr", "pt").contains(l) ? l : "en";
    }

    /** TMDB's language tag, such as it-IT. */
    static String tmdbLang() {
        return lang() + "-" + catalogue();
    }

    /** The country's own name in the TV's language, for the news section label. */
    static String homeLabel() {
        String cc = country();
        if (cc.isEmpty()) return It.t("World");
        return new Locale("", cc).getDisplayCountry(Locale.getDefault());
    }
}
