package android.os;
public class Handler {
    public Handler() {}
    public Handler(Looper l) {}
    public boolean postDelayed(Runnable r, long ms) { return true; }
    public boolean post(Runnable r) { return true; }
    public void removeCallbacks(Runnable r) {}
}
