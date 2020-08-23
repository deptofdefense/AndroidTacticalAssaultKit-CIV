
package com.atakmap.android.cotdetails.extras;

/**
 * Listens for events related to extra details management
 */
public interface ExtraDetailsListener {

    /**
     * A details provider has been added to the manager
     * @param provider New provider
     */
    void onProviderAdded(ExtraDetailsProvider provider);

    /**
     * A details provider has been changed
     * @param provider The affected provider
     */
    void onProviderChanged(ExtraDetailsProvider provider);

    /**
     * A details provider has been removed from the manager
     * @param provider Removed provider
     */
    void onProviderRemoved(ExtraDetailsProvider provider);
}
