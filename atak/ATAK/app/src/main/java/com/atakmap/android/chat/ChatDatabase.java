
package com.atakmap.android.chat;

import com.atakmap.coremap.io.DatabaseInformation;

import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;

import android.net.Uri;
import android.util.Pair;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import android.database.DatabaseUtils;
import com.atakmap.util.zip.IoUtils;

public class ChatDatabase {
    public static final String TAG = "ChatDatabase";
    public static final int VERSION = 8;

    private static DatabaseIface chatDb;

    private static final File CHAT_DB_FILE2 = FileSystemUtils
            .getItem("Databases/ChatDb2.sqlite");

    static final String TABLE_CHAT = "Chat";
    static final String TABLE_GROUPS = "Groups";
    static final String ARRAY_DELIMITER = ",";

    private static class DBColumn {
        public String key;
        public String type;

        DBColumn(String key, String type) {
            this.key = key;
            this.type = type;
        }
    }

    // By convention, make these match the names of the fields in the Bundle.
    private static final String ID_COL_NAME = "id";
    private static final String CONVO_ID_COL_NAME = "conversationId";
    private static final String CONVO_NAME_COL_NAME = "conversationName";
    private static final String MESSAGE_ID_COL_NAME = "messageId";
    private static final String PROTOCOL_COL_NAME = "protocol";
    private static final String TYPE_COL_NAME = "type";
    private static final String STATUS_COL_NAME = "status";
    private static final String RECEIVE_TIME_COL_NAME = "receiveTime";
    private static final String SENT_TIME_COL_NAME = "sentTime";
    private static final String READ_TIME_COL_NAME = "readTime";
    private static final String SENDER_UID_COL_NAME = "senderUid";
    private static final String MESSAGE_COL_NAME = "message";

    private static final String CREATED_LOCALLY = "createdLocally"; //Expressed as boolean
    private static final String RECIPIENTS = "destinations"; //Expressed as UIDs
    private static final String GROUP_PARENT = "parent";

    private static final String CONTACT_CALLSIGN_COL_NAME = "senderCallsign";

    private static String getBundleNameForColumn(String columnName) {
        return columnName;
    }

    // DB types
    private static final String PK_COL_TYPE = "INTEGER PRIMARY KEY";
    private static final String TEXT_COL_TYPE = "TEXT";
    private static final String INTEGER_COL_TYPE = "INTEGER";

    private static final DBColumn[] CHAT_COLS = {
            new DBColumn(ID_COL_NAME, PK_COL_TYPE),
            new DBColumn(CONVO_ID_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(MESSAGE_ID_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(PROTOCOL_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(TYPE_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(RECEIVE_TIME_COL_NAME, INTEGER_COL_TYPE),
            new DBColumn(SENT_TIME_COL_NAME, INTEGER_COL_TYPE),
            new DBColumn(READ_TIME_COL_NAME, INTEGER_COL_TYPE),
            new DBColumn(SENDER_UID_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(MESSAGE_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(CONTACT_CALLSIGN_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(STATUS_COL_NAME, TEXT_COL_TYPE)
    };

    private static final DBColumn[] GROUP_COLS = {
            new DBColumn(ID_COL_NAME, PK_COL_TYPE),
            new DBColumn(CONVO_ID_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(CONVO_NAME_COL_NAME, TEXT_COL_TYPE),
            new DBColumn(CREATED_LOCALLY, TEXT_COL_TYPE),
            new DBColumn(RECIPIENTS, TEXT_COL_TYPE),
            new DBColumn(GROUP_PARENT, TEXT_COL_TYPE)
    };

    private static ChatDatabase _instance = null;

    /**
     * Get an instance of the ChatDabase for search, retrieval and archive of chat messages.
     * @param ignored no longer used.
     * @return the singleton instance of the ChatDatabase.
     */
    public synchronized static ChatDatabase getInstance(Context ignored) {
        if (_instance == null) {
            _instance = new ChatDatabase();
        }
        return _instance;
    }

    private void initDatabase() {

        final DatabaseIface oldChatDb = chatDb;

        DatabaseInformation dbi = new DatabaseInformation(
                Uri.fromFile(CHAT_DB_FILE2),
                DatabaseInformation.OPTION_RESERVED1
                        | DatabaseInformation.OPTION_ENSURE_PARENT_DIRS);

        DatabaseIface newChatDb = IOProviderFactory.createDatabase(dbi);

        if (newChatDb != null) {

            if (newChatDb.getVersion() != VERSION) {
                Log.d(TAG, "Upgrading from v" + newChatDb.getVersion()
                        + " to v" + VERSION);
                onUpgrade(newChatDb, newChatDb.getVersion(), VERSION);
            }
        } else {
            try {
                final File f = CHAT_DB_FILE2;
                if (!IOProviderFactory.renameTo(f,
                        new File(CHAT_DB_FILE2 + ".corrupt."
                                + new CoordinatedTime().getMilliseconds()))) {
                    Log.d(TAG, "could not move corrupt db out of the way");
                } else {
                    Log.d(TAG,
                            "default chat database corrupted, move out of the way: "
                                    + f);
                }
            } catch (Exception ignored) {
            }
            newChatDb = IOProviderFactory.createDatabase(dbi);
            if (newChatDb != null) {
                Log.d(TAG, "Upgrading from v" + newChatDb.getVersion()
                        + " to v" + VERSION);
                onUpgrade(newChatDb, newChatDb.getVersion(), VERSION);
            }
        }

        // swap only after the newChatDb is good to go.
        chatDb = newChatDb;

        try {
            if (oldChatDb != null)
                oldChatDb.close();
        } catch (Exception ignored) {
        }

    }

    private ChatDatabase() {

        initDatabase();

    }

    void close() {
        try {
            chatDb.close();
        } catch (Exception ignored) {
        }
    }

    private void onCreate(DatabaseIface db) {
        createTable(db, TABLE_CHAT, CHAT_COLS);
        createTable(db, TABLE_GROUPS, GROUP_COLS);
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
            case 4:
                db.execute("DROP TABLE IF EXISTS " + "Contacts", null); //drop the contact table since we don't use that anymore
                db.execute("ALTER TABLE " + TABLE_CHAT + " ADD COLUMN "
                        + CONTACT_CALLSIGN_COL_NAME + " " + TEXT_COL_TYPE
                        + " DEFAULT ''", null);
            case 5:
                // Add parent column to groups
                db.execute("ALTER TABLE " + TABLE_GROUPS + " ADD COLUMN "
                        + GROUP_PARENT + " " + TEXT_COL_TYPE
                        + " DEFAULT ''", null);
            case 6:
                // Add status column to chat
                db.execute("ALTER TABLE " + TABLE_CHAT + " ADD COLUMN "
                        + STATUS_COL_NAME + " " + TEXT_COL_TYPE
                        + " DEFAULT ''", null);
                break;
            case 7:
                // Add read time column to chat
                db.execute("ALTER TABLE " + TABLE_CHAT + " ADD COLUMN "
                        + READ_TIME_COL_NAME + " " + INTEGER_COL_TYPE, null);
                break;
            default:
                db.execute("DROP TABLE IF EXISTS " + TABLE_CHAT, null);
                db.execute("DROP TABLE IF EXISTS " + TABLE_GROUPS, null);
                onCreate(db);
        }
        db.setVersion(VERSION);
    }

    void onDowngrade(DatabaseIface db, int oldVersion, int newVersion) {
        db.execute("DROP TABLE IF EXISTS " + TABLE_CHAT, null);
        db.execute("DROP TABLE IF EXISTS " + TABLE_GROUPS, null);
        // Create tables again
        onCreate(db);
        db.setVersion(VERSION);
    }

    /**
     * Ability to take a correctly formatted Chat Bundle and add it to the ChatDatabase.
     * @param chatMessage a bundle created from ChatMessage.toBundle() or a bundle containing the
     *                    following keys - "conversationName", "conversationId", "messageId",
     *                    "senderUid", "senderCallsign", "parent", "paths", "deleteChild",
     *                    "groupOwner", "message", status
     * @return a list of longs, but effectively a single row based on the Chat Message bundle.
     */
    public List<Long> addChat(Bundle chatMessage) {
        Log.d(TAG, "adding chat to DB.");

        // Populate ContentValues
        ContentValues chatValues = new ContentValues();
        for (DBColumn dbCol : CHAT_COLS) {
            String dbColName = dbCol.key;
            String bundleKey = getBundleNameForColumn(dbColName);
            String dataType = dbCol.type;
            if (TEXT_COL_TYPE.equals(dataType)) {
                String dataFromBundle = chatMessage.getString(bundleKey);

                if (dbColName != null && dataFromBundle != null) {
                    chatValues.put(dbColName, dataFromBundle);
                }
            } else if (INTEGER_COL_TYPE.equals(dataType)) {
                Long dataFromBundle = chatMessage.getLong(bundleKey, -1);
                if (dataFromBundle < 0)
                    dataFromBundle = null;
                if (dbColName != null && dataFromBundle != null) {
                    chatValues.put(dbColName, dataFromBundle);
                }
            } // ignore other types, including PK
        }
        ContentValues groupValues = new ContentValues();
        for (DBColumn dbColumn : GROUP_COLS) {
            String dbColName = dbColumn.key;
            String bundleKey = getBundleNameForColumn(dbColName);
            String dataType = dbColumn.type;
            if (TEXT_COL_TYPE.equals(dataType)) {
                String dataFromBundle;
                if (!bundleKey.equals(RECIPIENTS))
                    dataFromBundle = chatMessage.getString(bundleKey);
                else
                    dataFromBundle = convertStringArrayToString(chatMessage
                            .getStringArray(bundleKey));
                if (dbColName != null && dataFromBundle != null) {
                    groupValues.put(dbColName, dataFromBundle);
                }
            } else if (INTEGER_COL_TYPE.equals(dataType)) {
                Long dataFromBundle = chatMessage.getLong(bundleKey, -1);
                if (dataFromBundle < 0)
                    dataFromBundle = null;
                if (dbColName != null && dataFromBundle != null) {
                    groupValues.put(dbColName, dataFromBundle);
                }
            }
        }

        // Add to DB
        long id = -1;
        String convId = groupValues.getAsString(CONVO_ID_COL_NAME);
        long groupId = getGroupIndex(convId);
        DatabaseIface db;
        try {
            String msgId = chatValues.getAsString(MESSAGE_ID_COL_NAME);
            Bundle existingMsg = getChatMessage(msgId);
            db = chatDb;
            if (existingMsg != null) {

                String v = parseForUpdate(chatValues);
                StatementIface stmt = null;
                try {
                    stmt = db.compileStatement("UPDATE " + TABLE_CHAT + " SET "
                            + v + " WHERE " + MESSAGE_ID_COL_NAME
                            + "=(?)");
                    stmt.bind(1, msgId);
                    stmt.execute();
                } finally {
                    if (stmt != null)
                        stmt.close();
                }

                id = existingMsg.getLong(ID_COL_NAME);
            } else {
                Pair<String, String[]> v = parseForInsert(chatValues);
                StatementIface stmt = null;
                try {
                    stmt = db.compileStatement("INSERT INTO " + TABLE_CHAT + "("
                            + v.first + ")" + " VALUES " + "("
                            + formWildcard(v.second) + ")");
                    for (int i = 0; i < v.second.length; ++i)
                        stmt.bind(i + 1, v.second[i]);

                    stmt.execute();
                    id = Databases.lastInsertRowId(db);
                } finally {
                    if (stmt != null)
                        stmt.close();
                }
            }
            //check to make sure it's a group that should be persisted (ie group name doesn't
            // equal the UID)  All streaming is a special case.
            Contacts cts = Contacts.getInstance();
            String convName = groupValues.getAsString(CONVO_NAME_COL_NAME);
            Contact existing = cts.getContactByUuid(convId);
            if (isUserGroup(convName, convId)) {
                // Make sure parent groups exist
                String selfUID = MapView.getDeviceUid();
                String sender = chatValues.getAsString(SENDER_UID_COL_NAME);
                String destinations = groupValues.getAsString(RECIPIENTS);
                boolean local = sender != null
                        && sender.equals(selfUID)
                        && !destinations.contains(convId);
                // If the group exists this should take priority
                if (GroupContact.isGroup(existing))
                    local = ((GroupContact) existing).isUserCreated()
                            || convId.equals(Contacts.USER_GROUPS);
                HierarchyParser parser = new HierarchyParser(chatMessage);
                if (!parser.build() && groupId == -1) {
                    // Legacy user group
                    groupValues.put(CREATED_LOCALLY, String.valueOf(local));
                    Pair<String, String[]> v = parseForInsert(groupValues);
                    StatementIface stmt = null;
                    try {
                        stmt = db.compileStatement(
                                "INSERT INTO " + TABLE_GROUPS + "("
                                        + v.first + ")" + " VALUES " + "("
                                        + formWildcard(v.second) + ")");
                        for (int i = 0; i < v.second.length; ++i)
                            stmt.bind(i + 1, v.second[i]);

                        stmt.execute();
                        groupId = Databases.lastInsertRowId(db);
                    } finally {
                        if (stmt != null)
                            stmt.close();
                    }
                }
            }
            String deleteUID = chatMessage.getString(
                    "deleteChild", null);
            if (deleteUID != null) {
                Contact del = cts.getContactByUuid(deleteUID);
                if (isUserGroup(del.getName(), del.getUID()))
                    removeGroupTree(del);
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
            chatMessage.putLong("id", id);
            chatMessage.putLong("groupId", groupId);

            ids.add(id);
            ids.add(groupId);
        }

        return ids;
    }

    /**
     * Turns a ContentValues class into a string in the form "(key=value, key1=value1,...,keyn=valuen)"
     * @param cv the ContentValues used to parse for the update.
     */
    private String parseForUpdate(final ContentValues cv) {
        StringBuilder kRet = new StringBuilder();
        final Set<String> keys = cv.keySet();
        for (String key : keys) {
            String value = cv.getAsString(key);
            if (kRet.length() != 0)
                kRet.append(",");
            kRet.append(key).append("=").append(DatabaseUtils
                    .sqlEscapeString(value));
        }
        return kRet.toString();
    }

    // TODO - Use CL's suggestion about the Map.
    // check out the class com.atakmap.database.android.BindArgument
    //you can return a LinkedHashMap<String, BindArgument> instead of a pair, where String is column name (used to build SQL) and the bind arguments are the args (edited)
    // there's a static function on BindArgument that takes a collection of them, compiles a statement, binds the arguemtns and executes it

    private void dumbBind(final StatementIface stmt, final String[] args) {
    }

    private String formWildcard(final String[] args) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < args.length; ++i) {
            if (i != 0)
                ret.append(",");
            ret.append("?");
        }
        return ret.toString();
    }

    private Pair<String, String[]> parseForInsert(ContentValues cv) {
        StringBuilder kRet = new StringBuilder();
        List<String> l = new ArrayList<>();
        final Set<String> keys = cv.keySet();
        for (String key : keys) {
            String value = cv.getAsString(key);
            if (value != null) {
                if (kRet.length() != 0)
                    kRet.append(",");
                kRet.append(key);
                l.add(value);
            }
        }
        String[] vRet = new String[l.size()];
        l.toArray(vRet);
        return new Pair<>(kRet.toString(), vRet);
    }

    private class HierarchyParser {

        private final Contacts _cts;
        private final Contact _userGroups;
        private final String _selfUID;
        private final String _senderUID;
        private final Contact _sender;
        private final String _groupID;
        private final boolean _groupOwner;
        private final boolean _sending;
        private final Bundle _paths;

        private HierarchyParser(Bundle msg) {
            _cts = Contacts.getInstance();
            _userGroups = _cts.getContactByUuid(Contacts.USER_GROUPS);
            _selfUID = MapView.getDeviceUid();
            _senderUID = msg.getString(SENDER_UID_COL_NAME);
            String senderName = msg.getString(CONTACT_CALLSIGN_COL_NAME);
            Contact sender = _cts.getContactByUuid(_senderUID);
            _sender = sender == null ? new IndividualContact(senderName,
                    _senderUID) : sender;
            _groupOwner = msg.getBoolean("groupOwner");
            _groupID = msg.getString(CONVO_ID_COL_NAME);
            _sending = (_selfUID != null) && _senderUID.equals(_selfUID);
            _paths = msg.getBundle("paths");
        }

        public boolean build() {
            if (_sending)
                return true;
            if (_paths != null && !_paths.isEmpty()) {
                List<GroupContact> newGroups = new ArrayList<>();

                // Build contact hierarchy and store new groups in list
                buildContact(_paths, newGroups);

                // Update group usage locks
                Contact group = _cts.getContactByUuid(_groupID);
                if (GroupContact.isGroup(group)) {
                    GroupContact root = ((GroupContact) group)
                            .getRootUserGroup();
                    if (root != null)
                        root.updateLocks();
                    else
                        ((GroupContact) group).updateLocks();
                }

                // Once everything is finished, add/update new groups to database
                for (GroupContact gc : newGroups)
                    addGroup(gc, false);

                return true;
            }
            return false;
        }

        /**
         * Convert bundle to contact and automatically include
         * any children contacts
         * @param bundle Contact bundle
         * @param newGroups New groups are stored here
         * @return Individual or group contact (null if self)
         */
        private Contact buildContact(Bundle bundle,
                List<GroupContact> newGroups) {
            String uid = bundle.getString("uid");
            String name = bundle.getString("name");
            String type = bundle.getString("type");
            Contact existing = _cts.getContactByUuid(uid);
            if (existing == null) {
                // Create and register new contact
                if (type != null && type.equals("group"))
                    existing = new GroupContact(uid, name, false);
                else
                    existing = new IndividualContact(name, uid);
            }
            if (existing instanceof GroupContact) {
                GroupContact gc = (GroupContact) existing;
                // We shouldn't modify our own groups here
                if (gc.isUserCreated())
                    return null;
                else if (_groupOwner && gc != _userGroups)
                    gc.clearHierarchy();
                // Build child hierarchy
                for (String key : bundle.keySet()) {
                    Object child = bundle.get(key);
                    if (child instanceof Bundle) {
                        Contact c = buildContact((Bundle) child, newGroups);
                        if (c == null)
                            break;
                        if (gc == _userGroups) {
                            if (c instanceof IndividualContact) {
                                // Never add individuals to root user group
                                continue;
                            } else if (_groupOwner
                                    && c instanceof GroupContact) {
                                // Make sure group creator is part of the root user group
                                ((GroupContact) c).addContact(_sender);
                            }
                        }
                        _cts.addContact(gc, c);
                    }
                }
                // Add group to list of groups to be added to database
                newGroups.add(gc);
            }
            return existing;
        }
    }

    /**
     * If true the group is persisted on restart.
     * 
     * @param conversationId The conversation identifier that will will be modified in the group table.
     * @param bool if true, the group is set to locally created.
     */
    void changeLocallyCreated(final String conversationId, final boolean bool) {
        DatabaseIface db;
        try {
            db = chatDb;
            StatementIface stmt = null;
            try {
                stmt = db.compileStatement("UPDATE " + TABLE_GROUPS + " SET "
                        + CREATED_LOCALLY
                        + "=\"" +
                        bool + "\" WHERE " + CONVO_ID_COL_NAME
                        + "=(?)");
                stmt.bind(1, conversationId);
                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues", e);
        }
    }

    private static String convertStringArrayToString(final String[] arr) {
        if (arr == null)
            return null;

        StringBuilder sb = new StringBuilder();
        String delim = "";
        for (String str : arr) {
            sb.append(delim).append(str);
            delim = ARRAY_DELIMITER;
        }

        return sb.toString();
    }

    private static Bundle cursorToBundle(CursorIface cursor) {
        Bundle row = new Bundle();
        for (int col = 0; col < cursor.getColumnCount(); col++) {
            if (!cursor.isNull(col)) {
                if (CursorIface.FIELD_TYPE_INTEGER == cursor.getType(col)) {
                    row.putLong(cursor.getColumnName(col), cursor.getLong(col));
                } else if (CursorIface.FIELD_TYPE_STRING == cursor
                        .getType(col)) {
                    row.putString(cursor.getColumnName(col),
                            cursor.getString(col));
                } else if (CursorIface.FIELD_TYPE_FLOAT == cursor
                        .getType(col)) {
                    row.putDouble(cursor.getColumnName(col),
                            cursor.getDouble(col));
                }
            } // ignore NULL and BLOB
        }
        return row;
    }

    private long getGroupIndex(String conversationId) {
        long ret = -1;
        DatabaseIface db;
        CursorIface cursor = null;
        try {
            db = chatDb;
            cursor = db.query(
                    "SELECT " + ID_COL_NAME + " FROM " + TABLE_GROUPS
                            + " WHERE " + CONVO_ID_COL_NAME + "="
                            + DatabaseUtils.sqlEscapeString(conversationId),
                    null);

            if (cursor.moveToNext())
                ret = cursor.getLong(0);
        } catch (SQLiteException e) {
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return ret;
    }

    public void addGroup(GroupContact gc, boolean local) {
        if (gc == null || !isUserGroup(gc.getName(), gc.getUID()))
            return;
        ContentValues groupValues = new ContentValues();
        groupValues.put(CONVO_ID_COL_NAME, gc.getUID());
        groupValues.put(CONVO_NAME_COL_NAME, gc.getName());
        groupValues.put(CREATED_LOCALLY, String.valueOf(local));
        groupValues.put(GROUP_PARENT, gc.getParentUID());

        List<String> dests = gc.getAllContactUIDs(false);
        StringBuilder destSB = new StringBuilder();
        for (int i = 0; i < dests.size(); i++) {
            String uid = dests.get(i);
            if (uid == null || uid.isEmpty())
                continue;
            destSB.append(uid);
            if (i < dests.size() - 1)
                destSB.append(",");
        }
        String destStr = destSB.toString();
        if (destStr.endsWith(","))
            destStr = destStr.substring(0, destStr.length() - 1);
        groupValues.put(RECIPIENTS, destStr);
        try {
            DatabaseIface db = chatDb;
            //check to make sure it's a group that should be persisted (ie group name doesn't
            // equal the UID)  All streaming is a special case.
            Log.d(TAG, "Adding " + gc.getName() + " ("
                    + gc.getUID() + ") to database (local = " + local + ")");
            long groupId = getGroupIndex(gc.getUID());
            if (groupId == -1) {
                Pair<String, String[]> v = parseForInsert(groupValues);
                StatementIface stmt = null;
                try {
                    stmt = db.compileStatement("INSERT INTO " + TABLE_GROUPS
                            + "(" + v.first + ")" + " VALUES " + "("
                            + formWildcard(v.second) + ")");
                    for (int i = 0; i < v.second.length; ++i)
                        stmt.bind(i + 1, v.second[i]);

                    stmt.execute();
                    groupId = Databases.lastInsertRowId(db);
                } finally {
                    if (stmt != null) {
                        stmt.close();
                    }
                }
            } else {
                String v = parseForUpdate(groupValues);
                StatementIface stmt = null;
                try {
                    stmt = db.compileStatement("UPDATE " + TABLE_GROUPS
                            + " SET " + v + " WHERE " + "id=(?)");
                    stmt.bind(1, groupId);

                    stmt.execute();
                } finally {
                    if (stmt != null)
                        stmt.close();
                }
            }
        } catch (SQLiteException e) {
            Toast.makeText(MapView.getMapView().getContext(),
                    MapView.getMapView().getContext()
                            .getString(R.string.chat_text14) +
                            MapView.getMapView().getContext()
                                    .getString(R.string.clear_db_file),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues", e);
        }
    }

    public void removeGroup(final String conversationId) {
        Log.d(TAG, "Removing " + conversationId + " from database");
        DatabaseIface db;
        try {
            db = chatDb;
            db.execute("DELETE FROM " + TABLE_GROUPS + " WHERE " +
                    CONVO_ID_COL_NAME + "=\"" + conversationId + "\"", null);
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to delete invalid Chat Group.", e);
        }
    }

    /**
     * Remove group contact and all its sub-groups
     * @param c Group contact
     */
    private void removeGroupTree(final Contact c) {
        if (GroupContact.isGroup(c)) {
            Log.d(TAG, "Removing group tree " + c.getName());
            Contacts cts = Contacts.getInstance();
            List<GroupContact> del = new ArrayList<>();
            del.add((GroupContact) c);
            for (Contact child : ((GroupContact) c)
                    .getAllContacts(true)) {
                if (GroupContact.isGroup(child))
                    del.add((GroupContact) child);
            }
            for (GroupContact gc : del) {
                cts.removeContact(gc);
                removeGroup(gc.getUID());
            }
        }
    }

    private boolean isUserGroup(final String groupName, final String groupUID) {
        String deviceUID = MapView.getDeviceUid();
        Contact existing = Contacts.getInstance()
                .getContactByUuid(groupUID);
        return (existing == null || existing
                .descendedFrom(Contacts.USER_GROUPS))
                && !groupName.equals(groupUID)
                && !groupUID.toLowerCase(LocaleUtil.getCurrent()).equals(
                        deviceUID.toLowerCase(LocaleUtil.getCurrent()));
    }

    /**
     * Returns the history of a given conversation as a list of bundles.
     * @param conversationId the chat conversation identification 
     * @return return an array of bundles that represent each line in the
     * chat history where the bundle is a representation of the cursor return.
     */
    public List<Bundle> getHistory(final String conversationId) {
        List<Bundle> ret = new LinkedList<>();
        DatabaseIface db;
        CursorIface cursor = null;
        try {
            db = chatDb;
            cursor = db.query(
                    "SELECT * FROM " + TABLE_CHAT + " WHERE "
                            + CONVO_ID_COL_NAME + "= ?"
                            + " ORDER BY CASE"
                            + " WHEN " + RECEIVE_TIME_COL_NAME
                            + " IS NULL THEN " + SENT_TIME_COL_NAME
                            + " ELSE " + RECEIVE_TIME_COL_NAME
                            + " END",
                    new String[] {
                            conversationId
                    });
            if (cursor.moveToNext()) {
                do {
                    ret.add(cursorToBundle(cursor));
                } while (cursor.moveToNext());
            }
        } catch (IllegalStateException e) {
            String name = "<UNKNOWN>";
            Contact contact = Contacts.getInstance().getContactByUuid(
                    conversationId);
            if (contact != null)
                name = contact.getName();
            Toast.makeText(
                    MapView.getMapView().getContext(),
                    "Error retrieving history for " + name,
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error retrieving chat history for " + name + "!", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return ret;
    }

    /**
     * Returns a list of the persisted conversation identifiers.
     * @return the conversaiton identifiers for the system that have been persisted.
     */
    public List<String> getPersistedConversationIds() {
        List<String> ret = new ArrayList<>();
        DatabaseIface db;
        CursorIface cursor = null;
        try {
            db = chatDb;
            cursor = db.query("SELECT " + CONVO_ID_COL_NAME + " FROM "
                    + TABLE_GROUPS, null);
            while (cursor.moveToNext())
                ret.add(cursor.getString(0));
        } catch (SQLiteException e) {
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues", e);
        } finally {
            try {
                if (cursor != null)
                    cursor.close();
            } catch (Exception ignored) {
            }
        }
        return ret;
    }

    /**
     * Returns the group information for a specific conversation identifier.
     * @param conversationId the conversationId
     * @return the group informtion in list form.
     */
    public List<String> getGroupInfo(final String conversationId) {

        List<String> ret = new ArrayList<>();
        DatabaseIface db;
        CursorIface cursor = null;
        try {
            db = chatDb;
            cursor = db.query(
                    "SELECT " +
                            CONVO_NAME_COL_NAME + "," + RECIPIENTS + ","
                            + GROUP_PARENT + "," +
                            CREATED_LOCALLY
                            + " FROM " + TABLE_GROUPS + " WHERE " +
                            CONVO_ID_COL_NAME + " = ?" + " LIMIT 1",
                    new String[] {
                            conversationId
                    });
            if (cursor.moveToNext()) {
                ret.add(cursor.getString(0));
                ret.add(cursor.getString(1));
                ret.add(cursor.getString(2));
                ret.add(cursor.getString(3));
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Experienced an issue with the SQL Query.  " +
                    "Clear your DB file if this continues", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return ret;
    }

    /**
     * Obtain the list of conversation identifiers.
     * @return the list of string identifiers.
     */
    public List<String> getAvailableConversations() {
        List<String> ret = new LinkedList<>();
        DatabaseIface db;
        CursorIface cursor = null;
        try {
            db = chatDb;
            cursor = db.query(
                    "SELECT DISTINCT " + CONVO_ID_COL_NAME + " FROM "
                            + TABLE_CHAT,
                    null);
            if (cursor.moveToNext()) {
                do {
                    ret.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return ret;
    }

    /**
     * Given an id and a table, return the bundle for a specific message.
     * @param id the given message id
     * @param table the table to pull from
     * @return the bundle that represents the message.
     */
    public Bundle getMessage(final long id, final String table) {
        Bundle ret = null;
        DatabaseIface db;

        CursorIface cursor = null;
        try {
            db = chatDb;
            cursor = db.query(
                    "SELECT * FROM " + table + " WHERE "
                            + ID_COL_NAME + "=\"" + id + "\"",
                    null);
            if (cursor.moveToNext()) {
                ret = cursorToBundle(cursor);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return ret;
    }

    /**
     * Given a specific message identifier, retrieve the chat message associated.
     * @param messageId the messageId
     * @return the bundle that represents the chat message.
     */
    public Bundle getChatMessage(final String messageId) {
        Bundle ret = null;
        DatabaseIface db;

        // in the case that the messageId is null
        if (messageId == null)
            return null;

        QueryIface query = null;
        try {
            db = chatDb;
            query = db.compileQuery("SELECT * FROM " + TABLE_CHAT
                    + " WHERE " + MESSAGE_ID_COL_NAME + "=? LIMIT 1");
            query.bind(1, messageId);
            if (query.moveToNext())
                ret = cursorToBundle(query);
        } finally {
            if (query != null)
                query.close();
        }
        return ret;
    }

    /**
     * The ability to remove a chat message from the database given a message identifier.
     * @param messageId the message identifier.
     * @return true if the deletion was sucessfull, false if the deletion failed.
     */
    public boolean removeChatMessage(final String messageId) {
        boolean removed;
        DatabaseIface db;
        try {
            db = chatDb;
            db.execute("DELETE FROM " + TABLE_CHAT + " WHERE "
                    + MESSAGE_ID_COL_NAME + "=\""
                    + messageId + "\"", null);
            removed = true;
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to delete invalid chat message", e);
            removed = false;
        }
        return removed;
    }

    void clearAll() {
        chatDb.close();
        IOProviderFactory.delete(CHAT_DB_FILE2, IOProvider.SECURE_DELETE);

        initDatabase();
        fireChatDatabaseChanged();
    }

    void clearOlderThan(final long minTimeToKeep) {
        DatabaseIface db;

        db = chatDb;
        db.execute("DELETE FROM " + TABLE_CHAT + " WHERE " +
                " (? IS NOT NULL AND ? < ?) OR (? IS NOT NULL AND ? < ?) ",
                new String[] {
                        SENT_TIME_COL_NAME, SENT_TIME_COL_NAME,
                        "" + minTimeToKeep,
                        RECEIVE_TIME_COL_NAME, RECEIVE_TIME_COL_NAME,
                        "" + minTimeToKeep
                });
        fireChatDatabaseChanged();

    }

    private void fireChatDatabaseChanged() {
        // TODO: remove intent firing and make this a real callback
        com.atakmap.android.ipc.AtakBroadcast.getInstance().sendBroadcast(
                new android.content.Intent(GeoChatService.HISTORY_UPDATE));

    }

    void exportHistory(final String filename) {
        DatabaseIface db;
        CursorIface cursor = null;
        List<List<String>> resultTable;
        try {
            db = chatDb;
            cursor = db.query(
                    "SELECT * FROM " + TABLE_CHAT, null);
            resultTable = new LinkedList<>();
            //Code to add header to the csv exported -------------
            List<String> colTitle = new LinkedList<>();
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                colTitle.add(cursor.getColumnName(i));
            }
            resultTable.add(0, colTitle);
            //end of header code ---------------------------------
            if (cursor.moveToNext()) {
                do {
                    List<String> currentRow = new LinkedList<>();
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        currentRow.add(cursor.getString(i));
                    }
                    resultTable.add(currentRow);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        try {
            writeToFile(filename, resultTable);
        } catch (IOException ioe) {
            Log.e(TAG, "Error writing chat history to file " + filename, ioe);
        }
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
