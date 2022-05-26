
package com.atakmap.coremap.io;

import android.net.Uri;

/**
 * Encapsulation of the several parameters used when opening a database connection.
 *
 * <P>URIs are typically expected to be file URIs (e.g.
 * "file:///path/to/file"). A scheme of "memory" (e.g. "memory://") may be used
 * to open in-memory databases.
 */
public final class DatabaseInformation {

    public static final String TAG = "DatabaseInformation";

    /** if the bit is set, the database will be opened in read-only mode */
    public static final int OPTION_READONLY = 0x01;
    public static final int OPTION_ENSURE_PARENT_DIRS = 0x02;

    public static final String SCHEME_MEMORY = "memory";

    // XXX - reserved bitmasks to support two legacy encryption cases in the
    //       face of the "default" provider which should not be performing
    //       encryption per the contract, but where ATAK has historically
    //       performed off-the-shelf encryption
    // #1 DB encrypted with passphrase stored in authdb
    // #2 DB encryption for authdb/certdb
    public static final int OPTION_RESERVED1 = 0x04;
    public static final int OPTION_RESERVED2 = 0x08;

    public final Uri uri;
    public final int options;

    /**
     * The information used when calling create on the database factory.
     * @param uri the uri used to reference the database
     */
    public DatabaseInformation(final Uri uri) {
        this(uri, 0);
    }

    /**
     * The information used when calling create on the database factory.
     * @param uri the uri used to reference the database
     * @param options   The bitmask containing options for opening the database
     */
    public DatabaseInformation(final Uri uri,
            final int options) {
        if (uri == null)
            throw new IllegalArgumentException("Uri cannot be null");

        this.uri = uri;
        this.options = options;
    }

    /**
     * Returns <code>true</code> true if the info indicates a memory database.
     * @param info  The info
     * @return  <code>true</code> if a memory DB, <code>falese</code> otherwise
     */
    public static boolean isMemoryDatabase(DatabaseInformation info) {
        return (info != null && info.uri != null
                && SCHEME_MEMORY.equals(info.uri.getScheme()));
    }
}
