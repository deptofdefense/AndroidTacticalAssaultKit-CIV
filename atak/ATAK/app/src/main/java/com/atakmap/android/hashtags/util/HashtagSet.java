
package com.atakmap.android.hashtags.util;

import androidx.annotation.NonNull;

import com.atakmap.coremap.locale.LocaleUtil;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Case-insensitive hash set
 */
public class HashtagSet extends AbstractSet<String>
        implements Set<String>, Cloneable {

    private transient HashtagMap<String> map;

    public HashtagSet() {
        map = new HashtagMap<>();
    }

    public HashtagSet(Collection<? extends String> c) {
        map = new HashtagMap<>(Math.max((int) (c.size() / .75f) + 1, 16));
        addAll(c);
    }

    public HashtagSet(int initialCapacity, float loadFactor) {
        map = new HashtagMap<>(initialCapacity, loadFactor);
    }

    public HashtagSet(int initialCapacity) {
        map = new HashtagMap<>(initialCapacity);
    }

    @Override
    @NonNull
    public Iterator<String> iterator() {
        return map.values().iterator();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return map.containsKey(key(o));
    }

    @Override
    public boolean add(String tag) {
        return map.put(key(tag), tag) == null;
    }

    @Override
    public boolean remove(Object o) {
        return map.remove(key(o)) == o;
    }

    @Override
    public void clear() {
        map.clear();
    }

    public HashtagSet clone() {
        try {
            HashtagSet newSet = (HashtagSet) super.clone();
            newSet.map = (HashtagMap<String>) map.clone();
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String key(Object tag) {
        return String.valueOf(tag).toLowerCase(LocaleUtil.getCurrent());
    }
}
