
package com.atakmap.android.hashtags.util;

import com.atakmap.coremap.locale.LocaleUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Hash map that uses case-insensitive keys
 */
public class HashtagMap<V> extends HashMap<String, V> {

    public HashtagMap() {
        super();
    }

    public HashtagMap(int initialCapacity) {
        super(initialCapacity);
    }

    public HashtagMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public HashtagMap(HashtagMap<V> other) {
        super(other);
    }

    @Override
    public V put(String tag, V values) {
        return super.put(key(tag), values);
    }

    @Override
    public void putAll(Map<? extends String, ? extends V> m) {
        if (m instanceof HashtagMap)
            super.putAll(m);
        else {
            for (Map.Entry<? extends String, ? extends V> e : m.entrySet())
                put(key(e.getKey()), e.getValue());
        }
    }

    @Override
    public V get(Object tag) {
        return super.get(key(tag));
    }

    @Override
    public boolean containsKey(Object tag) {
        return super.containsKey(key(tag));
    }

    @Override
    public V remove(Object tag) {
        return super.remove(key(tag));
    }

    @Override
    public boolean remove(Object tag, Object v) {
        return super.remove(key(tag), v);
    }

    private String key(Object tag) {
        return String.valueOf(tag).toLowerCase(LocaleUtil.getCurrent());
    }
}
