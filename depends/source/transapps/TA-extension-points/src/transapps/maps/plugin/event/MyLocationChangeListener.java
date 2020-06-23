package transapps.maps.plugin.event;

import transapps.geom.GeoPoint;

/**
 * Extension point for things that do work when the my location changes
 * 
 * @author mriley
 */
public interface MyLocationChangeListener {

    /**
     * Notified when the device location changes 
     * 
     * @param newLocation
     */
    void onLocationChanged( GeoPoint newLocation );
}
