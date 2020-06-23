
package com.atakmap.android.importexport.handlers;

import com.atakmap.android.maps.MapGroup;

/**
 * A map item that uses a child group for managing sub-items
 */
public interface ParentMapItem {

    /**
     * Get this item's child map group
     * @return Child map group
     */
    MapGroup getChildMapGroup();
}
