
package com.atakmap.android.hierarchy.filters;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem2;

import com.atakmap.coremap.log.Log;

/**
 * Filter out lists with no children items
 */
public class EmptyListFilter extends HierarchyListFilter {
    private static final String TAG = "EmptyListFilter";

    public EmptyListFilter() {
        super(null);
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        return !isEmptyList(item);
    }

    @Override
    public boolean isDefaultFilter() {
        return true;
    }

    public static boolean isEmptyList(HierarchyListItem item) {
        try {
            return item == null || item.isChildSupported()
                    && item.getChildCount() < 1
                    && item instanceof HierarchyListItem2
                    && ((HierarchyListItem2) item).hideIfEmpty();
        } catch (IllegalStateException e) {
            // ATAK-12110 if the feature throws an invalid state exception
            // becase the pointer is no longer valid.
            Log.e(TAG, "error", e);
            return true;
        }
    }
}
