package org.lumesuite.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** tvOS-style home screen: blurred backdrop, top shelf title, rounded app tile grid. */
public class MainActivity extends Activity {

    static final int COLS = 6;
    static final String SETTINGS_KEY = "special:settings";
    static final String PRESENT_KEY = "special:present";
    static final String INPUTS_KEY = "special:inputs";
    static final String AIRPLAY_KEY = "special:airplay";
    static final String FOLDER_KEY = "special:folder";
    static final String PREF_ORDER = "order";
    static final String PREF_HIDDEN = "hidden";
    static final String TV_SETTINGS = "com.android.tv.settings";

    /** Packages placed first (in this order) until the user rearranges the grid. */
    static final List<String> DEFAULT_ORDER = Arrays.asList(
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "org.smarttube.stable",
            "in.startv.hotstar",
            "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android",
            "com.mitv.tvhome.mitvplus",
            "com.google.android.youtube.tv",
            "org.videolan.vlc",
            "io.github.jqssun.airplay");

    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** tvOS focused icon is ~1.2x its resting size (303x183 vs 248x148 px in Apple's reference). */
    static final float FOCUS_SCALE = 1.2f;
    /** Focus lift with a slight spring, like tvOS. */
    static final android.view.animation.OvershootInterpolator LIFT =
            new android.view.animation.OvershootInterpolator(1.3f);

    static class AppEntry {
        String key, pkg, label;
        boolean generic;          // the app ships no icon of its own (system default robot)
        String style = "auto";   // card look: auto, tile (icon), mono (initials) or banner
        Drawable banner, icon;
        Intent launch;
        int color;
        boolean system;
    }

    /** Scroll position is driven by focus changes, not by ScrollView's own key handling. */
    static class ShelfScrollView extends ScrollView {
        ShelfScrollView(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
        @Override public boolean executeKeyEvent(KeyEvent e) { return false; }
    }

    SharedPreferences prefs;
    final Map<String, AppEntry> all = new LinkedHashMap<>();
    /** Visible home apps, in user order. */
    final List<AppEntry> apps = new ArrayList<>();
    /** Apps the user hid; reachable through the "More Apps" folder tile. */
    final List<AppEntry> hiddenApps = new ArrayList<>();
    /** What the grid currently shows: home apps (+ folder tile) or the folder's contents. */
    final List<AppEntry> shown = new ArrayList<>();
    final List<View> tiles = new ArrayList<>();
    final Handler handler = new Handler(Looper.getMainLooper());

    FrameLayout root;
    ImageView wallView;
    /** Top shelf carousel: two crossfading backdrops + info panel. */
    ImageView heroBack, heroFront;
    View heroScrim, shade;
    FrameLayout shelfView;
    LinearLayout heroInfo, heroDots;
    ImageView heroLogo;
    TextView heroTitle, heroMeta, heroOverview, heroRemove;
    /** OK is being held on a Continue Watching card: its options opened, so the release doesn't Resume. */
    boolean heroHeld;
    /** tvOS-style capsule button: glass when idle, solid white with dark text when focused. */
    LinearLayout heroButton;
    TextView heroBtnText;
    ImageView heroBtnIcon;
    ImageView heroProvider;
    GradientDrawable heroBtnBg;
    List<Suggestions.Item> heroItems = new ArrayList<>();
    int heroIndex;
    boolean heroShown;          // carousel currently forms the background
    int heroVersion = -1;       // Suggestions.version() the items were loaded for
    String wallStyle;
    int gallerySlot = -1;
    /** What Liquid Glass samples: a blurred copy of the wallpaper or of the carousel artwork. */
    Bitmap wallBlur, heroBlur, glassBg;
    final LiquidGlass.Backdrop glassSource = () -> glassBg;
    View dockRow;
    boolean returningFromSettings;
    final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    /**
     * The carousel item on screen loads on its own thread, so a step never waits behind a refresh
     * (network fetches on io) or artwork prefetching; a step you have already moved past is skipped.
     */
    final java.util.concurrent.ExecutorService heroIo = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Artwork prefetch: low priority, nearest items first; a newer list stops an older run. */
    final java.util.concurrent.ExecutorService prefetchIo = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lume-prefetch");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    volatile int prefetchGen;
    final Runnable heroAdvance = () -> {
        if (this.search != null) scheduleAdvance();  // nothing to see under search; keep the slot
        else heroStep(1, false);
    };
    ShelfScrollView scroll;
    LinearLayout content;
    LinearLayout clockBox;
    LinearLayout titleBox;
    View chevron;
    TextView shelfTitle, shelfHint;
    float unit = 2f;
    int screenW, screenH, tileW, tileH, gap, side, shelfH, labelH, radius, dockPadV, dockInset, dockOuter;
    int focusedIndex = 0;
    int moveIndex = -1;
    float launchTileSx = 1f, launchTileSy = 1f;
    boolean folderMode;
    boolean resumed;
    /** The visible Home, for overlays (Recent Apps) that borrow its glass and wallpaper. */
    static MainActivity activeInstance;
    /** The big-clock "Scene" look of the Home (default) or the classic corner widgets. */
    SceneClock scene;
    boolean sceneLook() { return !"classic".equals(Profiles.homePrefs(this).getString("home_look", "scene")); }
    ObjectAnimator wiggle;
    AppEntry shelfEntry;
    BroadcastReceiver pkgReceiver;

    /** Full-screen card that zooms out of a tile when an app is launched. */
    View launchOverlay;
    View launchTile;

    final Runnable shelfUpdate = () -> {
        if (focusedIndex >= 0 && focusedIndex < shown.size()) updateShelf(shown.get(focusedIndex));
    };
    final Runnable reloadRun = () -> reload(currentKey());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // The wallpaper/artwork always covers the screen: skip the window's own full-screen fill
        // (this Mali-450 GPU is fill-rate bound, so every hidden full-screen layer costs frames).
        getWindow().setBackgroundDrawable(null);
        Appearance.load(this);
        Shot.maybe(this, getIntent());
        String setpref = getIntent().getStringExtra("setpref");  // adb: --es setpref wallpaper=aurora (string home preferences, for measurements)
        if (setpref != null && setpref.contains("=")) {
            Profiles.homePrefs(this).edit().putString(setpref.substring(0, setpref.indexOf('=')), setpref.substring(setpref.indexOf('=') + 1)).apply();
        }
        Appearance.load(this);   // again: --es setpref theme=graphite may just have changed it
        prefs = Profiles.homePrefs(this);

        android.graphics.Point size = Ui.screen(this);
        screenW = size.x;
        screenH = size.y;
        unit = Ui.unit(this);
        // Measured from Apple's tvOS 26 Home Screen (1920x1080): dock 48 px from the screen edges,
        // 42 px inner padding, 248x148 px icons (5:3, the 800x480 HIG icon), 51 px between icons.
        dockOuter = dp(24);
        dockInset = dp(21);
        side = dockOuter + dockInset;  // grid rows line up with the dock icons (>= 80 pt safe area)
        gap = dp(25);
        tileW = (screenW - 2 * side - (COLS - 1) * gap) / COLS;
        tileH = tileW * 3 / 5;
        labelH = dp(30);
        radius = Math.round(tileW * 0.071f);  // Apple's tvOS 26 card corner: ~7% of card width
        shelfH = (int) (screenH * 0.73f);    // dock top sits ~73% down the screen
        dockPadV = dp(20);

        Clicks.init(this);

        Motion.init(this);
        Gallery.init(this);
        // Startup: the app list (package manager, banners, icon colours) and the wallpaper are
        // prepared in the background while the window's views are built (~250 ms saved on 1 GHz TVs).
        java.util.concurrent.Future<?> appsTask = io.submit(this::loadApps);
        String wall = prefs.getString("wallpaper", "galleria");
        wallPrefetch = new java.util.concurrent.FutureTask<>(() -> makeWallpaper(wall));
        wallPrefetchStyle = wall;
        new Thread((Runnable) wallPrefetch, "lume-wallpaper").start();
        // From this version the top shelf is the gallery: switch existing profiles over once.
        if (!Suggestions.prefs(this).getBoolean("art_v1", false)) {
            Suggestions.setShelf(this, Suggestions.SHELF_ART);
            Suggestions.prefs(this).edit().putBoolean("art_v1", true).apply();
        }
        // After an update that changes what the top shelf holds, start from a fresh carousel.
        if (Suggestions.prefs(this).getInt("cache_format", 0) != 3) {
            Suggestions.changed(this);
            Suggestions.prefs(this).edit().putInt("cache_format", 3).apply();
        }
        buildChrome();
        try {
            appsTask.get();
        } catch (Exception e) {
            loadApps();
        }
        buildGrid();
        focusIndex(0);
        Motion.cascade(tiles, dp(18));  // first appearance: the apps arrive one after another
        registerPackageReceiver();
        // Presentation mode: run the admin server if enabled, and honour "Start on boot".
        org.lumesuite.launcher.present.Presenter.ensureRunning(this);
        if (org.lumesuite.launcher.present.Presenter.justBooted()) {
            handler.postDelayed(() -> org.lumesuite.launcher.present.Presenter.openOnBootOnce(this), 1500);
            Startup.onBoot(this);  // Start with the TV (Presentation's own boot start wins)
        }
        wakeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                AirPlayGuard.check(c, true);
                Startup.onScreenOn(c);  // also fired by PowerService; repeats are dropped
                if (Startup.opensOnWake(c)) return;  // going straight to an app: no "Who's watching?"
                if (!Profiles.askOnWake(c) || Profiles.list(c).size() < 2) return;
                if (resumed) {
                    showProfilePicker(true);
                } else {
                    startActivity(new Intent(c, MainActivity.class).putExtra("pick_profile", true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            }
        };
        registerReceiver(wakeReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // A fresh launch (the Home key, the icon, another app handing back) starts from the plain Home: an overlay left
        // open when the person went away must not greet them on the way back.
        if (!"recents".equals(intent.getStringExtra("panel"))) RecentAppsOverlay.dismiss();
        if (intent.getBooleanExtra("pick_profile", false)) {
            Shot.maybe(this, intent);
            closeMenu();
            if (launchOverlay != null) finishLaunchReturn();
            showProfilePicker(true);
            return;
        }
        // adb perf probe: --es perf weather|status|hero|dockglass|shadows hides one layer to measure its GPU cost.
        String perf = intent.getStringExtra("perf");
        if (perf != null) {
            switch (perf) {
                case "weather": weatherCard.setVisibility(View.GONE); break;
                case "status": clockBox.setVisibility(View.GONE); break;
                case "hero": heroFront.setVisibility(View.GONE); heroBack.setVisibility(View.GONE); break;
                case "dockglass": if (dockRow != null) dockRow.setBackground(null); break;
                case "shadows": for (View t : tiles) t.setElevation(0); break;
                default: recreate(); break;
            }
            return;
        }
        // adb hook for screenshots: am start -n org.lumesuite.launcher/.MainActivity --es panel cc|inputs|weather
        String open = intent.getStringExtra("open");  // adb hook: --es open health|markets|news|weather|airplay [--es shot name]
        if (open != null) {
            Class<?> k = open.equals("health") ? HealthActivity.class : open.equals("markets") ? MarketsActivity.class
                    : open.equals("news") ? NewsActivity.class : open.equals("weather") ? WeatherCitiesActivity.class
                    : open.equals("airplay") ? AirPlayActivity.class : open.equals("settings") ? SettingsActivity.class
                    : open.equals("setup") ? SetupActivity.class : open.equals("phone") ? PhoneActivity.class : null;
            if (k != null) startActivity(new Intent(this, k).putExtra("shot", intent.getStringExtra("shot"))
                    .putExtra("walk", intent.getStringExtra("walk")).putExtra("page", intent.getStringExtra("page"))
                    .putExtra("shot_delay", intent.getIntExtra("shot_delay", 1800)));
            return;
        }
        Shot.maybe(this, intent);
        if (intent.getBooleanExtra("ambient", false)) handler.postDelayed(() -> { if (!idle) enterIdle(); }, 600);
        String panel = intent.getStringExtra("panel");
        if (panel != null) {
            closeMenu();
            if (panel.equals("cc")) showControlCenter();
            else if (panel.equals("recents")) RecentAppsOverlay.showInActivity(this);
            else if (panel.equals("inputs")) showInputs(ccButton);
            else if (panel.equals("weather")) showWeather();
            else if (panel.equals("present")) org.lumesuite.launcher.present.Presenter.open(this);
            else if (panel.equals("search")) showSearch(intent.getStringExtra("q"));
            return;
        }
        if (idle) exitIdle();
        resetIdle();
        // Home always lands on the first app in the dock: close any overlay first
        // (Control Center, profiles, Inputs, weather forecast, menus, move mode, folder).
        if (pickerLayer != null) {
            popoverReturn = null;
            FrameLayout l = pickerLayer;
            releaseSearch();
            pickerLayer = null;
            weatherOpen = false;
            root.removeView(l);
            weatherCard.setAlpha(weatherAlpha());
        }
        closeMenu();
        // Home pressed while already on the home screen: jump back to the top of the home grid.
        if (moveIndex >= 0) endMove();
        if (folderMode) {
            folderMode = false;
            switcher.setAlpha(1f);
            weatherCard.setAlpha(weatherAlpha());
            launchTile = null;
            buildGrid();
            shelfEntry = null;
            scroll.scrollTo(0, 0);
            focusIndex(0);
        } else if (launchOverlay == null) {
            closeMenu();
            goTop();
        }
    }

    /** A different display, resolution or density: rebuild so every size is recomputed. */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        android.graphics.Point size = Ui.screen(this);
        if (size.x != screenW || size.y != screenH || Ui.unit(this) != unit) recreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        activeInstance = this;
        String ws = Profiles.homePrefs(this).getString("wallpaper", "galleria");
        if (!ws.equals(wallStyle) || (Gallery.KEY.equals(ws) && gallerySlot != Gallery.slot())) {
            wallStyle = ws;
            setWallpaper(ws);
        }
        if (returningFromSettings && !folderMode && launchOverlay == null && moveIndex < 0) reload(currentKey());
        returningFromSettings = false;
        loadHero();
        if (launchOverlay != null) finishLaunchReturn();
        // Setup not finished (e.g. Home was pressed during it): pick it up again.
        if (!SetupActivity.done(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        // Screensaver: Lume never holds the screen on unless the user turned the screensaver off here.
        if (getSharedPreferences("screensaver", MODE_PRIVATE).getBoolean("allow", true)) {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        updateWeather();
        applyTabs();
        resetIdle();
        // Opened from the app list while another launcher is the home screen: offer once to take over.
        // Not asked while Lume's switch already sends the Home button here (the stock launcher stays default).
        if (SetupActivity.done(this) && !Perms.isDefaultHome(this) && !PowerService.HomeButton.active(this)
                && !prefs.getBoolean("asked_default_home", false)) {
            prefs.edit().putBoolean("asked_default_home", true).apply();
            Clicks.dialog(this)
                    .setTitle(It.t("Make Lume your home screen?"))
                    .setMessage(It.t("The Home button on the remote will open Lume."))
                    .setPositiveButton(It.t("Make Home Screen"), (d, w) -> Perms.requestDefaultHome(this))
                    .setNegativeButton(It.t("Not Now"), null).show();
        }
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        RecentAppsOverlay.dismiss();   // it lives only while this screen is in front
        ControlPanelOverlay.dismiss();
        if (activeInstance == this) activeInstance = null;
        handler.removeCallbacks(heroAdvance);
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(idleRunnable);
        if (idle) exitIdle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pkgReceiver != null) unregisterReceiver(pkgReceiver);
        if (netCallback != null) {
            try {
                getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) {
                // already unregistered
            }
        }
        if (wakeReceiver != null) unregisterReceiver(wakeReceiver);
        handler.removeCallbacksAndMessages(null);
    }

    // ---------------------------------------------------------------- layout

    void buildChrome() {
        root = new FrameLayout(this);
        root.setBackground(null);

        // One ambient wallpaper for every app. It is drawn tiny and upscaled, so it is already
        // soft; a slow drift keeps it alive without a per-frame blur on this weak GPU.
        wallView = new ImageView(this);
        wallView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallStyle = Profiles.homePrefs(this).getString("wallpaper", "galleria");
        setWallpaper(wallStyle);
        root.addView(wallView, match());

        heroBack = heroLayer();
        heroFront = heroLayer();
        root.addView(heroBack, match());
        root.addView(heroFront, match());

        shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x14000000, 0x40000000, 0xB3000000}));
        root.addView(shade, match());
        shade.setVisibility(View.GONE);  // baked into wallpaper + artwork instead

        // Left-side scrim so the carousel's title and text stay legible over any artwork.
        heroScrim = new View(this);
        heroScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xCC000000, 0x66000000, 0x00000000}));
        heroScrim.setAlpha(0f);
        root.addView(heroScrim, new FrameLayout.LayoutParams(screenW * 3 / 4, ViewGroup.LayoutParams.MATCH_PARENT));

        scroll = new ShelfScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setFillViewport(true);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (launchOverlay == null && !statusFocused()) clockBox.setAlpha(clockAlpha());
            if (launchOverlay == null && !switcher.isFocused()) switcher.setAlpha(folderMode ? 0f : clockAlpha());
            if (launchOverlay == null && weatherCard != null && !weatherCard.isFocused()) weatherCard.setAlpha(weatherAlpha());
            if (dockRow != null) dockRow.invalidate();
        });

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(0, 0, 0, dp(80));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, match());

        // Top shelf: header shown only inside the More Apps folder.
        FrameLayout shelf = new FrameLayout(this);
        shelfView = shelf;
        buildHeroInfo(shelf);
        titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(side, 0, side, dp(26));
        shelfTitle = text(46, Ui.font(this, "display"), 1f);
        shelfTitle.setSingleLine(true);
        shelfTitle.setEllipsize(TextUtils.TruncateAt.END);
        shelfHint = text(15, Ui.font(this, "regular"), 0.6f);
        titleBox.addView(shelfTitle);
        titleBox.addView(shelfHint);
        shelf.addView(titleBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        // Wide, thin chevron above the dock, as on tvOS.
        int cw = dp(30), ch = dp(9);
        Bitmap cb = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
        cp.setColor(0xCCFFFFFF);
        cp.setStyle(Paint.Style.STROKE);
        cp.setStrokeWidth(dp(2));
        cp.setStrokeCap(Paint.Cap.ROUND);
        cp.setStrokeJoin(Paint.Join.ROUND);
        android.graphics.Path cpath = new android.graphics.Path();
        float pad = dp(2);
        cpath.moveTo(pad, ch - pad);
        cpath.lineTo(cw / 2f, pad);
        cpath.lineTo(cw - pad, ch - pad);
        new Canvas(cb).drawPath(cpath, cp);
        ImageView chevronView = new ImageView(this);
        chevronView.setImageBitmap(cb);
        chevron = chevronView;
        FrameLayout.LayoutParams chp = new FrameLayout.LayoutParams(cw, ch,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        chp.bottomMargin = dp(14);
        shelf.addView(chevron, chp);
        content.addView(shelf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, shelfH));

        // tvOS 26 status row (per Apple's Control Center reference): plain time, then a glass
        // Control Center button and the profile avatar; each turns white/lifts when focused.
        int btn = dp(34);
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.HORIZONTAL);
        clockBox.setGravity(Gravity.CENTER_VERTICAL);
        clockBox.setClipChildren(false);
        TextClock time = new TextClock(this);
        Ui.textSize(time, 16);
        time.setTypeface(Ui.font(this, "medium"));
        time.setTextColor(Color.WHITE);
        time.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("H:mm");
        time.setAllCaps(true);
        // Internet status, left of the time: white when online, orange when there is no internet.
        netIcon = new ImageView(this);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(24), dp(20));
        nlp.rightMargin = dp(8);
        // A faint dark chip behind the network icon and time, for contrast over bright artwork.
        LinearLayout timeChip = new LinearLayout(this);
        timeChip.setOrientation(LinearLayout.HORIZONTAL);
        timeChip.setGravity(Gravity.CENTER_VERTICAL);
        timeChip.setPadding(dp(12), 0, dp(14), 0);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(btn / 2f);
        chipBg.setColor(0x47000000);
        timeChip.setBackground(chipBg);
        time.setShadowLayer(dp(8), 0, dp(1), 0x99000000);
        timeChip.addView(netIcon, nlp);
        timeChip.addView(time);
        clockBox.addView(timeChip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, btn));

        // Refresh: reloads every part of the carousel (recommendations, news, Continue Watching) and weather.
        refreshButton = new ImageView(this);
        refreshButton.setScaleType(ImageView.ScaleType.CENTER);
        refreshButton.setImageBitmap(Glyphs.get("refresh", dp(20), Color.WHITE));
        LiquidGlass rfGlass = new LiquidGlass(btn / 2f, 0x1F000000, glassSource, screenW, screenH, unit).adaptive();
        rfGlass.lens = dp(4);
        refreshButton.setBackground(rfGlass);
        refreshButton.setFocusable(true);
        refreshButton.setClickable(true);
        LinearLayout.LayoutParams rblp = new LinearLayout.LayoutParams(btn, btn);
        rblp.leftMargin = dp(18);
        clockBox.addView(refreshButton, rblp);
        GradientDrawable rfLit = new GradientDrawable();
        rfLit.setShape(GradientDrawable.OVAL);
        rfLit.setColor(Color.WHITE);
        refreshButton.setOnFocusChangeListener((v, has) -> {
            refreshButton.setBackground(has ? rfLit : rfGlass);
            refreshButton.setImageBitmap(Glyphs.get("refresh", dp(20), has ? 0xFF111114 : Color.WHITE));
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(Motion.d(200)).setInterpolator(LIFT).start();
        });
        refreshButton.setOnClickListener(v -> refreshAll());

        // Search, beside Control Center: apps, settings, and movies and shows on every service.
        searchButton = new ImageView(this);
        searchButton.setScaleType(ImageView.ScaleType.CENTER);
        searchButton.setImageBitmap(Glyphs.get("search", dp(20), Color.WHITE));
        LiquidGlass seGlass = new LiquidGlass(btn / 2f, 0x1F000000, glassSource, screenW, screenH, unit).adaptive();
        seGlass.lens = dp(4);
        searchButton.setBackground(seGlass);
        searchButton.setFocusable(true);
        searchButton.setClickable(true);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(btn, btn);
        sblp.leftMargin = dp(12);
        clockBox.addView(searchButton, sblp);
        GradientDrawable seLit = new GradientDrawable();
        seLit.setShape(GradientDrawable.OVAL);
        seLit.setColor(Color.WHITE);
        searchButton.setOnFocusChangeListener((v, has) -> {
            searchButton.setBackground(has ? seLit : seGlass);
            searchButton.setImageBitmap(Glyphs.get("search", dp(20), has ? 0xFF111114 : Color.WHITE));
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(Motion.d(200)).setInterpolator(LIFT).start();
        });
        searchButton.setOnClickListener(v -> showSearch(null));

        ccButton = new ImageView(this);
        ccButton.setScaleType(ImageView.ScaleType.CENTER);
        ccButton.setImageBitmap(Glyphs.get("controls", dp(20), Color.WHITE));
        LiquidGlass ccGlass = new LiquidGlass(btn / 2f, 0x1F000000, glassSource, screenW, screenH, unit).adaptive();
        ccGlass.lens = dp(4);
        ccButton.setBackground(ccGlass);
        ccButton.setFocusable(true);
        ccButton.setClickable(true);
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(btn, btn);
        cblp.leftMargin = dp(12);
        clockBox.addView(ccButton, cblp);
        GradientDrawable ccLit = new GradientDrawable();
        ccLit.setShape(GradientDrawable.OVAL);
        ccLit.setColor(Color.WHITE);
        ccButton.setOnFocusChangeListener((v, has) -> {
            ccButton.setBackground(has ? ccLit : ccGlass);
            ccButton.setImageBitmap(Glyphs.get("controls", dp(20), has ? 0xFF111114 : Color.WHITE));
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(Motion.d(200)).setInterpolator(LIFT).start();
        });
        ccButton.setOnClickListener(v -> showControlCenter());


        int av = btn;
        ImageView avatar = new ImageView(this);
        avatar.setImageBitmap(Profiles.avatar(this, Profiles.current(this), av));
        avatar.setFocusable(true);
        avatar.setClickable(true);
        avatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        avatar.setElevation(dp(2));
        avatarView = avatar;
        avatarSize = av;
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(av, av);
        alp.leftMargin = dp(12);
        clockBox.addView(avatar, alp);
        avatar.setOnFocusChangeListener((v, has) ->
                v.animate().scaleX(has ? 1.18f : 1f).scaleY(has ? 1.18f : 1f).translationZ(has ? dp(8) : 0)
                        .setDuration(Motion.d(200)).setInterpolator(LIFT).start());
        avatar.setOnClickListener(v -> showProfilePicker(false));

        // Room around the buttons for their focus growth (1.12x / 1.18x) so nothing is clipped;
        // the margins shrink by the same amount, so the row sits exactly where it did.
        int grow = dp(6);
        clockBox.setPadding(grow, grow, grow, grow);
        clockBox.setClipToPadding(false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, btn + 2 * grow, Gravity.TOP | Gravity.END);
        clp.topMargin = dp(30) - grow;
        clp.rightMargin = dockOuter - grow + dp(18);   // clear of the TV's overscan
        root.addView(clockBox, clp);
        watchNetwork();

        // Weather overview in the top-left corner, over the top shelf; OK opens the forecast.
        weatherCard = new WeatherView(this, false, unit);
        weatherGlass = new LiquidGlass(dp(WeatherView.COMPACT_H) / 2f, 0x2E000000, glassSource, screenW, screenH, unit).adaptive();
        weatherGlass.lens = dp(6);
        weatherCard.setBackground(weatherGlass);
        weatherCard.setFocusable(true);
        weatherCard.setClickable(true);
        weatherCard.setOnFocusChangeListener((v, has) -> {
            weatherGlass.setLit(has);
            if (has) v.setAlpha(1f);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).translationZ(has ? dp(10) : 0)
                    .setDuration(Motion.d(200)).setInterpolator(LIFT).start();
        });
        weatherCard.setPivotX(0);
        weatherCard.setPivotY(0);
        weatherCard.setOnClickListener(v -> showWeather());
        // Top left, level with the status row and inside the same 48 px edge margin.
        // As wide as its content (the place name varies), like a tvOS capsule.
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(WeatherView.COMPACT_H),
                Gravity.TOP | Gravity.START);
        wlp.topMargin = dp(24);
        wlp.leftMargin = dockOuter;
        root.addView(weatherCard, wlp);
        if (sceneLook()) {
            scene = new SceneClock(this, unit, this::showWeather);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            slp.topMargin = dp(30);
            root.addView(scene, root.indexOfChild(clockBox), slp);
            time.setVisibility(View.GONE);  // the big clock replaces the small one; the network icon stays
        }
        updateWeather();

        setContentView(root);
        Clicks.quiet(root);  // Android's own click sounds off: the Sounds switch decides
    }

    void buildGrid() {
        stopWiggle();
        shown.clear();
        if (folderMode) {
            shown.addAll(hiddenApps);
        } else {
            shown.addAll(apps);
            if (!hiddenApps.isEmpty()) shown.add(folderEntry());
        }
        // Like tvOS, the home grid has no big title; only the folder gets a header.
        titleBox.setVisibility(folderMode ? View.VISIBLE : View.GONE);
        chevron.setVisibility(folderMode ? View.GONE : View.VISIBLE);
        shelfTitle.setText("More Apps");
        shelfHint.setText("Back to return   \u00b7   Hold OK for options");

        // Removing the focused card would make Android hand focus to the first card (and scroll
        // to the top); block that while rebuilding \u2014 callers focus the right card afterwards.
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        while (content.getChildCount() > 1) content.removeViewAt(1);
        tiles.clear();
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setPadding(side, 0, side, 0);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = gap / 2;
                if (i == 0 && hasDock()) {
                    // tvOS 26 dock: the top row sits on one frosted glass panel.
                    row.setPadding(dockInset, dockPadV, dockInset, dockPadV);
                    rlp.leftMargin = side - dockInset;
                    rlp.rightMargin = side - dockInset;
                    rlp.bottomMargin = dockGapBelow();
                    row.setBackground(dockBackground());
                    dockRow = row;
                }
                content.addView(row, rlp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % COLS != 0) lp.leftMargin = gap;
            row.addView(makeCell(shown.get(i), i, i < COLS && hasDock()), lp);
        }
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    View makeCell(AppEntry a, int index, boolean inDock) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);

        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(radius);
        bg.setColor(a.banner != null ? 0xFF1C1C1E : a.color);
        tile.setBackground(bg);
        tile.setOutlineProvider(roundedOutline(new float[]{radius}));
        tile.setClipToOutline(true);
        tile.setElevation(dp(3));

        ImageView iv = new ImageView(this);
        if (a.banner != null) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            tile.addView(iv, match());
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(a.icon);
            int s = tileH * 55 / 100;
            tile.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }

        // tvOS focus has no outline ring: a soft top sheen plus a one-shot glare sweep instead.
        View topSheen = new View(this);
        topSheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1AFFFFFF, 0x00FFFFFF}));
        topSheen.setAlpha(0f);
        tile.addView(topSheen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tileH / 2, Gravity.TOP));
        View glare = new View(this);
        glare.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x1AFFFFFF, 0x00FFFFFF}));
        glare.setRotation(18f);
        glare.setAlpha(0f);
        tile.addView(glare, new FrameLayout.LayoutParams(tileW * 45 / 100, tileH * 2, Gravity.CENTER_VERTICAL));

        TextView label = text(15, Ui.font(this, "medium"), 0f);
        label.setText(a.label);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);

        cell.addView(tile, new LinearLayout.LayoutParams(tileW, tileH));
        if (!inDock) cell.addView(label, new LinearLayout.LayoutParams(tileW, labelH));

        tile.setOnFocusChangeListener((v, has) -> {
            if (has) {
                cell.setTranslationZ(dp(10));
                if (moveIndex >= 0) {
                    // Rebuilt while moving: appear already lifted, or every step would "pop".
                    v.setScaleX(FOCUS_SCALE);
                    v.setScaleY(FOCUS_SCALE);
                    v.setTranslationZ(dp(18));
                    label.setAlpha(1f);
                    label.setTranslationY(tileH * (FOCUS_SCALE - 1f) / 2f);
                } else {
                    v.animate().withLayer().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).translationZ(dp(18)).setDuration(Motion.d(260))
                            .setInterpolator(LIFT).start();
                    label.animate().alpha(1f).translationY(tileH * (FOCUS_SCALE - 1f) / 2f).setDuration(Motion.d(200)).start();
                    // Liquid-glass illumination: a glare sweeps across once, then rests and
                    // drifts with the card's gentle sway (HIG: "gently sways while the surface
                    // illuminates").
                    glare.animate().cancel();
                    glare.setTranslationX(-tileW * 0.7f);
                    glare.setAlpha(1f);
                    glare.animate().translationX(tileW * 1.1f).alpha(0f).setStartDelay(Motion.d(60)).setDuration(Motion.d(700))
                            .setInterpolator(EASE).start();
                }
                topSheen.animate().alpha(1f).setDuration(Motion.d(200)).start();
                onTileFocused(index);
            } else {
                cell.setTranslationZ(0);
                stopSway(v);
                v.animate().withLayer().scaleX(1f).scaleY(1f).translationZ(0).setDuration(Motion.d(180)).setInterpolator(EASE).start();
                label.animate().alpha(0f).translationY(0).setDuration(Motion.d(120)).start();
                topSheen.animate().alpha(0f).setDuration(Motion.d(160)).start();
                glare.animate().cancel();
                glare.setAlpha(0f);
            }
            v.invalidate();
        });
        tile.setCameraDistance(9000 * unit);
        tile.setOnClickListener(v -> {
            if (moveIndex >= 0 || launchOverlay != null) return;
            // Pressed state (HIG): the card dips instantly, then the action runs.
            stopSway(v);
            v.animate().scaleX(FOCUS_SCALE * 0.92f).scaleY(FOCUS_SCALE * 0.92f).setDuration(Motion.d(90))
                    .setInterpolator(EASE).withEndAction(() -> {
                        v.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).setDuration(Motion.d(140)).setInterpolator(LIFT).start();
                        if (FOLDER_KEY.equals(a.key)) enterFolder();
                        else launchAnimated(v, a);
                    }).start();
        });
        tile.setOnLongClickListener(v -> {
            if (moveIndex < 0 && !FOLDER_KEY.equals(a.key)) showOptions(index);
            return true;
        });
        tiles.add(tile);
        return cell;
    }

    /** tvOS-style ambient colour field: soft glows of indigo, teal, magenta and amber on navy. */
    java.util.concurrent.FutureTask<Bitmap> wallPrefetch;
    String wallPrefetchStyle;

    void setWallpaper(String style) {
        Bitmap wp = null;
        if (wallPrefetch != null && style.equals(wallPrefetchStyle)) {
            try {
                wp = wallPrefetch.get();
            } catch (Exception ignored) {
                // generate below
            }
        }
        wallPrefetch = null;
        if (wp == null) wp = makeWallpaper(style);
        if (Gallery.KEY.equals(style)) {
            gallerySlot = Gallery.slot();
            if (!Gallery.ready()) Gallery.fetchAsync(() -> runOnUiThread(() -> {
                if (Gallery.KEY.equals(wallStyle)) setWallpaper(Gallery.KEY);
            }));
        }
        wallView.setImageBitmap(wp);
        wallBlur = Ui.glassBackdrop(wp, 192, 108);
        if (!heroShown || heroBlur == null) glassBg = wallBlur;
        invalidateGlass();
    }

    void invalidateGlass() {
        if (dockRow != null) dockRow.invalidate();
        if (clockBox != null) clockBox.invalidate();
        if (weatherCard != null) weatherCard.invalidate();
        if (menuLayer != null && menuLayer.getChildCount() > 0) menuLayer.getChildAt(0).invalidate();
    }

    static final String[] WALL_KEYS = {"galleria", "aurora", "midnight", "sunset", "ocean", "graphite"};
    static final String[] WALL_NAMES = {It.t("Art Gallery"), "Aurora", "Midnight", "Sunset", "Ocean", "Graphite"};

    Bitmap makeWallpaper(String style) {
        return bakeShade(makeWallpaperBase(style));
    }

    /** The bottom darkening that keeps the dock and labels legible, painted into the image. */
    static Bitmap bakeShade(Bitmap b) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, b.getHeight(), new int[]{0x14000000, 0x40000000, 0xB3000000},
                null, Shader.TileMode.CLAMP));
        new Canvas(b).drawRect(0, 0, b.getWidth(), b.getHeight(), p);
        return b;
    }

    static Bitmap makeWallpaperBase(String style) {
        if (Gallery.KEY.equals(style)) {
            Bitmap g = Gallery.wallpaper();
            if (g != null) return g;       // not cached yet: Aurora meanwhile, the real one follows
            style = "aurora";
        }
        int w = 384, h = 216;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int base;
        int[] colors;
        switch (style) {
            case "midnight":
                base = 0xFF04060F;
                colors = new int[]{0xFF1B2A6B, 0xFF0F3D63, 0xFF2B1B5E, 0xFF14254F, 0xFF22306E};
                break;
            case "sunset":
                base = 0xFF140812;
                colors = new int[]{0xFFB0412E, 0xFF8E2C6E, 0xFFD08A2E, 0xFF6B2A8E, 0xFFC0563A};
                break;
            case "ocean":
                base = 0xFF031418;
                colors = new int[]{0xFF0C6E86, 0xFF0B8C76, 0xFF1A4FA0, 0xFF0E5E6E, 0xFF2A7FB0};
                break;
            case "graphite":
                base = 0xFF0C0C0E;
                colors = new int[]{0xFF3A3A42, 0xFF2A2C34, 0xFF4A4A52, 0xFF26262C, 0xFF34363E};
                break;
            default:
                base = 0xFF080C1E;
                colors = new int[]{0xFF4B2DB0, 0xFF0B7F8C, 0xFFB02F6E, 0xFFC27A2C, 0xFF2150C0};
        }
        c.drawColor(base);
        float[][] blobs = {      // centre x, centre y, radius (fractions of width)
                {0.14f, 0.18f, 0.62f},
                {0.88f, 0.22f, 0.50f},
                {0.72f, 0.92f, 0.55f},
                {0.20f, 0.95f, 0.42f},
                {0.50f, 0.45f, 0.38f}};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        for (int i = 0; i < colors.length; i++) {
            int col = colors[i];
            p.setShader(new RadialGradient(blobs[i][0] * w, blobs[i][1] * h, blobs[i][2] * w,
                    new int[]{col & 0xE6FFFFFF, col & 0x8CFFFFFF, col & 0x33FFFFFF, col & 0x00FFFFFF},
                    new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        // Depth, as on tvOS: a soft light from the top left, and the corners falling away.
        p.setShader(new RadialGradient(0.18f * w, -0.05f * h, 0.75f * w,
                new int[]{0x2EFFFFFF, 0x0FFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(0.5f * w, 0.5f * h, 0.78f * w,
                new int[]{0x00000000, 0x00000000, 0x8C000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // The room's light: a little amber in the evening, dimmer late at night, so the screen loses its glare when
        // the room does (the same hours the music screen follows).
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int tint = hour >= 18 && hour <= 21 ? 0x18FF9A3C : hour >= 22 || hour < 6 ? 0x4D000000 : 0;
        if (tint != 0) c.drawColor(tint);
        // A faint grain breaks the 8-bit banding in the wide gradients.
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) {
            int n = rnd.nextInt(5) - 2;
            int r = Math.max(0, Math.min(255, ((px[i] >> 16) & 255) + n));
            int g = Math.max(0, Math.min(255, ((px[i] >> 8) & 255) + n));
            int bl = Math.max(0, Math.min(255, (px[i] & 255) + n));
            px[i] = 0xFF000000 | r << 16 | g << 8 | bl;
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    void startWallpaperDrift() {
        ObjectAnimator dx = ObjectAnimator.ofFloat(wallView, View.TRANSLATION_X, -dp(60), dp(60));
        dx.setDuration(38000);
        ObjectAnimator dy = ObjectAnimator.ofFloat(wallView, View.TRANSLATION_Y, dp(30), -dp(30));
        dy.setDuration(29000);
        for (ObjectAnimator an : new ObjectAnimator[]{dx, dy}) {
            an.setRepeatCount(ValueAnimator.INFINITE);
            an.setRepeatMode(ValueAnimator.REVERSE);
            an.setInterpolator(new PathInterpolator(0.45f, 0f, 0.55f, 1f));
            an.start();
        }
    }

    /** Round profile avatar: indigo-to-pink gradient with a person glyph. */
    Bitmap avatarBitmap(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, size, 0xFFA8A8B0, 0xFF838389, Shader.TileMode.CLAMP));
        float r = size / 2f;
        c.drawCircle(r, r, r, p);
        p.setShader(null);
        p.setColor(0xF2FFFFFF);
        c.drawCircle(r, size * 0.40f, size * 0.17f, p);
        c.save();
        android.graphics.Path clip = new android.graphics.Path();
        clip.addCircle(r, r, r, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        c.drawOval(size * 0.20f, size * 0.64f, size * 0.80f, size * 1.10f, p);
        c.restore();
        return b;
    }

    /** System/utility apps that get a drawn tvOS-style card instead of their Android art. */
    /** Apps whose own banner is a plain letter or a flat tile with tiny text: they get the icon card instead. */
    static final Set<String> TILE_PKGS = new HashSet<>(Arrays.asList("com.amazon.cloud9", "com.amazon.spiderpork", "org.smarttube.stable", "com.liskovsoft.smarttubetv.beta"));

    static final Set<String> TVOS_CARD_PKGS = new HashSet<>(Arrays.asList(
            TV_SETTINGS, "com.android.vending", "com.mitv.dream"));

    /**
     * tvOS-style card: soft vertical gradient with a top sheen and the app's glyph centred.
     * Settings gets the silver card with a drawn gear, like tvOS.
     */
    /** Calm dark tint of the app's hue with a soft glow in the middle (shared by icon and monogram cards). */
    static void paintTint(Canvas c, Paint p, int color, int w, int h) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        boolean grey = hsv[1] < 0.15f;
        int top = Color.HSVToColor(new float[]{hsv[0], grey ? 0.05f : 0.55f, grey ? 0.30f : 0.34f});
        int bottom = Color.HSVToColor(new float[]{hsv[0], grey ? 0.05f : 0.60f, 0.14f});
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int glow = Color.HSVToColor(0x70, new float[]{hsv[0], grey ? 0.10f : 0.75f, 0.95f});
        p.setShader(new android.graphics.RadialGradient(w / 2f, h * 0.46f, h * 0.62f,
                new int[]{glow, glow & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    /**
     * A banner that makes a poor card: almost white (clashes with the dark cards, like Netflix) or nearly
     * blank (a flat colour with a tiny logo). Those apps get the icon tile instead.
     */
    static boolean weakBanner(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Rect old = d.copyBounds();
            d.setBounds(0, 0, 32, 18);
            d.draw(c);
            d.setBounds(old);
            int[] px = new int[32 * 18];
            b.getPixels(px, 0, 32, 0, 0, 32, 18);
            b.recycle();
            double lum = 0;
            for (int v : px) lum += (0.299 * Color.red(v) + 0.587 * Color.green(v) + 0.114 * Color.blue(v)) / 255.0;
            lum /= px.length;
            int[] corners = {px[0], px[31], px[32 * 17], px[32 * 17 + 31]};
            int br = 0, bg = 0, bb = 0;
            for (int v : corners) {
                br += Color.red(v);
                bg += Color.green(v);
                bb += Color.blue(v);
            }
            br /= 4;
            bg /= 4;
            bb /= 4;
            int diff = 0;
            for (int v : px) {
                int dr = Color.red(v) - br, dg = Color.green(v) - bg, db = Color.blue(v) - bb;
                if (dr * dr + dg * dg + db * db > 60 * 60) diff++;
            }
            // Visual weight: a banner that is a flat or smooth colour with a few tiny letters has almost no edges (the "internet" tile).
            Bitmap w = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
            Canvas wc = new Canvas(w);
            Rect old2 = d.copyBounds();
            d.setBounds(0, 0, 64, 36);
            d.draw(wc);
            d.setBounds(old2);
            int[] q = new int[64 * 36];
            w.getPixels(q, 0, 64, 0, 0, 64, 36);
            w.recycle();
            int edges = 0;
            for (int y = 0; y < 35; y++) {
                for (int x = 0; x < 63; x++) {
                    int a = lumaOf(q[y * 64 + x]);
                    if (Math.abs(a - lumaOf(q[y * 64 + x + 1])) + Math.abs(a - lumaOf(q[(y + 1) * 64 + x])) > 40) edges++;
                }
            }
            boolean sparse = edges / (float) (63 * 35) < 0.05f;
            return lum > 0.78 || diff / (float) px.length < 0.10f || sparse;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int lumaOf(int v) {
        return (int) (0.299 * Color.red(v) + 0.587 * Color.green(v) + 0.114 * Color.blue(v));
    }

    /** Share of clearly opaque pixels in a glyph mask (0..1). */
    static float solidShare(Bitmap g) {
        int[] px = new int[g.getWidth() * g.getHeight()];
        g.getPixels(px, 0, g.getWidth(), 0, 0, g.getWidth(), g.getHeight());
        int n = 0;
        for (int v : px) if (Color.alpha(v) > 128) n++;
        return n / (float) px.length;
    }

    /** Card for an app with no icon of its own: its initials on the same dark tinted card, never a robot. */
    Bitmap monogramCard(AppEntry e, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int tint = e.color;
        if (e.generic) {   // the default icon's colour is meaningless: derive a stable hue from the name instead
            float[] hv = {Math.abs(e.label.hashCode()) % 360, 0.55f, 0.55f};
            tint = Color.HSVToColor(hv);
        }
        paintTint(c, p, tint, w, h);
        String t = Profiles.initials(e.label == null || e.label.isEmpty() ? e.pkg : e.label);
        p.setColor(0xF2FFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Ui.font(this, "semibold"));
        p.setTextSize(h * (t.length() > 1 ? 0.40f : 0.50f));
        p.setShadowLayer(h * 0.03f, 0, h * 0.015f, 0x66000000);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(t, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f - h * 0.02f, p);
        return b;
    }

    /**
     * Card for an app without a known white glyph: a calm dark tint of the app's own hue with a soft glow,
     * and the icon at 60% of the height as a continuous-corner tile (shadow, top gloss, hairline rim).
     * Dark and low-saturation keeps contrast high and the home screen restful; the glow ties each card
     * to its brand colour without shouting.
     */
    Bitmap iconCard(AppEntry e, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paintTint(c, p, e.color, w, h);

        int sz = (int) (h * 0.72f);
        float x = (w - sz) / 2f, y = (h - sz) / 2f - h * 0.02f, r = sz * 0.225f;
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setColor(0x66000000);
        sp.setMaskFilter(new android.graphics.BlurMaskFilter(h * 0.03f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new android.graphics.RectF(x, y + h * 0.03f, x + sz, y + sz + h * 0.03f), r, r, sp);

        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(new android.graphics.RectF(x, y, x + sz, y + sz), r, r, android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        p.setColor(0xFF1C1C1E);
        c.drawRect(x, y, x + sz, y + sz, p);                    // base under transparent icons
        Bitmap logo = AppLogos.get(this, e.pkg);   // a clean brand square where there is one, else the app's own icon
        if (logo != null) {
            c.drawBitmap(logo, null, new android.graphics.RectF(x, y, x + sz, y + sz), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        } else {
            Rect old = e.icon.copyBounds();
            e.icon.setBounds((int) x, (int) y, (int) (x + sz), (int) (y + sz));
            e.icon.draw(c);
            e.icon.setBounds(old);
        }
        p.setShader(new LinearGradient(0, y, 0, y + sz * 0.55f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + sz, y + sz * 0.55f, p);          // gloss
        p.setShader(null);
        c.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0x33FFFFFF);
        c.drawPath(clip, p);
        return b;
    }

    /** Fire OS tools that only confuse on a home screen: shown nowhere until the person asks (hidden apps). */
    static final Set<String> AUTO_HIDE = new HashSet<>(java.util.Arrays.asList(
            "com.amazon.tv.earlyaccess", "com.amazon.whasettings", "com.amazon.ftv.profilepicker", "com.amazon.ssm",
            "com.amazon.hedwig", "com.amazon.ftv.screensaver", "com.amazon.firetv.troubleshooting"));

    /** Amazon's own Home as a tile: a white house on graphite blue, in the same family as the other system tiles. */
    Bitmap amazonHomeCard(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF3C4C66, 0xFF171E2B, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.5f, p);
        p.setShader(null);
        Bitmap g = Glyphs.get("house", (int) (h * 0.46f), Color.WHITE);
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        sp.setColorFilter(new android.graphics.PorterDuffColorFilter(0x4D000000, android.graphics.PorterDuff.Mode.SRC_IN));
        float x = (w - g.getWidth()) / 2f, y = (h - g.getHeight()) / 2f;
        c.drawBitmap(g.extractAlpha(), x, y + h * 0.02f, sp);
        c.drawBitmap(g, x, y, new Paint(Paint.FILTER_BITMAP_FLAG));
        return b;
    }

    Bitmap tvosCard(AppEntry e) {
        int w = 480, h = 270;
        if (e.pkg.startsWith("com.amazon.") && It.t("Amazon Home").equals(e.label)) return amazonHomeCard(w, h);
        boolean plain = !TV_SETTINGS.equals(e.pkg) && !"com.android.vending".equals(e.pkg);
        if (plain && "mono".equals(e.style)) return monogramCard(e, w, h);
        if (plain && "tile".equals(e.style) && e.icon != null && !e.generic) return iconCard(e, w, h);
        if (plain && (e.generic || e.icon == null)) return monogramCard(e, w, h);
        if (plain) {
            // A white glyph only for real logo shapes; a square or blob (opaque icon background) looks like a
            // pale box on the card, so those get the proper icon tile instead.
            Bitmap g = whiteGlyph(e, (int) (h * 0.5f));
            if (g == null || solidShare(g) > 0.33f) return iconCard(e, w, h);
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boolean settings = TV_SETTINGS.equals(e.pkg);
        int top, bottom;
        if (settings) {                                  // tvOS Settings: dark graphite under a silver gear
            top = 0xFF4A4A52;
            bottom = 0xFF1B1B1F;
        } else if ("com.android.vending".equals(e.pkg)) { // tvOS App Store-style blue
            top = 0xFF1FA2FF;
            bottom = 0xFF0A55E6;
        } else {                                         // vivid gradient in the app's own hue
            float[] hsv = new float[3];
            Color.colorToHSV(e.color, hsv);
            float sat = hsv[1] < 0.15f ? 0.12f : 0.7f;   // greys stay neutral
            top = Color.HSVToColor(new float[]{hsv[0], sat * 0.85f, 0.92f});
            bottom = Color.HSVToColor(new float[]{(hsv[0] + 8f) % 360f, Math.min(1f, sat * 1.15f), sat < 0.2f ? 0.42f : 0.68f});
        }
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (settings) {
            drawSilverGear(c, w / 2f, h / 2f, h * 0.31f);
            return b;
        }
        Bitmap glyph = fitGlyph(whiteGlyph(e, h), (int) (w * 0.60f), (int) (h * 0.52f));   // wordmarks use the card's width, not a small square
        if (glyph != null) {
            // Soft drop shadow under the glyph, like tvOS system icons.
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            sp.setColorFilter(new android.graphics.PorterDuffColorFilter(0x38000000, android.graphics.PorterDuff.Mode.SRC_IN));
            sp.setMaskFilter(new android.graphics.BlurMaskFilter(h * 0.025f, android.graphics.BlurMaskFilter.Blur.NORMAL));
            float x = (w - glyph.getWidth()) / 2f, y = (h - glyph.getHeight()) / 2f;
            c.drawBitmap(glyph.extractAlpha(), x, y + h * 0.015f, sp);
            c.drawBitmap(glyph, x, y, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else if (e.icon != null) {
            int sz = (int) (h * 0.46f);
            Rect old = e.icon.copyBounds();
            e.icon.setBounds((w - sz) / 2, (h - sz) / 2, (w + sz) / 2, (h + sz) / 2);
            e.icon.draw(c);
            e.icon.setBounds(old);
        }
        return b;
    }

    /**
     * The app's glyph as a flat white shape (tvOS system-icon style): uses the adaptive icon's
     * foreground layer when there is one. Returns null for full-bleed artwork that can't be
     * reduced to a glyph, so it is shown as-is instead of becoming a white blob.
     */
    Bitmap whiteGlyph(AppEntry e, int size) {
        if (e.icon == null) return null;
        Drawable d = e.icon;
        float zoom = 1f;
        if (d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
            Drawable fg = ((android.graphics.drawable.AdaptiveIconDrawable) d).getForeground();
            if (fg != null) {
                d = fg;
                zoom = 1.5f;  // adaptive foregrounds keep the glyph in the centre 2/3
            }
        }
        int big = Math.round(size * zoom);
        Bitmap full = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888);
        Rect old = d.copyBounds();
        d.setBounds(0, 0, big, big);
        d.draw(new Canvas(full));
        d.setBounds(old);
        Bitmap g = Bitmap.createBitmap(full, (big - size) / 2, (big - size) / 2, size, size);
        int[] px = new int[size * size];
        g.getPixels(px, 0, size, 0, 0, size, size);
        int solid = 0;
        for (int v : px) if (Color.alpha(v) > 128) solid++;
        if (solid > px.length * 0.6f || solid < px.length * 0.02f) return null;
        for (int i = 0; i < px.length; i++) px[i] = (Color.alpha(px[i]) << 24) | 0xFFFFFF;
        Bitmap out = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        if (g != full) g.recycle();
        full.recycle();
        return out;
    }

    /**
     * A white glyph cropped to what is actually drawn and scaled to fill the box (keeping its shape): an icon that is a small
     * word in the middle of a transparent square (Silk's "internet") would otherwise sit tiny on its card.
     */
    static Bitmap fitGlyph(Bitmap g, int maxW, int maxH) {
        if (g == null) return null;
        int w = g.getWidth(), h = g.getHeight();
        int[] px = new int[w * h];
        g.getPixels(px, 0, w, 0, 0, w, h);
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (Color.alpha(px[y * w + x]) > 24) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
            }
        }
        if (x1 < x0 || y1 < y0) return g;
        int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
        float scale = Math.min(maxW / (float) cw, maxH / (float) ch);
        scale = Math.min(scale, 2.2f);   // never blown up beyond what the source can bear
        Bitmap crop = Bitmap.createBitmap(g, x0, y0, cw, ch);
        Bitmap out = Bitmap.createScaledBitmap(crop, Math.max(1, Math.round(cw * scale)), Math.max(1, Math.round(ch * scale)), true);
        if (crop != g) crop.recycle();
        return out;
    }

    /** tvOS Settings-style gear: toothed ring with a hollow centre. */
    /**
     * A machined silver gear on graphite, as on tvOS: twelve rounded teeth, a brushed-metal gradient, a bright edge on the
     * light side, a dark hub with its own inner shadow, and a soft drop shadow so it floats off the card.
     */
    static void drawSilverGear(Canvas c, float cx, float cy, float r) {
        int teeth = 12;
        float tw = r * 0.34f, th = r * 0.30f;
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x73000000);
        shadow.setMaskFilter(new android.graphics.BlurMaskFilter(r * 0.12f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        android.graphics.Path gear = new android.graphics.Path();
        for (int i = 0; i < teeth; i++) {
            android.graphics.Path t = new android.graphics.Path();
            t.addRoundRect(new android.graphics.RectF(-tw / 2, -r - th * 0.55f, tw / 2, -r + th), tw * 0.22f, tw * 0.22f, android.graphics.Path.Direction.CW);
            android.graphics.Matrix mx = new android.graphics.Matrix();
            mx.postRotate(i * 360f / teeth);
            t.transform(mx);
            gear.addPath(t);
        }
        gear.addCircle(0, 0, r, android.graphics.Path.Direction.CW);
        c.save();
        c.translate(cx, cy);
        c.save();
        c.translate(0, r * 0.07f);
        c.drawPath(gear, shadow);
        c.restore();
        Paint metal = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        metal.setShader(new LinearGradient(-r, -r * 1.2f, r, r * 1.2f,
                new int[]{0xFFF6F6FA, 0xFFC9C9D2, 0xFF8E8E99, 0xFFD6D6DD}, new float[]{0f, 0.42f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(gear, metal);
        // bright edge on the upper left, a darker one lower right
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, r * 0.035f));
        rim.setShader(new LinearGradient(-r, -r, r, r, 0xCCFFFFFF, 0x33000000, Shader.TileMode.CLAMP));
        c.drawPath(gear, rim);
        // face ring and hub
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(r * 0.05f);
        ring.setColor(0x40000000);
        c.drawCircle(0, 0, r * 0.66f, ring);
        Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
        hub.setShader(new LinearGradient(0, -r * 0.42f, 0, r * 0.42f, 0xFF1D1D21, 0xFF3A3A41, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, r * 0.42f, hub);
        Paint hubRim = new Paint(Paint.ANTI_ALIAS_FLAG);
        hubRim.setStyle(Paint.Style.STROKE);
        hubRim.setStrokeWidth(r * 0.03f);
        hubRim.setColor(0x59FFFFFF);
        c.drawArc(new android.graphics.RectF(-r * 0.42f, -r * 0.42f, r * 0.42f, r * 0.42f), 20, 140, false, hubRim);   // light catching the lower lip
        c.restore();
    }

    static void drawGear(Canvas c, float cx, float cy, float r, int color, int holeColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        int teeth = 12;
        float tw = r * 0.30f, th = r * 0.30f;
        for (int i = 0; i < teeth; i++) {
            c.save();
            c.rotate(i * 360f / teeth, cx, cy);
            c.drawRoundRect(cx - tw / 2, cy - r - th * 0.55f, cx + tw / 2, cy - r + th, tw * 0.25f, tw * 0.25f, p);
            c.restore();
        }
        c.drawCircle(cx, cy, r, p);
        p.setColor(holeColor);
        c.drawCircle(cx, cy, r * 0.42f, p);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.08f);
        c.drawCircle(cx, cy, r * 0.62f, p);
    }

    boolean hasDock() {
        return !folderMode;
    }

    int dockGapBelow() {
        return dp(20);
    }

    /** Frosted glass dock (tvOS 26): big radius, dark-tinted translucent fill, sheen and rim. */
    Drawable dockBackground() {
        // Apple's dock corner is ~2x the card corner (36 px vs ~18 px at 1080p).
        LiquidGlass g = new LiquidGlass(radius * 2f, 0x0FFFFFFF, glassSource, screenW, screenH, unit);
        g.lens = dp(12);
        g.rimStrength = 0.25f;  // a faint edge, not a bright outline
        return g;
    }

    Bitmap grainBitmap;

    /** Fine monochrome noise, tiled over glass to give it a frosted texture. */
    Bitmap grain() {
        if (grainBitmap != null) return grainBitmap;
        int n = 96;
        int[] px = new int[n * n];
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) {
            int a = rnd.nextInt(14);
            px[i] = rnd.nextBoolean() ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0);
        }
        grainBitmap = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        return grainBitmap;
    }

    /**
     * Liquid Glass (iOS/tvOS 26), drawn from what is actually behind the surface: a blurred,
     * colour-boosted copy of the backdrop aligned to the surface's screen position; a band along
     * the edges that magnifies it (lensing, like thick glass); a light tint; and a specular rim
     * that is bright top-left and softer bottom-right. No grain.
     */
    static class LiquidGlass extends Drawable {
        interface Backdrop {
            Bitmap get();
        }

        final float r, density;
        final Backdrop backdrop;
        final int screenW, screenH;
        final int baseTint;
        float lens;
        float rimStrength = 1f;
        // Every layer is a single rounded-rect draw (shader-filled), which the GPU does cheaply;
        // there is no clipping and no off-screen/software rendering at draw time.
        final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint lens1 = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint lens2 = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF box = new android.graphics.RectF(), tmp = new android.graphics.RectF();
        final android.graphics.Matrix m = new android.graphics.Matrix();
        final int[] loc = new int[2];
        Bitmap shaderFor;
        android.graphics.BitmapShader shader1, shader2, shader3;

        LiquidGlass(float r, int tint, Backdrop backdrop, int screenW, int screenH, float density) {
            this.r = r;
            this.baseTint = tint;
            this.backdrop = backdrop;
            this.screenW = screenW;
            this.screenH = screenH;
            this.density = density;
            fill.setColor(tint);
            lens1.setStyle(Paint.Style.STROKE);
            lens2.setStyle(Paint.Style.STROKE);
            lens1.setAlpha(150);
            lens2.setAlpha(150);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.1f * density);
        }

        boolean lit, adaptive;
        int adaptiveTint, minBlack;
        Bitmap lumFor;
        int lumX = Integer.MIN_VALUE, lumY;

        /** Never lighter than this much black: for text-heavy tiles (Control Center). */
        LiquidGlass darker(int alpha) {
            minBlack = alpha;
            return this;
        }
        final int[] sample = new int[64];

        /**
         * Glass for small controls (Liquid Glass "regular"): the backdrop is desaturated so the
         * control reads as clear glass rather than a coloured blob, and the tint follows the
         * brightness behind it \u2014 darker over bright artwork so white text stays legible, a light
         * frost over dark artwork so the control stays visible.
         */
        LiquidGlass adaptive() {
            adaptive = true;
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(0.4f);
            android.graphics.ColorMatrixColorFilter f = new android.graphics.ColorMatrixColorFilter(cm);
            body.setColorFilter(f);
            lens1.setColorFilter(f);
            lens2.setColorFilter(f);
            return this;
        }

        void setLit(boolean lit) {
            this.lit = lit;
            fill.setColor(lit ? 0x52FFFFFF : adaptive && adaptiveTint != 0 ? adaptiveTint : baseTint);
            invalidateSelf();
        }

        /** Average luminance (0..1) of the backdrop under this surface, from up to 8x8 samples. */
        float luminanceBehind(Bitmap b) {
            float sx = b.getWidth() / (float) screenW, sy = b.getHeight() / (float) screenH;
            int x0 = Math.max(0, (int) ((loc[0] + box.left) * sx)), y0 = Math.max(0, (int) ((loc[1] + box.top) * sy));
            int x1 = Math.min(b.getWidth() - 1, (int) ((loc[0] + box.right) * sx)), y1 = Math.min(b.getHeight() - 1, (int) ((loc[1] + box.bottom) * sy));
            if (x1 < x0 || y1 < y0) return 0.3f;
            float sum = 0;
            int n = 0;
            for (int j = 0; j < 8; j++) {
                for (int i = 0; i < 8; i++) {
                    int px = b.getPixel(x0 + (x1 - x0) * i / 7, y0 + (y1 - y0) * j / 7);
                    sum += (0.2126f * Color.red(px) + 0.7152f * Color.green(px) + 0.0722f * Color.blue(px)) / 255f;
                    n++;
                }
            }
            return sum / n;
        }

        static int tintFor(float lum) {
            if (lum >= 0.45f) {  // bright: darken, up to 36% black
                int a = Math.round(0x1F + (Math.min(lum, 0.85f) - 0.45f) / 0.4f * (0x5C - 0x1F));
                return a << 24;
            }
            int a = Math.round((0.45f - Math.max(lum, 0.05f)) / 0.4f * 0x24);  // dark: light frost
            return (a << 24) | 0xFFFFFF;
        }

        int rimA(int a) {
            return (Math.round(a * rimStrength) << 24) | 0xFFFFFF;
        }

        @Override protected void onBoundsChange(Rect b) {
            box.set(b);
            float h = box.height();
            glow.setShader(new LinearGradient(0, box.top, 0, box.top + h * 0.5f,
                    0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                    new int[]{rimA(0xD9), rimA(0x40), rimA(0x14), rimA(0x26), rimA(0x8C)},
                    new float[]{0f, 0.22f, 0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP));
            float l = Math.min(lens, Math.min(box.width(), box.height()) / 3f);
            lens1.setStrokeWidth(l);
            lens2.setStrokeWidth(l / 2f);
        }

        /** Maps the full-screen backdrop onto this surface's on-screen spot, optionally magnified. */
        void place(android.graphics.BitmapShader sh, Bitmap b, float zoom) {
            m.reset();
            m.setScale(screenW / (float) b.getWidth(), screenH / (float) b.getHeight());
            m.postTranslate(box.left - loc[0], box.top - loc[1]);
            if (zoom != 1f) m.postScale(zoom, zoom, box.centerX(), box.centerY());
            sh.setLocalMatrix(m);
        }

        private final Paint flatFill = new Paint(Paint.ANTI_ALIAS_FLAG), flatLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** Graphite and High contrast: a solid rounded surface with a thin outline: no backdrop, no blur, no lens. */
        private void drawFlat(Canvas c) {
            android.graphics.RectF box = new android.graphics.RectF(getBounds());
            flatFill.setColor(Appearance.controlFill(lit));
            c.drawRoundRect(box, r, r, flatFill);
            flatLine.setStyle(Paint.Style.STROKE);
            flatLine.setStrokeWidth(Appearance.contrast() ? Math.max(2f, density * 1.4f) : Math.max(1f, density * 0.7f));
            flatLine.setColor(Appearance.controlStroke(lit));
            box.inset(flatLine.getStrokeWidth() / 2f, flatLine.getStrokeWidth() / 2f);
            c.drawRoundRect(box, r, r, flatLine);
        }

        @Override public void draw(Canvas c) {
            if (Appearance.flat()) {
                drawFlat(c);
                return;
            }
            Bitmap b = backdrop.get();
            Object cb = getCallback();
            if (cb instanceof View) ((View) cb).getLocationOnScreen(loc);
            if (b != null) {
                if (b != shaderFor) {
                    shader1 = new android.graphics.BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    shader2 = new android.graphics.BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    shader3 = new android.graphics.BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    body.setShader(shader1);
                    lens1.setShader(shader2);
                    lens2.setShader(shader3);
                    shaderFor = b;
                }
                if (adaptive && (b != lumFor || loc[0] != lumX || loc[1] != lumY)) {
                    // Only when the backdrop or this surface's position changes, not every frame.
                    lumFor = b;
                    lumX = loc[0];
                    lumY = loc[1];
                    int t = tintFor(luminanceBehind(b));
                    if (minBlack > 0) {
                        boolean black = (t & 0xFFFFFF) == 0;
                        t = (black ? Math.max(t >>> 24, minBlack) : minBlack) << 24;
                    }
                    if (t != adaptiveTint) {
                        adaptiveTint = t;
                        if (!lit) fill.setColor(t);
                    }
                }
                place(shader1, b, 1f);
                c.drawRoundRect(box, r, r, body);
                if (lens > 0) {
                    // Edge lensing: the band along the rim shows the backdrop magnified (two soft steps).
                    float w1 = lens1.getStrokeWidth(), w2 = lens2.getStrokeWidth();
                    place(shader2, b, 1.035f);
                    tmp.set(box.left + w1 / 2, box.top + w1 / 2, box.right - w1 / 2, box.bottom - w1 / 2);
                    c.drawRoundRect(tmp, r - w1 / 2, r - w1 / 2, lens1);
                    place(shader3, b, 1.06f);
                    tmp.set(box.left + w2 / 2, box.top + w2 / 2, box.right - w2 / 2, box.bottom - w2 / 2);
                    c.drawRoundRect(tmp, r - w2 / 2, r - w2 / 2, lens2);
                }
            }
            c.drawRoundRect(box, r, r, fill);
            c.drawRoundRect(box, r, r, glow);
            float in = rim.getStrokeWidth() / 2f;
            c.drawRoundRect(box.left + in, box.top + in, box.right - in, box.bottom - in, r - in, r - in, rim);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * Frosted Liquid Glass surface: tinted luminosity lift, fine frost grain, a soft inner glow
     * at the top, gentle shade at the bottom, and a rim that catches light on the top edge and
     * fades down the sides (no uniform outline).
     */
    static class FrostedGlass extends Drawable {
        final float r, density;
        final int tint;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF box = new android.graphics.RectF();
        final android.graphics.Path clip = new android.graphics.Path();
        /** 1 = full light-catching rim (dock); lower for small controls like the capsule. */
        float rimStrength = 1f;

        FrostedGlass(float r, int tint, Bitmap grain, float density) {
            this.r = r;
            this.tint = tint;
            this.density = density;
            fill.setColor(tint);
            grainPaint.setShader(new android.graphics.BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.2f * density);
        }

        @Override protected void onBoundsChange(Rect b) {
            box.set(b);
            clip.reset();
            clip.addRoundRect(box, r, r, android.graphics.Path.Direction.CW);
            float h = box.height();
            glow.setShader(new LinearGradient(0, box.top, 0, box.top + h * 0.45f,
                    0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            shade.setShader(new LinearGradient(0, box.bottom - h * 0.4f, 0, box.bottom,
                    0x00000000, 0x1F000000, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(0, box.top, 0, box.bottom,
                    new int[]{rimA(0x8C), rimA(0x26), rimA(0x0D), rimA(0x1F)},
                    new float[]{0f, 0.25f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        }

        int rimA(int a) {
            return (Math.round(a * rimStrength) << 24) | 0xFFFFFF;
        }

        @Override public void draw(Canvas c) {
            c.save();
            c.clipPath(clip);
            c.drawRect(box, fill);
            c.drawRect(box, grainPaint);
            c.drawRect(box, glow);
            c.drawRect(box, shade);
            c.restore();
            float inset = rim.getStrokeWidth() / 2f;
            c.drawRoundRect(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset,
                    r - inset, r - inset, rim);
        }

        /** Focused: brighter glass (tvOS lifts and lightens focused glass controls). */
        void setLit(boolean lit) {
            fill.setColor(lit ? 0x59FFFFFF : tint);
            invalidateSelf();
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }

        @Override public boolean getPadding(Rect padding) {
            padding.set(0, 0, 0, 0);
            return false;
        }
    }

    /** Liquid-glass surface: translucent fill, top sheen and a bright hairline rim. */
    LayerDrawable glassBackground(GradientDrawable fill) {
        fill.setCornerRadius(radius);
        fill.setColor(0x26FFFFFF);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x38FFFFFF, 0x08FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(radius);
        GradientDrawable rim = new GradientDrawable();
        rim.setCornerRadius(radius);
        rim.setColor(Color.TRANSPARENT);
        rim.setStroke(Math.max(1, dp(1)), 0x66FFFFFF);
        return new LayerDrawable(new Drawable[]{fill, sheen, rim});
    }

    /** Outline whose corner radius can be changed while animating (radius[0]). */
    ViewOutlineProvider roundedOutline(float[] r) {
        return new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r[0]);
            }
        };
    }

    // ---------------------------------------------------------------- suggestions carousel

    ImageView heroLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    /** Title logo (or title), meta line, overview, "Open in \u2026" capsule and page dots, bottom-left of the shelf. */
    void buildHeroInfo(FrameLayout shelf) {
        heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.VERTICAL);
        heroInfo.setClipChildren(false);
        heroInfo.setClipToPadding(false);
        heroInfo.setAlpha(0f);
        heroProvider = new ImageView(this);
        heroProvider.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroProvider.setOutlineProvider(roundedOutline(new float[]{dp(7)}));
        heroProvider.setClipToOutline(true);
        heroProvider.setElevation(dp(2));
        LinearLayout.LayoutParams pvlp = new LinearLayout.LayoutParams(dp(30), dp(30));
        pvlp.bottomMargin = dp(12);
        heroInfo.addView(heroProvider, pvlp);
        heroLogo = new ImageView(this);
        heroLogo.setAdjustViewBounds(true);
        heroLogo.setScaleType(ImageView.ScaleType.FIT_START);
        heroLogo.setMaxWidth(dp(260));
        heroLogo.setMaxHeight(dp(90));
        heroInfo.addView(heroLogo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        heroTitle = text(36, Ui.font(this, "display"), 1f);
        heroTitle.setLetterSpacing(-0.02f);
        heroTitle.setIncludeFontPadding(false);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        // never wider than the space left of the big clock (a long painting title used to run into its first digit)
        heroInfo.addView(heroTitle, new LinearLayout.LayoutParams(Math.min(dp(420), (int) (getResources().getDisplayMetrics().widthPixels * 0.29f)), ViewGroup.LayoutParams.WRAP_CONTENT));
        heroMeta = text(12.5f, Ui.font(this, "medium"), 0.8f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(10);
        heroInfo.addView(heroMeta, mlp);
        heroOverview = text(12.5f, Ui.font(this, "regular"), 0.8f);
        heroOverview.setMaxLines(2);
        heroOverview.setEllipsize(TextUtils.TruncateAt.END);
        heroOverview.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = dp(6);
        heroInfo.addView(heroOverview, olp);
        heroButton = new LinearLayout(this);
        heroButton.setOrientation(LinearLayout.HORIZONTAL);
        heroButton.setGravity(Gravity.CENTER_VERTICAL);
        heroButton.setPadding(dp(18), 0, dp(20), 0);
        heroBtnBg = new GradientDrawable();
        heroBtnBg.setCornerRadius(dp(16));
        heroBtnBg.setColor(0x47FFFFFF);
        heroButton.setBackground(heroBtnBg);
        heroBtnText = text(13, Ui.font(this, "semibold"), 1f);
        heroBtnText.setShadowLayer(0, 0, 0, 0);
        heroButton.addView(heroBtnText);
        heroBtnIcon = new ImageView(this);
        heroBtnIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBtnIcon.setOutlineProvider(roundedOutline(new float[]{dp(5)}));
        heroBtnIcon.setClipToOutline(true);
        LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iclp.leftMargin = dp(9);
        heroButton.addView(heroBtnIcon, iclp);
        // Resume / Open capsule, plus a "Hold OK" hint on Continue Watching items (Left/Right keep
        // browsing the carousel; holding OK or Menu offers Resume / Remove).
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setClipChildren(false);
        btnRow.addView(heroButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        heroRemove = text(13, Ui.font(this, "semibold"), 1f);
        heroRemove.setShadowLayer(0, 0, 0, 0);
        heroRemove.setText("Hold OK to remove");
        heroRemove.setAlpha(0.7f);
        heroRemove.setGravity(Gravity.CENTER);
        heroRemove.setPadding(dp(4), 0, dp(4), 0);
        heroRemove.setVisibility(View.GONE);
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        rlp2.leftMargin = dp(10);
        btnRow.addView(heroRemove, rlp2);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        blp.topMargin = dp(14);
        heroInfo.addView(btnRow, blp);
        // Overview and button only show while the carousel itself is focused (tvOS).
        heroOverview.setAlpha(0f);
        heroButton.setAlpha(0f);
        heroOverview.setVisibility(View.GONE);
        heroButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        int room = dp(12);
        heroInfo.setPadding(room, room, room, room);
        ilp.leftMargin = side - room;
        ilp.bottomMargin = dp(40) - room;
        root.addView(heroInfo, ilp);
        heroInfo.setTranslationY(heroInfoRestY());

        heroDots = new LinearLayout(this);
        heroDots.setOrientation(LinearLayout.HORIZONTAL);
        heroDots.setAlpha(0f);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dp(22);
        root.addView(heroDots, dlp);
        heroDots.setTranslationY(heroDotsRestY());

        // Up Next strip (Continue Watching): thumbnails with progress, shown while the shelf is focused.
        heroStrip = new LinearLayout(this);
        heroStrip.setOrientation(LinearLayout.HORIZONTAL);
        heroStrip.setClipChildren(false);
        heroStrip.setClipToPadding(false);
        heroStrip.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(64));
        slp.topMargin = dp(16);
        heroInfo.addView(heroStrip, slp);

        // Empty state for Continue Watching.
        shelfEmpty = text(15, Ui.font(this, "medium"), 0f);
        shelfEmpty.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        shelf.addView(shelfEmpty, elp);

        buildSwitcher();

        // The shelf itself is the carousel's focus target (UP from the dock).
        shelf.setFocusable(false);
        shelf.setOnFocusChangeListener((v, has) -> heroFocused(has));
    }

    /**
     * Darkening for legibility (left side for the text, bottom for the dock), painted into the
     * artwork once when it loads, so the GPU draws one full-screen image instead of three layers.
     */
    Bitmap bakeScrims(Bitmap src) {
        if (src == null) return null;
        Bitmap b = src.isMutable() ? src : src.copy(Bitmap.Config.ARGB_8888, true);
        if (b != src) src.recycle();
        int w = b.getWidth(), h = b.getHeight();
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, w * 0.75f, 0, new int[]{0xCC000000, 0x66000000, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w * 0.75f, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x14000000, 0x40000000, 0xB3000000},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        topShade(c, w, h);
        return b;
    }

    /** A soft shade along the top so the time and status icons stay legible over bright artwork. */
    static void topShade(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.2f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.2f, p);
    }

    /**
     * News layout (editorial, built for reading): a blurred, darkened copy of the photo fills the
     * screen, the photo itself sits sharp on the right ~60% and fades in from the left, and the left
     * side is a near-solid dark panel for the headline and summary. Baked once per story.
     */
    Bitmap bakeNews(Bitmap src) {
        if (src == null) return null;
        int tw = Math.min(1280, screenW), th = tw * screenH / screenW;
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        // Background: the photo shrunk to a few dozen pixels and stretched back (a cheap blur), darkened.
        Bitmap tiny = Bitmap.createScaledBitmap(src, 32, Math.max(1, 32 * src.getHeight() / src.getWidth()), true);
        c.drawBitmap(tiny, null, new android.graphics.RectF(0, 0, tw, th), p);
        tiny.recycle();
        c.drawColor(0xB30B0B10);
        // The photo, centre-cropped into the right 60%, with a soft left edge.
        android.graphics.RectF dst = new android.graphics.RectF(tw * 0.4f, 0, tw, th);
        float scale = Math.max(dst.width() / src.getWidth(), dst.height() / src.getHeight());
        float sw = dst.width() / scale, sh = dst.height() / scale;
        Rect from = new Rect((int) ((src.getWidth() - sw) / 2), (int) ((src.getHeight() - sh) / 2),
                (int) ((src.getWidth() + sw) / 2), (int) ((src.getHeight() + sh) / 2));
        int layer = c.saveLayer(dst, null);
        c.drawBitmap(src, from, dst, p);
        Paint mask = new Paint();
        mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        mask.setShader(new LinearGradient(dst.left, 0, dst.left + dst.width() * 0.35f, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(dst, mask);
        c.restoreToCount(layer);
        src.recycle();
        // Reading panel on the left, then the usual bottom shade for the dock.
        p.setShader(new LinearGradient(0, 0, tw * 0.62f, 0, new int[]{0xF20B0B10, 0xD90B0B10, 0x000B0B10},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, tw * 0.62f, th, p);
        p.setShader(new LinearGradient(0, 0, 0, th, new int[]{0x00000000, 0x26000000, 0xB3000000},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, tw, th, p);
        topShade(c, tw, th);
        return out;
    }

    /** Info block sits just above the dock while a dock app is focused... */
    float heroInfoRestY() {
        return -(screenH - shelfH) + dp(40) - dp(36);
    }

    /** ...and the page dots sit centred between the info and the dock. */
    float heroDotsRestY() {
        return -(screenH - shelfH) + dp(22) - dp(12);
    }

    /** tvOS-style top tab bar: a glass capsule, top centre, with two text tabs. */
    void buildSwitcher() {
        shelfMode = Profiles.homePrefs(this).getString("shelf_mode", "suggest");
        switcher = new LinearLayout(this);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        switcher.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(30);
        switcher.setPadding(dp(3), 0, dp(3), 0);
        LiquidGlass g = new LiquidGlass(h / 2f, 0x1F000000, glassSource, screenW, screenH, unit).adaptive();
        g.rimStrength = 0.6f;
        g.lens = dp(5);
        switcher.setBackground(g);
        switcher.setFocusable(true);
        tabSuggestBg = new GradientDrawable();
        tabContinueBg = new GradientDrawable();
        tabSuggest = tab("Suggestions", tabSuggestBg);
        tabContinue = tab("Continue Watching", tabContinueBg);
        switcher.addView(tabSuggest, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h - dp(6)));
        switcher.addView(tabContinue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h - dp(6)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(26);
        root.addView(switcher, lp);
        switcher.setOnFocusChangeListener((v, has) -> {
            styleTabs();
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(Motion.d(200)).setInterpolator(LIFT).start();
            if (has) setHeroShown(!heroItems.isEmpty());
        });
        styleTabs();
    }

    TextView tab(String label, GradientDrawable bg) {
        TextView t = text(12.5f, Ui.font(this, "medium"), 1f);
        t.setText(label);
        t.setShadowLayer(0, 0, 0, 0);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), 0, dp(14), 0);
        bg.setCornerRadius(dp(12));
        t.setBackground(bg);
        return t;
    }

    /** Selected tab: soft glass pill; white with dark text while the tab bar has focus (tvOS). */
    void styleTabs() {
        boolean focused = switcher.isFocused();
        boolean sug = !"continue".equals(shelfMode);
        TextView on = sug ? tabSuggest : tabContinue, off = sug ? tabContinue : tabSuggest;
        GradientDrawable onBg = sug ? tabSuggestBg : tabContinueBg, offBg = sug ? tabContinueBg : tabSuggestBg;
        onBg.setColor(focused ? 0xFFFFFFFF : 0x33FFFFFF);
        on.setTextColor(focused ? 0xFF111114 : Color.WHITE);
        offBg.setColor(Color.TRANSPARENT);
        off.setTextColor(0xB3FFFFFF);
    }

    void setShelfMode(String mode) {
        if (mode.equals(shelfMode)) return;
        shelfMode = mode;
        Profiles.homePrefs(this).edit().putString("shelf_mode", mode).apply();
        styleTabs();
        heroItems = new ArrayList<>();
        heroIndex = 0;
        heroVersion = -1;
        heroFront.setImageDrawable(null);
        heroBack.setImageDrawable(null);
        setHeroShown(false);
        loadHero();
    }

    /** Continue Watching can be switched off per profile (Settings -> Continue Watching). */
    boolean cwEnabled() {
        return Profiles.homePrefs(this).getBoolean("cw_enabled", true);
    }

    /** The tab bar only appears when there are two things to switch between. */
    boolean tabsShown() {
        return false;  // one carousel mixes everything; no Suggestions / Continue Watching tabs
    }

    /** Applies the profile's choices: hides the tab bar and picks the one remaining mode. */
    void applyTabs() {
        if (switcher == null) return;
        if (!"suggest".equals(shelfMode)) setShelfMode("suggest");
        boolean show = tabsShown();
        if (!show && switcher.isFocused()) ccButton.requestFocus();
        switcher.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** Down from the status row: the tab bar, or (when it's hidden) the carousel or the dock. */
    void focusBelowStatus() {
        if (switcher != null && switcher.getVisibility() == View.VISIBLE) {
            switcher.requestFocus();
        } else if (shelfView.isFocusable() && !folderMode) {
            heroRequested = true;
            shelfView.requestFocus();
        } else {
            focusIndex(Math.min(focusedIndex, COLS - 1));
        }
    }

    /** Up from the shelf/dock: the tab bar, or the status row when the tab bar is hidden. */
    void focusTop() {
        (switcher != null && switcher.getVisibility() == View.VISIBLE ? switcher : ccButton).requestFocus();
    }

    boolean continueMode() {
        return "continue".equals(shelfMode);
    }

    /** Loads cached suggestions at once and refreshes them in the background when stale or reconfigured. */
    String lastCwSig = "";

    /**
     * One carousel: news / recommendations (if this profile shows them) with Continue Watching
     * titles mixed in (if it's on) — one after every two items; or Continue Watching alone.
     */
    void loadHero() {
        updateEmpty();
        boolean shelf = Suggestions.enabled(this) && Suggestions.ready(this);
        boolean cw = cwEnabled();
        boolean rows = AppRows.enabled(this);
        if (!shelf && !cw && !rows) {
            heroItems = new ArrayList<>();
            shelfView.setFocusable(false);
            setHeroShown(false);
            return;
        }
        int v = Suggestions.version(this) * 4 + (cw ? 1 : 0) + (rows ? 2 : 0);
        boolean reconfigured = v != heroVersion;
        heroVersion = v;
        io.execute(() -> {
            WatchNext.deleteOldHistory(this);
            AppRows.Rows appRows = cw || rows ? AppRows.load(this) : new AppRows.Rows();
            List<Suggestions.Item> cwItems = cw
                    ? Carousel.continueWatching(this, WatchNext.all(this), appRows.resume) : new ArrayList<>();
            List<Suggestions.Item> appItems = rows ? Carousel.fromApps(this, appRows.byApp) : new ArrayList<>();
            StringBuilder sig = new StringBuilder();
            for (Suggestions.Item it : cwItems) sig.append(it.key).append(it.progress).append(';');
            for (Suggestions.Item it : appItems) sig.append(it.key).append(';');
            boolean cwChanged = !sig.toString().equals(lastCwSig);
            lastCwSig = sig.toString();
            List<Suggestions.Item> base = shelf ? Suggestions.cached(this, true) : null;
            List<Suggestions.Item> mixed = Carousel.compose(cwItems, appItems, base);
            if (!mixed.isEmpty() && (reconfigured || cwChanged || heroItems.isEmpty())) {
                handler.post(() -> setHeroItems(mixed));
            } else if (mixed.isEmpty()) {
                handler.post(() -> {
                    heroItems = new ArrayList<>();
                    shelfView.setFocusable(false);
                    setHeroShown(false);
                });
            }
            if (shelf && Suggestions.cached(this, false) == null) {
                try {
                    List<Suggestions.Item> fresh = Suggestions.fetch(this);
                    List<Suggestions.Item> m2 = Carousel.compose(cwItems, appItems, fresh);
                    handler.post(() -> setHeroItems(m2));
                } catch (Exception e) {
                    android.util.Log.w("TvHome", "suggestions: " + e);
                }
            }
        });
    }

    void updateEmpty() {
        boolean show = continueMode() && heroItems.isEmpty() && !folderMode;
        if (show) {
            shelfEmpty.setText(Perms.hasTvListings(this)
                    ? "Shows and movies you play will appear here."
                    : !WatchNext.supported()
                    ? "Continue Watching needs Android TV 8 or later."
                    : "To see what you watch here, allow it in Settings \u2192 Permissions.");
        }
        shelfEmpty.animate().alpha(show ? 0.75f : 0f).setDuration(Motion.d(250)).start();
    }

    void setHeroItems(List<Suggestions.Item> items) {
        // Keep the carousel where it was when the list is refreshed (same title -> same position).
        String curTitle = heroItems.isEmpty() ? null : heroItems.get(Math.min(heroIndex, heroItems.size() - 1)).title;
        boolean same = curTitle != null && heroFront.getDrawable() != null;
        if (items.size() > 24) items = new ArrayList<>(items.subList(0, 24));
        heroItems = items;
        int keep = 0;
        for (int i = 0; curTitle != null && i < items.size(); i++) if (items.get(i).title.equals(curTitle)) keep = i;
        heroIndex = keep;
        shelfView.setFocusable(!items.isEmpty());
        heroDots.removeAllViews();
        for (int i = 0; i < items.size() && items.size() > 1 && !continueMode(); i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setCornerRadius(dp(3));
            g.setColor(Color.WHITE);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.rightMargin = dp(5);
            heroDots.addView(d, lp);
        }
        updateDots(keep, false);
        buildStrip(items);
        if (items.isEmpty()) {
            setHeroShown(false);
            return;
        }
        View f = getCurrentFocus();
        int i = tiles.indexOf(f);
        boolean dock = f == shelfView || (hasDock() && i >= 0 && i < COLS);
        setHeroShown(dock);
        if (!(same && items.get(keep).title.equals(curTitle))) showHero(keep, 0);
        else updateDots(keep, false);
        // Download the artwork ahead, nearest to the current item first (both directions).
        List<Suggestions.Item> pre = new ArrayList<>();
        for (int d = 1; pre.size() < items.size() - 1 && d <= items.size(); d++) {
            Suggestions.Item a = items.get((keep + d) % items.size()), b = items.get((keep - d + items.size()) % items.size());
            if (!pre.contains(a) && a != items.get(keep)) pre.add(a);
            if (!pre.contains(b) && b != items.get(keep)) pre.add(b);
        }
        int gen = ++prefetchGen;
        prefetchIo.execute(() -> {
            for (Suggestions.Item it : pre) {
                if (gen != prefetchGen) return;  // a newer list arrived
                Suggestions.image(this, it.backdrop, "w1280", 64);  // downloads to disk; tiny decode
                Suggestions.logoFor(this, it);
            }
        });
    }

    /** Carousel vs launcher background: crossfade between them. */
    void setHeroShown(boolean show) {
        show = show && !heroItems.isEmpty() && !folderMode;
        if (show == heroShown) return;
        heroShown = show;
        glassBg = show && heroBlur != null ? heroBlur : wallBlur;
        invalidateGlass();
        float a = show ? 1f : 0f;
        if (!show) wallView.setVisibility(View.VISIBLE);
        heroFront.animate().alpha(a).setDuration(Motion.d(450)).withEndAction(() -> {
            if (heroShown && heroFront.getAlpha() >= 1f) wallView.setVisibility(View.INVISIBLE);
        }).start();
        heroScrim.setVisibility(View.GONE);

        heroInfo.animate().withLayer().alpha(a).setDuration(Motion.d(350)).start();
        heroDots.animate().alpha(a).setDuration(Motion.d(350)).start();
        chevron.animate().alpha(1f - a).setDuration(Motion.d(350)).start();
        heroBack.animate().alpha(0f).setDuration(Motion.d(450)).start();
        handler.removeCallbacks(heroAdvance);
        if (show) scheduleAdvance();
    }

    void scheduleAdvance() {
        handler.removeCallbacks(heroAdvance);
        if (continueMode()) return;  // Up Next stays put
        int sec = Suggestions.advanceSeconds(this);
        if (sec > 0 && heroIsNews()) sec = Math.max(sec, 15);  // time to read a story
        if (sec > 0 && heroShown && resumed) handler.postDelayed(heroAdvance, sec * 1000L);
    }

    void heroStep(int dir, boolean user) {
        if (heroItems.size() < 2) return;
        showHero((heroIndex + dir + heroItems.size()) % heroItems.size(), dir);
        if (!user || !shelfView.hasFocus()) scheduleAdvance();
    }

    /**
     * The page indicator: a capsule for the current story and dots for the ones near it, at most seven in view
     * (twenty-four dots read as a row of noise). The window follows the current story; the ends fade.
     */
    void updateDots(int index, boolean animate) {
        int n = heroDots.getChildCount(), win = 7;
        int start = n <= win ? 0 : Math.max(0, Math.min(index - win / 2, n - win));
        for (int i = 0; i < n; i++) {
            View d = heroDots.getChildAt(i);
            boolean in = i >= start && i < start + win;
            d.setVisibility(in ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = i == index ? dp(18) : dp(6);
            d.setLayoutParams(lp);
            boolean edge = n > win && ((i == start && start > 0) || (i == start + win - 1 && start + win < n));
            float a = i == index ? 1f : edge ? 0.18f : 0.38f;
            if (animate) d.animate().alpha(a).setDuration(Motion.d(250)).start(); else d.setAlpha(a);
        }
    }

    /** dir: +1 next, -1 previous, 0 first show (no slide). */
    void showHero(int index, int dir) {
        heroIndex = index;
        Suggestions.Item it = heroItems.get(index);
        updateDots(index, true);
        int want = index;
        heroIo.execute(() -> {
            if (heroIndex != want) return;  // already moved on: don't spend time on this one
            Bitmap raw = Suggestions.image(this, it.backdrop, "w1280", Math.min(Optimizer.heroPx(this), screenW));
            Bitmap bd = "news".equals(it.type) ? bakeNews(raw) : bakeScrims(raw);
            Bitmap bdBlur = Ui.glassBackdrop(bd, 160, 90);
            String logoPath = Suggestions.logoFor(this, it);
            Bitmap logo = logoPath.isEmpty() ? null : Suggestions.image(this, logoPath, "w500", 500);
            handler.post(() -> {
                if (heroIndex != want || heroItems.isEmpty()) return;
                applyHeroInfo(it, logo, dir);
                if (bd != null) crossfadeHero(bd, dir);
                if (bdBlur != null) {
                    heroBlur = bdBlur;
                    if (heroShown) {
                        glassBg = heroBlur;
                        invalidateGlass();
                    }
                }
            });
        });
    }

    /** Title/meta/overview for an item, applied in the same frame as its artwork. */
    void applyHeroInfo(Suggestions.Item it, Bitmap logo, int dir) {
        boolean news = "news".equals(it.type);
        heroRemove.setVisibility("continue".equals(it.type) && shelfView.isFocused() ? View.VISIBLE : View.GONE);
        if (shelfView.isFocused()) {
            heroButton.setVisibility(news ? View.GONE : View.VISIBLE);
            if (!news) heroButton.setAlpha(1f);
        }
        Ui.textSize(heroTitle, news ? 26 : 36);  // headlines are long: smaller, up to four lines
        heroTitle.setMaxLines(news ? 4 : 2);
        if (!news) {
            heroOverview.setMaxLines(2);
            Ui.textSize(heroOverview, 12.5f);
            if (!shelfView.isFocused()) {  // titles keep tvOS behaviour: overview only when focused
                heroOverview.animate().cancel();
                heroOverview.setAlpha(0f);
                heroOverview.setVisibility(View.GONE);
            }
        }
        heroTitle.setText(it.title);
        heroTitle.setVisibility(View.VISIBLE);
        heroLogo.setImageDrawable(null);
        heroLogo.setVisibility(View.GONE);
        selectStrip(heroIndex);
        if (!it.pkg.isEmpty()) {  // Continue Watching, or an app's own row
            boolean app = "app".equals(it.type);
            List<String> meta = new ArrayList<>();
            meta.add(appLabel(it.pkg));
            // Watch Next "Up Next" / "New", or the app's row name (unless it just repeats the app's name)
            if (!it.genres.isEmpty() && !it.genres.equalsIgnoreCase(meta.get(0))) meta.add(it.genres);
            if (it.remainingMin > 0) meta.add(it.remainingMin >= 60
                    ? (it.remainingMin / 60) + " h " + (it.remainingMin % 60) + " min left" : it.remainingMin + " min left");
            heroMeta.setText(String.join("  \u00B7  ", meta));
            heroOverview.setText(it.overview);
            heroBtnText.setText(app && it.progress <= 0 ? "\u25B6  Open" : "\u25B6  Resume");
            try {
                heroProvider.setImageDrawable(getPackageManager().getApplicationIcon(it.pkg));
                heroProvider.setVisibility(View.VISIBLE);
            } catch (PackageManager.NameNotFoundException e) {
                heroProvider.setVisibility(View.GONE);
            }
            heroBtnIcon.setVisibility(View.GONE);
            if (heroShown && dir != 0) {
                heroInfo.animate().cancel();
                heroInfo.setAlpha(0f);
                heroInfo.setTranslationX(dp(28) * dir);
                heroInfo.animate().withLayer().alpha(1f).translationX(0).setDuration(Motion.d(520)).setInterpolator(EASE).start();
            }
            return;
        }
        if ("news".equals(it.type)) {
            // News: section and newsroom, how long ago. No button: stories are read on the shelf.
            String age = News.age(it.published);
            heroMeta.setText(it.genres + (age.isEmpty() ? "" : "  \u00B7  " + age));
            heroOverview.setText(it.overview);
            // Stories are for reading: the summary stays visible, up to four lines.
            heroOverview.setMaxLines(4);
            Ui.textSize(heroOverview, 13.5f);
            heroOverview.animate().cancel();
            heroOverview.setVisibility(it.overview.isEmpty() ? View.GONE : View.VISIBLE);
            heroOverview.setAlpha(0.85f);
            heroProvider.setVisibility(View.GONE);
            heroBtnIcon.setVisibility(View.GONE);
            if (heroShown && dir != 0) {
                heroInfo.animate().cancel();
                heroInfo.setAlpha(0f);
                heroInfo.setTranslationX(dp(28) * dir);
                heroInfo.animate().withLayer().alpha(1f).translationX(0).setDuration(Motion.d(520)).setInterpolator(EASE).start();
            }
            return;
        }
        if ("art".equals(it.type)) {
            // Gallery: painting, then artist always visible, year and subject, button into the Gallery app.
            heroMeta.setText(it.year + (it.genres.isEmpty() ? "" : "  \u00B7  " + it.genres));
            heroOverview.setText(it.overview);
            heroOverview.setMaxLines(1);
            Ui.textSize(heroOverview, 16f);
            heroOverview.animate().cancel();
            heroOverview.setVisibility(View.VISIBLE);
            heroOverview.setAlpha(0.9f);
            heroBtnText.setText("\u25B6  " + It.t("Open the Gallery"));
            heroProvider.setVisibility(View.GONE);
            heroBtnIcon.setVisibility(View.GONE);
            if (heroShown && dir != 0) {
                heroInfo.animate().cancel();
                heroInfo.setAlpha(0f);
                heroInfo.setTranslationX(dp(28) * dir);
                heroInfo.animate().withLayer().alpha(1f).translationX(0).setDuration(Motion.d(520)).setInterpolator(EASE).start();
            }
            return;
        }
        List<String> meta = new ArrayList<>();
        if (!it.year.isEmpty()) meta.add(it.year);
        meta.add(it.type.equals("tv") ? "Series" : "Movie");
        if (!it.genres.isEmpty()) meta.add(it.genres);
        if (it.rating > 0) meta.add(String.format(java.util.Locale.US, "\u2605 %.1f", it.rating));
        heroMeta.setText(String.join("  \u00B7  ", meta));
        heroOverview.setText(it.overview);
        Suggestions.Provider p = it.provider(this);
        heroBtnText.setText(p != null && !Suggestions.installed(this, p.pkg) ? "Get" : "\u25B6  Open");
        int icon = p == null ? 0 : getResources().getIdentifier("provider_" + p.jwShort, "drawable", getPackageName());
        heroProvider.setImageResource(icon);
        heroProvider.setVisibility(icon == 0 ? View.GONE : View.VISIBLE);
        heroBtnIcon.setVisibility(View.GONE);
        if (logo != null) {
            heroLogo.setImageBitmap(logo);
            heroLogo.setVisibility(View.VISIBLE);
            heroTitle.setVisibility(View.GONE);
        }
        if (heroShown && dir != 0) {
            // Text glides in from the side you pressed, like the artwork.
            heroInfo.animate().cancel();
            heroInfo.setAlpha(0f);
            heroInfo.setTranslationX(dp(28) * dir);
            heroInfo.animate().withLayer().alpha(1f).translationX(0).setDuration(Motion.d(520)).setInterpolator(EASE).start();
        }
    }

    /**
     * Artwork transition (tvOS carousel): the new image slides in from the side you pressed and
     * fades up over the old one, which drifts the other way and fades out.
     */
    ObjectAnimator heroZoom;

    /**
     * The carousel artwork drifts slowly closer while it is on screen (Apple TV's Top Shelf and
     * screensaver feel): 1.0 → 1.06 over 18 s. Only with Motion at Full.
     */
    void slowZoom(ImageView v) {
        if (heroZoom != null) heroZoom.cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        if (!Motion.ambient()) return;
        heroZoom = ObjectAnimator.ofPropertyValuesHolder(v,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f));
        heroZoom.setDuration(18000);
        heroZoom.setInterpolator(new PathInterpolator(0.3f, 0f, 0.7f, 1f));
        heroZoom.start();
    }

    void crossfadeHero(Bitmap bd, int dir) {
        ImageView next = heroBack;
        heroBack = heroFront;
        heroFront = next;
        ImageView old = heroBack;
        next.animate().cancel();
        next.setImageBitmap(bd);
        slowZoom(next);
        float target = heroShown ? 1f : 0f;
        if (dir == 0 || !heroShown) {
            next.setTranslationX(0);
            next.setAlpha(target);
            old.setAlpha(0f);
            return;
        }
        next.setTranslationX(screenW * 0.12f * dir);
        next.setAlpha(0f);
        next.animate().translationX(0).alpha(1f).setDuration(Motion.d(650)).setInterpolator(EASE).start();
        old.animate().cancel();
        old.animate().translationX(-screenW * 0.06f * dir).alpha(0f).setDuration(Motion.d(650))
                .setInterpolator(EASE).withEndAction(() -> old.setTranslationX(0)).start();
    }

    /** Carousel focused: the dock slides away and the artwork takes the whole screen (tvOS Top Shelf). */
    boolean heroRequested;

    ImageView avatarView, ccButton, refreshButton, searchButton, netIcon;
    /** The search screen while it is open (its layer is also pickerLayer, so Home and idle treat it as an overlay). */
    SearchPanel search;
    android.net.ConnectivityManager.NetworkCallback netCallback;
    WeatherView weatherCard;
    LiquidGlass weatherGlass;
    int avatarSize;
    FrameLayout pickerLayer;
    BroadcastReceiver wakeReceiver;

    /** Top shelf content: "suggest" (carousel) or "continue" (Continue Watching). */
    String shelfMode;
    LinearLayout switcher;
    TextView tabSuggest, tabContinue;
    GradientDrawable tabSuggestBg, tabContinueBg;
    LinearLayout heroStrip;
    TextView shelfEmpty;

    void heroFocused(boolean has) {
        if (has && !heroRequested) {
            // Android parked focus here on its own (startup/rebuild): hand it back to the dock.
            handler.post(() -> focusIndex(Math.min(focusedIndex, Math.max(0, tiles.size() - 1))));
            return;
        }
        if (!has) heroRequested = false;
        if (has) {
            setHeroShown(true);
            heroButton.animate().scaleX(1.08f).scaleY(1.08f).setDuration(Motion.d(240)).setInterpolator(LIFT).start();
            heroButton.setElevation(dp(10));
            heroBtnBg.setColor(0xFFFFFFFF);
            heroBtnText.setTextColor(0xFF111114);
            scroll.smoothScrollTo(0, 0);
            scroll.animate().withLayer().translationY(screenH - shelfH + dp(30)).setDuration(Motion.d(500)).setInterpolator(EASE)
                    .setUpdateListener(null).withEndAction(this::invalidateGlass).start();
            heroInfo.animate().withLayer().translationY(0).setDuration(Motion.d(500)).setInterpolator(EASE).start();
            heroDots.animate().withLayer().translationY(0).setDuration(Motion.d(500)).setInterpolator(EASE).start();
            heroOverview.setVisibility(View.VISIBLE);
            heroButton.setVisibility(heroIsNews() ? View.GONE : View.VISIBLE);
            heroRemove.setVisibility(heroIsContinue() ? View.VISIBLE : View.GONE);
            heroRemove.setAlpha(0.7f);
            if (continueMode() && heroStrip.getChildCount() > 0) heroStrip.setVisibility(View.VISIBLE);
            heroOverview.animate().alpha(0.85f).setDuration(Motion.d(400)).setStartDelay(Motion.d(120)).start();
            heroButton.animate().alpha(1f).setDuration(Motion.d(400)).setStartDelay(Motion.d(120)).start();
            handler.removeCallbacks(heroAdvance);
        } else {
            heroButton.animate().scaleX(1f).scaleY(1f).setDuration(Motion.d(180)).start();
            heroButton.setElevation(0);
            heroBtnBg.setColor(0x47FFFFFF);
            heroBtnText.setTextColor(Color.WHITE);
            scroll.animate().withLayer().translationY(0).setDuration(Motion.d(450)).setInterpolator(EASE)
                    .setUpdateListener(null).withEndAction(this::invalidateGlass).start();
            heroInfo.animate().withLayer().translationY(heroInfoRestY()).setDuration(Motion.d(450)).setInterpolator(EASE).start();
            heroDots.animate().withLayer().translationY(heroDotsRestY()).setDuration(Motion.d(450)).setInterpolator(EASE).start();
            if (!heroIsNews()) {
                heroOverview.animate().alpha(0f).setDuration(Motion.d(160)).setStartDelay(0)
                        .withEndAction(() -> heroOverview.setVisibility(View.GONE)).start();
            }
            heroButton.animate().alpha(0f).setDuration(Motion.d(160)).setStartDelay(0)
                    .withEndAction(() -> heroButton.setVisibility(View.GONE)).start();
            heroRemove.setVisibility(View.GONE);
            heroStrip.setVisibility(View.GONE);
            scheduleAdvance();
        }
    }

    boolean heroIsContinue() {
        return heroIndex >= 0 && heroIndex < heroItems.size() && "continue".equals(heroItems.get(heroIndex).type);
    }

    /** Continue Watching and apps' rows have options (Hold OK / Menu). */
    boolean heroHasMenu() {
        return heroIndex >= 0 && heroIndex < heroItems.size() && !heroItems.get(heroIndex).pkg.isEmpty();
    }

    /**
     * Hold OK or Menu on a carousel card: Continue Watching offers Resume / Remove; an app's row
     * offers Open / Hide this app's rows (Settings → Top Shelf turns them back on).
     */
    void heroMenu(Suggestions.Item it) {
        List<String> l = new ArrayList<>();
        List<Runnable> act = new ArrayList<>();
        if ("continue".equals(it.type)) {
            l.add("Resume");
            act.add(() -> openHeroItem(it));
            l.add("Remove from Continue Watching");
            act.add(() -> removeHeroItem(it));
        } else {
            String app = appLabel(it.pkg);
            l.add("Open");
            act.add(() -> openHeroItem(it));
            l.add("Don\u2019t show " + app + " here");
            act.add(() -> {
                AppRows.setHidden(this, it.pkg, true);
                lastCwSig = "";
                heroVersion = -1;
                toast(app + " won\u2019t appear in the carousel");
                loadHero();
            });
        }
        showMenu(heroButton, it.title, l, act, 1);
    }

    void removeHeroItem(Suggestions.Item it) {
        forgetContinue(it);
        lastCwSig = "";
        heroVersion = -1;
        toast("Removed " + it.title);
        loadHero();
    }

    /** News stories have no button: they are read right on the shelf. */
    boolean heroIsNews() {
        return heroIndex >= 0 && heroIndex < heroItems.size() && "news".equals(heroItems.get(heroIndex).type);
    }

    /** Carousel and capsule navigation: LEFT/RIGHT browse, OK opens, UP/DOWN move between capsule, carousel and dock. */
    boolean heroKeys(KeyEvent ev) {
        if (moveIndex >= 0) return false;
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        View f = getCurrentFocus();
        boolean heroActive = shelfView.isFocusable() && !folderMode;
        if (f == shelfView) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) heroStep(-1, true);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) heroStep(1, true);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    // A press opens; held past the key-repeat delay (~0.5 s) on a Continue Watching
                    // card it offers Resume / Remove instead. The menu takes the release, so a fresh
                    // press clears the flag.
                    if (down && ev.getRepeatCount() == 0) {
                        heroHeld = false;
                    } else if (down && ev.getRepeatCount() == 1 && heroHasMenu()) {
                        heroHeld = true;
                        heroMenu(heroItems.get(heroIndex));
                    } else if (!down && !heroItems.isEmpty() && !heroHeld) {
                        openHeroItem(heroItems.get(heroIndex));
                    }
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    if (!down && heroHasMenu()) heroMenu(heroItems.get(heroIndex));
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP: if (down) focusTop(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_BACK:
                    if (down) focusIndex(Math.min(focusedIndex, COLS - 1));
                    return true;
                default: return false;
            }
        }
        if (f == switcher) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) {
                        if (continueMode()) setShelfMode("suggest");
                        else if (weatherShown()) weatherCard.requestFocus();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // Past the last tab, Right continues to the status row (Control Center).
                    if (down) {
                        if (continueMode()) ccButton.requestFocus();
                        else setShelfMode("continue");
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (!down) setShelfMode(continueMode() ? "suggest" : "continue");
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP: if (down) ccButton.requestFocus(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_BACK:
                    if (down) {
                        if (heroActive) {
                            heroRequested = true;
                            shelfView.requestFocus();
                        } else {
                            focusIndex(Math.min(focusedIndex, COLS - 1));
                        }
                    }
                    return true;
                default: return false;
            }
        }
        if (f == refreshButton || f == searchButton || f == ccButton || f == avatarView) {
            // Status row, left to right: Refresh, Search, Control Center, profile.
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (down) {
                    if (f == avatarView) ccButton.requestFocus();
                    else if (f == ccButton) searchButton.requestFocus();
                    else if (f == searchButton) refreshButton.requestFocus();
                    else if (switcher.getVisibility() == View.VISIBLE) switcher.requestFocus();
                    else if (weatherShown()) weatherCard.requestFocus();
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) {
                    if (f == refreshButton) searchButton.requestFocus();
                    else if (f == searchButton) ccButton.requestFocus();
                    else if (f == ccButton) avatarView.requestFocus();
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_BACK) {
                if (down) focusBelowStatus();
                return true;
            }
            return k == KeyEvent.KEYCODE_DPAD_UP;
        }
        if (f == weatherCard) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) focusTop(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_BACK:
                    if (down) {
                        if (heroActive) {
                            heroRequested = true;
                            shelfView.requestFocus();
                        } else {
                            focusIndex(Math.min(focusedIndex, COLS - 1));
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return true;
                default: return false;
            }
        }
        int i = tiles.indexOf(f);
        if (i >= 0 && ((k == KeyEvent.KEYCODE_DPAD_RIGHT && (i % COLS == COLS - 1 || i == tiles.size() - 1))
                || (k == KeyEvent.KEYCODE_DPAD_LEFT && i % COLS == 0))) {
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP && i >= 0 && i < COLS && (hasDock() || i < COLS)) {
            if (down) {
                if (heroActive) {
                    heroRequested = true;
                    shelfView.requestFocus();
                } else {
                    focusTop();
                }
            }
            return true;
        }
        return false;
    }

    void openHeroItem(Suggestions.Item it) {
        if ("news".equals(it.type)) return;  // news is read on the shelf; there is nothing to open
        if ("art".equals(it.type)) {         // our Gallery app: browse the whole collection with the remote
            try {
                startActivity(new Intent().setClassName("org.lumesuite.gallery", "org.lumesuite.gallery.GalleryActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                // Gallery app not installed: nothing to open
            }
            return;
        }
        if (it.pkg.isEmpty()) {
            Suggestions.open(this, it);
            return;
        }
        WatchNext.open(this, it);  // the title itself where the app published a link, else the app
    }

    /** Remove from Continue Watching: apps' rows can't be deleted, so it is hidden until watched again. */
    void forgetContinue(Suggestions.Item it) {
        WatchNext.dismiss(this, it.key);
    }

    // ---------------------------------------------------------------- focus sway

    ValueAnimator sway;
    View swayView;

    /** Slow elliptical tilt of the focused card with the highlight drifting against it. */
    void startSway(View v, View glare) {
        if (!Motion.ambient() || moveIndex >= 0 || !v.hasFocus() && !v.isFocused()) return;
        stopSway(swayView);
        swayView = v;
        sway = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        sway.setDuration(5200);
        sway.setRepeatCount(ValueAnimator.INFINITE);
        sway.setInterpolator(new android.view.animation.LinearInterpolator());
        sway.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            float sin = (float) Math.sin(t), cos = (float) Math.cos(t);
            v.setRotationY(2.2f * sin);
            v.setRotationX(-1.4f * cos);
        });
        sway.start();
    }

    void stopSway(View v) {
        if (sway != null && (v == null || v == swayView)) {
            sway.cancel();
            sway = null;
            if (swayView != null) {
                swayView.animate().rotationX(0).rotationY(0).setDuration(Motion.d(160)).start();
            }
            swayView = null;
        }
    }

    // ---------------------------------------------------------------- launch animation

    /**
     * tvOS-style launch: a full-screen card in the app's colour grows out of the tile while the
     * grid recedes; the app is started near the end of the zoom and cross-fades in.
     */
    void launchAnimated(View tile, AppEntry a) {
        if (launchOverlay != null) return;
        if (INPUTS_KEY.equals(a.key)) {
            showInputs(tile);
            return;
        }
        if (a.pkg != null) RecentAppsOverlay.recordAppUsed(this, a.pkg);  // Recent Apps works even without the accessibility service
        // Calm launch: the tile lifts a little while the Home fades back, and the app fades in over it.
        View ov = new View(this);   // placeholder so finishLaunchReturn() can restore the Home on return
        root.addView(ov, new FrameLayout.LayoutParams(1, 1));
        launchOverlay = ov;
        launchTile = tile;
        launchTileSx = tile.getScaleX();
        launchTileSy = tile.getScaleY();
        tile.animate().scaleX(launchTileSx * 1.08f).scaleY(launchTileSy * 1.08f)
                .setDuration(Motion.d(160)).setInterpolator(EASE).start();
        scroll.animate().withLayer().scaleX(0.96f).scaleY(0.96f).alpha(0f)
                .setDuration(Motion.d(240)).setInterpolator(EASE).setUpdateListener(null).start();
        clockBox.animate().alpha(0f).setDuration(Motion.d(200)).start();
        switcher.animate().alpha(0f).setDuration(Motion.d(200)).start();
        weatherCard.animate().alpha(0f).setDuration(Motion.d(200)).start();

        handler.postDelayed(() -> {
            try {
                Bundle opts = ActivityOptions.makeCustomAnimation(
                        this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
                startActivity(a.launch, opts);
                noteLaunched(a.pkg);
            } catch (ActivityNotFoundException | SecurityException e) {
                toast("Can't open " + a.label);
                finishLaunchReturn();
            }
        }, 140);
    }

    /** Coming back to Home after a launch: the card fades away and the grid zooms back in. */
    void finishLaunchReturn() {
        View ov = launchOverlay;
        View tile = launchTile;
        launchOverlay = null;
        launchTile = null;
        if (ov == null) return;
        handler.removeCallbacksAndMessages(null);

        scroll.setScaleX(1.06f);
        scroll.setScaleY(1.06f);
        scroll.animate().withLayer().scaleX(1f).scaleY(1f).alpha(1f).setDuration(Motion.d(420)).setInterpolator(EASE)
                .setUpdateListener(null).withEndAction(this::invalidateGlass).start();
        clockBox.animate().alpha(clockAlpha()).setDuration(Motion.d(420)).start();
        switcher.animate().alpha(folderMode ? 0f : clockAlpha()).setDuration(Motion.d(420)).start();
        weatherCard.animate().alpha(weatherAlpha()).setDuration(Motion.d(420)).start();
        if (tile != null) tile.animate().alpha(1f).scaleX(launchTileSx).scaleY(launchTileSy).setDuration(Motion.d(300)).start();
        ov.animate().withLayer().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(Motion.d(300))
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        root.removeView(ov);
                    }
                }).start();
        if (tile != null) tile.requestFocus();
    }

    /**
     * The colour the app paints first: its Android 12 splash background, else its window
     * background. Ending the zoom on this colour makes the handoff to the app seamless.
     */
    int launchBackground(AppEntry a, int fallback) {
        ComponentName cn = a.launch.getComponent();
        if (cn == null) return fallback;
        try {
            ActivityInfo ai = getPackageManager().getActivityInfo(cn, 0);
            Context pc = createPackageContext(cn.getPackageName(), 0);
            Resources.Theme theme = pc.getResources().newTheme();
            theme.applyStyle(ai.getThemeResource(), true);
            int[] attrs = Build.VERSION.SDK_INT >= 31
                    ? new int[]{android.R.attr.windowSplashScreenBackground, android.R.attr.windowBackground}
                    : new int[]{android.R.attr.windowBackground};
            TypedArray ta = theme.obtainStyledAttributes(attrs);
            try {
                for (int i = 0; i < attrs.length; i++) {
                    TypedValue v = ta.peekValue(i);
                    if (v == null) continue;
                    int color = 0;
                    if (v.type >= TypedValue.TYPE_FIRST_COLOR_INT && v.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        color = v.data;
                    } else {
                        Drawable d = ta.getDrawable(i);
                        if (d instanceof ColorDrawable) color = ((ColorDrawable) d).getColor();
                    }
                    if (Color.alpha(color) > 200) return color | 0xFF000000;
                }
            } finally {
                ta.recycle();
            }
        } catch (Exception e) {
            // Theme unreadable: keep the banner colour.
        }
        return fallback;
    }

    static int colorDistance(int x, int y) {
        return Math.abs(Color.red(x) - Color.red(y)) + Math.abs(Color.green(x) - Color.green(y))
                + Math.abs(Color.blue(x) - Color.blue(y));
    }

    static int blendArgb(int x, int y, float t) {
        return Color.argb(
                (int) (Color.alpha(x) + (Color.alpha(y) - Color.alpha(x)) * t),
                (int) (Color.red(x) + (Color.red(y) - Color.red(x)) * t),
                (int) (Color.green(x) + (Color.green(y) - Color.green(x)) * t),
                (int) (Color.blue(x) + (Color.blue(y) - Color.blue(x)) * t));
    }

    static int blend(int x, int y, float t) {
        return Color.rgb(
                (int) (Color.red(x) + (Color.red(y) - Color.red(x)) * t),
                (int) (Color.green(x) + (Color.green(y) - Color.green(x)) * t),
                (int) (Color.blue(x) + (Color.blue(y) - Color.blue(x)) * t));
    }

    /** Colour along the banner's edges, so the zoomed card blends with the banner art. */
    int edgeColor(AppEntry a) {
        Bitmap b = backdropSharp(a, 96, 54);
        int w = b.getWidth() - 3, h = b.getHeight() - 3;
        int[] px = {b.getPixel(2, 2), b.getPixel(w, 2), b.getPixel(2, h), b.getPixel(w, h),
                b.getPixel(w / 2, 2), b.getPixel(w / 2, h)};
        int r = 0, g = 0, bl = 0;
        for (int p : px) {
            r += Color.red(p);
            g += Color.green(p);
            bl += Color.blue(p);
        }
        b.recycle();
        return Color.rgb(r / px.length, g / px.length, bl / px.length);
    }

    String appLabel(String pkg) {
        AppEntry e = null;
        for (AppEntry a : all.values()) if (pkg.equals(a.pkg)) e = a;
        if (e != null) return e.label;
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException ex) {
            return pkg;
        }
    }

    /** Up Next strip: one thumbnail per entry with a progress bar; the current one is lifted. */
    void buildStrip(List<Suggestions.Item> items) {
        heroStrip.removeAllViews();
        if (!continueMode()) return;
        for (int i = 0; i < items.size() && i < 8; i++) {
            Suggestions.Item it = items.get(i);
            FrameLayout th = new FrameLayout(this);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setCornerRadius(dp(6));
            tbg.setColor(0x33FFFFFF);
            th.setBackground(tbg);
            th.setOutlineProvider(roundedOutline(new float[]{dp(6)}));
            th.setClipToOutline(true);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.addView(iv, match());
            if (it.progress >= 0) {
                View track = new View(this);
                track.setBackgroundColor(0x4DFFFFFF);
                FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3),
                        Gravity.BOTTOM);
                tlp.setMargins(dp(6), 0, dp(6), dp(5));
                th.addView(track, tlp);
                View bar = new View(this);
                bar.setBackgroundColor(Color.WHITE);
                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                        Math.max(dp(3), Math.round((dp(100) - dp(12)) * it.progress)), dp(3), Gravity.BOTTOM | Gravity.START);
                blp.setMargins(dp(6), 0, 0, dp(5));
                th.addView(bar, blp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(100), dp(56));
            lp.rightMargin = dp(10);
            heroStrip.addView(th, lp);
            io.execute(() -> {
                Bitmap b = Suggestions.image(this, it.backdrop, "w300", 240);
                if (b != null) handler.post(() -> iv.setImageBitmap(b));
            });
        }
    }

    void selectStrip(int index) {
        for (int i = 0; i < heroStrip.getChildCount(); i++) {
            View v = heroStrip.getChildAt(i);
            boolean on = i == index;
            v.animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).alpha(on ? 1f : 0.6f).setDuration(Motion.d(220))
                    .setInterpolator(LIFT).start();
        }
    }

    /** Banner rendered sharp, minus a hairline border so anti-aliased edges don't get extended. */
    Bitmap bannerArt(AppEntry a) {
        int w = 640, h = 360, in = 3;
        Bitmap full = backdropSharp(a, w, h);
        Bitmap art = Bitmap.createBitmap(full, in, in, w - 2 * in, h - 2 * in);
        if (art != full) full.recycle();
        return art;
    }

    /**
     * The launch card, drawn in one pass so nothing distorts: a rounded rect that grows from the
     * card's on-screen rect to full screen while its corners flatten. Banner apps draw the banner
     * centre-cropped exactly like the card, with its edge pixels clamped outward, so the whole
     * screen continues the card's own background; the logo settles at ~42% of the screen.
     */
    static class LaunchCard extends Drawable {
        final RectF card;
        final float r0;
        final Bitmap art;
        final Drawable icon;
        final int color0, color1;
        final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Matrix m = new android.graphics.Matrix();
        final android.graphics.Path clip = new android.graphics.Path();
        final RectF o = new RectF(), br = new RectF();
        float f;

        LaunchCard(RectF card, float r0, Bitmap art, Drawable icon, int color0, int color1) {
            this.card = card;
            this.r0 = r0;
            this.art = art;
            this.icon = icon;
            this.color0 = color0;
            this.color1 = color1;
            if (art != null) {
                artPaint.setShader(new android.graphics.BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            }
        }

        void setProgress(float f) {
            this.f = f;
            invalidateSelf();
        }

        static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float W = b.width(), H = b.height();
            o.set(lerp(card.left, 0, f), lerp(card.top, 0, f), lerp(card.right, W, f), lerp(card.bottom, H, f));
            float rad = r0 * (1f - f);
            if (art != null) {
                // Banner rect: starts as the card itself, ends centred at 42% of the screen.
                float bw = lerp(card.width(), W * 0.42f, f), bh = lerp(card.height(), H * 0.42f, f);
                br.set(o.centerX() - bw / 2, o.centerY() - bh / 2, o.centerX() + bw / 2, o.centerY() + bh / 2);
                float scale = Math.max(br.width() / art.getWidth(), br.height() / art.getHeight());
                m.setScale(scale, scale);
                m.postTranslate(br.centerX() - art.getWidth() * scale / 2, br.centerY() - art.getHeight() * scale / 2);
                artPaint.getShader().setLocalMatrix(m);
                c.drawRoundRect(o, rad, rad, artPaint);
            } else {
                float t = Math.max(0f, Math.min(1f, (f - 0.55f) / 0.45f));
                fill.setColor(blend(color0, color1, t));
                c.drawRoundRect(o, rad, rad, fill);
                if (icon != null) {
                    float s = lerp(card.height() * 0.55f, H * 0.27f, f);
                    Rect old = icon.copyBounds();
                    icon.setBounds((int) (o.centerX() - s / 2), (int) (o.centerY() - s / 2),
                            (int) (o.centerX() + s / 2), (int) (o.centerY() + s / 2));
                    icon.draw(c);
                    icon.setBounds(old);
                }
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    Drawable copyOf(Drawable d) {
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable(getResources()).mutate() : d;
    }

    float clockAlpha() {
        return Math.max(0f, 1f - scroll.getScrollY() / (shelfH * 0.4f));
    }

    // ---------------------------------------------------------------- focus & shelf

    void onTileFocused(int index) {
        focusedIndex = index;
        boolean dock = hasDock() && index < COLS;
        setHeroShown(dock && !heroItems.isEmpty());
        scrollToRow(index / COLS);
        handler.removeCallbacks(shelfUpdate);
        handler.postDelayed(shelfUpdate, 90);
    }

    /**
     * Row tops come from the fixed grid geometry rather than getTop(), so this also works right
     * after buildGrid() (e.g. while moving a card), before the new rows have been laid out.
     */
    void scrollToRow(int row) {
        int rowH = tileH + labelH + gap / 2;
        int rowTop = !hasDock() ? shelfH + row * rowH
                : row == 0 ? shelfH
                : shelfH + tileH + 2 * dockPadV + dockGapBelow() + (row - 1) * rowH;
        int target = row == 0 ? 0 : rowTop - (int) (screenH * 0.30f);
        scroll.smoothScrollTo(0, Math.max(0, target));
    }

    void updateShelf(AppEntry a) {
        shelfEntry = a;
    }

    void focusIndex(int i) {
        if (tiles.isEmpty()) return;
        i = Math.max(0, Math.min(i, tiles.size() - 1));
        tiles.get(i).requestFocus();
        focusedIndex = i;
        updateShelf(shown.get(i));
        int row = i / COLS;
        scroll.post(() -> scrollToRow(row));
    }

    void goTop() {
        scroll.smoothScrollTo(0, 0);
        focusIndex(0);
    }

    String currentKey() {
        return focusedIndex >= 0 && focusedIndex < shown.size() ? shown.get(focusedIndex).key : null;
    }

    int indexOfKey(String key) {
        for (int j = 0; j < shown.size(); j++) if (shown.get(j).key.equals(key)) return j;
        return -1;
    }

    // ---------------------------------------------------------------- folder of hidden apps

    AppEntry folderEntry() {
        AppEntry f = new AppEntry();
        f.key = FOLDER_KEY;
        f.label = "More Apps";
        f.color = 0xFF3A3A3C;
        f.banner = new BitmapDrawable(getResources(), folderArt());
        f.icon = f.banner;
        return f;
    }

    /** Folder card: dark glass gradient, 3x3 app-grid glyph, title and hidden-app count. */
    Bitmap folderArt() {
        int w = 640, h = 360;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        // tvOS system-icon style: a vivid vertical gradient, white glyph, no words.
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF6E7BF2, 0xFF3A3FC8, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        int cell = 46, gapPx = 18, grid = cell * 3 + gapPx * 2;
        float gx = (w - grid) / 2f, gy = (h - grid) / 2f;
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0x33000000);
        sh.setMaskFilter(new android.graphics.BlurMaskFilter(10, android.graphics.BlurMaskFilter.Blur.NORMAL));
        p.setColor(Color.WHITE);
        for (int i = 0; i < 9; i++) {
            float x = gx + (i % 3) * (cell + gapPx), y = gy + (i / 3) * (cell + gapPx);
            c.drawRoundRect(x, y + 5, x + cell, y + cell + 5, 13, 13, sh);
            c.drawRoundRect(x, y, x + cell, y + cell, 13, 13, p);
        }
        return b;
    }

    Bitmap backdropSharp(AppEntry e, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(e.banner != null ? 0xFF1C1C1E : e.color);
        Drawable d = e.banner != null ? e.banner : e.icon;
        if (d != null) {
            Rect old = d.copyBounds();
            if (e.banner != null) {
                d.setBounds(0, 0, w, h);
            } else {
                int s = h * 6 / 10;
                d.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
            }
            d.draw(c);
            d.setBounds(old);
        }
        return b;
    }

    void enterFolder() {
        if (hiddenApps.isEmpty()) return;
        animateGridSwap(() -> {
            folderMode = true;
            setHeroShown(false);
            switcher.setAlpha(0f);
            weatherCard.setAlpha(0f);
            shelfEmpty.setAlpha(0f);
            buildGrid();
            shelfEntry = null;
            scroll.scrollTo(0, 0);
            focusIndex(0);
        });
    }

    void exitFolder() {
        animateGridSwap(() -> {
            folderMode = false;
            switcher.animate().alpha(1f).setDuration(Motion.d(250)).start();
            weatherCard.animate().alpha(weatherAlpha()).setDuration(Motion.d(250)).start();
            buildGrid();
            shelfEntry = null;
            int i = indexOfKey(FOLDER_KEY);
            focusIndex(i >= 0 ? i : 0);
        });
    }

    /** Quick zoom-through between home and folder, so it reads as "opening" the folder. */
    void animateGridSwap(Runnable swap) {
        scroll.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(Motion.d(140)).setInterpolator(EASE)
                .withEndAction(() -> {
                    swap.run();
                    scroll.setScaleX(1.04f);
                    scroll.setScaleY(1.04f);
                    scroll.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.d(260)).setInterpolator(EASE)
                            .withEndAction(null).start();
                }).start();
    }

    // ---------------------------------------------------------------- keys

    /** Unused keys go to the views; a D-pad press nothing used moves focus here (no Android sound). */
    boolean superDispatch(KeyEvent ev) {
        return super.dispatchKeyEvent(ev) || Clicks.moveFocus(getWindow(), ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (launchOverlay != null) return true;
        // Idle full-screen carousel: the first press only brings the Home Screen back.
        if (idle) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                exitIdle();
                swallowUp = true;
                // Left / Right also move the carousel (it is what's on screen), instead of being lost.
                int k = ev.getKeyCode();
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    heroStep(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, true);
                }
            }
            return true;
        }
        if (swallowUp && ev.getAction() == KeyEvent.ACTION_UP) {
            swallowUp = false;
            return true;
        }
        resetIdle();
        if (!replayingBack) navSound(this, ev);  // a replayed Back already clicked
        if (search != null && pickerLayer == search.layer && menuLayer == null) {
            return search.dispatch(ev) || superDispatch(ev);
        }
        if (pickerLayer == null && menuLayer == null && moveIndex < 0 && keyboardSearch(ev)) return true;
        if (pickerLayer == null && menuLayer == null && moveIndex < 0 && holdBackSearch(ev)) return true;
        if (ev.getKeyCode() == KeyEvent.KEYCODE_SEARCH && pickerLayer == null && menuLayer == null && moveIndex < 0) {
            // The remote's search/mic button: open Search and start listening, like tvOS's Siri button.
            if (ev.getAction() == KeyEvent.ACTION_UP) {
                showSearch(null);
                if (search.mic != null) search.mic.requestFocus();
                search.listen();
            }
            return true;
        }
        if (pickerLayer != null && menuLayer == null) {
            int pk = ev.getKeyCode();
            boolean pd = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (pk == KeyEvent.KEYCODE_BACK) {
                if (!pd) closeProfilePicker();
                return true;
            }
            if (pk == KeyEvent.KEYCODE_MENU) {
                if (!pd) profileMenu(getCurrentFocus());
                return true;
            }
            if (pk == KeyEvent.KEYCODE_DPAD_UP || pk == KeyEvent.KEYCODE_DPAD_DOWN
                    || pk == KeyEvent.KEYCODE_DPAD_LEFT || pk == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (pd) pickerMove(pk);
                return true;
            }
            return superDispatch(ev);
        }
        if (menuLayer != null) {
            int mk = ev.getKeyCode();
            if (mk == KeyEvent.KEYCODE_BACK || mk == KeyEvent.KEYCODE_MENU || mk == KeyEvent.KEYCODE_ESCAPE) {
                if (ev.getAction() == KeyEvent.ACTION_UP) closeMenu();
                return true;
            }
            return superDispatch(ev);
        }
        if (heroKeys(ev)) return true;
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;

        if (moveIndex >= 0) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:   if (down) moveTo(moveIndex - 1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:  if (down) moveTo(moveIndex + 1); return true;
                case KeyEvent.KEYCODE_DPAD_UP:     if (down) moveTo(moveIndex - COLS); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:   if (down) moveTo(moveIndex + COLS); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:      if (down) endMove(); return true;
                default: return superDispatch(ev);
            }
        }
        if (k == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                int i = tiles.indexOf(getCurrentFocus());
                if (i >= 0 && !FOLDER_KEY.equals(shown.get(i).key)) showOptions(i);
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                if (folderMode) exitFolder();
                else goTop();
            }
            return true;
        }
        return superDispatch(ev);
    }

    // ---------------------------------------------------------------- apps

    void loadApps() {
        all.clear();
        apps.clear();
        hiddenApps.clear();
        PackageManager pm = getPackageManager();
        Set<String> pkgs = new HashSet<>();
        addResolved(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, pkgs, true);
        addResolved(pm, Intent.CATEGORY_LAUNCHER, pkgs, false);
        if (!pkgs.contains(TV_SETTINGS)) addSettingsEntry(pm);
        if (org.lumesuite.launcher.present.Presenter.enabled(this)) addPresentEntry();
        if (AirPlayActivity.receiver(this) != null) addAirPlayEntry();
        inputs = Inputs.list(this);
        if (Inputs.showCard(this) && !inputs.isEmpty()) addInputsEntry();
        // Lume's own launcher entry has no place on its own home screen.
        for (java.util.Iterator<AppEntry> it = all.values().iterator(); it.hasNext(); ) {
            AppEntry e = it.next();
            if (e.pkg.equals(getPackageName()) && e.key.contains(".MainActivity")) it.remove();
        }
        for (AppEntry e : all.values()) {
            e.style = prefs.getString("style_" + e.pkg, "auto");
            if ("auto".equals(e.style) && e.banner != null && weakBanner(e.banner)) e.style = "tile";
            // An app with no icon of its own but a banner is usually the default one (a blank gradient): draw the card instead.
            if (e.banner == null || (e.generic && !e.system) || TVOS_CARD_PKGS.contains(e.pkg) || "tile".equals(e.style) || "mono".equals(e.style)
                    || TILE_PKGS.contains(e.pkg)) {
                e.banner = new BitmapDrawable(getResources(), tvosCard(e));
            }
        }

        // Amazon's internal tools (early access, profile picker, system monitor...) are not for the home screen: tucked
        // away once, on the first run, and always one step from coming back (hidden apps).
        if (!prefs.getBoolean("auto_hidden_v1", false)) {
            Set<String> h = new HashSet<>(prefs.getStringSet(PREF_HIDDEN, Collections.emptySet()));
            for (AppEntry e : all.values()) if (AUTO_HIDE.contains(e.pkg)) h.add(e.key);
            prefs.edit().putStringSet(PREF_HIDDEN, h).putBoolean("auto_hidden_v1", true).apply();
        }
        Set<String> hidden = prefs.getStringSet(PREF_HIDDEN, Collections.emptySet());
        Map<String, AppEntry> pool = new LinkedHashMap<>();
        for (AppEntry e : all.values()) {
            if (hidden.contains(e.key)) hiddenApps.add(e);
            else pool.put(e.key, e);
        }
        Collections.sort(hiddenApps, (x, y) -> x.label.compareToIgnoreCase(y.label));

        for (String key : prefs.getString(PREF_ORDER, "").split(",")) {
            AppEntry e = pool.remove(key);
            if (e != null) apps.add(e);
        }
        List<AppEntry> rest = new ArrayList<>(pool.values());
        Collections.sort(rest, (x, y) -> {
            int px = priority(x), py = priority(y);
            return px != py ? Integer.compare(px, py) : x.label.compareToIgnoreCase(y.label);
        });
        apps.addAll(rest);
        // Inputs starts as the first card in the dock until it is moved.
        if (!Arrays.asList(prefs.getString(PREF_ORDER, "").split(",")).contains(INPUTS_KEY)) {
            for (int i = 0; i < apps.size(); i++) {
                if (INPUTS_KEY.equals(apps.get(i).key)) {
                    apps.add(0, apps.remove(i));
                    break;
                }
            }
        }
    }

    /** Fallback Settings tile for TVs whose settings app has no launcher entry. */
    void addSettingsEntry(PackageManager pm) {
        AppEntry s = new AppEntry();
        s.key = SETTINGS_KEY;
        s.pkg = TV_SETTINGS;
        s.label = "Settings";
        s.system = true;
        s.launch = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            s.banner = pm.getApplicationBanner(s.pkg);
            s.icon = pm.getApplicationIcon(s.pkg);
        } catch (PackageManager.NameNotFoundException e) {
            s.icon = getDrawable(android.R.drawable.ic_menu_preferences);
        }
        s.color = 0xFF3A3A3C;
        all.put(s.key, s);
    }

    int priority(AppEntry e) {
        if (INPUTS_KEY.equals(e.key)) return -1;
        if (AIRPLAY_KEY.equals(e.key)) return 0;
        if (isSettings(e)) return 2000;
        if (PRESENT_KEY.equals(e.key)) return 1999;
        int i = DEFAULT_ORDER.indexOf(e.pkg);
        return i >= 0 ? i : 1000;
    }

    boolean isSettings(AppEntry e) {
        return SETTINGS_KEY.equals(e.key) || TV_SETTINGS.equals(e.pkg) || PRESENT_KEY.equals(e.key) || AIRPLAY_KEY.equals(e.key);
    }

    List<Inputs.Input> inputs = new ArrayList<>();

    /** Inputs card: a graphite tvOS-style card with the input glyph; OK opens the input picker. */
    void addInputsEntry() {
        AppEntry e = new AppEntry();
        e.key = INPUTS_KEY;
        e.pkg = getPackageName();
        e.label = "Inputs";
        e.system = true;
        e.color = 0xFF48484A;
        int w = 640, h = 384;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF6E6E73, 0xFF2C2C2E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        Bitmap g = Glyphs.get("input", (int) (h * 0.5f), Color.WHITE);
        c.drawBitmap(g, (w - g.getWidth()) / 2f, (h - g.getHeight()) / 2f, null);
        e.banner = new BitmapDrawable(getResources(), b);
        e.icon = e.banner;
        all.put(e.key, e);
    }

    /** AirPlay card: opens the guided "Share to this TV" page; the receiver app itself is hidden from the grid. */
    void addAirPlayEntry() {
        AppEntry e = new AppEntry();
        e.key = AIRPLAY_KEY;
        e.pkg = getPackageName();
        e.label = "AirPlay";
        e.system = true;
        e.color = 0xFF4A5BFF;
        // Our own receiver opens straight into its screen (it already shows the three steps and its state); the
        // guided page remains for the other receivers.
        Intent own = Suggestions.installed(this, "org.lumesuite.cast")
                ? getPackageManager().getLaunchIntentForPackage("org.lumesuite.cast") : null;
        e.launch = own != null ? own.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                : new Intent(this, AirPlayActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        e.banner = new BitmapDrawable(getResources(), AirPlayActivity.card(480, 270));
        e.icon = e.banner;
        all.put(e.key, e);
    }

    /** Presentation tile (shown while Presentation mode is on): tvOS-style card. */
    void addPresentEntry() {
        AppEntry e = new AppEntry();
        e.key = PRESENT_KEY;
        e.pkg = getPackageName();
        e.label = "Presentation";
        e.system = true;
        e.color = 0xFF4370FF;
        e.launch = new Intent(this, org.lumesuite.launcher.present.PresentationActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bitmap b = org.lumesuite.launcher.present.PresentIcon.banner(640, 384);
        e.banner = new BitmapDrawable(getResources(), b);
        e.icon = e.banner;
        all.put(e.key, e);
    }

    void addResolved(PackageManager pm, String category, Set<String> pkgs, boolean leanback) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(getPackageName()) || pkgs.contains(ai.packageName) || AirPlayActivity.isReceiver(ai.packageName)) continue;
            AppEntry e = new AppEntry();
            ComponentName cn = new ComponentName(ai.packageName, ai.name);
            e.pkg = ai.packageName;
            e.key = cn.flattenToShortString();
            e.label = ri.loadLabel(pm).toString();
            if (e.pkg.startsWith("com.amazon.") && "Home".equalsIgnoreCase(e.label)) e.label = It.t("Amazon Home");   // the way back to Amazon's own screen
            e.banner = leanback ? ai.loadBanner(pm) : null;
            e.icon = ri.loadIcon(pm);
            e.generic = ai.icon == 0 && ai.applicationInfo.icon == 0 && ai.logo == 0;
            Drawable hi = hiIcon(pm, ai);       // the icon at its sharpest, not at this TV's lower density
            if (hi != null && !e.generic) e.icon = hi;
            e.color = tintFrom(e.icon);
            Integer brand = Brand.tint(e.pkg);
            if (brand != null) e.color = brand;
            e.system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            e.launch = new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            all.put(e.key, e);
            pkgs.add(e.pkg);
        }
    }

    /** Average colour of the icon, darkened so white logos stay readable on top of it. */
    /** The app's launcher icon at xxxhdpi when the package ships one, else null (the normal icon is used). */
    static Drawable hiIcon(PackageManager pm, ActivityInfo ai) {
        try {
            int id = ai.icon != 0 ? ai.icon : ai.applicationInfo.icon;
            if (id == 0) return null;
            Resources res = pm.getResourcesForApplication(ai.applicationInfo);
            Drawable d = res.getDrawableForDensity(id, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
            // Some apps ship 512 px icons (1 MB each): keep them at 192 px, which is all a card can show.
            if (d instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap b = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                if (b != null && b.getWidth() > 192) {
                    Bitmap small = Bitmap.createScaledBitmap(b, 192, 192, true);
                    return new BitmapDrawable(res, small);
                }
            }
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    int tintFrom(Drawable d) {
        if (d == null) return 0xFF2C2C2E;
        Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        Rect old = d.copyBounds();
        d.setBounds(0, 0, 16, 16);
        d.draw(new Canvas(b));
        d.setBounds(old);
        long r = 0, g = 0, bl = 0, n = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p);
                g += Color.green(p);
                bl += Color.blue(p);
                n++;
            }
        }
        b.recycle();
        if (n == 0) return 0xFF2C2C2E;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
        hsv[1] = Math.min(hsv[1], 0.8f);
        hsv[2] = Math.min(hsv[2], 0.5f);
        return Color.HSVToColor(hsv);
    }

    void reload(String focusKey) {
        // An app installed or updated in the background (Play Store) mustn't pull focus out of the
        // carousel the person is browsing.
        boolean onShelf = shelfView.isFocused();
        loadApps();
        if (folderMode && hiddenApps.isEmpty()) folderMode = false;
        buildGrid();
        shelfEntry = null;
        if (onShelf) {
            focusedIndex = Math.min(focusedIndex, Math.max(0, shown.size() - 1));
            return;
        }
        int i = focusKey == null ? -1 : indexOfKey(focusKey);
        focusIndex(i >= 0 ? i : Math.min(focusedIndex, shown.size() - 1));
    }

    void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : apps) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.key);
        }
        prefs.edit().putString(PREF_ORDER, sb.toString()).apply();
    }

    void setHidden(AppEntry a, boolean hide) {
        Set<String> hidden = new HashSet<>(prefs.getStringSet(PREF_HIDDEN, Collections.emptySet()));
        if (hide) hidden.add(a.key);
        else hidden.remove(a.key);
        prefs.edit().putStringSet(PREF_HIDDEN, hidden).apply();
        int i = focusedIndex;
        loadApps();
        if (folderMode && hiddenApps.isEmpty()) folderMode = false;
        buildGrid();
        shelfEntry = null;
        focusIndex(Math.min(i, shown.size() - 1));
        toast(hide ? a.label + " moved to More Apps" : a.label + " is back on Home");
    }

    void registerPackageReceiver() {
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                handler.removeCallbacks(reloadRun);
                handler.postDelayed(reloadRun, 600);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(pkgReceiver, f);
        }
    }

    // ---------------------------------------------------------------- options

    void showOptions(int index) {
        AppEntry a = shown.get(index);
        View tile = tiles.get(index);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Open");
        actions.add(() -> launchAnimated(tile, a));
        if (!isSettings(a) && !INPUTS_KEY.equals(a.key)) {
            String[] order = {"auto", "tile", "mono", "banner"};
            String[] names = {"Auto", "Icon", "Monogram", "Banner"};
            String cur = prefs.getString("style_" + a.pkg, "auto");
            int idx = Math.max(0, java.util.Arrays.asList(order).indexOf(cur));
            labels.add(It.f("Card style: %s", It.t(names[idx])));
            actions.add(() -> {
                prefs.edit().putString("style_" + a.pkg, order[(idx + 1) % order.length]).apply();
                reload(a.key);
            });
        }
        if (folderMode) {
            labels.add("Show on Home Screen");
            actions.add(() -> setHidden(a, false));
        } else {
            labels.add("Move");
            actions.add(() -> startMove(index));
            if (!isSettings(a)) {
                labels.add("Hide");
                actions.add(() -> setHidden(a, true));
            }
        }
        if (!isSettings(a) && !INPUTS_KEY.equals(a.key)) {
            labels.add("App Info");
            actions.add(() -> startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg))));
            if (!a.system) {
                labels.add("Uninstall");
                actions.add(() -> startSafe(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))));
            }
        }
        if (!folderMode) {
            labels.add("Reset Layout");
            actions.add(() -> {
                prefs.edit().remove(PREF_ORDER).apply();
                reload(a.key);
            });
        }
        showMenu(tile, a.label, labels, actions, labels.indexOf("Uninstall"));
    }

    // ---------------------------------------------------------------- Control Center

    /**
     * tvOS 26 Control Center (per Apple's reference): glass tiles under the status row \u2014
     * a square Power tile, Wi-Fi and Do Not Disturb capsules, Audio and Sleep Timer capsules,
     * and a row of round shortcuts. Geometry in design units: 141-wide columns, 13 gaps.
     */
    void showControlCenter() {
        RecentAppsOverlay.dismiss();
        ControlPanelOverlay.dismiss();
        dropPicker();
        stopSway(null);
        popoverReturn = ccButton;
        FrameLayout layer = new FrameLayout(this);
        layer.setClickable(true);
        layer.setClipChildren(false);
        addBlurredBackdrop(layer, 0x8C000000);

        List<View> cells = new ArrayList<>();
        FrameLayout panel = ControlPanelOverlay.buildControlPanelView(
                this, screenW, screenH, unit, glassSource, cells,
                () -> {
                    closeProfilePicker();
                    ControlPanelOverlay.openInputs(this, () -> showInputs(popoverReturn != null ? popoverReturn : ccButton));
                },
                null,
                () -> {
                    closeProfilePicker();
                    returningFromSettings = true;
                    startActivity(new Intent(this, SettingsActivity.class));
                },
                () -> {
                    closeProfilePicker();
                    showSearch(null);
                });

        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        plp.gravity = Gravity.TOP | Gravity.END;
        plp.topMargin = dp(58);
        plp.rightMargin = dockOuter;
        layer.addView(panel, plp);

        root.addView(layer, match());
        pickerLayer = layer;
        pickerCells = cells;
        layer.setAlpha(0f);
        panel.setTranslationY(-Motion.dist(dp(10)));
        layer.animate().alpha(1f).setDuration(Motion.d(200)).start();
        panel.animate().translationY(0).setDuration(Motion.d(260)).setInterpolator(EASE).start();
        Motion.cascade(cells, -dp(8));  // tiles drop in one after another, like tvOS
        if (!cells.isEmpty()) cells.get(0).requestFocus();
    }

    interface Status {
        String get();
    }

    /**
     * One Control Center tile: glass with glyph (+ title / live status). Square tiles stack the
     * glyph over the title; capsules put the glyph left of two lines; circles are glyph-only.
     * Focus: the glass lights up and the tile lifts.
     */
    View ccTile(FrameLayout panel, int x, int y, int w, int h, String glyph, String title, Status status,
                boolean square, View.OnClickListener click) {
        boolean circle = title == null;
        float radius = circle ? h / 2f : square ? dp(26) : h / 2f;
        LiquidGlass glass = new LiquidGlass(radius, 0x24FFFFFF, glassSource, screenW, screenH, unit).adaptive().darker(0x59);
        glass.lens = dp(7);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(square ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(glass);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(circle ? 0 : dp(13), 0, circle ? 0 : dp(8), 0);
        int gs = square ? dp(54) : circle ? dp(34) : dp(32);
        ImageView gi = new ImageView(this);
        gi.setImageBitmap(Glyphs.get(glyph, gs, glyph.equals("wifi") ? 0xFF0A84FF : Color.WHITE));
        t.addView(gi, new LinearLayout.LayoutParams(gs, gs));
        TextView sub = null;
        TextView[] titleRef = {null};
        if (!circle) {
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setGravity(square ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            TextView tt = text(square ? 15.5f : 15f, Ui.font(this, "semibold"), 1f);
            titleRef[0] = tt;
            tt.setText(title);
            tt.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
            tt.setMaxLines(2);
            texts.addView(tt);
            if (status != null) {
                sub = text(13f, Ui.font(this, "regular"), 0.85f);
                sub.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
                sub.setText(status.get());
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(sub);
            }
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(square ? ViewGroup.LayoutParams.WRAP_CONTENT : 0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, square ? 0 : 1f);
            if (square) tlp.topMargin = dp(12);
            else tlp.leftMargin = dp(8);
            t.addView(texts, tlp);
        }
        TextView subRef = sub;
        // Focused (tvOS): the tile turns solid white with a dark glyph and text.
        GradientDrawable white = new GradientDrawable();
        white.setCornerRadius(radius);
        white.setColor(0xF7FFFFFF);
        int glyphColor = glyph.equals("wifi") ? 0xFF0A84FF : Color.WHITE;
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(has ? white : glass);
            gi.setImageBitmap(Glyphs.get(glyph, gs, has ? (glyph.equals("wifi") ? 0xFF0A84FF : 0xFF111114) : glyphColor));
            TextView tt = titleRef[0];
            if (tt != null) {
                tt.setTextColor(has ? 0xFF111114 : Color.WHITE);
                tt.setShadowLayer(has ? 0 : dp(4), 0, has ? 0 : dp(1), 0x99000000);
            }
            if (subRef != null) {
                subRef.setTextColor(has ? 0xB3111114 : Color.WHITE);
                subRef.setShadowLayer(has ? 0 : dp(4), 0, has ? 0 : dp(1), 0x99000000);
            }
            if (memBarFill != null && memBarFill.getParent() != null && v == ((View) memBarFill.getParent()).getParent().getParent()) {
                ((GradientDrawable) ((View) memBarFill.getParent()).getBackground()).setColor(has ? 0x33111114 : 0x40FFFFFF);
                ((GradientDrawable) memBarFill.getBackground()).setColor(has ? 0xFF111114 : (memoryUsed() > 0.85f ? 0xFFFF9F0A : Color.WHITE));
            }
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? dp(10) : 0)
                    .setDuration(Motion.d(200)).setInterpolator(LIFT).start();
        });
        t.setOnClickListener(v -> {
            click.onClick(v);
            if (subRef != null && status != null) subRef.setText(status.get());
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        panel.addView(t, lp);
        return t;
    }

    // ---------------------------------------------------------------- memory

    float memoryUsed() {
        android.app.ActivityManager am = getSystemService(android.app.ActivityManager.class);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem > 0 ? 1f - mi.availMem / (float) mi.totalMem : 0f;
    }

    String memoryState() {
        return It.f("%d%% used  \u00b7  Clean", Math.round(memoryUsed() * 100));
    }

    View memBarFill;

    /** A thin usage bar under the Memory tile's text. */
    void addMemoryBar(View tile) {
        if (!(tile instanceof LinearLayout) || ((LinearLayout) tile).getChildCount() < 2) return;
        View texts = ((LinearLayout) tile).getChildAt(1);
        if (!(texts instanceof LinearLayout)) return;
        FrameLayout track = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable();
        tb.setCornerRadius(dp(2));
        tb.setColor(0x40FFFFFF);
        track.setBackground(tb);
        memBarFill = new View(this);
        GradientDrawable fb = new GradientDrawable();
        fb.setCornerRadius(dp(2));
        fb.setColor(Color.WHITE);
        memBarFill.setBackground(fb);
        track.addView(memBarFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(4));
        lp.topMargin = dp(4);
        ((LinearLayout) texts).addView(track, lp);
        track.post(this::updateMemoryBar);
    }

    void updateMemoryBar() {
        if (memBarFill == null || memBarFill.getParent() == null) return;
        View track = (View) memBarFill.getParent();
        float used = memoryUsed();
        ViewGroup.LayoutParams lp = memBarFill.getLayoutParams();
        lp.width = Math.round(track.getWidth() * used);
        memBarFill.setLayoutParams(lp);
        ((GradientDrawable) memBarFill.getBackground()).setColor(used > 0.85f ? 0xFFFF9F0A : Color.WHITE);
    }

    /** Apps opened from Home recently, newest first (Android hides other apps' processes). */
    void noteLaunched(String pkg) {
        if (pkg == null || pkg.equals(getPackageName())) return;
        List<String> l = new ArrayList<>(Arrays.asList(getSharedPreferences("clean", MODE_PRIVATE).getString("recent", "").split(",")));
        l.remove(pkg);
        l.remove("");
        l.add(0, pkg);
        while (l.size() > 12) l.remove(l.size() - 1);
        getSharedPreferences("clean", MODE_PRIVATE).edit().putString("recent", String.join(",", l)).apply();
    }

    List<String> recentApps() {
        List<String> l = new ArrayList<>();
        for (String p : getSharedPreferences("clean", MODE_PRIVATE).getString("recent", "").split(",")) {
            if (!p.isEmpty() && Suggestions.installed(this, p)) l.add(p);
        }
        return l;
    }

    /**
     * Clean: close the apps you've opened since the last clean. With Lume's accessibility switch on,
     * it presses Force stop on each app's info page (what you'd do by hand); otherwise Android is
     * asked to drop their background processes. The memory freed is measured.
     */
    void cleanMemory() {
        android.app.ActivityManager am = getSystemService(android.app.ActivityManager.class);
        List<String> apps = recentApps();
        if (apps.isEmpty()) {
            toast("No apps to close");
            return;
        }
        long before = availMem(am);
        for (String pkg : apps) {
            try {
                am.killBackgroundProcesses(pkg);
            } catch (Exception ignored) {
                // not allowed for this one
            }
        }
        Runnable done = () -> {
            getSharedPreferences("clean", MODE_PRIVATE).edit().remove("recent").apply();
            long freed = Math.max(0, availMem(am) - before) / (1024 * 1024);
            updateMemoryBar();
            toast("Closed " + apps.size() + (apps.size() == 1 ? " app" : " apps") + (freed > 0 ? " \u00b7 freed " + freed + " MB" : ""));
        };
        if (PowerService.instance != null) {
            closeProfilePicker();
            PowerService.forceStop(this, apps, () -> handler.postDelayed(done, 600));
        } else {
            handler.postDelayed(done, 800);
        }
    }

    long availMem(android.app.ActivityManager am) {
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    String wifiName() {
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return "Not Connected";
            if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (!nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return "Connected";
            android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            String ssid = info.getSSID();
            // The band and how good the link is: the usual reason for stutter, said before it happens.
            String link = "";
            if (info.getFrequency() > 0 && info.getRssi() > -127) {
                int rssi = info.getRssi();
                link = (info.getFrequency() < 3000 ? "2.4 GHz" : info.getFrequency() < 5900 ? "5 GHz" : "6 GHz") + " \u00b7 "
                        + It.t(rssi >= -60 ? "Excellent" : rssi >= -72 ? "Good" : "Weak");
            }
            boolean named = ssid != null && !ssid.contains("unknown ssid");
            if (!named) return link.isEmpty() ? "Connected" : link;
            return ssid.replace("\"", "") + (link.isEmpty() ? "" : "  \u00b7  " + link);
        } catch (Exception e) {
            return "Connected";
        }
    }

    String dndState() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        return nm.getCurrentInterruptionFilter() > android.app.NotificationManager.INTERRUPTION_FILTER_ALL ? "On" : "Off";
    }

    void toggleDnd() {
        MediaWatchService svc = MediaWatchService.instance;
        if (svc == null) {
            Perms.requestListener(this);
            return;
        }
        boolean on = dndState().equals("On");
        svc.requestInterruptionFilter(on ? NotificationListenerService.INTERRUPTION_FILTER_ALL
                : NotificationListenerService.INTERRUPTION_FILTER_PRIORITY);
    }

    String audioOutput() {
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return d.getProductName() + "";
                case android.media.AudioDeviceInfo.TYPE_HDMI_ARC:
                case android.media.AudioDeviceInfo.TYPE_HDMI_EARC: return "HDMI ARC";
                default: break;
            }
        }
        return "TV Speakers";
    }

    String timerState() {
        int left = SleepReceiver.timerLeft(this);
        return left > 0 ? left + " min left" : "Off";
    }

    /** Off \u2192 15 \u2192 30 \u2192 60 \u2192 90 \u2192 Off. */
    void cycleSleepTimer() {
        if (!SleepReceiver.canSleep(this)) {
            Perms.requestAdmin(this);
            return;
        }
        int left = SleepReceiver.timerLeft(this);
        int[] steps = {15, 30, 60, 90};
        int next = steps[0];
        if (left > 0) {
            next = 0;
            for (int st : steps) if (st > left + 1) { next = st; break; }
        }
        SleepReceiver.setTimer(this, next);
    }


    // ---------------------------------------------------------------- inputs

    /** Input picker under the status row: Control Center capsules, two columns. */
    void showInputs(View from) {
        List<Inputs.Input> list = Inputs.list(this);
        if (list.isEmpty()) {
            toast("This TV has no inputs");
            return;
        }
        dropPicker();
        stopSway(null);
        popoverReturn = from;
        FrameLayout layer = new FrameLayout(this);
        layer.setClickable(true);
        layer.setClipChildren(false);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0x99000000, 0x40000000, 0x00000000}));
        layer.addView(shade, new FrameLayout.LayoutParams(screenW * 3 / 5, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        int col = dp(141), gap = dp(13), cap = (col - gap) / 2;
        FrameLayout panel = new FrameLayout(this);
        panel.setClipChildren(false);
        List<View> cells = new ArrayList<>();
        TextView title = text(13, Ui.font(this, "semibold"), 0.75f);
        title.setText("Inputs");
        panel.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(20)));
        int top = dp(26);
        for (int i = 0; i < list.size(); i++) {
            Inputs.Input in = list.get(i);
            int x = (i % 2) * (col + gap), y = top + (i / 2) * (cap + gap);
            cells.add(ccTile(panel, x, y, col, cap, Inputs.glyph(in), in.label,
                    in.disconnected ? () -> "Not connected" : null, false, v -> {
                        closeProfilePicker();
                        if (!Inputs.open(this, in)) toast("Can't switch to " + in.label);
                    }));
        }
        int rows = (list.size() + 1) / 2;
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(2 * col + gap, top + rows * cap + (rows - 1) * gap,
                Gravity.TOP | Gravity.END);
        plp.topMargin = dp(72);
        plp.rightMargin = dockOuter;
        layer.addView(panel, plp);

        root.addView(layer, match());
        pickerLayer = layer;
        pickerCells = cells;
        layer.setAlpha(0f);
        panel.setTranslationY(-Motion.dist(dp(10)));
        layer.animate().alpha(1f).setDuration(Motion.d(200)).start();
        panel.animate().translationY(0).setDuration(Motion.d(260)).setInterpolator(EASE).start();
        Motion.cascade(cells, -dp(8));  // tiles drop in one after another, like tvOS
        cells.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- internet status

    /** Follows the default network; Android marks it "validated" once the internet is reachable. */
    void watchNetwork() {
        updateNet();
        if (netCallback != null) return;
        netCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(android.net.Network n) { handler.post(MainActivity.this::updateNet); }
            @Override public void onLost(android.net.Network n) { handler.post(MainActivity.this::updateNet); }
            @Override public void onCapabilitiesChanged(android.net.Network n, android.net.NetworkCapabilities nc) {
                handler.post(MainActivity.this::updateNet);
            }
        };
        try {
            getSystemService(android.net.ConnectivityManager.class).registerDefaultNetworkCallback(netCallback);
        } catch (Exception e) {
            netCallback = null;
        }
    }

    int netState = -1, netBars = -1;
    boolean netEthernet;

    void updateNet() {
        if (netIcon == null) return;
        android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        int state = nc == null ? 0
                : nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 2 : 1;
        boolean eth = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
        int bars = 3;
        if (nc != null && !eth) {
            try {
                android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
                int level = android.net.wifi.WifiManager.calculateSignalLevel(wm.getConnectionInfo().getRssi(), 4);
                bars = level <= 1 ? 1 : level == 2 ? 2 : 3;
            } catch (Exception ignored) {
                // keep full bars
            }
        }
        boolean back = netState == 0 || netState == 1;
        if (state == netState && eth == netEthernet && bars == netBars) return;
        netState = state;
        netEthernet = eth;
        netBars = bars;
        netIcon.setImageBitmap(netGlyph(state, eth, bars, dp(20)));
        netIcon.setContentDescription(state == 2 ? "Online" : state == 1 ? "No internet" : "Offline");
        // Back online: refresh what needs the internet.
        if (state == 2) AirPlayGuard.check(this, back);
        if (state == 2 && back) {
            Weather.refresh(this, true, this::updateWeather);
            loadHero();
        }
    }

    /**
     * Apple-style network symbol, white only: Wi-Fi as a dot-wedge and two bands lit by signal
     * strength (unlit at 30%), or Ethernet; "!" beside it without internet (wifi.exclamationmark)
     * and a slash through dimmed bands with no network (wifi.slash).
     */
    Bitmap netGlyph(int state, boolean eth, int bars, int s) {
        int w = Math.round(s * 1.2f);
        Bitmap b = Bitmap.createBitmap(w, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int on = Color.WHITE, off = 0x4DFFFFFF;
        if (eth && state != 0) drawEthernet(c, s, state == 2 ? on : off);
        else drawWifi(c, s, state == 2 ? bars : 0, on, off);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(on);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (state == 1) {  // exclamation mark to the right
            p.setStrokeWidth(s * 0.13f);
            float x = s * 1.08f;
            c.drawLine(x, s * 0.2f, x, s * 0.6f, p);
            c.drawCircle(x, s * 0.82f, s * 0.075f, p);
        } else if (state == 0) {  // slash with a clear gap around it
            Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
            cut.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            cut.setStrokeCap(Paint.Cap.ROUND);
            cut.setStrokeWidth(s * 0.24f);
            c.drawLine(s * 0.12f, s * 0.1f, s * 0.9f, s * 0.92f, cut);
            p.setStrokeWidth(s * 0.1f);
            c.drawLine(s * 0.12f, s * 0.1f, s * 0.9f, s * 0.92f, p);
        }
        // Soft shadow behind everything, like the time's.
        Bitmap out = Bitmap.createBitmap(w, s, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColorFilter(new android.graphics.PorterDuffColorFilter(0x40000000, android.graphics.PorterDuff.Mode.SRC_IN));
        oc.drawBitmap(b, 0, s * 0.05f, sh);
        oc.drawBitmap(b, 0, 0, null);
        return out;
    }

    /** SF-style Wi-Fi: a wedge and two equal bands fanning 90 degrees from a point near the bottom. */
    void drawWifi(Canvas c, float s, int lit, int on, int off) {
        float cx = s * 0.5f, cy = s * 0.88f, rw = s * 0.24f, t = s * 0.15f, gap = s * 0.075f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(s * 0.05f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        RectF r = new RectF();
        for (int i = 0; i < 3; i++) {
            android.graphics.Path path = new android.graphics.Path();
            if (i == 0) {
                path.moveTo(cx, cy);
                r.set(cx - rw, cy - rw, cx + rw, cy + rw);
                path.arcTo(r, -135, 90);
            } else {
                float inner = rw + gap + (i - 1) * (t + gap), outer = inner + t;
                r.set(cx - outer, cy - outer, cx + outer, cy + outer);
                path.arcTo(r, -135, 90, true);
                r.set(cx - inner, cy - inner, cx + inner, cy + inner);
                path.arcTo(r, -45, -90);
            }
            path.close();
            // Unlit bands: drawn opaque on a faded layer so fill and rounded edge don't overlap.
            boolean dim = i >= lit;
            if (dim) c.saveLayerAlpha(0, 0, c.getWidth(), c.getHeight(), Color.alpha(off));
            p.setColor(dim ? (off | 0xFF000000) : on);
            c.drawPath(path, p);
            if (dim) c.restore();
        }
    }

    /** Ethernet: three solid nodes joined by lines, drawn bolder to match the Wi-Fi weight. */
    void drawEthernet(Canvas c, float s, int col) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(col);
        p.setStrokeWidth(s * 0.09f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float m = s * 0.5f;
        c.drawLine(m, s * 0.36f, m, s * 0.52f, p);
        c.drawLine(s * 0.24f, s * 0.52f, s * 0.76f, s * 0.52f, p);
        c.drawLine(s * 0.24f, s * 0.52f, s * 0.24f, s * 0.64f, p);
        c.drawLine(s * 0.76f, s * 0.52f, s * 0.76f, s * 0.64f, p);
        RectF r = new RectF(m - s * 0.15f, s * 0.08f, m + s * 0.15f, s * 0.36f);
        c.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
        r.set(s * 0.09f, s * 0.62f, s * 0.39f, s * 0.9f);
        c.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
        r.set(s * 0.61f, s * 0.62f, s * 0.91f, s * 0.9f);
        c.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
    }

    /** Android TV's system click on D-pad moves, select and Back (Settings -> Home -> Navigation sounds). */
    static void navSound(Context c, KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return;
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                Clicks.move(c, ev.getKeyCode());
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (ev.getRepeatCount() == 0) Clicks.select(c);
                break;
            case KeyEvent.KEYCODE_BACK:
                if (ev.getRepeatCount() == 0) Clicks.back(c);
                break;
            default:
                break;
        }
    }

    /** Refresh button: fetch fresh recommendations and news, reload Continue Watching and weather. */
    void refreshAll() {
        refreshButton.animate().cancel();
        refreshButton.setRotation(0);
        refreshButton.animate().rotation(360).setDuration(Motion.d(700)).setInterpolator(EASE).start();
        Suggestions.expire(this);  // keeps showing the current items until fresh ones arrive
        AppRows.invalidate();
        lastCwSig = "";
        loadHero();
        Weather.refresh(this, true, this::updateWeather);
        toast("Refreshing");
    }

    // ---------------------------------------------------------------- idle full-screen carousel

    boolean idle, swallowUp;
    float idleScrollY, idleInfoY, idleDotsY;
    boolean idleOverviewShown;

    /** Seconds without input before the dock slides away (Settings -> Home); 0 = never. */
    int idleSeconds() {
        int sec = Profiles.homePrefs(this).getInt("idle_sec", 20);
        // Lume carousel chosen as the screensaver: it always takes over when idle.
        if (sec == 0 && !getSharedPreferences("screensaver", MODE_PRIVATE).getBoolean("allow", true)) sec = 20;
        return sec;
    }

    final Runnable idleRunnable = this::enterIdle;

    void resetIdle() {
        handler.removeCallbacks(idleRunnable);
        int sec = idleSeconds();
        if (sec > 0 && resumed) handler.postDelayed(idleRunnable, sec * 1000L);
    }

    /**
     * No input for a while: the dock and grid slide down off screen, the weather card and tab bar
     * fade, and the carousel keeps playing full screen with its title and summary at the bottom.
     */
    /** Ambient Mode from the Control Panel: our own gallery, shown as the Home idle mode. */
    static void triggerBuiltInAmbient(Context c) {
        MainActivity a = activeInstance;
        if (a != null && a.resumed) { a.runOnUiThread(() -> { if (!a.idle) a.enterIdle(); }); return; }
        c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("ambient", true));
    }

    void enterIdle() {
        if (idle || !resumed || !heroShown || heroItems.isEmpty() || continueMode() || folderMode
                || launchOverlay != null || pickerLayer != null || menuLayer != null || moveIndex >= 0) {
            resetIdle();
            return;
        }
        idle = true;
        idleScrollY = scroll.getTranslationY();
        idleInfoY = heroInfo.getTranslationY();
        idleDotsY = heroDots.getTranslationY();
        idleOverviewShown = heroOverview.getVisibility() == View.VISIBLE;
        scroll.animate().cancel();
        scroll.animate().withLayer().translationY(screenH).alpha(0f).setDuration(Motion.d(900)).setInterpolator(EASE)
                .setUpdateListener(null).start();
        heroInfo.animate().withLayer().translationY(0).setDuration(Motion.d(900)).setInterpolator(EASE).start();
        heroDots.animate().withLayer().translationY(dp(4)).setDuration(Motion.d(900)).setInterpolator(EASE).start();
        weatherCard.animate().alpha(0f).setDuration(Motion.d(500)).start();
        switcher.animate().alpha(0f).setDuration(Motion.d(500)).start();
        heroButton.animate().alpha(0f).setDuration(Motion.d(300)).start();
        heroOverview.animate().cancel();
        heroOverview.setVisibility(heroOverview.getText().length() > 0 ? View.VISIBLE : View.GONE);
        heroOverview.animate().alpha(0.85f).setDuration(Motion.d(600)).start();
        handler.removeCallbacks(heroAdvance);
        scheduleAdvance();
    }

    void exitIdle() {
        if (!idle) return;
        idle = false;
        scroll.animate().cancel();
        scroll.animate().withLayer().translationY(idleScrollY).alpha(1f).setDuration(Motion.d(450)).setInterpolator(EASE)
                .setUpdateListener(null).withEndAction(this::invalidateGlass).start();
        heroInfo.animate().withLayer().translationY(idleInfoY).setDuration(Motion.d(450)).setInterpolator(EASE).start();
        heroDots.animate().withLayer().translationY(idleDotsY).setDuration(Motion.d(450)).setInterpolator(EASE).start();
        weatherCard.animate().alpha(weatherAlpha()).setDuration(Motion.d(350)).start();
        switcher.animate().alpha(folderMode ? 0f : clockAlpha()).setDuration(Motion.d(350)).start();
        if (shelfView.isFocused()) {
            heroButton.animate().alpha(1f).setDuration(Motion.d(300)).start();
            handler.removeCallbacks(heroAdvance);  // browsing by hand: no auto-advance
        }
        if (!idleOverviewShown && !heroIsNews()) {
            heroOverview.animate().alpha(0f).setDuration(Motion.d(200))
                    .withEndAction(() -> heroOverview.setVisibility(View.GONE)).start();
        }
        resetIdle();
    }

    // ---------------------------------------------------------------- weather

    /**
     * Once a minute: fetch when the forecast is due (every 15 minutes by default), move "Now" on,
     * and pick up Wi-Fi signal changes.
     */
    int tickCount;
    final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            Weather.refresh(MainActivity.this, false, MainActivity.this::updateWeather);
            updateWeather();
            // Carousel content rotates even if Home stays open for hours (fetches only when stale).
            if (++tickCount % 5 == 0 && !shelfView.hasFocus()) loadHero();
            if (tickCount % 15 == 0) AirPlayGuard.check(MainActivity.this, false);
            updateNet();
            handler.postDelayed(this, 60 * 1000L);
        }
    };

    boolean weatherShown() {
        return weatherCard != null && weatherCard.getVisibility() == View.VISIBLE && weatherAlpha() > 0.5f;
    }

    boolean weatherOpen;

    float weatherAlpha() {
        float a = folderMode || weatherOpen ? 0f : clockAlpha();
        syncScene(a);
        return a;
    }

    /** The Scene (greeting, date, big clock) fades out exactly when the corner weather would: with the app grid, folders, the forecast. */
    void syncScene(float a) {
        if (scene == null) return;
        if (a > 0.02f) scene.setVisibility(View.VISIBLE);
        scene.animate().alpha(a).setDuration(Motion.d(250)).withEndAction(() -> {
            if (scene != null && scene.getAlpha() < 0.02f) scene.setVisibility(View.INVISIBLE);  // not focusable while hidden
        }).start();
    }

    /** Shows the cached forecast (hidden when weather is off or nothing has loaded yet). */
    void updateWeather() {
        if (weatherCard == null) return;
        Weather.Data d = Weather.enabled(this) ? Weather.cached(this) : null;
        boolean show = d != null && scene == null;  // in the Scene look the weather lives in the line under the greeting
        if (scene != null) scene.refresh();
        if (!show && weatherCard.isFocused()) focusTop();
        weatherCard.setVisibility(show ? View.VISIBLE : View.GONE);
        weatherCard.setData(d);
        if (show && !weatherCard.isFocused() && launchOverlay == null) weatherCard.setAlpha(weatherAlpha());
    }

    /** The full forecast: a glass panel opening from the weather card in the top-left corner. */
    void showWeather() {
        Weather.Data d = Weather.cached(this);
        if (d == null) return;
        dropPicker();
        stopSway(null);
        popoverReturn = weatherCard;
        if (scene != null) scene.setVisibility(View.INVISIBLE);  // the forecast panel covers the greeting and clock
        FrameLayout layer = new FrameLayout(this);
        layer.setClickable(true);
        layer.setClipChildren(false);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xA6000000, 0x4D000000, 0x00000000}));
        layer.addView(shade, new FrameLayout.LayoutParams(screenW * 3 / 5, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        WeatherView panel = new WeatherView(this, true, unit);
        LiquidGlass glass = new LiquidGlass(dp(26), 0x33000000, glassSource, screenW, screenH, unit).adaptive();
        glass.lens = dp(7);
        panel.setBackground(glass);
        panel.setFocusable(true);
        panel.setData(d);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(WeatherView.FULL_W), dp(WeatherView.FULL_H),
                Gravity.TOP | Gravity.START);
        plp.topMargin = dp(24);
        plp.leftMargin = dockOuter;
        layer.addView(panel, plp);

        root.addView(layer, match());
        pickerLayer = layer;
        pickerCells = new ArrayList<>();
        pickerCells.add(panel);
        weatherOpen = true;
        weatherCard.animate().alpha(0f).setDuration(Motion.d(150)).start();
        layer.setAlpha(0f);
        panel.setTranslationY(-dp(10));
        layer.animate().alpha(1f).setDuration(Motion.d(200)).start();
        panel.animate().translationY(0).setDuration(Motion.d(260)).setInterpolator(EASE).start();
        panel.requestFocus();
        Weather.refresh(this, false, () -> {
            Weather.Data nd = Weather.cached(this);
            if (nd != null && panel.isAttachedToWindow()) panel.setData(nd);
            updateWeather();
        });
    }

    // ---------------------------------------------------------------- profiles

    List<View> pickerCells = new ArrayList<>();

    /**
     * tvOS 26 profile switcher (per Apple's UI): a popover under the top-right status area with a
     * two-column grid of round avatars, names in soft grey, a green check on the current profile,
     * and a glass "+" Add User. Content stays visible behind it.
     */
    int profileAvatarSize;

    /**
     * The whole Home Screen (artwork, dock, cards, status row) blurred and dimmed behind an overlay:
     * a quarter-size snapshot taken once when the overlay opens, then blurred off the GPU path.
     */
    void addBlurredBackdrop(FrameLayout layer, int dim) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        try {
            int w = Math.max(1, screenW / 4), h = Math.max(1, screenH / 4);
            Bitmap snap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(snap);
            c.scale(w / (float) screenW, h / (float) screenH);
            root.draw(c);
            Bitmap blurred = Ui.blur(snap, w / 2, h / 2, 4, 3);
            snap.recycle();
            if (blurred != null) iv.setImageBitmap(blurred);
        } catch (Throwable e) {
            Bitmap bg = glassSource.get();
            if (bg != null) iv.setImageBitmap(bg);
        }
        layer.addView(iv, match());
        View v = new View(this);
        v.setBackgroundColor(dim);
        layer.addView(v, match());
        layer.addView(UiKit.grain(this), match());  // after the dim: breaks its 8-bit banding (still screens only)
    }

    /**
     * Full-screen profile switcher (Apple TV "Who's Watching?"): the Home Screen blurred and dimmed
     * behind, a centred title and a centred row of large avatars with a green check on the current
     * one, plus Add User.
     */
    void showProfilePicker(boolean onWake) {
        dropPicker();
        popoverReturn = onWake ? null : avatarView;
        stopSway(null);
        FrameLayout layer = new FrameLayout(this);
        layer.setClickable(true);
        layer.setClipChildren(false);
        addBlurredBackdrop(layer, 0xB3000000);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        TextView title = text(30, Ui.font(this, "display"), 1f);
        title.setText(onWake ? "Who\u2019s Watching?" : "Switch Profile");
        title.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(34);
        col.addView(title, tlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        profileAvatarSize = dp(120);
        List<Profiles.Profile> ps = Profiles.list(this);
        String cur = Profiles.current(this).id;
        List<View> cells = new ArrayList<>();
        for (Profiles.Profile p : ps) {
            View cell = profileCell(Profiles.avatar(this, p, profileAvatarSize), p.name, false, p.id.equals(cur));
            cell.setTag(p.id);
            cell.setOnClickListener(v -> {
                switchProfile(p.id);
                popoverReturn = null;
                closeProfilePicker();
            });
            // Long-press OK (like the Menu key): Change Emoji, Rename, Change Colour, Delete.
            cell.setOnLongClickListener(v -> {
                profileMenu(v);
                return true;
            });
            cells.add(cell);
        }
        if (Profiles.canAdd(this)) {
            View add = profileCell(null, "Add User", true, false);
            add.setOnClickListener(v -> askName("New Profile", "", name -> {
                if (Profiles.add(this, name) != null) showProfilePicker(false);
            }));
            cells.add(add);
        }
        // Each cell is as wide as its circle grown on focus plus room for the name, so they never overlap.
        for (View c : cells) {
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(profileAvatarSize + dp(44), ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.leftMargin = clp.rightMargin = dp(10);
            row.addView(c, clp);
        }
        profileAvatarSize = 0;
        col.addView(row);
        pickerCells = cells;
        layer.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        root.addView(layer, match());
        pickerLayer = layer;
        layer.setAlpha(0f);
        col.setScaleX(0.96f);
        col.setScaleY(0.96f);
        layer.animate().alpha(1f).setDuration(Motion.d(onWake ? 380 : 260)).start();
        col.animate().scaleX(1f).scaleY(1f).setDuration(Motion.d(onWake ? 420 : 300)).setInterpolator(EASE).start();
        Motion.cascade(cells, dp(24));  // avatars rise in one after another
        View focus = cells.isEmpty() ? null : cells.get(0);
        for (View c : cells) if (cur.equals(c.getTag())) focus = c;
        if (focus != null) {
            focus.requestFocus();
            // Rebuilt from a dialog (Add User / Rename): take focus once the dialog has gone.
            View f = focus;
            handler.postDelayed(() -> {
                if (pickerLayer == layer && !pickerCells.contains(getCurrentFocus())) f.requestFocus();
            }, 350);
        }
    }

    // ---------------------------------------------------------------- Search

    /** Opens search over the blurred Home Screen; q (adb hook, voice) starts with that text. */
    void showSearch(String q) {
        dropPicker();
        closeMenu();
        stopSway(null);
        popoverReturn = searchButton;
        search = new SearchPanel(this);
        pickerLayer = search.layer;
        pickerCells = new ArrayList<>();
        root.addView(search.layer, match());
        search.firstKey().requestFocus();  // only possible once the layer is attached
        SearchPanel opened = search;
        search.layer.setAlpha(0f);
        // Once search covers the screen, the Home layers under it stop drawing (the backdrop is a snapshot).
        search.layer.animate().alpha(1f).setDuration(Motion.d(200)).withEndAction(() -> {
            if (search != opened) return;
            hiddenUnderSearch.clear();
            for (int i = 0; i < root.getChildCount(); i++) {
                View c = root.getChildAt(i);
                if (c != opened.layer && c != launchOverlay && c.getVisibility() == View.VISIBLE) {
                    c.setVisibility(View.INVISIBLE);
                    hiddenUnderSearch.add(c);
                }
            }
        }).start();
        if (q != null && !q.isEmpty()) search.setQuery(q);
    }

    /** How long Back is held on Home to open Search. */
    static final long BACK_HOLD_MS = 1000;

    /** Back pressed on Home and held for BACK_HOLD_MS: open Search (the release is swallowed). */
    final Runnable backHold = () -> {
        backDown = null;
        swallowUp = true;
        showSearch(null);
    };
    KeyEvent backDown;
    boolean replayingBack;

    /**
     * The remote shortcut for Search: hold Back on Home. A short press must still behave exactly
     * as before, so Back is held back until it is released (or held long enough), then replayed.
     */
    boolean holdBackSearch(KeyEvent ev) {
        if (ev.getKeyCode() != KeyEvent.KEYCODE_BACK || replayingBack) return false;
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            if (ev.getRepeatCount() == 0) {
                backDown = new KeyEvent(ev);
                handler.removeCallbacks(backHold);
                handler.postDelayed(backHold, BACK_HOLD_MS);
            }
            return true;
        }
        handler.removeCallbacks(backHold);
        KeyEvent d = backDown;
        backDown = null;
        if (d == null) return true;
        replayingBack = true;
        try {
            dispatchKeyEvent(d);
            dispatchKeyEvent(ev);
        } finally {
            replayingBack = false;
        }
        return true;
    }

    /**
     * Keyboard shortcuts on Home (a USB/Bluetooth keyboard, not the remote): typing a letter or
     * digit opens Search with it typed; "/", Ctrl+F and Ctrl+K open it empty.
     */
    boolean keyboardSearch(KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN || ev.getRepeatCount() > 0) return false;
        android.view.InputDevice dev = ev.getDevice();
        if (dev == null || dev.getKeyboardType() != android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC) return false;
        int k = ev.getKeyCode();
        boolean shortcut = (ev.isCtrlPressed() && (k == KeyEvent.KEYCODE_F || k == KeyEvent.KEYCODE_K))
                || (k == KeyEvent.KEYCODE_SLASH && !ev.isCtrlPressed() && !ev.isAltPressed());
        int ch = ev.isCtrlPressed() || ev.isAltPressed() || ev.isMetaPressed() ? 0 : ev.getUnicodeChar();
        if (!shortcut && !Character.isLetterOrDigit(ch)) return false;
        swallowUp = true;  // the key's release must not reach the search screen
        showSearch(shortcut ? null : String.valueOf((char) ch).toLowerCase());
        return true;
    }

    void closeSearch() {
        if (search == null) return;
        FrameLayout l = search.layer;
        releaseSearch();
        if (pickerLayer == l) {
            pickerLayer = null;
            l.animate().alpha(0f).setDuration(Motion.d(200)).withEndAction(() -> root.removeView(l)).start();
        }
        if (popoverReturn != null && popoverReturn.isAttachedToWindow()) popoverReturn.requestFocus();
        else goTop();
        popoverReturn = null;
    }

    final List<View> hiddenUnderSearch = new ArrayList<>();

    void releaseSearch() {
        for (View c : hiddenUnderSearch) c.setVisibility(View.VISIBLE);
        hiddenUnderSearch.clear();
        if (search == null) return;
        search.release();
        search = null;
    }

    /** Removes whichever overlay is open (stopping search's work if it was search). */
    void dropPicker() {
        if (scene != null) scene.setVisibility(View.VISIBLE);
        if (pickerLayer == null) return;
        if (search != null && pickerLayer == search.layer) releaseSearch();
        root.removeView(pickerLayer);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == SearchPanel.REQ_VOICE && res == RESULT_OK && search != null) search.onVoice(data);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == SearchPanel.REQ_MIC && search != null) {
            search.onMicPermission(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
        }
        if (req == Perms.REQ_TV_LISTINGS) {
            AppRows.invalidate();
            lastCwSig = "";
            heroVersion = -1;
            loadHero();
        }
    }

    boolean statusFocused() {
        View f = getCurrentFocus();
        return f != null && (f == ccButton || f == avatarView || f == refreshButton || f == searchButton);
    }

    /** Directional focus inside a popover: nearest cell whose centre lies in that direction. */
    boolean pickerMove(int keyCode) {
        View cur = getCurrentFocus();
        if (!pickerCells.contains(cur)) {
            if (!pickerCells.isEmpty()) pickerCells.get(0).requestFocus();
            return true;
        }
        int[] a = new int[2];
        cur.getLocationOnScreen(a);
        float ax = a[0] + cur.getWidth() / 2f, ay = a[1] + cur.getHeight() / 2f;
        View best = null;
        float bestScore = Float.MAX_VALUE;
        for (View v : pickerCells) {
            if (v == cur) continue;
            int[] b = new int[2];
            v.getLocationOnScreen(b);
            float dx = b[0] + v.getWidth() / 2f - ax, dy = b[1] + v.getHeight() / 2f - ay;
            float main, cross;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: main = -dx; cross = dy; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: main = dx; cross = dy; break;
                case KeyEvent.KEYCODE_DPAD_UP: main = -dy; cross = dx; break;
                default: main = dy; cross = dx; break;
            }
            if (main <= dp(4)) continue;
            float score = main + Math.abs(cross) * 2.2f;
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        if (best != null) best.requestFocus();
        return true;
    }

    /** Avatar (or glass +) with the name below; the current profile gets a green check. */
    View profileCell(Bitmap avatar, String name, boolean isAdd, boolean current) {
        int big = profileAvatarSize;
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        int size = big > 0 ? big : dp(80);
        FrameLayout picBox = new FrameLayout(this);
        picBox.setClipChildren(false);
        View pic;
        if (isAdd) {
            TextView plus = text(34, Ui.font(this, "regular"), 0.9f);
            plus.setText("+");
            plus.setGravity(Gravity.CENTER);
            plus.setShadowLayer(0, 0, 0, 0);
            LiquidGlass g = new LiquidGlass(size / 2f, 0x1AFFFFFF, glassSource, screenW, screenH, unit).adaptive();
            g.lens = dp(7);
            plus.setBackground(g);
            pic = plus;
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(avatar);
            pic = iv;
            pic.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            pic.setElevation(dp(3));
        }
        picBox.addView(pic, new FrameLayout.LayoutParams(size, size));
        if (current) {
            int cs = dp(22);
            ImageView check = new ImageView(this);
            check.setImageBitmap(checkBadge(cs));
            check.setElevation(dp(5));
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cs, cs, Gravity.TOP | Gravity.END);
            clp.topMargin = -dp(2);
            clp.rightMargin = -dp(2);
            picBox.addView(check, clp);
        }
        cell.addView(picBox, new LinearLayout.LayoutParams(size, size));
        TextView label = text(big > 0 ? 15 : 12.5f, Ui.font(this, big > 0 ? "medium" : "regular"), 0.7f);
        label.setText(name);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(size + dp(16), ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        cell.addView(label, llp);
        cell.setOnFocusChangeListener((v, has) -> {
            picBox.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).translationZ(has ? dp(12) : 0)
                    .setDuration(Motion.d(220)).setInterpolator(LIFT).start();
            label.animate().alpha(has ? 1f : 0.7f).translationY(has ? dp(5) : 0).setDuration(Motion.d(200)).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        lp.rightMargin = dp(8);
        cell.setLayoutParams(lp);
        return cell;
    }

    /** Green circle with a white check (current profile). */
    Bitmap checkBadge(int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF34C759);
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.11f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(s * 0.28f, s * 0.52f);
        path.lineTo(s * 0.44f, s * 0.67f);
        path.lineTo(s * 0.73f, s * 0.35f);
        c.drawPath(path, p);
        return b;
    }

    View popoverReturn;

    void closeProfilePicker() {
        if (pickerLayer == null) return;
        FrameLayout l = pickerLayer;
        pickerLayer = null;
        l.animate().alpha(0f).setDuration(Motion.d(220)).withEndAction(() -> root.removeView(l)).start();
        weatherOpen = false;
        weatherCard.animate().alpha(weatherAlpha()).setDuration(Motion.d(220)).start();
        if (popoverReturn != null && popoverReturn.isAttachedToWindow()) popoverReturn.requestFocus();
        else goTop();
        popoverReturn = null;
    }

    /** Menu on a profile: Rename, Change Colour, Delete. */
    void profileMenu(View cell) {
        if (cell == null || !(cell.getTag() instanceof String)) return;
        String id = (String) cell.getTag();
        Profiles.Profile p = null;
        for (Profiles.Profile x : Profiles.list(this)) if (x.id.equals(id)) p = x;
        if (p == null) return;
        Profiles.Profile prof = p;
        List<String> l = new ArrayList<>();
        List<Runnable> act = new ArrayList<>();
        l.add("Rename");
        act.add(() -> askName("Rename Profile", prof.name, name -> {
            prof.name = name;
            Profiles.update(this, prof);
            refreshAvatar();
            showProfilePicker(false);
        }));
        l.add("Change Emoji");
        act.add(() -> {
            List<String> free = Profiles.freeEmojis(this, prof.id);
            String[] items = new String[free.size()];
            int cur = -1;
            for (int i = 0; i < free.size(); i++) {
                items[i] = free.get(i) + "   " + Profiles.emojiName(free.get(i));
                if (free.get(i).equals(prof.emoji)) cur = i;
            }
            Clicks.dialog(this)
                    .setTitle(It.t("Emoji"))
                    .setSingleChoiceItems(items, cur, (d, w) -> {
                        prof.emoji = free.get(w);
                        Profiles.update(this, prof);
                        refreshAvatar();
                        d.dismiss();
                        showProfilePicker(false);
                    }).show();
        });
        l.add("Change Colour");
        act.add(() -> Clicks.dialog(this)
                .setTitle(It.t("Colour"))
                .setSingleChoiceItems(Profiles.COLOR_NAMES, prof.color, (d, w) -> {
                    prof.color = w;
                    Profiles.update(this, prof);
                    refreshAvatar();
                    d.dismiss();
                    showProfilePicker(false);
                }).show());
        int del = -1;
        if (Profiles.list(this).size() > 1) {
            del = l.size();
            l.add("Delete");
            act.add(() -> Clicks.dialog(this)
                    .setTitle(It.t("Delete \u201C") + prof.name + "\u201D?")
                    .setMessage(It.t("Its layout and Continue Watching list will be removed."))
                    .setPositiveButton(It.t("Delete"), (d, w) -> {
                        boolean wasCurrent = Profiles.current(this).id.equals(prof.id);
                        Profiles.delete(this, prof.id);
                        if (wasCurrent) switchProfile(Profiles.current(this).id);
                        showProfilePicker(false);
                    })
                    .setNegativeButton(It.t("Cancel"), null).show());
        }
        showMenu(cell, prof.name, l, act, del);
    }

    interface NameResult {
        void ok(String name);
    }

    void askName(String title, String current, NameResult r) {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setText(current);
        in.setSelection(in.getText().length());
        FrameLayout box = new FrameLayout(this);
        int pad = dp(14);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        Clicks.dialog(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(It.t("Save"), (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) r.ok(n.length() > 20 ? n.substring(0, 20) : n);
                })
                .setNegativeButton(It.t("Cancel"), null)
                .show();
        in.requestFocus();
    }

    void refreshAvatar() {
        if (avatarView != null) avatarView.setImageBitmap(Profiles.avatar(this, Profiles.current(this), avatarSize));
    }

    /** Switch to a profile: its layout, background, top-shelf tab and Continue Watching. */
    void switchProfile(String id) {
        if (id.equals(Profiles.current(this).id) && prefs != null) {
            refreshAvatar();
            return;
        }
        Profiles.setCurrent(this, id);
        prefs = Profiles.homePrefs(this);
        refreshAvatar();
        wallStyle = prefs.getString("wallpaper", "galleria");
        setWallpaper(wallStyle);
        folderMode = false;
        moveIndex = -1;
        String mode = prefs.getString("shelf_mode", "suggest");
        shelfMode = mode;
        styleTabs();
        heroItems = new ArrayList<>();
        heroIndex = 0;
        heroVersion = -1;
        heroFront.setImageDrawable(null);
        heroBack.setImageDrawable(null);
        setHeroShown(false);
        loadApps();
        buildGrid();
        shelfEntry = null;
        focusIndex(0);
        Motion.cascade(tiles, dp(18));  // the new profile's apps arrive one after another
        loadHero();
    }

    // ---------------------------------------------------------------- tvOS context menu

    FrameLayout menuLayer;
    View menuReturnFocus;

    /**
     * tvOS-style context menu: a dark Liquid Glass panel beside the focused card, title on top,
     * rows below; the focused row becomes a white pill with dark text. Back closes it.
     */
    void showMenu(View anchor, String title, List<String> labels, List<Runnable> actions, int destructive) {
        closeMenu();
        menuReturnFocus = anchor;
        stopSway(null);
        menuLayer = new FrameLayout(this);
        menuLayer.setBackgroundColor(0x00000000);
        menuLayer.setClickable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(12), dp(8), dp(8));
        // "Regular" Liquid Glass: heavier dimming so the menu text stays legible over anything.
        LiquidGlass glass = new LiquidGlass(dp(20), 0xB81A1A20, glassSource, screenW, screenH, unit);
        glass.lens = dp(8);
        panel.setBackground(glass);
        panel.setElevation(dp(30));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        TextView head = text(11, Ui.font(this, "semibold"), 0.55f);
        head.setText(title);
        head.setShadowLayer(0, 0, 0, 0);
        head.setPadding(dp(12), 0, dp(12), dp(8));
        head.setSingleLine(true);
        head.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(head);

        List<View> rows = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            int idx = i;
            TextView row = text(14, Ui.font(this, "medium"), 1f);
            row.setText(labels.get(i));
            row.setShadowLayer(0, 0, 0, 0);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setFocusable(true);
            row.setClickable(true);
            int base = i == destructive ? 0xFFFF6B5E : Color.WHITE;
            row.setTextColor(base);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(12));
            pill.setColor(Color.TRANSPARENT);
            row.setBackground(pill);
            row.setOnFocusChangeListener((v, has) -> {
                pill.setColor(has ? 0xFFFFFFFF : Color.TRANSPARENT);
                row.setTextColor(has ? (idx == destructive ? 0xFFD7372A : 0xFF111114) : base);
                v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(Motion.d(160)).setInterpolator(LIFT).start();
            });
            row.setOnClickListener(v -> {
                closeMenu();
                actions.get(idx).run();
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            rlp.topMargin = dp(2);
            panel.addView(row, rlp);
            rows.add(row);
        }
        // Wrap focus top <-> bottom and keep it inside the menu.
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            r.setId(View.generateViewId());
        }
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            r.setNextFocusUpId(rows.get((i - 1 + rows.size()) % rows.size()).getId());
            r.setNextFocusDownId(rows.get((i + 1) % rows.size()).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }

        int w = dp(220);
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        float aw = anchor.getWidth() * anchor.getScaleX(), ah = anchor.getHeight() * anchor.getScaleY();
        int x = (int) (loc[0] + aw + dp(18));
        if (x + w > screenW - dp(24)) x = (int) (loc[0] - w - dp(18));
        x = Math.max(dp(24), x);
        panel.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int h = panel.getMeasuredHeight();
        int y = (int) (loc[1] + ah / 2 - h / 2f);
        y = Math.max(dp(24), Math.min(y, screenH - h - dp(24)));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = x;
        plp.topMargin = y;
        menuLayer.addView(panel, plp);
        root.addView(menuLayer, match());

        // Pop in from the card's side.
        panel.setPivotX(x > loc[0] ? 0 : w);
        panel.setPivotY(h / 2f);
        panel.setAlpha(0f);
        panel.setScaleX(0.9f);
        panel.setScaleY(0.9f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.d(220)).setInterpolator(LIFT).start();
        menuLayer.animate().setDuration(Motion.d(220)).start();
        menuLayer.setBackgroundColor(0x59000000);
        if (!rows.isEmpty()) rows.get(0).requestFocus();
    }

    void closeMenu() {
        if (menuLayer == null) return;
        FrameLayout m = menuLayer;
        menuLayer = null;
        m.animate().alpha(0f).setDuration(Motion.d(150)).withEndAction(() -> root.removeView(m)).start();
        if (menuReturnFocus != null && menuReturnFocus.isAttachedToWindow()) menuReturnFocus.requestFocus();
        menuReturnFocus = null;
    }

    void startSafe(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            toast("Not available on this TV");
        }
    }

    // ---------------------------------------------------------------- move mode

    void startMove(int index) {
        stopSway(null);
        moveIndex = index;
        focusIndex(index);
        startWiggle(tiles.get(index));
        toast("Use arrows to move \u00b7 OK when done");
    }

    void moveTo(int to) {
        int last = apps.size() - 1;  // the folder tile always stays at the end
        if (to > last && moveIndex / COLS < last / COLS) to = last;
        if (to < 0 || to > last || to == moveIndex) return;
        Map<String, int[]> before = tilePositions();
        AppEntry e = apps.remove(moveIndex);
        apps.add(to, e);
        moveIndex = to;
        saveOrder();
        buildGrid();
        focusIndex(to);
        startWiggle(tiles.get(to));
        slideFrom(before);
    }

    /** Window position of every visible card, keyed by app, for the move animation. */
    Map<String, int[]> tilePositions() {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (int i = 0; i < tiles.size(); i++) {
            int[] loc = new int[2];
            tiles.get(i).getLocationInWindow(loc);
            m.put(shown.get(i).key, loc);
        }
        return m;
    }

    /**
     * tvOS-style reflow: after the grid is rebuilt in its new order, every card starts at its old
     * on-screen position and glides to its new slot, so neighbours visibly make room.
     */
    void slideFrom(Map<String, int[]> before) {
        content.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                content.getViewTreeObserver().removeOnPreDrawListener(this);
                for (int i = 0; i < tiles.size(); i++) {
                    int[] old = before.get(shown.get(i).key);
                    if (old == null) continue;
                    View t = tiles.get(i);
                    int[] now = new int[2];
                    t.getLocationInWindow(now);
                    float dx = old[0] - now[0], dy = old[1] - now[1];
                    if (dx == 0 && dy == 0) continue;
                    if (Math.abs(dy) > tileH / 2f && Math.abs(dx) > tileW * 1.5f) {
                        // Wrapped to another row (end of a row): a slide would streak across the
                        // whole screen, so settle into the new slot instead, as tvOS does.
                        View cell = (View) t.getParent();
                        cell.setAlpha(0f);
                        cell.setScaleX(0.86f);
                        cell.setScaleY(0.86f);
                        cell.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.d(240)).setInterpolator(EASE).start();
                        continue;
                    }
                    t.setTranslationX(dx);
                    t.setTranslationY(dy);
                    t.animate().translationX(0).translationY(0).setDuration(Motion.d(260)).setInterpolator(EASE).start();
                }
                return true;
            }
        });
    }

    void endMove() {
        stopWiggle();
        moveIndex = -1;
    }

    void startWiggle(View v) {
        stopWiggle();
        wiggle = ObjectAnimator.ofFloat(v, View.ROTATION, -1.5f, 1.5f);
        wiggle.setDuration(130);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.start();
    }

    void stopWiggle() {
        if (wiggle != null) {
            wiggle.cancel();
            Object t = wiggle.getTarget();
            if (t instanceof View) ((View) t).setRotation(0);
            wiggle = null;
        }
    }

    // ---------------------------------------------------------------- helpers

    TextView text(float sp, Typeface tf, float alpha) {
        TextView t = new HTextView(this);
        Ui.textSize(t, sp);
        t.setTypeface(tf);
        t.setTextColor(Color.WHITE);
        t.setAlpha(alpha);
        t.setShadowLayer(10, 0, 2, 0x99000000);
        return t;
    }

    FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Design units (1/960 of the screen width), so layout follows the display's resolution. */
    int dp(float v) {
        return Math.round(v * unit);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
