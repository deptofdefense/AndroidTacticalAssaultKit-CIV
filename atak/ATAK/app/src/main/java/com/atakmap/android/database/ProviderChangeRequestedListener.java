
package com.atakmap.android.database;

public interface ProviderChangeRequestedListener {

    int NEW_DEFAULT = 1;
    int REMOVED = 2;

    /**
     * Fired whenenver there is a change to the Database provider.   This is expected to be a blocking
     * call to allow the implementation to appropriately take action prior to the DataProvider being changed.
     * @param provider The provider that has caused the change to the Factory
     * @param change the type of change that has occured { NEW_DEFAULT, REMOVED }
     */
    void onProviderChangeRequested(DatabaseProvider provider,
            int change);
}
