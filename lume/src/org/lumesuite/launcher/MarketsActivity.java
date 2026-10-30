package org.lumesuite.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Global markets: one glass row per index, with the day's chart, the price and the change in a green or red capsule.
 * "Choose indices" turns the rows into a checklist of the whole catalogue. Opens on what was kept from last time and
 * refreshes in the background; nothing moves once it has settled.
 */
public class MarketsActivity extends Activity {
    static final int UP = 0xFF30D158, DOWN = 0xFFFF453A;

    LinearLayout list;
    TextView stamp, choose;
    boolean editing, trending;
    TextView tabMine, tabTrending;

    int u(float v) {
        return Ui.px(this, v);
    }

    /** One index: name and symbol on the left, chart in the middle, price and change on the right. */
    final class Row extends View {
        final String sym;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        final Path path = new Path();
        final RectF rect = new RectF();
        final android.graphics.Typeface regular, medium, semibold;

        Row(Context c, String sym) {
            super(c);
            this.sym = sym;
            regular = Ui.font(c, "regular");
            medium = Ui.font(c, "medium");
            semibold = Ui.font(c, "semibold");
            setFocusable(true);
            setClickable(true);
            setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(Motion.d(160)).start();
                invalidate();
            });
            setOnClickListener(v -> {
                if (!editing) return;
                Set<String> s = Markets.selectedSet(getContext());
                if (!s.remove(sym)) s.add(sym);
                if (s.isEmpty()) return;                       // keep at least one
                Markets.setSelected(getContext(), s);
                invalidate();
            });
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), u(47));
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            if (isFocused()) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x24FFFFFF);
                rect.set(0, 0, w, h);
                cv.drawRoundRect(rect, u(14), u(14), p);
            }
            float x = u(14);
            Markets.Quote q = Markets.cached(getContext(), sym);
            if (editing) {
                boolean on = Markets.selectedSet(getContext()).contains(sym);
                p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setStrokeWidth(u(1.6f));
                p.setColor(on ? UP : 0x80FFFFFF);
                cv.drawCircle(x + u(10), h / 2f, u(10), p);
                if (on) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Color.WHITE);
                    p.setStrokeWidth(u(2));
                    p.setStrokeCap(Paint.Cap.ROUND);
                    cv.drawLine(x + u(5.5f), h / 2f, x + u(9), h / 2f + u(3.5f), p);
                    cv.drawLine(x + u(9), h / 2f + u(3.5f), x + u(15), h / 2f - u(3.5f), p);
                }
                x += u(34);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Color.WHITE);
            p.setTypeface(medium);
            p.setTextSize(u(17));
            cv.drawText(Markets.inCatalog(sym) || q == null ? It.t(Markets.nameOf(sym)) : q.name, x, h / 2f - u(2), p);
            p.setTypeface(regular);
            p.setTextSize(u(12));
            p.setColor(0x80FFFFFF);
            cv.drawText(sym.replace("=X", "").replace("=F", " fut."), x, h / 2f + u(15), p);
            if (q == null) {
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTextSize(u(17));
                cv.drawText("—", w - u(14), h / 2f + u(6), p);
                return;
            }
            double pct = q.pct();
            int col = Math.abs(pct) < 0.005 ? 0xFF8E8E93 : pct > 0 ? UP : DOWN;
            // Chart
            if (q.series.length > 2) {
                float x0 = w * 0.40f, x1 = w * 0.64f, y0 = u(14), y1 = h - u(14);
                float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
                for (float v : q.series) {
                    mn = Math.min(mn, v);
                    mx = Math.max(mx, v);
                }
                if (mx - mn < 1e-9f) mx = mn + 1;
                path.reset();
                for (int i = 0; i < q.series.length; i++) {
                    float px = x0 + (x1 - x0) * i / (q.series.length - 1f), py = y1 - (y1 - y0) * (q.series[i] - mn) / (mx - mn);
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u(1.8f));
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(col);
                cv.drawPath(path, p);
                path.lineTo(x1, h);
                path.lineTo(x0, h);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, y0, 0, h, (col & 0x00FFFFFF) | 0x40000000, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
                cv.drawPath(path, p);
                p.setShader(null);
            }
            // Change capsule and price
            String chip = String.format(Locale.getDefault(), "%+.2f%%", pct);
            p.setTypeface(semibold);
            p.setTextSize(u(14));
            float cw = p.measureText(chip) + u(20), cx = w - u(14) - cw;
            p.setStyle(Paint.Style.FILL);
            p.setColor((col & 0x00FFFFFF) | 0x33000000);
            rect.set(cx, h / 2f - u(14), cx + cw, h / 2f + u(14));
            cv.drawRoundRect(rect, u(14), u(14), p);
            p.setColor(col);
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText(chip, cx + cw / 2f, h / 2f + u(5), p);
            p.setTypeface(medium);
            p.setTextSize(u(17));
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(price(q.price), cx - u(14), h / 2f + u(6), p);
        }
    }

    static String price(double v) {
        if (v >= 10000) return String.format(Locale.getDefault(), "%,.0f", v);
        if (v >= 100) return String.format(Locale.getDefault(), "%,.2f", v);
        return String.format(Locale.getDefault(), "%.4f", v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Shot.maybe(this, getIntent());
        Clicks.init(this);
        Motion.init(this);
        FrameLayout root = UiKit.screen(this);
        LinearLayout panel = UiKit.glass(this, 780);
        root.addView(panel);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        TextView title = UiKit.text(this, "Markets", 30, "semibold", Color.WHITE);
        head.addView(title);
        stamp = UiKit.text(this, "", 14, "regular", 0x99FFFFFF);
        stamp.setGravity(Gravity.END);
        head.addView(stamp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(head);

        // Two views: the list the person keeps, and what is trending right now.
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setClipChildren(false);
        tabMine = UiKit.pill(this, "My list", true, v -> setTrending(false));
        tabTrending = UiKit.pill(this, "Trending now", false, v -> setTrending(true));
        tabs.addView(tabMine);
        LinearLayout.LayoutParams tm = new LinearLayout.LayoutParams(-2, -2);
        tm.leftMargin = u(10);
        tabs.addView(tabTrending, tm);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = u(12);
        panel.addView(tabs, tlp);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.px(this, 26));  // a soft edge says "more below" instead of a cut line
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToOutline(true);
        sv.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(true);
        sv.setClipToPadding(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(true);
        list.setPadding(u(6), u(4), u(6), u(4));
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, u(252));
        slp.topMargin = u(8);
        panel.addView(sv, slp);

        // Said on the page itself, not only in a document: prices come from a free public source and can be late.
        TextView notice = UiKit.text(this, "Prices can be delayed. For information only, not financial advice.", 13, "regular", 0x99FFFFFF);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = u(8);
        panel.addView(notice, np);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        TextView refresh = UiKit.pill(this, "Refresh", true, v -> reload());
        choose = UiKit.pill(this, "Choose indices", false, v -> {
            editing = !editing;
            choose.setText(It.t(editing ? "Done" : "Choose indices"));
            rebuild();
        });
        TextView back = UiKit.pill(this, "Back", false, v -> finish());
        buttons.addView(refresh);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-2, -2);
        m.leftMargin = u(12);
        buttons.addView(choose, m);
        buttons.addView(back, m);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = u(14);
        panel.addView(buttons, blp);
        UiKit.fit(buttons);

        setContentView(root);
        rebuild();
        refresh.requestFocus();
        panel.setAlpha(0f);
        panel.setTranslationY(u(14));
        panel.animate().alpha(1f).translationY(0).setDuration(Motion.d(450)).start();
        reload();
    }

    void setTrending(boolean on) {
        trending = on;
        editing = false;
        choose.setText(It.t("Choose indices"));
        choose.setVisibility(on ? View.GONE : View.VISIBLE);
        tabMine.setAlpha(on ? 0.6f : 1f);
        tabTrending.setAlpha(on ? 1f : 0.6f);
        rebuild();
        reload();
    }

    void rebuild() {
        list.removeAllViews();
        if (trending) {
            for (String s : Markets.trending(this)) list.addView(new Row(this, s));
        } else if (editing) {
            for (String[] e : Markets.CATALOG) list.addView(new Row(this, e[0]));
        } else {
            List<String> sel = Markets.selected(this);
            for (String s : sel) list.addView(new Row(this, s));
        }
        long latest = 0;
        for (String s : trending ? Markets.trending(this) : Markets.selected(this)) {
            Markets.Quote q = Markets.cached(this, s);
            if (q != null) latest = Math.max(latest, q.at);
        }
        stamp.setText(latest == 0 ? It.t("Loading…")
                : It.t("Updated") + " " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(latest)) + "  ·  Yahoo Finance");
    }

    void reload() {
        Runnable after = () -> {
            if (!isDestroyed()) {
                for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).invalidate();
                if (!editing) rebuild();
            }
        };
        if (trending) Markets.refreshTrending(this, after); else Markets.refresh(this, after);
    }
}
