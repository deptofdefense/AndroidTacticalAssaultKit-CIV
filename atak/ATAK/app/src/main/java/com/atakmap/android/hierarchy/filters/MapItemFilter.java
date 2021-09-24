
package com.atakmap.android.hierarchy.filters;

import com.atakmap.android.features.FeatureSetHierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.maps.MapItem;

/**
 * A filter that only accepts map items
 * Meant to be extended for specific map item filtering
 */
public abstract class MapItemFilter extends HierarchyListFilter {

    protected MapItemFilter(HierarchyListItem.Sort sort) {
        super(sort);
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        if (!(item instanceof MapItemUser)) {
            // If this isn't based on a map item, then reject individual items
            // and accept lists using the isChildSupported check
            // Specifically reject feature sets since they don't count as
            // real map items
            return item.isChildSupported()
                    && !(item instanceof FeatureSetHierarchyListItem);
        }

        MapItem mi = ((MapItemUser) item).getMapItem();
        if (mi == null)
            return false;

        return accept(mi);
    }

    /**
     * Check if this filter accepts a certain map item
     * @param item Map item
     * @return True if accepted
     */
    public abstract boolean accept(MapItem item);
}
