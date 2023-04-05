
package com.atakmap.android.contact;

import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.util.zip.IoUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class FilteredContactsDatabase {
    public static final String TAG = "FilteredContactsDatabase";
    public static final int VERSION = 7;
    static final String TABLE_FILTEREDCONTACTS = "FilteredContacts";
    private static final String STRING_COL_TYPE = "TEXT";
    private final static File FILTEREDCONTACTS_DB_FILE = FileSystemUtils
            .getItem("Databases/filteredcontacts.sqlite");

    private DatabaseIface filteredContactsDb;

    private static class DBColumn {
        public String key;
        public String type;

        DBColumn(String key, String type) {
            this.key = key;
            this.type = type;
        }
    }

    //filteredcontacts table columns
    public final static String COLUMN_ID = "UID"; // unique id field

    private static final DBColumn[] FILTER_COLS = {
            new DBColumn(COLUMN_ID, STRING_COL_TYPE)
    };

    private void initDatabase() {

        final DatabaseIface oldFilteredContactsDb = filteredContactsDb;

        DatabaseInformation dbi = new DatabaseInformation(
                Uri.fromFile(FILTEREDCONTACTS_DB_FILE),
                DatabaseInformation.OPTION_RESERVED1
                        | DatabaseInformation.OPTION_ENSURE_PARENT_DIRS);

        DatabaseIface newFilteredContactsDb = IOProviderFactory
                .createDatabase(dbi);

        if (newFilteredContactsDb != null) {

            if (newFilteredContactsDb.getVersion() != VERSION) {
                Log.d(TAG,
                        "Upgrading from v" + newFilteredContactsDb.getVersion()
                                + " to v" + VERSION);
                onUpgrade(newFilteredContactsDb,
                        newFilteredContactsDb.getVersion(), VERSION);
            }
        } else {
            try {
                final File f = FILTEREDCONTACTS_DB_FILE;
                if (!IOProviderFactory.renameTo(f,
                        new File(FILTEREDCONTACTS_DB_FILE + ".corrupt."
                                + new CoordinatedTime().getMilliseconds()))) {
                    Log.d(TAG, "could not move corrupt db out of the way");
                } else {
                    Log.d(TAG,
                            "default chat database corrupted, move out of the way: "
                                    + f);
                }
            } catch (Exception ignored) {
            }
            newFilteredContactsDb = IOProviderFactory.createDatabase(dbi);
            if (newFilteredContactsDb != null) {
                Log.d(TAG,
                        "Upgrading from v" + newFilteredContactsDb.getVersion()
                                + " to v" + VERSION);
                onUpgrade(newFilteredContactsDb,
                        newFilteredContactsDb.getVersion(), VERSION);
            }
        }

        // swap only after the newChatDb is good to go.
        filteredContactsDb = newFilteredContactsDb;

        try {
            if (oldFilteredContactsDb != null)
                oldFilteredContactsDb.close();
        } catch (Exception ignored) {
        }

    }

    public FilteredContactsDatabase() {
        if (!IOProviderFactory.exists(FILTEREDCONTACTS_DB_FILE.getParentFile()))

            if (!IOProviderFactory
                    .mkdirs(FILTEREDCONTACTS_DB_FILE.getParentFile())) {
                Log.e(TAG, "Failed to make Directory at " +
                        FILTEREDCONTACTS_DB_FILE.getParentFile()
                                .getAbsolutePath());
            }

        initDatabase();
    }

    private void createTable(DatabaseIface db, String tableName,
            DBColumn[] columns) {
        StringBuilder createGroupTable = new StringBuilder("CREATE TABLE "
                + tableName + " (");
        String delim = "";
        for (DBColumn col : columns) {
            createGroupTable.append(delim).append(col.key).append(" ")
                    .append(col.type);
            delim = ", ";
        }
        createGroupTable.append(")");
        db.execute(createGroupTable.toString(), null);
    }

    private void onUpgrade(DatabaseIface db, int oldVersion, int newVersion) {
        // Drop older table if existed
        switch (oldVersion) {
            //wasn't implemented before so just drop the tables and recreate
            default:
                db.execute("DROP TABLE IF EXISTS " + COLUMN_ID, null);
                onCreate(db);
        }
    }

    void onDowngrade(DatabaseIface db, int oldVersion, int newVersion) {
        db.execute("DROP TABLE IF EXISTS " + TABLE_FILTEREDCONTACTS, null);
        // Create tables again
        onCreate(db);
    }

    public void close() {
        try {
            filteredContactsDb.close();
        } catch (Exception ignored) {
        }
    }

    private void onCreate(DatabaseIface db) {
        createTable(db, TABLE_FILTEREDCONTACTS, FILTER_COLS);
        db.setVersion(VERSION);
    }

    public List<Long> addUid(String uidString) {
        Log.d(TAG, "adding UID to DB.");

        // Add to DB
        long id = -1;
        DatabaseIface db;

        try {
            String existingUid = getUid(uidString);
            db = filteredContactsDb;
            if (existingUid == null) {
                StatementIface stmt = null;
                try {
                    stmt = db.compileStatement("INSERT INTO "
                            + TABLE_FILTEREDCONTACTS +
                            " (" + COLUMN_ID + ")" + " VALUES " + "(?)");
                    stmt.bind(1, uidString);
                    stmt.execute();

                    id = Databases.lastInsertRowId(db);
                } finally {
                    if (stmt != null)
                        stmt.close();
                }
            }
        } catch (SQLiteException e) {
            final MapView mv = MapView.getMapView();
            if (mv != null) {
                mv.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mv.getContext(), mv.getContext()
                                .getString(R.string.chat_text14)
                                + mv.getContext().getString(
                                        R.string.clear_db_file),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues", e);
        }

        ArrayList<Long> ids = new ArrayList<>();

        // Add the row ID to the chatMessage bundle
        if (id != -1) {
            ids.add(id);
        }

        return ids;
    }

    public String getUid(final String uid) {
        String ret = null;
        DatabaseIface db;

        // in the case that the messageId is null
        if (uid == null)
            return null;

        QueryIface query = null;
        try {
            db = filteredContactsDb;
            query = db.compileQuery("SELECT * FROM " + TABLE_FILTEREDCONTACTS
                    + " WHERE " + COLUMN_ID + "=? LIMIT 1");
            query.bind(1, uid);
            if (query.moveToNext())
                ret = query.toString();
        } finally {
            if (query != null)
                query.close();
        }
        return ret;
    }

    public List<String> getAllUids() {
        List<String> ret = new ArrayList<>();
        DatabaseIface db;

        // in the case that the messageId is null
        CursorIface cursor = null;
        try {
            db = filteredContactsDb;
            cursor = db.query(
                    "SELECT " + COLUMN_ID + " FROM " + TABLE_FILTEREDCONTACTS,
                    null);
            while (cursor.moveToNext())
                ret.add(cursor.getString(0));
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return ret;
    }

    public boolean removeUid(final String uidString) {
        boolean removed;
        DatabaseIface db;
        try {
            db = filteredContactsDb;
            db.execute("DELETE FROM " + TABLE_FILTEREDCONTACTS + " WHERE "
                    + COLUMN_ID + "=\""
                    + uidString + "\"", null);
            removed = true;
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to delete invalid chat message", e);
            removed = false;
        }
        return removed;
    }

    /**
     * Given a results table, write the values to a file.
     * @param filename the filename to write to
     * @param resultTable the results table
     * @throws IOException error if the write failed.
     */
    public static void writeToFile(final String filename,
            List<List<String>> resultTable)
            throws IOException {
        File file = new File(filename);
        IOProviderFactory.createNewFile(file);
        BufferedWriter writer = new BufferedWriter(
                IOProviderFactory.getFileWriter(file));
        try {
            for (List<String> row : resultTable) {
                String delim = "";
                for (String entry : row) {
                    writer.write(delim + entry);
                    delim = ", ";
                }
                writer.newLine();
            }
            writer.flush();
        } finally {
            IoUtils.close(writer, TAG, "failed to close the writer");
        }
    }
}
