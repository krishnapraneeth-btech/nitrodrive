package com.arena.nitrodrive;

import android.content.Context;
import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Offline harness: renders the real game to PNG screenshots and simulates runs. */
public class Harness {
    static int W = 540, H = 1170;

    public static void main(String[] a) throws Exception {
        boolean headless = !java.awt.GraphicsEnvironment.isHeadless();
        File outDir = new File(System.getProperty("nd.out", "/home/user/nitrodrive/screens"));
        outDir.mkdirs();
        Context ctx = new Context();
        Draw.init(ctx.getAssets());
        Store.load(ctx);
        Store.coins = 9999;

        GameView gv = new GameView(ctx);
        // GameView constructor uses real Android bits; it should be stubbed fine.
        Game game = gv.game;
        if (game == null) throw new RuntimeException("game null");
        gv.onSizeChanged(W, H, 0, 0);

        // ---- MENU ----
        render(gv, outDir, "01_menu");

        // ---- GARAGE ----
        game.screen = Game.GARAGE;
        game.carPage = 2;
        render(gv, outDir, "02_garage");

        // ---- MISSIONS ----
        game.screen = Game.MISSIONS;
        render(gv, outDir, "03_missions");

        // ---- SETTINGS ----
        game.screen = Game.SETTINGS;
        render(gv, outDir, "04_settings");

        // ---- GAMEPLAY: simulate a run, then screenshot ----
        game.screen = Game.MENU;
        game.startRun();
        World w = game.world;
        long t0 = System.currentTimeMillis();
        float dt = 1/60f;
        int frames = 0;
        float maxSpeed = 0;
        int collisions = 0;
        int lastLives = w.lives;
        w.nitro = 1f;
        for (int i = 0; i < 60 * 40; i++) {  // 40 seconds
            game.updateExternal(dt);
            frames++;
            maxSpeed = Math.max(maxSpeed, w.speedKmh);
            if (w.lives < lastLives) { collisions++; lastLives = w.lives; }
            if (w.over) break;
            // simple bot: dodge nearest traffic
            if (i % 10 == 0) {
                w.steerTo(botLane(w));
                if (w.nitro > 0.5f && (i / 10) % 30 == 0) { w.nitroActive = true; w.nitroT = 1.5f; }
            }
            if (frames == 60 * 12) render(gv, outDir, "05_gameplay_a");
            if (frames == 60 * 20) render(gv, outDir, "06_gameplay_b");
            if (frames == 60 * 28) render(gv, outDir, "07_gameplay_c");
        }
        System.out.println("SIM: frames=" + frames + " dist=" + (int) w.meters + "m level=" + w.level +
                " score=" + w.score + " coins=" + w.coinRun + " maxKmh=" + (int) maxSpeed +
                " collisions=" + collisions + " over=" + w.over + " lives=" + w.lives +
                " trafficAlive=" + aliveTraffic(w) + " ms=" + (System.currentTimeMillis() - t0));

        // ---- PAUSE ----
        w.paused = true;
        render(gv, outDir, "07_pause");
        w.paused = false;

        // ---- GAME OVER ----
        game.screen = Game.PLAY;
        w.over = true; w.wrecked = true;
        game.endRun();
        render(gv, outDir, "08_gameover");

        // ---- stress: 25 minutes of simulated driving for numeric sanity ----
        game.screen = Game.MENU;
        game.startRun();
        World w2 = game.world;
        float simDt = 0.05f;
        int nano = 0;
        boolean anyNaN = false;
        for (int i = 0; i < 20 * 60 * 25 && !w2.over; i++) {
            if (i % 6 == 0) {
                float best = -1, bestD = -1e9f;
                for (int k = 0; k < 5; k++) {
                    float d = 1e9f;
                    for (int j = 0; j < 22; j++) {
                        if (isOn(w2, j) && Math.abs(w2.laneOf(j) - k) < 0.5f && w2.yOf(j) < w2.py) d = Math.min(d, w2.py - w2.yOf(j));
                    }
                    if (d > bestD) { bestD = d; best = k; }
                }
                w2.steerTo(best);
                if (w2.nitro > 0.4f) { w2.nitroActive = true; w2.nitroT = 1.2f; }
            }
            game.updateExternal(simDt);
            nano++;
            if (Float.isNaN(w2.meters) || Float.isNaN(w2.speedPx) || Float.isNaN(w2.score)) { anyNaN = true; break; }
        }
        System.out.println("STRESS: steps=" + nano + " over=" + w2.over + " dist=" + (int) w2.meters +
                " level=" + w2.level + " score=" + w2.score + " nan=" + anyNaN + " lives=" + w2.lives);

        // saved prefs sanity
        Store.save();
        System.out.println("PREFS: coins=" + Store.coins + " best=" + Store.bestDist + " skinsOwned=" + count(Store.skinOwned));
        System.out.println("HARNESS_OK");
        System.exit(0);
    }

    static int botLane(World w) {
        int cur = Math.round(w.carLane);
        int best = cur;
        float bestScore = -1e9f;
        for (int k = 0; k < 5; k++) {
            float gap = 1e9f;
            for (int j = 0; j < 22; j++) {
                if (!w.tOnPublic(j)) continue;
                float dy = w.yOf(j) - w.py;
                if (dy < -w.laneWidth() * 0.2f) continue;         // already passed
                if (Math.abs(w.laneOf(j) - k) > 0.55f) continue;   // other lane
                if (dy < gap) gap = dy;
            }
            float s = Math.min(gap, w.laneWidth() * 6f) - Math.abs(k - cur) * w.laneWidth() * 0.55f;
            if (s > bestScore) { bestScore = s; best = k; }
        }
        return best;
    }

    static boolean isOn(World w, int i) { return w.tOnPublic(i); }
    static int aliveTraffic(World w) { int n = 0; for (int i = 0; i < 22; i++) if (w.tOnPublic(i)) n++; return n; }
    static int count(boolean[] b) { int n = 0; for (boolean x : b) if (x) n++; return n; }

    static void render(GameView gv, File dir, String name) throws Exception {
        Canvas c = new Canvas(Harness.W, Harness.H);
        gv.game.draw(c);
        BufferedImage img = c.image;
        File f = new File(dir, name + ".png");
        ImageIO.write(img, "png", f);
        System.out.println("shot " + f.getName());
    }
}
