package android.content;
public interface SharedPreferences {
  String getString(String k, String d); boolean getBoolean(String k, boolean d); Editor edit();
  interface Editor { Editor putString(String k, String v); Editor putBoolean(String k, boolean v); Editor remove(String k); Editor clear(); void apply(); }
}
