
package com.atakmap.android.routes.elevation.model;

import java.util.HashMap;
import java.util.Map;

public class RouteCache {
    protected static RouteCache _instance;
    private final int _MAX_CACHED_ITEMS = 5;

    final Map<String, RouteData> cache = new HashMap<>();

    protected RouteCache() {
    }

    public synchronized static RouteCache getInstance() {
        if (_instance == null) {
            _instance = new RouteCache();
        }
        return _instance;
    }

    public synchronized void cache(String id, RouteData data) {
        if (cache.size() <= _MAX_CACHED_ITEMS)
            cache.put(id, data);
    }

    public synchronized RouteData retrieve(String id) {
        if (cache.containsKey(id))
            return cache.get(id);
        return null;
    }

    public synchronized void invalidate(String id) {
        cache.remove(id);
    }
}
