
package com.atakmap.android.user.icon;

import androidx.fragment.app.Fragment;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * An icon pallet that allows user to drop markers/points
 * 
 * 
 */
public interface IconPallet {

    class CreatePointException extends Exception {
        public CreatePointException(String message) {
            super(message);
        }
    }

    /**
     * Get title/label for this pallet
     * @return the human readable name for this pallet, should be shorter than 14
     * characters
     */
    String getTitle();

    /**
     * Get unique ID for this pallet.   
     * @return the recommended return is a UUID that has been pregenerated and 
     * remains static through the lifespan of this implementation.
     */
    String getUid();

    /**
     * Generate a map item at the specified point.   Does not need to be 
     * implemented if the Fragment is responsible for all of the map interactions.
     * Note, the intent should have a "uid" extra for the point to be created
     * 
     * @param point the geopoint that the user selected for the point.
     * @return the MapItem that is a direct result from the touch of the map.
     */
    MapItem getPointPlacedIntent(GeoPointMetaData point, String uid)
            throws CreatePointException;

    /**
     * Provides fragment containing the view to select point details.   It is 
     * suggested that this fragment fit within a 1/3 width of the screen.
     * 
     * @return The Android fragment that defines this Pallet.
     */
    Fragment getFragment();

    /**
     * Select an icon by resource id
     * @param resId The button resource id or '0' if no default.   For most 
     * user defined pallets, this implementation will or can be empty.
     */
    void select(int resId);

    /**
     * Clear current user selection
     */
    void clearSelection(boolean bPauseListener);

    /**
     * Indicates drop down is being displayed again, allow pallets opportunity 
     * to refresh
     */
    void refresh();
}
