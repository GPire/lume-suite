package org.lumesuite.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * News for the top shelf from newsrooms' RSS feeds, which carry a large image per story (free, no key). The
 * newsrooms follow the TV's country (see Region): Home, Local (the city, where a newsroom has city feeds) and World
 * stories mixed together. Each story becomes a carousel item \u2014 headline, summary, image,
 * section, source and age (no button: stories are read on the shelf).
 */
final class News {
    static final String K_SOURCES = "news_sources";
    static final String INDIA = "india", LOCAL = "local", WORLD = "world";
    static final String[] SECTIONS = {INDIA, LOCAL, WORLD};

    /** A newsroom and its feed for each section ({city} is the local city's slug; null = none). */
    static final class Source {
        final String id, name, india, world, local;
        /** Countries this newsroom is a default for ("*" = the international fallback). */
        final String regions;

        Source(String id, String name, String india, String world, String local) {
            this(id, name, india, world, local, "IN");
        }

        Source(String id, String name, String india, String world, String local, String regions) {
            this.id = id;
            this.name = name;
            this.india = india;     // the newsroom's home feed (the field keeps its first name)
            this.world = world;
            this.local = local;
            this.regions = regions;
        }
    }

    static final List<Source> SOURCES = new ArrayList<>();

    /** Times of India city feed ids (each checked against the feed's own title). */
    static final java.util.Map<String, String> TOI_CITIES = new java.util.HashMap<>();

    static {
        String[][] ids = {{"delhi", "-2128839596"}, {"mumbai", "-2128838597"}, {"bengaluru", "-2128833038"},
                {"chennai", "2950623"}, {"kolkata", "-2128830821"}, {"hyderabad", "-2128816011"}, {"pune", "-2128821991"},
                {"ahmedabad", "-2128821153"}, {"lucknow", "-2128819658"}, {"chandigarh", "-2128816762"},
                {"jaipur", "3012544"}, {"patna", "-2128817995"}, {"noida", "8021716"}, {"gurugram", "6547154"},
                {"nagpur", "442002"}, {"thiruvananthapuram", "878156304"}};
        for (String[] e : ids) TOI_CITIES.put(e[0], e[1]);
        SOURCES.add(new Source("toi", "Times of India",
                "https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms",
                "https://timesofindia.indiatimes.com/rssfeeds/296589292.cms",
                "https://timesofindia.indiatimes.com/rssfeeds/{toi}.cms"));
        SOURCES.add(new Source("ht", "Hindustan Times",
                "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml",
                "https://www.hindustantimes.com/feeds/rss/world-news/rssfeed.xml",
                "https://www.hindustantimes.com/feeds/rss/cities/{ht}-news/rssfeed.xml"));
        SOURCES.add(new Source("ie", "The Indian Express",
                "https://indianexpress.com/section/india/feed/",
                "https://indianexpress.com/section/world/feed/",
                "https://indianexpress.com/section/cities/{ie}/feed/"));
        SOURCES.add(new Source("indiatoday", "India Today",
                "https://www.indiatoday.in/rss/1206578",
                "https://www.indiatoday.in/rss/1206577", null));
        SOURCES.add(new Source("news18", "News18",
                "https://www.news18.com/commonfeeds/v1/eng/rss/india.xml",
                "https://www.news18.com/commonfeeds/v1/eng/rss/world.xml", null));
        SOURCES.add(new Source("ndtv", "NDTV",
                "https://feeds.feedburner.com/ndtvnews-india-news",
                "https://feeds.feedburner.com/ndtvnews-world-news", null));
        SOURCES.add(new Source("hindu", "The Hindu",
                "https://www.thehindu.com/news/national/feeder/default.rss",
                "https://www.thehindu.com/news/international/feeder/default.rss", null));
        // Home and world feeds checked on 2026-10-30 (all answer, all carry one image per story).
        SOURCES.add(new Source("rainews", "Rai News", "https://www.rainews.it/rss/tutti", null, null, "IT,SM,VA"));
        SOURCES.add(new Source("repubblica", "la Repubblica", "https://www.repubblica.it/rss/cronaca/rss2.0.xml",
                "https://www.repubblica.it/rss/esteri/rss2.0.xml", null, "IT,SM,VA"));
        // Regional ANSA feeds: the only local news with feeds for every Italian region (text and summary, no picture).
        SOURCES.add(new Source("ansalocal", "ANSA Regioni", null, null, "https://www.ansa.it/{ansa}/notizie/{ansa}_rss.xml", "IT,SM,VA"));
        SOURCES.add(new Source("nyt", "The New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml",
                "https://rss.nytimes.com/services/xml/rss/nyt/World.xml", null, "US,CA,AU,NZ"));
        SOURCES.add(new Source("bbc", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml",
                "https://feeds.bbci.co.uk/news/world/rss.xml", null, "GB,IE,*"));
        SOURCES.add(new Source("guardian", "The Guardian", "https://www.theguardian.com/uk/rss",
                "https://www.theguardian.com/world/rss", null, "GB,IE,*"));
        SOURCES.add(new Source("elpais", "EL PA\u00cdS", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada",
                null, null, "ES,MX,AR,CL,CO,PE"));
        SOURCES.add(new Source("20min", "20minutos", "https://www.20minutos.es/rss/", null, null, "ES"));
        SOURCES.add(new Source("bbcmundo", "BBC Mundo", "https://feeds.bbci.co.uk/mundo/rss.xml", null, null, "MX,AR,CL,CO,PE,ES"));
        SOURCES.add(new Source("spiegel", "DER SPIEGEL", "https://www.spiegel.de/schlagzeilen/index.rss", null, null, "DE,AT,CH"));
        SOURCES.add(new Source("tagesschau", "tagesschau", "https://www.tagesschau.de/index~rss2.xml", null, null, "DE,AT,CH"));
        SOURCES.add(new Source("lemonde", "Le Monde", "https://www.lemonde.fr/rss/une.xml", null, null, "FR,BE,CH"));
        SOURCES.add(new Source("franceinfo", "franceinfo", "https://www.francetvinfo.fr/titres.rss", null, null, "FR,BE,CH"));
        SOURCES.add(new Source("france24", "France 24", "https://www.france24.com/fr/rss", null, null, "FR,BE,CH"));
        SOURCES.add(new Source("bbcbrasil", "BBC News Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml", null, null, "BR,PT"));
    }

    /** The newsrooms switched on until the person chooses: those for this TV's country, else the international ones. */
    static Set<String> defaultSources() {
        String cc = Region.country();
        Set<String> own = new HashSet<>(), fallback = new HashSet<>();
        for (Source s : SOURCES) {
            java.util.List<String> r = java.util.Arrays.asList(s.regions.split(","));
            if (r.contains(cc)) own.add(s.id);
            if (r.contains("*")) fallback.add(s.id);
        }
        return own.isEmpty() ? fallback : own;
    }

    /** The local city for news: the weather location (from the internet address or chosen). */
    static String city(Context c) {
        return Weather.place(c);
    }

    /**
     * The ANSA regional feed name for a city ("sardegna" for Cagliari): the region comes from the same geocoding the
     * weather uses, then is written the way ANSA spells it. Remembered, so it is asked once per city. Null if unknown.
     */
    static String ansaRegion(Context c, String city) {
        SharedPreferences p = Suggestions.prefs(c);
        String key = "ansa_" + city.toLowerCase(Locale.ROOT);
        String known = p.getString(key, null);
        if (known != null) return known.isEmpty() ? null : known;
        try {
            for (Weather.Place pl : Weather.search(city)) {
                if (pl.detail == null || !pl.detail.endsWith("Italy")) continue;
                String admin = pl.detail.split(",")[0].trim().toLowerCase(Locale.ROOT);
                String slug = java.text.Normalizer.normalize(admin, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").replaceAll("[^a-z]", "");
                if (slug.startsWith("trentino")) slug = "trentino";
                if (!slug.isEmpty()) {
                    p.edit().putString(key, slug).apply();
                    return slug;
                }
            }
        } catch (Exception ignored) {
            // no answer now: asked again next time
        }
        return null;
    }

    /** City slugs as each newsroom spells them in its URLs. */
    private static String slug(String city, boolean indianExpress) {
        String s = city.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z ]", "").replace(' ', '-');
        if (s.equals("new-delhi")) s = "delhi";
        if (indianExpress) {
            switch (s) {
                case "bengaluru": return "bangalore";
                case "gurugram": return "gurgaon";
                case "mysuru": return "mysore";
                case "thiruvananthapuram": return "thiruvananthapuram";
                default: return s;
            }
        }
        switch (s) {
            case "bangalore": return "bengaluru";
            case "gurgaon": return "gurugram";
            case "bombay": return "mumbai";
            default: return s;
        }
    }

    private News() {
    }

    static final String K_REFRESH = "news_refresh_min";
    static final int[] REFRESH_CHOICES = {15, 30, 60};

    /** Minutes between news updates (30 by default). */
    static int refreshMinutes(Context c) {
        return Suggestions.prefs(c).getInt(K_REFRESH, 30);
    }

    static Set<String> sources(Context c) {
        Set<String> def = defaultSources();
        return new HashSet<>(Suggestions.prefs(c).getStringSet(K_SOURCES, def));
    }

    static void setSources(Context c, Set<String> ids) {
        SharedPreferences p = Suggestions.prefs(c);
        p.edit().putStringSet(K_SOURCES, ids).apply();
    }

    /** Latest stories: India, local and world mixed together, alternating between newsrooms. Blocking. */
    static List<Suggestions.Item> fetch(Context c, int max) {
        return fetch(c, max, null);
    }

    /** Same, limited to one section: INDIA (the TV's own country), LOCAL (the city) or WORLD; null = all three. */
    static List<Suggestions.Item> fetch(Context c, int max, String only) {
        List<List<Suggestions.Item>> lists = new ArrayList<>();
        Set<String> chosen = sources(c);
        String city = city(c);
        for (String sec : SECTIONS) {
            if (only != null && !sec.equals(only)) continue;
            if (sec.equals(LOCAL) && city.isEmpty()) continue;
            for (Source s : SOURCES) {
                if (!chosen.contains(s.id)) continue;
                String url = sec.equals(INDIA) ? s.india : sec.equals(WORLD) ? s.world : s.local;
                if (url == null) continue;
                if (url.contains("{toi}")) {
                    String id = TOI_CITIES.get(slug(city, false));
                    if (id == null) continue;
                    url = url.replace("{toi}", id);
                }
                if (url.contains("{ansa}")) {
                    String region = ansaRegion(c, city);
                    if (region == null) continue;
                    url = url.replace("{ansa}", region);
                }
                url = url.replace("{ie}", slug(city, true)).replace("{ht}", slug(city, false));
                String label = sec.equals(LOCAL) ? city : sec.equals(WORLD) ? It.t("World") : Region.homeLabel();
                try {
                    long maxAge = (sec.equals(LOCAL) ? 7 : 2) * 86400000L;  // city news moves slower
                    List<Suggestions.Item> l = read(url, label + "  \u00b7  " + s.name, maxAge, !sec.equals(LOCAL));
                    if (!l.isEmpty()) lists.add(l);
                } catch (Exception e) {
                    // skip a feed that can't be reached (or a city without its own feed)
                    android.util.Log.w("TvHome", "news feed " + url + ": " + e);
                }
            }
        }
        List<Suggestions.Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int round = 0; out.size() < max; round++) {
            boolean any = false;
            for (List<Suggestions.Item> l : lists) {
                if (round < l.size()) {
                    any = true;
                    Suggestions.Item it = l.get(round);
                    if (out.size() < max && seen.add(it.title.toLowerCase(Locale.ROOT))) out.add(it);
                }
            }
            if (!any) break;
        }
        return out;
    }

    private static List<Suggestions.Item> read(String url, String label, long maxAge, boolean needImage) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) Lume");
        List<Suggestions.Item> out = new ArrayList<>();
        try (InputStream in = con.getInputStream()) {
            XmlPullParser x = XmlPullParserFactory.newInstance().newPullParser();
            x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            x.setInput(in, null);
            Suggestions.Item cur = null;
            String tag = null, rawDesc = "";
            int seenItems = 0;
            for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT && out.size() < 30 && seenItems < 80; ev = x.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = x.getName();
                    if (tag.equals("item")) {
                        seenItems++;
                        cur = new Suggestions.Item();
                        cur.type = "news";
                        cur.title = cur.overview = cur.backdrop = cur.year = "";
                        cur.genres = label;
                        cur.logo = "";
                        rawDesc = "";
                    } else if (cur != null && (tag.equals("media:content") || tag.equals("media:thumbnail") || tag.equals("enclosure"))) {
                        String u = x.getAttributeValue(null, "url");
                        String type = x.getAttributeValue(null, "type");
                        if (u != null && (type == null || type.startsWith("image")) && cur.backdrop.isEmpty()) cur.backdrop = bigger(u);
                    }
                } else if (ev == XmlPullParser.TEXT && cur != null && tag != null) {
                    String t = x.getText();
                    switch (tag) {
                        case "title": cur.title += t; break;
                        case "link": cur.link += t.trim(); break;
                        case "description": cur.overview += t; rawDesc += t; break;
                        case "pubDate": cur.published = parseDate(t.trim()); break;
                        default: break;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (x.getName().equals("item") && cur != null) {
                        cur.title = clean(cur.title);
                        cur.overview = clean(cur.overview);
                        if (cur.backdrop.isEmpty()) {  // some feeds (India Today) put the image in the summary
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<img[^>]+src=\"([^\"]+)\"").matcher(rawDesc);
                            if (m.find()) cur.backdrop = bigger(m.group(1).replace("&amp;", "&"));
                        }
                        boolean fresh = cur.published == 0 || System.currentTimeMillis() - cur.published < maxAge;
                        if (!cur.title.isEmpty() && (!needImage || !cur.backdrop.isEmpty()) && fresh) out.add(cur);
                        cur = null;
                    }
                    tag = null;
                }
            }
        } finally {
            con.disconnect();
        }
        out.sort((a, b) -> Long.compare(b.published, a.published));  // newest first
        return out.size() > 8 ? new ArrayList<>(out.subList(0, 8)) : out;
    }

    /** Ask for a larger rendition where the newsroom's image URLs allow it. */
    private static String bigger(String u) {
        java.util.regex.Matcher toi = java.util.regex.Pattern.compile("toiimg\\.com/photo/msid-(\\d+)").matcher(u);
        if (toi.find()) return "https://static.toiimg.com/thumb/msid-" + toi.group(1) + ",width-1280,height-720,resizemode-4/photo.jpg";
        return u.replaceAll("/LANDSCAPE_\\d+/", "/LANDSCAPE_1200/");
    }

    /** Strip tags and entities; keep one short paragraph. */
    static String clean(String s) {
        String t = s.replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
        return t.length() > 260 ? t.substring(0, 257).trim() + "\u2026" : t;
    }

    /** RSS dates: RFC 822 ("Thu, 22 Oct 2026 13:33:54 +0530") or ISO 8601 (Times of India). */
    static long parseDate(String pub) {
        String[] fmts = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"};
        for (String f : fmts) {
            try {
                return new SimpleDateFormat(f, Locale.US).parse(pub).getTime();
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return 0;
    }

    /** "Just now", "5 min ago", "3 h ago", "Yesterday" \u2014 worked out when shown, so it never goes stale. */
    static String age(long published) {
        if (published <= 0) return "";
        long min = (System.currentTimeMillis() - published) / 60000;
        if (min < 1) return It.t("Just now");
        if (min < 60) return It.f("%d min ago", min);
        if (min < 24 * 60) return It.f("%d h ago", min / 60);
        return min < 48 * 60 ? It.t("Yesterday") : It.f("%d days ago", min / 1440);
    }
}
