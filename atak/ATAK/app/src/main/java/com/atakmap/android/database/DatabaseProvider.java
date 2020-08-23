
package com.atakmap.android.database;

import androidx.annotation.NonNull;

import com.atakmap.database.DatabaseIface;
import com.atakmap.spi.ServiceProvider;

/**
 * Implementation of a Database Provider that can be used to create encrypted databases.
 */
public interface DatabaseProvider
        extends ServiceProvider<DatabaseIface, DatabaseInformation> {

    /**
     * Needs to be unique and consistent across all providers since it it used as the prefix for the
     * database name
     * @return the unique and consistent prefix
     */
    @NonNull
    String getPrefix();

}
