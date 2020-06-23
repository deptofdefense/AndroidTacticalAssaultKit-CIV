
package com.atakmap.android.hierarchy.items;

import android.graphics.Color;

import com.atakmap.android.hierarchy.HierarchyListItem;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractHierarchyListItem implements HierarchyListItem {

    private final Map<String, Object> localData;

    protected AbstractHierarchyListItem() {
        this.localData = new HashMap<>();
    }

    @Override
    public String getUID() {
        return getTitle();
    }

    @Override
    public int getPreferredListIndex() {
        return -1;
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public int getIconColor() {
        return Color.WHITE;
    }

    @Override
    public String getIconUri() {
        return null;
    }

    @Override
    public final Object setLocalData(String s, Object o) {
        return this.localData.put(s, o);
    }

    @Override
    public final Object getLocalData(String s) {
        return this.localData.get(s);
    }

    @Override
    public final <T> T getLocalData(String s, Class<T> clazz) {
        return (T) this.getLocalData(s);
    }
}
