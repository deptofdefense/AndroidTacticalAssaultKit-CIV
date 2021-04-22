
package com.atakmap.android.devtools;

import android.view.View;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;

import java.util.ArrayList;
import java.util.UUID;

class DevToolGroup implements HierarchyListItem {
    protected final ArrayList<HierarchyListItem> _children;
    private final String _title;
    private final String _uid;

    public DevToolGroup(String title) {
        this(title, UUID.randomUUID().toString());
    }

    protected DevToolGroup(String title, String uid) {
        _title = title;
        _uid = uid;

        _children = new ArrayList<>();
    }

    @Override
    public final String getUID() {
        return _uid;
    }

    @Override
    public final String getTitle() {
        return _title;
    }

    @Override
    public int getPreferredListIndex() {
        return -1;
    }

    @Override
    public int getChildCount() {
        return _children.size();
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        if (index < 0 || index >= _children.size())
            return null;
        return _children.get(index);
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public String getIconUri() {
        return null;
    }

    @Override
    public int getIconColor() {
        return -1;
    }

    @Override
    public Object setLocalData(String s, Object o) {
        return null;
    }

    @Override
    public Object getLocalData(String s) {
        return null;
    }

    @Override
    public <T> T getLocalData(String s, Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.isAssignableFrom(this.getClass()))
            return (T) this;
        return null;
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public Sort refresh(Sort sortHint) {
        return null;
    }
}
