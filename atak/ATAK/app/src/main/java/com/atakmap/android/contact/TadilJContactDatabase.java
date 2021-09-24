
package com.atakmap.android.contact;

import android.content.ContentValues;
import android.database.sqlite.SQLiteException;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.NetConnectString;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.android.AndroidDatabaseAdapter;
import com.atakmap.database.android.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Database to maintain Tadil-J contact info.  Allows for the connections
 * to be persisted between ATAK instances.
 */
public class TadilJContactDatabase extends SQLiteOpenHelper {
    public static final String TAG = "TadilJContactDatabase";
    public static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "TadilJDb.sqlite";
    public static final String TABLE_CONTACTS = "Contacts";
    public static final String ARRAY_DELIMITER = ",";

    static class DBColumn {
        public String key = null;
        public String type = null;

        public DBColumn(String key, String type) {
            this.key = key;
            this.type = type;
        }
    }

    // By convention, make these match the names of the fields in the Bundle.
    private static final String ID_COL_NAME = "id";
    private static final String CONTACT_UID_COL_NAME = "contactUid";
    private static final String CONTACT_NAME_COL_NAME = "contactName";
    private static final String ENABLED_COL_NAME = "enabled";
    private static final String POINT_CONN_INFO_COL_NAME = "pointConnectionInfo";
    private static final String CHAT_CONN_INFO_COL_NAME = "chatConnectionInfo";

    private static String getBundleNameForColumn(String columnName) {
        return columnName;
    }

    // DB types
    private static final String PK_COL_TYPE = "INTEGER PRIMARY KEY";
    private static final String TEXT_COL_TYPE = "TEXT";
    private static final String INTEGER_COL_TYPE = "INTEGER";

    private static final DBColumn[] CONTACT_COLS = {
            new DBColumn(ID_COL_NAME, PK_COL_TYPE),
            new DBColumn(CONTACT_UID_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(CONTACT_NAME_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(ENABLED_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(POINT_CONN_INFO_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(CHAT_CONN_INFO_COL_NAME, TEXT_COL_TYPE)
    };

    private static TadilJContactDatabase _instance = null;

    private final File databaseFile;

    synchronized public static TadilJContactDatabase getInstance() {
        if (_instance == null) {
            _instance = new TadilJContactDatabase(
                    FileSystemUtils.getItem("Databases/" + DATABASE_NAME));
        }
        return _instance;
    }

    // this object does not cache any state, so reloading the connection on
    // provider change is sufficient

    private TadilJContactDatabase(File dbfile) {
        super(dbfile.getAbsolutePath(), DATABASE_VERSION, true);
        this.databaseFile = dbfile;
    }

    @Override
    public void onCreate(DatabaseIface db) {
        createTable(db, TABLE_CONTACTS, CONTACT_COLS);
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

    @Override
    public void onUpgrade(DatabaseIface db, int oldVersion, int newVersion) {
        // Drop older table if existed
        switch (oldVersion) {
            //wasn't implemented before so just drop the tables and recreate
            default:
                db.execute("DROP TABLE IF EXISTS " + TABLE_CONTACTS, null);
                onCreate(db);
        }
    }

    @Override
    public void onDowngrade(DatabaseIface db, int oldVersion, int newVersion) {
        db.execute("DROP TABLE IF EXISTS " + TABLE_CONTACTS, null);
        // Create tables again
        onCreate(db);
    }

    public void clearTable() {
        DatabaseIface db = null;
        try {
            db = getWritableDatabase();
            db.execute("DELETE FROM " + TABLE_CONTACTS, null);
        } catch (SQLiteException e) {
            Toast.makeText(MapView.getMapView().getContext(),
                    "TADIL-J DB broke",
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues");
            Log.e(TAG, "error occurred", e);
        } finally {
            if (db != null)
                db.close();
        }
    }

    /**
     * Add or update the specified contact to the DB
     * @param contact TadilJContact object to be added
     * @return Result of add, True if added/updated, false if error
     */

    public boolean addContact(TadilJContact contact) {
        boolean result = false;
        ContentValues contactValues = new ContentValues();
        contactValues.put(CONTACT_UID_COL_NAME, contact.getUID());
        contactValues.put(CONTACT_NAME_COL_NAME, contact.getName());
        contactValues
                .put(ENABLED_COL_NAME, String.valueOf(contact.isEnabled()));

        final Connector ipConnector = contact
                .getConnector(IpConnector.CONNECTOR_TYPE);

        if (ipConnector != null)
            contactValues.put(POINT_CONN_INFO_COL_NAME, ipConnector
                    .getConnectionString());

        final Connector tjConnector = contact
                .getConnector(TadilJChatConnector.CONNECTOR_TYPE);

        if (tjConnector != null)
            contactValues.put(CHAT_CONN_INFO_COL_NAME, tjConnector
                    .getConnectionString());

        // Add to DB
        long id = getId(contact.getUID());
        DatabaseIface db = null;
        try {
            db = getWritableDatabase();
            if (id == -1)
                id = AndroidDatabaseAdapter.insert(db, TABLE_CONTACTS, null,
                        contactValues);
            else
                AndroidDatabaseAdapter.update(db, TABLE_CONTACTS, contactValues,
                        "id=" + id, null);
            if (id != -1)
                result = true;
        } catch (SQLiteException e) {
            Toast.makeText(MapView.getMapView().getContext(),
                    "TADIL-J DB broke",
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues");
            Log.e(TAG, "error occurred", e);
        } finally {
            if (db != null)
                db.close();
        }
        return result;
    }

    private long getId(String uid) {
        long ret = -1;
        DatabaseIface db = null;
        CursorIface cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.query(
                    "SELECT " + ID_COL_NAME + " FROM " + TABLE_CONTACTS
                            + " WHERE "
                            + CONTACT_UID_COL_NAME + "=\""
                            + uid + "\"",
                    null);
            if (cursor.moveToNext())
                ret = cursor.getLong(0);
        } catch (SQLiteException e) {
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues");
            Log.e(TAG, "error occurred", e);
        } finally {
            if (cursor != null)
                cursor.close();
            if (db != null)
                db.close();
        }
        return ret;
    }

    /**
     * Remove the specified contact from the DB
     * @param uid the uid for the desired contact to remove
     * @return true if the contact was removed.
     */

    public boolean removeContact(String uid) {
        boolean result = false;
        DatabaseIface db = null;
        try {
            db = this.getWritableDatabase();
            db.execute("DELETE FROM " + TABLE_CONTACTS + " WHERE " +
                    CONTACT_UID_COL_NAME + "=\"" + uid + "\"", null);
            result = true;
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to delete invalid Contact.");
            Log.e(TAG, "error occurred", e);
        } finally {
            if (db != null) {
                db.close();
            }
        }
        return result;
    }

    /**
     * Retrieve all Tadil-J contacts in the DB.
     * @return
     */

    public List<Contact> getContacts() {
        List<Contact> ret = new ArrayList<>();
        DatabaseIface db = null;
        CursorIface cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.query("SELECT * FROM " + TABLE_CONTACTS, null);
            if (cursor.moveToNext()) {
                do {
                    TadilJContact temp = new TadilJContact(cursor.getString(2),
                            cursor.getString(1),
                            Boolean.parseBoolean(cursor.getString(3)),
                            new IpConnector(NetConnectString.fromString(cursor
                                    .getString(4))),
                            new IpConnector(NetConnectString.fromString(cursor
                                    .getString(5))));
                    ret.add(temp);
                } while (cursor.moveToNext());
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues");
            Log.e(TAG, "error occurred", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return ret;
    }

    /**
     * Retrieve a specific Tadil-J contact from the DB.
     * @param uid
     * @return
     */

    public TadilJContact getContact(String uid) {
        TadilJContact result = null;
        DatabaseIface db = null;
        CursorIface cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.query("SELECT * FROM " + TABLE_CONTACTS
                    + " WHERE " +
                    CONTACT_UID_COL_NAME + "=\"" + uid + "\"", null);
            if (cursor.moveToNext()) {
                result = new TadilJContact(cursor.getString(2),
                        cursor.getString(1),
                        Boolean.parseBoolean(cursor.getString(3)),
                        new IpConnector(NetConnectString.fromString(cursor
                                .getString(4))));
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to delete invalid Contact.");
            Log.e(TAG, "error occurred", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return result;
    }

    public void clearAll() {
        // logic adopted from `SecureDelete.deleteDatabase(SQLiteOpenHelper)`
        DatabaseIface db = getReadableDatabase();
        if (db != null)
            db.close();
        IOProviderFactory.delete(this.databaseFile, IOProvider.SECURE_DELETE);
    }
}
