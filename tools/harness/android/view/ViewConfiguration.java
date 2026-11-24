package android.view;
import android.content.Context;
public class ViewConfiguration {
    public int getScaledTouchSlop() { return 12; }
    public static ViewConfiguration get(Context c) { return new ViewConfiguration(); }
}
