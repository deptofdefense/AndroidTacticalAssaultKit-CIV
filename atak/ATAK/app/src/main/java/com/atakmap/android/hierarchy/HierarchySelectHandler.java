
package com.atakmap.android.hierarchy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HierarchySelectHandler {

    static private final Map<String, HierarchyListUserSelect> handlers = new HashMap<>();

    public static void register(String clazz, HierarchyListUserSelect handler) {
        synchronized (handlers) {
            handlers.put(clazz, handler);
        }
    }

    public static void register(Class<?> clazz,
            HierarchyListUserSelect handler) {
        register(clazz.getName(), handler);
    }

    static public void unregister(String clazz) {
        synchronized (handlers) {
            handlers.remove(clazz);
        }
    }

    public static void unregister(Class<?> clazz) {
        unregister(clazz.getName());
    }

    static public HierarchyListUserSelect get(String clazz) {
        synchronized (handlers) {
            return handlers.get(clazz);
        }
    }

    /**
     * Get all handlers which support external usage
     *
     * @return List of handlers
     */
    public static List<HierarchyListUserSelect> getExternalHandlers() {
        List<HierarchyListUserSelect> copy;
        synchronized (handlers) {
            copy = new ArrayList<>(handlers.values());
        }
        List<HierarchyListUserSelect> ret = new ArrayList<>();
        for (HierarchyListUserSelect h : copy) {
            if (h.isExternalUsageSupported())
                ret.add(h);
        }
        return ret;
    }
}
