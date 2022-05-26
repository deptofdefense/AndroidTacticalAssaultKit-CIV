
package com.atakmap.android.maps.graphics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.atakmap.coremap.log.Log;

public class GLDebugProfile {

    private static final boolean _ACTIVE = false;

    public static void reportActive(String type, Object object) {
        if (_ACTIVE) {
            HashSet<Object> active = _types.get(type);
            if (active == null) {
                active = new HashSet<>();
                _types.put(type, active);
            }
            active.add(object);
        }
    }

    public static void reportInactive(String type, Object object) {
        if (_ACTIVE) {
            HashSet<Object> active = _types.get(type);
            if (active != null) {
                active.remove(object);
                if (active.size() == 0) {
                    _types.remove(type);
                }
            }
        }
    }

    public static void logActive(String type, int level) {
        if (_ACTIVE) {
            HashSet<Object> active = _types.get(type);
            if (active != null) {
                Log.println(level, "GLDebugProfile." + type, "---- ACTIVE " + type + " ----");
                Log.println(level, "GLDebugProfile." + type, "count: " + active.size());
                for (Object a : active) {
                    Log.println(level, "GLDebugProfile." + type, a.toString());
                }

            }
        }
    }

    public static void logActive(int level) {
        if (_ACTIVE) {
            Set<String> keys = _types.keySet();
            for (String k : keys) {
                logActive(k, level);
            }
        }
    }

    private static HashMap<String, HashSet<Object>> _types = new HashMap<>();
}
