package android.preference;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.IdentityHashMap;
import java.util.Map;

public final class PreferenceManager {
    private static Map<Context, SharedPreferences> prefs = new IdentityHashMap<>();

    private PreferenceManager() {}

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        synchronized(prefs) {
            SharedPreferences retval = prefs.get(context);
            if(retval == null) {
                retval = new SharedPreferences();
                prefs.put(context, retval);
            }
            return retval;
        }
    }
}
