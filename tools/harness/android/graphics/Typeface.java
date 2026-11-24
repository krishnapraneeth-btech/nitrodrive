package android.graphics;
import android.content.res.AssetManager;
import java.awt.Font;
import java.io.File;
public class Typeface {
    public java.awt.Font awt;
    public static Typeface DEFAULT = new Typeface(new Font("SansSerif", Font.PLAIN, 16));
    public Typeface(Font f) { awt = f; }
    public static Typeface createFromAsset(AssetManager am, String path) {
        String dir = System.getProperty("nd.assets", "/home/user/nitrodrive/app/assets");
        try {
            Font f = Font.createFont(Font.TRUETYPE_FONT, new File(dir + "/" + path));
            return new Typeface(f);
        } catch (Exception e) {
            return new Typeface(new Font("SansSerif", Font.BOLD, 16));
        }
    }
}
