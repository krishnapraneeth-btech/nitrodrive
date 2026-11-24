package android.app;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
public class Activity extends Context {
    public Window win = new Window();
    public Window getWindow() { return win; }
    protected void onCreate(Bundle b) {}
    protected void onPause() {}
    protected void onResume() {}
    public void onBackPressed() {}
    public void finish() {}
    public void setContentView(android.view.View v) {}
    public void onWindowFocusChanged(boolean b) {}
}
