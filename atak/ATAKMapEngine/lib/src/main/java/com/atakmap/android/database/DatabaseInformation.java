
package com.atakmap.android.database;

import android.net.Uri;

public class DatabaseInformation {

    public static final String TAG = "DatabaseInformation";

    private final Uri uri;
    public final ProviderChangeRequestedListener pcrl;

    /**
     * The information used when calling create on the database factory.
     * @param uri the uri used to reference the database
     * @param pcrl the callback that indicates when the database provider change has been requested.
     */
    public DatabaseInformation(final Uri uri,
            final ProviderChangeRequestedListener pcrl) {
        if (uri == null)
            throw new IllegalArgumentException("Uri cannot be null");

        if (pcrl == null)
            throw new IllegalArgumentException(
                    "ProviderChangeRequestedListener cannot be null");

        this.uri = uri;
        this.pcrl = pcrl;
    }

    /**
     * Gets the uri used to reference the database
     * @return uri used to reference the database
     */
    final public Uri getUri() {
        return this.uri;
    }
}
