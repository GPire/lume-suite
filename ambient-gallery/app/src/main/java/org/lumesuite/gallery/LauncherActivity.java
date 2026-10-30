package org.lumesuite.gallery;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home in stile tvOS: un capolavoro a tutto schermo, orologio e saluto in alto, dock a vetro in basso
 * con schede 16:9. Al focus la scheda si solleva, si ingrandisce e mostra il nome. Margini anti-overscan.
 */
public class LauncherActivity extends Activity {
    private static final int SAFE_X = 96, SAFE_Y = 54;           // 5% di 1920x1080
    private static final int CARD_W = 288, CARD_H = 162;         // banner Leanback 16:9
    private static final long ART_EVERY_MS = 90_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private ImageView artA, artB;
    private View scrim;
    private TextView greeting, clock, shelfTitle, shelfArtist;
    private GlassPanel glass;
    private LinearLayout dock;
    private HorizontalScrollView dockScroll;
    private List<Art.Work> works;
    private int artIndex = 0;
    private boolean artFront;     // quale delle due immagini e' visibile

    private static final String[] STREAM = {"youtube", "netflix", "disney", "primevideo", "amazon.avod", "rainet", "timvision",
            "mediaset", "infinity", "la7", "pluto", "appletv", "atve"};

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        root = new FrameLayout(this);
        artA = backdrop(); artB = backdrop();
        root.addView(artA); root.addView(artB);

        scrim = new View(this);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        greeting = text(44, 0xFFFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock = text(22, 0xCCFFFFFF, Typeface.create("sans-serif", Typeface.NORMAL));
        top.addView(greeting); top.addView(clock);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tp.setMargins(SAFE_X, SAFE_Y, 0, 0);
        root.addView(top, tp);

        // avatar: monogramma in un cerchio di vetro (nessuna emoji)
        TextView avatar = text(30, 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        avatar.setText("G"); avatar.setGravity(Gravity.CENTER); avatar.setShadowLayer(0, 0, 0, 0);
        GradientDrawable av = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x66FFFFFF, 0x26FFFFFF});
        av.setShape(GradientDrawable.OVAL); av.setStroke(2, 0x66FFFFFF);
        avatar.setBackground(av);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(76, 76, Gravity.TOP | Gravity.END);
        ap.setMargins(0, SAFE_Y, SAFE_X, 0);
        root.addView(avatar, ap);

        // top shelf: il capolavoro in evidenza sopra il dock
        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.VERTICAL);
        shelfTitle = text(46, 0xFFFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
        shelfArtist = text(22, 0xCCFFFFFF, Typeface.create("sans-serif", Typeface.NORMAL));
        shelf.addView(shelfTitle); shelf.addView(shelfArtist);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        sp.setMargins(SAFE_X, 0, SAFE_X, SAFE_Y + 340);
        root.addView(shelf, sp);

        // dock a vetro
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false); hs.setClipToPadding(false);
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setClipChildren(false);
        dock.setPadding(40, 40, 40, 64);
        hs.addView(dock);
        dockScroll = hs;
        glass = new GlassPanel(this);
        glass.addView(hs, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        dp.setMargins(SAFE_X - 16, 0, SAFE_X - 16, SAFE_Y - 8);
        root.addView(glass, dp);
        setContentView(root);

        works = Art.catalog(this);
    }

    private ImageView backdrop() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private TextView text(int sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(sp); t.setTextColor(color); t.setTypeface(tf);
        t.setShadowLayer(10, 0, 2, 0x99000000);
        return t;
    }

    @Override protected void onResume() {
        super.onResume();
        tick();
        buildDock();
        nextArt();
    }

    @Override protected void onPause() { ui.removeCallbacksAndMessages(null); super.onPause(); }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); buildDock(); }
    @Override public void onBackPressed() { /* e' la Home */ }

    private void tick() {
        TimeOfDay.Phase p = TimeOfDay.now();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello = p == TimeOfDay.Phase.NIGHT ? "Buonanotte" : p == TimeOfDay.Phase.EVENING ? "Buonasera" : (h < 12 ? "Buongiorno" : "Buon pomeriggio");
        greeting.setText(hello);
        clock.setText(new SimpleDateFormat("HH:mm   EEEE d MMMM", Locale.ITALIAN).format(new Date()));
        // velo: piu' scuro di notte, sempre piu' denso in basso per la leggibilita' del dock
        int a = p == TimeOfDay.Phase.NIGHT ? 0xB0 : p == TimeOfDay.Phase.EVENING ? 0x80 : 0x55;
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{(a / 2) << 24, (a / 3) << 24, (0xE0) << 24}));
        ui.postDelayed(this::tick, 30000);
    }

    /** Cambia il capolavoro di sfondo con una dissolvenza lenta; usa la cache, sul disco. */
    private void nextArt() {
        if (works == null || works.isEmpty()) {
            root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF16406F, 0xFF05070D}));
            return;
        }
        final Art.Work w = works.get(artIndex++ % works.size());
        io.execute(() -> {
            Bitmap b = Art.load(this, w, 1920, 1080);
            ui.post(() -> {
                if (b != null && !isFinishing()) {
                    ImageView in = artFront ? artA : artB, out = artFront ? artB : artA;
                    in.setImageBitmap(b);
                    in.animate().alpha(1f).setDuration(2500).setInterpolator(new DecelerateInterpolator()).start();
                    out.animate().alpha(0f).setDuration(2500).withEndAction(() -> out.setImageDrawable(null)).start();
                    artFront = !artFront;
                    shelfTitle.setText(w.title);
                    shelfArtist.setText(w.artist + ", " + w.year);
                    glass.setBlur(GlassPanel.blurOf(b));
                }
                ui.postDelayed(this::nextArt, ART_EVERY_MS);
            });
        });
    }

    private void buildDock() {
        dock.removeAllViews();
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = tvApps(pm);
        List<ResolveInfo> stream = new ArrayList<>(), rest = new ArrayList<>();
        for (ResolveInfo r : apps) (rankOf(r.activityInfo.packageName) < STREAM.length ? stream : rest).add(r);
        stream.sort((a, b) -> Integer.compare(rankOf(a.activityInfo.packageName), rankOf(b.activityInfo.packageName)));

        dock.addView(card("Galleria", "I capolavori dell'arte", 0xFF8A6D3B, null, true, () -> startActivity(new Intent(this, GalleryActivity.class))));
        for (ResolveInfo r : stream) dock.addView(appCard(r, pm));
        for (ResolveInfo r : rest) dock.addView(appCard(r, pm));
        dock.addView(card("Impostazioni", null, 0xFF3A3F4B, null, true, () -> startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS))));
        dock.addView(card("Home Amazon", null, 0xFF232730, null, true, this::openAmazonHome));
        for (int i = 0; i < dock.getChildCount(); i++) {
            View c = dock.getChildAt(i);
            c.setAlpha(0f); c.setTranslationY(36f);
            c.animate().alpha(1f).translationY(0f).setStartDelay(60L * Math.min(i, 8)).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
    }

    /** Tinta della scheda ricavata dal colore medio dell'icona, scurito per far risaltare l'icona. */
    private static int tintOf(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            d.setBounds(0, 0, 16, 16); d.draw(c);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int px = b.getPixel(x, y);
                if ((px >>> 24) < 128) continue;
                r += (px >> 16) & 255; g += (px >> 8) & 255; bl += px & 255; n++;
            }
            if (n == 0) return 0xFF2A2F3A;
            return 0xFF000000 | ((int) (r / n * 0.45) << 16) | ((int) (g / n * 0.45) << 8) | (int) (bl / n * 0.45);
        } catch (Throwable e) { return 0xFF2A2F3A; }
    }

    private void openAmazonHome() {
        try { startActivity(new Intent(Intent.ACTION_MAIN).setClassName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.HomeActivity_vNext")); }
        catch (Exception ignored) { }
    }

    private View appCard(ResolveInfo r, PackageManager pm) {
        final String pkg = r.activityInfo.packageName;
        Drawable banner = r.activityInfo.loadBanner(pm);
        boolean isBanner = banner != null;
        Drawable art = isBanner ? banner : r.loadIcon(pm);
        int tint = isBanner ? 0xFF2A2F3A : tintOf(art);
        return card(String.valueOf(r.loadLabel(pm)), null, tint, art, isBanner, () -> {
            Intent go = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (go == null) go = pm.getLaunchIntentForPackage(pkg);
            if (go != null) startActivity(go);
        });
    }

    /** Scheda 16:9 con nome sotto che compare solo al focus. */
    private View card(String title, String subtitle, int color, Drawable art, boolean fullBleed, Runnable go) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setFocusable(true);
        cell.setPadding(14, 14, 14, 0);

        FrameLayout face = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(22f);
        face.setBackground(bg);
        face.setClipToOutline(true);
        if (art != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(art);
            if (fullBleed) {          // banner ufficiale 16:9: riempie la scheda
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                face.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            } else {                  // icona quadrata: alla sua dimensione, mai ingrandita a forza
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                face.addView(iv, new FrameLayout.LayoutParams(112, 112, Gravity.CENTER));
                TextView in = text(16, 0xDDFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
                in.setText(title); in.setSingleLine(); in.setGravity(Gravity.CENTER); in.setShadowLayer(0, 0, 0, 0);
                FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
                ip.bottomMargin = 10;
                face.addView(in, ip);
            }
        } else {
            TextView t = text(26, 0xFFFFF6E0, Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setText(title); t.setGravity(Gravity.CENTER); t.setShadowLayer(0, 0, 0, 0);
            // A long name ("Impostazioni", "Home Amazon") gets a smaller size on one line, never a break in mid-word.
            t.setSingleLine();
            t.setTextSize(title.length() <= 8 ? 26 : title.length() <= 11 ? 21 : 17);
            t.setPadding(8, 0, 8, 0);
            face.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        cell.addView(face, new LinearLayout.LayoutParams(CARD_W, CARD_H));

        TextView name = text(22, 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setText(subtitle != null ? subtitle : title);
        name.setSingleLine(); name.setGravity(Gravity.CENTER); name.setAlpha(0f);
        name.setPadding(0, 18, 0, 0);
        cell.addView(name, new LinearLayout.LayoutParams(CARD_W, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = 12;
        cell.setLayoutParams(lp);

        cell.setOnFocusChangeListener((v, f) -> {
            face.animate().scaleX(f ? 1.14f : 1f).scaleY(f ? 1.14f : 1f).translationY(f ? -14f : 0f)
                    .setDuration(f ? 320 : 180).setInterpolator(f ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator()).start();
            face.setElevation(f ? 36f : 4f);
            bg.setStroke(f ? 4 : 0, 0xFFFFFFFF);
            name.animate().alpha(f ? 1f : 0f).setDuration(180).start();
            face.setAlpha(f ? 1f : 0.88f);
            if (f) dockScroll.smoothScrollTo(v.getLeft() - (dockScroll.getWidth() - v.getWidth()) / 2, 0);
        });
        cell.setOnClickListener(v -> go.run());
        return cell;
    }

    private List<ResolveInfo> tvApps(PackageManager pm) {
        List<ResolveInfo> out = new ArrayList<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER");
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.startsWith("com.amazon.tv.launcher") || pkg.equals("com.petrobot.hume")) continue;
            out.add(r);
        }
        out.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        return out;
    }

    private static int rankOf(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        for (int i = 0; i < STREAM.length; i++) if (p.contains(STREAM[i])) return i;
        return STREAM.length;
    }

    /** Pannello "Liquid Glass": sfocato del quadro dietro, velo chiaro, riflesso sul bordo alto, bordo sottile. */
    static final class GlassPanel extends FrameLayout {
        private Bitmap blur;
        private final ImageView layerA, layerB;
        private boolean frontIsA;
        private static final float R = 40f;

        GlassPanel(Context c) {
            super(c);
            layerA = new ImageView(c); layerB = new ImageView(c);
            layerA.setScaleType(ImageView.ScaleType.FIT_XY); layerB.setScaleType(ImageView.ScaleType.FIT_XY);
            layerA.setAlpha(0f); layerB.setAlpha(0f);
            addView(layerA, 0, new LayoutParams(-1, -1)); addView(layerB, 1, new LayoutParams(-1, -1));
        }

        /** Sfocato economico: riduce a 64x36 e leviga con due passate di media; il filtro bilineare fa il resto. */
        static Bitmap blurOf(Bitmap src) {
            Bitmap s = Bitmap.createScaledBitmap(src, 64, 36, true);
            int[] px = new int[64 * 36], out = new int[64 * 36];
            s.getPixels(px, 0, 64, 0, 0, 64, 36);
            for (int pass = 0; pass < 3; pass++) {
                for (int y = 0; y < 36; y++) for (int x = 0; x < 64; x++) {
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= 64 || yy >= 36) continue;
                        int c = px[yy * 64 + xx]; r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
                    }
                    out[y * 64 + x] = 0xFF000000 | (r / n << 16) | (g / n << 8) | b / n;
                }
                int[] t = px; px = out; out = t;
            }
            s.setPixels(px, 0, 64, 0, 0, 64, 36);
            return s;
        }

        void setBlur(Bitmap b) { blur = b; compose(); }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int bt) {
            super.onLayout(changed, l, t, r, bt);
            if (changed && blur != null) post(this::compose);
        }

        /** Compone UNA volta il vetro (sfocato + velo + riflesso + bordo) e lo mostra con dissolvenza: niente lavoro per fotogramma. */
        private void compose() {
            int w = getWidth(), h = getHeight();
            if (blur == null || w <= 0 || h <= 0) return;
            int[] loc = new int[2]; getLocationOnScreen(loc);
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            RectF rect = new RectF(0, 0, w, h);
            Path clip = new Path(); clip.addRoundRect(rect, R, R, Path.Direction.CW);
            c.save(); c.clipPath(clip);
            c.drawBitmap(blur, null, new RectF(-loc[0], -loc[1], -loc[0] + dm.widthPixels, -loc[1] + dm.heightPixels), paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0x2EFFFFFF); c.drawRect(rect, paint);
            paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h * 0.45f, 0x40FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(rect, paint); paint.setShader(null);
            c.restore();
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f); paint.setColor(0x55FFFFFF);
            c.drawPath(clip, paint);
            ImageView in = frontIsA ? layerB : layerA, outV = frontIsA ? layerA : layerB;
            in.setImageBitmap(out);
            in.animate().alpha(1f).setDuration(2500).start();
            outV.animate().alpha(0f).setDuration(2500).withEndAction(() -> outV.setImageDrawable(null)).start();
            frontIsA = !frontIsA;
        }
    }
}
