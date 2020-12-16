
package com.atakmap.android.database;

import android.database.sqlite.SQLiteException;
import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.math.MathUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

import java.io.File;

class DefaultDatabaseProvider {

    private static final String TAG = "DatabaseProvider";

    /**
     * Determines whether database given by its path is a SQLite database
     * @param path the path to database
     * @return true if the given path points to a database; false, otherwise
     */
    public boolean isDatabase(File path) {
        return Databases.isSQLiteDatabase(path.getAbsolutePath());
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

        final Uri uri = dbi.uri;

        final String scheme = uri.getScheme();

        if (scheme == null || !scheme.equals("file"))
            return null;

        DatabaseIface db;
        if(MathUtils.hasBits(dbi.options, DatabaseInformation.OPTION_READONLY))
            db = Databases.openDatabase(uri.getPath(), true);
        else
            db = Databases.openOrCreateDatabase(uri.getPath());

        if(MathUtils.hasBits(dbi.options, DatabaseInformation.OPTION_RESERVED1)) {
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
        }
        return db;
    }
}
