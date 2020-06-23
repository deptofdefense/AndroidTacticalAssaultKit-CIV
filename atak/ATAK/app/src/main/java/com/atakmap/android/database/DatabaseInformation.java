
package com.atakmap.android.database;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.atakmap.coremap.filesystem.FileSystemUtils;

public class DatabaseInformation {

    public static final String TAG = "DatabaseInformation";

    private final Uri uri;
    public final ProviderChangeRequestedListener pcrl;

    /**
     * The information used when calling create on the database factory.
     * @param uri the uri used to reference the database
     * @param pcrl the callback that indicates when the database provider change has been requested.
     */
    public DatabaseInformation(final @NonNull Uri uri,
            final @NonNull ProviderChangeRequestedListener pcrl) {
        if (uri == null)
            throw new IllegalArgumentException("Uri cannot be null");

        if (pcrl == null)
            throw new IllegalArgumentException(
                    "ProviderChangeRequestedListener cannot be null");

        this.uri = uri;
        this.pcrl = pcrl;
    }

    /**
     * Get the Uri for the DatabaseInformation that has been altered based on the DatabaseProvider prefix.
     * This allows for users of the DatabaseFactory not have to know how to generate unique names for the
     * database providers.
     * @param dp the DatabaseProvider used to generate the uri for the database using this specific provider.
     * @throws IllegalArgumentException the uri passed in is invalid, it will throw an IllegalArgumentException.
     */
    final public Uri getUri(final DatabaseProvider dp) {
        final String s = uri.toString();
        final String fileName = uri.getLastPathSegment();

        if (fileName != null) {
            return Uri.parse(s.replace(fileName,
                    FileSystemUtils
                            .sanitizeFilename(dp.getPrefix() + fileName)));
        }

        throw new IllegalArgumentException("invalid database name: " + uri);
    }

}
