package org.lumesuite.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "Share to this TV": a guided page for AirPlay. Shows this TV's name, the three things to do on the iPhone
 * (same Wi-Fi, Music, Screen) and whether the receiver is up right now. Readiness comes from listening for the
 * receiver's own announcement on the network, never from poking its port (a probe makes it drop a real sender).
 */
public class AirPlayActivity extends Activity {
    /** AirPlay receivers we know, best first. Whichever is installed is the one the guide opens. */
    static final String[] PKGS = {"org.lumesuite.cast", "com.weenas.castbay", "io.github.jqssun.airplay"};

    /** The installed receiver, or null. */
    static String receiver(android.content.Context c) {
        for (String p : PKGS) if (Suggestions.installed(c, p)) return p;
        return null;
    }

    /** True for any receiver app: their own screens are replaced by this guide, so Home hides them. */
    static boolean isReceiver(String pkg) {
        for (String p : PKGS) if (p.equals(pkg)) return true;
        return false;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusText, nameView, step2, step3;
    private String deviceName;
    private final java.util.ArrayDeque<NsdServiceInfo> pending = new java.util.ArrayDeque<>();
    private boolean resolving;
    private String localIp = "";
    private GradientDrawable dot;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener listener;
    private boolean ready;

    int u(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Shot.maybe(this, getIntent());
        Gallery.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070D);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap w = Gallery.wallpaper();
        if (w != null) bg.setImageBitmap(w);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC000000, 0xB3000000, 0xE6000000}));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(u(54), u(36), u(54), u(28));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // title row
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView g = new ImageView(this);
        g.setImageBitmap(glyph(u(30) * 2, 0xFFFFFFFF));
        head.addView(g, new LinearLayout.LayoutParams(u(30), u(30)));
        HTextView title = text("Share to this TV", 26, "semibold", 0xFFFFFFFF);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = u(10);
        head.addView(title, tlp);
        col.addView(head);

        // this TV's name: the one thing the person must recognise in the list
        HTextView name = text("AirPlay", 40, "semibold", 0xFFFFFFFF);
        nameView = name;
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = u(16);
        col.addView(name, nlp);

        // status pill
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(u(14), u(6), u(16), u(6));
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(u(20));
        pg.setColor(0x33FFFFFF);
        pill.setBackground(pg);
        View d = new View(this);
        dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFFFFB020);
        d.setBackground(dot);
        pill.addView(d, new LinearLayout.LayoutParams(u(9), u(9)));
        statusText = text("Looking for the receiver…", 14, "medium", 0xFFFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.leftMargin = u(8);
        pill.addView(statusText, slp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = u(10);
        col.addView(pill, plp);

        // three steps
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.HORIZONTAL);
        steps.addView(step("1", "Same Wi-Fi", "Your iPhone and this TV must be on the same Wi-Fi network."));
        View s2 = step("2", "Music", It.f("Open Apple Music, tap the AirPlay icon and choose %s.", "\u2026"));
        View s3 = step("3", "Screen", It.f("Control Center, then Screen Mirroring, then choose %s.", "\u2026"));
        step2 = (TextView) ((LinearLayout) s2).getChildAt(2);
        step3 = (TextView) ((LinearLayout) s3).getChildAt(2);
        steps.addView(s2);
        steps.addView(s3);
        for (int i = 0; i < steps.getChildCount(); i++) {  // the three cards share the height of the tallest
            ViewGroup.LayoutParams lp = steps.getChildAt(i).getLayoutParams();
            if (lp != null) { lp.height = ViewGroup.LayoutParams.MATCH_PARENT; steps.getChildAt(i).setLayoutParams(lp); }
        }
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-2, -2);
        stlp.topMargin = u(26);
        col.addView(steps, stlp);

        HTextView tip = text("The first time, the connection can fail once: just try again.", 12, "regular", 0xB3FFFFFF);
        LinearLayout.LayoutParams tip_ = new LinearLayout.LayoutParams(-2, -2);
        tip_.topMargin = u(16);
        col.addView(tip, tip_);

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final String pkg = receiver(this);
        boolean installed = pkg != null;
        View primary = null;
        if (installed) {
            primary = button("Open the receiver", true, () -> {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) startActivity(i);
            });
            buttons.addView(primary);
        }
        View back = button("Back", !installed, this::finish);
        buttons.addView(back);
        col.addView(buttons);
        setContentView(root);
        (primary != null ? primary : back).requestFocus();

        if (!installed) setStatus(false, "Receiver not installed");
        else startListening();
    }

    // ------------------------------------------------------------ status

    private void startListening() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            localIp = (ip & 255) + "." + ((ip >> 8) & 255) + "." + ((ip >> 16) & 255) + "." + ((ip >> 24) & 255);
        } catch (Exception ignored) {
            // without our own address the name stays generic
        }
        nsd = (NsdManager) getSystemService(NSD_SERVICE);
        listener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onStartDiscoveryFailed(String t, int e) { }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
            @Override public void onDiscoveryStopped(String t) { }

            @Override public void onServiceFound(NsdServiceInfo si) {
                synchronized (pending) {
                    pending.add(si);
                }
                ui.post(AirPlayActivity.this::resolveNext);
            }

            @Override public void onServiceLost(NsdServiceInfo si) {
                if (deviceName != null && norm(si.getServiceName()).equals(norm(deviceName))) {
                    ui.post(() -> setStatus(false, "Receiver not running"));
                }
            }
        };
        try {
            nsd.discoverServices("_airplay._tcp.", NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            setStatus(false, "Receiver not running");
        }
        // if nothing is announced within a few seconds, say so instead of spinning forever
        ui.postDelayed(() -> { if (!ready) setStatus(false, "Receiver not running"); }, 6000);
    }

    /** Resolves announced services one at a time (Android allows one at a time) and keeps the one on this TV. */
    private void resolveNext() {
        if (resolving) return;
        NsdServiceInfo si;
        synchronized (pending) {
            si = pending.poll();
        }
        if (si == null || nsd == null) return;
        resolving = true;
        try {
            nsd.resolveService(si, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo i, int e) {
                    resolving = false;
                    ui.post(AirPlayActivity.this::resolveNext);
                }

                @Override public void onServiceResolved(NsdServiceInfo i) {
                    resolving = false;
                    boolean mine = i.getHost() != null && i.getHost().getHostAddress().equals(localIp);
                    if (mine) ui.post(() -> {
                        deviceName = i.getServiceName();
                        nameView.setText(deviceName);
                        step2.setText(It.f("Open Apple Music, tap the AirPlay icon and choose %s.", deviceName));
                        step3.setText(It.f("Control Center, then Screen Mirroring, then choose %s.", deviceName));
                        setStatus(true, "Ready: this TV appears in AirPlay");
                    });
                    ui.post(AirPlayActivity.this::resolveNext);
                }
            });
        } catch (Exception e) {
            resolving = false;
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private void setStatus(boolean ok, String text) {
        ready = ok;
        dot.setColor(ok ? 0xFF34C759 : 0xFFFFB020);
        statusText.setText(text);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try {
            if (nsd != null && listener != null) nsd.stopServiceDiscovery(listener);
        } catch (Exception ignored) {
            // already stopped
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------ building blocks

    private HTextView text(String s, float size, String weight, int color) {
        HTextView t = new HTextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(Ui.font(this, weight));
        Ui.textSize(t, size);
        t.setShadowLayer(u(6), 0, u(1), 0x66000000);
        return t;
    }

    private View step(String n, String title, String detail) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(u(18), u(16), u(18), u(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(16));
        bg.setColor(0x26FFFFFF);
        bg.setStroke(Math.max(1, u(1)), 0x33FFFFFF);
        c.setBackground(bg);
        HTextView num = text(n, 13, "semibold", 0xFF111114);
        num.setGravity(Gravity.CENTER);
        GradientDrawable ng = new GradientDrawable();
        ng.setShape(GradientDrawable.OVAL);
        ng.setColor(0xFFFFFFFF);
        num.setBackground(ng);
        c.addView(num, new LinearLayout.LayoutParams(u(22), u(22)));
        HTextView t = text(title, 17, "semibold", 0xFFFFFFFF);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = u(10);
        c.addView(t, tl);
        HTextView dt = text(detail, 12.5f, "regular", 0xCCFFFFFF);
        dt.setMaxLines(4);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(u(190), -2);
        dl.topMargin = u(6);
        c.addView(dt, dl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = lp.rightMargin = u(8);
        c.setLayoutParams(lp);
        return c;
    }

    private View button(String label, boolean primary, Runnable go) {
        HTextView b = new HTextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Ui.font(this, primary ? "semibold" : "medium"));
        Ui.textSize(b, 15);
        b.setFocusable(true);
        b.setClickable(true);
        b.setMinWidth(u(150));
        b.setPadding(u(26), 0, u(26), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(20));
        int idle = primary ? 0x59FFFFFF : 0x26FFFFFF;
        g.setColor(idle);
        b.setBackground(g);
        b.setTextColor(Color.WHITE);
        b.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? 0xFFFFFFFF : idle);
            b.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(Motion.d(180)).start();
        });
        b.setOnClickListener(v -> go.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u(40));
        lp.leftMargin = lp.rightMargin = u(8);
        b.setLayoutParams(lp);
        return b;
    }

    // ------------------------------------------------------------ the AirPlay mark and the home card

    /** The AirPlay mark (screen outline open at the bottom, triangle below) drawn as vectors at any size. */
    static Bitmap glyph(int size, int color) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawGlyph(new Canvas(b), size / 2f, size / 2f, size, color);
        return b;
    }

    static void drawGlyph(Canvas c, float cx, float cy, float size, int color) {
        float k = size / 100f, ox = cx - size / 2f, oy = cy - size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7f * k);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path screen = new Path();
        screen.moveTo(ox + 28 * k, oy + 75 * k);
        screen.lineTo(ox + 12 * k, oy + 75 * k);
        screen.lineTo(ox + 12 * k, oy + 14 * k);
        screen.lineTo(ox + 88 * k, oy + 14 * k);
        screen.lineTo(ox + 88 * k, oy + 75 * k);
        screen.lineTo(ox + 72 * k, oy + 75 * k);
        c.drawPath(screen, p);
        p.setStyle(Paint.Style.FILL);
        Path tri = new Path();
        tri.moveTo(ox + 50 * k, oy + 58 * k);
        tri.lineTo(ox + 84 * k, oy + 92 * k);
        tri.lineTo(ox + 16 * k, oy + 92 * k);
        tri.close();
        c.drawPath(tri, p);
    }

    /** The home card: calm indigo with a soft glow and the mark, matching the other generated cards. */
    static Bitmap card(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF2A2F6B, 0xFF0B0D24, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w / 2f, h * 0.46f, h * 0.65f,
                new int[]{0x886A7CFF, 0x006A7CFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float sz = h * 0.50f;
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setMaskFilter(new android.graphics.BlurMaskFilter(h * 0.03f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        Bitmap shadow = glyph((int) sz, 0x66000000);
        c.drawBitmap(shadow, (w - sz) / 2f, (h - sz) / 2f + h * 0.02f, sh);
        drawGlyph(c, w / 2f, h / 2f, sz, 0xFFFFFFFF);
        return b;
    }
}
