package com.arena.nitrodrive;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Tiny procedural synthwave loop, generated once and hardware-looped. */
public class Music {
    private static AudioTrack track;
    private static volatile boolean playing;
    static boolean ready;
    private static final int SR = 22050;

    public static void init() {
        if (track != null) return;
        Thread t = new Thread(() -> {
            try {
                short[] buf = build();
                AudioTrack.Builder b = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(buf.length * 2);
                AudioTrack at = b.build();
                at.write(buf, 0, buf.length);
                at.setLoopPoints(0, buf.length, -1);
                track = at;
                ready = true;
                if (playing) at.play();
            } catch (Throwable ignored) { }
        }, "music-gen");
        t.setDaemon(true);
        t.start();
    }

    public static void setEnabled(boolean on) {
        playing = on && Store.musicOn;
        try {
            if (track == null) return;
            if (playing) track.play(); else track.pause();
        } catch (Throwable ignored) { }
    }
    public static void start() { setEnabled(true); }
    public static void stop() { setEnabled(false); }

    /** Builds a ~7s synthwave loop: kick, bass, arp, pad, hats. */
    static short[] build() {
        double bpm = 118;
        double beat = 60.0 / bpm;
        int bars = 4;
        int beatsPerBar = 4;
        double total = bars * beatsPerBar * beat;
        int n = (int) (total * SR);
        double[] out = new double[n];

        int[] bass = {0, 0, 3, 0, 5, 0, 3, 0};       // semitone offsets in scale
        int root = 45;                                // A2
        double[] scale = {0, 3, 5, 7, 10};
        int[] arp = {0, 1, 2, 3, 4, 3, 2, 1};

        for (int step = 0; step < bars * 16; step++) {
            double t0 = step * beat / 4.0;
            int s16 = step % 16;
            // kick on every beat
            if (s16 % 4 == 0) addKick(out, t0);
            // hat on off-beats
            if (s16 % 2 == 1) addHat(out, t0, 0.05);
            // bass 8ths
            if (s16 % 2 == 0) {
                int idx = (step / 2) % bass.length;
                int note = root + bass[idx];
                addNote(out, t0, beat * 0.46, mtof(note), 0.20, "saw", 0.03);
            }
            // arp 16ths (filtered square)
            int a = arp[step % arp.length];
            int note2 = root + 24 + (int) scale[a];
            addNote(out, t0, beat * 0.24, mtof(note2), 0.045, "sq", 0.01);
            // pad chord each bar
            if (s16 == 0) {
                int bar = step / 16;
                int chordRoot = root + 12 + (bar % 2 == 0 ? 0 : 5);
                for (int k = 0; k < 3; k++) {
                    int nt = chordRoot + (int) scale[(k * 2) % scale.length] + (k == 2 ? 12 : 0);
                    addNote(out, t0, beat * 3.9, mtof(nt), 0.035, "tri", 0.0);
                }
            }
        }
        // gentle limiter
        double peak = 0.0001;
        for (double v : out) peak = Math.max(peak, Math.abs(v));
        double g = 0.80 / peak;
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            double fade = Math.min(1, Math.min(i / (SR * 0.05), (n - i) / (SR * 0.05)));
            pcm[i] = (short) (Draw.clamp((float) (out[i] * g * fade), -1f, 1f) * 32767);
        }
        return pcm;
    }

    static double mtof(int n) { return 440.0 * Math.pow(2, (n - 69) / 12.0); }

    static void addKick(double[] out, double t0) {
        int start = (int) (t0 * SR);
        int len = (int) (SR * 0.18);
        for (int i = 0; i < len && start + i < out.length; i++) {
            double t = (double) i / SR;
            double f = 120 * Math.exp(-t * 18) + 42;
            double env = Math.exp(-t * 14);
            out[start + i] += Math.sin(2 * Math.PI * f * t) * env * 0.55;
        }
    }
    static void addHat(double[] out, double t0, double vol) {
        int start = (int) (t0 * SR);
        int len = (int) (SR * 0.03);
        java.util.Random r = new java.util.Random((long) (t0 * 1000));
        double prev = 0;
        for (int i = 0; i < len && start + i < out.length; i++) {
            double t = (double) i / SR;
            double nz = r.nextDouble() * 2 - 1;
            double hp = nz - prev;
            prev = nz;
            out[start + i] += hp * Math.exp(-t * 60) * vol;
        }
    }
    static void addNote(double[] out, double t0, double dur, double freq, double vol, String type, double noise) {
        int start = (int) (t0 * SR);
        int len = (int) (SR * dur);
        double ph = 0;
        for (int i = 0; i < len && start + i < out.length; i++) {
            double t = (double) i / SR;
            ph += 2 * Math.PI * freq / SR;
            double v;
            if (type.equals("sq")) v = Math.sin(ph) >= 0 ? 1 : -1;
            else if (type.equals("saw")) v = 2 * ((ph / (2 * Math.PI)) % 1) - 1;
            else v = Math.sin(ph);
            if (noise > 0) v = v * (1 - noise) + (Math.random() * 2 - 1) * noise;
            double env = Math.min(1, t / 0.01) * Math.exp(-t * (type.equals("tri") ? 1.2 : 6.0));
            double rel = Math.min(1, (dur - t) / 0.03);
            out[start + i] += v * vol * env * rel;
        }
    }
}
