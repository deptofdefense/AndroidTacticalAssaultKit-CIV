
package com.atakmap.app.system;

import com.atakmap.android.maps.MapComponent;

public interface MapComponentProvider {

    /**
     * MapComponent to be loaded after all of the other map components have been loaded.
     */
    MapComponent[] getMapComponents();
}
