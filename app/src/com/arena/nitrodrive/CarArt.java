package com.arena.nitrodrive;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Procedural top-down car sprites. O(1) draws per car, so 20+ cars render at 60fps. */
public class CarArt {
    static final Path P = new Path();
    static final Path P2 = new Path();

    /**
     * @param kind 0 traffic, 2 police base, 3 player
     * @param carModel 0..5 (adds spoiler/intakes for higher models)
     * @param tilt lateral skew for the player car
     */
    public static void draw(Canvas c, float x, float y, float w, int body, int body2, int accent,
                            int kind, int carModel, float tilt, boolean glow, float t) {
        Paint p = Draw.p;
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);

        float L = w * 1.92f;   // length
        float hw = w / 2f;     // half width
        p.setColor(0x55000000);
        c.drawOval(x - hw * 1.1f, y - L * 0.55f, x + hw * 1.1f, y + L * 0.55f, p);

        c.save();
        c.translate(x, y);
        if (tilt != 0) {
            c.rotate(-tilt * 9f);
            c.skew(-tilt * 0.28f, 0);
        }

        // ---- tires ----
        p.setColor(0xFF05060A);
        rr(c, -hw * 1.06f, -L * 0.40f, -hw * 0.80f, -L * 0.18f, w * 0.05f);
        rr(c, hw * 0.80f, -L * 0.40f, hw * 1.06f, -L * 0.18f, w * 0.05f);
        rr(c, -hw * 1.06f, L * 0.20f, -hw * 0.80f, L * 0.42f, w * 0.05f);
        rr(c, hw * 0.80f, L * 0.20f, hw * 1.06f, L * 0.42f, w * 0.05f);
        p.setColor(0xFF2B303C);
        c.drawRect(-hw * 1.04f, -L * 0.34f, -hw * 0.82f, -L * 0.24f, p);
        c.drawRect(hw * 0.82f, -L * 0.34f, hw * 1.04f, -L * 0.24f, p);
        c.drawRect(-hw * 1.04f, L * 0.26f, -hw * 0.82f, L * 0.36f, p);
        c.drawRect(hw * 0.82f, L * 0.26f, hw * 1.04f, L * 0.36f, p);

        // ---- body silhouette ----
        P.reset();
        P.moveTo(-hw * 0.34f, -L * 0.50f);
        P.quadTo(-hw * 0.66f, -L * 0.44f, -hw * 0.80f, -L * 0.30f);
        P.quadTo(-hw * 0.97f, -L * 0.12f, -hw * 0.97f, L * 0.06f);
        P.quadTo(-hw * 0.96f, L * 0.30f, -hw * 0.80f, L * 0.44f);
        P.quadTo(-hw * 0.60f, L * 0.52f, -hw * 0.30f, L * 0.50f);
        P.lineTo(hw * 0.30f, L * 0.50f);
        P.quadTo(hw * 0.60f, L * 0.52f, hw * 0.80f, L * 0.44f);
        P.quadTo(hw * 0.96f, L * 0.30f, hw * 0.97f, L * 0.06f);
        P.quadTo(hw * 0.97f, -L * 0.12f, hw * 0.80f, -L * 0.30f);
        P.quadTo(hw * 0.66f, -L * 0.44f, hw * 0.34f, -L * 0.50f);
        P.quadTo(0, -L * 0.545f, -hw * 0.34f, -L * 0.50f);
        P.close();
        p.setColor(body);
        c.drawPath(P, p);
        // side shading
        p.setColor(Draw.alpha(Draw.darken(body, 0.5f), 210));
        c.drawRect(-hw * 0.99f, -L * 0.30f, -hw * 0.62f, L * 0.42f, p);
        c.drawRect(hw * 0.62f, -L * 0.30f, hw * 0.99f, L * 0.42f, p);
        // nose dark trapezoid
        p.setColor(Draw.darken(body, 0.35f));
        P2.reset();
        P2.moveTo(-hw * 0.42f, -L * 0.50f);
        P2.quadTo(0, -L * 0.545f, hw * 0.42f, -L * 0.50f);
        P2.lineTo(hw * 0.30f, -L * 0.36f);
        P2.lineTo(-hw * 0.30f, -L * 0.36f);
        P2.close();
        c.drawPath(P2, p);
        // hood highlight
        p.setColor(Draw.alpha(Draw.lighten(body, 0.45f), 90));
        rr(c, -hw * 0.34f, -L * 0.44f, hw * 0.34f, -L * 0.15f, w * 0.04f);

        // ---- windshield + roof ----
        p.setColor(0xFF0A1420);
        P.reset();
        P.moveTo(-hw * 0.54f, -L * 0.16f);
        P.quadTo(0, -L * 0.22f, hw * 0.54f, -L * 0.16f);
        P.lineTo(hw * 0.44f, L * 0.02f);
        P.lineTo(-hw * 0.44f, L * 0.02f);
        P.close();
        c.drawPath(P, p);
        p.setColor(0x5537B6FF);
        c.drawPath(P, p);
        p.setColor(Draw.darken(body2, 0.1f));
        rr(c, -hw * 0.46f, L * 0.03f, hw * 0.46f, L * 0.20f, w * 0.05f);
        p.setColor(0xFF0A1420);
        rr(c, -hw * 0.48f, L * 0.22f, hw * 0.48f, L * 0.33f, w * 0.05f);
        p.setColor(0x4437B6FF);
        rr(c, -hw * 0.48f, L * 0.22f, hw * 0.48f, L * 0.33f, w * 0.05f);

        // ---- racing stripes ----
        p.setColor(Draw.alpha(Draw.lighten(body, 0.65f), 150));
        c.drawRect(-hw * 0.055f, -L * 0.47f, hw * 0.055f, L * 0.47f, p);

        // ---- headlights ----
        p.setColor(0x99FFF6D0);
        c.drawCircle(-hw * 0.52f, -L * 0.44f, w * 0.13f, p);
        c.drawCircle(hw * 0.52f, -L * 0.44f, w * 0.13f, p);
        p.setColor(0xFFFFFBE8);
        c.drawRect(-hw * 0.66f, -L * 0.465f, -hw * 0.35f, -L * 0.435f, p);
        c.drawRect(hw * 0.35f, -L * 0.465f, hw * 0.66f, -L * 0.435f, p);
        // beam cones — soft, short falloff so they read as light not geometry
        p.setColor(0x0CFFF6D0);
        P.reset();
        P.moveTo(-hw * 0.66f, -L * 0.46f); P.lineTo(-hw * 1.16f, -L * 0.80f);
        P.lineTo(-hw * 0.30f, -L * 0.80f); P.close(); c.drawPath(P, p);
        P.reset();
        P.moveTo(hw * 0.66f, -L * 0.46f); P.lineTo(hw * 1.16f, -L * 0.80f);
        P.lineTo(hw * 0.30f, -L * 0.80f); P.close(); c.drawPath(P, p);

        // ---- taillights ----
        p.setColor(0xFFFF2D55);
        rr(c, -hw * 0.72f, L * 0.455f, -hw * 0.22f, L * 0.50f, w * 0.03f);
        rr(c, hw * 0.22f, L * 0.455f, hw * 0.72f, L * 0.50f, w * 0.03f);
        p.setColor(0x55FF2D55);
        c.drawCircle(-hw * 0.47f, L * 0.52f, w * 0.18f, p);
        c.drawCircle(hw * 0.47f, L * 0.52f, w * 0.18f, p);

        // ---- accents ----
        p.setColor(accent);
        c.drawRect(-hw * 0.99f, -L * 0.05f, -hw * 0.90f, L * 0.30f, p);
        c.drawRect(hw * 0.90f, -L * 0.05f, hw * 0.99f, L * 0.30f, p);
        p.setColor(0x88FFFFFF);
        c.drawRect(-hw * 0.99f, -L * 0.05f, -hw * 0.90f, L * 0.02f, p);
        c.drawRect(hw * 0.90f, -L * 0.05f, hw * 0.99f, L * 0.02f, p);

        // ---- model flair ----
        if (carModel >= 1) {
            p.setColor(0xCC0C1420);
            c.drawRect(-hw * 0.60f, L * 0.40f, hw * 0.60f, L * 0.45f, p);
            c.drawRect(-hw * 0.60f, L * 0.40f, -hw * 0.54f, L * 0.55f, p);
            c.drawRect(hw * 0.54f, L * 0.40f, hw * 0.60f, L * 0.55f, p);
        }
        if (carModel >= 2) {
            // side intakes
            p.setColor(0xAA05080E);
            rr(c, -hw * 0.86f, L * 0.02f, -hw * 0.66f, L * 0.18f, w * 0.03f);
            rr(c, hw * 0.66f, L * 0.02f, hw * 0.86f, L * 0.18f, w * 0.03f);
        }
        if (carModel >= 4) {
            // hood vent + roof scoop
            p.setColor(0xAA05080E);
            rr(c, -hw * 0.26f, -L * 0.33f, hw * 0.26f, -L * 0.28f, w * 0.02f);
            rr(c, -hw * 0.10f, -L * 0.02f, hw * 0.10f, L * 0.10f, w * 0.02f);
        }
        if (kind == 2) {
            // police livery
            p.setColor(0xFF101826);
            c.drawRect(-hw * 0.99f, -L * 0.12f, hw * 0.99f, -L * 0.02f, p);
            p.setColor(0xFFFFE14D);
            c.drawRect(-hw * 0.99f, L * 0.34f, -hw * 0.80f, L * 0.40f, p);
            c.drawRect(hw * 0.80f, L * 0.34f, hw * 0.99f, L * 0.40f, p);
        }
        c.restore();
    }

    static void rr(Canvas c, float l, float t, float r, float b, float rad) {
        c.drawRoundRect(l, t, r, b, rad, rad, Draw.p);
    }

    /** Big showcase car for menus (with neon glow ring). */
    public static void hero(Canvas c, float cx, float cy, float w, int carIdx, int skinIdx, float t, int themeNeon) {
        int[] col = Store.carColors(carIdx, skinIdx);
        Paint p = Draw.p;
        p.setShader(new RadialGradient(cx, cy + w * 0.2f, w * 1.15f, Draw.alpha(themeNeon, 46), 0x00000000, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy + w * 0.2f, w * 1.15f, p);
        p.setShader(null);
        // rotating speed ring
        c.save();
        c.translate(cx, cy);
        c.rotate(t * 22f);
        Draw.s.setStyle(Paint.Style.STROKE);
        Draw.s.setStrokeWidth(w * 0.006f);
        Draw.s.setColor(Draw.alpha(themeNeon, 110));
        float r = w * 0.78f;
        for (int i = 0; i < 24; i++) {
            if (i % 3 == 0) continue;
            c.drawArc(-r, -r, r, r, i * 15, 9, false, Draw.s);
        }
        c.restore();
        draw(c, cx, cy, w, col[0], col[1], col[2], 1, carIdx, (float) Math.sin(t * 0.9f) * 0.03f, true, t);
    }
}
