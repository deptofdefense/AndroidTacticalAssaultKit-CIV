
package com.atakmap.android.contact;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;

/**
 * Filter for contacts based on if they are currently filtered or not
 */

public final class FilteredContactsFilter extends HierarchyListFilter {

    public static final String PREF = "filteredContacts_filter";

    public FilteredContactsFilter(HierarchyListItem.Sort sort) {
        super(sort);
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        if (!(item instanceof Contact))
            return false;
        if (item instanceof GroupContact)
            return true;
        return !FilteredContactsManager.getInstance()
                .isContactFiltered((Contact) item);
    }
}
