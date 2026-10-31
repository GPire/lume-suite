package org.lumesuite.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumesuite.launcher.present.Presenter;

/**
 * Pairing a phone: a QR code to scan (it opens the remote page), the six-digit code to type there, and the address for
 * anyone who prefers to type it. The code changes each time Lume starts; a phone that has paired once keeps working.
 */
public class PhoneActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Shot.maybe(this, getIntent());
        Clicks.init(this);
        Motion.init(this);
        // Opening this page means "I want to pair a phone": switch phone control on, or the code below would lead nowhere.
        if (!Presenter.remoteEnabled(this)) Presenter.setRemote(this, true);
        Presenter.ensureRunning(this);
        FrameLayout root = UiKit.screen(this);
        LinearLayout panel = UiKit.glass(this, 820);
        root.addView(panel);

        panel.addView(UiKit.text(this, "Control Lume from your phone", 30, "semibold", Color.WHITE));
        TextView sub = UiKit.text(this, "Scan the code, then type the number you see here. Your phone and the TV must be on the same Wi-Fi.", 15, "regular", 0xB3FFFFFF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Ui.px(this, 6);
        panel.addView(sub, sp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = Ui.px(this, 22);
        panel.addView(body, bp);

        int qrPx = Ui.px(this, 230);
        ImageView qr = new ImageView(this);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(Ui.px(this, 16));
        frame.setColor(Color.WHITE);
        qr.setBackground(frame);
        int pad = Ui.px(this, 12);
        qr.setPadding(pad, pad, pad, pad);
        Bitmap bmp = Presenter.remoteQr(qrPx);
        qr.setImageBitmap(bmp);
        body.addView(qr, new LinearLayout.LayoutParams(qrPx + 2 * pad, qrPx + 2 * pad));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.leftMargin = Ui.px(this, 34);
        body.addView(side, slp);
        side.addView(UiKit.text(this, "Your code", 15, "medium", 0x99FFFFFF));
        TextView pin = UiKit.text(this, Presenter.pin(this), 64, "semibold", Color.WHITE);
        pin.setLetterSpacing(0.12f);
        side.addView(pin);
        TextView addr = UiKit.text(this, Presenter.remoteAddress().replace("http://", ""), 17, "medium", 0xCCFFFFFF);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.topMargin = Ui.px(this, 10);
        side.addView(addr, ap);
        TextView note = UiKit.text(this, "It changes each time Lume restarts. A phone that has paired once stays paired.", 13, "regular", 0x80FFFFFF);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = Ui.px(this, 10);
        side.addView(note, np);

        TextView back = UiKit.pill(this, "Back", true, v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.px(this, 22);
        panel.addView(back, blp);

        setContentView(root);
        back.requestFocus();
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(Motion.d(400)).start();
    }
}
