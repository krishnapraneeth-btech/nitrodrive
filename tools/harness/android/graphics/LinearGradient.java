package android.graphics;
public class LinearGradient extends Shader {
    float x0, y0, x1, y1; int c0, c1;
    public LinearGradient(float x0, float y0, float x1, float y1, int c0, int c1, TileMode m) {
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.c0 = c0; this.c1 = c1;
    }
    public java.awt.Paint awtPaint() {
        java.awt.Color a = new java.awt.Color((c0 & 0xFFFFFF) | (((c0 >>> 24) & 0xFF) << 24), true);
        java.awt.Color b = new java.awt.Color((c1 & 0xFFFFFF) | (((c1 >>> 24) & 0xFF) << 24), true);
        return new java.awt.LinearGradientPaint(x0, y0, x1, y1, new float[]{0f, 1f}, new java.awt.Color[]{a, b});
    }
}
