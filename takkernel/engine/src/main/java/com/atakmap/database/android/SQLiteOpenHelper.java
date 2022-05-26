package com.atakmap.database.android;

import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;

import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;

import java.io.File;

/**
 * An <I>almost drop-in</I> for {@link android.database.sqlite.SQLiteOpenHelper}, using the TAK APIs.
 */
public abstract class SQLiteOpenHelper {
    private static final String TAG = SQLiteOpenHelper.class.getSimpleName();
    private final String mName;
    private final int mNewVersion;
    private final int mMinimumSupportedVersion;
    private OpenHelperDatabase mDatabase;
    private boolean mIsInitializing;
    private final DatabaseInformation mDatabaseInformation;
    /**
     * Create a helper object to create, open, and/or manage a database.
     * This method always returns very quickly.  The database is not actually
     * created or opened until one of {@link #getWritableDatabase} or
     * {@link #getReadableDatabase} is called.
     *
     * @param name of the database file, or null for an in-memory database
     * @param version number of the database (starting at 1); if the database is older,
     *     {@link #onUpgrade} will be used to upgrade the database; if the database is
     *     newer, {@link #onDowngrade} will be used to downgrade the database
     */
    public SQLiteOpenHelper(String name, int version, boolean reloadOnProviderChange) {
        if (version < 1) throw new IllegalArgumentException("Version must be >= 1, was " + version);
        mName = name;
        mNewVersion = version;
        mMinimumSupportedVersion = 0;
        if(mName == null) {
            // in-memory
            mDatabaseInformation = null;
        } else {
            mDatabaseInformation = new DatabaseInformation(Uri.fromFile(new File(mName)),
                    DatabaseInformation.OPTION_ENSURE_PARENT_DIRS);
        }
    }
    /**
     * Return the name of the SQLite database being opened, as given to
     * the constructor.
     */
    public String getDatabaseName() {
        return mName;
    }
    /**
     * Create and/or open a database that will be used for reading and writing.
     * The first time this is called, the database will be opened and
     * {@link #onCreate}, {@link #onUpgrade} and/or {@link #onOpen} will be
     * called.
     *
     * <p>Once opened successfully, the database is cached, so you can
     * call this method every time you need to write to the database.
     * (Make sure to call {@link #close} when you no longer need the database.)
     * Errors such as bad permissions or a full disk may cause this method
     * to fail, but future attempts may succeed if the problem is fixed.</p>
     *
     * <p class="caution">Database upgrade may take a long time, you
     * should not call this method from the application main thread, including
     * from {@link android.content.ContentProvider#onCreate ContentProvider.onCreate()}.
     *
     * @throws SQLiteException if the database cannot be opened for writing
     * @return a read/write database object valid until {@link #close} is called
     */
    public DatabaseIface getWritableDatabase() {
        synchronized (this) {
            return getDatabaseLocked(true);
        }
    }
    /**
     * Create and/or open a database.  This will be the same object returned by
     * {@link #getWritableDatabase} unless some problem, such as a full disk,
     * requires the database to be opened read-only.  In that case, a read-only
     * database object will be returned.  If the problem is fixed, a future call
     * to {@link #getWritableDatabase} may succeed, in which case the read-only
     * database object will be closed and the read/write object will be returned
     * in the future.
     *
     * <p class="caution">Like {@link #getWritableDatabase}, this method may
     * take a long time to return, so you should not call it from the
     * application main thread, including from
     * {@link android.content.ContentProvider#onCreate ContentProvider.onCreate()}.
     *
     * @throws SQLiteException if the database cannot be opened
     * @return a database object valid until {@link #getWritableDatabase}
     *     or {@link #close} is called.
     */
    public DatabaseIface getReadableDatabase() {
        synchronized (this) {
            return getDatabaseLocked(false);
        }
    }
    private DatabaseIface getDatabaseLocked(boolean writable) {
        if (mDatabase != null) {
            if (!mDatabase.isOpen()) {
                // Darn!  The user closed the database by calling mDatabase.close().
                mDatabase = null;
            } else if (!writable || !mDatabase.isReadOnly()) {
                // The database is already open for business.
                return mDatabase;
            }
        }
        if (mIsInitializing) {
            throw new IllegalStateException("getDatabase called recursively");
        }
        DatabaseIface db = mDatabase;
        try {
            mIsInitializing = true;
            if (db != null) {
                if (writable && db.isReadOnly()) {
                    ((OpenHelperDatabase)db).reopenReadWrite();
                }
            } else if (mName == null) {
                // open in-memory, always R/W since it cannot be reopened
                db = Databases.openDatabase(null, false);
            } else {
                final File filePath = new File(mName);
                try {
                    db = IOProviderFactory.createDatabase(mDatabaseInformation);
                } catch (SQLException ex) {
                    if (writable) {
                        throw ex;
                    }
                    Log.e(TAG, "Couldn't open " + mName
                            + " for writing (will try read-only):", ex);
                    db = IOProviderFactory.createDatabase(new DatabaseInformation(
                            mDatabaseInformation.uri,
                            DatabaseInformation.OPTION_READONLY));
                }
            }
            onConfigure(db);
            final int version = db.getVersion();
            if (version != mNewVersion) {
                if (db.isReadOnly()) {
                    throw new SQLiteException("Can't upgrade read-only database from version " +
                            db.getVersion() + " to " + mNewVersion + ": " + mName);
                }
                if (version > 0 && version < mMinimumSupportedVersion) {
                    File databaseFile = new File(mName);
                    onBeforeDelete(db);
                    db.close();
                    if (IOProviderFactory.delete(databaseFile, IOProvider.SECURE_DELETE)) {
                        mIsInitializing = false;
                        return getDatabaseLocked(writable);
                    } else {
                        throw new IllegalStateException("Unable to delete obsolete database "
                                + mName + " with version " + version);
                    }
                } else {
                    db.beginTransaction();
                    try {
                        if (version == 0) {
                            onCreate(db);
                        } else {
                            if (version > mNewVersion) {
                                onDowngrade(db, version, mNewVersion);
                            } else {
                                onUpgrade(db, version, mNewVersion);
                            }
                        }
                        db.setVersion(mNewVersion);
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                }
            }
            onOpen(db);
            if (db.isReadOnly()) {
                Log.w(TAG, "Opened " + mName + " in read-only mode");
            }
            if(mDatabase != db) {
                mDatabase = new OpenHelperDatabase((mName != null) ? new File(mName) : null, db);
                db = mDatabase;
            }
            return db;
        } finally {
            mIsInitializing = false;
            if (db != null && db != mDatabase) {
                db.close();
            }
        }
    }
    /**
     * Close any open database object.
     */
    public synchronized void close() {
        if (mIsInitializing) throw new IllegalStateException("Closed during initialization");
        if (mDatabase != null && mDatabase.isOpen()) {
            mDatabase.close();
            mDatabase = null;
        }
    }
    /**
     * Called when the database connection is being configured, to enable features such as
     * write-ahead logging or foreign key support.
     * <p>
     * This method is called before {@link #onCreate}, {@link #onUpgrade}, {@link #onDowngrade}, or
     * {@link #onOpen} are called. It should not modify the database except to configure the
     * database connection as required.
     * </p>
     * <p>
     * This method should only call methods that configure the parameters of the database
     * connection, such as setting the journal mode.
     * </p>
     *
     * @param db The database.
     */
    public void onConfigure(DatabaseIface db) {}
    /**
     * Called before the database is deleted when the version returned by
     * {@link DatabaseIface#getVersion()} is lower than the minimum supported version passed (if at
     * all) while creating this helper. After the database is deleted, a fresh database with the
     * given version is created. This will be followed by {@link #onConfigure(DatabaseIface)} and
     * {@link #onCreate(DatabaseIface)} being called with a new DatabaseIface object
     *
     * @param db the database opened with this helper
     * @hide
     */
    public void onBeforeDelete(DatabaseIface db) {
    }
    /**
     * Called when the database is created for the first time. This is where the
     * creation of tables and the initial population of the tables should happen.
     *
     * @param db The database.
     */
    public abstract void onCreate(DatabaseIface db);
    /**
     * Called when the database needs to be upgraded. The implementation
     * should use this method to drop tables, add tables, or do anything else it
     * needs to upgrade to the new schema version.
     *
     * <p>
     * The SQLite ALTER TABLE documentation can be found
     * <a href="http://sqlite.org/lang_altertable.html">here</a>. If you add new columns
     * you can use ALTER TABLE to insert them into a live table. If you rename or remove columns
     * you can use ALTER TABLE to rename the old table, then create the new table and then
     * populate the new table with the contents of the old table.
     * </p><p>
     * This method executes within a transaction.  If an exception is thrown, all changes
     * will automatically be rolled back.
     * </p>
     *
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    public abstract void onUpgrade(DatabaseIface db, int oldVersion, int newVersion);
    /**
     * Called when the database needs to be downgraded. This is strictly similar to
     * {@link #onUpgrade} method, but is called whenever current version is newer than requested one.
     * However, this method is not abstract, so it is not mandatory for a customer to
     * implement it. If not overridden, default implementation will reject downgrade and
     * throws SQLiteException
     *
     * <p>
     * This method executes within a transaction.  If an exception is thrown, all changes
     * will automatically be rolled back.
     * </p>
     *
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    public void onDowngrade(DatabaseIface db, int oldVersion, int newVersion) {
        throw new SQLiteException("Can't downgrade database from version " +
                oldVersion + " to " + newVersion);
    }
    /**
     * Called when the database has been opened.  The implementation
     * should check {@link DatabaseIface#isReadOnly} before updating the
     * database.
     * <p>
     * This method is called after the database connection has been configured
     * and after the database schema has been created, upgraded or downgraded as necessary.
     * If the database connection must be configured in some way before the schema
     * is created, upgraded, or downgraded, do it in {@link #onConfigure} instead.
     * </p>
     *
     * @param db The database.
     */
    public void onOpen(DatabaseIface db) {}
}
