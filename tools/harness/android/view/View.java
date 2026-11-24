package android.view;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
public class View {
    public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 4096;
    public static final int SYSTEM_UI_FLAG_FULLSCREEN = 4;
    public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 2;
    public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 256;
    public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 1024;
    public static final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 512;
    private Context ctx;
    public View(Context c) { ctx = c; }
    public View(Context c, AttributeSet a) { ctx = c; }
    public Context getContext() { return ctx; }
    public void setKeepScreenOn(boolean b) {}
    public void setFocusable(boolean b) {}
    public void invalidate() {}
    public void postDelayed(Runnable r, long ms) {}
    public void post(Runnable r) {}
    public void setSystemUiVisibility(int v) {}
    public void setContentView(View v) {}
    protected void onDraw(Canvas c) {}
    public boolean onTouchEvent(MotionEvent e) { return false; }
    protected void onSizeChanged(int w, int h, int ow, int oh) {}
    protected void onAttachedToWindow() {}
    protected void onDetachedFromWindow() {}
}
