package com.arena.nitrodrive;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.SoundPool;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/** All audio is synthesized at startup — zero asset files, zero licensing. */
public class Sfx {
    public static boolean enabled = true;
    private static SoundPool pool;
    private static int iCoin;
    private static int iHit;
    private static int iPass;
    private static int iNear;
    private static int iClick;
    private static int iPower;
    private static int iBuy;
    private static int iCrash;
    private static int iShield;
    private static int iLevel;
    private static int iCount;
    private static int iGo;
    private static AudioTrack engine;
    private static boolean engineOn;
    private static Thread engineThread;
    private static volatile float engFreq = 70;
    private static volatile float engVol = 0;
    private static final Random R = new Random();
    private static final int SR = 22050;

    // ---------- primitive wave synthesis ----------
    static double[] osc(double freq, double dur, String type, double vol, double detune, double noiseAmt) {
        int n = (int) (dur * SR);
        double[] o = new double[n];
        double ph = 0;
        double ph2 = 0;
        for (int i = 0; i < n; i++) {
            double tt = (double) i / SR;
            ph += 2 * Math.PI * freq / SR;
            ph2 += 2 * Math.PI * freq * (1 + detune) / SR;
            double v;
            switch (type) {
                case "sq": v = Math.sin(ph) >= 0 ? 1 : -1; break;
                case "saw": { double x = (ph / (2 * Math.PI)) % 1; v = 2 * x - 1; break; }
                case "tri": { double x = (ph / (2 * Math.PI)) % 1; v = 4 * Math.abs(x - 0.5) - 1; break; }
                case "noise": v = R.nextDouble() * 2 - 1; break;
                default: v = Math.sin(ph);
            }
            if (noiseAmt > 0) v = v * (1 - noiseAmt) + noiseAmt * (R.nextDouble() * 2 - 1);
            o[i] = v * vol;
        }
        return o;
    }
    static void sweep(double[] o, double f0, double f1, String type, double vol) {
        double ph = 0;
        for (int i = 0; i < o.length; i++) {
            double f = f0 + (f1 - f0) * (double) i / o.length;
            ph += 2 * Math.PI * f / SR;
            double v;
            switch (type) {
                case "sq": v = Math.sin(ph) >= 0 ? 1 : -1; break;
                case "saw": v = 2 * ((ph / (2 * Math.PI)) % 1) - 1; break;
                default: v = Math.sin(ph);
            }
            o[i] += v * vol;
        }
    }
    static void env(double[] o, double a, double d, double s, double relFrac) {
        int n = o.length;
        int ai = Math.max(1, (int) (a * SR));
        int di = Math.max(1, (int) (d * SR));
        for (int i = 0; i < n; i++) {
            double e;
            if (i < ai) e = (double) i / ai;
            else if (i < ai + di) e = 1 - (1 - s) * (double) (i - ai) / di;
            else e = s;
            double rel = (double) (n - i) / (n * relFrac);
            if (rel < 1) e *= rel;
            o[i] *= e;
        }
    }
    static void mix(double[] dst, double[] src, double vol) {
        if (src.length > dst.length) {
            for (int i = 0; i < dst.length; i++) dst[i] += src[i] * vol;
        } else {
            for (int i = 0; i < src.length; i++) dst[i] += src[i] * vol;
        }
    }
    static void limit(double[] o) {
        double peak = 0.0001;
        for (double v : o) peak = Math.max(peak, Math.abs(v));
        double g = Math.min(1.0, 0.92 / peak);
        for (int i = 0; i < o.length; i++) o[i] *= g;
    }
    static byte[] pcm(double[] o) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(o.length * 2);
        for (double v : o) {
            int s = (int) (Draw.clamp((float) v, -1f, 1f) * 32767);
            bo.write(s & 0xFF); bo.write((s >> 8) & 0xFF);
        }
        return bo.toByteArray();
    }
    static byte[] wav(double[] o) {
        byte[] pcm = pcm(o);
        int dataLen = pcm.length;
        byte[] w = new byte[44 + dataLen];
        int fileLen = 36 + dataLen;
        w[0] = 'R'; w[1] = 'I'; w[2] = 'F'; w[3] = 'F';
        w[4] = (byte) fileLen; w[5] = (byte) (fileLen >> 8); w[6] = (byte) (fileLen >> 16); w[7] = (byte) (fileLen >> 24);
        w[8] = 'W'; w[9] = 'A'; w[10] = 'V'; w[11] = 'E';
        w[12] = 'f'; w[13] = 'm'; w[14] = 't'; w[15] = ' ';
        w[16] = 16; w[20] = 1; w[22] = 1;
        int sr = SR;
        int br = SR * 2;
        w[24] = (byte) sr; w[25] = (byte) (sr >> 8); w[26] = (byte) (sr >> 16); w[27] = (byte) (sr >> 24);
        w[28] = (byte) br; w[29] = (byte) (br >> 8); w[30] = (byte) (br >> 16); w[31] = (byte) (br >> 24);
        w[32] = 2; w[34] = 16;
        w[36] = 'd'; w[37] = 'a'; w[38] = 't'; w[39] = 'a';
        w[40] = (byte) dataLen; w[41] = (byte) (dataLen >> 8); w[42] = (byte) (dataLen >> 16); w[43] = (byte) (dataLen >> 24);
        System.arraycopy(pcm, 0, w, 44, dataLen);
        return w;
    }

    // ---------- loading ----------
    public static void init(Context ctx) {
        try {
            pool = new SoundPool.Builder().setMaxStreams(8)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            iCoin = loadWav(ctx, "coin", coin());
            iHit = loadWav(ctx, "hit", hit());
            iPass = loadWav(ctx, "pass", pass());
            iNear = loadWav(ctx, "near", near());
            iClick = loadWav(ctx, "click", click());
            iPower = loadWav(ctx, "power", power());
            iBuy = iPower;
            iCrash = loadWav(ctx, "crash", crash());
            iShield = loadWav(ctx, "shield", shield());
            iLevel = loadWav(ctx, "level", levelUpSfx());
            iCount = loadWav(ctx, "count", beep(660, 0.10));
            iGo = loadWav(ctx, "go", beep(1180, 0.30));
            buildEngine();
        } catch (Throwable ignored) { }
    }

    private static int loadWav(Context ctx, String name, double[] samples) {
        try {
            java.io.File f = new java.io.File(ctx.getCacheDir(), "nd_" + name + ".wav");
            if (!f.exists() || f.length() < 100) {
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                fo.write(wav(samples));
                fo.close();
            }
            return pool.load(f.getAbsolutePath(), 1);
        } catch (Throwable t) {
            return 0;
        }
    }

    static double[] coin() {
        double[] o = new double[(int) (SR * 0.22)];
        mix(o, osc(988, 0.22, "sq", 0.16, 0, 0.35), 1);
        double[] b = osc(1568, 0.16, "sq", 0.14, 0, 0.25);
        for (int i = 0; i < b.length && i + 1100 < o.length; i++) o[i + 1100] += b[i];
        env(o, 0.001, 0.02, 0.8, 0.35); limit(o); return o;
    }
    static double[] beep(double f, double d) {
        double[] o = new double[(int) (SR * d)];
        mix(o, osc(f, d, "tri", 0.5, 0.01, 0.05), 1);
        env(o, 0.004, 0.02, 0.85, 0.3); limit(o); return o;
    }
    static double[] pass() {
        double[] o = new double[(int) (SR * 0.18)];
        sweep(o, 280, 820, "tri", 0.28);
        env(o, 0.005, d(0.03), 0.4, 0.5); limit(o); return o;
    }
    static double[] near() {
        double[] o = new double[(int) (SR * 0.16)];
        sweep(o, 900, 300, "saw", 0.12);
        env(o, 0.002, 0.03, 0.2, 0.6); limit(o); return o;
    }
    static double[] power() {
        double[] o = new double[(int) (SR * 0.5)];
        sweep(o, 220, 1040, "sq", 0.18);
        sweep(o, 221, 1560, "tri", 0.12);
        env(o, 0.01, 0.10, 0.55, 0.5); limit(o); return o;
    }
    static double[] shield() {
        double[] o = new double[(int) (SR * 0.35)];
        mix(o, osc(660, 0.35, "tri", 0.22, 0.02, 0), 1);
        mix(o, osc(990, 0.35, "tri", 0.14, 0.02, 0), 1);
        env(o, 0.005, 0.06, 0.3, 0.6); limit(o); return o;
    }
    static double[] hit() {
        double[] o = new double[(int) (SR * 0.32)];
        mix(o, osc(150, 0.32, "noise", 0.4, 0, 1), 1);
        sweep(o, 200, 40, "sq", 0.30);
        env(o, 0.001, 0.08, 0.25, 0.5); limit(o); return o;
    }
    static double[] crash() {
        double[] o = new double[(int) (SR * 0.8)];
        mix(o, osc(120, 0.8, "noise", 0.55, 0, 1), 1);
        sweep(o, 160, 30, "saw", 0.35);
        mix(o, osc(70, 0.8, "sq", 0.2, -0.01, 0.3), 1);
        env(o, 0.002, 0.3, 0.15, 0.6); limit(o); return o;
    }
    static double[] click() {
        double[] o = new double[(int) (SR * 0.06)];
        mix(o, osc(1200, 0.06, "tri", 0.35, 0, 0.1), 1);
        env(o, 0.001, 0.02, 0.2, 0.4); limit(o); return o;
    }
    static double[] levelUpSfx() {
        double[] o = new double[(int) (SR * 0.9)];
        double[] nt = {523, 659, 784, 1046};
        for (int k = 0; k < nt.length; k++) {
            double[] b = osc(nt[k], 0.28, "sq", 0.13, 0.005, 0.2);
            env(b, 0.004, 0.05, 0.6, 0.5);
            int off = (int) (SR * 0.11 * k);
            for (int i = 0; i < b.length && i + off < o.length; i++) o[i + off] += b[i];
        }
        limit(o); return o;
    }
    static double d(double s) { return s; }

    static void buildEngine() {
        final int n = SR * 2;
        final short[] buf = new short[n];
        engine = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(n * 2).build();
        engine.play();
        engine.setLoopPoints(0, n, -1);
        engineThread = new Thread(() -> {
            Random r = new Random();
            double ph = 0;
            double ph2 = 0;
            while (true) {
                float f = engFreq;
                float v = engVol;
                if (v < 0.002f) { try { Thread.sleep(30); } catch (Exception e) { return; } continue; }
                for (int i = 0; i < n; i++) {
                    ph += 2 * Math.PI * f / SR;
                    ph2 += 2 * Math.PI * f * 0.5 / SR;
                    double saw = 2 * ((ph / (2 * Math.PI)) % 1) - 1;
                    double saw2 = 2 * ((ph2 / (2 * Math.PI)) % 1) - 1;
                    double noise = (r.nextDouble() - 0.5) * 0.16;
                    double val = saw * 0.55 + saw2 * 0.30 + noise;
                    val += Math.sin(ph * 2) * 0.10;
                    buf[i] = (short) (val * v * 9000);
                }
                try { engine.write(buf, 0, n); } catch (Exception e) { return; }
            }
        });
        engineThread.setDaemon(true);
        engineThread.start();
        engineOn = true;
    }

    public static void setEngine(float freq, float vol) {
        if (!enabled) vol = 0;
        engFreq = Draw.clamp(freq, 40, 240);
        engVol = Draw.clamp(vol, 0, 1);
    }
    static void play(int id, float vol, float pitch) {
        if (!enabled || pool == null) return;
        try { pool.play(id, vol, vol, 1, 0, pitch); } catch (Throwable ignored) { }
    }
    public static void coinSfx() { play(iCoin, 0.55f, 1 + (float) Math.random() * 0.12f - 0.06f); }
    public static void hitSfx() { play(iHit, 0.8f, 1); }
    public static void passSfx() { play(iPass, 0.45f, 1); }
    public static void nearSfx() { play(iNear, 0.30f, 1); }
    public static void clickSfx() { play(iClick, 0.5f, 1); }
    public static void powerSfx() { play(iPower, 0.7f, 1); }
    public static void buySfx() { play(iBuy, 0.8f, 0.9f); }
    public static void crashSfx() { play(iCrash, 0.9f, 1); }
    public static void shieldSfx() { play(iShield, 0.6f, 1); }
    public static void levelUpPlay() { play(iLevel, 0.7f, 1); }
    public static void countdownSfx() { play(iCount, 0.6f, 1); }
    public static void goSfx() { play(iGo, 0.7f, 1); }
}
