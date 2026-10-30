package org.lumesuite.gallery;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

import java.util.List;

/** Screensaver: i dipinti piu' importanti, uno dopo l'altro, come quadri appesi a una parete. */
public class ArtDream extends DreamService {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WallView wall;
    private volatile boolean running;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        wall = new WallView(this);
        setContentView(wall);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        running = true;
        new Thread(this::loop, "ambient-art").start();
    }

    @Override public void onDreamingStopped() {
        running = false;
        super.onDreamingStopped();
    }

    private void loop() {
        List<Art.Work> works = Art.catalog(this);
        if (works.isEmpty()) return;
        int i = 0;
        while (running) {
            try {
                Art.Work w = works.get(i++ % works.size());
                Bitmap bmp = Art.load(this, w, wall.screenW(), wall.screenH());
                TimeOfDay.Phase p = TimeOfDay.now();
                if (bmp != null) ui.post(() -> wall.show(bmp, w, p, false));
                Thread.sleep(TimeOfDay.holdSeconds(p) * 1000L);
            } catch (InterruptedException e) { return; }
        }
    }
}
