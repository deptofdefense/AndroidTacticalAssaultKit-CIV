
package com.atakmap.android.hierarchy.filters;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Generic search filter
 */
public class SearchFilter extends HierarchyListFilter {

    private final String _terms;

    public SearchFilter(String terms) {
        super(null);
        _terms = terms.toLowerCase(LocaleUtil.getCurrent());
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        if (FileSystemUtils.isEmpty(_terms))
            return true;
        // First search title
        if (item.getTitle() != null && item.getTitle()
                .toLowerCase(LocaleUtil.getCurrent()).contains(_terms))
            return true;
        // Then search description
        if (item instanceof HierarchyListItem2) {
            HierarchyListItem2 item2 = (HierarchyListItem2) item;
            if (item2.getDescription() != null && item2.getDescription()
                    .toLowerCase(LocaleUtil.getCurrent()).contains(_terms))
                return true;
        }
        // Then search map item
        if (item instanceof MapItemUser) {
            MapItem mi = ((MapItemUser) item).getMapItem();
            if (mi != null && ATAKUtilities.getDisplayName(mi)
                    .toLowerCase(LocaleUtil.getCurrent()).contains(_terms))
                return true;
        }
        // Finally search user object
        if (item.getUserObject() != null)
            return item.getUserObject().toString()
                    .toLowerCase(LocaleUtil.getCurrent())
                    .contains(_terms);
        return false;
    }
}
