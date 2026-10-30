package org.lumesuite.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The few pieces every full-screen Lume page shares: the wallpaper behind a dim, a glass panel, text and pills. */
final class UiKit {
    private UiKit() {
    }

    static FrameLayout screen(Activity a) {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0xFF000000);
        ImageView back = new ImageView(a);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        back.setImageBitmap(MainActivity.makeWallpaperBase(Profiles.homePrefs(a).getString("wallpaper", "aurora")));
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(a);
        Appearance.load(a);
        dim.setBackgroundColor(Appearance.pageDim());
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        if (!Appearance.contrast()) root.addView(grain(a), new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private static android.graphics.Bitmap noise;

    /**
     * A fine film grain at screen resolution, drawn over the dim. The wallpaper is a small bitmap stretched up, and a
     * dim over a smooth gradient steps in 8-bit bands that show as contour lines on dark backgrounds; random speckle
     * across the whole screen breaks those steps. For still screens only: a full-screen layer costs a weak GPU on every frame.
     */
    static View grain(Context c) {
        if (noise == null) {
            int n = 256;
            int[] px = new int[n * n];
            java.util.Random r = new java.util.Random(11);
            for (int i = 0; i < px.length; i++) {
                int a = 3 + r.nextInt(9);
                px[i] = (r.nextBoolean() ? 0x00FFFFFF : 0x00000000) | a << 24;
            }
            noise = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888);
        }
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(c.getResources(), noise);
        d.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        View v = new View(c);
        v.setBackground(d);
        v.setFocusable(false);
        return v;
    }

    static LinearLayout glass(Context c, int widthUnits) {
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(Ui.px(c, 34), Ui.px(c, 28), Ui.px(c, 34), Ui.px(c, 28));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(c, 28));
        g.setColor(Appearance.panelFill());
        g.setStroke(Math.max(1, Ui.px(c, Appearance.contrast() ? 1.6f : 0.7f)), Appearance.panelStroke());
        panel.setBackground(g);
        panel.setLayoutParams(new FrameLayout.LayoutParams(Ui.px(c, widthUnits), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return panel;
    }

    /**
     * A row of pills that must fit the panel: when the translated labels are longer than the room, the text
     * steps down (never below 12 units) instead of the last pill being squeezed into a sliver.
     */
    static void fit(LinearLayout row) {
        row.post(() -> {
            View parent = (View) row.getParent();
            if (parent == null) return;
            int avail = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            if (avail <= 0) return;
            for (float units = 17; units >= 12; units -= 1) {
                int sum = 0;
                for (int i = 0; i < row.getChildCount(); i++) {
                    View ch = row.getChildAt(i);
                    if (ch instanceof TextView) Ui.textSize((TextView) ch, units);
                    ch.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ch.getLayoutParams();
                    sum += ch.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                }
                if (sum <= avail) break;
            }
            row.requestLayout();
        });
    }

    static TextView text(Context c, String s, float units, String weight, int color) {
        TextView t = new HTextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(Ui.font(c, weight));
        Ui.textSize(t, units);
        return t;
    }

    static TextView pill(Context c, String label, boolean main, View.OnClickListener click) {
        TextView t = text(c, label, 17, "semibold", Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.px(c, 26), Ui.px(c, 11), Ui.px(c, 26), Ui.px(c, 11));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(c, 40));
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
}
