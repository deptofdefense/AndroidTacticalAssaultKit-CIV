
package com.atakmap.android.hierarchy.items;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Visibility2;

/**
 * Extension of {@link AbstractHierarchyListItem2} where children items
 * are not supported
 */
public abstract class AbstractChildlessListItem extends
        AbstractHierarchyListItem2 {

    @Override
    public boolean isChildSupported() {
        return false;
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        return null;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public HierarchyListFilter refresh(final HierarchyListFilter filter) {
        refreshImpl();
        return filter;
    }

    @Override
    protected void refreshImpl() {
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    @Override
    public void dispose() {
    }

    @Override
    public int getVisibility() {
        return isVisible() ? Visibility2.VISIBLE : Visibility2.INVISIBLE;
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}
