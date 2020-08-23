
package com.atakmap.android.cotdetails.extras;

import android.view.View;

import com.atakmap.android.maps.MapItem;

/**
 * Provider for extra views in map item details drop-down
 */
public interface ExtraDetailsProvider {

    /**
     * Get extra view to display in the details
     * @param item Associated map item
     * @param existing Existing view returned by this method
     * @return Extra view
     */
    View getExtraView(MapItem item, View existing);
}
