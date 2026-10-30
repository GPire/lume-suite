package org.lumesuite.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Global markets for the TV: indices, gold, oil, bitcoin and a few currencies, each with the day's price, change and
 * a small chart. Data from Yahoo Finance's public chart endpoint (no key; unofficial, so a failed request just keeps
 * the last values shown). The starting set follows the TV's country (see {@link Region}); the person can change it.
 */
final class Markets {
    static final String[][] CATALOG = {
            {"FTSEMIB.MI", "FTSE MIB"}, {"^GSPC", "S&P 500"}, {"^IXIC", "Nasdaq"}, {"^DJI", "Dow Jones"},
            {"^GDAXI", "DAX"}, {"^FCHI", "CAC 40"}, {"^FTSE", "FTSE 100"}, {"^IBEX", "IBEX 35"},
            {"^STOXX50E", "Euro Stoxx 50"}, {"^N225", "Nikkei 225"}, {"^HSI", "Hang Seng"}, {"^BVSP", "Bovespa"},
            {"^BSESN", "Sensex"}, {"^NSEI", "Nifty 50"}, {"GC=F", "Gold"}, {"CL=F", "Oil (WTI)"}, {"BTC-USD", "Bitcoin"},
            {"EURUSD=X", "EUR/USD"}, {"GBPUSD=X", "GBP/USD"}, {"ETH-USD", "Ethereum"},
            // The most followed shares: the big American names, the Italian blue chips, and Europe's leaders.
            {"AAPL", "Apple"}, {"MSFT", "Microsoft"}, {"NVDA", "Nvidia"}, {"GOOGL", "Alphabet"}, {"AMZN", "Amazon"},
            {"TSLA", "Tesla"}, {"META", "Meta"}, {"RACE.MI", "Ferrari"}, {"ENI.MI", "Eni"}, {"ISP.MI", "Intesa Sanpaolo"},
            {"UCG.MI", "UniCredit"}, {"ENEL.MI", "Enel"}, {"STLAM.MI", "Stellantis"}, {"SAP.DE", "SAP"},
            {"ASML.AS", "ASML"}, {"MC.PA", "LVMH"}};

    static final class Quote {
        String sym, name;
        /** True for a symbol the person did not choose (the trending list). */
        boolean listed = true;
        double price, prev;
        float[] series = new float[0];
        long at;

        double pct() {
            return prev == 0 ? 0 : (price / prev - 1) * 100;
        }
    }

    private Markets() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("markets", Context.MODE_PRIVATE);
    }

    static String nameOf(String sym) {
        for (String[] e : CATALOG) if (e[0].equals(sym)) return e[1];
        return sym;
    }

    static boolean inCatalog(String sym) {
        for (String[] e : CATALOG) if (e[0].equals(sym)) return true;
        return false;
    }

    /** The starting set for this TV's country: its own market first, then the world's. */
    static List<String> defaults() {
        switch (Region.country()) {
            case "IT": case "SM": case "VA": return Arrays.asList("FTSEMIB.MI", "^STOXX50E", "^GSPC", "^IXIC", "^GDAXI", "GC=F");
            case "US": case "CA": return Arrays.asList("^GSPC", "^IXIC", "^DJI", "GC=F", "CL=F", "BTC-USD");
            case "GB": case "IE": return Arrays.asList("^FTSE", "^GSPC", "^STOXX50E", "^GDAXI", "GC=F", "GBPUSD=X");
            case "ES": return Arrays.asList("^IBEX", "^STOXX50E", "^GSPC", "^GDAXI", "^IXIC", "EURUSD=X");
            case "DE": case "AT": case "CH": return Arrays.asList("^GDAXI", "^STOXX50E", "^GSPC", "^IXIC", "GC=F", "EURUSD=X");
            case "FR": case "BE": return Arrays.asList("^FCHI", "^STOXX50E", "^GSPC", "^GDAXI", "^IXIC", "GC=F");
            case "BR": return Arrays.asList("^BVSP", "^GSPC", "^IXIC", "GC=F", "BTC-USD", "EURUSD=X");
            case "PT": return Arrays.asList("^STOXX50E", "^IBEX", "^GSPC", "^GDAXI", "GC=F", "EURUSD=X");
            case "IN": return Arrays.asList("^BSESN", "^NSEI", "^GSPC", "^IXIC", "GC=F", "BTC-USD");
            default: return Arrays.asList("^GSPC", "^IXIC", "^STOXX50E", "^N225", "GC=F", "BTC-USD");
        }
    }

    static List<String> selected(Context c) {
        String s = prefs(c).getString("selected", null);
        if (s == null || s.isEmpty()) return new ArrayList<>(defaults());
        return new ArrayList<>(Arrays.asList(s.split(",")));
    }

    static void setSelected(Context c, Set<String> chosen) {
        // Keep the catalogue's order, so the list does not shuffle when something is switched on.
        StringBuilder sb = new StringBuilder();
        for (String[] e : CATALOG) if (chosen.contains(e[0])) sb.append(sb.length() == 0 ? "" : ",").append(e[0]);
        prefs(c).edit().putString("selected", sb.toString()).apply();
    }

    static Quote cached(Context c, String sym) {
        String j = prefs(c).getString("q_" + sym, null);
        if (j == null) return null;
        try {
            JSONObject o = new JSONObject(j);
            Quote q = new Quote();
            q.sym = sym;
            q.name = o.has("n") ? o.getString("n") : nameOf(sym);
            q.price = o.getDouble("p");
            q.prev = o.getDouble("v");
            q.at = o.optLong("t");
            JSONArray a = o.optJSONArray("s");
            q.series = new float[a == null ? 0 : a.length()];
            for (int i = 0; i < q.series.length; i++) q.series[i] = (float) a.getDouble(i);
            return q;
        } catch (Exception e) {
            return null;
        }
    }

    /** Fetches the chosen symbols off the main thread, keeps them, and calls back on the main thread. */
    static void refresh(Context ctx, Runnable done) {
        refresh(ctx, selected(ctx), done);
    }

    /** The shares people are looking at right now, from Yahoo's trending list for the TV's country (else the US). */
    static List<String> trending(Context c) {
        String j = prefs(c).getString("trending", "");
        return j.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(j.split(",")));
    }

    static void refreshTrending(Context ctx, Runnable done) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            List<String> out = new ArrayList<>();
            for (String region : new String[]{Region.catalogue(), "US"}) {
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL("https://query1.finance.yahoo.com/v1/finance/trending/" + region + "?count=10").openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(10000);
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) Lume");
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    try (InputStream in = con.getInputStream()) {
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                    } finally {
                        con.disconnect();
                    }
                    JSONArray qs = new JSONObject(bo.toString("UTF-8")).getJSONObject("finance").getJSONArray("result").getJSONObject(0).getJSONArray("quotes");
                    for (int i = 0; i < qs.length() && out.size() < 8; i++) {
                        String s = qs.getJSONObject(i).getString("symbol");
                        if (!out.contains(s)) out.add(s);
                    }
                } catch (Exception e) {
                    android.util.Log.w("Lume", "trending " + region + ": " + e);
                }
                if (out.size() >= 5) break;   // a country with a thin list borrows the American one
            }
            if (!out.isEmpty()) prefs(c).edit().putString("trending", android.text.TextUtils.join(",", out)).apply();
            refresh(c, trending(c), done);
        }).start();
    }

    static void refresh(Context ctx, List<String> symbols, Runnable done) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            for (String sym : symbols) {
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL("https://query1.finance.yahoo.com/v8/finance/chart/"
                            + URLEncoder.encode(sym, "UTF-8") + "?range=1d&interval=15m").openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(10000);
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) Lume");
                    String body;
                    try (InputStream in = con.getInputStream()) {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = in.read(b)) > 0) out.write(b, 0, n);
                        body = out.toString("UTF-8");
                    } finally {
                        con.disconnect();
                    }
                    JSONObject r = new JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0);
                    JSONObject meta = r.getJSONObject("meta");
                    JSONArray close = r.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close");
                    JSONArray s = new JSONArray();
                    for (int i = 0; i < close.length(); i++) if (!close.isNull(i)) s.put(close.getDouble(i));
                    JSONObject o = new JSONObject().put("p", meta.getDouble("regularMarketPrice"))
                            .put("v", meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", 0)))
                            .put("t", System.currentTimeMillis()).put("s", s).put("n", meta.optString("shortName", sym).trim());
                    prefs(c).edit().putString("q_" + sym, o.toString()).apply();
                } catch (Exception e) {
                    android.util.Log.w("Lume", "market " + sym + ": " + e);   // keep what was shown before
                }
            }
            new Handler(Looper.getMainLooper()).post(done);
        }).start();
    }

    static Set<String> selectedSet(Context c) {
        return new LinkedHashSet<>(selected(c));
    }
}
