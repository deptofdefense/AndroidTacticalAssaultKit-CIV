
package com.atakmap.android.hierarchy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration for {@link HierarchyListUserSelect} instances based on class name
 */
public class HierarchySelectHandler {

    static private final Map<String, HierarchyListUserSelect> handlers = new HashMap<>();

    /**
     * Register a select handler by class name
     * @param clazz Class name
     * @param handler Handler to register
     */
    public static void register(String clazz, HierarchyListUserSelect handler) {
        synchronized (handlers) {
            handlers.put(clazz, handler);
        }
    }

    /**
     * Register a select handler by class name
     * @param clazz Class
     * @param handler Handler to register
     */
    public static void register(Class<?> clazz,
            HierarchyListUserSelect handler) {
        register(clazz.getName(), handler);
    }

    /**
     * Unregister the select handler for the given class name
     * @param clazz Class name
     */
    static public void unregister(String clazz) {
        synchronized (handlers) {
            handlers.remove(clazz);
        }
    }

    /**
     * Unregister the select handler for the given class
     * @param clazz Class
     */
    public static void unregister(Class<?> clazz) {
        unregister(clazz.getName());
    }

    /**
     * Unregister a specific select handler only if it's already registered
     * for the given class name
     * @param clazz Class name
     * @param handler Handler to unregister
     */
    public static void unregister(String clazz,
            HierarchyListUserSelect handler) {
        synchronized (handlers) {
            HierarchyListUserSelect existing = handlers.get(clazz);
            if (handler == existing)
                handlers.remove(clazz);
        }
    }

    /**
     * Unregister a specific select handler only if it's already registered
     * for the given class name
     * @param clazz Class
     * @param handler Handler to unregister
     */
    public static void unregister(Class<?> clazz,
            HierarchyListUserSelect handler) {
        unregister(clazz.getName(), handler);
    }

    /**
     * Get the select handler registered for the given class name
     * @param clazz Class name
     */
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
