package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1, SUBPIXEL_TEXT_FLAG = 2;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Align { LEFT, CENTER, RIGHT }
    public Style style = Style.FILL;
    public Cap cap = Cap.BUTT;
    public Align textAlign = Align.LEFT;
    public int color = 0xFF000000;
    public int alpha = 255;
    public float strokeWidth = 1f;
    public Shader shader;
    public float shadowR = 0;
    public int shadowColor = 0;
    public Typeface typeface = Typeface.DEFAULT;
    public float textSize = 16f;
    public float letterSpacing = 0f;
    public Paint() {}
    public Paint(int flags) {}
    public void setStyle(Style s) { style = s; }
    public void setColor(int c) { color = c; alpha = (c >>> 24) & 0xFF; }
    public void setAlpha(int a) { alpha = a; }
    public void setStrokeWidth(float w) { strokeWidth = w; }
    public void setShader(Shader s) { shader = s; }
    public void setStrokeCap(Cap c) { cap = c; }
    public void setTypeface(Typeface t) { typeface = t; }
    public void setTextSize(float s) { textSize = s; }
    public void setTextAlign(Align a) { textAlign = a; }
    public void setLetterSpacing(float l) { letterSpacing = l; }
    public void setShadowLayer(float r, float dx, float dy, int c) { shadowR = r; shadowColor = c; }
    public void clearShadowLayer() { shadowR = 0; }
    public float measureText(String s) { return Canvas.measureStatic(s, this); }
    public float alphaF() { return Math.max(0f, Math.min(1f, alpha / 255f)); }
    public java.awt.Font font() {
        java.awt.Font f = typeface.awt;
        return f.deriveFont(textSize);
    }
}
