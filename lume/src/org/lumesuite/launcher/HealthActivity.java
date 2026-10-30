package org.lumesuite.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

/**
 * TV health: one ring for the memory that is free, one sentence for how the TV is doing, the measurements that
 * explain it, and one button that closes what runs in the background and shows what that gave back. The ring fills
 * and the number counts up once when the page opens and again after cleaning; nothing moves at rest.
 */
public class HealthActivity extends Activity {
    static final int GREEN = 0xFF30D158, AMBER = 0xFFFFD60A, RED = 0xFFFF453A;

    final Handler handler = new Handler(Looper.getMainLooper());
    Ring ring;
    TextView state, result;
    View dot;
    TextView primary;
    long totalMb;
    boolean busy;

    int u(float v) {
        return Ui.px(this, v);
    }

    /** The memory ring: a track, an arc in the state's colour, the number of MB free in the middle. */
    static final class Ring extends View {
        float fraction;
        int color = GREEN;
        long shownMb;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String unit;
        final android.graphics.Typeface big, small;

        Ring(Context c) {
            super(c);
            unit = It.t("MB free");
            big = Ui.font(c, "semibold");
            small = Ui.font(c, "regular");
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), stroke = w * 0.075f, in = stroke / 2f;
            RectF r = new RectF(in, in, w - in, w - in);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x29FFFFFF);
            cv.drawArc(r, -90, 360, false, p);
            p.setColor(color);
            cv.drawArc(r, -90, 360f * Math.max(0.004f, Math.min(1f, fraction)), false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(big);
            p.setTextSize(w * 0.25f);
            cv.drawText(String.valueOf(shownMb), w / 2f, w / 2f + w * 0.07f, p);
            p.setTypeface(small);
            p.setColor(0xB3FFFFFF);
            p.setTextSize(w * 0.075f);
            cv.drawText(unit, w / 2f, w / 2f + w * 0.07f + w * 0.12f, p);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Shot.maybe(this, getIntent());
        Clicks.init(this);
        Motion.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        back.setImageBitmap(MainActivity.makeWallpaperBase(Profiles.homePrefs(this).getString("wallpaper", "aurora")));
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        root.addView(UiKit.grain(this), new FrameLayout.LayoutParams(-1, -1));

        Optimizer.Report rep = Optimizer.measure(this);
        totalMb = Math.max(1, rep.ramMb);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(u(34), u(30), u(34), u(30));
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(u(28));
        glass.setColor(0x73000000);
        glass.setStroke(Math.max(1, u(0.7f)), 0x33FFFFFF);
        panel.setBackground(glass);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(u(800), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(panel, plp);

        ring = new Ring(this);
        panel.addView(ring, new LinearLayout.LayoutParams(u(200), u(200)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = u(34);
        panel.addView(col, clp);

        TextView title = text("TV health", 30, "semibold", Color.WHITE);
        col.addView(title);

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(GREEN);
        dot.setBackground(dg);
        stateRow.addView(dot, new LinearLayout.LayoutParams(u(10), u(10)));
        state = text("", 19, "medium", 0xE6FFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = u(10);
        stateRow.addView(state, slp);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srp.topMargin = u(6);
        col.addView(stateRow, srp);

        // The measurements that explain the state.
        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = u(16);
        col.addView(facts, flp);
        fact(facts, "Storage", It.f("%s free", Optimizer.gb(rep.storageFreeMb)));
        fact(facts, "Connection", Optimizer.link(rep));
        fact(facts, "Profile", Optimizer.tierName(rep.tier) + "  ·  " + rep.cores + " core");
        int extras = Optimizer.fireExtrasOn(this);
        if (extras >= 0) {
            fact(facts, "Amazon extras", extras == 0 ? It.t("All turned off") : It.f("%d still on. TV Doctor on a computer turns them off safely.", extras));
        }

        result = text("", 16, "regular", 0xCCFFFFFF);
        result.setAlpha(0f);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = u(12);
        col.addView(result, rlp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = u(16);
        col.addView(buttons, blp);
        UiKit.fit(buttons);
        primary = pill("Free up memory", true, v -> cleanNow());
        TextView backBtn = pill("Back", false, v -> finish());
        buttons.addView(primary);
        LinearLayout.LayoutParams bb = new LinearLayout.LayoutParams(-2, -2);
        bb.leftMargin = u(12);
        buttons.addView(backBtn, bb);

        setContentView(root);
        primary.requestFocus();
        panel.setAlpha(0f);
        panel.setTranslationY(u(14));
        panel.animate().alpha(1f).translationY(0).setDuration(Motion.d(450)).start();
        setMemory(rep.freeRamMb, 0, true);
    }

    TextView text(String s, float units, String weight, int color) {
        TextView t = new HTextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(Ui.font(this, weight));
        Ui.textSize(t, units);
        return t;
    }

    void fact(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = text(label, 15, "regular", 0x99FFFFFF);
        row.addView(l, new LinearLayout.LayoutParams(u(160), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = text(value, 15, "medium", 0xE6FFFFFF);
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = u(7);
        parent.addView(row, lp);
    }

    TextView pill(String label, boolean main, View.OnClickListener click) {
        TextView t = text(label, 17, "semibold", Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(u(26), u(11), u(26), u(11));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(40));
        g.setColor(main ? 0x40FFFFFF : 0x24FFFFFF);
        t.setBackground(g);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(click);
        t.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? Color.WHITE : (main ? 0x40FFFFFF : 0x24FFFFFF));
            t.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(Motion.d(160)).start();
        });
        return t;
    }

    /** Moves the ring and the number to a new amount of free memory, and the sentence and colour with it. */
    void setMemory(long freeMb, long fromMb, boolean first) {
        final float to = Math.min(1f, freeMb / (float) totalMb), from = Math.min(1f, fromMb / (float) totalMb);
        final int colour = to >= 0.30f ? GREEN : to >= 0.18f ? AMBER : RED;
        ring.color = colour;
        ((GradientDrawable) dot.getBackground()).setColor(colour);
        state.setText(It.t(to >= 0.30f ? "In great shape" : to >= 0.18f ? "Running well" : "Low on memory"));
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Motion.d(first ? 900 : 700));
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            ring.fraction = from + (to - from) * t;
            ring.shownMb = Math.round(fromMb + (freeMb - fromMb) * t);
            ring.invalidate();
        });
        a.start();
    }

    long avail() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    /** Closes what runs in the background (every app on the home screen, but this one and the system's) and says what it gave back. */
    void cleanNow() {
        if (busy) return;
        busy = true;
        primary.setText(It.t("Freeing…"));
        result.animate().alpha(0f).setDuration(Motion.d(150)).start();
        final long before = avail();
        new Thread(() -> {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            Set<String> pkgs = new HashSet<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo ri : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    pkgs.add(ri.activityInfo.packageName);
                }
            }
            pkgs.remove(getPackageName());
            pkgs.remove("com.amazon.tv.launcher");
            for (String p : pkgs) {
                try {
                    am.killBackgroundProcesses(p);
                } catch (Exception ignored) {
                    // not allowed for this one
                }
            }
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {
                // finish early
            }
            final long after = avail();
            handler.post(() -> {
                if (isDestroyed()) return;
                busy = false;
                primary.setText(It.t("Free up memory"));
                long freed = Math.max(0, after - before);
                result.setText(freed >= 8 ? It.f("Freed %d MB", freed) : It.t("Nothing more to free"));
                result.animate().alpha(1f).setDuration(Motion.d(300)).start();
                setMemory(Math.max(after, before), before, false);
            });
        }).start();
    }
}
