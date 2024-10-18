
package com.atakmap.android.hierarchy;

import androidx.annotation.NonNull;

/**
 * Generic filter that accepts all items
 * Extend this class for other filters
 */
public class HierarchyListFilter {

    public HierarchyListItem.Sort sort;

    public HierarchyListFilter(HierarchyListItem.Sort sort) {
        this.sort = sort;
    }

    /**
     * Test to accept item into children/descendants list
     * @param item Item to run filter on
     * @return True if item is accepted (displayed), false otherwise (hidden)
     */
    public boolean accept(HierarchyListItem item) {
        return true;
    }

    /**
     * Test to allow user to enter this item's sub-list
     * @param list List item
     * @return True to allow entry into sub-list, false otherwise
     */
    public boolean acceptEntry(HierarchyListItem list) {
        return true;
    }

    /**
     * True if this filter is a default filter
     * How this is used is based on the implementation
     * @return True if this filter is default
     */
    public boolean isDefaultFilter() {
        return getClass() == HierarchyListFilter.class;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " (" + sort.toString() + ")";
    }
}
