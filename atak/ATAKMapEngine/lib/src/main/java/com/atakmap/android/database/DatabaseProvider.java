
package com.atakmap.android.database;

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
    String getPrefix();

    /**
     * Determines whether database given by its path is a SQLite database
     * @param path the path to database
     * @return true if the given path points to a database; false, otherwise
     */
    boolean isDatabase(String path);
}
