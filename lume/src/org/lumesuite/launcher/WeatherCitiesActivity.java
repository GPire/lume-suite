package org.lumesuite.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The weather in more than one place: the current place first, then any cities added here, each as a glass card with
 * the condition icon, the temperature and the day's high and low. "Add a city" searches by name; "Edit" lets a city
 * be removed. Forecasts load in the background; the page opens at once.
 */
public class WeatherCitiesActivity extends Activity {
    final Handler ui = new Handler(Looper.getMainLooper());
    LinearLayout list;
    TextView edit, status;
    boolean editing;
    final Map<String, Weather.Data> data = new HashMap<>();
    List<Weather.Place> saved = new ArrayList<>();

    int u(float v) {
        return Ui.px(this, v);
    }

    /** A place: icon, name and condition on the left, temperature and high/low on the right. */
    final class Row extends View {
        final Weather.Place place;     // null = the current place
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        final RectF r = new RectF();
        final android.graphics.Typeface regular, medium, semibold;

        Row(Context c, Weather.Place place) {
            super(c);
            this.place = place;
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
                if (place == null) return;
                if (editing) {
                    saved.remove(place);
                    Weather.setCities(getContext(), saved);
                    rebuild();
                } else {
                    makeHome(place);
                }
            });
        }

        String key() {
            return place == null ? "here" : place.name + place.lat;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), u(62));
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(isFocused() ? 0x33FFFFFF : 0x14FFFFFF);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, u(16), u(16), p);
            Weather.Data d = place == null ? Weather.cached(getContext()) : data.get(key());
            float x = u(16);
            if (editing && place != null) {
                p.setColor(0xFFFF453A);
                cv.drawCircle(x + u(10), h / 2f, u(10), p);
                p.setColor(Color.WHITE);
                p.setStrokeWidth(u(2));
                cv.drawLine(x + u(5), h / 2f, x + u(15), h / 2f, p);
                x += u(32);
            }
            if (d != null) {
                Bitmap ic = WeatherIcons.get(d.code, d.day, u(40));
                if (ic != null) cv.drawBitmap(ic, x, (h - ic.getHeight()) / 2f, null);
            }
            x += u(54);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Color.WHITE);
            p.setTypeface(medium);
            p.setTextSize(u(19));
            String name = place == null ? (Weather.place(getContext()).isEmpty() ? It.t("My place") : Weather.place(getContext())) : place.name;
            cv.drawText(name, x, h / 2f - u(2), p);
            p.setTypeface(regular);
            p.setTextSize(u(13));
            p.setColor(0xB3FFFFFF);
            String sub = d == null ? It.t("Loading…") : Weather.describe(d.code, d.day)
                    + (place == null ? "  ·  " + It.t("My place") : place.detail == null || place.detail.isEmpty() ? "" : "  ·  " + place.detail);
            cv.drawText(sub, x, h / 2f + u(17), p);
            if (d != null) {
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTypeface(regular);
                p.setTextSize(u(32));
                p.setColor(Color.WHITE);
                cv.drawText(d.temp + "°", w - u(18), h / 2f + u(4), p);
                p.setTypeface(medium);
                p.setTextSize(u(13));
                p.setColor(0xB3FFFFFF);
                cv.drawText("H:" + d.max + "°  L:" + d.min + "°", w - u(18), h / 2f + u(22), p);
            }
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Shot.maybe(this, getIntent());
        Clicks.init(this);
        Motion.init(this);
        FrameLayout root = UiKit.screen(this);
        LinearLayout panel = UiKit.glass(this, 700);
        root.addView(panel);
        panel.addView(UiKit.text(this, "Weather", 30, "semibold", Color.WHITE));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.px(this, 26));  // a soft edge says "more below" instead of a cut line
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(true);
        sv.setClipToPadding(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(u(4), u(4), u(4), u(4));
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, u(230));
        slp.topMargin = u(12);
        panel.addView(sv, slp);

        status = UiKit.text(this, "", 14, "regular", 0x99FFFFFF);
        panel.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        TextView add = UiKit.pill(this, "Add a city", true, v -> askCity());
        edit = UiKit.pill(this, "Edit", false, v -> {
            editing = !editing;
            edit.setText(It.t(editing ? "Done" : "Edit"));
            rebuild();
        });
        TextView auto = UiKit.pill(this, "Use my location", false, v -> {
            if (Weather.manual(this)) {
                Weather.Place old = new Weather.Place();
                old.name = Weather.place(this);
                old.detail = "";
                old.lat = Weather.prefs(this).getFloat(Weather.K_LAT, 0);
                old.lon = Weather.prefs(this).getFloat(Weather.K_LON, 0);
                if (!old.name.isEmpty()) saved.add(old);
                Weather.setCities(this, saved);
            }
            Weather.setPlace(this, null);
            Weather.refresh(this, true, () -> ui.post(this::rebuild));
            rebuild();
        });
        TextView back = UiKit.pill(this, "Back", false, v -> finish());
        buttons.addView(add);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-2, -2);
        m.leftMargin = u(12);
        buttons.addView(edit, m);
        buttons.addView(auto, m);
        buttons.addView(back, m);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = u(12);
        panel.addView(buttons, blp);
        UiKit.fit(buttons);

        setContentView(root);
        add.requestFocus();
        panel.setAlpha(0f);
        panel.setTranslationY(u(14));
        panel.animate().alpha(1f).translationY(0).setDuration(Motion.d(450)).start();
        saved = Weather.cities(this);
        rebuild();
        loadAll();
    }

    /**
     * The city shown on Home becomes this one. The place that was shown (when it was a chosen city) goes back into the
     * list, so nothing is lost; an automatic location can be brought back with "Use my location".
     */
    void makeHome(Weather.Place pl) {
        if (Weather.manual(this)) {
            Weather.Place old = new Weather.Place();
            old.name = Weather.place(this);
            old.detail = "";
            old.lat = Weather.prefs(this).getFloat(Weather.K_LAT, 0);
            old.lon = Weather.prefs(this).getFloat(Weather.K_LON, 0);
            if (!old.name.isEmpty()) saved.add(old);
        }
        saved.remove(pl);
        Weather.setCities(this, saved);
        Weather.setPlace(this, pl);
        Weather.refresh(this, true, () -> ui.post(this::rebuild));
        rebuild();
    }

    void rebuild() {
        list.removeAllViews();
        list.addView(new Row(this, null), rowParams());
        for (Weather.Place p : saved) list.addView(new Row(this, p), rowParams());
        status.setText(saved.isEmpty() ? It.t("Add the cities you care about: home, family, a place you are going to.") : It.t("OK on a city shows it on Home."));
    }

    LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = u(8);
        return lp;
    }

    void loadAll() {
        final boolean f = Weather.units(this).equals("f");
        for (Weather.Place pl : new ArrayList<>(saved)) {
            new Thread(() -> {
                try {
                    Weather.Data d = Weather.fetchFor(pl.lat, pl.lon, f);
                    ui.post(() -> {
                        if (isDestroyed()) return;
                        data.put(pl.name + pl.lat, d);
                        for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).invalidate();
                    });
                } catch (Exception e) {
                    android.util.Log.w("Lume", "weather " + pl.name + ": " + e);
                }
            }).start();
        }
    }

    void askCity() {
        EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint(It.t("City name"));
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        Clicks.dialog(this).setTitle(It.t("Add a city")).setView(box)
                .setPositiveButton(It.t("Search"), (d, w) -> search(in.getText().toString()))
                .setNegativeButton(It.t("Cancel"), null).show();
        in.requestFocus();
    }

    void search(String q) {
        if (q.trim().isEmpty()) return;
        status.setText(It.t("Searching…"));
        new Thread(() -> {
            List<Weather.Place> found;
            try {
                found = Weather.search(q);
            } catch (Exception e) {
                found = new ArrayList<>();
            }
            final List<Weather.Place> res = found;
            ui.post(() -> {
                if (isDestroyed()) return;
                status.setText("");
                if (res.isEmpty()) {
                    status.setText(It.t("No city found. Check the name and the connection."));
                    return;
                }
                String[] names = new String[res.size()];
                for (int i = 0; i < names.length; i++) names[i] = res.get(i).name + (res.get(i).detail == null || res.get(i).detail.isEmpty() ? "" : "  ·  " + res.get(i).detail);
                Clicks.dialog(this).setTitle(It.t("Choose a city")).setItems(names, (d, which) -> {
                    saved.add(res.get(which));
                    Weather.setCities(this, saved);
                    rebuild();
                    loadAll();
                }).setNegativeButton(It.t("Cancel"), null).show();
            });
        }).start();
    }
}
