
package com.atakmap.android.devtools;

import android.view.View;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;

abstract class DevToolToggle
        implements HierarchyListItem, Visibility2, Visibility {
    private final String _title;
    private final String _uid;

    DevToolToggle(String title, String uid) {
        _title = title;
        _uid = uid;
    }

    protected abstract boolean isEnabled();

    protected abstract void setEnabled(boolean v);

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
        return 0;
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        return null;
    }

    @Override
    public boolean isChildSupported() {
        return false;
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
    public HierarchyListItem.Sort refresh(HierarchyListItem.Sort sortHint) {
        return null;
    }

    @Override
    public int getVisibility() {
        return isVisible() ? Visibility2.VISIBLE : Visibility2.INVISIBLE;
    }

    @Override
    public final boolean setVisible(boolean visible) {
        this.setEnabled(visible);
        return true;
    }

    @Override
    public final boolean isVisible() {
        return this.isEnabled();
    }
}
