package com.arena.nitrodrive;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Random;

/** Screen flow + UI: menu, garage, missions, settings, HUD, pause, game over. */
public class Game {
    public static final int MENU = 0, PLAY = 1, GARAGE = 2, MISSIONS = 3, SETTINGS = 4, GAMEOVER = 5, CREDITS = 6;
    public int screen = MENU;
    final GameView gv;
    final World world;
    float W, H;
    float t;
    final Random rnd = new Random();

    // input capture
    float downX, downY;

    // toast
    String toast;
    float toastT;
    int toastCol;

    // garage
    int carPage;

    // daily missions
    long[] dailyEarn = new long[3];
    boolean[] dailyDone = new boolean[3];
    boolean[] dailyClaimed = new boolean[3];
    static final long[] DAILY_GOAL = {8000, 25, 300};
    static final int[] DAILY_REW = {150, 150, 200};
    boolean goMenuAfter;

    final ArrayList<float[]> fx = new ArrayList<>();

    public Game(GameView gv) {
        this.gv = gv;
        world = new World(gv);
    }

    public void resize(float w, float h) {
        W = w;
        H = h;
        world.resize(w, h);
    }

    // ================= loop =================
    public void update() {
        updateExternal(1 / 60f);
        checkDailyReset();
    }

    /** Step with explicit dt (also used by the offline test harness). */
    public void updateExternal(float dt) {
        dt = Draw.clamp(dt, 0f, 0.05f);
        t += dt;
        if (toastT > 0) toastT -= dt;
        if (screen == PLAY) {
            world.update(dt);
            Sfx.setEngine(world.speedKmh * 1.1f + (world.nitroActive ? 40 : 0), 0.16f + world.speedKmh / 900f);
        } else {
            Sfx.setEngine(60, screen == GAMEOVER ? 0.03f : 0f);
        }
        for (int i = fx.size() - 1; i >= 0; i--) {
            float[] f = fx.get(i);
            f[4] -= dt;
            if (f[4] <= 0) { fx.remove(i); continue; }
            f[0] += f[2] * dt;
            f[1] += f[3] * dt;
            f[3] += 320 * dt;
        }
    }

    void checkDailyReset() {
        String today = Store.today();
        if (!today.equals(Store.missionDay)) {
            Store.missionDay = today;
            for (int i = 0; i < 3; i++) { dailyEarn[i] = 0; dailyDone[i] = false; dailyClaimed[i] = false; }
            Store.save();
        }
    }

    // ================= input =================
    public void onDown(float x, float y) { downX = x; downY = y; }

    public void onMove(float x, float y) {
        if (screen == PLAY && !world.over && !world.paused) {
            float dx = x - downX, dy = y - downY;
            if (Math.abs(dx) > W * 0.05f && Math.abs(dx) > Math.abs(dy) * 0.8f) {
                world.tapLane(dx > 0);
                downX = x;
            }
        }
    }

    public void onTap(float x, float y) {
        Sfx.clickSfx();
        if (screen == MENU) menuTap(x, y);
        else if (screen == PLAY) playTap(x, y);
        else if (screen == GARAGE) garageTap(x, y);
        else if (screen == MISSIONS) missionTap(x, y);
        else if (screen == SETTINGS) settingsTap(x, y);
        else if (screen == GAMEOVER) overTap(x, y);
        else screen = MENU;
    }

    void startRun() {
        goMenuAfter = false;
        world.start();
        Music.start();
        screen = PLAY;
    }

    // ----- menu layout -----
    float menuBtnY(int i) { return H * 0.405f + i * H * 0.102f; }
    float menuBtnH(int i) { return i == 0 ? H * 0.093f : H * 0.076f; }

    void menuTap(float x, float y) {
        for (int i = 0; i < 4; i++) {
            float by = menuBtnY(i), bh = menuBtnH(i);
            float bw = i == 0 ? W * 0.74f : W * 0.66f;
            float bl = (W - bw) / 2f;
            if (hit(bl, by, bw, bh, x, y)) {
                if (i == 0) startRun();
                else if (i == 1) { carPage = (int) Store.selectedCar; screen = GARAGE; }
                else if (i == 2) screen = MISSIONS;
                else screen = SETTINGS;
                return;
            }
        }
        float cy = menuCarY();
        if (hit(W * 0.02f, cy - H * 0.04f, W * 0.13f, H * 0.08f, x, y)) { cycleCar(-1); return; }
        if (hit(W * 0.85f, cy - H * 0.04f, W * 0.13f, H * 0.08f, x, y)) { cycleCar(1); }
    }

    float menuCarY() { return H * 0.855f; }

    void cycleCar(int d) {
        Store.selectedCar = ((Store.selectedCar + d) % 6 + 6) % 6;
        if (!Store.unlocked[(int) Store.selectedCar]) toast("LOCKED — OPEN GARAGE", 0xFFFF6B6B);
        Store.save();
    }

    // ----- play -----
    void playTap(float x, float y) {
        if (world.over) return;
        if (world.paused) { pauseTap(x, y); return; }
        if (hit(W * 0.40f, 0, W * 0.20f, H * 0.095f, x, y)) { world.paused = true; return; }
        if (hit(nitroBtnX(), nitroBtnY(), nitroBtnW(), nitroBtnH(), x, y)) { fireNitro(); return; }
        if (y < H * 0.16f) return;
        if (x < W * 0.5f) world.tapLane(false);
        else world.tapLane(true);
    }

    float nitroBtnX() { return W * 0.05f; }
    float nitroBtnY() { return H * 0.865f; }
    float nitroBtnW() { return W * 0.37f; }
    float nitroBtnH() { return H * 0.078f; }

    void fireNitro() {
        if (world.nitroActive) return;
        if (world.nitro < 0.25f) { toast("NITRO NOT READY", 0xFFFF6B6B); return; }
        world.nitroActive = true;
        world.nitroT = 0.55f + world.nitro * 1.6f;
        world.nitroUses++;
        Sfx.powerSfx();
        gv.haptic(20);
        world.addBurst(world.playerX(), world.playerY(), 0xFF37E9FF, 18);
    }

    float pauseY(int i) { return H * 0.40f + i * H * 0.115f; }

    void pauseTap(float x, float y) {
        float bw = W * 0.62f, bl = (W - bw) / 2f, bh = H * 0.082f;
        for (int i = 0; i < 4; i++) {
            if (hit(bl, pauseY(i), bw, bh, x, y)) {
                if (i == 0) world.paused = false;
                else if (i == 1) { world.paused = false; endRun(); }
                else if (i == 2) { Store.sfxOn = !Store.sfxOn; Sfx.enabled = Store.sfxOn; Store.save(); toast(Store.sfxOn ? "SOUND ON" : "SOUND OFF", 0xFF7CE7FF); }
                else { world.paused = false; gotoMenu(); }
                return;
            }
        }
    }

    public void onBack() {
        if (screen == PLAY) {
            if (world.paused) { world.paused = false; endRun(); }
            else world.paused = true;
        } else if (screen == GAMEOVER) gotoMenu();
        else if (screen != MENU) screen = MENU;
        else gv.finishApp();
    }

    // ----- garage -----
    float garageActY() { return H * 0.585f; }
    float garageSkinY() { return H * 0.745f; }
    float garageSkinSize() { return W * 0.108f; }
    float garageUpgY() { return H * 0.885f; }

    void garageTap(float x, float y) {
        if (hit(0, 0, W * 0.22f, H * 0.085f, x, y)) { screen = MENU; return; }
        if (hit(W * 0.02f, H * 0.20f, W * 0.14f, H * 0.14f, x, y)) { carPage = (carPage + 5) % 6; return; }
        if (hit(W * 0.84f, H * 0.20f, W * 0.14f, H * 0.14f, x, y)) { carPage = (carPage + 1) % 6; return; }
        for (int i = 0; i < 6; i++) {
            float dx = W / 2 + (i - 2.5f) * W * 0.085f;
            if (hit(dx - W * 0.03f, H * 0.385f, W * 0.06f, H * 0.04f, x, y)) { carPage = i; return; }
        }
        float bw = W * 0.72f, bl = (W - bw) / 2f, bh = H * 0.078f;
        if (hit(bl, garageActY(), bw, bh, x, y)) {
            Store.Car car = Store.CARS[carPage];
            if (Store.unlocked[carPage]) {
                if (Store.selectedCar != carPage) {
                    Store.selectedCar = carPage;
                    Store.save();
                    Sfx.buySfx();
                    toast("CAR SELECTED", 0xFF7CFF6B);
                } else startRun();
            } else if (Store.coins >= car.price) {
                Store.coins -= car.price;
                Store.unlocked[carPage] = true;
                Store.selectedCar = carPage;
                Store.save();
                Sfx.levelUpPlay();
                gv.haptic(20);
                burstConfetti(W * 0.5f, H * 0.35f, 40);
                toast(car.name + " UNLOCKED!", 0xFF00E5FF);
            } else toast("NOT ENOUGH COINS", 0xFFFF6B6B);
            return;
        }
        float size = garageSkinSize(), gap = W * 0.012f;
        float sl = (W - (6 * size + 5 * gap)) / 2f;
        for (int k = 0; k < 6; k++) {
            float sx = sl + k * (size + gap);
            if (hit(sx, garageSkinY(), size, size, x, y)) {
                int idx = carPage * 3 + k;
                if (Store.skinOwned[idx]) {
                    if (Store.selectedSkin != idx) { Store.selectedSkin = idx; Store.save(); Sfx.clickSfx(); }
                } else if (Store.coins >= Store.SKIN_PRICE[carPage]) {
                    Store.coins -= Store.SKIN_PRICE[carPage];
                    Store.skinOwned[idx] = true;
                    Store.selectedSkin = idx;
                    Store.save();
                    Sfx.buySfx();
                    toast("PAINT UNLOCKED", 0xFFB78CFF);
                } else toast("NOT ENOUGH COINS", 0xFFFF6B6B);
                return;
            }
        }
        float uw = (W - W * 0.10f - 3 * W * 0.028f) / 4f;
        for (int i = 0; i < 4; i++) {
            float ux = W * 0.05f + i * (uw + W * 0.028f);
            if (hit(ux, garageUpgY() - H * 0.042f, uw, H * 0.092f, x, y)) {
                if (Store.upg(i) >= Store.UPG_MAX[i]) { toast("MAX LEVEL", 0xFF00E5FF); return; }
                int cost = Store.upgCost(i);
                if (Store.coins < cost) { toast("NOT ENOUGH COINS", 0xFFFF6B6B); return; }
                Store.coins -= cost;
                if (i == 0) Store.upgSpeed++;
                else if (i == 1) Store.upgNitro++;
                else if (i == 2) Store.upgMagnet++;
                else Store.upgArmor++;
                Store.save();
                Sfx.buySfx();
                gv.haptic(14);
                toast("UPGRADED!", 0xFF7CFF6B);
                return;
            }
        }
    }

    // ----- missions -----
    float missionRowY(int i) { return H * 0.158f + i * H * 0.0805f; }
    float missionRowH() { return H * 0.0725f; }
    float dailyY() { return H * 0.742f; }

    void missionTap(float x, float y) {
        if (hit(0, 0, W * 0.22f, H * 0.085f, x, y)) { screen = MENU; return; }
        float l = W * 0.05f, w = W * 0.90f, h = missionRowH();
        for (int i = 0; i < 7; i++) {
            float ry = missionRowY(i);
            if (hit(l + w - W * 0.25f, ry, W * 0.24f, h, x, y)) {
                if (Store.claimed[i] <= Store.bestMission[i]) {
                    long rew = missionReward(i);
                    Store.claimed[i] = Store.bestMission[i] + 1;   // re-arms when the next tier is reached
                    Store.coins += rew;
                    Store.totalCoinsEarned += rew;
                    Store.save();
                    Sfx.buySfx();
                    burstConfetti(l + w * 0.86f, ry + h / 2, 18);
                    toast("+" + rew + " COINS", 0xFFFFD24D);
                }
                return;
            }
        }
        float dw = (w - W * 0.04f) / 3f;
        for (int i = 0; i < 3; i++) {
            float dx = l + i * (dw + W * 0.02f);
            if (hit(dx, dailyY(), dw, H * 0.12f, x, y)) {
                if (dailyDone[i] && !dailyClaimed[i] && dailyEarn[i] >= DAILY_GOAL[i]) {
                    dailyClaimed[i] = true;
                    Store.coins += DAILY_REW[i];
                    Store.totalCoinsEarned += DAILY_REW[i];
                    Store.save();
                    Sfx.buySfx();
                    toast("+" + DAILY_REW[i] + " COINS", 0xFFFFD24D);
                }
                return;
            }
        }
    }

    // ----- settings -----
    float setRowY(int i) { return H * 0.175f + i * H * 0.104f; }

    void settingsTap(float x, float y) {
        if (hit(0, 0, W * 0.22f, H * 0.085f, x, y)) { screen = MENU; return; }
        float l = W * 0.05f, w = W * 0.90f, h = H * 0.084f;
        for (int i = 0; i < 4; i++) {
            if (hit(l, setRowY(i), w, h, x, y)) {
                if (i == 0) { Store.sfxOn = !Store.sfxOn; Sfx.enabled = Store.sfxOn; }
                else if (i == 1) { Store.musicOn = !Store.musicOn; Music.setEnabled(Store.musicOn); }
                else if (i == 2) Store.hapticsOn = !Store.hapticsOn;
                else { Store.quality = Store.quality > 0.5f ? 0.5f : 1f; gv.applyQuality(); }
                Store.save();
                Sfx.clickSfx();
                return;
            }
        }
        if (hit(l, setRowY(4) + H * 0.01f, w, h, x, y)) { resetProgress(); return; }
        if (hit(l, setRowY(4) + H * 0.01f + h + H * 0.016f, w, h, x, y)) { screen = CREDITS; }
    }

    void resetProgress() {
        Store.coins = 500;
        Store.bestDist = 0;
        Store.totalDist = 0;
        Store.totalCoinsEarned = 0;
        Store.runCount = 0;
        Store.selectedCar = 0;
        Store.upgSpeed = 0;
        Store.upgNitro = 0;
        Store.upgMagnet = 0;
        Store.upgArmor = 0;
        for (int i = 0; i < 6; i++) { Store.unlocked[i] = i == 0; Store.stars[i] = 0; }
        for (int i = 0; i < 7; i++) { Store.bestMission[i] = 0; Store.claimed[i] = 0; }
        for (int i = 0; i < 18; i++) Store.skinOwned[i] = i < 6;
        Store.selectedSkin = 0;
        Store.save();
        toast("PROGRESS RESET", 0xFFFF6B6B);
    }

    // ----- game over -----
    void endRun() {
        if (screen == GAMEOVER) return;
        Store.runCount++;
        Store.coins += world.totalEarned;
        Store.totalCoinsEarned += world.totalEarned;
        Store.totalDist += (long) world.meters;
        if (world.meters > Store.bestDist) Store.bestDist = (long) world.meters;
        long[] add = {(long) world.meters, world.passRun + world.nearRun, world.coinRun,
                world.nearRun, world.nitroUses, world.coinRun, world.score};
        for (int i = 0; i < 7; i++) {
            while (add[i] >= Store.missionTarget(i)) Store.bestMission[i]++;
        }
        dailyEarn[0] += (long) world.meters;
        dailyEarn[1] += world.passRun + world.nearRun;
        dailyEarn[2] += world.coinRun;
        for (int i = 0; i < 3; i++) if (dailyEarn[i] >= DAILY_GOAL[i]) dailyDone[i] = true;
        Store.totalPassed += world.passRun + world.nearRun;
        Store.totalNearMiss += world.nearRun;
        Store.totalNitro += world.nitroUses;
        Store.totalScore += world.score;
        for (int i = 0; i < 3; i++) if (dailyEarn[i] >= DAILY_GOAL[i]) dailyDone[i] = true;
        Store.save();
        screen = GAMEOVER;
        Music.setEnabled(Store.musicOn);
    }

    void gotoMenu() {
        goMenuAfter = false;
        screen = MENU;
        Music.setEnabled(Store.musicOn);
        Sfx.setEngine(0, 0);
    }

    public void onRunOver() {
        if (screen == PLAY) {
            gv.haptic(200);
            gv.postDelayedAction(this::onRunOverDelay, 950);
        }
    }

    private void onRunOverDelay() {
        if (screen == PLAY) endRun();
    }

    float overBtnY(int i) { return H * 0.545f + i * H * 0.096f; }

    void overTap(float x, float y) {
        float bw = W * 0.78f, bl = (W - bw) / 2f, bh = H * 0.076f;
        if (!goMenuAfter && hit(bl, overBtnY(0), bw, bh, x, y)) {
            Store.coins += world.totalEarned;
            Store.totalCoinsEarned += world.totalEarned;
            Store.save();
            burstConfetti(W / 2, H * 0.5f, 30);
            toast("+" + Draw.fmt(world.totalEarned) + " BONUS COINS", 0xFFFFD24D);
            goMenuAfter = true;
            return;
        }
        if (hit(bl, overBtnY(1), bw, bh, x, y)) { startRun(); return; }
        if (hit(bl, overBtnY(2), bw, bh, x, y)) gotoMenu();
    }

    // ================= helpers =================
    boolean hit(float l, float top, float w, float h, float x, float y) {
        return x >= l && x <= l + w && y >= top && y <= top + h;
    }

    void toast(String s, int col) { toast = s; toastT = 1.7f; toastCol = col; }

    void burstConfetti(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            float[] f = new float[5];
            f[0] = x;
            f[1] = y;
            f[2] = (rnd.nextFloat() - 0.5f) * 720;
            f[3] = (rnd.nextFloat() - 0.8f) * 820;
            f[4] = 0.6f + rnd.nextFloat() * 0.7f;
            fx.add(f);
        }
    }

    // ================= draw =================
    public void draw(Canvas c) {
        if (screen == PLAY || screen == GAMEOVER) {
            float sh = world.crashShake * 16;
            c.save();
            if (sh > 0.3f) c.translate((float) (Math.random() - 0.5) * sh, (float) (Math.random() - 0.5) * sh);
            world.draw(c);
            c.restore();
            drawHud(c);
            if (world.paused) drawPause(c);
            if (screen == GAMEOVER) drawGameOver(c);
        } else if (screen == MENU) drawMenu(c);
        else if (screen == GARAGE) drawGarage(c);
        else if (screen == MISSIONS) drawMissions(c);
        else if (screen == SETTINGS) drawSettings(c);
        else drawCredits(c);
        drawFx(c);
        drawToast(c);
    }

    void drawFx(Canvas c) {
        for (int i = 0; i < fx.size(); i++) {
            float[] f = fx.get(i);
            int col = f[2] > 0 ? 0xFF00E5FF : 0xFFFF2E97;
            Draw.rrect(c, f[0] - 4, f[1] - 4, f[0] + 4, f[1] + 4, 2, Draw.alpha(col, (int) (255 * Math.min(1, f[4]))));
        }
    }

    void drawToast(Canvas c) {
        if (toastT <= 0 || toast == null) return;
        float a = Math.min(1, toastT * 2);
        float tw = Math.min(W * 0.9f, Draw.measure(toast, W * 0.040f, Draw.F_BOLD) + W * 0.10f);
        float x = W / 2 - tw / 2, y = H * 0.115f;
        Draw.rrect(c, x, y, x + tw, y + H * 0.052f, H * 0.026f, Draw.alpha(0xFF0B1020, (int) (230 * a)));
        Draw.rrectStroke(c, x, y, x + tw, y + H * 0.052f, H * 0.026f, Draw.alpha(toastCol, (int) (210 * a)), 2);
        Draw.text(c, toast, W / 2, y + H * 0.035f, W * 0.038f, Draw.F_BOLD, Draw.alpha(0xFFFFFFFF, (int) (255 * a)), Paint.Align.CENTER);
    }

    void drawBackground(Canvas c, int neon) {
        Draw.sh(new RadialGradient(W * 0.5f, H * 0.34f, H * 0.95f, 0xFF141B3A, 0xFF05070F, Shader.TileMode.CLAMP));
        Draw.p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, H, Draw.p);
        Draw.shClear();
        float hy = H * 0.56f;
        Draw.s.setStyle(Paint.Style.STROKE);
        Draw.s.setShader(null);
        Draw.s.setStrokeWidth(1.5f);
        for (int i = -9; i <= 9; i++) {
            Draw.s.setColor(Draw.alpha(neon, 24));
            c.drawLine(W / 2 + i * W * 0.13f, H, W / 2 + i * W * 0.013f, hy, Draw.s);
        }
        for (int i = 0; i < 12; i++) {
            float f = (float) Math.pow((i + (t * 0.30f) % 1f) / 12f, 2.2f);
            float y = hy + (H - hy) * f;
            Draw.s.setColor(Draw.alpha(neon, (int) (14 + 42 * f)));
            c.drawLine(0, y, W, y, Draw.s);
        }
        for (int i = 0; i < 44; i++) {
            float sx = (i * 97.13f) % W;
            float sy = (i * 53.7f) % (hy * 0.8f);
            Draw.p.setColor(Draw.alpha(0xFFFFFFFF, (int) (36 + 60 * Math.abs(Math.sin(t * 0.8f + i)))));
            c.drawCircle(sx, sy, 1.3f, Draw.p);
        }
        Draw.sh(new RadialGradient(W * 0.2f, H * 0.12f, W * 0.7f, Draw.alpha(0xFF00E5FF, 20), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, Draw.p);
        Draw.sh(new RadialGradient(W * 0.88f, H * 0.30f, W * 0.7f, Draw.alpha(0xFFFF2E97, 20), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, Draw.p);
        Draw.shClear();
    }

    void drawTopBar(Canvas c, String title) {
        Draw.iconChevron(c, W * 0.075f, H * 0.047f, H * 0.030f, 0xFFFFFFFF, true, H * 0.0055f);
        Draw.text(c, "BACK", W * 0.115f, H * 0.055f, W * 0.030f, Draw.F_BOLD, 0xFF8FA3C0, Paint.Align.LEFT);
        Draw.text(c, title, W * 0.45f, H * 0.056f, W * 0.055f, Draw.F_HEAD, 0xFFFFFFFF, Paint.Align.CENTER);
        Draw.line(c, W * 0.06f, H * 0.077f, W * 0.94f, H * 0.077f, Draw.alpha(0xFF00E5FF, 55), 1.5f);
        // coin pill (top-right, compact)
        float pw = W * 0.255f, ph = H * 0.045f, pl = W - pw - W * 0.035f, pt = H * 0.024f;
        Draw.rrect(c, pl, pt, pl + pw, pt + ph, ph / 2, 0xCC0B1020);
        Draw.rrectStroke(c, pl, pt, pl + pw, pt + ph, ph / 2, 0x66FFC53D, 1.6f);
        Draw.iconCoin(c, pl + ph * 0.52f, pt + ph / 2, ph * 0.33f, t * 2, 0);
        Draw.text(c, Draw.fmt(Store.coins), pl + ph * 1.05f, pt + ph * 0.68f, W * 0.036f, Draw.F_BOLD, 0xFFFFD24D, Paint.Align.LEFT);
    }

    void drawButton(Canvas c, float l, float top, float w, float h, String label, int c1, int c2, boolean filled) {
        float rad = h * 0.5f;
        if (filled) {
            Draw.sh(Draw.lg(l, top, l + w, top + h, Draw.alpha(c1, 240), Draw.alpha(c2, 240)));
            Draw.p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(l, top, l + w, top + h, rad, rad, Draw.p);
            Draw.shClear();
            Draw.rrect(c, l + w * 0.22f, top + h * 0.09f, l + w * 0.78f, top + h * 0.32f, h * 0.12f, 0x26FFFFFF);
        } else {
            Draw.rrect(c, l, top, l + w, top + h, rad, 0xE60C1424);
            Draw.glowRRect(c, l, top, l + w, top + h, rad, c1, Math.max(2f, h * 0.032f));
        }
        float ts = h * 0.36f;
        Draw.text(c, label, l + w / 2, top + h * 0.63f, ts, Draw.F_HEAD, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    // ---------- MENU ----------
    void drawMenu(Canvas c) {
        drawBackground(c, 0xFF00E5FF);
        float tx = W / 2;
        float size = Math.min(W * 0.165f, H * 0.076f);
        Draw.textGlow(c, "NITRO", tx, H * 0.105f, size, Draw.F_HEAD, 0xFFFFFFFF, 0xFFFF2E97, H * 0.018f);
        Draw.textGlow(c, "DRIVE", tx, H * 0.105f + size * 1.0f, size, Draw.F_HEAD, 0xFFFFFFFF, 0xFF00E5FF, H * 0.018f);
        Draw.textLS(c, "ENDLESS NEON RACER", tx, H * 0.105f + size * 1.58f, W * 0.028f, Draw.F_SEMI, 0xFF7CE7FF, Paint.Align.CENTER, 0.28f);

        // best chip
        float bw = W * 0.56f, bl = (W - bw) / 2f, by = H * 0.295f, bh = H * 0.046f;
        Draw.rrect(c, bl, by, bl + bw, by + bh, bh / 2, 0xAA0B1020);
        Draw.rrectStroke(c, bl, by, bl + bw, by + bh, bh / 2, 0x55FF2E97, 1.5f);
        Draw.iconTrophy(c, bl + bh * 0.62f, by + bh / 2, bh * 0.72f, 0xFFFFC53D);
        Draw.text(c, "BEST  " + Draw.fmt(Store.bestDist) + " m", bl + bh * 1.15f, by + bh * 0.66f, W * 0.036f, Draw.F_BOLD, 0xFFFFE9A8, Paint.Align.LEFT);
        Draw.text(c, Draw.fmt(Store.coins), bl + bw - bh * 0.55f, by + bh * 0.66f, W * 0.036f, Draw.F_BOLD, 0xFFFFD24D, Paint.Align.RIGHT);
        Draw.iconCoin(c, bl + bw - bh * 1.35f, by + bh / 2, bh * 0.26f, t * 2, 0);

        // primary + secondary buttons
        float pulse = 1f + 0.012f * (float) Math.sin(t * 3.4f);
        float pb = menuBtnH(0), pl = (W - W * 0.74f) / 2f;
        drawButton(c, pl - (pulse - 1) * W * 0.37f, menuBtnY(0) - (pulse - 1) * pb / 2, W * 0.74f * pulse, pb * pulse,
                "PLAY", 0xFFFF2E97, 0xFFA30FA6, true);
        drawButton(c, (W - W * 0.66f) / 2f, menuBtnY(1), W * 0.66f, menuBtnH(1), "GARAGE", 0xFF00E5FF, 0xFF7CFF6B, false);
        drawButton(c, (W - W * 0.66f) / 2f, menuBtnY(2), W * 0.66f, menuBtnH(2), "MISSIONS", 0xFFA65CFF, 0xFFFF6FD8, false);
        drawButton(c, (W - W * 0.66f) / 2f, menuBtnY(3), W * 0.66f, menuBtnH(3), "SETTINGS", 0xFF4A6280, 0xFF8FA3C0, false);

        // car showcase
        float cy = menuCarY();
        Draw.sh(new RadialGradient(W / 2, cy, W * 0.42f, 0x3300E5FF, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, cy - H * 0.06f, W, H, Draw.p);
        Draw.shClear();
        Draw.s.setStyle(Paint.Style.STROKE);
        Draw.s.setShader(null);
        Draw.s.setColor(Draw.alpha(0xFF00E5FF, 30));
        Draw.s.setStrokeWidth(1.5f);
        c.drawOval(W * 0.22f, cy - H * 0.012f, W * 0.78f, cy + H * 0.052f, Draw.s);
        int csel = (int) Store.selectedCar;
        int[] cc = Store.carColors(csel, Store.selectedSkin);
        c.save();
        c.rotate(-90, W * 0.5f, cy);
        CarArt.draw(c, W * 0.5f, cy, W * 0.105f, cc[0], cc[1], cc[2], 1, csel, 0, false, t);
        c.restore();
        Draw.rrect(c, W * 0.02f, cy - H * 0.030f, W * 0.115f, cy + H * 0.030f, H * 0.030f, 0x990B1020);
        Draw.rrect(c, W * 0.885f, cy - H * 0.030f, W * 0.98f, cy + H * 0.030f, H * 0.030f, 0x990B1020);
        Draw.iconChevron(c, W * 0.0675f, cy, H * 0.032f, 0xFF7CE7FF, true, H * 0.0055f);
        Draw.iconChevron(c, W * 0.9325f, cy, H * 0.032f, 0xFF7CE7FF, false, H * 0.0055f);
        Store.Car car = Store.CARS[csel];
        boolean lock = !Store.unlocked[csel];
        Draw.text(c, car.name, W / 2, cy + H * 0.060f, W * 0.038f, Draw.F_HEAD, lock ? 0xFF7A8699 : 0xFFFFFFFF, Paint.Align.CENTER);
        if (lock) Draw.text(c, "LOCKED", W / 2, cy + H * 0.082f, W * 0.024f, Draw.F_BOLD, 0xFFFF9A5C, Paint.Align.CENTER);
    }

    // ---------- HUD ----------
    void drawHud(Canvas c) {
        float topH = H * 0.088f;
        Draw.sh(Draw.lg(0, 0, 0, topH, 0xCC05070F, 0x0005070F));
        Draw.p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, topH, Draw.p);
        Draw.shClear();
        // pause + distance center pill
        float pw = W * 0.30f, ph = H * 0.046f, pl = (W - pw) / 2f, pt = H * 0.020f;
        Draw.rrect(c, pl, pt, pl + pw, pt + ph, ph / 2, 0xCC0B1220);
        Draw.rrectStroke(c, pl, pt, pl + pw, pt + ph, ph / 2, 0x6600E5FF, 1.4f);
        Draw.iconPause(c, pl + ph * 0.58f, pt + ph / 2, H * 0.022f, 0xFF7CE7FF);
        Draw.text(c, Draw.fmt((long) world.meters) + " m", pl + pw * 0.63f, pt + ph * 0.66f, W * 0.040f, Draw.F_BOLD, 0xFFFFFFFF, Paint.Align.CENTER);
        // level chip
        Draw.rrect(c, W * 0.025f, H * 0.022f, W * 0.30f, H * 0.066f, H * 0.020f, 0xB00B1220);
        Draw.rrectStroke(c, W * 0.025f, H * 0.022f, W * 0.30f, H * 0.066f, H * 0.020f, 0x88FF2E97, 1.4f);
        Draw.text(c, "LVL " + world.level, W * 0.175f, H * 0.058f, W * 0.036f, Draw.F_HEAD, 0xFFFF8FD0, Paint.Align.CENTER);
        // hearts
        int maxLives = 3 + (Store.upgArmor >= 4 ? 1 : 0);
        for (int i = 0; i < maxLives; i++) {
            float hx = W * 0.955f - i * W * 0.062f;
            Draw.iconHeart(c, hx, H * 0.044f, H * 0.040f, i < world.lives ? 0xFFFF4D6D : 0x33FFFFFF, i < world.lives);
        }
        // level progress
        float lw = W * 0.27f, lx = W * 0.025f, ly = H * 0.0745f;
        Draw.rrect(c, lx, ly, lx + lw, ly + H * 0.0055f, 3, 0x44FFFFFF);
        Draw.rrect(c, lx, ly, lx + lw * Draw.clamp(world.levelDist / world.levelGoal, 0, 1), ly + H * 0.0055f, 3, 0xFF00E5FF);
        Draw.text(c, world.TH.name, W * 0.30f + W * 0.012f, H * 0.0745f + H * 0.008f, W * 0.024f, Draw.F_SEMI, 0xFF6E8098, Paint.Align.LEFT);

        // score + coins row
        Draw.text(c, Draw.fmt(world.score), W * 0.965f, H * 0.125f, W * 0.056f, Draw.F_HEAD, 0xFFFFFFFF, Paint.Align.RIGHT);
        Draw.textLS(c, "SCORE", W * 0.965f, H * 0.150f, W * 0.022f, Draw.F_SEMI, 0xFF8FA3C0, Paint.Align.RIGHT, 0.22f);
        Draw.iconCoin(c, W * 0.045f, H * 0.115f, H * 0.016f, t * 3, 0);
        Draw.text(c, "" + world.coinRun, W * 0.078f, H * 0.124f, W * 0.040f, Draw.F_BOLD, 0xFFFFD24D, Paint.Align.LEFT);
        // temp powerups
        float px = W * 0.055f, pyy = H * 0.175f;
        if (world.shieldT > 0) {
            Draw.iconShield(c, px, pyy, H * 0.030f, 0xFF7CFF6B, true);
            Draw.text(c, "" + (int) Math.ceil(world.shieldT), px + W * 0.028f, pyy + H * 0.010f, W * 0.030f, Draw.F_BOLD, 0xFF7CFF6B, Paint.Align.LEFT);
            pyy += H * 0.042f;
        }
        if (world.magnetT > 0) {
            Draw.iconStar(c, px, pyy, H * 0.016f, H * 0.0065f, 0xFFFFE14D);
            Draw.text(c, "" + (int) Math.ceil(world.magnetT), px + W * 0.028f, pyy + H * 0.010f, W * 0.030f, Draw.F_BOLD, 0xFFFFE14D, Paint.Align.LEFT);
            pyy += H * 0.042f;
        }
        if (world.coinMulT > 0) {
            Draw.iconCoin(c, px, pyy, H * 0.016f, t * 4, 0);
            Draw.text(c, "x2", px + W * 0.028f, pyy + H * 0.010f, W * 0.030f, Draw.F_BOLD, 0xFFFF6FD8, Paint.Align.LEFT);
        }
        // combo
        if (world.comboShown >= 3) {
            float a = Math.min(1, world.comboT);
            float s = 1f + 0.05f * (float) Math.sin(t * 12);
            Draw.textGlow(c, "x" + world.comboShown + " COMBO", W / 2, H * 0.20f, W * 0.046f * s, Draw.F_HEAD,
                    Draw.alpha(0xFFFFE14D, (int) (255 * a)), Draw.alpha(0xFFFF7A1A, (int) (150 * a)), 10);
        }

        // ---- NITRO button (bottom-left) ----
        float bx = nitroBtnX(), by = nitroBtnY(), bw = nitroBtnW(), bh = nitroBtnH();
        boolean ready = world.nitro >= 0.25f;
        Draw.rrect(c, bx, by, bx + bw, by + bh, bh * 0.34f, ready ? 0xE60E2233 : 0x99101622);
        Draw.glowRRect(c, bx, by, bx + bw, by + bh, bh * 0.34f, ready ? 0xFF37E9FF : 0x44707A88, 2.2f);
        Draw.iconBolt(c, bx + bw * 0.17f, by + bh / 2, bh * 0.52f, ready ? 0xFF37E9FF : 0x66707A88);
        Draw.text(c, world.nitroActive ? "BOOST" : "NITRO", bx + bw * 0.60f, by + bh * 0.64f, bh * 0.30f, Draw.F_HEAD,
                ready ? 0xFFFFFFFF : 0x88707A88, Paint.Align.CENTER);
        Draw.s.setStyle(Paint.Style.STROKE);
        Draw.s.setShader(null);
        Draw.s.setStrokeWidth(3.5f);
        Draw.s.setColor(Draw.alpha(0xFF37E9FF, 190));
        c.drawArc(bx + 2, by + 2, bx + bw - 2, by + bh - 2, -90, 360 * Draw.clamp(world.nitro, 0, 1), false, Draw.s);
        Draw.s.setColor(0x33FFFFFF);
        Draw.s.setStrokeWidth(1.2f);
        c.drawArc(bx + 2, by + 2, bx + bw - 2, by + bh - 2, -90, 360, false, Draw.s);

        // ---- speedometer (bottom-right) ----
        float sx = W * 0.80f, sy = H * 0.905f, sr = H * 0.052f;
        Draw.s.setStyle(Paint.Style.STROKE);
        Draw.s.setColor(0x33FFFFFF);
        Draw.s.setStrokeWidth(4f);
        c.drawArc(sx - sr, sy - sr, sx + sr, sy + sr, 130, 280, false, Draw.s);
        float spdF = Draw.clamp((float) (world.speedKmh / 260.0), 0, 1);
        Draw.s.setColor(spdF > 0.8f ? 0xFFFF2E97 : 0xFF37E9FF);
        Draw.s.setStrokeWidth(4.5f);
        c.drawArc(sx - sr, sy - sr, sx + sr, sy + sr, 130, 280 * spdF, false, Draw.s);
        Draw.text(c, "" + (int) world.speedKmh, sx, sy + H * 0.016f, W * 0.062f, Draw.F_HEAD, 0xFFFFFFFF, Paint.Align.CENTER);
        Draw.textLS(c, "KM/H", sx, sy + H * 0.038f, W * 0.020f, Draw.F_SEMI, 0xFF8FA3C0, Paint.Align.CENTER, 0.18f);

        // steering hints (first seconds)
        if (world.meters < 90) {
            float a = Draw.clamp(1f - world.meters / 90f, 0, 1);
            Draw.textGlow(c, "TAP LEFT / RIGHT TO STEER", W / 2, H * 0.60f, W * 0.032f, Draw.F_BOLD,
                    Draw.alpha(0xFFFFFFFF, (int) (200 * a)), Draw.alpha(0xFF00E5FF, (int) (110 * a)), 8);
        }
        // banner
        if (world.bannerT > 0 && world.bannerText != null) {
            float a = Math.min(1, world.bannerT * 1.8f);
            float s = 1f + 0.09f * Math.max(0, world.bannerT - 0.9f);
            Draw.textGlow(c, world.bannerText, W / 2, H * 0.33f, W * 0.075f * s, Draw.F_HEAD,
                    Draw.alpha(0xFFFFFFFF, (int) (255 * a)),
                    Draw.alpha(world.bannerBig > 0 ? 0xFF00E5FF : 0xFFFF2E97, (int) (200 * a)), H * 0.016f);
        }
    }

    void drawPause(Canvas c) {
        Draw.rrect(c, 0, 0, W, H, 0, 0xD9000000);
        Draw.textGlow(c, "PAUSED", W / 2, H * 0.32f, W * 0.10f, Draw.F_HEAD, 0xFFFFFFFF, 0xFF00E5FF, 16);
        float bw = W * 0.62f, bl = (W - bw) / 2f, bh = H * 0.082f;
        drawButton(c, bl, pauseY(0), bw, bh, "RESUME", 0xFF00E5FF, 0xFF7CFF6B, true);
        drawButton(c, bl, pauseY(1), bw, bh, "END RUN", 0xFFFF2E97, 0xFFA30FA6, false);
        drawButton(c, bl, pauseY(2), bw, bh, Store.sfxOn ? "SOUND: ON" : "SOUND: OFF", 0xFF4A6280, 0xFF8FA3C0, false);
        drawButton(c, bl, pauseY(3), bw, bh, "MENU", 0xFF4A6280, 0xFF8FA3C0, false);
    }

    void drawGameOver(Canvas c) {
        Draw.rrect(c, 0, 0, W, H, 0, 0xE0000000);
        float pl = W * 0.05f, pr = W * 0.95f, pt = H * 0.10f, pb = H * 0.845f;
        Draw.rrect(c, pl, pt, pr, pb, W * 0.05f, 0xF20A1120);
        Draw.glowRRect(c, pl, pt, pr, pb, W * 0.05f, 0xFFFF2E97, 2.4f);

        Draw.textGlow(c, world.wrecked ? "WRECKED!" : "RUN OVER", W / 2, pt + H * 0.075f, W * 0.10f, Draw.F_HEAD,
                0xFFFFFFFF, 0xFFFF2E97, H * 0.014f);
        Draw.textLS(c, world.TH.name + "  ·  LEVEL " + world.level, W / 2, pt + H * 0.108f,
                W * 0.028f, Draw.F_SEMI, 0xFF8FA3C0, Paint.Align.CENTER, 0.16f);

        String[][] stats = {
                {"DISTANCE", Draw.fmt((long) world.meters) + " m"},
                {"SCORE", Draw.fmt(world.score)},
                {"COINS", Draw.fmt(world.totalEarned)},
                {"OVERTAKES", "" + (world.passRun + world.nearRun)},
                {"NEAR MISS", "" + world.nearRun},
                {"BEST COMBO", "x" + (int) world.comboMax}
        };
        float gw = (pr - pl - W * 0.09f) / 2f, gh = H * 0.050f;
        float gx0 = pl + W * 0.03f, gy0 = pt + H * 0.135f;
        for (int i = 0; i < 6; i++) {
            float gx = gx0 + (i % 2) * (gw + W * 0.03f);
            float gy = gy0 + (i / 2) * (gh + H * 0.010f);
            Draw.rrect(c, gx, gy, gx + gw, gy + gh, H * 0.014f, 0x660E1830);
            Draw.text(c, stats[i][0], gx + W * 0.016f, gy + gh * 0.40f, W * 0.021f, Draw.F_SEMI, 0xFF7A8DA8, Paint.Align.LEFT);
            Draw.text(c, stats[i][1], gx + W * 0.016f, gy + gh * 0.84f, W * 0.038f, Draw.F_BOLD, 0xFFFFFFFF, Paint.Align.LEFT);
        }
        float ny = gy0 + 3 * (gh + H * 0.010f) + H * 0.030f;
        boolean newBest = world.meters >= Store.bestDist && world.meters > 50;
        if (newBest) {
            float s = 1f + 0.05f * (float) Math.sin(t * 6);
            Draw.textGlow(c, "NEW BEST", W / 2, ny, W * 0.042f * s, Draw.F_HEAD, 0xFFFFE14D, 0xFFFF7A1A, 12);
        } else {
            Draw.text(c, "BEST  " + Draw.fmt(Store.bestDist) + " m", W / 2, ny, W * 0.032f, Draw.F_BOLD, 0xFF7A8DA8, Paint.Align.CENTER);
        }

        // run rating: 1-3 chevrons based on distance
        int stars = world.meters > 1200 ? 3 : (world.meters > 500 ? 2 : 1);
        for (int i = 0; i < 3; i++) {
            float sx = W / 2 + (i - 1) * W * 0.085f;
            float sy = ny + H * 0.042f;
            Draw.iconChevron(c, sx, sy, H * 0.030f, i < stars ? 0xFFFFD24D : 0x22FFFFFF, false, H * 0.005f);
        }

        float bw = W * 0.78f, bl = (W - bw) / 2f, bh = H * 0.076f;
        if (!goMenuAfter) {
            float s = 1f + 0.02f * (float) Math.sin(t * 5);
            drawButton(c, bl - (s - 1) * bw / 2, overBtnY(0) - (s - 1) * bh / 2, bw * s, bh * s,
                    "DOUBLE: CLAIM " + Draw.fmt(world.totalEarned), 0xFFFFC53D, 0xFFFF7A1A, true);
        } else {
            drawButton(c, bl, overBtnY(0), bw, bh, "COINS CLAIMED", 0xFF2A3A4A, 0xFF3A4A5A, true);
        }
        drawButton(c, bl, overBtnY(1), bw, bh, "RACE AGAIN", 0xFF00E5FF, 0xFF7CFF6B, true);
        drawButton(c, bl, overBtnY(2), bw, bh, "MENU", 0xFF4A6280, 0xFF8FA3C0, false);
    }

    // ================= GARAGE =================
    void drawGarage(Canvas c) {
        int neon = 0xFF00E5FF;
        drawBackground(c, 0xFFA65CFF);
        drawTopBar(c, "GARAGE");

        float cy = H * 0.265f;
        Draw.sh(new RadialGradient(W / 2, cy, W * 0.5f, 0x3500E5FF, 0x00000000, Shader.TileMode.CLAMP));
        Draw.p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, H * 0.45f, Draw.p);
        Draw.shClear();
        Draw.s.setStyle(Paint.Style.STROKE);
        Draw.s.setShader(null);
        Draw.s.setStrokeWidth(2f);
        Draw.s.setColor(Draw.alpha(neon, 45));
        c.drawOval(W * 0.12f, cy + H * 0.055f, W * 0.88f, cy + H * 0.115f, Draw.s);
        Draw.iconChevron(c, W * 0.085f, cy, H * 0.040f, 0xFFFFFFFF, true, H * 0.006f);
        Draw.iconChevron(c, W * 0.915f, cy, H * 0.040f, 0xFFFFFFFF, false, H * 0.006f);
        for (int i = 0; i < 6; i++) {
            float dx = W / 2 + (i - 2.5f) * W * 0.085f;
            float dy = H * 0.395f;
            boolean sel = i == carPage;
            Draw.p.setColor(sel ? neon : (Store.unlocked[i] ? 0xFF8FA3C0 : 0x44FFFFFF));
            c.drawCircle(dx, dy, sel ? H * 0.008f : H * 0.0055f, Draw.p);
            if (sel) {
                Draw.s.setStyle(Paint.Style.STROKE);
                Draw.s.setColor(Draw.alpha(neon, 90));
                Draw.s.setStrokeWidth(1.5f);
                c.drawCircle(dx, dy, H * 0.014f, Draw.s);
            }
        }
        Store.Car car = Store.CARS[carPage];
        boolean lock = !Store.unlocked[carPage];
        CarArt.hero(c, W / 2, cy, W * 0.215f, carPage, Store.selectedSkin, t, lock ? 0xFF4A5A70 : neon);

        float ny = H * 0.445f;
        Draw.textGlow(c, car.name, W / 2, ny, W * 0.060f, Draw.F_HEAD, lock ? 0xFF9AA8BC : 0xFFFFFFFF, Draw.alpha(neon, 150), 10);
        Draw.textLS(c, lock ? "LOCKED — UNLOCK WITH COINS" : car.tagline.toUpperCase(), W / 2, ny + H * 0.024f,
                W * 0.024f, Draw.F_SEMI, lock ? 0xFFFF9A5C : 0xFF8FA3C0, Paint.Align.CENTER, 0.1f);
        String[] lbl = {"SPEED", "GRIP", "NITRO"};
        float[] val = {car.topSpeedMul / 1.6f, (car.handling - 0.9f) / 0.6f, (car.nitroPow - 0.9f) / 0.7f};
        float sbw = W * 0.24f, sby = ny + H * 0.052f;
        for (int i = 0; i < 3; i++) {
            float sbx = W * 0.05f + i * (sbw + W * 0.04f);
            Draw.text(c, lbl[i], sbx + sbw / 2, sby, W * 0.023f, Draw.F_SEMI, 0xFF7A8DA8, Paint.Align.CENTER);
            Draw.rrect(c, sbx, sby + H * 0.007f, sbx + sbw, sby + H * 0.0145f, 4, 0x44FFFFFF);
            Draw.rrect(c, sbx, sby + H * 0.007f, sbx + sbw * Draw.clamp(val[i], 0.12f, 1f), sby + H * 0.0145f, 4,
                    i == 0 ? 0xFFFF2E97 : (i == 1 ? 0xFF00E5FF : 0xFFA65CFF));
        }

        float bw = W * 0.72f, bl = (W - bw) / 2f, bh = H * 0.078f;
        boolean selected = Store.selectedCar == carPage && Store.unlocked[carPage];
        if (selected) drawButton(c, bl, garageActY(), bw, bh, "PLAY", 0xFF00E5FF, 0xFF7CFF6B, true);
        else if (lock) {
            if (Store.coins >= car.price) drawButton(c, bl, garageActY(), bw, bh, "BUY  " + Draw.fmt(car.price), 0xFFFFC53D, 0xFFFF7A1A, true);
            else drawButton(c, bl, garageActY(), bw, bh, Draw.fmt(car.price) + " COINS NEEDED", 0xFF4A6280, 0xFF8FA3C0, false);
        } else drawButton(c, bl, garageActY(), bw, bh, "SELECT", 0xFFA65CFF, 0xFFFF6FD8, true);
        Draw.textLS(c, "POWER: " + car.power, W / 2, garageActY() + bh + H * 0.022f, W * 0.023f, Draw.F_SEMI, 0xFF6E8098, Paint.Align.CENTER, 0.14f);

        // paint
        float size = garageSkinSize(), gap = W * 0.012f;
        float sl = (W - (6 * size + 5 * gap)) / 2f;
        Draw.textLS(c, "PAINT", W * 0.05f, garageSkinY() - H * 0.012f, W * 0.024f, Draw.F_SEMI, 0xFF7A8DA8, Paint.Align.LEFT, 0.2f);
        for (int k = 0; k < 6; k++) {
            int idx = carPage * 3 + k;
            float sx = sl + k * (size + gap);
            boolean owned = Store.skinOwned[idx];
            boolean sel = Store.selectedSkin == idx;
            int[] cols = Store.carColors(carPage, idx);
            Draw.rrect(c, sx, garageSkinY(), sx + size, garageSkinY() + size, size * 0.22f, 0xCC0B1220);
            Draw.rrect(c, sx + size * 0.20f, garageSkinY() + size * 0.20f, sx + size * 0.80f, garageSkinY() + size * 0.80f, size * 0.18f, cols[0]);
            Draw.rrect(c, sx + size * 0.36f, garageSkinY() + size * 0.26f, sx + size * 0.64f, garageSkinY() + size * 0.52f, size * 0.08f, 0xFF0A1420);
            Draw.rrect(c, sx + size * 0.30f, garageSkinY() + size * 0.64f, sx + size * 0.70f, garageSkinY() + size * 0.70f, 2, cols[2]);
            if (sel) Draw.glowRRect(c, sx - 2, garageSkinY() - 2, sx + size + 2, garageSkinY() + size + 2, size * 0.24f, 0xFF7CFF6B, 2.4f);
            else if (owned) Draw.rrectStroke(c, sx, garageSkinY(), sx + size, garageSkinY() + size, size * 0.22f, 0x55FFFFFF, 1.4f);
            if (!owned) {
                Draw.rrect(c, sx, garageSkinY(), sx + size, garageSkinY() + size, size * 0.22f, 0xB005070C);
                Draw.iconCoin(c, sx + size / 2, garageSkinY() + size * 0.38f, size * 0.17f, t * 3, 0);
                Draw.text(c, Draw.fmt2(Store.SKIN_PRICE[carPage]), sx + size / 2, garageSkinY() + size * 0.74f, size * 0.24f, Draw.F_BOLD, 0xFFFFD24D, Paint.Align.CENTER);
            }
        }

        // upgrades
        String[] un = {"SPEED", "NITRO", "MAGNET", "ARMOR"};
        float uw = (W - W * 0.10f - 3 * W * 0.028f) / 4f;
        float uy = garageUpgY();
        Draw.textLS(c, "UPGRADES", W * 0.05f, uy - H * 0.055f, W * 0.024f, Draw.F_SEMI, 0xFF7A8DA8, Paint.Align.LEFT, 0.2f);
        for (int i = 0; i < 4; i++) {
            float ux = W * 0.05f + i * (uw + W * 0.028f);
            int lv = Store.upg(i);
            boolean max = lv >= Store.UPG_MAX[i];
            Draw.rrect(c, ux, uy - H * 0.042f, ux + uw, uy + H * 0.050f, H * 0.016f, 0xCC0C1424);
            Draw.rrectStroke(c, ux, uy - H * 0.042f, ux + uw, uy + H * 0.050f, H * 0.016f, max ? 0x887CFF6B : 0x55FFFFFF, 1.4f);
            Draw.text(c, un[i], ux + uw / 2, uy - H * 0.020f, W * 0.022f, Draw.F_SEMI, 0xFF9FB2C8, Paint.Align.CENTER);
            for (int k = 0; k < 10; k++) {
                float pxx = ux + uw * 0.10f + k * uw * 0.081f;
                Draw.rrect(c, pxx, uy - H * 0.010f, pxx + uw * 0.055f, uy + H * 0.002f, 1.5f, k < lv ? 0xFF00E5FF : 0x33FFFFFF);
            }
            if (max) Draw.text(c, "MAX", ux + uw / 2, uy + H * 0.032f, W * 0.026f, Draw.F_BOLD, 0xFF7CFF6B, Paint.Align.CENTER);
            else {
                Draw.iconCoin(c, ux + uw * 0.28f, uy + H * 0.026f, H * 0.010f, t * 3, 0);
                Draw.text(c, Draw.fmt2(Store.upgCost(i)), ux + uw * 0.62f, uy + H * 0.034f, W * 0.026f, Draw.F_BOLD,
                        Store.coins >= Store.upgCost(i) ? 0xFFFFD24D : 0xFF7A6A4A, Paint.Align.CENTER);
            }
        }
    }

    // ================= MISSIONS =================
    void drawMissions(Canvas c) {
        drawBackground(c, 0xFFFF2E97);
        drawTopBar(c, "MISSIONS");
        Draw.textLS(c, "ENDLESS CONTRACTS · EVERY TIER GROWS", W / 2, H * 0.108f, W * 0.022f, Draw.F_SEMI, 0xFF7A8DA8, Paint.Align.CENTER, 0.14f);
        float l = W * 0.05f, w = W * 0.90f, h = missionRowH();
        int[] cols = {0xFF00E5FF, 0xFFFF2E97, 0xFFFFD24D, 0xFFA65CFF, 0xFFFF7A1A, 0xFF7CFF6B, 0xFF7CE7FF};
        for (int i = 0; i < 7; i++) {
            float ry = missionRowY(i);
            int tier = Store.bestMission[i];
            boolean claimable = missionClaimable(i);
            long tgt = Store.missionTarget(i);
            long cur = Math.min(tgt, previewProgress(i));
            Draw.rrect(c, l, ry, l + w, ry + h, H * 0.016f, claimable ? 0xCC10233A : 0xCC0C1424);
            Draw.rrectStroke(c, l, ry, l + w, ry + h, H * 0.016f, claimable ? cols[i] : 0x2EFFFFFF, claimable ? 2f : 1.2f);
            Draw.rrect(c, l + w * 0.018f, ry + h * 0.16f, l + w * 0.018f + h * 0.60f, ry + h * 0.84f, h * 0.15f,
                    Draw.alpha(cols[i], claimable ? 70 : 30));
            Draw.text(c, "" + (tier + 1), l + w * 0.018f + h * 0.30f, ry + h * 0.62f, h * 0.28f, Draw.F_HEAD, cols[i], Paint.Align.CENTER);
            Draw.text(c, Store.M_NAMES[i], l + w * 0.115f, ry + h * 0.31f, W * 0.033f, Draw.F_BOLD,
                    claimable ? 0xFFFFFFFF : 0xFF9FB2C8, Paint.Align.LEFT);
            String prog = Store.M_DESC[i].replace("%s", Draw.fmt(tgt)) + "  (" + Draw.fmt(cur) + ")";
            Draw.text(c, prog, l + w * 0.115f, ry + h * 0.585f, W * 0.0225f, Draw.F_MED, 0xFF8EA3BC, Paint.Align.LEFT);
            Draw.rrect(c, l + w * 0.115f, ry + h * 0.70f, l + w * 0.66f, ry + h * 0.775f, 3, 0x2EFFFFFF);
            float f = Draw.clamp((float) cur / tgt, 0, 1f);
            if (f > 0.01f) Draw.rrect(c, l + w * 0.115f, ry + h * 0.70f, l + w * 0.115f + w * 0.545f * f, ry + h * 0.775f, 3, cols[i]);
            if (claimable) {
                Draw.rrect(c, l + w - W * 0.235f, ry + h * 0.24f, l + w - W * 0.018f, ry + h * 0.76f, h * 0.26f, 0xFF14301F);
                Draw.rrectStroke(c, l + w - W * 0.235f, ry + h * 0.24f, l + w - W * 0.018f, ry + h * 0.76f, h * 0.26f, 0xFF7CFF6B, 1.8f);
                Draw.text(c, "+" + missionReward(i), l + w - W * 0.126f, ry + h * 0.59f, W * 0.030f, Draw.F_BOLD, 0xFF7CFF6B, Paint.Align.CENTER);
            } else {
                Draw.rrect(c, l + w - W * 0.20f, ry + h * 0.28f, l + w - W * 0.018f, ry + h * 0.72f, h * 0.22f, 0x1FFFFFFF);
                Draw.text(c, "T" + (tier + 1), l + w - W * 0.109f, ry + h * 0.58f, W * 0.026f, Draw.F_BOLD, 0xFF7A8DA8, Paint.Align.CENTER);
            }
        }
        // daily
        float dy = dailyY();
        Draw.textLS(c, "DAILY GOALS", l, dy - H * 0.014f, W * 0.028f, Draw.F_SEMI, 0xFFFFD24D, Paint.Align.LEFT, 0.16f);
        Draw.text(c, Store.streak > 0 ? ("STREAK " + Store.streak + " DAYS") : "PLAY TODAY TO START A STREAK",
                l + w, dy - H * 0.014f, W * 0.022f, Draw.F_SEMI, 0xFF7A8DA8, Paint.Align.RIGHT);
        float dw = (w - W * 0.04f) / 3f;
        String[] dn = {"DRIVE (m)", "OVERTAKE", "COINS"};
        for (int i = 0; i < 3; i++) {
            float dx = l + i * (dw + W * 0.02f);
            boolean done = dailyEarn[i] >= DAILY_GOAL[i];
            boolean claimed = dailyClaimed[i];
            Draw.rrect(c, dx, dy, dx + dw, dy + H * 0.105f, H * 0.016f, done && !claimed ? 0xCC16281C : 0xCC0C1424);
            Draw.rrectStroke(c, dx, dy, dx + dw, dy + H * 0.105f, H * 0.016f, done && !claimed ? 0xFF7CFF6B : 0x2EFFFFFF, 1.4f);
            Draw.text(c, dn[i], dx + dw / 2, dy + H * 0.026f, W * 0.023f, Draw.F_BOLD, 0xFF9FB2C8, Paint.Align.CENTER);
            Draw.text(c, Draw.fmt2(Math.min(DAILY_GOAL[i], dailyEarn[i])) + " / " + Draw.fmt2(DAILY_GOAL[i]),
                    dx + dw / 2, dy + H * 0.052f, W * 0.028f, Draw.F_BOLD, 0xFFFFFFFF, Paint.Align.CENTER);
            float f = Draw.clamp((float) dailyEarn[i] / DAILY_GOAL[i], 0, 1);
            Draw.rrect(c, dx + dw * 0.10f, dy + H * 0.062f, dx + dw * 0.90f, dy + H * 0.068f, 3, 0x2EFFFFFF);
            if (f > 0.02f) Draw.rrect(c, dx + dw * 0.10f, dy + H * 0.062f, dx + dw * 0.10f + dw * 0.80f * f, dy + H * 0.068f, 3, 0xFFFFD24D);
            if (claimed) Draw.text(c, "CLAIMED", dx + dw / 2, dy + H * 0.092f, W * 0.022f, Draw.F_BOLD, 0xFF6A7A8A, Paint.Align.CENTER);
            else if (done) Draw.text(c, "TAP +" + DAILY_REW[i], dx + dw / 2, dy + H * 0.092f, W * 0.024f, Draw.F_BOLD, 0xFF7CFF6B, Paint.Align.CENTER);
            else Draw.text(c, "+" + DAILY_REW[i], dx + dw / 2, dy + H * 0.092f, W * 0.022f, Draw.F_SEMI, 0xFF8FA3C0, Paint.Align.CENTER);
        }
    }

    long previewProgress(int i) {
        switch (i) {
            case 0: return Store.totalDist;
            case 1: return Store.totalPassed;
            case 2: return Store.totalCoinsEarned;
            case 3: return Store.totalNearMiss;
            case 4: return Store.totalNitro;
            case 5: return Store.totalCoinsEarned;
            default: return Store.totalScore;
        }
    }

    /** Reward for mission i at its current tier — grows with each tier so claiming stays worth it. */
    long missionReward(int i) {
        return Store.M_REWARD[i] + Store.M_REWARD[i] * Store.bestMission[i] / 2L;
    }

    boolean missionClaimable(int i) { return Store.claimed[i] <= Store.bestMission[i]; }

    // ================= SETTINGS =================
    void drawSettings(Canvas c) {
        drawBackground(c, 0xFF7CFF6B);
        drawTopBar(c, "SETTINGS");
        float l = W * 0.05f, w = W * 0.90f, h = H * 0.084f;
        String[] names = {"SOUND EFFECTS", "MUSIC", "VIBRATION", "GRAPHICS"};
        String[] subs = {"Coins, nitro, crashes", "Synthwave background loop", "Haptic feedback", "LOW = smoother on old phones"};
        boolean[] vals = {Store.sfxOn, Store.musicOn, Store.hapticsOn, Store.quality > 0.5f};
        int[] cols = {0xFF00E5FF, 0xFFA65CFF, 0xFFFF6FD8, 0xFF7CFF6B};
        for (int i = 0; i < 4; i++) {
            float ry = setRowY(i);
            Draw.rrect(c, l, ry, l + w, ry + h, H * 0.016f, 0xCC0C1424);
            Draw.rrectStroke(c, l, ry, l + w, ry + h, H * 0.016f, Draw.alpha(cols[i], 80), 1.4f);
            Draw.text(c, names[i], l + W * 0.035f, ry + h * 0.45f, W * 0.033f, Draw.F_BOLD, 0xFFFFFFFF, Paint.Align.LEFT);
            Draw.text(c, subs[i], l + W * 0.035f, ry + h * 0.75f, W * 0.021f, Draw.F_MED, 0xFF7A8DA8, Paint.Align.LEFT);
            float tw = W * 0.14f, tx = l + w - tw - W * 0.03f, ty = ry + h / 2 - H * 0.015f;
            Draw.rrect(c, tx, ty, tx + tw, ty + H * 0.030f, H * 0.015f, vals[i] ? Draw.alpha(cols[i], 230) : 0x3AFFFFFF);
            float knobX = vals[i] ? tx + tw - H * 0.014f : tx + H * 0.014f;
            Draw.p.setColor(0xFFFFFFFF);
            c.drawCircle(knobX, ty + H * 0.015f, H * 0.012f, Draw.p);
        }
        drawButton(c, l, setRowY(4) + H * 0.01f, w, h, "RESET PROGRESS", 0xFFFF2E97, 0xFFA30FA6, false);
        drawButton(c, l, setRowY(4) + H * 0.01f + h + H * 0.016f, w, h, "ABOUT", 0xFF4A6280, 0xFF8FA3C0, false);
        Draw.textLS(c, "NITRO DRIVE · v1.0 · OFFLINE", W / 2, H * 0.955f, W * 0.022f, Draw.F_SEMI, 0xFF55647A, Paint.Align.CENTER, 0.14f);
    }

    // ================= CREDITS =================
    void drawCredits(Canvas c) {
        drawBackground(c, 0xFF00E5FF);
        drawTopBar(c, "ABOUT");
        float y = H * 0.22f;
        Draw.textGlow(c, "NITRO DRIVE", W / 2, y, W * 0.075f, Draw.F_HEAD, 0xFFFFFFFF, 0xFFFF2E97, 12);
        y += H * 0.045f;
        Draw.textLS(c, "ENDLESS NEON RACER · v1.0", W / 2, y, W * 0.024f, Draw.F_SEMI, 0xFF7CE7FF, Paint.Align.CENTER, 0.14f);
        String[] lines = {
                "100% offline. No ads. No tracking.",
                "The app requests no internet permission.",
                "",
                "Every car, road, sound and effect",
                "is generated in code at runtime.",
                "",
                "Fonts: Audiowide & Rajdhani",
                "(SIL Open Font License)",
                "",
                "Made with Arena.ai · 2026"
        };
        y += H * 0.065f;
        for (String s : lines) {
            Draw.text(c, s, W / 2, y, W * 0.030f, Draw.F_MED, 0xFFB8C6D8, Paint.Align.CENTER);
            y += H * 0.031f;
        }
        Draw.textGlow(c, "DRIVE FAST. STAY ENDLESS.", W / 2, H * 0.90f, W * 0.036f, Draw.F_HEAD, 0xFFFFE14D, 0xFFFF7A1A, 10);
    }
}
