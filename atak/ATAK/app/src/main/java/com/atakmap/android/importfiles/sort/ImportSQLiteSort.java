
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.atakmap.android.icons.UserIconDatabase;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sorts ATAK "SQLite" Databases
 * 
 * 
 */
public class ImportSQLiteSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportSQLiteSort";

    /**
     * Enumeration of supported ATAK databases including a sample (not an exhaustive listing) of
     * expected tables for each database
     * 
     * 
     */
    private enum TYPE {
        //        MOBAC(null, new String[] {
        //                "tiles"
        //        }, "layers"), // ATAK/layers (any filename)
        COT("cot.sqlite", new String[] {
                "spatial_ref_sys", "CotEvent"
        }, "Databases"), // ATAK/Databases
        LAYERS2("layers2.sqlite", new String[] {
                "layers", "catalog", "metadata"
        }, "Databases"), // ATAK/Databases
        SSE("sse.sqlite", new String[] {
                "spatial_ref_sys", "Entity", "ReportRelationMap", "Photo"
        }, "Databases"), // ATAK/Databases
        SITEEXPLOITATION("siteexploitation.sqlite", new String[] {
                "spatial_ref_sys", "Entity", "ReportRelationMap", "Photo"
        }, "Databases"), // ATAK/Databases
        SPATIAL("spatial.sqlite", new String[] {
                "spatial_ref_sys", "File", "Geometry", "Style"
        }, "Databases"), // ATAK/Databases
        USERICONSET("iconsets.sqlite", new String[] {
                UserIconDatabase.TABLE_ICONS, UserIconDatabase.TABLE_ICONSETS,
        }, "Databases") // ATAK/Databases
        ;

        final String _name;
        final String _folder;
        final String[] _tableNames;

        TYPE(String name, String[] tableNames, String folder) {
            _name = name;
            _folder = folder;
            _tableNames = tableNames;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", super.toString(), _name, _folder);
        }
    }

    public ImportSQLiteSort(Context context, boolean validateExt,
            boolean copyFile) {
        super(".sqlite", null, validateExt, copyFile,
                "SQLite Database", context.getDrawable(R.drawable.ic_database));

    }

    /**
     * Support any file extension that matches per <code>ImageryFileType</code>
     * 
     * @param file
     * @return
     */
    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .sqlite, now lets check table names
        TYPE t;
        try {
            t = getType(file);
        } catch (Exception e) {
            return false;
        }
        return t != null;
    }

    public static TYPE getType(File dbFile) {
        SQLiteDatabase db = null;

        try {
            if (!FileSystemUtils.isFile(dbFile)) {
                Log.w(TAG,
                        "Failed to find database: " + dbFile.getAbsolutePath());
                return null;
            }

            // open database file, return upon error
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY
                            | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    new DatabaseErrorHandler() {
                        @Override
                        public void onCorruption(SQLiteDatabase dbObj) {
                            dbObj.close();
                        }
                    });

            if (db == null || !db.isOpen()) {
                Log.d(TAG,
                        "Failed to open database: " + dbFile.getAbsolutePath());
                return null;
            }

            for (TYPE t : TYPE.values()) {
                // see if we should check db file name
                if (!FileSystemUtils.isEmpty(t._name)
                        && !dbFile.getName().equals(t._name))
                    continue;

                // see if we should check db table names
                if (t._tableNames != null && t._tableNames.length > 0) {
                    if (hasTableNames(db, t._tableNames))
                        return t;
                }
            }

            Log.d(TAG, "Failed to match ATAK SQLite content");
            return null;
        } catch (Exception e) {
            Log.d(TAG, "Failed to match sqlite", e);
            return null;
        } finally {
            if (db != null)
                db.close();
        }
    }

    private static boolean hasTableNames(SQLiteDatabase db,
            String[] tableNames) {

        // get all tables names from the DB
        List<String> foundNames = new ArrayList<>();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table'", null);
            if (c != null && c.moveToFirst()) {
                while (!c.isAfterLast()) {
                    String tblName;
                    while (!c.isAfterLast()) {
                        tblName = c.getString(c.getColumnIndex("name"));
                        if (!FileSystemUtils.isEmpty(tblName))
                            foundNames.add(tblName);
                        else
                            Log.w(TAG, "Ignoring table without a name");
                        c.moveToNext();

                        // Log.d(TAG, "Found table name: " + tblName);
                    }
                }
            }

            // see if DB contains all the required table names
            for (String name : tableNames) {
                if (!foundNames.contains(name)) {
                    // Log.d(TAG, "Failed to match sqlite table name: " + name);
                    return false;
                }
            }
        } finally {
            if (c != null)
                c.close();
        }

        // all table names were matched
        return true;
    }

    /**
     * Move to new location on same SD card Defer to TYPE for the relative path
     * 
     * @param file
     * @return produces a destination path based on the file type
     */
    @Override
    public File getDestinationPath(File file) {

        TYPE t = getType(file);
        if (t == null) {
            Log.e(TAG,
                    "Failed to match SQLite file: " + file.getAbsolutePath());
            return null;
        }

        File folder = FileSystemUtils.getItem(t._folder);

        // ATAK directory watchers expect files to have certain extensions so force it here
        String fileName = file.getName();
        if (!FileSystemUtils.isEmpty(getExt())
                && !fileName.endsWith(getExt())) {
            Log.d(TAG, "Added extension to destination path: " + fileName);
            fileName += getExt();
        }

        return new File(folder, fileName);
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>("SQLite Database", "application/x-sqlite3");
    }
}
