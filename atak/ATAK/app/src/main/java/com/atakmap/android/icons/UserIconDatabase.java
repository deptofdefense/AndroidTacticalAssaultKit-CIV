
package com.atakmap.android.icons;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.atakmap.coremap.filesystem.SecureDelete;
import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.StatementIface;
import com.atakmap.database.android.AndroidDatabaseAdapter;
import com.atakmap.database.android.SQLiteOpenHelper;
import com.atakmap.database.impl.DatabaseImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class for user icon database
 * 
 */
public class UserIconDatabase extends SQLiteOpenHelper {

    private static final String TAG = "UserIconDatabase";

    protected final static int DATABASE_VERSION = 2;

    public final static String TABLE_ICONS = "icons";
    public final static String TABLE_ICONSETS = "iconsets";

    public static final String DATABASE_NAME;

    // Database creation sql statement
    private static final String ICONS_CREATE;
    private static final String ICONSETS_CREATE;

    static {
        File dbfile = FileSystemUtils.getItem(
                "Databases" + File.separatorChar + "iconsets.sqlite");
        DATABASE_NAME = dbfile.getAbsolutePath();

        if (!IOProviderFactory.exists(dbfile.getParentFile()))
            IOProviderFactory.mkdirs(dbfile.getParentFile());

        StringBuilder temp = new StringBuilder(
                "CREATE TABLE IF NOT EXISTS " + TABLE_ICONS
                        + "(");
        for (int i = 0; i < UserIcon.META_DATA_LABELS.length; i++) {
            temp.append(UserIcon.META_DATA_LABELS[i][0]).append(" ")
                    .append(UserIcon.META_DATA_LABELS[i][1]);
            if (i + 1 < UserIcon.META_DATA_LABELS.length)
                temp.append(", ");
        }
        temp.append(");");
        ICONS_CREATE = temp.toString();

        temp = new StringBuilder(
                "CREATE TABLE IF NOT EXISTS " + TABLE_ICONSETS + "(");
        for (int i = 0; i < UserIconSet.META_DATA_LABELS.length; i++) {
            temp.append(UserIconSet.META_DATA_LABELS[i][0]).append(" ")
                    .append(UserIconSet.META_DATA_LABELS[i][1]);
            if (i + 1 < UserIconSet.META_DATA_LABELS.length)
                temp.append(", ");
        }
        temp.append(");");
        ICONSETS_CREATE = temp.toString();
    }

    private static UserIconDatabase instance;
    private final Context _context;

    synchronized public static UserIconDatabase instance(Context context) {
        if (instance == null) {
            instance = new UserIconDatabase(context);

            //a simple query to force DB creation, so it can be added to
            //GL layer
            try {
                instance.getIcon("", "", false);
            } catch (Exception e) {
                // ATAK-8048 Failure to Initialize IconSet Database causes cascading failures during initialization.
                Log.d(TAG, "user iconset database is corrupted", e);
                try {
                    instance.close();
                } catch (Exception ce) {
                    Log.d(TAG, "close the corrupted database", ce);
                }
                FileSystemUtils.delete(new File(DATABASE_NAME));
                try {
                    instance.getIcon("", "", false);
                } catch (Exception e1) {
                    Log.d(TAG, "deletion and attempts to reinitialize failed",
                            e1);
                }
            }

            GLBitmapLoader.mountDatabase(DATABASE_NAME);
        }
        return instance;
    }

    // this object does not cache any state, so reloading the connection on
    // provider change is sufficient

    private UserIconDatabase(Context context) {
        super(DATABASE_NAME, DATABASE_VERSION, true);
        _context = context;
        Log.d(TAG, "creating instance");
    }

    // Method is called during creation of the database
    @Override
    public void onCreate(DatabaseIface database) {
        Log.i(TAG, "Creating database version: " + DATABASE_VERSION);
        database.execute(ICONSETS_CREATE, null);
        database.execute(ICONS_CREATE, null);

        //default icons not loaded
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        Editor editor = prefs.edit();
        editor.putBoolean("iconset.default.loaded", false);
        editor.apply();

        // seed the database from the assets default on create
        try {
            seedIconDatabase(_context, database);
        } catch (IOException e) {
            Log.w(TAG, "Failed to seed database");
        }
    }

    // Method is called during an upgrade of the database
    @Override
    public void onUpgrade(DatabaseIface database, int oldVersion,
            int newVersion) {
        Log.i(TAG,
                "Upgrading from version " + oldVersion + " to " + newVersion);
        database.execute("DROP TABLE IF EXISTS " + TABLE_ICONS, null);
        database.execute("DROP TABLE IF EXISTS " + TABLE_ICONSETS, null);
        onCreate(database);
    }

    public void dropTables() {
        Log.d(TAG, "dropTables");
        DatabaseIface db = this.getWritableDatabase();
        db.execute("DROP TABLE IF EXISTS " + TABLE_ICONS, null);
        db.execute("DROP TABLE IF EXISTS " + TABLE_ICONSETS, null);
        onCreate(db);
    }

    /**
     * Get the specified iconset. Optionally get icons. Optionally get
     * icons' bitmaps
     * 
     * @param bIcons
     * @param bBitmaps
     * @return
     */
    public UserIconSet getIconSet(String uid, boolean bIcons,
            boolean bBitmaps) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Unable to get iconset without UID");
            return null;
        }

        CursorIface cursor = null;
        CursorIface iconCursor = null;

        UserIconSet ret;
        try {
            String[] selectionArgs = new String[1];
            selectionArgs[0] = uid;

            cursor = AndroidDatabaseAdapter.query(this.getReadableDatabase(),
                    TABLE_ICONSETS,
                    UserIconSet.getColumns(),
                    UserIconSet.COLUMN_ICONSETS_UID + "=?", selectionArgs,
                    null, null, null);

            if (!cursor.moveToNext())
                return null;

            //take first match
            ret = UserIconSet.fromCursor(cursor);
            if (bIcons) {
                //get all icons for this iconset
                iconCursor = AndroidDatabaseAdapter.query(
                        this.getReadableDatabase(),
                        TABLE_ICONS,
                        UserIcon.getColumns(),
                        UserIcon.COLUMN_ICONS_SETUID + "=?", selectionArgs,
                        null, null, null);

                if (iconCursor.moveToNext()) {
                    List<UserIcon> icons = new LinkedList<>();
                    do {
                        icons.add(UserIcon.fromCursor(iconCursor, bBitmaps));
                    } while (iconCursor.moveToNext());

                    if (icons.size() > 0) {
                        ret.setIcons(icons);
                    }
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
            if (iconCursor != null)
                iconCursor.close();
        }

        if (ret != null) {
            //Log.d(TAG, "Loaded iconset " + ret.toString());
            return ret;
        }

        Log.d(TAG, "Iconset does not exist: " + uid);
        return null;
    }

    /**
     * Get the specified iconset, lookup by name. Optionally get icons. Optionally get
     * icons' bitmaps
     * 
     * @param bIcons
     * @param bBitmaps
     * @return
     */
    public UserIconSet getIconSetByName(String name, boolean bIcons,
            boolean bBitmaps) {
        CursorIface cursor = null;
        CursorIface iconCursor = null;

        UserIconSet ret;
        try {
            cursor = AndroidDatabaseAdapter.query(this.getReadableDatabase(),
                    TABLE_ICONSETS,
                    UserIconSet.getColumns(),
                    UserIconSet.COLUMN_ICONSETS_NAME + "=?", new String[] {
                            name
                    },
                    null, null, null);

            if (!cursor.moveToNext())
                return null;

            //take first match
            ret = UserIconSet.fromCursor(cursor);
            if (bIcons) {
                //get all icons for this iconset
                iconCursor = AndroidDatabaseAdapter.query(
                        this.getReadableDatabase(),
                        TABLE_ICONS,
                        UserIcon.getColumns(),
                        UserIcon.COLUMN_ICONS_SETUID + "=?", new String[] {
                                ret.getUid()
                        },
                        null, null, null);

                if (iconCursor.moveToNext()) {
                    List<UserIcon> icons = new LinkedList<>();
                    do {
                        icons.add(UserIcon.fromCursor(iconCursor, bBitmaps));
                    } while (iconCursor.moveToNext());

                    if (icons.size() > 0) {
                        ret.setIcons(icons);
                    }
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
            if (iconCursor != null)
                iconCursor.close();
        }

        if (ret != null) {
            //Log.d(TAG, "Loaded iconset " + ret.toString());
            return ret;
        }

        Log.d(TAG, "Iconset does not exist with name: " + name);
        return null;
    }

    /**
     * Get all iconsets. Optionally get icons. Optionally get
     * icons' bitmaps
     * 
     * @param bIcons
     * @param bBitmaps
     * @return
     */
    public List<UserIconSet> getIconSets(boolean bIcons, boolean bBitmaps) {
        CursorIface cursor = null;

        List<UserIconSet> ret = new ArrayList<>();
        try {
            //get all iconsets
            cursor = AndroidDatabaseAdapter.query(this.getReadableDatabase(),
                    TABLE_ICONSETS,
                    UserIconSet.getColumns(),
                    null, null, null, null, null);

            while (cursor.moveToNext()) {
                UserIconSet iconset = UserIconSet.fromCursor(cursor);
                if (iconset == null || !iconset.isValid()) {
                    Log.w(TAG, "Skipping invalid iconset");
                    continue;
                }

                ret.add(iconset);
                if (bIcons) {
                    CursorIface iconCursor = null;
                    try {
                        //get all icons for this iconset
                        iconCursor = AndroidDatabaseAdapter.query(
                                this.getReadableDatabase(),
                                TABLE_ICONS,
                                UserIcon.getColumns(),
                                UserIcon.COLUMN_ICONS_SETUID + "=?",
                                new String[] {
                                        iconset.getUid()
                                },
                                null, null, null);

                        if (iconCursor.moveToNext()) {
                            List<UserIcon> icons = new LinkedList<>();
                            do {
                                icons.add(UserIcon.fromCursor(iconCursor,
                                        bBitmaps));
                            } while (iconCursor.moveToNext());

                            if (icons.size() > 0) {
                                iconset.setIcons(icons);
                            }
                        }
                    } finally {
                        if (iconCursor != null)
                            iconCursor.close();
                    }
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        Log.d(TAG, "Loaded iconsets " + ret.size());
        return ret;
    }

    public Bitmap getIconBitmap(int id) {
        byte[] bitMap = getIconBytes(id);
        if (FileSystemUtils.isEmpty(bitMap))
            return null;

        return UserIcon.decodeBitMap(bitMap);
    }

    public byte[] getIconBytes(int id) {
        CursorIface cursor = null;

        byte[] ret;
        try {
            String[] selectionArgs = new String[1];
            selectionArgs[0] = String.valueOf(id);

            cursor = AndroidDatabaseAdapter.query(this.getReadableDatabase(),
                    TABLE_ICONS,
                    new String[] {
                            UserIcon.COLUMN_ICONS_BITMAP
                    },
                    UserIcon.COLUMN_ICONS_ID + "=?",
                    selectionArgs,
                    null, null, null);

            if (!cursor.moveToNext())
                return null;

            ret = cursor.getBlob(cursor
                    .getColumnIndex(UserIcon.COLUMN_ICONS_BITMAP));

        } finally {
            if (cursor != null)
                cursor.close();
        }

        return ret;
    }

    public UserIcon getIcon(String iconsetUid, String filename,
            boolean bBitmap) {
        CursorIface cursor = null;

        UserIcon ret;
        try {
            String[] selectionArgs = new String[2];
            selectionArgs[0] = String.valueOf(iconsetUid);
            selectionArgs[1] = String.valueOf(filename);

            cursor = AndroidDatabaseAdapter.query(this.getReadableDatabase(),
                    TABLE_ICONS,
                    UserIcon.getColumns(),
                    UserIcon.COLUMN_ICONS_SETUID + "=? AND "
                            + UserIcon.COLUMN_ICONS_FILENAME + "=?",
                    selectionArgs,
                    null, null, null);

            if (!cursor.moveToNext())
                return null;

            ret = UserIcon.fromCursor(cursor, bBitmap);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return ret;
    }

    public UserIcon getIcon(int iconId, boolean bBitmap) {
        CursorIface cursor = null;

        UserIcon ret;
        try {
            String[] selectionArgs = new String[1];
            selectionArgs[0] = String.valueOf(iconId);

            cursor = AndroidDatabaseAdapter.query(this.getReadableDatabase(),
                    TABLE_ICONS,
                    UserIcon.getColumns(),
                    UserIcon.COLUMN_ICONS_ID + "=?",
                    selectionArgs,
                    null, null, null);

            if (!cursor.moveToNext())
                return null;

            ret = UserIcon.fromCursor(cursor, bBitmap);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return ret;
    }

    /**
     * Add iconset to DB
     * Does not process icons. They should be loaded individually via addIcon
     * @param iconset
     * 
     * @return row ID or -1 upon failure
     */
    public long addIconSet(UserIconSet iconset) {
        if (iconset == null || !iconset.isValid())
            return -1L;

        iconset.setName(FileSystemUtils.sanitizeFilename(iconset.getName()));
        iconset.setUid(FileSystemUtils.sanitizeFilename(iconset.getUid()));

        UserIconSet existing = getIconSet(iconset.getUid(), false, false);
        if (existing != null) {
            Log.w(TAG, "Iconset already exists: " + iconset);
            return -1;
        }

        Log.d(TAG, "Adding iconset: " + iconset);
        ContentValues insertValues = new ContentValues();
        insertValues.put(UserIconSet.COLUMN_ICONSETS_NAME, iconset.getName());
        insertValues.put(UserIconSet.COLUMN_ICONSETS_UID, iconset.getUid());
        insertValues.put(UserIconSet.COLUMN_ICONSETS_DEFAULT_FRIENDLY,
                iconset.getDefaultFriendly());
        insertValues.put(UserIconSet.COLUMN_ICONSETS_DEFAULT_HOSTILE,
                iconset.getDefaultHostile());
        insertValues.put(UserIconSet.COLUMN_ICONSETS_DEFAULT_NEUTRAL,
                iconset.getDefaultNeutral());
        insertValues.put(UserIconSet.COLUMN_ICONSETS_DEFAULT_UNKNOWN,
                iconset.getDefaultUnknown());
        insertValues.put(UserIconSet.COLUMN_ICONSETS_SELECTED_GROUP,
                iconset.getSelectedGroup());
        return AndroidDatabaseAdapter.insert(this.getWritableDatabase(),
                TABLE_ICONSETS, null,
                insertValues);
    }

    public boolean removeIconSet(UserIconSet iconset) {
        if (iconset == null || !iconset.isValid()) {
            Log.w(TAG, "Failed to remove invalid iconset");
            return false;
        }

        return removeIconSet(iconset.getUid());
    }

    public boolean removeIconSet(String uid) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Failed to remove invalid iconset");
            return false;
        }

        if (AndroidDatabaseAdapter.delete(this.getWritableDatabase(),
                TABLE_ICONSETS,
                UserIconSet.COLUMN_ICONSETS_UID + "=?", new String[] {
                        uid
                }) < 1) {
            Log.w(TAG, "Iconset does not exists, cannot remove: " + uid);
            return false;
        }

        //now remove all corresponding icons, should be at least one
        if (AndroidDatabaseAdapter.delete(this.getWritableDatabase(),
                TABLE_ICONS,
                UserIcon.COLUMN_ICONS_SETUID + "=?", new String[] {
                        uid
                }) < 1) {
            Log.w(TAG, "No Icons exists, cannot remove: " + uid);
        }

        Log.d(TAG, "Removed iconset: " + uid);
        return true;
    }

    /**
     * Add icon to DB and associate with the specified iconset
     * 
     * @param icon    must have valid iconsetId
     * @param bitMap
     */
    public boolean addIcon(UserIcon icon, byte[] bitMap) {
        //TODO for performance. better to pass in zip file and load all icons in a single transaction?
        //rather than invoking this method for each icon in iconset
        //Also could use pre-compiled statements throughout this class
        //TODO use indexed ID more than UID in query methods above?

        if (icon == null || !icon.isValid() || FileSystemUtils.isEmpty(bitMap))
            return false;

        icon.setIconsetUid(FileSystemUtils.sanitizeFilename(icon
                .getIconsetUid()));
        icon.setFileName(FileSystemUtils.sanitizeFilename(icon.getFileName()));
        icon.setGroup(FileSystemUtils.sanitizeWithSpaces(icon.getGroup()));
        icon.set2525cType(
                FileSystemUtils.sanitizeFilename(icon.get2525cType()));

        //Log.d(TAG, "Adding icon: " + icon.toString());
        ContentValues insertValues = new ContentValues();
        insertValues.put(UserIcon.COLUMN_ICONS_SETUID, icon.getIconsetUid());
        insertValues.put(UserIcon.COLUMN_ICONS_GROUP, icon.getGroup());
        insertValues.put(UserIcon.COLUMN_ICONS_FILENAME, icon.getFileName());
        insertValues.put(UserIcon.COLUMN_ICONS_COTTYPE, icon.get2525cType());
        insertValues.put(UserIcon.COLUMN_ICONS_BITMAP, bitMap);
        insertValues.put(UserIcon.COLUMN_ICONS_USECNT, 0);
        return AndroidDatabaseAdapter.insert(this.getWritableDatabase(),
                TABLE_ICONS, null,
                insertValues) != -1;
    }

    /**
     * Increment the database useCount by 1 for the specified icon
     * @param icon
     */
    public void incrementIconUseCount(UserIcon icon) {
        if (icon == null || !icon.isValid()) {
            Log.w(TAG, "Failed to increment use count for invalid icon");
            return;
        }

        String sql = "UPDATE " + TABLE_ICONS + " SET "
                + UserIcon.COLUMN_ICONS_USECNT + " = " +
                UserIcon.COLUMN_ICONS_USECNT + " + 1 WHERE "
                + UserIcon.COLUMN_ICONS_ID + " = " + icon.getId();

        //Log.d(TAG, "incrementIconUseCount: " + sql);

        try {
            this.getWritableDatabase().execute(sql, null);
        } catch (SQLException e) {
            Log.w(TAG,
                    "Failed to increment use count for icon: "
                            + icon,
                    e);
        }
    }

    /**
     * Update the selected group for the iconset
     * @param iconset
     */
    public boolean setSelectedGroup(UserIconSet iconset) {
        if (iconset == null || !iconset.isValid() ||
                FileSystemUtils.isEmpty(iconset.getSelectedGroup())) {
            Log.w(TAG, "Failed to set iconset group");
            return false;
        }

        try {
            ContentValues update = new ContentValues();
            update.put(UserIconSet.COLUMN_ICONSETS_SELECTED_GROUP,
                    iconset.getSelectedGroup());
            String[] whereArgs = new String[] {
                    String.valueOf(iconset.getId())
            };
            return AndroidDatabaseAdapter.update(this.getWritableDatabase(),
                    TABLE_ICONSETS,
                    update,
                    UserIconSet.COLUMN_ICONSETS_ID + " =?",
                    whereArgs) != -1;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to set selected group for iconset: "
                    + iconset, ex);
            return false;
        }
    }

    /**
     * Select most used icon, optionally within a group
     * Note, currently MapGroup hierarchy does not track iconsets
     * So, its possible two different iconsets have provided icons in the same group
     * Note, in DB iconset UID is tracked
     * 
     * @param group
     */
    public UserIcon getMostUsedIcon(String group) {

        String sql = "SELECT * FROM " + TABLE_ICONS + " ";
        if (!FileSystemUtils.isEmpty(group)) {
            sql += "WHERE " + UserIcon.COLUMN_ICONS_GROUP + " = '" + group
                    + "' ";
        }
        sql += "ORDER BY " + UserIcon.COLUMN_ICONS_USECNT + " DESC LIMIT 1";

        //Log.d(TAG, "getMostUsedIcon: " + sql);

        CursorIface cursor = null;
        try {
            cursor = this.getReadableDatabase().query(sql, null);
            if (!cursor.moveToNext())
                return null;

            return UserIcon.fromCursor(cursor, false);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    void beginTransaction() {
        this.getWritableDatabase().beginTransaction();
    }

    void setTransactionSuccessful() {
        this.getWritableDatabase().setTransactionSuccessful();
    }

    void endTransaction() {
        this.getWritableDatabase().endTransaction();
    }

    /**
     * Load assets packaged in APK as asset file and adds to the user icon database
     */
    private static void seedIconDatabase(Context context,
            DatabaseIface database) throws IOException {
        // NOTE: this method deploys the seed DB from assets using naked IO.
        // IOProviderFactory.openDatabase(..) will not be able to interpret
        // the unpacked asset correctly as it is always a plain unencrypted
        // SQLite database.

        Log.d(TAG, "Deploying asset iconset database");

        String assetPath = "dbs" + File.separatorChar + "iconsets.sqlite";
        File outputTempPath = File.createTempFile("iconset", "",
                context.getCacheDir());
        if (!SecureDelete.delete(outputTempPath))
            throw new IOException();
        if (!outputTempPath.mkdirs())
            throw new IOException();
        DatabaseIface assetsDb = null;
        try {
            File seedIconDbFile = new File(outputTempPath, "iconsets.sqlite");
            InputStream assetInputStream = FileSystemUtils
                    .getInputStreamFromAsset(context, assetPath);
            if (assetInputStream == null) {
                Log.w(TAG, "Unable to obtain asset " + assetPath);
                return;
            }
            // copy the APK asset using naked IO to the context cache directory
            FileSystemUtils.copyStream(
                    assetInputStream,
                    true,
                    new FileOutputStream(seedIconDbFile.getAbsolutePath()),
                    true);

            assetsDb = DatabaseImpl.open(seedIconDbFile.getAbsolutePath(),
                    true);

            final boolean localTransaction = !database.inTransaction();

            if (localTransaction)
                database.beginTransaction();
            try {
                CursorIface cursor;

                cursor = null;
                try {
                    cursor = assetsDb.query("SELECT "
                            + UserIcon.COLUMN_ICONS_ID + ","
                            + UserIcon.COLUMN_ICONS_SETUID + ","
                            + UserIcon.COLUMN_ICONS_FILENAME + ","
                            + UserIcon.COLUMN_ICONS_GROUP + ","
                            + UserIcon.COLUMN_ICONS_COTTYPE + ","
                            + UserIcon.COLUMN_ICONS_USECNT + ","
                            + UserIcon.COLUMN_ICONS_BITMAP
                            + " FROM " + TABLE_ICONS + ";", null);
                    while (cursor.moveToNext()) {
                        String statement = "INSERT INTO " + TABLE_ICONS + " ("
                                + UserIcon.COLUMN_ICONS_ID + ","
                                + UserIcon.COLUMN_ICONS_SETUID + ","
                                + UserIcon.COLUMN_ICONS_FILENAME + ","
                                + UserIcon.COLUMN_ICONS_GROUP + ","
                                + UserIcon.COLUMN_ICONS_COTTYPE + ","
                                + UserIcon.COLUMN_ICONS_USECNT + ","
                                + UserIcon.COLUMN_ICONS_BITMAP
                                + ") VALUES (?,?,?,?,?,?,?);";
                        StatementIface insert = null;
                        try {
                            insert = database.compileStatement(statement);
                            insert.bind(1, cursor.getInt(0));
                            insert.bind(2, cursor.getString(1));
                            insert.bind(3, cursor.getString(2));
                            insert.bind(4, cursor.getString(3));
                            insert.bind(5, cursor.getString(4));
                            insert.bind(6, cursor.getInt(5));
                            insert.bind(7, cursor.getBlob(6));
                            insert.execute();
                        } finally {
                            if (insert != null)
                                insert.close();
                        }
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                cursor = null;
                try {
                    cursor = assetsDb.query(
                            "SELECT "
                                    + UserIconSet.COLUMN_ICONSETS_ID + ","
                                    + UserIconSet.COLUMN_ICONSETS_NAME + ","
                                    + UserIconSet.COLUMN_ICONSETS_UID + ","
                                    + UserIconSet.COLUMN_ICONSETS_DEFAULT_FRIENDLY
                                    + ","
                                    + UserIconSet.COLUMN_ICONSETS_DEFAULT_HOSTILE
                                    + ","
                                    + UserIconSet.COLUMN_ICONSETS_DEFAULT_NEUTRAL
                                    + ","
                                    + UserIconSet.COLUMN_ICONSETS_DEFAULT_UNKNOWN
                                    + ","
                                    + UserIconSet.COLUMN_ICONSETS_SELECTED_GROUP
                                    + " FROM " + TABLE_ICONSETS + ";",
                            null);

                    while (cursor.moveToNext()) {
                        String statement = "INSERT INTO " + TABLE_ICONSETS
                                + " ("
                                + UserIconSet.COLUMN_ICONSETS_ID + ","
                                + UserIconSet.COLUMN_ICONSETS_NAME + ","
                                + UserIconSet.COLUMN_ICONSETS_UID + ","
                                + UserIconSet.COLUMN_ICONSETS_DEFAULT_FRIENDLY
                                + ","
                                + UserIconSet.COLUMN_ICONSETS_DEFAULT_HOSTILE
                                + ","
                                + UserIconSet.COLUMN_ICONSETS_DEFAULT_NEUTRAL
                                + ","
                                + UserIconSet.COLUMN_ICONSETS_DEFAULT_UNKNOWN
                                + ","
                                + UserIconSet.COLUMN_ICONSETS_SELECTED_GROUP
                                + ") VALUES (?,?,?,?,?,?,?,?);";
                        StatementIface insert = null;
                        try {
                            insert = database.compileStatement(statement);
                            insert.bind(1, cursor.getInt(0));
                            insert.bind(2, cursor.getString(1));
                            insert.bind(3, cursor.getString(2));
                            insert.bind(4, cursor.getString(3));
                            insert.bind(5, cursor.getString(4));
                            insert.bind(6, cursor.getString(5));
                            insert.bind(7, cursor.getString(6));
                            insert.bind(8, cursor.getString(7));
                            insert.execute();
                        } finally {
                            if (insert != null)
                                insert.close();
                        }
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }
                if (localTransaction)
                    database.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(TAG,
                        "Error during initial population. " + e.getMessage());
            } finally {
                if (localTransaction)
                    database.endTransaction();

                if (assetsDb != null)
                    assetsDb.close();
            }
        } finally {
            SecureDelete.deleteDirectory(outputTempPath, false);
        }
    }
}
