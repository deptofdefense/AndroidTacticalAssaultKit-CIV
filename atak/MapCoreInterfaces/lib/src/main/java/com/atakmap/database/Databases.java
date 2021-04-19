
package com.atakmap.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.impl.DatabaseImpl;
import com.atakmap.util.Collections2;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Pair;

public final class Databases {
    private Databases() {
    }

    public static Set<String> getTableNames(SQLiteDatabase database) {
        Set<String> retval = new HashSet<String>();

        Cursor result;

        result = null;
        try {
            result = database.query("sqlite_master", new String[] {
                    "name"
            }, "type = \'table\'", null, null, null, null);
            while (result.moveToNext())
                retval.add(result.getString(0));
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query("sqlite_master", new String[] {
                    "name"
            }, "type = \'view\'", null, null, null, null);
            while (result.moveToNext())
                retval.add(result.getString(0));
        } finally {
            if (result != null)
                result.close();
        }

        return retval;
    }

    /**
     * Returns a set of column names for a given table name in a database.
     * @param database the opened database.
     * @param tableName the tablename to look at for the columns.
     * @return a set of Strings or null if the table does not exist.
     */
    public static Set<String> getColumnNames(SQLiteDatabase database,
            String tableName) {
        // whitelist the table name
        if (!tableExists(database, tableName))
            return null;

        Cursor result = null;
        try {

            /**
             * ATAK-7984 Security - sanitize SQL table name.
             * The table name is implicitly whitelisted in the 
             * referenced code as it is derived directly from sqlite_master and 
             * not provided by the client.  We could explore an empty set query, 
             * but I'm leaning towards asking this to be waived given the current 
             * implementation.
             */
            result = database.rawQuery("PRAGMA table_info(" + tableName + ")",
                    null);
            if (!result.moveToNext())
                return null;

            final int idx = result.getColumnIndex("name");
            if (idx < 0)
                return null;

            Set<String> retval = new HashSet<String>();
            do {
                retval.add(result.getString(idx));
            } while (result.moveToNext());

            return retval;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static Map<String, Set<String>> getColumnNames(
            SQLiteDatabase database) {
        Map<String, Set<String>> databaseStructure = new LinkedHashMap<String, Set<String>>();

        Cursor tablesResult = null;
        Cursor tableInfoResult;
        try {
            tablesResult = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type IN ('table', 'view')",
                    null);
            String tablename;

            while (tablesResult.moveToNext()) {
                tablename = tablesResult.getString(0);
                databaseStructure.put(tablename, new LinkedHashSet<String>());
                tableInfoResult = null;
                try {
                    try {
                        // Note:   This query uses specifically the return from
                        //       SELECT name FROM sqlite_master WHERE type='table'
                        // and does not take any other input.
                        // Executing a rawQuery with a PRAGMA table_info(?)
                        // passing in a String[] { table } is not valid in Android.

                        // use of the table name here is implicitly whitelisted
                        // as it comes directly from 'sqlite_master' and not
                        // from an untrusted source
                        tableInfoResult = database.rawQuery(
                                "PRAGMA table_info(" + tablename + ")",
                                null);
                        while (tableInfoResult.moveToNext())
                            databaseStructure.get(tablename).add(
                                    tableInfoResult.getString(tableInfoResult
                                            .getColumnIndex("name")));
                    } catch (SQLiteException e) {
                        Log.e("Databases",
                                "Failed to get table info: " + tablename, e);
                    }
                } finally {
                    if (tableInfoResult != null)
                        tableInfoResult.close();
                }
            }

            return databaseStructure;
        } finally {
            if (tablesResult != null)
                tablesResult.close();
        }
    }

    // http://www.sqlite.org/fileformat2.html section 1.2

    public static boolean isSQLiteDatabase(String path) {
        try(InputStream fis = IOProviderFactory
                .getInputStream(new File(path))) {
            byte[] buf = new byte[16];
            if (fis.read(buf) < 16)
                return false;
            final String headerString = new String(buf,
                    FileSystemUtils.UTF8_CHARSET);
            return headerString.equals("SQLite format 3\0");
        } catch (IOException ignored) {
            // quietly ignore -- this obviously isn't a sqlite file
            return false;
        }
    }

    /**
     * Returns the next auto-increment ID for the specified table. If the specified table does not
     * contain an AUTOINCREMENT column, <code>0L</code> will be returned.
     *
     * @param database A SQLite database
     * @param table The name of the table
     * @return The next auto-increment ID or <code>0L</code> if there is no AUTOINCREMENT column for
     *         the specified table.
     */
    public static long getNextAutoincrementId(SQLiteDatabase database,
            String table) {
        Cursor result = null;
        try {
            result = database.rawQuery(
                    "SELECT seq FROM sqlite_sequence WHERE name = ?",
                    new String[] {
                            table
                    });
            if (!result.moveToNext())
                return 1L;
            return result.getLong(0) + 1L;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static Set<String> getTableNames(DatabaseIface database) {
        Set<String> retval = new HashSet<String>();

        CursorIface result;

        result = null;
        try {
            result = database.query(
                    "SELECT tbl_name FROM sqlite_master WHERE type=\'table\'",
                    null);

            while (result.moveToNext())
                retval.add(result.getString(0));
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query(
                    "SELECT tbl_name FROM sqlite_master WHERE type=\'view\'",
                    null);

            while (result.moveToNext())
                retval.add(result.getString(0));
        } finally {
            if (result != null)
                result.close();
        }

        return retval;
    }

    public static Set<String> getColumnNames(DatabaseIface database,
            String tableName) {
        // whitelist the table name
        if (!tableExists(database, tableName))
            return null;

        CursorIface result = null;
        try {
            result = database.query("PRAGMA table_info(" + tableName + ")",
                    null);
            if (!result.moveToNext())
                return null;

            final int idx = result.getColumnIndex("name");
            if (idx < 0)
                return null;

            Set<String> retval = new HashSet<String>();
            do {
                retval.add(result.getString(idx));
            } while (result.moveToNext());

            return retval;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static Map<String, Set<String>> getColumnNames(
            DatabaseIface database) {
        Map<String, Set<String>> databaseStructure = new LinkedHashMap<String, Set<String>>();

        CursorIface tablesResult = null;
        CursorIface tableInfoResult;
        try {
            tablesResult = database.query(
                    "SELECT name FROM sqlite_master WHERE type IN ('table', 'view')",
                    null);
            String tablename;

            while (tablesResult.moveToNext()) {
                tablename = tablesResult.getString(0);
                databaseStructure.put(tablename, new LinkedHashSet<String>());
                tableInfoResult = null;
                try {
                    // table names are implicitly whitelisted here as they are
                    // derived directly from sqlite_master
                    tableInfoResult = database.query(
                            "PRAGMA table_info(" + tablename + ")",
                            null);
                    while (tableInfoResult.moveToNext())
                        databaseStructure.get(tablename).add(
                                tableInfoResult.getString(tableInfoResult
                                        .getColumnIndex("name")));
                } finally {
                    if (tableInfoResult != null)
                        tableInfoResult.close();
                }
            }

            return databaseStructure;
        } finally {
            if (tablesResult != null)
                tablesResult.close();
        }
    }

    /**
     * Returns the next auto-increment ID for the specified table. If the specified table does not
     * contain an AUTOINCREMENT column, <code>0L</code> will be returned.
     *
     * @param database A SQLite database
     * @param table The name of the table
     * @return The next auto-increment ID or <code>0L</code> if there is no AUTOINCREMENT column for
     *         the specified table.
     */
    public static long getNextAutoincrementId(DatabaseIface database,
            String table) {
        CursorIface result = null;
        try {
            result = database.query(
                    "SELECT seq FROM sqlite_sequence WHERE name = ?",
                    new String[] {
                            table
                    });
            if (!result.moveToNext())
                return 1L;
            return result.getLong(0) + 1L;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static int lastChangeCount(DatabaseIface database) {
        CursorIface result = null;
        try {
            result = database.query("SELECT changes()", null);
            if (!result.moveToNext())
                return 0;
            return result.getInt(0);
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static long lastInsertRowId(DatabaseIface database) {
        CursorIface result = null;
        try {
            result = database.query("SELECT last_insert_rowid()", null);
            if (!result.moveToNext())
                return 0;
            return result.getLong(0);
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static String getDatabaseFile(DatabaseIface database) {
        CursorIface result = null;
        try {
            result = database.query("PRAGMA database_list", null);
            String col2;
            while (result.moveToNext()) {
                col2 = result.getString(1);
                if (col2 != null && col2.equalsIgnoreCase("main"))
                    return result.getString(2);
            }
            return null;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static Map<String, Pair<String, String>> foreignKeyList(
            DatabaseIface database, String table) {
        if (!tableExists(database, table))
            return Collections.<String, Pair<String, String>> emptyMap();
        CursorIface result = null;
        try {
            result = database.query("PRAGMA foreign_key_list(" + table + ")",
                    null);
            int tableIdx = result.getColumnIndex("table");
            int fromIdx = result.getColumnIndex("from");
            int toIdx = result.getColumnIndex("to");
            if (tableIdx < 0 || fromIdx < 0 || toIdx < 0)
                return Collections.<String, Pair<String, String>> emptyMap();

            Map<String, Pair<String, String>> retval = new HashMap<String, Pair<String, String>>();
            while (result.moveToNext()) {
                retval.put(result.getString(tableIdx),
                        Pair.create(
                                result.getString(fromIdx),
                                result.getString(toIdx)));
            }
            return retval;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public static boolean matchesSchema(DatabaseIface db,
            Map<String, Collection<String>> schema, boolean exact) {
        for (Map.Entry<String, Collection<String>> tableDef : schema
                .entrySet()) {
            Set<String> dbCols = getColumnNames(db, tableDef.getKey());
            if (dbCols == null)
                return false;
            for (String col : tableDef.getValue()) {
                if (!Collections2.containsIgnoreCase(dbCols, col))
                    return false;
            }

            if (exact) {
                for (String col : dbCols)
                    if (!Collections2.containsIgnoreCase(tableDef.getValue(),
                            col))
                        return false;
            }
        }

        return true;
    }

    /**
     * Returns <code>true</code> if the specified table exists for the given
     * database, <code>false</code>. This method should be used to
     * <I>whitelist</I> table names that will be concatenated into raw queries
     * (e.g. PRAGMA statements).
     *
     * @param database  The database
     * @param tableName The table name
     *
     * @return  <code>true</code> if the table exists for the database,
     *          <code>false</code> otherwise.
     */
    private static boolean tableExists(DatabaseIface database,
            String tableName) {
        QueryIface result = null;
        try {
            result = database.compileQuery(
                    "SELECT 1 FROM sqlite_master WHERE type IN ('table', 'view') AND name = ? LIMIT 1");
            result.bind(1, tableName);
            return result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**
     * Returns <code>true</code> if the specified table exists for the given
     * database, <code>false</code>. This method should be used to
     * <I>whitelist</I> table names that will be concatenated into raw queries
     * (e.g. PRAGMA statements).
     *
     * @param database  The database
     * @param tableName The table name
     *
     * @return  <code>true</code> if the table exists for the database,
     *          <code>false</code> otherwise.
     */
    private static boolean tableExists(SQLiteDatabase database,
            String tableName) {
        Cursor result;

        result = null;
        try {
            result = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type=\'table\' AND name = ? LIMIT 1",
                    new String[] {
                            tableName
                    });
            if (result.moveToNext())
                return true;
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type=\'view\' AND name = ? LIMIT 1",
                    new String[] {
                            tableName
                    });
            if (result.moveToNext())
                return true;
        } finally {
            if (result != null)
                result.close();
        }

        return false;
    }

    /**
     * Opens the database from the specified existing path.
     *
     * @param path      The path to the database, or <code>null</code> to open
     *                  an in-memory database
     * @param readOnly  <code>true</code> if the database should be opened as
     *                  read-only, <code>false</code> otherwise
     * @return the database interface
     */
    public static DatabaseIface openDatabase(String path, boolean readOnly) {
        return DatabaseImpl.open(path, readOnly);
    }

    /**
     * Opens the database from the specified existing path. If the specified
     * path does not exist, a new file is created.
     *
     * @param path      The path to the database, or <code>null</code> to open
     *                  an in-memory database
     * @return the database interface
     */
    public static DatabaseIface openOrCreateDatabase(String path) {
        return DatabaseImpl.openOrCreate(path);
    }
}
