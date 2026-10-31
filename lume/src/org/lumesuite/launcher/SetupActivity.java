package org.lumesuite.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * First-time setup, in the style of Apple TV's own setup: one question per page, a large title
 * and a short line of help at the top, choices as a centred list (white when focused), and the
 * page's buttons (Back, Continue) as pills under it, so moving on never looks like a choice.
 * Each row says what it is: an On/Off switch, a radio dot (one of a list), or an action with its
 * state on the right ("Allow ›", "✓ Allowed"). Choices change in place; nothing moves on by itself. Pages: Welcome (in many languages), your profile, look, top shelf, weather, presentation,
 * home screen & permissions, done. Shown once; Settings \u2192 Home \u2192 Run Setup Again repeats it.
 */
public class SetupActivity extends Activity {
    static final String K_DONE = "setup_done";

    FrameLayout root;
    ImageView backdrop;
    LinearLayout page;
    int index = 0;
    final Handler handler = new Handler(Looper.getMainLooper());

    interface Step {
        void build();
    }

    final List<Step> steps = new ArrayList<>();

    static boolean done(Activity a) {
        return a.getSharedPreferences("setup", MODE_PRIVATE).getBoolean(K_DONE, false);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Shot.maybe(this, getIntent());
        Clicks.init(this);
        Motion.init(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View dim = new View(this);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x66000000, 0xB3000000}));
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(UiKit.grain(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        page.setPadding(0, u(54), 0, u(28));
        root.addView(page, plp);
        setContentView(root);
        Clicks.quiet(root);

        if (getIntent().getBooleanExtra("only_clean", false)) {   // the guided cleaning, from Settings or from first run
            onlyClean = true;
            cleanNeedsComputer = getIntent().getBooleanExtra("computer", false);   // adb: --ez computer true shows the computer page
            steps.add(this::cleanGuide);
            steps.add(this::cleanChoose);
            steps.add(this::cleanDone);
            show(0, 0);
            return;
        }
        onlyLook = getIntent().getBooleanExtra("only_look", false);   // from Settings: just the looks, then back
        if (onlyLook) {
            steps.add(this::look);
            show(0, 0);
            return;
        }
        steps.add(this::hello);
        steps.add(this::profile);
        steps.add(this::look);
        steps.add(this::optimize);
        steps.add(this::cleanOffer);
        // Home content, weather and presentation decide themselves (our gallery on the shelf, weather on, presentation off):
        // a first run is five pages, and each of those stays one step away in Settings.
        steps.add(this::homeAndPermissions);
        steps.add(this::finish_);
        if (getIntent().getBooleanExtra("skip", false)) {  // adb: --ez skip true marks setup as done
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).apply();
            finish();
            return;
        }
        show(getIntent().getIntExtra("step", 0), 0);  // adb: --ei step N opens a page directly
    }

    int u(float v) {
        return Ui.px(this, v);
    }

    // ---------------------------------------------------------------- navigation

    void show(int i, int dir) {
        handler.removeCallbacksAndMessages(null);
        index = Math.max(0, Math.min(i, steps.size() - 1));
        page.animate().cancel();
        page.removeAllViews();
        firstChoice = null;
        primaryButton = null;
        buttonsFocus = null;
        steps.get(index).build();
        View f = firstChoice != null ? firstChoice : primaryButton;
        if (f != null) f.requestFocus();
        if (dir != 0) {
            // A new page: its parts settle in one after another (title, help, choices, buttons).
            List<View> parts = new ArrayList<>();
            for (int k = 0; k < page.getChildCount(); k++) parts.add(page.getChildAt(k));
            Motion.cascade(parts, u(10));
        }
        // New page: slide and fade. Same page re-drawn (back from a system screen): a quick fade only.
        page.setAlpha(dir == 0 ? 0.6f : 0f);
        page.setTranslationX(Motion.dist(u(30)) * dir);
        page.animate().alpha(1f).translationX(0).setDuration(Motion.d(dir == 0 ? 120 : 320)).start();
    }

    boolean onlyLook, onlyClean;

    void next() {
        if (onlyLook) { finish(); return; }
        if (onlyClean && index == steps.size() - 1) { finish(); return; }
        show(index + 1, 1);
    }

    void back() {
        if (onlyLook) { finish(); return; }
        if (onlyClean && index == 0) { finish(); return; }
        if (index > 0) show(index - 1, -1);
    }

    @Override
    public void onBackPressed() {
        back();  // the first page has nowhere to go back to: setup stays until it is finished
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (index > 0) show(index, 0);  // show what is now allowed
    }

    /** Back from a system screen (a permission, Accessibility, choosing a city): show the new state. */
    boolean leftForSystemScreen;

    @Override
    protected void onPause() {
        super.onPause();
        leftForSystemScreen = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (leftForSystemScreen && index > 0) show(index, 0);
        leftForSystemScreen = false;
    }

    // ---------------------------------------------------------------- building blocks

    TextView title(String s) {
        TextView t = new HTextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Ui.font(this, "display"));
        t.setLetterSpacing(-0.02f);
        Ui.textSize(t, 34);
        t.setGravity(Gravity.CENTER);
        page.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    TextView subtitle(String s) {
        TextView t = new HTextView(this);
        t.setText(s);
        t.setTextColor(0xB3FFFFFF);
        t.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(t, 14);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(520), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = u(10);
        lp.bottomMargin = u(22);
        page.addView(t, lp);
        return t;
    }

    LinearLayout list() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        page.addView(l, new LinearLayout.LayoutParams(u(440), ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** What a choice row's right side shows. */
    static final int SWITCH = 0, RADIO = 1, ACTION = 2;

    /** First choice on the page (or the selected one in a list), focused when the page opens. */
    View firstChoice;
    View primaryButton;
    /** Called when the page's buttons take focus (e.g. Look puts back the chosen background). */
    Runnable buttonsFocus;

    /**
     * A choice row. SWITCH: an On/Off switch, toggled in place. RADIO: one of a list, with a dot.
     * ACTION: opens something (a dialog or a system screen); the right side says its state, e.g.
     * "Allow ›" or "✓ Allowed". Moving on is never a row: it is the button under the list.
     */
    final class Row {
        final LinearLayout view = new LinearLayout(SetupActivity.this);
        final TextView label = new HTextView(SetupActivity.this), detail = new HTextView(SetupActivity.this);
        final TextView status = new HTextView(SetupActivity.this);
        final FrameLayout track = new FrameLayout(SetupActivity.this);
        final View knob = new View(SetupActivity.this);
        final GradientDrawable bg = new GradientDrawable(), trackBg = new GradientDrawable(), knobBg = new GradientDrawable();
        final int kind;
        boolean on, focused, ok;

        Row(int kind) {
            this.kind = kind;
        }

        void set(boolean v) {
            on = v;
            paint();
        }

        /** ACTION rows: the right-hand text, green when done. */
        void status(String s, boolean done) {
            status.setText(s);
            ok = done;
            paint();
        }

        void detail(String s) {
            detail.setText(s);
            detail.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
        }

        void paint() {
            bg.setColor(focused ? 0xF2FFFFFF : 0x26FFFFFF);
            label.setTextColor(focused ? 0xFF111114 : Color.WHITE);
            detail.setTextColor(focused ? 0x99111114 : 0x99FFFFFF);
            int green = focused ? 0xFF1E9E48 : 0xFF30D158;
            if (kind == SWITCH) {
                trackBg.setColor(on ? green : focused ? 0x33111114 : 0x40FFFFFF);
                knob.animate().translationX(on ? u(14) : 0).setDuration(Motion.d(150)).start();
            } else if (kind == RADIO) {
                trackBg.setColor(on ? green : 0);
                trackBg.setStroke(u(1.5f), on ? green : focused ? 0x80111114 : 0x80FFFFFF);
                status.setText(on ? "✓" : "");
                status.setTextColor(Color.WHITE);
            } else {
                status.setTextColor(ok ? green : focused ? 0x99111114 : 0x99FFFFFF);
            }
        }
    }

    Row row(LinearLayout list, String label, String detail, int kind, boolean on, Runnable action) {
        Row r = new Row(kind);
        LinearLayout v = r.view;
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFocusable(true);
        v.setClickable(true);
        v.setPadding(u(18), u(8), u(16), u(8));
        r.bg.setCornerRadius(u(12));
        v.setBackground(r.bg);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        r.label.setText(label);
        r.label.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(r.label, 15);
        texts.addView(r.label);
        r.detail.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(r.detail, 12);
        r.detail.setSingleLine(true);
        r.detail.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(r.detail);
        r.detail(detail);
        v.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (kind == SWITCH) {
            // A small On/Off switch (track and knob).
            r.trackBg.setCornerRadius(u(9));
            r.track.setBackground(r.trackBg);
            r.knobBg.setShape(GradientDrawable.OVAL);
            r.knobBg.setColor(Color.WHITE);
            r.knob.setBackground(r.knobBg);
            FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(u(14), u(14), Gravity.CENTER_VERTICAL);
            klp.leftMargin = u(2);
            r.track.addView(r.knob, klp);
            r.knob.setTranslationX(on ? u(14) : 0);
            v.addView(r.track, new LinearLayout.LayoutParams(u(32), u(18)));
        } else if (kind == RADIO) {
            r.trackBg.setShape(GradientDrawable.OVAL);
            r.status.setBackground(r.trackBg);
            r.status.setGravity(Gravity.CENTER);
            r.status.setTypeface(Ui.font(this, "semibold"));
            Ui.textSize(r.status, 11);
            v.addView(r.status, new LinearLayout.LayoutParams(u(18), u(18)));
        } else {
            r.status.setTypeface(Ui.font(this, "medium"));
            Ui.textSize(r.status, 13);
            v.addView(r.status);
        }
        r.on = on;
        r.paint();
        v.setOnFocusChangeListener((x, has) -> {
            r.focused = has;
            r.paint();
            x.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(Motion.d(180)).start();
        });
        v.setOnClickListener(x -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                detail == null || detail.isEmpty() ? u(46) : u(56));
        lp.bottomMargin = u(8);
        list.addView(v, lp);
        if (firstChoice == null || kind == RADIO && on) firstChoice = v;
        return r;
    }

    /** One of a list: selecting a row clears the others. */
    void select(List<Row> group, Row chosen) {
        for (Row r : group) r.set(r == chosen);
    }

    /**
     * The page's buttons, under the choices: Back (from the second page on) and the main one.
     * Pill buttons, so they never look like a choice; Down from the list reaches them.
     */
    void buttons(String primary, Runnable go) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setClipChildren(false);
        if (index > 0) bar.addView(button("Back", false, this::back));
        primaryButton = button(primary, true, go);
        bar.addView(primaryButton);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = u(14);
        page.addView(bar, lp);
    }

    TextView button(String text, boolean primary, Runnable go) {
        TextView b = new HTextView(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Ui.font(this, primary ? "semibold" : "medium"));
        Ui.textSize(b, 15);
        b.setFocusable(true);
        b.setClickable(true);
        b.setMinWidth(u(150));
        b.setPadding(u(26), 0, u(26), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(20));
        int idle = primary ? 0x59FFFFFF : 0x26FFFFFF;
        g.setColor(idle);
        b.setBackground(g);
        b.setTextColor(Color.WHITE);
        b.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? 0xFFFFFFFF : idle);
            b.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(Motion.d(180)).start();
            if (has && buttonsFocus != null) buttonsFocus.run();
        });
        b.setOnClickListener(v -> go.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u(40));
        lp.leftMargin = lp.rightMargin = u(8);
        b.setLayoutParams(lp);
        return b;
    }

    void dots() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i < steps.size() - 1; i++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == index ? Color.WHITE : 0x59FFFFFF);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(6), u(6));
            lp.leftMargin = lp.rightMargin = u(4);
            d.addView(dot, lp);
        }
        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        page.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** The Art Gallery look as a preview: a night-to-gold painting in a gilded frame, with the rings of a starry sky. */
    static Bitmap galleryThumb(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, h, new int[]{0xFF11315A, 0xFF2A69A8, 0xFFD6A23F}, new float[]{0f, 0.58f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < 4; k++) {
            p.setColor(Color.argb(210 - k * 45, 255, 236, 170));
            p.setStrokeWidth(Math.max(2f, h * 0.022f));
            float r = h * (0.09f + k * 0.055f);
            c.drawCircle(w * 0.72f, h * 0.34f, r, p);
        }
        p.setColor(0xEBF0D68C);
        p.setStrokeWidth(Math.max(3f, h * 0.035f));
        float in = h * 0.07f;
        c.drawRoundRect(new android.graphics.RectF(in, in, w - in, h - in), in * 0.6f, in * 0.6f, p);
        return b;
    }

    void setBackdrop(String style) {
        Bitmap b = MainActivity.makeWallpaperBase(style);
        backdrop.setImageDrawable(new BitmapDrawable(getResources(), b));
        backdrop.animate().alpha(1f).setDuration(Motion.d(300)).start();
    }

    // ---------------------------------------------------------------- pages

    /** Welcome, then Get Started. */
    void hello() {
        backdrop.animate().alpha(0f).setDuration(Motion.d(300)).start();
        View top = new View(this);
        page.addView(top, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView hi = title("Benvenuto");
        Ui.textSize(hi, 64);
        hi.setAlpha(0f);
        hi.setTranslationY(u(12));
        hi.animate().alpha(1f).translationY(0).setDuration(Motion.d(700)).start();
        // "Welcome" in the product's own six languages first (the one on this TV leads), then a few of the world's.
        String[] words = {"Benvenuto", "Welcome", "Bienvenido", "Willkommen", "Bienvenue", "Bem-vindo", "Welkom", "ようこそ", "欢迎"};
        int[] k = {0};
        Runnable[] cycle = new Runnable[1];
        cycle[0] = () -> {
            k[0] = (k[0] + 1) % words.length;
            hi.animate().alpha(0f).setDuration(Motion.d(350)).withEndAction(() -> {
                hi.setText(words[k[0]]);
                hi.animate().alpha(1f).setDuration(Motion.d(450)).start();
            }).start();
            handler.postDelayed(cycle[0], 2200);
        };
        handler.postDelayed(cycle[0], 2400);
        TextView s = subtitle("Let’s set up Lume. It takes about a minute.");
        Ui.textSize(s, 17);
        buttons("Get Started", this::next);
        View mid = new View(this);
        page.addView(mid, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView by = new HTextView(this);
        by.setText("Lume");
        by.setTextColor(0x80FFFFFF);
        by.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(by, 13);
        by.setGravity(Gravity.CENTER);
        page.addView(by, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void profile() {
        title("Create Your Profile");
        subtitle("Pick a name and an avatar. Each profile keeps its own apps, background and Home Screen; up to three people can have one.");
        Profiles.Profile p = Profiles.current(this);
        LinearLayout l = list();
        Row name = row(l, "Name", null, ACTION, false, () -> { });  // its click is set below (needs the row)
        name.status(p.name + "  ›", false);
        name.view.setOnClickListener(v -> {
            EditText in = new EditText(this);
            in.setSingleLine(true);
            in.setText(p.name);
            in.setSelection(in.getText().length());
            int pad = u(14);
            FrameLayout box = new FrameLayout(this);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(in);
            Clicks.dialog(this)
                    .setTitle(It.t("Your name")).setView(box)
                    .setPositiveButton(It.t("OK"), (d, w) -> {
                        String n = in.getText().toString().trim();
                        if (!n.isEmpty()) {
                            p.name = n;
                            Profiles.update(this, p);
                            name.status(n + "  ›", false);
                        }
                    }).setNegativeButton(It.t("Cancel"), null).show();
            in.requestFocus();
        });
        // Avatar: glass discs in the profile colours, with your initials, as on Apple TV. The chosen one is ringed.
        TextView hint = new HTextView(this);
        hint.setText("Colour");
        hint.setTextColor(0x99FFFFFF);
        hint.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(hint, 12);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = u(6);
        page.addView(hint, hlp);
        LinearLayout avatars = new LinearLayout(this);
        avatars.setOrientation(LinearLayout.HORIZONTAL);
        avatars.setGravity(Gravity.CENTER);
        avatars.setClipChildren(false);
        List<GradientDrawable> rings = new ArrayList<>();
        for (int i = 0; i < Profiles.COLORS.length; i++) {
            final int ci = i;
            Profiles.Profile preview = new Profiles.Profile();
            preview.name = p.name;
            preview.color = ci;
            ImageView t = new ImageView(this);
            t.setImageBitmap(Profiles.avatar(this, preview, u(46) * 2));
            t.setFocusable(true);
            t.setClickable(true);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Color.TRANSPARENT);
            if (ci == p.color) g.setStroke(u(2.5f), 0xFF30D158);
            rings.add(g);
            t.setForeground(g);
            t.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.2f : 1f).scaleY(has ? 1.2f : 1f).setDuration(Motion.d(160)).start());
            t.setOnClickListener(v -> {
                p.color = ci;
                Profiles.update(this, p);
                for (GradientDrawable r : rings) r.setStroke(0, 0);
                g.setStroke(u(2.5f), 0xFF30D158);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(46), u(46));
            lp.leftMargin = lp.rightMargin = u(5);
            avatars.addView(t, lp);
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = u(6);
        page.addView(avatars, rlp);
        buttons("Continue", this::next);
        dots();
    }

    /** Backgrounds preview live behind the page as you move through them; OK picks one. */
    void look() {
        title("Choose a Look");
        subtitle("The background behind your apps. Move over a look to preview it, press OK to keep it.");
        final String[] keys = MainActivity.WALL_KEYS;
        final String[] cur = {Profiles.homePrefs(this).getString("wallpaper", "aurora")};
        setBackdrop(cur[0]);
        final int cw = u(220), ch = u(124);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_HORIZONTAL);
        grid.setClipChildren(false);
        final List<TextView> ticks = new ArrayList<>();
        final List<ImageView> shots = new ArrayList<>();
        LinearLayout rowL = null;
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            if (i % 3 == 0) {
                rowL = new LinearLayout(this);
                rowL.setOrientation(LinearLayout.HORIZONTAL);
                rowL.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = i == 0 ? 0 : u(12);
                grid.addView(rowL, rp);
            }
            FrameLayout card = new FrameLayout(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(u(14));
            bg.setColor(0xFF1C1C1E);
            card.setBackground(bg);
            card.setClipToOutline(true);
            ImageView shot = new ImageView(this);
            shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shots.add(shot);
            card.addView(shot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View shade = new View(this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xB3000000}));
            card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(54), Gravity.BOTTOM));
            TextView name = new HTextView(this);
            name.setText(MainActivity.WALL_NAMES[i]);
            name.setTextColor(Color.WHITE);
            name.setTypeface(Ui.font(this, "semibold"));
            Ui.textSize(name, 15);
            name.setPadding(u(12), 0, u(12), u(8));
            card.addView(name, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
            TextView tick = new TextView(this);
            tick.setText("\u2713");
            tick.setTextColor(Color.WHITE);
            tick.setGravity(Gravity.CENTER);
            Ui.textSize(tick, 14);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0xFF30D158);
            tick.setBackground(dot);
            tick.setVisibility(key.equals(cur[0]) ? View.VISIBLE : View.INVISIBLE);
            ticks.add(tick);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(u(26), u(26), Gravity.TOP | Gravity.END);
            tlp.topMargin = tlp.rightMargin = u(10);
            card.addView(tick, tlp);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(u(14));
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(u(3), Color.WHITE);
            final int idx = i;
            card.setFocusable(true);
            card.setClickable(true);
            card.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(Motion.d(160)).start();
                v.setForeground(has ? ring : null);
                if (has) setBackdrop(key);
            });
            card.setOnClickListener(v -> {
                Profiles.homePrefs(this).edit().putString("wallpaper", key).apply();
                cur[0] = key;
                for (int k = 0; k < ticks.size(); k++) ticks.get(k).setVisibility(k == idx ? View.VISIBLE : View.INVISIBLE);
            });
            if (key.equals(cur[0]) || firstChoice == null && i == 0) firstChoice = card;
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cw, ch);
            clp.leftMargin = clp.rightMargin = u(8);
            rowL.addView(card, clp);
        }
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = u(8);
        page.addView(grid, glp);
        // The previews are drawn one after another off the main thread, so the page opens at once.
        new Thread(() -> {
            for (int i = 0; i < keys.length; i++) {
                Bitmap b = "galleria".equals(keys[i]) ? galleryThumb(cw, ch) : MainActivity.makeWallpaperBase(keys[i]);
                if (b == null) continue;
                final Bitmap s = Bitmap.createScaledBitmap(b, cw, ch, true);
                if (s != b) b.recycle();
                final ImageView target = shots.get(i);
                handler.post(() -> { if (!isDestroyed()) target.setImageBitmap(s); });
            }
        }).start();
        buttonsFocus = () -> setBackdrop(cur[0]);  // leaving the cards: show what was picked
        buttons(onlyLook ? "Done" : "Continue", this::next);
        if (!onlyLook) dots();
    }

    /** This profile's Home Screen: what the carousel shows, as switches. */
    void homeContent() {
        backdrop.animate().alpha(0.5f).setDuration(Motion.d(300)).start();
        title("Your Home Screen");
        subtitle("What plays in the carousel at the top of Home. Turn on any, all or none; you can change this in Settings.");
        LinearLayout l = list();
        boolean[] recs = {Suggestions.showRecs(this)}, news = {Suggestions.showNews(this)};
        Row[] r = new Row[1];
        r[0] = row(l, "Recommendations", "Popular movies and series on your streaming services", SWITCH, recs[0], () -> {
            recs[0] = !recs[0];
            Suggestions.setShelf(this, recs[0], news[0]);
            r[0].set(recs[0]);
        });
        Row[] n = new Row[1];
        n[0] = row(l, "News", "Headlines from your country, your city and the world", SWITCH, news[0], () -> {
            news[0] = !news[0];
            Suggestions.setShelf(this, recs[0], news[0]);
            n[0].set(news[0]);
        });
        // Continue Watching and apps' rows read the TV's Watch Next and channel lists (Android TV 8+):
        // turning one on asks for that access right here.
        boolean tv = WatchNext.supported();
        Row cw = tvRow(l, "Continue Watching", "Pick up shows and movies where you left off", "cw_enabled", tv);
        Row apps = tvRow(l, "From Your Apps", "Rows your apps publish, like YouTube’s", "app_rows", tv);
        if (!tv) {
            cw.detail("Needs Android TV 8 or later");
            apps.detail("Needs Android TV 8 or later");
        }
        buttons("Continue", this::next);
        dots();
    }

    /** A switch for a feature that needs the TV-listings permission; asks for it when turned on. */
    Row tvRow(LinearLayout l, String label, String detail, String key, boolean supported) {
        android.content.SharedPreferences hp = Profiles.homePrefs(this);
        boolean on = supported && hp.getBoolean(key, true);
        boolean needs = on && !Perms.hasTvListings(this);
        Row[] r = new Row[1];
        r[0] = row(l, label, needs ? "Needs your OK — press OK to allow" : detail, SWITCH, on && !needs, () -> {
            if (!supported) {
                android.widget.Toast.makeText(this, label + " needs Android TV 8 or later.", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            if (!Perms.hasTvListings(this)) {
                hp.edit().putBoolean(key, true).apply();
                Perms.requestTvListings(this);  // the page redraws with the answer
                return;
            }
            boolean v = !hp.getBoolean(key, true);
            hp.edit().putBoolean(key, v).apply();
            r[0].set(v);
        });
        return r[0];
    }

    void weather() {
        title("Weather");
        subtitle("A small weather card in the top-left corner of Home, with a 5-day forecast.");
        LinearLayout l = list();
        String city = Weather.place(this);
        boolean on = Weather.enabled(this), manual = Weather.manual(this);
        List<Row> group = new ArrayList<>();
        Row[] auto = new Row[1], pick = new Row[1], off = new Row[1];
        auto[0] = row(l, "Use My Location", city.isEmpty() || manual ? "Found from your internet connection" : "Looks like " + city,
                RADIO, on && !manual, () -> {
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
                    Weather.setPlace(this, null);
                    select(group, auto[0]);
                });
        pick[0] = row(l, "Choose a City…", manual ? Weather.place(this) : "Search by name (opens Settings)", RADIO, on && manual, () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
            startActivity(new Intent(this, SettingsActivity.class).putExtra("page", SettingsActivity.WEATHER));
        });
        off[0] = row(l, "Don’t Show Weather", null, RADIO, !on, () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, false).apply();
            Weather.changed(this);
            select(group, off[0]);
        });
        group.add(auto[0]);
        group.add(pick[0]);
        group.add(off[0]);
        if (city.isEmpty()) {
            int here = index;
            Weather.refresh(this, true, () -> {
                if (index == here) auto[0].detail(Weather.place(this).isEmpty() ? "Found from your internet connection"
                        : "Looks like " + Weather.place(this));
            });
        }
        buttons("Continue", this::next);
        dots();
    }

    /** Presentation is for the whole TV: switch it on and choose its options here, once. */
    /** Measures this box and applies the profile that suits it; the switch gives the generous defaults back. */
    void optimize() {
        title("Optimize your TV");
        subtitle("I measured this device and chose the best settings for smoothness and stability. You can change them any time.");
        Optimizer.Report r = Optimizer.measure(this);
        if (Optimizer.auto(this)) Optimizer.apply(this, r);
        LinearLayout l = list();
        row(l, "Device", r.hardware + "  \u00B7  " + r.cores + " core  \u00B7  " + It.f("profile %s", Optimizer.tierName(r.tier)),
                ACTION, false, () -> { });
        row(l, "Memory", It.f("%s total, %s free", Optimizer.gb(r.ramMb), Optimizer.gb(r.freeRamMb))
                + (r.tier == Optimizer.ESSENTIAL ? It.t(". Light animations and compact images") : ""), ACTION, false, () -> { });
        row(l, "Storage", It.f("%s free. Images use at most %d MB", Optimizer.gb(r.storageFreeMb),
                (int) (Optimizer.cacheLimit(this) / (1024 * 1024))), ACTION, false, () -> { });
        boolean on = Optimizer.auto(this);   // the connection is shown in TV health: five rows push the buttons past the safe area
        row(l, "Automatic optimization", on ? "On: adapts to this device" : "Off: animations and images at their best",
                SWITCH, on, () -> {
            if (Optimizer.auto(this)) Optimizer.release(this); else Optimizer.apply(this, Optimizer.measure(this));
            show(index, 0);
        });
        buttons("Continue", this::next);
        dots();
    }

    void presentation() {
        ImageView icon = new ImageView(this);
        icon.setImageBitmap(org.lumesuite.launcher.present.PresentIcon.banner(u(90) * 2, u(54) * 2));
        icon.setClipToOutline(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(u(9));
        icon.setBackground(shape);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(u(90), u(54));
        ilp.bottomMargin = u(10);
        page.addView(icon, ilp);
        title("Presentation");
        subtitle("Show images, videos and webpages sent from any phone or computer on the same Wi-Fi. Most homes can leave this off.");
        boolean on = org.lumesuite.launcher.present.Presenter.enabled(this);
        LinearLayout l = list();
        row(l, "Use Presentation", on ? "Adds a Presentation card to Home" : "For shops, offices and signage", SWITCH, on, () -> {
            org.lumesuite.launcher.present.Presenter.set(this, org.lumesuite.launcher.present.Presenter.KEY_ENABLED, !on);
            show(index, 0);  // its options appear or go
        });
        if (on) {
            boolean[] boot = {org.lumesuite.launcher.present.Presenter.startOnBoot(this)},
                    awake = {org.lumesuite.launcher.present.Presenter.keepAwake(this)};
            Row[] b = new Row[1], a = new Row[1];
            b[0] = row(l, "Start with the TV", "Play as soon as the TV turns on", SWITCH, boot[0], () -> {
                boot[0] = !boot[0];
                org.lumesuite.launcher.present.Presenter.set(this, org.lumesuite.launcher.present.Presenter.KEY_BOOT, boot[0]);
                b[0].set(boot[0]);
            });
            a[0] = row(l, "Keep Screen Awake", "No screensaver or sleep while presenting", SWITCH, awake[0], () -> {
                awake[0] = !awake[0];
                org.lumesuite.launcher.present.Presenter.set(this, org.lumesuite.launcher.present.Presenter.KEY_AWAKE, awake[0]);
                a[0].set(awake[0]);
            });
        }
        buttons("Continue", this::next);
        dots();
    }

    /**
     * Last page: what Lume still needs, each with its state on the right. Nothing is asked for
     * until its row is pressed; everything here can be skipped and allowed later in Settings.
     */
    void homeAndPermissions() {
        title("Finish Setting Up");
        subtitle("Press a row to allow it. Only the first one matters for most people; the rest can wait.");
        LinearLayout l = list();
        boolean home = Perms.isDefaultHome(this) || PowerService.HomeButton.active(this);
        Row h = row(l, "Make Lume the Home Screen", home ? "The Home button opens Lume"
                : "So the Home button opens Lume", ACTION, false, () -> Perms.requestDefaultHome(this));
        h.status(home ? "✓ Done" : "Set Up  ›", home);
        boolean cw = Profiles.homePrefs(this).getBoolean("cw_enabled", true), apps = Profiles.homePrefs(this).getBoolean("app_rows", true);
        if (WatchNext.supported() && (cw || apps)) {
            boolean ok = Perms.hasTvListings(this);
            Row r = row(l, "Continue Watching & Your Apps", "Read the lists your apps publish for Home", ACTION, false,
                    () -> Perms.requestTvListings(this));
            r.status(ok ? "✓ Allowed" : "Allow  ›", ok);
        }
        if (Perms.adminSupported(this)) {
            boolean ok = Perms.hasAdmin(this);
            Row r = row(l, "Power Off & Sleep Timer", "Let Lume turn the screen off", ACTION, false, () -> Perms.requestAdmin(this));
            r.status(ok ? "✓ Allowed" : "Allow  ›", ok);
        }
        boolean wifi = Perms.hasLocation(this);
        Row w = row(l, "Wi-Fi Name in Control Center", "Android counts this as location access", ACTION, false,
                () -> Perms.requestLocation(this));
        w.status(wifi ? "✓ Allowed" : "Allow  ›", wifi);
        buttons("Continue", this::next);
        dots();
    }

    // ---------------------------------------------------------------- guided cleaning (no computer)

    /** First run: one optional page that opens the guided cleaning. */
    void cleanOffer() {
        title("Make it lighter");
        subtitle("Lume can switch off apps that slow your TV down or watch you. It takes a minute and you can undo it.");
        buttons("Start", () -> startActivity(new Intent(this, SetupActivity.class).putExtra("only_clean", true)));
        TextView skip = button("Skip", false, this::next);
        ((LinearLayout) page.getChildAt(page.getChildCount() - 1)).addView(skip);
        dots();
    }

    boolean cleanAllowed, cleanBusy;
    String cleanNote = "";
    int cleanState = -1;

    boolean devOn() {
        try {
            return android.provider.Settings.Global.getInt(getContentResolver(), android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    boolean adbOn() {
        try {
            return android.provider.Settings.Global.getInt(getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 0: developer options off, 1: debugging off, 2: ready to allow, 3: allowed. */
    int cleanProgress() {
        return cleanAllowed ? 3 : !devOn() ? 0 : !adbOn() ? 1 : 2;
    }

    void openSettingsFor(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    boolean cleanNeedsComputer;

    /** Android 11+ TVs that only offer encrypted Wireless debugging: the same cleaning, from a computer, in one scan. */
    void cleanComputer() {
        title("This TV needs a computer once");
        subtitle("It only offers Wireless debugging, which a TV cannot use on itself. Scan this with a phone or open the address on a computer: TV Doctor finds this TV and does the rest.");
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(org.lumesuite.launcher.present.Presenter.qrOf("https://gpire.github.io/lume-suite/", u(150)));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(u(150), u(150));
        qlp.topMargin = u(8);
        page.addView(qr, qlp);
        TextView url = subtitle("gpire.github.io/lume-suite");
        buttons("Done", () -> { if (onlyClean) finish(); else next(); });
        ((LinearLayout) page.getChildAt(page.getChildCount() - 1)).addView(button("Try again", false, () -> { cleanNeedsComputer = false; show(index, 0); }));
    }

    void cleanGuide() {
        if (cleanNeedsComputer) {
            cleanComputer();
            return;
        }
        boolean fire = android.os.Build.MANUFACTURER.equalsIgnoreCase("Amazon");
        int st = cleanProgress();
        cleanState = st;
        title("Clean your TV");
        subtitle("Two switches, once. Lume sees when you have turned each one on.");
        LinearLayout l = list();
        Row a = row(l, "Developer options", st == 0 ? (fire ? "Press the Fire TV name 7 times" : "Press Build 7 times") : "", ACTION, false, () -> { });
        a.status(st >= 1 ? "✓ " + It.t("On") : It.t("Off"), st >= 1);
        Row b = row(l, "Debugging", st == 1 ? (fire ? "Turn on ADB debugging" : "Turn on USB debugging") : "", ACTION, false, () -> { });
        b.status(st >= 2 ? "✓ " + It.t("On") : It.t("Off"), st >= 2);
        Row c = row(l, "Allow Lume", st == 2 ? It.t("On the TV choose Always allow, then OK") : cleanNote, ACTION, false, () -> { });
        c.status(st >= 3 ? "✓ " + It.t("Allowed") : cleanBusy ? "..." : "", st >= 3);
        firstChoice = null;   // these rows only report; the focus starts on the button that does the next thing
        String label = st == 0 ? "Open About" : st == 1 ? "Open Developer options" : st == 2 ? "Allow" : "Continue";
        buttons(label, () -> {
            if (st == 0) openSettingsFor(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS);
            else if (st == 1) openSettingsFor(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            else if (st == 2) cleanAllow();
            else next();
        });
        TextView skip = button("Not Now", false, () -> { if (onlyClean) finish(); else next(); });
        ((LinearLayout) page.getChildAt(page.getChildCount() - 1)).addView(skip);
        // Lume watches the switches: the page moves on by itself when one is turned on.
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (index >= steps.size() || steps.get(index) != null && cleanProgress() != cleanState && !cleanBusy) show(index, 0);
                else handler.postDelayed(this, 1500);
            }
        }, 1500);
    }

    void cleanAllow() {
        if (cleanBusy) return;
        cleanBusy = true;
        cleanNote = "";
        show(index, 0);
        new Thread(() -> {
            try (AdbClient adb = AdbClient.connect(this, "127.0.0.1", 5555, 120000)) {
                TvOptimize.current = TvOptimize.plan(this, adb);
                runOnUiThread(() -> {
                    cleanAllowed = true;
                    cleanBusy = false;
                    show(index + 1, 1);
                });
            } catch (AdbClient.NotReady e) {
                runOnUiThread(() -> {
                    cleanBusy = false;
                    cleanNote = It.t(e.waitingForOk ? "Waiting for your OK on the TV" : "Debugging is off: see how to turn it on");
                    cleanNeedsComputer = !e.waitingForOk && adbOn();   // debugging is on but the port stays closed: Wireless debugging only
                    show(index, 0);
                });
            } catch (Exception e) {
                android.util.Log.w("LumeOptimize", "allow failed", e);
                runOnUiThread(() -> {
                    cleanBusy = false;
                    cleanNote = It.t("Could not check this TV");
                    show(index, 0);
                });
            }
        }).start();
    }

    final java.util.Map<String, Boolean> keepChoice = new java.util.LinkedHashMap<>();
    int cleanCount;
    TextView cleanCountView;

    List<String> cleanSelection() {
        List<String> out = new ArrayList<>();
        if (TvOptimize.current == null) return out;
        for (String p : TvOptimize.current.safe) {
            String g = TvOptimize.group(this, p);
            if (g == null || !Boolean.TRUE.equals(keepChoice.get(g))) out.add(p);
        }
        return out;
    }

    void cleanChoose() {
        TvOptimize.Plan plan = TvOptimize.current;
        if (plan == null || plan.safe.isEmpty()) {
            title("All done");
            subtitle("Nothing to clean: this TV is already tidy");
            buttons("Done", this::next);
            return;
        }
        title("Ready to clean");
        cleanCountView = subtitle("");
        LinearLayout l = list();
        java.util.LinkedHashSet<String> groups = new java.util.LinkedHashSet<>();
        for (String p : plan.safe) {
            String g = TvOptimize.group(this, p);
            if (g != null) groups.add(g);
        }
        for (String g : groups) {
            if (!keepChoice.containsKey(g)) keepChoice.put(g, TvOptimize.keepByDefault(this, g));
            Row r = row(l, It.f("Keep %s", It.t(groupName(g))), "", SWITCH, Boolean.TRUE.equals(keepChoice.get(g)), () -> { });
            r.view.setOnClickListener(v -> {
                boolean now = !Boolean.TRUE.equals(keepChoice.get(g));
                keepChoice.put(g, now);
                r.set(now);
                updateCleanCount();
            });
        }
        updateCleanCount();
        buttons("Clean now", this::cleanApply);
    }

    static String groupName(String g) {
        switch (g) {
            case "assistant": return "the voice assistant";
            case "cast": return "casting from my phone";
            case "video": return "video apps";
            case "screensaver": return "the screensaver";
            case "kids": return "kids mode";
            case "smarthome": return "smart home";
            case "privacy": return "telemetry";
            default: return g;   // games, music, shopping, recommendations
        }
    }

    void updateCleanCount() {
        int n = cleanSelection().size();
        if (cleanCountView != null) cleanCountView.setText(It.f("%d apps will be switched off. Choose what to keep.", n));
    }

    int cleanDone;
    boolean cleanRolled;

    void cleanApply() {
        if (cleanBusy) return;
        cleanBusy = true;
        List<String> pkgs = cleanSelection();
        if (cleanCountView != null) cleanCountView.setText(It.t("Cleaning..."));
        new Thread(() -> {
            try (AdbClient adb = AdbClient.connect(this, "127.0.0.1", 5555, 15000)) {
                TvOptimize.Result r = TvOptimize.apply(this, adb, TvOptimize.current, pkgs);
                cleanDone = r.done;
                cleanRolled = r.rolledBack;
            } catch (Exception e) {
                android.util.Log.w("LumeOptimize", "clean failed", e);
                cleanDone = 0;
                cleanRolled = true;
            }
            runOnUiThread(() -> {
                cleanBusy = false;
                show(index + 1, 1);
            });
        }).start();
    }

    void cleanDone() {
        title(cleanRolled ? "Stopped" : "All done");
        subtitle(cleanRolled ? It.t("Stopped: the TV did not answer well, the last group was turned back on")
                : It.f("%d apps switched off. Undo any time in Settings > Optimize this TV.", cleanDone));
        buttons("Done", this::next);
    }

    void finish_() {
        backdrop.animate().alpha(0f).setDuration(Motion.d(300)).start();
        View top = new View(this);
        page.addView(top, new LinearLayout.LayoutParams(1, 0, 1f));
        int cs = u(64);
        Bitmap cb = Bitmap.createBitmap(cs, cs, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cc = new android.graphics.Canvas(cb);
        android.graphics.Paint cp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        cp.setColor(0xFF30D158);
        cc.drawCircle(cs / 2f, cs / 2f, cs / 2f, cp);
        cp.setColor(Color.WHITE);
        cp.setStyle(android.graphics.Paint.Style.STROKE);
        cp.setStrokeWidth(cs * 0.09f);
        cp.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        cp.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        android.graphics.Path tick = new android.graphics.Path();
        tick.moveTo(cs * 0.29f, cs * 0.52f);
        tick.lineTo(cs * 0.44f, cs * 0.67f);
        tick.lineTo(cs * 0.72f, cs * 0.37f);
        cc.drawPath(tick, cp);
        ImageView check = new ImageView(this);
        check.setImageBitmap(cb);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(u(64), u(64));
        clp.bottomMargin = u(18);
        page.addView(check, clp);
        title("You\u2019re All Set");
        subtitle("Change any of this later in Settings, from the gear in Control Center.");
        buttons("Go to Home Screen", () -> {
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).apply();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        View bottom = new View(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, 1.4f));
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        MainActivity.navSound(this, ev);
        return super.dispatchKeyEvent(ev) || Clicks.moveFocus(getWindow(), ev);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
