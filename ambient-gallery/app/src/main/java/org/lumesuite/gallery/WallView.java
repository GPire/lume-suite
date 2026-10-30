package org.lumesuite.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Parete da galleria con un dipinto intero: passepartout, cornice, ombra, targhetta. Nessun movimento. */
final class WallView extends FrameLayout {
    private static final int FADE_MS = 3500;
    private final int screenW, screenH;
    private final TextView label;
    private Slot front, back;

    private final class Slot {
        final FrameLayout frame = new FrameLayout(getContext());
        final FrameLayout mat = new FrameLayout(getContext());
        final ImageView canvas = new ImageView(getContext());
        Slot() {
            frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFFB08D4A, 0xFF7A5C2B, 0xFFC9A55E}));
            frame.setElevation(48f);
            frame.setAlpha(0f);
            mat.setBackgroundColor(0xFFEFE9DC);
            canvas.setScaleType(ImageView.ScaleType.FIT_XY);
            mat.addView(canvas, new LayoutParams(-1, -1));
            frame.addView(mat, new LayoutParams(-1, -1));
        }
    }

    WallView(Context c) {
        super(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        screenW = dm.widthPixels; screenH = dm.heightPixels;
        front = new Slot(); back = new Slot();
        addView(back.frame); addView(front.frame);
        label = new TextView(c);
        label.setTextSize(20);
        label.setTypeface(Typeface.SERIF, Typeface.ITALIC);
        label.setPadding(28, 16, 28, 16);
        label.setAlpha(0f);
        LayoutParams lp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, 96, 64);
        addView(label, lp);
        applyWall(TimeOfDay.now());
    }

    /** Dimensione massima utile del dipinto: il resto e' parete, cornice e passepartout. */
    int screenW() { return Math.round(screenW * 0.80f); }
    int screenH() { return Math.round(screenH * 0.76f); }

    /** Parete: intonaco caldo con luce dall'alto, piu' scuro di sera e di notte. */
    void applyWall(TimeOfDay.Phase p) {
        int center, edge;
        switch (p) {
            case EVENING: center = 0xFF5B4637; edge = 0xFF2B2019; break;
            case NIGHT:   center = 0xFF1B1713; edge = 0xFF080706; break;
            default:      center = 0xFFD9D0C1; edge = 0xFF9C9382;
        }
        GradientDrawable wall = new GradientDrawable();
        wall.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        wall.setGradientRadius(Math.max(screenW, screenH) * 0.75f);
        wall.setGradientCenter(0.5f, 0.35f);
        wall.setColors(new int[]{center, edge});
        setBackground(wall);
        label.setTextColor(p == TimeOfDay.Phase.DAY ? 0xFF3A3328 : 0xFFD9CDB8);
        GradientDrawable plate = new GradientDrawable();
        plate.setColor(p == TimeOfDay.Phase.DAY ? 0xCCF4EFE4 : 0x55000000);
        plate.setCornerRadius(4f);
        label.setBackground(plate);
    }

    /** Mostra un dipinto con dissolvenza. Va chiamato dal thread principale. */
    void show(Bitmap bmp, Art.Work w, TimeOfDay.Phase p, boolean keepLabel) {
        applyWall(p);
        float availW = screenW * 0.84f, availH = screenH * 0.82f; // coerente con screenW()/screenH()
        int border = Math.max(6, Math.round(screenH * 0.011f));
        int matW = Math.max(14, Math.round(screenH * 0.034f));
        float fit = Math.min((availW - 2 * (border + matW)) / bmp.getWidth(),
                             (availH - 2 * (border + matW)) / bmp.getHeight());
        int cw = Math.round(bmp.getWidth() * fit), ch = Math.round(bmp.getHeight() * fit);

        Slot next = back, cur = front;
        next.frame.setPadding(border, border, border, border);
        next.mat.setPadding(matW, matW, matW, matW);
        next.canvas.setImageBitmap(bmp);
        next.canvas.setColorFilter(new ColorMatrixColorFilter(TimeOfDay.matrix(p)));
        LayoutParams lp = new LayoutParams(cw + 2 * (border + matW), ch + 2 * (border + matW), Gravity.CENTER);
        lp.bottomMargin = Math.round(screenH * 0.03f);
        next.frame.setLayoutParams(lp);
        next.frame.bringToFront();
        label.bringToFront();
        next.frame.animate().alpha(1f).setDuration(FADE_MS).start();
        cur.frame.animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction(() -> cur.canvas.setImageDrawable(null)).start();
        front = next; back = cur;

        label.setText(w.title + "\n" + w.artist + ", " + w.year);
        float target = p == TimeOfDay.Phase.NIGHT ? 0.5f : 0.9f;
        label.animate().cancel();
        label.setAlpha(0f);
        if (keepLabel) label.animate().alpha(target).setStartDelay(FADE_MS).setDuration(1200).start();
        else label.animate().alpha(target).setStartDelay(FADE_MS).setDuration(1200)
                .withEndAction(() -> label.animate().alpha(0f).setStartDelay(12000).setDuration(2500).start()).start();
    }

    /** Mostra o nasconde la targhetta (tasto OK nella Galleria). */
    void toggleLabel() { label.animate().cancel(); label.animate().alpha(label.getAlpha() > 0.1f ? 0f : 0.9f).setDuration(300).start(); }
}
