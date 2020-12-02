package android.content;

public class SharedPreferences {
    public static interface Editor {
        public Editor putString(String key, String value);
        public Editor remove(String key);
        public void apply();
    }

    public String getString(String key, String defaultValue) {
        throw new UnsupportedOperationException();
    }

    public boolean contains(String key) {
        throw new UnsupportedOperationException();
    }

    public Editor edit() {
        throw new UnsupportedOperationException();
    }
}
