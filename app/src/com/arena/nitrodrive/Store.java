package com.arena.nitrodrive;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent save + all game content tables (cars, themes, missions, upgrades). */
public class Store {
    public static SharedPreferences sp;

    public static long coins;
    public static long bestDist;
    public static long totalDist;
    public static long totalCoinsEarned;
    public static long runCount;
    public static long selectedCar;
    public static long totalPassed;
    public static long totalNearMiss;
    public static long totalNitro;
    public static long totalScore;
    public static int upgSpeed;
    public static int upgNitro;
    public static int upgMagnet;
    public static int upgArmor;
    public static boolean sfxOn = true;
    public static boolean musicOn = true;
    public static boolean hapticsOn = true;
    public static float quality = 1f;
    public static boolean[] unlocked = new boolean[6];
    public static int[] stars = new int[6];
    public static int[] bestMission = new int[7];
    public static long[] claimed = new long[7];
    public static String missionDay = "";
    public static int streak;
    public static boolean[] skinOwned = new boolean[18];
    public static int selectedSkin;

    public static final int[] UPG_COST_BASE = {120, 150, 180, 140};
    public static final int[] UPG_MAX = {10, 10, 10, 10};

    public static final class Car {
        public String name;
        public String tagline;
        public String power;
        public int body;
        public int body2;
        public int accent;
        public int glass;
        public long price;
        public float topSpeedMul;
        public float handling;
        public float nitroPow;
        public Car(String n, String tag, String pow, int body, int body2, int accent, int glass,
                   long price, float ts, float h, float np) {
            this.name = n;
                   tagline = tag; power = pow;
            this.body = body; this.body2 = body2; this.accent = accent; this.glass = glass;
            this.price = price; topSpeedMul = ts; handling = h; nitroPow = np;
        }
    }

    public static final Car[] CARS = {
            new Car("APEX GT", "Where legends begin", "SNAP  ·  GRIP", 0xFFFF2E97, 0xFF9C1B63, 0xFF00E5FF, 0xFF13202E,
                    0, 1.00f, 1.00f, 1.00f),
            new Car("VOLT X", "Silent. Deadly. Electric.", "ZAP  ·  PULSE", 0xFF00E5FF, 0xFF0A7D8C, 0xFF7CFF6B, 0xFF0D2233,
                    900, 1.10f, 1.05f, 1.12f),
            new Car("FUEGO RS", "Molten asphalt ahead", "SUN  ·  BLAZE", 0xFFFF7A1A, 0xFFA63E02, 0xFFFFE14D, 0xFF2A1608,
                    2000, 1.20f, 1.12f, 1.20f),
            new Car("VIPER-9", "Strike before they see you", "VENOM  ·  FANG", 0xFF35F59A, 0xFF0B7A4C, 0xFFF6FF6B, 0xFF0A231B,
                    3800, 1.32f, 1.20f, 1.28f),
            new Car("HELLCAT V12", "Dominate the night", "CLAW  ·  ROAR", 0xFFA65CFF, 0xFF4E1B93, 0xFFFF6FD8, 0xFF180D2A,
                    6500, 1.45f, 1.28f, 1.38f),
            new Car("PHANTOM X", "Ghost through everything", "VOID  ·  WRAITH", 0xFFE8F6FF, 0xFF3D5A73, 0xFF00E5FF, 0xFF101C26,
                    11000, 1.60f, 1.38f, 1.50f)
    };

    /** Endless biome themes — rotate every 4 levels. */
    public static final class Theme {
        public String name;
        public int skyTop;
        public int skyBot;
        public int ground1;
        public int ground2;
        public int neon;
        public int neon2;
        public int lane;
        public Theme(String n, int st, int sb, int g1, int g2, int ne, int ne2, int ln) {
            name = n; skyTop = st; skyBot = sb; ground1 = g1; ground2 = g2; neon = ne; neon2 = ne2; lane = ln;
        }
    }
    public static final Theme[] THEMES = {
            new Theme("NEON CITY", 0xFF070B1A, 0xFF1B1140, 0xFF10101E, 0xFF171730, 0xFF00E5FF, 0xFFFF2E97, 0xFF3A4258),
            new Theme("SUNSET STRIP", 0xFF1A0B24, 0xFF7A1E4B, 0xFF241021, 0xFF321827, 0xFFFF7A1A, 0xFFFFE14D, 0xFF4A3050),
            new Theme("EMERALD RUN", 0xFF03140F, 0xFF0A3A2A, 0xFF0A1A14, 0xFF12251C, 0xFF35F59A, 0xFFB7FF5C, 0xFF2C4A3A),
            new Theme("ICE CIRCUIT", 0xFF06111F, 0xFF123E66, 0xFF0B1622, 0xFF152434, 0xFF7CE7FF, 0xFFC7F0FF, 0xFF33506B),
            new Theme("MAGMA CORE", 0xFF1A0505, 0xFF5C1208, 0xFF1B0A08, 0xFF2A120C, 0xFFFF3B30, 0xFFFFB020, 0xFF5A2A20),
            new Theme("MIDNIGHT PURPLE", 0xFF0D0518, 0xFF3A0F66, 0xFF150A26, 0xFF201038, 0xFFA65CFF, 0xFFFF6FD8, 0xFF4A3568)
    };
    public static Theme theme(int level) { return THEMES[((level - 1) / 4) % THEMES.length]; }

    /** 7 endless mission chains — each grows forever. */
    public static final String[] M_NAMES = {
            "Road Warrior", "Speed Demon", "Coin Rush", "Untouchable", "Nitro Fiend", "High Roller", "Night Rider"};
    public static final String[] M_DESC = {
            "Drive %s total", "Pass %s cars", "Grab %s coins", "Nitro %s cars", "Use nitro %s times", "Earn %s coins", "Score %s points"};
    public static final long[] M_BASE = {1500, 8, 90, 4, 2, 500, 1500};
    public static final float[] M_GROW = {1.22f, 1.18f, 1.20f, 1.22f, 1.20f, 1.22f, 1.25f};
    public static final long[] M_REWARD = {100, 140, 160, 180, 150, 120, 220};

    public static String today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return "" + c.get(java.util.Calendar.YEAR) + (c.get(java.util.Calendar.MONTH) + 1) + c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    public static long missionTarget(int i) {
        long t = M_BASE[i];
        for (int k = 0; k < bestMission[i]; k++) t = (long) Math.ceil(t * M_GROW[i]);
        return t;
    }

    public static int upgCost(int kind) {
        int lvl = upg(kind);
        return UPG_COST_BASE[kind] + lvl * (int) Math.round(UPG_COST_BASE[kind] * 0.85);
    }
    public static int upg(int kind) {
        switch (kind) { case 0: return upgSpeed; case 1: return upgNitro; case 2: return upgMagnet; default: return upgArmor; }
    }

    public static void load(Context c) {
        sp = c.getSharedPreferences("nitrodrive", Context.MODE_PRIVATE);
        coins = sp.getLong("coins", 500);
        totalPassed = sp.getLong("tPassed", 0);
        totalNearMiss = sp.getLong("tNear", 0);
        totalNitro = sp.getLong("tNitro", 0);
        totalScore = sp.getLong("tScore", 0);
        bestDist = sp.getLong("bestDist", 0);
        totalDist = sp.getLong("totalDist", 0);
        totalCoinsEarned = sp.getLong("totalCoins", 0);
        runCount = sp.getLong("runs", 0);
        selectedCar = sp.getLong("car", 0);
        upgSpeed = sp.getInt("u0", 0); upgNitro = sp.getInt("u1", 0);
        upgMagnet = sp.getInt("u2", 0); upgArmor = sp.getInt("u3", 0);
        sfxOn = sp.getBoolean("sfx", true);
        musicOn = sp.getBoolean("music", true);
        hapticsOn = sp.getBoolean("hap", true);
        quality = sp.getFloat("qual", 1f);
        streak = sp.getInt("streak", 0);
        missionDay = sp.getString("mday", "");
        selectedSkin = sp.getInt("skin", 0);
        unlocked[0] = true;
        for (int i = 1; i < 6; i++) unlocked[i] = sp.getBoolean("uCar" + i, false);
        for (int i = 0; i < 6; i++) stars[i] = sp.getInt("star" + i, 0);
        for (int i = 0; i < 7; i++) { bestMission[i] = sp.getInt("m" + i, 0); claimed[i] = sp.getLong("md" + i, 0); }
        for (int i = 0; i < 18; i++) skinOwned[i] = sp.getBoolean("sk" + i, i < 6);
        if (selectedSkin < 0 || selectedSkin >= 18) selectedSkin = 0;
    }
    public static void save() {
        if (sp == null) return;
        SharedPreferences.Editor e = sp.edit();
        e.putLong("coins", coins).putLong("bestDist", bestDist).putLong("totalDist", totalDist)
                .putLong("tPassed", totalPassed).putLong("tNear", totalNearMiss).putLong("tNitro", totalNitro).putLong("tScore", totalScore)
                .putLong("totalCoins", totalCoinsEarned).putLong("runs", runCount).putLong("car", selectedCar)
                .putInt("u0", upgSpeed).putInt("u1", upgNitro).putInt("u2", upgMagnet).putInt("u3", upgArmor)
                .putBoolean("sfx", sfxOn).putBoolean("music", musicOn).putBoolean("hap", hapticsOn)
                .putFloat("qual", quality).putInt("streak", streak).putString("mday", missionDay)
                .putInt("skin", selectedSkin);
        for (int i = 1; i < 6; i++) e.putBoolean("uCar" + i, unlocked[i]);
        for (int i = 0; i < 6; i++) e.putInt("star" + i, stars[i]);
        for (int i = 0; i < 7; i++) { e.putInt("m" + i, bestMission[i]); e.putLong("md" + i, claimed[i]); }
        for (int i = 0; i < 18; i++) e.putBoolean("sk" + i, skinOwned[i]);
        e.apply();
    }

    // ---------- car skins (colors) ----------
    public static final String[] SKIN_NAMES = {"FACTORY", "MIDNIGHT", "GOLD", "ICE", "BLOOD", "TOXIC",
            "CHROME", "SAKURA", "DEEP SEA", "LAVA", "GALAXY", "SAND"};
    public static final int[] SKIN_PRICE = {0, 250, 600, 400, 400, 400, 900, 600, 600, 900, 1200, 500};
    public static final int[][] SKIN_COLORS = {
            {-1, -1, -1},
            {0xFF1C2230, 0xFF0B0E15, 0xFF7CE7FF},
            {0xFFFFC53D, 0xFF8A5A00, 0xFFFFF3B0},
            {0xFFBDE9FF, 0xFF3E7FA8, 0xFF00E5FF},
            {0xFFB31B2E, 0xFF5C0A14, 0xFFFF6B6B},
            {0xFF8FFF00, 0xFF3A6B00, 0xFFEFFF8A},
            {0xFFDDE6F0, 0xFF8794A8, 0xFFEAF6FF},
            {0xFFFF9ECF, 0xFFB0407A, 0xFFFFE0F0},
            {0xFF0A3D62, 0xFF04202F, 0xFF41D3FF},
            {0xFFFF5A00, 0xFF7A1A00, 0xFFFFD000},
            {0xFF3B1E70, 0xFF160A33, 0xFFB78CFF},
            {0xFFD7C4A3, 0xFF8A7654, 0xFFFFF0C9}
    };

    public static int[] carColors(int carIdx, int skinIdx) {
        Car car = CARS[carIdx];
        int[] base = {car.body, car.body2, car.accent};
        if (skinIdx <= 0 || skinIdx >= SKIN_COLORS.length) return base;
        int[] sk = SKIN_COLORS[skinIdx];
        return new int[]{sk[0], sk[1], sk[2]};
    }
    public static int carSkinOffset() { return (int) (selectedCar * 3) % 18; }
}
