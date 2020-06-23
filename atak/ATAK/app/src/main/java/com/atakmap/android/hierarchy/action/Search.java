
package com.atakmap.android.hierarchy.action;

import com.atakmap.android.hierarchy.HierarchyListItem;

import java.util.Set;

public interface Search extends Action {
    Set<HierarchyListItem> find(String terms);
}
