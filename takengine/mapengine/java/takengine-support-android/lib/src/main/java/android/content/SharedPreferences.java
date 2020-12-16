package android.content;

import java.util.Properties;

public class SharedPreferences {
    public static interface Editor {
        public Editor putString(String key, String value);
        public Editor remove(String key);
        public void apply();
    }

    final Properties properties = new Properties();

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean contains(String key) {
        return properties.containsKey(key);
    }

    public Editor edit() {
        return new EditorImpl();
    }

    final class EditorImpl implements Editor {

        @Override
        public Editor putString(String key, String value) {
            SharedPreferences.this.properties.setProperty(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            SharedPreferences.this.properties.remove(key);
            return this;
        }

        @Override
        public void apply() {

        }
    }
}
