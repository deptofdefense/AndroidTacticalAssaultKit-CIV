
package com.atakmap.android.menu;

import com.atakmap.android.maps.MapItem;

/**
 * Interface for the creation of MapMenuWidget instances.
 * A MapItem instance is provided so that the users may associate
 * MapMenuWidget specializations with MapItem attributes. Factories
 * meeting this interface are queried in the order of their registration
 * with the <a href=#{@link}>{@link MapMenuReceiver}</a>.
 * The first factory to return a non-null instance will
 * be providing the MapMenuWidget instance to be rendered.
 */
public interface MapMenuFactory {

    /**
     * Create a MapMenuWidget using attribution from a MapItem.
     * @param item providing attribution for menu creation logic.
     * @return fully formed MapMenuWidget instance.
     */
    MapMenuWidget create(MapItem item);
}
