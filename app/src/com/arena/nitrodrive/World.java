package com.arena.nitrodrive;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** One endless run: simulation + world rendering. */
public class World {
    public static final int LANES = 5;
    private final GameView gv;
    private final Random rnd = new Random();

    // screen
    float W;
    float H;
    float laneW;
    float roadL;
    float roadR;
    float horizonY;
    float py;

    // run state
    public boolean over;
    public boolean wrecked;
    public boolean overNotified;
    public boolean paused;
    public float crashShake;
    public int level = 1;
    public float levelDist;
    public float levelGoal;
    public float meters;
    public float speedPx;
    public float speedKmh;
    public long score;
    public int lives;
    public float invuln;
    public float nitro;
    public boolean nitroActive;
    public float nitroT;
    public int coinRun;
    public int passRun;
    public int nearRun;
    public int nitroUses;
    public int levelsDone;
    public int coinMul = 1;
    public float coinMulT;
    public float magnetT;
    public float shieldT;
    public int comboShown;
    public float comboT;
    public float comboMax;
    public float carLane = 2f;
    public float carLaneVis = 2f;
    public float tilt;
    public float tick;
    public float bannerT;
    public float bannerBig;
    public String bannerText = "";
    public int totalEarned;

    // tables
    static final int MAXT = 22;
    static final int MAXC = 90;
    static final int MAXP = 10;
    static final int MAXPART = 200;
    static final int MAXTXT = 24;
    final boolean[] tOn = new boolean[MAXT];
    final float[] tLane = new float[MAXT];
    final float[] tY = new float[MAXT];
    final float[] tRel = new float[MAXT];
    final float[] tScale = new float[MAXT];
    final int[] tType = new int[MAXT];
    final int[] tCol = new int[MAXT];
    final boolean[] tPass = new boolean[MAXT];
    final boolean[] cOn = new boolean[MAXC];
    final float[] cLane = new float[MAXC];
    final float[] cY = new float[MAXC];
    final float[] cSpin = new float[MAXC];
    final boolean[] cAttract = new boolean[MAXC];
    final boolean[] pOn = new boolean[MAXP];
    final float[] pLane = new float[MAXP];
    final float[] pY = new float[MAXP];
    final int[] pType = new int[MAXP];
    final float[] paX = new float[MAXPART];
    final float[] paY = new float[MAXPART];
    final float[] paVX = new float[MAXPART];
    final float[] paVY = new float[MAXPART];
    final float[] paL = new float[MAXPART];
    final float[] paM = new float[MAXPART];
    final float[] paSize = new float[MAXPART];
    final int[] paCol = new int[MAXPART];
    final int[] paKind = new int[MAXPART];
    final String[] txS = new String[MAXTXT];
    final float[] txX = new float[MAXTXT];
    final float[] txY = new float[MAXTXT];
    final float[] txL = new float[MAXTXT];
    final int[] txCol = new int[MAXTXT];

    // scenery
    float scrollFar;
    float scrollMid;
    float dashOff;
    float stripeOff;
    float sideOff;
    final float[] bldX = new float[26];
    final float[] bldW = new float[26];
    final float[] bldH = new float[26];
    final int[] bldC = new int[26];
    final int[] bldWin = new int[26];
    final float[] bldX2 = new float[30];
    final float[] bldW2 = new float[30];
    final float[] bldH2 = new float[30];
    final int[] bldC2 = new int[30];
    float[] starX = new float[50];
    float[] starY = new float[50];
    float[] starP = new float[50];

    float spawnAcc;
    float gapBase;
    int nextType;
    Store.Theme TH;
    int carIdx;
    int[] carCol;

    public World(GameView gv) { this.gv = gv; }

    public void resize(float w, float h) {
        W = w; H = h;
        laneW = W / (LANES + 0.7f);
        roadL = (W - laneW * LANES) / 2f;
        roadR = roadL + laneW * LANES;
        horizonY = H * 0.30f;
        py = H * 0.805f;
        rnd.setSeed(20261124L);
        for (int i = 0; i < 26; i++) {
            bldW[i] = W * (0.06f + rnd.nextFloat() * 0.09f);
            bldX[i] = rnd.nextFloat() * W * 1.3f - W * 0.15f;
            bldH[i] = H * (0.08f + rnd.nextFloat() * 0.19f);
            bldC[i] = 0xFF11162B + ((rnd.nextInt(3) - 1) * 0x000A0A0A);
            bldWin[i] = rnd.nextInt(1000);
        }
        for (int i = 0; i < 30; i++) {
            bldW2[i] = W * (0.045f + rnd.nextFloat() * 0.075f);
            bldX2[i] = rnd.nextFloat() * W * 1.3f - W * 0.15f;
            bldH2[i] = H * (0.07f + rnd.nextFloat() * 0.17f);
            bldC2[i] = 0xFF0B0F1E;
        }
        for (int i = 0; i < 50; i++) {
            starX[i] = rnd.nextFloat() * W; starY[i] = rnd.nextFloat() * horizonY * 0.9f;
            starP[i] = rnd.nextFloat() * 6.28f;
        }
    }

    public void start() {
        over = false; wrecked = false; overNotified = false; paused = false;
        level = 1; levelDist = 0; levelGoal = 5200; meters = 0;
        speedPx = 520; speedKmh = 90; score = 0;
        carIdx = (int) Store.selectedCar;
        carCol = Store.carColors(carIdx, Store.selectedSkin);
        lives = 3 + (Store.upgArmor >= 4 ? 1 : 0);
        invuln = 1.2f; nitro = Store.upgNitro >= 5 ? 0.35f : 0.2f;
        nitroActive = false; nitroT = 0;
        coinRun = 0; passRun = 0; nearRun = 0; nitroUses = 0; levelsDone = 0; totalEarned = 0;
        coinMul = 1; coinMulT = 0; magnetT = 0; shieldT = 0; comboShown = 0; comboT = 0;
        carLane = 2f; carLaneVis = 2f; tilt = 0; tick = 0; bannerT = 0; bannerBig = 0;
        for (int i = 0; i < MAXT; i++) tOn[i] = false;
        for (int i = 0; i < MAXC; i++) cOn[i] = false;
        for (int i = 0; i < MAXP; i++) pOn[i] = false;
        for (int i = 0; i < MAXPART; i++) paL[i] = 0;
        for (int i = 0; i < MAXTXT; i++) txL[i] = 0;
        TH = Store.theme(level);
        spawnAcc = 0; gapBase = 620; nextType = 0;
        banner("GO!", 1.0f, true);
        Sfx.goSfx();
    }

    public void tapLane(boolean right) {
        if (over || crashed()) return;
        float nl = Draw.clamp(carLane + (right ? 1 : -1), 0, LANES - 1);
        if (nl != carLane) { carLane = nl; gv.haptic(8); }
    }
    public void steerTo(float lane) { carLane = Draw.clamp(lane, 0, LANES - 1); }

    public boolean crashed() { return wrecked; }
    public boolean tOnPublic(int i) { return tOn[i]; }
    public float laneOf(int i) { return tLane[i]; }
    public float yOf(int i) { return tY[i]; }
    public float horizon() { return horizonY; }
    public float playerY() { return py; }
    public float laneWidth() { return laneW; }
    public int combo() { return comboShown; }

    float speedBase() {
        return (430 + Store.upgSpeed * 26) * Store.CARS[carIdx].topSpeedMul;
    }

    public void update(float dt) {
        if (over) { updateParticles(dt, true); return; }
        if (paused) return;
        tick += dt;
        TH = Store.theme(level);
        if (bannerT > 0) bannerT -= dt;

        // ---- speed ----
        float target = speedBase() + Math.min(level, 40) * 14f;
        if (nitroActive) target *= 1.55f;
        speedPx += (target - speedPx) * Math.min(1, dt * (nitroActive ? 3.2f : 1.4f));
        speedKmh = 40 + speedPx * 0.145f;
        if (meters < 1) speedKmh = Math.max(speedKmh, 80);
        meters += speedPx * dt * 0.06f;
        score += (long) (speedPx * dt * 0.06f * (nitroActive ? 3 : 1) * (1 + comboShown * 0.05f));
        levelDist += speedPx * dt;

        // ---- steering ----
        float steer = Store.CARS[carIdx].handling;
        carLaneVis += (carLane - carLaneVis) * Math.min(1, dt * 11f * steer);
        tilt += ((carLane - carLaneVis) * 0.16f - tilt) * Math.min(1, dt * 10f);
        if (invuln > 0) invuln -= dt;
        if (comboT > 0) { comboT -= dt; if (comboT <= 0) { comboShown = 0; comboMax = 0; } }

        // ---- nitro ----
        if (nitroActive) {
            nitro -= dt * 0.30f;
            nitroT -= dt;
            if (nitro <= 0 || nitroT <= 0) { nitroActive = false; }
            if (rnd.nextFloat() < 0.9f) addParticle(playerX() + (rnd.nextFloat() - 0.5f) * laneW * 0.7f,
                    py + laneW * 0.5f, (rnd.nextFloat() - 0.5f) * 60, 320 + rnd.nextFloat() * 200,
                    rnd.nextFloat() * 0.4f + 0.2f, 0xFF37E9FF, laneW * 0.05f, 0);
        } else {
            nitro = Math.min(1, nitro + dt * 0.028f);
        }

        if (magnetT > 0) magnetT -= dt;
        if (shieldT > 0) shieldT -= dt;
        if (coinMulT > 0) { coinMulT -= dt; if (coinMulT <= 0) coinMul = 1; }
        if (crashShake > 0) crashShake -= dt * 2.2f;

        // ---- spawn ----
        gapBase = Math.max(360, 800 - level * 22);
        spawnAcc += speedPx * dt;
        while (spawnAcc > gapBase) {
            spawnAcc -= gapBase;
            spawnRow();
        }

        float scroll = speedPx * dt;
        scrollFar += scroll * 0.06f;
        scrollMid += scroll * 0.14f;
        dashOff += scroll;
        stripeOff += scroll * 1.2f;
        sideOff += scroll;

        // ---- traffic ----
        for (int i = 0; i < MAXT; i++) {
            if (!tOn[i]) continue;
            float dy = scroll * (1 - tRel[i]);
            tY[i] += dy;
            if (tType[i] == 2 && rnd.nextFloat() < dt * 0.35f) {
                tLane[i] = Draw.clamp(tLane[i] + (rnd.nextBoolean() ? 1 : -1) * 0, 0, LANES - 1);
            }
            float scaleAt = depth(tY[i]);
            float carW2 = laneW * 0.80f * scaleAt;
            float carL2 = carW2 * 1.85f;
            // pass / near miss
            if (!tPass[i] && tY[i] > py + carL2 * 0.1f) {
                tPass[i] = true;
                if (Math.abs(tLane[i] - carLaneVis) < 1.05f) {
                    nearRun++;
                    score += 60;
                    addText("NEAR MISS!", playerX(), py - 10, 0xFF7CE7FF);
                    Sfx.nearSfx();
                    comboInc();
                    addScoreCoins(0);
                } else {
                    passRun++;
                    score += 25;
                }
            }
            // collide
            if (!over && !nitroActive && tY[i] > py - carL2 * 0.62f && tY[i] < py + carL2 * 0.62f
                    && Math.abs(tLane[i] - carLaneVis) < 0.54f) {
                if (invuln <= 0) hitTraffic(i);
            }
            if (tY[i] > H + carL2) tOn[i] = false;
        }

        // ---- coins ----
        for (int i = 0; i < MAXC; i++) {
            if (!cOn[i]) continue;
            cY[i] += scroll;
            cSpin[i] += dt * 6;
            boolean mag = magnetT > 0;
            if (mag) {
                float dx = (carLaneVis - cLane[i]) * laneW;
                float dyy = cY[i] - py;
                float d = (float) Math.sqrt(dx * dx + dyy * dyy);
                if (d < laneW * 6) {
                    cLane[i] += (carLaneVis - cLane[i]) * Math.min(1, dt * 4.5f);
                    cY[i] += (py - cY[i]) * Math.min(1, dt * 3.2f);
                }
            }
            if (Math.abs(cLane[i] - carLaneVis) < 0.55f + (mag ? 3.5f : 0) && Math.abs(cY[i] - py) < laneW * (mag ? 3.2f : 0.55f)) {
                cOn[i] = false;
                int v = coinMul;
                coinRun += v;
                totalEarned += v;
                score += 15 * v;
                Sfx.coinSfx();
                gv.haptic(4);
                addParticle(cX(cLane[i], py), py, 0, -80, 0.4f, 0xFFFFD24D, laneW * 0.07f, 1);
                comboInc();
                if (comboShown > 0 && comboShown % 5 == 0) addScoreCoins(20 * v);
            }
            if (cY[i] > H + 60) cOn[i] = false;
        }

        // ---- pickups ----
        for (int i = 0; i < MAXP; i++) {
            if (!pOn[i]) continue;
            pY[i] += scroll;
            if (Math.abs(pLane[i] - carLaneVis) < 0.66f && Math.abs(pY[i] - py) < laneW * 0.7f) {
                pOn[i] = false;
                applyPickup(pType[i]);
            }
            if (pY[i] > H + 80) pOn[i] = false;
        }

        // ---- level ----
        if (levelDist >= levelGoal) {
            levelDist -= levelGoal;
            level++;
            levelsDone++;
            levelGoal = 4600 + level * 950;
            int bonus = 40 + level * 30;
            coinRun += bonus;
            totalEarned += bonus;
            score += 400 * level;
            Sfx.levelUpPlay();
            gv.haptic(30);
            banner("LEVEL " + level, 1.6f, true);
            if (level % 3 == 0 && lives < 2 + (Store.upgArmor >= 4 ? 1 : 0)) {
                lives++;
                addText("+1 SPARE", playerX(), py - laneW, 0xFF6BFF9C);
            }
            addBurst(W / 2, H * 0.42f, TH.neon, 26);
        }

        updateParticles(dt, false);
        if (over && !overNotified) { overNotified = true; gv.onRunOver(); }
    }

    void hitTraffic(int i) {
        tOn[i] = false;
        if (shieldT > 0) {
            shieldT = 0;
            invuln = 1.4f;
            Sfx.shieldSfx();
            banner("SHIELD SAVED YOU", 1.2f, false);
            addBurst(cX(tLane[i], py), py, 0xFF7CE7FF, 18);
            gv.haptic(18);
            return;
        }
        lives--;
        Sfx.crashSfx();
        gv.haptic(120);
        crashShake = 1f;
        addBurst(playerX(), py, 0xFFFF8A50, 26);
        addBurst(playerX(), py, 0xFFFFE14D, 16);
        speedPx *= 0.34f;
        comboShown = 0; comboT = 0;
        if (lives <= 0) {
            wrecked = true;
            over = true;
            for (int k = 0; k < 4; k++) addBurst(playerX() + (rnd.nextFloat() - 0.5f) * laneW, py, k % 2 == 0 ? 0xFFFF6B3D : 0xFFFFE14D, 20);
        } else {
            invuln = 2.0f;
            banner(lives == 1 ? "DAMAGE! LAST CHANCE" : "DAMAGE!", 1.1f, false);
        }
    }

    void applyPickup(int t) {
        switch (t) {
            case 0:
                nitro = Math.min(1f, nitro + 0.5f);
                banner("NITRO +50%", 0.9f, true);
                Sfx.powerSfx();
                break;
            case 1:
                magnetT = 7f;
                banner("COIN MAGNET", 1.0f, true);
                Sfx.powerSfx();
                break;
            case 2:
                shieldT = 9f;
                banner("SHIELD UP", 1.0f, true);
                Sfx.shieldSfx();
                break;
            case 3:
                coinMul = 2;
                coinMulT = 8f;
                banner("DOUBLE COINS", 1.0f, true);
                Sfx.powerSfx();
                break;
            default:
                coinRun += 60;
                totalEarned += 60;
                addText("+60", playerX(), py - 30, 0xFFFFD24D);
                Sfx.coinSfx();
        }
        gv.haptic(12);
        addBurst(playerX(), py, TH.neon, 16);
    }

    void comboInc() {
        if (comboT > 0) comboShown++; else comboShown = 1;
        comboT = 2.6f;
        comboMax = Math.max(comboMax, comboShown);
        if (comboShown == 5 || comboShown == 10 || comboShown == 20 || (comboShown > 20 && comboShown % 10 == 0)) {
            if (comboShown <= 20) addText("COMBO x" + comboShown + "!", W / 2, py - laneW * 1.6f, 0xFFFFE14D);
            addScoreCoins(comboShown * 3);
        }
    }

    void addScoreCoins(int n) {
        coinRun += n;
        totalEarned += n;
    }

    // ---------- spawning ----------
    void spawnRow() {
        float roll = rnd.nextFloat();
        int lvl = Math.min(level, 30);
        if (roll < 0.20f) { spawnCoins(); return; }
        if (roll < 0.30f && level >= 3) { spawnPickup(); return; }
        if (roll < 0.40f && level >= 7) { spawnDouble(); return; }
        spawnCarRow(roll < 0.86f ? 1 : 2, lvl);
    }

    int freeLane() { return rnd.nextInt(LANES); }

    void spawnCarRow(int count, int lvl) {
        int free = freeLane();
        int t = 0;
        boolean truck = rnd.nextFloat() < (0.10f + lvl * 0.008f);
        for (int i = 0; i < MAXT && t < count; i++) {
            if (tOn[i]) continue;
            int lane = rnd.nextInt(LANES);
            if (lane == free) continue;
            float spawnY = -laneW * (2.2f + rnd.nextFloat() * 1.4f);
            if (laneBusy(lane, spawnY) || laneBusy(lane, spawnY - laneW * 3f)) continue;
            tOn[i] = true;
            tLane[i] = lane;
            tY[i] = -laneW * (2.2f + rnd.nextFloat() * 1.4f);
            tRel[i] = truck ? 0.42f + rnd.nextFloat() * 0.15f : 0.35f + rnd.nextFloat() * 0.42f;
            tType[i] = truck ? 1 : (rnd.nextFloat() < 0.14f ? 2 : 0);
            tCol[i] = 0xFF000000 | (0x30 + rnd.nextInt(0xC0)) << 16 | (0x20 + rnd.nextInt(0xA0)) << 8 | (0x30 + rnd.nextInt(0xC0));
            if (tType[i] == 2) tCol[i] = 0xFFF5F7FA;
            tPass[i] = false;
            tScale[i] = truck ? 1.35f : 1f;
            t++;
        }
    }

    void spawnDouble() {
        int l1 = rnd.nextInt(LANES);
        int l2 = rnd.nextInt(LANES);
        if (l1 == l2) return;
        spawnAt(l1, 1); spawnAt(l2, 0);
    }
    boolean laneBusy(int lane, float y) {
        for (int j = 0; j < MAXT; j++) {
            if (tOn[j] && Math.abs(tLane[j] - lane) < 0.9f && Math.abs(tY[j] - y) < laneW * 2.8f) return true;
        }
        return false;
    }

    void spawnAt(int lane, int type) {
        if (laneBusy(lane, -laneW * 2.6f)) return;
        for (int i = 0; i < MAXT; i++) {
            if (tOn[i]) continue;
            tOn[i] = true;
            tLane[i] = lane;
            tY[i] = -laneW * (2.2f + rnd.nextFloat());
            tRel[i] = type == 1 ? 0.55f : 0.4f + rnd.nextFloat() * 0.3f;
            tType[i] = type;
            tCol[i] = 0xFF000000 | (0x30 + rnd.nextInt(0xC0)) << 16 | (0x20 + rnd.nextInt(0xA0)) << 8 | (0x30 + rnd.nextInt(0xC0));
            tPass[i] = false;
            tScale[i] = type == 1 ? 1.35f : 0.9f;
            return;
        }
    }

    void spawnCoins() {
        int lane = rnd.nextInt(LANES);
        int n = 4 + rnd.nextInt(4);
        boolean arc = rnd.nextBoolean();
        float startY = -laneW * 2.5f;
        int placed = 0;
        for (int i = 0; i < MAXC && placed < n; i++) {
            if (cOn[i]) continue;
            cOn[i] = true;
            cLane[i] = lane + (arc ? (float) Math.sin(placed * 0.9f) * 0.8f : 0);
            cY[i] = startY - placed * laneW * 0.95f;
            cSpin[i] = rnd.nextFloat() * 6;
            cAttract[i] = false;
            placed++;
        }
    }
    void spawnPickup() {
        int type = rnd.nextInt(5);
        int lane = rnd.nextInt(LANES);
        for (int i = 0; i < MAXP; i++) {
            if (pOn[i]) continue;
            pOn[i] = true;
            pLane[i] = lane;
            pY[i] = -laneW * 3f;
            pType[i] = type;
            return;
        }
    }

    // ---------- particles ----------
    public void addParticle(float x, float y, float vx, float vy, float life, int col, float size, int kind) {
        for (int i = 0; i < MAXPART; i++) {
            if (paL[i] > 0) continue;
            paX[i] = x; paY[i] = y; paVX[i] = vx; paVY[i] = vy;
            paL[i] = life; paM[i] = life; paCol[i] = col; paSize[i] = size; paKind[i] = kind;
            return;
        }
    }
    public void addBurst(float x, float y, int col, int n) {
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * 6.283f;
            float sp = 120 + rnd.nextFloat() * 340;
            addParticle(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    0.35f + rnd.nextFloat() * 0.5f, col, laneW * (0.02f + rnd.nextFloat() * 0.05f), 0);
        }
    }
    public void addText(String s, float x, float y, int col) {
        for (int i = 0; i < MAXTXT; i++) {
            if (txL[i] > 0) continue;
            txS[i] = s; txX[i] = x; txY[i] = y; txL[i] = 1.1f; txCol[i] = col;
            return;
        }
    }
    void updateParticles(float dt, boolean frozen) {
        for (int i = 0; i < MAXPART; i++) {
            if (paL[i] <= 0) continue;
            paL[i] -= dt;
            paX[i] += paVX[i] * dt;
            paY[i] += paVY[i] * dt;
            paVY[i] += 260 * dt;
            paVX[i] *= (1 - dt * 1.5f);
        }
        if (frozen) return;
        for (int i = 0; i < MAXTXT; i++) {
            if (txL[i] <= 0) continue;
            txL[i] -= dt;
            txY[i] -= 55 * dt;
        }
    }

    void banner(String s, float t, boolean big) { bannerText = s; bannerT = t; bannerBig = big ? 1 : 0; }

    // ---------- geometry ----------
    float playerX() { return cX(carLaneVis, py); }
    float cX(float lane, float y) {
        float l = roadL + laneW * (lane + 0.5f);
        float k = depth(y);
        float center = W / 2f;
        return center + (l - center) * (0.28f + 0.72f * k);
    }
    float depth(float y) {
        float d = (y - horizonY) / (py - horizonY);
        d = Draw.clamp(d, 0f, 1.25f);
        return 0.30f + 0.70f * d;
    }

    /** Render scale: 0 exactly at the horizon so sprites never pop in above it. */
    float scaleAt(float y) {
        float d = (y - horizonY) / (py - horizonY);
        return Draw.clamp(d, 0f, 1.3f);
    }
    float laneWidthAt(float y) { return laneW * (0.28f + 0.72f * depth(y)); }

    // ---------- render ----------
    public void draw(Canvas c) {
        drawSky(c);
        drawRoad(c);
        for (int i = 0; i < MAXC; i++) if (cOn[i]) drawCoin(c, i);
        for (int i = 0; i < MAXT; i++) if (tOn[i]) drawTraffic(c, i);
        for (int i = 0; i < MAXP; i++) if (pOn[i]) drawPickup(c, i);
        drawPlayer(c);
        drawParticles(c);
        drawTexts(c);
        drawVignette(c);
    }

    void drawSky(Canvas c) {
        // --- sky gradient ---
        Draw.sh(new LinearGradient(0, 0, 0, horizonY * 1.12f, TH.skyTop, TH.skyBot, Shader.TileMode.CLAMP));
        Draw.p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, horizonY * 1.12f, Draw.p);
        Draw.shClear();
        // --- stars ---
        for (int i = 0; i < 50; i++) {
            float tw = 0.3f + 0.7f * (float) Math.abs(Math.sin(tick * 1.3f + starP[i]));
            Draw.p.setColor(Draw.alpha(0xFFFFFFFF, (int) (70 * tw)));
            c.drawCircle(starX[i], starY[i], 1.4f, Draw.p);
        }
        // --- neon sun with scan bands ---
        float ox = W * 0.5f, oy = horizonY * 0.62f, orad = W * 0.20f;
        Draw.sh(new RadialGradient(ox, oy, orad * 2.6f, Draw.alpha(TH.neon2, 90), 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(ox, oy, orad * 2.6f, Draw.p);
        Draw.shClear();
        Draw.p.setColor(Draw.alpha(TH.neon2, 235));
        c.drawCircle(ox, oy, orad, Draw.p);
        Draw.p.setColor(Draw.alpha(TH.neon, 200));
        c.drawCircle(ox, oy, orad * 0.72f, Draw.p);
        for (int b = 0; b < 5; b++) {
            float by = oy - orad + orad * 0.55f * b + 6;
            Draw.p.setColor(Draw.alpha(TH.skyTop, 170));
            c.drawRect(ox - orad, by, ox + orad, by + orad * (0.06f + b * 0.03f), Draw.p);
        }
        // --- far skyline ---
        for (int i = 0; i < 30; i++) {
            float x = mod(bldX2[i] - scrollMid * 0.35f, W * 1.3f) - W * 0.15f;
            float top = horizonY - bldH2[i] * 0.8f;
            Draw.rrect(c, x, top, x + bldW2[i], horizonY + 2, 2, bldC2[i]);
            Draw.p.setColor(Draw.alpha(TH.neon2, 70));
            c.drawRect(x, top, x + bldW2[i], top + 2, Draw.p);
        }
        // --- near skyline: bodies with neon window strips ---
        for (int i = 0; i < 26; i++) {
            float x = mod(bldX[i] - scrollMid * 0.9f, W * 1.3f) - W * 0.15f;
            float top = horizonY - bldH[i];
            int bodyCol = Draw.lerpColor(0xFF0A0F1E, TH.neon, 0.10f + (bldWin[i] % 5) * 0.02f);
            Draw.rrect(c, x, top, x + bldW[i], horizonY + 3, 3, bodyCol);
            // side shaded face
            Draw.p.setColor(0x33000000);
            c.drawRect(x + bldW[i] * 0.62f, top, x + bldW[i], horizonY + 3, Draw.p);
            // roof neon
            Draw.p.setColor(Draw.alpha(TH.neon, 200));
            c.drawRect(x, top, x + bldW[i], top + 2.5f, Draw.p);
            // antenna
            if (bldWin[i] % 4 == 0) {
                Draw.line(c, x + bldW[i] * 0.5f, top, x + bldW[i] * 0.5f, top - H * 0.022f, Draw.alpha(TH.neon2, 160), 1.5f);
                Draw.p.setColor(Draw.alpha(TH.neon2, 220));
                c.drawCircle(x + bldW[i] * 0.5f, top - H * 0.022f, 2.2f, Draw.p);
            }
            // window strips
            int seed = bldWin[i];
            int cols = Math.max(2, (int) (bldW[i] / (W * 0.020f)));
            int rows = Math.max(3, (int) (bldH[i] / (H * 0.013f)));
            for (int cy = 0; cy < rows; cy++) {
                int lit = (seed + cy * 37) % 10;
                if (lit > 6) continue;
                int col = lit < 3 ? TH.neon : (lit < 5 ? TH.neon2 : 0xFFFFD27D);
                Draw.p.setColor(Draw.alpha(col, 60 + lit * 16));
                float wy = top + bldH[i] * (cy + 0.5f) / rows;
                float wx = x + bldW[i] * 0.08f;
                float ww = bldW[i] * 0.84f;
                if (lit < 3) c.drawRect(wx, wy, wx + ww, wy + 1.6f, Draw.p);
                else {
                    for (int cx = 0; cx < cols; cx++) {
                        if ((seed + cx * 13 + cy * 7) % 3 == 0) continue;
                        float px2 = x + bldW[i] * (cx + 0.5f) / cols;
                        c.drawRect(px2 - 1.6f, wy - 1.6f, px2 + 1.6f, wy + 1.6f, Draw.p);
                    }
                }
            }
        }
        // --- horizon haze ---
        Draw.sh(new LinearGradient(0, horizonY - H * 0.05f, 0, horizonY + H * 0.02f,
                Draw.alpha(TH.neon, 0), Draw.alpha(TH.neon, 70), Shader.TileMode.CLAMP));
        Draw.p.setStyle(Paint.Style.FILL);
        c.drawRect(0, horizonY - H * 0.05f, W, horizonY + H * 0.02f, Draw.p);
        Draw.shClear();
        Draw.line(c, 0, horizonY, W, horizonY, Draw.alpha(TH.neon, 170), 2f);
    }

    void drawRoad(Canvas c) {
        // --- ground ---
        Draw.sh(new LinearGradient(0, horizonY, 0, H, TH.ground2, TH.ground1, Shader.TileMode.CLAMP));
        Draw.p.setStyle(Paint.Style.FILL);
        c.drawRect(0, horizonY, W, H, Draw.p);
        Draw.shClear();

        // --- roadside city blocks flying past ---
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 6; k++) {
                float phase = ((sideOff * 0.0018f + k / 6f + side * 0.11f) % 1f);
                float y = horizonY + (H - horizonY) * phase * phase;
                float scl = 0.28f + 0.72f * depth(y);
                float bwid = W * 0.20f * scl;
                float bhei = Math.min(W * 0.42f * scl, y - horizonY - 2);
                if (bhei < 6) continue;
                float bx = side == 0 ? roadL - bwid * 0.42f : roadR + bwid * 0.42f;
                Draw.rrect(c, bx - bwid / 2, y - bhei, bx + bwid / 2, y, W * 0.014f * scl,
                        Draw.lerpColor(0xFF0C1120, TH.neon, 0.06f));
                Draw.p.setColor(Draw.alpha(TH.neon2, (int) (30 + 120 * scl)));
                c.drawRect(bx - bwid * 0.44f, y - bhei, bx + bwid * 0.44f, y - bhei + Math.max(1.5f, 3 * scl), Draw.p);
                Draw.p.setColor(Draw.alpha(TH.neon, (int) (26 + 80 * scl)));
                for (int wv = 0; wv < 4; wv++) {
                    float wy = y - bhei * (0.16f + wv * 0.22f);
                    c.drawRect(bx - bwid * 0.32f, wy, bx + bwid * 0.32f, wy + Math.max(1f, 2.6f * scl), Draw.p);
                }
            }
        }

        // --- asphalt ---
        Draw.path.reset();
        float tl = W / 2f + (roadL - W / 2f) * 0.28f, tr = W / 2f + (roadR - W / 2f) * 0.28f;
        Draw.path.moveTo(tl, horizonY);
        Draw.path.lineTo(tr, horizonY);
        Draw.path.lineTo(roadR, H);
        Draw.path.lineTo(roadL, H);
        Draw.path.close();
        Draw.sh(new LinearGradient(0, horizonY, 0, H, 0xFF1B2334, 0xFF0B0E16, Shader.TileMode.CLAMP));
        Draw.p.setColor(0xFF000000);
        c.drawPath(Draw.path, Draw.p);
        Draw.shClear();

        // --- light pools on the asphalt ---
        for (int k = 0; k < 5; k++) {
            float phase = ((dashOff * 0.0011f + k / 5f) % 1f);
            float y = horizonY + (H - horizonY) * phase * phase;
            if (y < horizonY + H * 0.05f) continue;
            float rr = laneW * 2.2f * depth(y);
            Draw.sh(new RadialGradient(W / 2f, y, rr, Draw.alpha(0xFF9FD8FF, (int) (26 * depth(y))), 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, y - rr, W, y + rr, Draw.p);
            Draw.shClear();
        }

        // --- lane dashes ---
        for (int l = 1; l < LANES; l++) {
            for (int k = 0; k < 26; k++) {
                float phase = (dashOff * 0.00085f + k / 26f) % 1f;
                float yb = horizonY + (H - horizonY) * phase * phase;
                float yt = yb - (10 + 60 * phase * phase);
                if (yb < horizonY + 8) continue;
                float xb = W / 2f + (roadL + laneW * l - W / 2f) * (0.28f + 0.72f * depth(yb));
                float xt = W / 2f + (roadL + laneW * l - W / 2f) * (0.28f + 0.72f * depth(yt));
                Draw.s.setStyle(Paint.Style.FILL);
                Draw.s.setShader(null);
                Draw.s.setColor(Draw.alpha(0xFFCFE6FF, (int) (60 + 150 * phase)));
                Draw.s.setStrokeWidth(Math.max(1.6f, 5f * depth(yb)));
                c.drawLine(xt, yt, xb, yb, Draw.s);
            }
        }

        // --- neon road edges + guardrail pylons ---
        for (int side = 0; side < 2; side++) {
            float xe = side == 0 ? roadL : roadR;
            float xt = W / 2f + (xe - W / 2f) * 0.28f;
            int edgeCol = side == 0 ? TH.neon : TH.neon2;
            Draw.s.setStyle(Paint.Style.STROKE);
            Draw.s.setShader(null);
            Draw.s.setColor(Draw.alpha(edgeCol, 55));
            Draw.s.setStrokeWidth(W * 0.030f);
            c.drawLine(xt, horizonY, xe, H, Draw.s);
            Draw.s.setColor(edgeCol);
            Draw.s.setStrokeWidth(W * 0.007f);
            c.drawLine(xt, horizonY, xe, H, Draw.s);
            Draw.s.setColor(0xCCFFFFFF);
            Draw.s.setStrokeWidth(W * 0.0022f);
            c.drawLine(xt, horizonY, xe, H, Draw.s);
            for (int k = 0; k < 9; k++) {
                float phase = ((sideOff * 0.0013f + k / 9f) % 1f);
                float y = horizonY + (H - horizonY) * phase * phase;
                if (y < horizonY + 12) continue;
                float x = W / 2f + (xe - W / 2f) * (0.28f + 0.72f * depth(y));
                float r = W * 0.007f * depth(y) * 2.2f;
                // lamp post
                Draw.line(c, x + (side == 0 ? -1 : 1) * r * 2.4f, y, x + (side == 0 ? -1 : 1) * r * 2.4f, y - r * 9f, Draw.alpha(0xFFFFFFFF, 90), Math.max(1f, r * 0.5f));
                Draw.p.setColor(Draw.alpha(0xFFDFF6FF, 200));
                c.drawCircle(x + (side == 0 ? -1 : 1) * r * 2.4f, y - r * 9f, Math.max(1.2f, r * 1.1f), Draw.p);
                Draw.sh(new RadialGradient(x + (side == 0 ? -1 : 1) * r * 2.4f, y - r * 9f, r * 6f, 0x33DFF6FF, 0x00000000, Shader.TileMode.CLAMP));
                c.drawCircle(x + (side == 0 ? -1 : 1) * r * 2.4f, y - r * 9f, r * 6f, Draw.p);
                Draw.shClear();
                // base glow
                Draw.p.setColor(Draw.alpha(edgeCol, 70));
                c.drawCircle(x, y, r * 2.6f, Draw.p);
                Draw.p.setColor(edgeCol);
                c.drawCircle(x, y, r, Draw.p);
            }
        }
    }

    void drawTraffic(Canvas c, int i) {
        float y = tY[i];
        if (y < horizonY + 4) return;
        float sc = scaleAt(y) * 0.55f + 0.45f * depth(y);
        sc = Draw.clamp(sc / 1.0f, 0.02f, 1.3f);
        float fade = Draw.clamp((y - horizonY) / (H * 0.10f), 0f, 1f);
        float w = laneW * 0.80f * tScale[i] * (0.30f + 0.70f * Draw.clamp((y - horizonY) / (py - horizonY), 0f, 1.25f));
        int a = (int) (255 * fade);
        float x = cX(tLane[i], y);
        if (tType[i] == 1) drawTruck(c, x, y, w);
        else if (tType[i] == 2) drawPolice(c, x, y, w);
        else CarArt.draw(c, x, y, w, tCol[i], Draw.darken(tCol[i], 0.45f), 0xFF222A38, 0, 0, 0, false, 0);
    }

    void drawTruck(Canvas c, float x, float y, float w) {
        float l = w * 2.5f;
        Draw.rrect(c, x - w / 2, y - l / 2, x + w / 2, y + l / 2, w * 0.10f, 0xFF23262F);
        Draw.rrect(c, x - w * 0.44f, y - l * 0.46f, x + w * 0.44f, y + l * 0.10f, w * 0.06f, 0xFF6E7686);
        Draw.rrect(c, x - w * 0.42f, y + l * 0.12f, x + w * 0.42f, y + l * 0.46f, w * 0.06f, 0xFF3A3F4C);
        Draw.p.setColor(0x33FFFFFF);
        c.drawRect(x - w * 0.36f, y + l * 0.16f, x + w * 0.36f, y + l * 0.20f, Draw.p);
        Draw.p.setColor(0xFFFF5A4D);
        c.drawCircle(x - w * 0.30f, y + l * 0.44f, w * 0.05f, Draw.p);
        c.drawCircle(x + w * 0.30f, y + l * 0.44f, w * 0.05f, Draw.p);
        // headlights
        Draw.p.setColor(0x66FFF3C4);
        c.drawCircle(x - w * 0.30f, y - l * 0.44f, w * 0.16f, Draw.p);
        c.drawCircle(x + w * 0.30f, y - l * 0.44f, w * 0.16f, Draw.p);
        Draw.p.setColor(0xFFFFF3C4);
        c.drawRect(x - w * 0.38f, y - l * 0.47f, x - w * 0.22f, y - l * 0.44f, Draw.p);
        c.drawRect(x + w * 0.22f, y - l * 0.47f, x + w * 0.38f, y - l * 0.44f, Draw.p);
        // wheels
        Draw.p.setColor(0xFF0B0C10);
        c.drawRect(x - w * 0.56f, y - l * 0.28f, x - w * 0.42f, y - l * 0.12f, Draw.p);
        c.drawRect(x + w * 0.42f, y - l * 0.28f, x + w * 0.56f, y - l * 0.12f, Draw.p);
        c.drawRect(x - w * 0.56f, y + l * 0.18f, x - w * 0.42f, y + l * 0.34f, Draw.p);
        c.drawRect(x + w * 0.42f, y + l * 0.18f, x + w * 0.56f, y + l * 0.34f, Draw.p);
    }

    void drawPolice(Canvas c, float x, float y, float w) {
        CarArt.draw(c, x, y, w, 0xFFF2F5FA, 0xFF1B2430, 0xFF2A3A55, 2, 0, 0, false, 0);
        boolean red = ((int) (tick * 8) % 2) == 0;
        Draw.p.setColor(red ? 0xFFFF2D55 : 0xFF2D6BFF);
        float ly = y - w * 0.42f;
        Draw.sh(new RadialGradient(x - w * 0.12f, ly, w * 0.45f, red ? 0xAAFF2D55 : 0x00000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(x - w * 0.12f, ly, w * 0.45f, Draw.p);
        Draw.sh(null);
        Draw.rrect(c, x - w * 0.28f, ly - w * 0.03f, x - w * 0.02f, ly + w * 0.03f, 2, red ? 0xFFFF2D55 : 0xFF5A2030);
        Draw.rrect(c, x + w * 0.02f, ly - w * 0.03f, x + w * 0.28f, ly + w * 0.03f, 2, red ? 0xFF20305A : 0xFF2D6BFF);
    }

    void drawCoin(Canvas c, int i) {
        float y = cY[i];
        if (y < horizonY + 6) return;
        float sc = depth(y);
        float r = laneW * 0.17f * sc;
        if (r < 1) return;
        Draw.p.setColor(0x44FFD24D);
        c.drawCircle(cX(cLane[i], y), y + r * 0.2f, r * 1.7f, Draw.p);
        Draw.iconCoin(c, cX(cLane[i], y), y, r, cSpin[i], 0);
    }

    void drawPickup(Canvas c, int i) {
        float y = pY[i];
        if (y < horizonY + 8) return;
        float sc = depth(y);
        float r = laneW * 0.24f * sc;
        float x = cX(pLane[i], y);
        int col = pickupColor(pType[i]);
        Draw.p.setColor(Draw.alpha(col, 70));
        c.drawCircle(x, y, r * 1.8f, Draw.p);
        Draw.rrect(c, x - r, y - r, x + r, y + r, r * 0.28f, 0xDD0B1020);
        Draw.glowRRect(c, x - r, y - r, x + r, y + r, r * 0.28f, col, Math.max(1.5f, r * 0.09f));
        if (pType[i] == 0) Draw.iconBolt(c, x, y, r * 1.3f, col);
        else if (pType[i] == 1) Draw.iconStar(c, x, y, r * 0.72f, r * 0.3f, col);
        else if (pType[i] == 2) Draw.iconShield(c, x, y, r * 1.25f, col, true);
        else if (pType[i] == 3) {
            Draw.text(c, "2", x - r * 0.28f, y + r * 0.5f, r * 1.3f, Draw.F_HEAD, col, Paint.Align.CENTER);
            Draw.iconCoin(c, x + r * 0.36f, y, r * 0.42f, tick * 4, 0);
        } else Draw.iconGift(c, x, y, r * 1.3f, col);
    }
    int pickupColor(int t) {
        switch (t) { case 0: return 0xFF37E9FF; case 1: return 0xFFFFE14D; case 2: return 0xFF7CFF6B;
            case 3: return 0xFFFF6FD8; default: return 0xFFFFB020; }
    }

    void drawPlayer(Canvas c) {
        if (over) return;
        float x = playerX();
        float y = py;
        float w = laneW * 0.92f;
        boolean blink = invuln > 0 && ((int) (tick * 12) % 2 == 0);
        // shadow
        Draw.p.setColor(0x66000000);
        c.drawOval(x - w * 0.62f, y - laneW * 0.62f, x + w * 0.62f, y + laneW * 0.72f, Draw.p);
        // nitro flames
        if (nitroActive) {
            float f = 0.85f + (float) Math.sin(tick * 40) * 0.15f;
            Draw.sh(new LinearGradient(0, y + w * 0.55f, 0, y + w * 0.55f + laneW * 1.1f * f,
                    Draw.alpha(0xFF37E9FF, 200), 0x00FF2E97, Shader.TileMode.CLAMP));
            Draw.p.setStyle(Paint.Style.FILL);
            Draw.path.reset();
            Draw.path.moveTo(x - w * 0.24f, y + w * 0.5f);
            Draw.path.lineTo(x + w * 0.24f, y + w * 0.5f);
            Draw.path.lineTo(x, y + w * 0.5f + laneW * 1.1f * f);
            Draw.path.close();
            c.drawPath(Draw.path, Draw.p);
            Draw.sh(null);
        }
        // underglow
        Draw.sh(new RadialGradient(x, y, w * 0.95f, Draw.alpha(carCol[2], 90), 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, w * 0.95f, Draw.p);
        Draw.sh(null);
        if (blink) Draw.p.setAlpha(120);
        CarArt.draw(c, x, y, w, carCol[0], carCol[1], carCol[2], 3, (int) Store.selectedCar, tilt, false, 0);
        if (blink) Draw.p.setAlpha(255);
        if (shieldT > 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 5);
            Draw.s.setStyle(Paint.Style.STROKE);
            Draw.s.setColor(Draw.alpha(0xFF7CE7FF, 120 + (int) (80 * pulse)));
            Draw.s.setStrokeWidth(3 + 2 * pulse);
            c.drawCircle(x, y, w * 0.95f + 8 + 4 * pulse, Draw.s);
        }
        // speed streaks
        float spd01 = Draw.clamp((speedKmh - 110f) / 130f, 0f, 1f);
        if (spd01 > 0.03f || nitroActive) {
            float k = nitroActive ? 1f : spd01;
            Draw.s.setStyle(Paint.Style.STROKE);
            Draw.s.setStrokeWidth(2f);
            for (int i = 0; i < 10; i++) {
                float yy = ((tick * (700 + speedPx) + i * H * 0.137f) % H);
                float xx = W * ((i * 0.37f) % 1f);
                Draw.s.setColor(Draw.alpha(nitroActive ? 0xFF37E9FF : 0xFFCFE6FF, (int) (60 * k)));
                c.drawLine(xx, yy, xx, yy + H * 0.05f * k, Draw.s);
            }
        }
    }

    void drawParticles(Canvas c) {
        for (int i = 0; i < MAXPART; i++) {
            if (paL[i] <= 0) continue;
            float f = paL[i] / paM[i];
            Draw.p.setColor(Draw.alpha(paCol[i], (int) (230 * f)));
            c.drawCircle(paX[i], paY[i], paSize[i] * (0.6f + f * 0.8f), Draw.p);
            Draw.p.setColor(Draw.alpha(paCol[i], (int) (70 * f)));
            c.drawCircle(paX[i], paY[i], paSize[i] * 2.4f * f, Draw.p);
        }
    }

    void drawTexts(Canvas c) {
        for (int i = 0; i < MAXTXT; i++) {
            if (txL[i] <= 0) continue;
            float a = Math.min(1, txL[i] / 0.4f);
            Draw.textGlow(c, txS[i], txX[i], txY[i], W * 0.045f, Draw.F_HEAD, Draw.alpha(txCol[i], (int) (255 * a)), Draw.alpha(txCol[i], (int) (140 * a)), 10);
        }
    }

    void drawVignette(Canvas c) {
        Draw.sh(new RadialGradient(W / 2, H * 0.55f, H * 0.65f, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        Draw.p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, H, Draw.p);
        Draw.sh(null);
        if (crashShake > 0) {
            Draw.p.setColor(Draw.alpha(0xFFFF3030, (int) (80 * crashShake)));
            c.drawRect(0, 0, W, H, Draw.p);
        }
    }

    static float mod(float a, float m) { a = a % m; return a < 0 ? a + m : a; }
}
