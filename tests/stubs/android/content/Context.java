package android.content;
import java.util.*;
public class Context {
  public static final int MODE_PRIVATE = 0;
  public static final String WIFI_SERVICE = "wifi";
  public Context getApplicationContext() { return this; }
  public Object getSystemService(String n) { return new android.net.wifi.WifiManager(); }
  public final Map<String, String> store = new HashMap<>();
  public SharedPreferences getSharedPreferences(String n, int m) {
    return new SharedPreferences() {
      public String getString(String k, String d) { synchronized (store) { return store.getOrDefault(k, d); } }
      public boolean getBoolean(String k, boolean d) { synchronized (store) { String v = store.get(k); return v == null ? d : Boolean.parseBoolean(v); } }
      public Editor edit() { return new Editor() {
        public Editor putString(String k, String v) { synchronized (store) { if (v == null) store.remove(k); else store.put(k, v); } return this; }
        public Editor putBoolean(String k, boolean v) { synchronized (store) { store.put(k, String.valueOf(v)); } return this; }
        public Editor remove(String k) { synchronized (store) { store.remove(k); } return this; }
        public Editor clear() { synchronized (store) { store.clear(); } return this; }
        public void apply() {} }; }
    };
  }
}
