package com.arena.nitrodrive;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.os.Vibrator;
import android.os.VibrationEffect;

/** Fullscreen game surface: drives the frame loop and routes touches. */
public class GameView extends View implements Runnable {
    public Game game;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private float downX;
    private float downY;
    private long downTime;
    private boolean dragged;
    private boolean longPressUsed;
    private long lastHaptic;
    private int lowFps;

    public GameView(Context c) { super(c); init(c); }
    public GameView(Context c, AttributeSet a) { super(c, a); init(c); }

    private void init(Context c) {
        setKeepScreenOn(true);
        setFocusable(true);
        Draw.init(c.getAssets());
        Store.load(c);
        Sfx.enabled = Store.sfxOn;
        Sfx.init(c.getApplicationContext());
        Music.init();
        game = new Game(this);
    }

    public void applyQuality() {
        // quality flag is read by the game's particle budget on next run
        Sfx.enabled = Store.sfxOn;
    }

    public void haptic(int ms) {
        if (!Store.hapticsOn) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastHaptic < 40) return;
        lastHaptic = now;
        try {
            if (ms <= 0) return;
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                if (ms <= 25) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(VibrationEffect.createOneShot(ms, 180));
            } else {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) { }
    }

    public void onRunOver() { if (game != null) game.onRunOver(); }
    public void postDelayedAction(Runnable r, long ms) { handler.postDelayed(r, ms); }
    public void finishApp() {
        // handled by Activity
        if (getContext() instanceof MainActivity) ((MainActivity) getContext()).finishApp();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        Draw.quality = Store.quality;
        game.resize(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        game.draw(c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); downTime = SystemClock.uptimeMillis();
                dragged = false; longPressUsed = false;
                game.onDown(downX, downY);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(e.getX() - downX);
                float dy = Math.abs(e.getY() - downY);
                if (dx > ViewConfiguration.get(getContext()).getScaledTouchSlop() || dy > ViewConfiguration.get(getContext()).getScaledTouchSlop())
                    dragged = true;
                game.onMove(e.getX(), e.getY());
                return true;
            }
            case MotionEvent.ACTION_UP: {
                long dt = SystemClock.uptimeMillis() - downTime;
                if (!dragged && dt < 500) game.onTap(downX, downY);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public void run() {
        if (!running) return;
        long start = SystemClock.uptimeMillis();
        game.update();
        invalidate();
        long cost = SystemClock.uptimeMillis() - start;
        long delay = Math.max(1, 16 - cost);
        handler.postDelayed(this, delay);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        handler.post(this);
        if (Store.musicOn) Music.setEnabled(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        handler.removeCallbacks(this);
        Store.save();
        Sfx.setEngine(0, 0);
        Music.stop();
        super.onDetachedFromWindow();
    }

    public void startLoopAgain() { if (!running) { running = true; handler.post(this); } }
}
