
package com.atakmap.android.maps;

/**
 * A map item that has an anchor marker
 */
public interface AnchoredMapItem {

    /**
     * Get the anchor marker for this item
     * @return Anchor marker
     */
    PointMapItem getAnchorItem();
}
