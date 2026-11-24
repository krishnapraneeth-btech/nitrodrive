package com.arena.nitrodrive;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Static drawing helpers: fonts, neon text, glass panels, icons. */
public class Draw {
    public static Typeface F_HEAD, F_BOLD, F_SEMI, F_MED;
    public static float quality = 1f;
    public static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    public static final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Path path = new Path();
    public static final RectF rect = new RectF();

    public static void init(AssetManager am) {
        F_HEAD = Typeface.createFromAsset(am, "fonts/Audiowide-Regular.ttf");
        F_BOLD = Typeface.createFromAsset(am, "fonts/Rajdhani-Bold.ttf");
        F_SEMI = Typeface.createFromAsset(am, "fonts/Rajdhani-SemiBold.ttf");
        F_MED = Typeface.createFromAsset(am, "fonts/Rajdhani-Medium.ttf");
        t.setTypeface(F_BOLD);
    }

    public static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
    public static float lerp(float a, float b, float f) { return a + (b - a) * f; }
    public static int lerpColor(int a, int b, float f) {
        int aa = (int) lerp((a >>> 24) & 0xFF, (b >>> 24) & 0xFF, f);
        int rr = (int) lerp((a >> 16) & 0xFF, (b >> 16) & 0xFF, f);
        int gg = (int) lerp((a >> 8) & 0xFF, (b >> 8) & 0xFF, f);
        int bb = (int) lerp(a & 0xFF, b & 0xFF, f);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }
    public static int alpha(int color, int a) { return (color & 0x00FFFFFF) | ((a & 0xFF) << 24); }
    public static int lighten(int c, float f) { return lerpColor(c, 0xFFFFFFFF, f); }
    public static int darken(int c, float f) { return lerpColor(c, 0xFF000000, f); }

    // ---------- text ----------
    public static void text(Canvas c, String str, float x, float y, float size, Typeface f, int color, Paint.Align al) {
        t.setTypeface(f); t.setTextSize(size); t.setColor(color); t.setTextAlign(al);
        t.clearShadowLayer(); t.setShader(null); t.setStyle(Paint.Style.FILL);
        c.drawText(str, x, y, t);
    }
    public static void textGlow(Canvas c, String str, float x, float y, float size, Typeface f, int color, int glow, float r) {
        t.setTypeface(f); t.setTextSize(size); t.setColor(color); t.setTextAlign(Paint.Align.CENTER);
        t.setShader(null); t.setStyle(Paint.Style.FILL);
        t.setShadowLayer(r, 0, 0, glow);
        c.drawText(str, x, y, t);
        t.clearShadowLayer();
    }
    public static void textLS(Canvas c, String str, float x, float y, float size, Typeface f, int color, Paint.Align al, float ls) {
        t.setTypeface(f); t.setTextSize(size); t.setColor(color); t.setTextAlign(al);
        t.setLetterSpacing(ls); t.clearShadowLayer(); t.setShader(null);
        c.drawText(str, x, y, t);
        t.setLetterSpacing(0);
    }
    public static float measure(String str, float size, Typeface f) {
        t.setTypeface(f); t.setTextSize(size);
        return t.measureText(str);
    }

    // ---------- shapes ----------
    public static void rrect(Canvas c, float l, float top, float r, float b, float rad, int color) {
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        c.drawRoundRect(l, top, r, b, rad, rad, p);
    }
    public static void rrectStroke(Canvas c, float l, float top, float r, float b, float rad, int color, float w) {
        s.setStyle(Paint.Style.STROKE); s.setShader(null); s.setColor(color); s.setStrokeWidth(w);
        c.drawRoundRect(l, top, r, b, rad, rad, s);
    }
    /** Rounded rect with layered neon glow. */
    public static void glowRRect(Canvas c, float l, float top, float r, float b, float rad, int color, float w) {
        for (int i = 3; i >= 1; i--) {
            s.setStyle(Paint.Style.STROKE); s.setShader(null);
            s.setColor(alpha(color, 16 * i));
            s.setStrokeWidth(w + i * 5f);
            c.drawRoundRect(l, top, r, b, rad, rad, s);
        }
        s.setColor(color); s.setStrokeWidth(w);
        c.drawRoundRect(l, top, r, b, rad, rad, s);
    }
    public static void line(Canvas c, float x0, float y0, float x1, float y1, int color, float w) {
        s.setStyle(Paint.Style.STROKE); s.setShader(null); s.setColor(color); s.setStrokeWidth(w);
        c.drawLine(x0, y0, x1, y1, s);
    }
    /** Set a shader with full paint alpha (Android multiplies shader by paint alpha). */
    public static void sh(Shader sh) { p.setColor(0xFFFFFFFF); p.setShader(sh); }
    public static void shT(Shader sh) { t.setColor(0xFFFFFFFF); t.setShader(sh); }
    public static void shClear() { p.setShader(null); p.setColor(0xFFFFFFFF); }
    public static void shTClear() { t.setShader(null); t.setColor(0xFFFFFFFF); }
    public static LinearGradient lg(float x0, float y0, float x1, float y1, int c0, int c1) {
        return new LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP);
    }
    public static void fill(Canvas c, float l, float top, float r, float b, Shader sh) {
        p.setStyle(Paint.Style.FILL); p.setShader(sh);
        c.drawRect(l, top, r, b, p);
        p.setShader(null);
    }

    // ---------- icons (centered, size s = full extent) ----------
    public static void iconPlay(Canvas c, float cx, float cy, float s, int color) {
        path.reset();
        path.moveTo(cx - s * 0.28f, cy - s * 0.38f);
        path.lineTo(cx + s * 0.40f, cy);
        path.lineTo(cx - s * 0.28f, cy + s * 0.38f);
        path.close();
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        c.drawPath(path, p);
    }
    public static void iconPause(Canvas c, float cx, float cy, float s, int color) {
        float w = s * 0.18f;
        float h = s * 0.62f;
        float gap = s * 0.14f;
        float rad = w * 0.4f;
        rrect(c, cx - gap / 2 - w, cy - h / 2, cx - gap / 2, cy + h / 2, rad, color);
        rrect(c, cx + gap / 2, cy - h / 2, cx + gap / 2 + w, cy + h / 2, rad, color);
    }
    public static void iconChevron(Canvas c, float cx, float cy, float size, int color, boolean left, float w) {
        s.setStyle(Paint.Style.STROKE); s.setShader(null); s.setColor(color);
        s.setStrokeWidth(w); s.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        float d = left ? 1 : -1;
        path.moveTo(cx + size * 0.14f * d, cy - size * 0.26f);
        path.lineTo(cx - size * 0.14f * d, cy);
        path.lineTo(cx + size * 0.14f * d, cy + size * 0.26f);
        c.drawPath(path, s);
        s.setStrokeCap(Paint.Cap.BUTT);
    }
    public static void iconBolt(Canvas c, float cx, float cy, float s, int color) {
        path.reset();
        path.moveTo(cx + s * 0.08f, cy - s * 0.5f);
        path.lineTo(cx - s * 0.26f, cy + s * 0.07f);
        path.lineTo(cx - s * 0.01f, cy + s * 0.07f);
        path.lineTo(cx - s * 0.08f, cy + s * 0.5f);
        path.lineTo(cx + s * 0.27f, cy - s * 0.08f);
        path.lineTo(cx + s * 0.02f, cy - s * 0.08f);
        path.close();
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        c.drawPath(path, p);
    }
    public static void iconShield(Canvas c, float cx, float cy, float s, int color, boolean fillIt) {
        path.reset();
        path.moveTo(cx, cy - s * 0.46f);
        path.cubicTo(cx + s * 0.30f, cy - s * 0.34f, cx + s * 0.42f, cy - s * 0.30f, cx + s * 0.42f, cy - s * 0.08f);
        path.cubicTo(cx + s * 0.42f, cy + s * 0.22f, cx + s * 0.22f, cy + s * 0.40f, cx, cy + s * 0.48f);
        path.cubicTo(cx - s * 0.22f, cy + s * 0.40f, cx - s * 0.42f, cy + s * 0.22f, cx - s * 0.42f, cy - s * 0.08f);
        path.cubicTo(cx - s * 0.42f, cy - s * 0.30f, cx - s * 0.30f, cy - s * 0.34f, cx, cy - s * 0.46f);
        path.close();
        p.setStyle(fillIt ? Paint.Style.FILL : Paint.Style.STROKE);
        p.setShader(null); p.setColor(color);
        if (!fillIt) p.setStrokeWidth(s * 0.1f);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }
    public static void iconStar(Canvas c, float cx, float cy, float outer, float inner, int color) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        c.drawPath(path, p);
    }
    public static void iconClock(Canvas c, float cx, float cy, float s, int color) {
        ring(c, cx, cy, s * 0.42f, color, s * 0.1f);
        line(c, cx, cy - s * 0.2f, cx, cy, color, s * 0.09f);
        line(c, cx, cy, cx + s * 0.16f, cy + s * 0.06f, color, s * 0.09f);
    }
    private static void ring(Canvas c, float cx, float cy, float r, int color, float w) {
        s.setStyle(Paint.Style.STROKE); s.setColor(color); s.setStrokeWidth(w); s.setShader(null);
        c.drawCircle(cx, cy, r, s);
    }
    public static void iconTrophy(Canvas c, float cx, float cy, float sz, int color) {
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        path.reset();
        path.moveTo(cx - sz * 0.26f, cy - sz * 0.34f);
        path.lineTo(cx + sz * 0.26f, cy - sz * 0.34f);
        path.lineTo(cx + sz * 0.26f, cy - sz * 0.02f);
        path.cubicTo(cx + sz * 0.26f, cy + sz * 0.20f, cx + sz * 0.10f, cy + sz * 0.26f, cx, cy + sz * 0.26f);
        path.cubicTo(cx - sz * 0.10f, cy + sz * 0.26f, cx - sz * 0.26f, cy + sz * 0.20f, cx - sz * 0.26f, cy - sz * 0.02f);
        path.close();
        c.drawPath(path, p);
        s.setStyle(Paint.Style.STROKE); s.setColor(color); s.setStrokeWidth(sz * 0.07f); s.setShader(null);
        c.drawArc(cx - sz * 0.42f, cy - sz * 0.32f, cx - sz * 0.10f, cy - sz * 0.02f, 90, 180, false, s);
        c.drawArc(cx + sz * 0.10f, cy - sz * 0.32f, cx + sz * 0.42f, cy - sz * 0.02f, -90, 180, false, s);
        rrect(c, cx - sz * 0.06f, cy + sz * 0.24f, cx + sz * 0.06f, cy + sz * 0.36f, sz * 0.02f, color);
        rrect(c, cx - sz * 0.22f, cy + sz * 0.34f, cx + sz * 0.22f, cy + sz * 0.44f, sz * 0.04f, color);
    }
    public static void iconGift(Canvas c, float cx, float cy, float sz, int color) {
        rrect(c, cx - sz * 0.36f, cy - sz * 0.10f, cx + sz * 0.36f, cy + sz * 0.36f, sz * 0.05f, color);
        rrect(c, cx - sz * 0.42f, cy - sz * 0.26f, cx + sz * 0.42f, cy - sz * 0.08f, sz * 0.04f, color);
        p.setColor(0xCC000000); p.setStyle(Paint.Style.FILL); p.setShader(null);
        c.drawRect(cx - sz * 0.05f, cy - sz * 0.10f, cx + sz * 0.05f, cy + sz * 0.36f, p);
        p.setColor(color);
        c.drawCircle(cx - sz * 0.14f, cy - sz * 0.34f, sz * 0.11f, p);
        c.drawCircle(cx + sz * 0.14f, cy - sz * 0.34f, sz * 0.11f, p);
    }
    public static void iconSound(Canvas c, float cx, float cy, float sz, int color, boolean on) {
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        path.reset();
        path.moveTo(cx - sz * 0.34f, cy - sz * 0.12f);
        path.lineTo(cx - sz * 0.18f, cy - sz * 0.12f);
        path.lineTo(cx + sz * 0.02f, cy - sz * 0.32f);
        path.lineTo(cx + sz * 0.02f, cy + sz * 0.32f);
        path.lineTo(cx - sz * 0.18f, cy + sz * 0.12f);
        path.lineTo(cx - sz * 0.34f, cy + sz * 0.12f);
        path.close();
        c.drawPath(path, p);
        if (on) {
            s.setStyle(Paint.Style.STROKE); s.setColor(color); s.setStrokeWidth(sz * 0.07f); s.setShader(null);
            c.drawArc(cx - sz * 0.28f, cy - sz * 0.24f, cx + sz * 0.20f, cy + sz * 0.24f, -50, 100, false, s);
            c.drawArc(cx - sz * 0.20f, cy - sz * 0.38f, cx + sz * 0.40f, cy + sz * 0.38f, -45, 90, false, s);
        } else {
            line(c, cx + sz * 0.14f, cy - sz * 0.18f, cx + sz * 0.42f, cy + sz * 0.18f, color, sz * 0.08f);
            line(c, cx + sz * 0.42f, cy - sz * 0.18f, cx + sz * 0.14f, cy + sz * 0.18f, color, sz * 0.08f);
        }
    }
    public static void iconMusic(Canvas c, float cx, float cy, float sz, int color) {
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        c.drawCircle(cx - sz * 0.20f, cy + sz * 0.22f, sz * 0.13f, p);
        c.drawCircle(cx + sz * 0.24f, cy + sz * 0.12f, sz * 0.13f, p);
        rrect(c, cx - sz * 0.10f, cy - sz * 0.34f, cx - sz * 0.04f, cy + sz * 0.22f, sz * 0.02f, color);
        rrect(c, cx + sz * 0.34f, cy - sz * 0.42f, cx + sz * 0.40f, cy + sz * 0.12f, sz * 0.02f, color);
        rrect(c, cx - sz * 0.10f, cy - sz * 0.34f, cx + sz * 0.40f, cy - sz * 0.26f, sz * 0.02f, color);
    }
    public static void iconHeart(Canvas c, float cx, float cy, float sz, int color, boolean filled) {
        path.reset();
        path.moveTo(cx, cy + sz * 0.38f);
        path.cubicTo(cx - sz * 0.55f, cy - sz * 0.02f, cx - sz * 0.34f, cy - sz * 0.42f, cx, cy - sz * 0.16f);
        path.cubicTo(cx + sz * 0.34f, cy - sz * 0.42f, cx + sz * 0.55f, cy - sz * 0.02f, cx, cy + sz * 0.38f);
        path.close();
        p.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        p.setShader(null); p.setColor(color); if (!filled) p.setStrokeWidth(sz * 0.09f);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }
    public static void iconFlag(Canvas c, float cx, float cy, float sz, int color) {
        line(c, cx - sz * 0.26f, cy - sz * 0.4f, cx - sz * 0.26f, cy + sz * 0.42f, color, sz * 0.08f);
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        path.reset();
        path.moveTo(cx - sz * 0.24f, cy - sz * 0.40f);
        path.lineTo(cx + sz * 0.36f, cy - sz * 0.24f);
        path.lineTo(cx - sz * 0.24f, cy - sz * 0.04f);
        path.close();
        c.drawPath(path, p);
    }
    public static void iconGear(Canvas c, float cx, float cy, float sz, int color) {
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
        for (int i = 0; i < 8; i++) {
            c.save(); c.rotate(i * 45f, cx, cy);
            rrect(c, cx - sz * 0.07f, cy - sz * 0.46f, cx + sz * 0.07f, cy - sz * 0.30f, sz * 0.04f, color);
            c.restore();
        }
        s.setStyle(Paint.Style.STROKE); s.setColor(color); s.setStrokeWidth(sz * 0.12f); s.setShader(null);
        c.drawCircle(cx, cy, sz * 0.24f, s);
    }
    public static void iconCoin(Canvas c, float cx, float cy, float r, float spin, int glowCol) {
        float sx = 0.25f + 0.75f * Math.abs((float) Math.cos(spin));
        int g1 = 0xFFFFE082;
        int g2 = 0xFFFFB300;
        int g3 = 0xFFE65100;
        t.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, g1, g2, Shader.TileMode.CLAMP));
        t.setStyle(Paint.Style.FILL);
        c.save(); c.scale(sx, 1f, cx, cy);
        c.drawCircle(cx, cy, r, t);
        t.setShader(null);
        t.setColor(g3); t.setStyle(Paint.Style.STROKE); t.setStrokeWidth(r * 0.16f);
        c.drawCircle(cx, cy, r * 0.72f, t);
        t.setColor(0xFFFFF6D8); t.setStyle(Paint.Style.FILL);
        iconStar(c, cx, cy, r * 0.48f, r * 0.2f, 0xFFFFF3C4);
        c.restore();
    }
    public static String fmt(long n) {
        if (n < 1000) return "" + n;
        if (n < 1000000) return (n / 1000) + "," + String.format("%03d", n % 1000);
        return (n / 1000000) + "," + String.format("%03d", (n / 1000) % 1000) + "," + String.format("%03d", n % 1000);
    }
    public static String fmt2(long n) {
        if (n < 1000) return "" + n;
        if (n < 100000) return (n / 1000f < 10 ? String.format("%.1f", n / 1000f) : "" + (n / 1000)) + "k";
        return (n / 1000000f < 10 ? String.format("%.1f", n / 1000000f) : "" + (n / 1000000)) + "M";
    }
}
