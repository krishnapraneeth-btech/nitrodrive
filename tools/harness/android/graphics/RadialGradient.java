package android.graphics;
public class RadialGradient extends Shader {
    float x, y, r; int c0, c1;
    public RadialGradient(float x, float y, float r, int c0, int c1, TileMode m) {
        this.x = x; this.y = y; this.r = Math.max(0.01f, r); this.c0 = c0; this.c1 = c1;
    }
    public java.awt.Paint awtPaint() {
        java.awt.Color a = new java.awt.Color((c0 & 0xFFFFFF) | (((c0 >>> 24) & 0xFF) << 24), true);
        java.awt.Color b = new java.awt.Color((c1 & 0xFFFFFF) | (((c1 >>> 24) & 0xFF) << 24), true);
        return new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(x, y), r,
                new java.awt.geom.Point2D.Float(x, y), new float[]{0f, 1f}, new java.awt.Color[]{a, b},
                java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }
}
