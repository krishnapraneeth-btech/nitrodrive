package com.arena.nitrodrive;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsetsController;

public class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        hideSystemUI();
        setContentView(view);
    }

    @Override
    public void onBackPressed() {
        if (view != null) view.game.onBack();
        else super.onBackPressed();
    }

    public void finishApp() {
        Store.save();
        finish();
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideSystemUI();
        if (view != null) {
            if (focused) { view.startLoopAgain(); Music.setEnabled(Store.musicOn); }
            else { Store.save(); Music.setEnabled(false); Sfx.setEngine(0, 0); }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.save();
        Sfx.setEngine(0, 0);
        Music.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.musicOn) Music.setEnabled(true);
    }
}
