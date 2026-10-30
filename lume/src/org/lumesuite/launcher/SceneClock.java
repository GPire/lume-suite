package org.lumesuite.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

/**
 * The "Scene" look of the Home (after tvOS Launcher's scene home): a greeting, the date with the weather, and a very
 * large clock in light, translucent type that lets the painting show through. Lume's own twist: the clock sits over
 * the gallery paintings (public domain) and the weather line opens the forecast. It draws once a minute and nothing
 * else, so the Home still sits at 0 frames while idle.
 */
final class SceneClock extends LinearLayout {
    private final TextView greeting, line;
    private final Clock clock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - now % 60_000 + 200);  // just after the next minute starts
        }
    };

    SceneClock(Context c, float unit, Runnable onWeather) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        greeting = label(c, unit, 17, "medium", 0xCCFFFFFF);
        line = label(c, unit, 19, "semibold", Color.WHITE);
        line.setFocusable(onWeather != null);
        line.setClickable(onWeather != null);
        line.setPadding((int) (14 * unit), (int) (5 * unit), (int) (14 * unit), (int) (5 * unit));
        if (onWeather != null) {
            line.setOnClickListener(v -> onWeather.run());
            line.setOnFocusChangeListener((v, has) -> {
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setCornerRadius(40 * unit);
                g.setColor(has ? 0x40FFFFFF : 0);
                v.setBackground(g);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(Motion.d(180)).start();
            });
        }
        clock = new Clock(c, unit);
        addView(greeting);
        addView(line, new LayoutParams(-2, -2));
        LayoutParams cp = new LayoutParams(-2, -2);
        cp.topMargin = (int) (4 * unit);
        addView(clock, cp);
        refresh();
    }

    private static TextView label(Context c, float unit, float units, String weight, int color) {
        TextView t = new TextView(c);
        t.setTypeface(Ui.font(c, weight));
        t.setTextColor(color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, units * unit);
        t.setShadowLayer(10 * unit, 0, 1.5f * unit, 0x99000000);  // legible over bright paintings too
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        return t;
    }

    /** Good morning / afternoon / evening / night, with the profile's name. */
    static String greetingFor(Context c) {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String g = h >= 5 && h < 12 ? "Good morning" : h >= 12 && h < 18 ? "Good afternoon" : h >= 18 && h < 22 ? "Good evening" : "Good night";
        Profiles.Profile p = Profiles.current(c);
        String name = p == null || p.name == null ? "" : p.name.trim();
        if (name.equals("Me") || name.equals("Io")) name = "";  // the stock placeholder is not a name worth greeting
        return It.t(g) + (name.isEmpty() ? "" : ", " + name);
    }

    void refresh() {
        Context c = getContext();
        greeting.setText(greetingFor(c));
        String date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), new Date()).toString();
        Weather.Data d = Weather.enabled(c) ? Weather.cached(c) : null;
        line.setText(d == null ? date : date + "  ·  " + d.temp + "° " + Weather.describe(d.code, d.day));
        clock.refresh();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /** The big time: Antonio Light (SIL OFL), a soft white-to-glass gradient, a thin bright rim, a faint shadow. */
    private static final class Clock extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
                shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect b = new Rect();
        private final float target;
        private String text = "";

        Clock(Context c, float unit) {
            super(c);
            Typeface tf;
            try {
                tf = Typeface.createFromAsset(c.getAssets(), "fonts/Antonio-Light.ttf");
            } catch (RuntimeException e) {
                tf = Ui.font(c, "medium");
            }
            for (Paint p : new Paint[]{fill, rim, shadow}) {
                p.setTypeface(tf);
                p.setTextAlign(Paint.Align.CENTER);
            }
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1.2f, 1.1f * unit));
            rim.setColor(0x66FFFFFF);
            shadow.setColor(0x00000000);
            shadow.setShadowLayer(22 * unit, 0, 6 * unit, 0x80000000);
            target = 132 * unit;  // digit height
            setLayerType(LAYER_TYPE_SOFTWARE, null);  // needed for the shadow; drawn once a minute only
        }

        void refresh() {
            String t = DateFormat.is24HourFormat(getContext())
                    ? DateFormat.format("HH:mm", new Date()).toString() : DateFormat.format("h:mm", new Date()).toString();
            if (!t.equals(text)) {
                text = t;
                requestLayout();
                invalidate();
            }
        }

        private void fit() {
            fill.setTextSize(200f);
            fill.getTextBounds("0", 0, 1, b);
            float size = 200f * target / Math.max(1, b.height());
            for (Paint p : new Paint[]{fill, rim, shadow}) p.setTextSize(size);
        }

        @Override protected void onMeasure(int w, int h) {
            fit();
            int width = (int) Math.ceil(fill.measureText(text.isEmpty() ? "00:00" : text)) + (int) (target * 0.12f);
            setMeasuredDimension(width, (int) (target * 1.18f));
        }

        @Override protected void onDraw(Canvas cv) {
            float x = getWidth() / 2f, base = target * 1.07f;
            cv.drawText(text, x, base, shadow);
            fill.setShader(new LinearGradient(0, base - target, 0, base, 0xFAFFFFFF, 0xB8FFFFFF, Shader.TileMode.CLAMP));
            cv.drawText(text, x, base, fill);
            cv.drawText(text, x, base, rim);
        }
    }
}
