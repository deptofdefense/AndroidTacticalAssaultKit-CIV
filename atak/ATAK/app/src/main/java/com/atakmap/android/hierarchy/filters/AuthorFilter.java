
package com.atakmap.android.hierarchy.filters;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Filter for showing all items that were created by a given user
 */
public class AuthorFilter extends MapItemFilter {

    private final String authorUID;

    public AuthorFilter(HierarchyListItem.Sort sort, String authorUID) {
        super(sort);
        this.authorUID = authorUID;
    }

    @Override
    public boolean accept(MapItem item) {
        return FileSystemUtils.isEquals(this.authorUID,
                item.getMetaString("parent_uid", null));
    }
}
