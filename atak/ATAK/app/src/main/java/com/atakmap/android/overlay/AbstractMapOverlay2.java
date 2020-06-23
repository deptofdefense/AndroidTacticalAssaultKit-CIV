
package com.atakmap.android.overlay;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;

/**
 * Abstract version for compatibility purposes
 */
public abstract class AbstractMapOverlay2 implements MapOverlay2 {
    @Override
    public HierarchyListItem getListModel(BaseAdapter callback, long actions,
            HierarchyListItem.Sort sort) {
        return getListModel(callback, actions, new HierarchyListFilter(sort));
    }
}
