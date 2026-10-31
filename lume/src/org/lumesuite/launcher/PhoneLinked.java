package org.lumesuite.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The moment a phone pairs: a glass capsule drops in at the top of the Home with a spring, shows a green dot and "Phone connected",
 * and floats away. Seeing it is how the person knows the QR worked without looking at the phone.
 */
public final class PhoneLinked {
    private PhoneLinked() {
    }

    public static void show(Context c) {
        new Handler(Looper.getMainLooper()).post(() -> {
            MainActivity a = MainActivity.activeInstance;
            String text = It.t("Phone connected");
            if (a == null || !a.resumed || a.root == null) {   // not on the Home: a plain note is better than nothing
                Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
                return;
            }
            LinearLayout cap = new LinearLayout(a);
            cap.setOrientation(LinearLayout.HORIZONTAL);
            cap.setGravity(Gravity.CENTER_VERTICAL);
            int px = Ui.px(a, 22), py = Ui.px(a, 12);
            cap.setPadding(px, py, px + Ui.px(a, 4), py);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.px(a, 40));
            bg.setColor(0xE6141418);
            bg.setStroke(Math.max(1, Ui.px(a, 0.8f)), 0x33FFFFFF);
            cap.setBackground(bg);
            View dot = new View(a);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0xFF30D158);
            dot.setBackground(d);
            cap.addView(dot, new LinearLayout.LayoutParams(Ui.px(a, 12), Ui.px(a, 12)));
            TextView t = UiKit.text(a, text, 20, "semibold", Color.WHITE);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.leftMargin = Ui.px(a, 12);
            cap.addView(t, tp);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Ui.px(a, 30);
            a.root.addView(cap, lp);
            cap.setAlpha(0f);
            cap.setTranslationY(-Ui.px(a, 40));
            cap.setScaleX(0.9f);
            cap.setScaleY(0.9f);
            cap.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(Motion.d(520))
                    .setInterpolator(new OvershootInterpolator(1.4f)).start();
            dot.animate().scaleX(1.5f).scaleY(1.5f).setDuration(Motion.d(600)).withEndAction(() -> dot.animate().scaleX(1f).scaleY(1f).setDuration(Motion.d(400)).start()).start();
            cap.postDelayed(() -> cap.animate().alpha(0f).translationY(-Ui.px(a, 24)).setDuration(Motion.d(400))
                    .withEndAction(() -> a.root.removeView(cap)).start(), 3200);
        });
    }
}
