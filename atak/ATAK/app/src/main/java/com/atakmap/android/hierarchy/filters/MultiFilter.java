
package com.atakmap.android.hierarchy.filters;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;

import java.util.List;

/**
 * Filter for combining multiple filters (with a single sort)
 */
public class MultiFilter extends HierarchyListFilter {

    private static final String TAG = "MultiFilter";

    private final HierarchyListFilter[] filters;

    public MultiFilter(HierarchyListItem.Sort sort,
            List<HierarchyListFilter> filters) {
        super(sort);
        this.filters = filters.toArray(new HierarchyListFilter[0]);
    }

    public HierarchyListFilter find(Class<?> filterType) {
        for (HierarchyListFilter f : this.filters) {
            if (f.getClass() == filterType)
                return f;
        }
        return null;
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        for (HierarchyListFilter f : this.filters) {
            if (!f.accept(item))
                return false;
        }
        return true;
    }

    @Override
    public boolean acceptEntry(HierarchyListItem list) {
        for (HierarchyListFilter f : this.filters) {
            if (!f.acceptEntry(list))
                return false;
        }
        return true;
    }

    @Override
    public boolean isDefaultFilter() {
        for (HierarchyListFilter f : this.filters) {
            if (!f.isDefaultFilter())
                return false;
        }
        return true;
    }
}
