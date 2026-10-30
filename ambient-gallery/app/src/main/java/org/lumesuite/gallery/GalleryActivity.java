package org.lumesuite.gallery;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Galleria da sfogliare col telecomando: destra/sinistra cambia quadro, su/giu' filtra per tema,
 * OK mostra o nasconde la targhetta, Indietro esce.
 */
public class GalleryActivity extends Activity {
    private static final String[][] THEMES = {
        {"", "Tutti"}, {"paesaggio", "Paesaggi"}, {"mare", "Mare"}, {"citta", "Citta'"}, {"natura", "Natura"}, {"ritratto", "Ritratti"}};
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WallView wall;
    private TextView hint;
    private List<Art.Work> all, shown = new ArrayList<>();
    private int index = 0, theme = 0;
    private long requestId = 0;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wall = new WallView(this);
        hint = new TextView(this);
        hint.setTextSize(18);
        hint.setTextColor(0xCCFFFFFF);
        hint.setShadowLayer(6, 0, 1, 0xFF000000);
        hint.setPadding(96, 56, 96, 0);
        wall.addView(hint);
        setContentView(wall);
        all = Art.catalog(this);
        rebuild();
        showCurrent();
    }

    private void rebuild() {
        shown.clear();
        for (Art.Work w : all) if (THEMES[theme][0].isEmpty() || w.theme.equals(THEMES[theme][0])) shown.add(w);
        if (shown.isEmpty()) { theme = 0; shown.addAll(all); }
        index = 0;
    }

    private void showCurrent() {
        if (shown.isEmpty()) { hint.setText("Catalogo vuoto"); return; }
        final long id = ++requestId;
        final Art.Work w = shown.get(index);
        hint.setText(THEMES[theme][1] + "   " + (index + 1) + "/" + shown.size()
                + "      <  >  sfoglia      su/giu'  tema      OK  titolo      Indietro  esci");
        io.execute(() -> {
            Bitmap b = Art.load(this, w, wall.screenW(), wall.screenH());
            // precarica il successivo in cache disco senza decodificarlo a schermo
            if (shown.size() > 1) Art.load(this, shown.get((index + 1) % shown.size()), 64, 64);
            ui.post(() -> { if (id == requestId && b != null) wall.show(b, w, TimeOfDay.now(), true); });
        });
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                index = (index + 1) % shown.size(); showCurrent(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_PREVIOUS: case KeyEvent.KEYCODE_MEDIA_REWIND:
                index = (index - 1 + shown.size()) % shown.size(); showCurrent(); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                theme = (theme + 1) % THEMES.length; rebuild(); showCurrent(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                theme = (theme - 1 + THEMES.length) % THEMES.length; rebuild(); showCurrent(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                wall.toggleLabel(); return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}
