package android.content;
public interface SharedPreferences {
    long getLong(String k, long d);
    int getInt(String k, int d);
    boolean getBoolean(String k, boolean d);
    float getFloat(String k, float d);
    String getString(String k, String d);
    Editor edit();
    interface Editor {
        Editor putLong(String k, long v);
        Editor putInt(String k, int v);
        Editor putBoolean(String k, boolean v);
        Editor putFloat(String k, float v);
        Editor putString(String k, String v);
        void apply();
    }
}
