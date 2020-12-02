
package com.atakmap.android.database;

import android.database.sqlite.SQLiteException;
import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

class DefaultDatabaseProvider implements DatabaseProvider {

    private static final String TAG = "DatabaseProvider";

    /**
     * Needs to be unique and consistent across all providers since it it used as the prefix for the
     * database name
     * @return the unique and consistent prefix
     */
    public String getPrefix() {
        return "";
    }

    /**
     * Determines whether database given by its path is a SQLite database
     * @param path the path to database
     * @return true if the given path points to a database; false, otherwise
     */
    public boolean isDatabase(String path) {
        return Databases.isSQLiteDatabase(path);
    }

    /**
     * Creates a DatabaseIface from provided DatabaseInformation.
     *
     * @param dbi the key information required for creating a
     * DatabaseIface object.
     *
     * @return  An instance of the DatabaseIface if successfuly,
     *          <code>null</code> otherwise.
     */
    public final DatabaseIface create(final DatabaseInformation dbi) {
        if (dbi == null)
            return null;

        final Uri uri = dbi.getUri();

        final String scheme = uri.getScheme();

        if (scheme == null || !scheme.equals("file"))
            return null;

        DatabaseIface db = Databases.openOrCreateDatabase(uri.getPath());

        final AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        "com.atakmap.app.v2");

        if (credentials != null
                && !FileSystemUtils.isEmpty(credentials.username)) {
            db.execute("PRAGMA key = '" + credentials.password + "'",
                    null);
        } else {
            db.execute("PRAGMA key = '" + "'", null);
        }

        // check to see if the database is valid
        CursorIface ci = null;
        try {
            ci = db.query("SELECT count(*) FROM sqlite_master", null);
            long ret = -1;

            if (ci.moveToNext())
                ret = ci.getLong(0);
            if (ret == -1) {
                Log.d(TAG, "database error");
                try {
                    db.close();
                } catch (Exception ignored) {
                }
                return null;
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "corrupt database", e);
            try {
                db.close();
            } catch (Exception ignored) {
            }

            return null;
        } finally {
            if (ci != null)
                ci.close();
        }
        return db;

    }

    /**
     * Determine the Uri for the DatabaseProvider that has been altered based on the prefix from the DatabaseInformation.
     * @param uri the uri used to reference the database
     * @throws IllegalArgumentException the uri passed in is invalid, it will throw an IllegalArgumentException.
     */
     private Uri determineUriForProvider(Uri uri) {
        final String s = uri.toString();
        final String fileName = uri.getLastPathSegment();

        if (fileName != null) {
            return Uri.parse(s.replace(fileName,
                    FileSystemUtils
                            .sanitizeFilename(this.getPrefix() + fileName)));
        }

        throw new IllegalArgumentException("invalid database name: " + uri);
    }

}
