package org.lumesuite.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * News on the TV: the headlines on the left, and on the right the one in focus with its picture and summary. The
 * newsrooms follow the TV's country (see {@link Region}); "Newsrooms" lists all of them to switch on or off.
 */
public class NewsActivity extends Activity {
    final Handler ui = new Handler(Looper.getMainLooper());
    LinearLayout list;
    ImageView pic;
    TextView heroTitle, heroMeta, heroText, choose, status;
    List<Suggestions.Item> items = new ArrayList<>();
    final Map<String, Bitmap> images = new HashMap<>();
    boolean editing, loading;
    /** 0 everything, 1 the TV's country, 2 the world, 3 the city. */
    int scope;
    TextView scopeBtn;

    int u(float v) {
        return Ui.px(this, v);
    }

    String scopeLabel() {
        String name = scope == 0 ? It.t("Everything") : scope == 1 ? Region.homeLabel() : scope == 2 ? It.t("World") : News.city(this);
        return It.t("Show") + ": " + (name.isEmpty() ? It.t("Everything") : name);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Shot.maybe(this, getIntent());
        Clicks.init(this);
        Motion.init(this);
        FrameLayout root = UiKit.screen(this);
        LinearLayout panel = UiKit.glass(this, 860);
        root.addView(panel);

        TextView title = UiKit.text(this, "News", 30, "semibold", Color.WHITE);
        panel.addView(title);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setClipChildren(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, u(300));
        bp.topMargin = u(12);
        panel.addView(body, bp);

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
        body.addView(sv, new LinearLayout.LayoutParams(u(380), -1));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -1, 1f);
        hp.leftMargin = u(26);
        body.addView(hero, hp);
        pic = new ImageView(this);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(u(16));
        pg.setColor(0x26FFFFFF);
        pic.setBackground(pg);
        pic.setClipToOutline(true);
        hero.addView(pic, new LinearLayout.LayoutParams(-1, u(150)));
        heroMeta = UiKit.text(this, "", 13, "medium", 0xB3FFFFFF);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
        mp.topMargin = u(10);
        hero.addView(heroMeta, mp);
        heroTitle = UiKit.text(this, "", 20, "semibold", Color.WHITE);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hero.addView(heroTitle);
        heroText = UiKit.text(this, "", 14, "regular", 0xCCFFFFFF);
        heroText.setMaxLines(3);
        heroText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = u(6);
        hero.addView(heroText, tp);

        status = UiKit.text(this, "", 14, "regular", 0x99FFFFFF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = u(8);
        panel.addView(status, sp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        TextView refresh = UiKit.pill(this, "Refresh", true, v -> load());
        choose = UiKit.pill(this, "Newsrooms", false, v -> {
            editing = !editing;
            choose.setText(It.t(editing ? "Done" : "Newsrooms"));
            if (editing) showSources(); else load();
        });
        scopeBtn = UiKit.pill(this, scopeLabel(), false, v -> {
            scope = (scope + 1) % 4;
            scopeBtn.setText(scopeLabel());
            load();
        });
        TextView back = UiKit.pill(this, "Back", false, v -> finish());
        buttons.addView(refresh);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-2, -2);
        m.leftMargin = u(12);
        buttons.addView(scopeBtn, m);
        buttons.addView(choose, m);
        buttons.addView(back, m);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = u(10);
        panel.addView(buttons, blp);
        UiKit.fit(buttons);

        setContentView(root);
        refresh.requestFocus();
        panel.setAlpha(0f);
        panel.setTranslationY(u(14));
        panel.animate().alpha(1f).translationY(0).setDuration(Motion.d(450)).start();
        load();
    }

    TextView row(String line1, String line2, boolean check, boolean on) {
        TextView t = UiKit.text(this, (check ? (on ? "✓  " : "     ") : "") + line1 + (line2.isEmpty() ? "" : "\n" + line2), 15, "medium", Color.WHITE);
        t.setMaxLines(3);
        t.setPadding(u(14), u(9), u(14), u(9));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(14));
        g.setColor(0x00000000);
        t.setBackground(g);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? 0x2EFFFFFF : 0x00000000);
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(Motion.d(160)).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = u(4);
        t.setLayoutParams(lp);
        return t;
    }

    void showHero(Suggestions.Item it) {
        heroMeta.setText(it.genres + (it.published > 0 ? "  ·  " + News.age(it.published) : ""));
        heroTitle.setText(it.title);
        heroText.setText(it.overview);
        Bitmap bm = images.get(it.backdrop);
        pic.setImageBitmap(bm);
        if (bm == null && !it.backdrop.isEmpty()) {
            final String key = it.backdrop;
            new Thread(() -> {
                Bitmap b = Suggestions.image(this, key, "w780", 760);
                if (b == null) return;
                images.put(key, b);
                ui.post(() -> {
                    if (!isDestroyed() && heroTitle.getText().toString().equals(it.title)) pic.setImageBitmap(b);
                });
            }).start();
        }
    }

    void load() {
        if (loading) return;
        loading = true;
        editing = false;
        choose.setText(It.t("Newsrooms"));
        status.setText(It.t("Loading…"));
        new Thread(() -> {
            List<Suggestions.Item> got = News.fetch(this, 12, scope == 0 ? null : scope == 1 ? News.INDIA : scope == 2 ? News.WORLD : News.LOCAL);
            ui.post(() -> {
                loading = false;
                if (isDestroyed()) return;
                items = got;
                list.removeAllViews();
                status.setText(got.isEmpty() ? (scope == 3 ? It.f("No newsroom with local news for %s yet. Choose another view.", News.city(this)) : It.t("No news right now. Check the connection.")) : "");
                for (Suggestions.Item it : got) {
                    String src = it.genres;
                    TextView r = row(it.title, src, false, false);
                    r.setMaxLines(3);
                    r.setOnFocusChangeListener(((View.OnFocusChangeListener) r.getOnFocusChangeListener()) == null ? null : wrap(r, it));
                    list.addView(r);
                }
                if (!got.isEmpty()) showHero(got.get(0));
            });
        }).start();
    }

    /** Keeps the row's own focus look and also shows its story on the right. */
    View.OnFocusChangeListener wrap(TextView r, Suggestions.Item it) {
        View.OnFocusChangeListener base = r.getOnFocusChangeListener();
        return (v, has) -> {
            base.onFocusChange(v, has);
            if (has) showHero(it);
        };
    }

    void showSources() {
        list.removeAllViews();
        status.setText(It.t("Choose the newsrooms to show. OK switches one on or off."));
        Set<String> chosen = News.sources(this);
        Set<String> mine = News.defaultSources();
        List<News.Source> order = new ArrayList<>();
        for (News.Source s : News.SOURCES) if (mine.contains(s.id)) order.add(s);   // this country's first
        for (News.Source s : News.SOURCES) if (!mine.contains(s.id)) order.add(s);
        for (News.Source s : order) {
            TextView r = row(s.name, "", true, chosen.contains(s.id));
            r.setOnClickListener(v -> {
                Set<String> cur = new HashSet<>(News.sources(this));
                if (!cur.remove(s.id)) cur.add(s.id);
                if (cur.isEmpty()) return;
                News.setSources(this, cur);
                r.setText((cur.contains(s.id) ? "✓  " : "     ") + s.name);
            });
            list.addView(r);
        }
        pic.setImageBitmap(null);
        heroMeta.setText("");
        heroTitle.setText("");
        heroText.setText("");
    }
}
