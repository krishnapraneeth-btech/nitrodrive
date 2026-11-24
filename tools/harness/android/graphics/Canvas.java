package android.graphics;

import java.awt.*;
import java.awt.Color;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** AWT-backed Canvas stub so the real game code can render offline to PNG. */
public class Canvas {
    public Graphics2D g;
    private final ArrayDeque<Graphics2D> stack = new ArrayDeque<>();

    public Canvas(int w, int h) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        W = w;
        H = h;
        image = img;
    }
    public int W, H;
    public BufferedImage image;

    public void save() {
        Graphics2D copy = (Graphics2D) g.create();
        copy.setComposite(g.getComposite());
        copy.setPaint(g.getPaint());
        copy.setStroke(g.getStroke());
        stack.push(copy);
    }
    public void restore() {
        if (stack.isEmpty()) return;
        g.dispose();
        g = stack.pop();
    }

    public void translate(float x, float y) { g.translate(x, y); }
    public void rotate(float deg) { g.rotate(Math.toRadians(deg)); }
    public void rotate(float deg, float cx, float cy) { g.rotate(Math.toRadians(deg), cx, cy); }
    public void scale(float sx, float sy) { g.scale(sx, sy); }
    public void scale(float sx, float sy, float cx, float cy) { g.translate(cx, cy); g.scale(sx, sy); g.translate(-cx, -cy); }
    public void skew(float sx, float sy) { g.transform(new AffineTransform(1, Math.tan(sy), Math.tan(sx), 1, 0, 0)); }

    private void apply(Paint p) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alphaF()));
        g.setStroke(new BasicStroke(Math.max(0.6f, p.strokeWidth), p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        java.awt.Paint fill;
        if (p.shader != null) fill = p.shader.awtPaint();
        else fill = new Color((p.color & 0xFFFFFF) | ((p.alpha & 0xFF) << 24), true);
        g.setPaint(fill);
        if (p.shadowR > 0) {
            // cheap glow: draw faded offsets on top handled by caller; here just brighten
        }
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        saveState(p);
        if (p.style == Paint.Style.STROKE) g.draw(new Rectangle2D.Float(l, t, r - l, b - t));
        else g.fill(new Rectangle2D.Float(l, t, r - l, b - t));
        popState();
    }
    public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
        saveState(p);
        RoundRectangle2D rr = new RoundRectangle2D.Float(l, t, r - l, b - t, Math.min(rx, ry) * 2, Math.min(rx, ry) * 2);
        if (p.style == Paint.Style.STROKE) g.draw(rr); else g.fill(rr);
        popState();
    }
    public void drawCircle(float x, float y, float r, Paint p) {
        saveState(p);
        Ellipse2D e = new Ellipse2D.Float(x - r, y - r, r * 2, r * 2);
        if (p.style == Paint.Style.STROKE) g.draw(e); else g.fill(e);
        popState();
    }
    public void drawOval(float l, float t, float r, float b, Paint p) {
        saveState(p);
        Ellipse2D e = new Ellipse2D.Float(l, t, r - l, b - t);
        if (p.style == Paint.Style.STROKE) g.draw(e); else g.fill(e);
        popState();
    }
    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        saveState(p);
        g.draw(new Line2D.Float(x0, y0, x1, y1));
        popState();
    }
    public void drawArc(float l, float t, float r, float b, float start, float sweep, boolean useCenter, Paint p) {
        saveState(p);
        Arc2D a = new Arc2D.Float(l, t, r - l, b - t, start, sweep, useCenter ? Arc2D.PIE : Arc2D.OPEN);
        if (p.style == Paint.Style.STROKE) g.draw(a); else g.fill(a);
        popState();
    }
    public void drawPath(Path path, Paint p) {
        saveState(p);
        if (p.style == Paint.Style.STROKE) g.draw(path.p); else g.fill(path.p);
        popState();
    }
    public void drawText(String str, float x, float y, Paint p) {
        saveState(p);
        g.setFont(p.font());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (p.shadowR > 0) {
            Color glow = new Color((p.shadowColor & 0xFFFFFF) | (70 << 24), true);
            Color old = g.getColor();
            g.setColor(glow);
            for (int i = 1; i <= 3; i++) {
                float o = p.shadowR * 0.45f * i / 3f;
                drawStr(str, x - o, y, p); drawStr(str, x + o, y, p);
                drawStr(str, x, y - o, p); drawStr(str, x, y + o, p);
            }
            g.setColor(old);
        }
        drawStr(str, x, y, p);
        popState();
    }
    private void drawStr(String str, float x, float y, Paint p) {
        g.setFont(p.font());
        FontMetrics fm = g.getFontMetrics();
        float dx = 0;
        if (p.textAlign == Paint.Align.CENTER) dx = -fm.stringWidth(str) / 2f;
        else if (p.textAlign == Paint.Align.RIGHT) dx = -fm.stringWidth(str);
        if (p.letterSpacing != 0) {
            float cx = x + dx;
            for (char ch : str.toCharArray()) {
                g.drawString(String.valueOf(ch), cx, y);
                cx += g.getFontMetrics().charWidth(ch) + p.letterSpacing * p.textSize;
            }
        } else {
            g.drawString(str, x + dx, y);
        }
    }
    private void saveState(Paint p) {
        apply(p);
    }
    private void popState() { }

    public static float measureStatic(String str, Paint p) {
        BufferedImage tmp = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tg = tmp.createGraphics();
        tg.setFont(p.font());
        float w = tg.getFontMetrics().stringWidth(str);
        tg.dispose();
        return w;
    }
}
