
package com.atakmap.android.contact;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;

/**
 * Filter for contacts based on type
 */

public class UnreadFilter extends HierarchyListFilter {

    public static final String PREF = "unread_filter";

    public UnreadFilter(HierarchyListItem.Sort sort) {
        super(sort);
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        if (!(item instanceof Contact))
            return false;
        if (item instanceof GroupContact) {
            GroupContact gc = (GroupContact) item;
            return gc.alwaysShow() || gc.getUnreadCount(true) > 0;
        }
        return ((Contact) item).getExtras().getBoolean("metaGroup", false)
                || ((Contact) item).getUnreadCount() > 0;
    }
}
