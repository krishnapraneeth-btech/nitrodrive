package android.content;
import android.content.res.AssetManager;
import java.io.*;
import java.util.*;
public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final String VIBRATOR_SERVICE = "vibrator";
    public Context getApplicationContext() { return this; }
    public AssetManager getAssets() { return new AssetManager(); }
    public java.io.File getCacheDir() { File f = new File("/tmp/ndcache"); f.mkdirs(); return f; }
    public Object getSystemService(String s) { return null; }
    private static final Map<String, SharedPreferences> prefs = new HashMap<>();
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return prefs.computeIfAbsent(name, k -> new MemPrefs());
    }
    static class MemPrefs implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        public long getLong(String k, long d) { Object o = m.get(k); return o == null ? d : (Long) o; }
        public int getInt(String k, int d) { Object o = m.get(k); return o == null ? d : ((Number) o).intValue(); }
        public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o == null ? d : (Boolean) o; }
        public float getFloat(String k, float d) { Object o = m.get(k); return o == null ? d : ((Number) o).floatValue(); }
        public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
        public Editor edit() {
            return new Editor() {
                final Map<String, Object> buf = new HashMap<>();
                public Editor putLong(String k, long v) { buf.put(k, v); return this; }
                public Editor putInt(String k, int v) { buf.put(k, v); return this; }
                public Editor putBoolean(String k, boolean v) { buf.put(k, v); return this; }
                public Editor putFloat(String k, float v) { buf.put(k, v); return this; }
                public Editor putString(String k, String v) { buf.put(k, v); return this; }
                public void apply() { m.putAll(buf); }
            };
        }
    }
}
