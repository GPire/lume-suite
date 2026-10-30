package org.lumesuite.launcher;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Lume's own dialog, in the same dark glass as its pages: a centred panel over a dim, a title, a message or a list of
 * choices (radio, tick or plain) and pill buttons that turn white on focus. Fire OS draws the system dialog full screen
 * in its own style whatever theme is asked for, so this replaces it. Same calls as AlertDialog.Builder for what Lume uses.
 */
final class LumeDialog {
    private LumeDialog() {
    }

    static final class Builder {
        final Context c;
        CharSequence title, message;
        View view;
        CharSequence[] items;
        boolean[] checked;
        int single = -1;
        boolean isSingle, isMulti;
        DialogInterface.OnClickListener itemClick, pos, neg, neu;
        DialogInterface.OnMultiChoiceClickListener multiClick;
        CharSequence posText, negText, neuText;

        Builder(Context c) {
            this.c = c;
        }

        Builder setTitle(CharSequence t) {
            title = t;
            return this;
        }

        Builder setMessage(CharSequence m) {
            message = m;
            return this;
        }

        Builder setView(View v) {
            view = v;
            return this;
        }

        Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) {
            posText = t;
            pos = l;
            return this;
        }

        Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) {
            negText = t;
            neg = l;
            return this;
        }

        Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) {
            neuText = t;
            neu = l;
            return this;
        }

        Builder setItems(CharSequence[] i, DialogInterface.OnClickListener l) {
            items = i;
            itemClick = l;
            return this;
        }

        Builder setSingleChoiceItems(CharSequence[] i, int selected, DialogInterface.OnClickListener l) {
            items = i;
            single = selected;
            isSingle = true;
            itemClick = l;
            return this;
        }

        Builder setMultiChoiceItems(CharSequence[] i, boolean[] on, DialogInterface.OnMultiChoiceClickListener l) {
            items = i;
            checked = on;
            isMulti = true;
            multiClick = l;
            return this;
        }

        Dialog show() {
            final Dialog d = new Dialog(c, android.R.style.Theme_DeviceDefault_NoActionBar);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            final int u1 = Ui.px(c, 1);
            FrameLayout root = new FrameLayout(c);
            root.setBackgroundColor(0xB3000000);
            LinearLayout panel = new LinearLayout(c);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setClipChildren(false);
            panel.setPadding(Ui.px(c, 30), Ui.px(c, 26), Ui.px(c, 30), Ui.px(c, 22));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.px(c, 28));
            g.setColor(0xF01C1C1E);
            g.setStroke(Math.max(1, u1 / 2), 0x33FFFFFF);
            panel.setBackground(g);
            root.addView(panel, new FrameLayout.LayoutParams(Ui.px(c, 520), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            if (title != null && title.length() > 0) {
                TextView t = UiKit.text(c, title.toString(), 24, "semibold", Color.WHITE);
                t.setGravity(Gravity.CENTER);
                panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
            }
            if (message != null && message.length() > 0) {
                TextView m = UiKit.text(c, message.toString(), 16, "regular", 0xCCFFFFFF);
                m.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.px(c, 8);
                panel.addView(m, lp);
            }
            if (view != null) {
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.px(c, 12);
                panel.addView(view, lp);
            }
            final boolean hasButtons = posText != null || negText != null || neuText != null;
            View firstFocus = null;
            if (items != null) {
                ScrollView sv = new ScrollView(c);
                sv.setVerticalScrollBarEnabled(false);
                sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                LinearLayout col = new LinearLayout(c);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(Ui.px(c, 4), Ui.px(c, 4), Ui.px(c, 4), Ui.px(c, 4));
                sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
                final Row[] rows = new Row[items.length];
                for (int i = 0; i < items.length; i++) {
                    final int idx = i;
                    final Row r = new Row(c, items[i].toString(), isMulti ? (checked != null && checked[i] ? 2 : 1) : isSingle ? (i == single ? 4 : 3) : 0);
                    rows[i] = r;
                    r.setOnClickListener(v -> {
                        if (isMulti) {
                            checked[idx] = !checked[idx];
                            r.state = checked[idx] ? 2 : 1;
                            r.invalidate();
                            if (multiClick != null) multiClick.onClick(d, idx, checked[idx]);
                        } else if (isSingle) {
                            single = idx;
                            for (int k = 0; k < rows.length; k++) {
                                rows[k].state = k == idx ? 4 : 3;
                                rows[k].invalidate();
                            }
                            if (itemClick != null) itemClick.onClick(d, idx);
                            if (!hasButtons && d.isShowing()) d.dismiss();
                        } else {
                            if (itemClick != null) itemClick.onClick(d, idx);
                            if (d.isShowing()) d.dismiss();
                        }
                    });
                    col.addView(r, new LinearLayout.LayoutParams(-1, Ui.px(c, 48)));
                    if (firstFocus == null || (isSingle && i == single)) firstFocus = r;
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.min(items.length * Ui.px(c, 48) + Ui.px(c, 8), Ui.px(c, 280)));
                lp.topMargin = Ui.px(c, 12);
                panel.addView(sv, lp);
            }
            if (hasButtons) {
                LinearLayout bar = new LinearLayout(c);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setGravity(Gravity.CENTER);
                bar.setClipChildren(false);
                if (posText != null) bar.addView(button(c, d, posText, true, pos, DialogInterface.BUTTON_POSITIVE));
                if (neuText != null) bar.addView(button(c, d, neuText, false, neu, DialogInterface.BUTTON_NEUTRAL));
                if (negText != null) bar.addView(button(c, d, negText, false, neg, DialogInterface.BUTTON_NEGATIVE));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.px(c, 16);
                panel.addView(bar, lp);
                if (firstFocus == null && view == null) firstFocus = bar.getChildAt(0);
            }
            d.setContentView(root);
            if (d.getWindow() != null) {
                d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            }
            Clicks.quiet(d);
            panel.setAlpha(0f);
            panel.setScaleX(0.96f);
            panel.setScaleY(0.96f);
            d.show();
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.d(220)).start();
            if (firstFocus != null) firstFocus.requestFocus();
            return d;
        }

        static TextView button(Context c, final Dialog d, CharSequence text, boolean main, final DialogInterface.OnClickListener l, final int which) {
            TextView b = UiKit.pill(c, text.toString(), main, v -> {
                if (l != null) l.onClick(d, which);
                if (d.isShowing()) d.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = lp.rightMargin = Ui.px(c, 6);
            b.setLayoutParams(lp);
            return b;
        }
    }

    /** A choice row: plain, tick box (1 off / 2 on) or radio (3 off / 4 on); soft white when focused. */
    static final class Row extends View {
        final String label;
        int state;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final android.graphics.Typeface face;

        Row(Context c, String label, int state) {
            super(c);
            this.label = label;
            this.state = state;
            face = Ui.font(c, "medium");
            setFocusable(true);
            setClickable(true);
            setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(Motion.d(140)).start();
                invalidate();
            });
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), u = Ui.px(getContext(), 1);
            p.setStyle(Paint.Style.FILL);
            if (isFocused()) {
                p.setColor(0x2EFFFFFF);
                r.set(0, 0, w, h);
                cv.drawRoundRect(r, 14 * u, 14 * u, p);
            }
            p.setColor(Color.WHITE);
            p.setTypeface(face);
            p.setTextSize(17 * u);
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(label, 16 * u, h / 2f + 6 * u, p);
            if (state == 0) return;
            float cx = w - 26 * u, cy = h / 2f, rad = 10 * u;
            boolean on = state == 2 || state == 4;
            if (state >= 3) {            // radio
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f * u);
                p.setColor(on ? 0xFF30D158 : 0x80FFFFFF);
                cv.drawCircle(cx, cy, rad, p);
                if (on) {
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(cx, cy, rad * 0.55f, p);
                }
            } else {                      // tick
                p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setStrokeWidth(1.8f * u);
                p.setColor(on ? 0xFF30D158 : 0x80FFFFFF);
                cv.drawCircle(cx, cy, rad, p);
                if (on) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Color.WHITE);
                    p.setStrokeWidth(2f * u);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    cv.drawLine(cx - 5 * u, cy, cx - 1.5f * u, cy + 3.5f * u, p);
                    cv.drawLine(cx - 1.5f * u, cy + 3.5f * u, cx + 5 * u, cy - 3.5f * u, p);
                }
            }
        }
    }
}
