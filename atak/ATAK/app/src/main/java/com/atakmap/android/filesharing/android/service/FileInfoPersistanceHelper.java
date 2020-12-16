
package com.atakmap.android.filesharing.android.service;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteBlobTooBigException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.filesystem.SecureDelete;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Tables: (all same schema) transferFileinfo: Files available
 * for download savedFileinfo: Files saved by user, not yet available for download
 */
public class FileInfoPersistanceHelper extends SQLiteOpenHelper {
    private static final String TAG = "FileInfoPersistanceHelper";

    private static final int DATABASE_VERSION = 3;
    private static final String DATABASE_NAME = "fileinfodb";
    private static final String TRANSFER_TABLE_NAME = "transferFileinfo";
    private static final String SAVED_TABLE_NAME = "savedFileinfo";
    private static final String LOG_TABLE_NAME = "FileTransferLog";

    private static String TRANSFER_TABLE_CREATE_STR = null;
    private static String SAVED_TABLE_CREATE_STR = null;
    private static String LOG_TABLE_CREATE_STR = null;
    private static String LOG_TABLE_TRIGGER_SQL = null;

    private final Set<FileTransferLog.Listener> _ftListeners = new HashSet<>();

    static {
        // Automatically create the DB table based on the FileInfo meta-data
        // TODO: probably want an automatically generated UID field...
        StringBuilder format = new StringBuilder("CREATE TABLE %s(");
        for (int i = 0; i < FileInfo.META_DATA_LABELS.length; i++) {
            format.append(FileInfo.META_DATA_LABELS[i][0]).append(" ")
                    .append(FileInfo.META_DATA_LABELS[i][1]);
            if (i + 1 < FileInfo.META_DATA_LABELS.length)
                format.append(", ");
        }
        format.append(");");
        TRANSFER_TABLE_CREATE_STR = String.format(format.toString(),
                TRANSFER_TABLE_NAME);
        SAVED_TABLE_CREATE_STR = String.format(format.toString(),
                SAVED_TABLE_NAME);

        format = new StringBuilder("CREATE TABLE %s(");
        for (int i = 0; i < FileTransferLog.META_DATA_LABELS.length; i++) {
            format.append(FileTransferLog.META_DATA_LABELS[i][0]).append(" ")
                    .append(FileTransferLog.META_DATA_LABELS[i][1]);
            if (i + 1 < FileTransferLog.META_DATA_LABELS.length)
                format.append(", ");
        }
        format.append(");");
        LOG_TABLE_CREATE_STR = String.format(format.toString(), LOG_TABLE_NAME);

        // now setup a trigger to automatically manage size of log table
        int MaxLogTableSize = 30;
        LOG_TABLE_TRIGGER_SQL = "CREATE TRIGGER limitlogtablsize AFTER INSERT ON "
                + LOG_TABLE_NAME +
                " BEGIN DELETE FROM " + LOG_TABLE_NAME +
                " WHERE " + FileTransferLog.ID_LABEL +
                " <= (SELECT " + FileTransferLog.ID_LABEL + " FROM "
                + LOG_TABLE_NAME +
                " ORDER BY " + FileTransferLog.ID_LABEL
                + " DESC LIMIT "
                + MaxLogTableSize + ", 1); END;";
    }

    public enum TABLETYPE {
        TRANSFER(TRANSFER_TABLE_NAME, TRANSFER_TABLE_CREATE_STR),
        SAVED(SAVED_TABLE_NAME, SAVED_TABLE_CREATE_STR);

        String _tableName;
        String _tableCreateSQL;

        TABLETYPE(String tableName, String tableCreateSQL) {
            _tableName = tableName;
            _tableCreateSQL = tableCreateSQL;
        }

        public String getTableName() {
            return _tableName;
        }

        public String getTableCreateSQL() {
            return _tableCreateSQL;
        }
    }

    private static FileInfoPersistanceHelper instance = null;

    public synchronized static FileInfoPersistanceHelper initialize(
            final Context context) {
        if (instance == null) {
            instance = new FileInfoPersistanceHelper(context);
        }
        return instance;
    }

    public synchronized static FileInfoPersistanceHelper instance() {
        return instance;
    }

    private FileInfoPersistanceHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        try {
            Log.d(TAG, "creating table: " + TABLETYPE.TRANSFER.getTableName()
                    + " with SQL: "
                    + TABLETYPE.TRANSFER.getTableCreateSQL());
            db.execSQL(TABLETYPE.TRANSFER.getTableCreateSQL());
        } catch (Exception e) {
            Log.e(TAG,
                    "exception while creating table: "
                            + TABLETYPE.TRANSFER.getTableName(),
                    e);
        }

        try {
            Log.d(TAG, "creating table: " + TABLETYPE.SAVED.getTableName()
                    + " with SQL: "
                    + TABLETYPE.SAVED.getTableCreateSQL());
            db.execSQL(TABLETYPE.SAVED.getTableCreateSQL());
        } catch (Exception e) {
            Log.e(TAG,
                    "exception while creating table: "
                            + TABLETYPE.SAVED.getTableName(),
                    e);
        }

        try {
            Log.d(TAG, "creating table: " + LOG_TABLE_NAME + " with SQL: "
                    + LOG_TABLE_CREATE_STR);
            db.execSQL(LOG_TABLE_CREATE_STR);
        } catch (Exception e) {
            Log.e(TAG, "exception while creating table: " + LOG_TABLE_NAME, e);
        }

        try {
            Log.d(TAG, "creating table: " + LOG_TABLE_NAME
                    + " trigger with SQL: "
                    + LOG_TABLE_TRIGGER_SQL);
            db.execSQL(LOG_TABLE_TRIGGER_SQL);
        } catch (Exception e) {
            Log.e(TAG, "exception while creating table trigger on: "
                    + LOG_TABLE_NAME, e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop tables and re-wrap
        Log.d(TAG, "onUpgrade: v" + oldVersion + " to v" + newVersion);
        db.execSQL("DROP TABLE IF EXISTS " + TABLETYPE.TRANSFER.getTableName());
        db.execSQL("DROP TABLE IF EXISTS " + TABLETYPE.SAVED.getTableName());
        db.execSQL("DROP TABLE IF EXISTS " + LOG_TABLE_NAME);
        onCreate(db);
    }

    public void clearAll() {
        SecureDelete.deleteDatabase(this);
    }

    /**
     * Simply close the DB
     */
    public void dispose() {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            if (db != null)
                db.close();
        } catch (Exception e) {
            Log.w(TAG, "Failed to close DB", e);
        }
        synchronized (_ftListeners) {
            _ftListeners.clear();
        }
    }

    /**
     * All tables have the same schema
     */
    private static String[] getFileInfoColumns() {
        String[] columns = new String[FileInfo.META_DATA_LABELS.length];
        for (int i = 0; i < FileInfo.META_DATA_LABELS.length; ++i) {
            columns[i] = FileInfo.META_DATA_LABELS[i][0];
        }
        return columns;
    }

    public synchronized List<AndroidFileInfo> allFiles(TABLETYPE type) {
        Cursor cursor = null;
        List<AndroidFileInfo> ret;
        SQLiteDatabase db;
        try {
            db = this.getWritableDatabase();
            cursor = db.query(
                    type.getTableName(),
                    getFileInfoColumns(),
                    null, null, null, null, null);

            if (!cursor.moveToFirst())
                return null;

            ret = new LinkedList<>();
            try {
                while (!cursor.isAfterLast()) {
                    ret.add(fromDbCursor(cursor));
                    cursor.moveToNext();
                }
            } catch (SQLiteBlobTooBigException e) {
                Log.e(TAG, "error listing the fileinfo files", e);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return ret;
    }

    /**
     * Constructor from a DB cursor (using the meta-data tags from above)
     */
    private static AndroidFileInfo fromDbCursor(Cursor cursor) {
        return new AndroidFileInfo(
                new FileInfo(
                        cursor.getInt(cursor.getColumnIndex(FileInfo.ID_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(FileInfo.FILENAME_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(FileInfo.CONTENT_TYPE_LABEL)),
                        cursor.getInt(cursor
                                .getColumnIndex(FileInfo.SIZE_LABEL)),
                        cursor.getLong(cursor
                                .getColumnIndex(FileInfo.UPDATE_TIME_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(FileInfo.USERNAME_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(FileInfo.USERLABEL_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(
                                        FileInfo.DESTINATION_PATH_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(FileInfo.DOWNLOAD_URL_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(FileInfo.SHA256SUM_LABEL)),
                        cursor.getString(cursor
                                .getColumnIndex(FileInfo.FILE_METADATA))));
    }

    public AndroidFileInfo getFileInfoFromFilename(File file, TABLETYPE type) {
        return getFileInfoFromFilename(file.getName(), file.getParent(), type);
    }

    public synchronized AndroidFileInfo getFileInfoFromFilename(String filename,
            String destPath, TABLETYPE type) {
        // make query
        SQLiteDatabase db;
        Cursor cursor = null;
        try {
            db = this.getWritableDatabase();
            cursor = db.query(
                    type.getTableName(), // -> DB table
                    getFileInfoColumns(), // -> select
                    FileInfo.FILENAME_LABEL + "=? AND "
                            + FileInfo.DESTINATION_PATH_LABEL + "=?",
                    // -> where clause (in prep stmt form)
                    new String[] {
                            filename, destPath
                    },
                    // -> replace ?'s in where clause
                    null, null, null); // -> groupBy, having, orderBy

            // advance cursor
            if (!cursor.moveToFirst())
                return null;

            return fromDbCursor(cursor);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public synchronized AndroidFileInfo getFileInfoFromUserLabel(
            String userLabel,
            TABLETYPE type) {
        // make query
        Cursor cursor = null;

        SQLiteDatabase db;
        try {
            db = this.getWritableDatabase();
            cursor = db.query(
                    type.getTableName(), // -> DB table
                    getFileInfoColumns(), // -> select
                    FileInfo.USERLABEL_LABEL + "=?",
                    // -> where clause (in prep stmt form)
                    new String[] {
                            userLabel
                    },
                    // -> replace ?'s in where clause
                    null, null, null); // -> groupBy, having, orderBy

            // advance cursor
            if (!cursor.moveToFirst())
                return null;

            return fromDbCursor(cursor);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    /**
     * Search for a file with the specified lable and file checksum. Use SHA256 if provided,
     * otherwise use MD5
     * 
     * @param userLabel
     * @param sha256
     * @param type
     * @return
     */
    public synchronized AndroidFileInfo getFileInfoFromUserLabelHash(
            String userLabel,
            String sha256, TABLETYPE type) {

        // make query
        Cursor cursor = null;
        SQLiteDatabase db;
        try {
            db = this.getWritableDatabase();
            if (!FileSystemUtils.isEmpty(sha256)) {
                cursor = db.query(
                        type.getTableName(), // -> DB table
                        getFileInfoColumns(), // -> select
                        FileInfo.USERLABEL_LABEL + "=? AND "
                                + FileInfo.SHA256SUM_LABEL + "=?",
                        // -> where clause (in prep stmt form)
                        new String[] {
                                userLabel, sha256
                        },
                        // -> replace ?'s in where clause
                        null, null, null); // -> groupBy, having, orderBy
            }

            if (cursor == null) {
                Log.w(TAG, "SHA256 must be provided");
                return null;
            }

            // advance cursor
            if (!cursor.moveToFirst()) {
                Log.d(TAG, "Found no files matching: " + userLabel + ", "
                        + sha256);
                return null;
            }

            return fromDbCursor(cursor);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public synchronized int update(FileInfo file, TABLETYPE type) {
        ContentValues updateValues = new ContentValues();
        for (String col : getFileInfoColumns()) {
            if (!FileInfo.ID_LABEL.equals(col)) { // ignore ID
                if (file.getFromMetaDataLabel(col) != null)
                    updateValues.put(col,
                            file.getFromMetaDataLabel(col).toString());
            }
        }
        SQLiteDatabase db = this.getWritableDatabase();
        final Object filenameLabel = file
                .getFromMetaDataLabel(FileInfo.FILENAME_LABEL);
        final Object destinationPath = file
                .getFromMetaDataLabel(FileInfo.DESTINATION_PATH_LABEL);
        if (filenameLabel != null && destinationPath != null) {
            return db.update(
                    type.getTableName(),
                    updateValues,
                    FileInfo.FILENAME_LABEL + "=? AND "
                            + FileInfo.DESTINATION_PATH_LABEL + "=?",
                    new String[] {
                            filenameLabel.toString(),
                            destinationPath.toString()
                    });
        } else {
            return 0;
        }

    }

    public synchronized boolean insert(FileInfo file, TABLETYPE type) {
        ContentValues insertValues = new ContentValues();
        for (String col : getFileInfoColumns()) {
            if (!FileInfo.ID_LABEL.equals(col)) { // auto-generate ID
                if (file.getFromMetaDataLabel(col) != null)
                    insertValues.put(col,
                            file.getFromMetaDataLabel(col).toString());
            }
        }
        SQLiteDatabase db = this.getWritableDatabase();
        return db.insert(type.getTableName(), null,
                insertValues) != -1;
    }

    public boolean insertOrReplace(FileInfo file, TABLETYPE type) {
        // Log.d(TAG, "before insert/replace... all files: " + allFiles());
        return update(file, type) >= 1 || insert(file, type);
    }

    // Note if file has already been deleted from filesystem use this version to locate the FileInfo
    // as other FileInfo ctor attempts to load the file
    public boolean delete(File file, TABLETYPE type) {
        return file != null
                && delete(
                        getFileInfoFromFilename(file.getName(),
                                file.getParent(), type),
                        type);

    }

    public synchronized boolean delete(FileInfo fi, TABLETYPE type) {
        if (fi == null)
            return false;

        SQLiteDatabase db = this.getWritableDatabase();
        int numRowsUpdated = db.delete(
                type.getTableName(),
                FileInfo.ID_LABEL + "=" + fi.id(), null);
        return numRowsUpdated > 0;
    }

    public void purge() {
        purge(TABLETYPE.TRANSFER);
        purge(TABLETYPE.SAVED);
    }

    /**
     * Remove any entries which no longer exist on file system
     */
    public synchronized void purge(TABLETYPE type) {
        try {
            Log.d(TAG, "Purging table: " + type.getTableName());
            List<AndroidFileInfo> files = this.allFiles(type);
            if (files == null || files.size() < 1)
                return;

            for (FileInfo file : files) {
                if (!IOProviderFactory.exists(file.file())) {
                    this.delete(file, type);
                    Log.d(TAG, "Purged stale file: " + file.fileName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while purging TABLE " + type.getTableName(),
                    e);
        }
    }

    /******************
     * FileTransferLog support
     *************************/

    private static String[] getLogColumns() {
        String[] columns = new String[FileTransferLog.META_DATA_LABELS.length];
        for (int i = 0; i < FileTransferLog.META_DATA_LABELS.length; ++i) {
            columns[i] = FileTransferLog.META_DATA_LABELS[i][0];
        }
        return columns;
    }

    public synchronized boolean insertLog(FileTransferLog log) {
        ContentValues insertValues = new ContentValues();
        for (String col : getLogColumns()) {
            if (!FileTransferLog.ID_LABEL.equals(col)) { // auto-generate ID
                if (log.getFromMetaDataLabel(col) != null)
                    insertValues.put(col, log.getFromMetaDataLabel(col)
                            .toString());
            }
        }

        SQLiteDatabase db = this.getWritableDatabase();
        if (db.insert(LOG_TABLE_NAME, null, insertValues) != -1) {
            onEvent(log, true);
            return true;
        }
        return false;
    }

    public synchronized List<FileTransferLog> allLogs() {
        Cursor cursor = null;
        SQLiteDatabase db;
        List<FileTransferLog> ret;
        try {
            db = this.getReadableDatabase();
            cursor = db.query(
                    LOG_TABLE_NAME,
                    getLogColumns(),
                    null, null, null, null, null);

            if (!cursor.moveToFirst())
                return null;

            ret = new LinkedList<>();
            while (!cursor.isAfterLast()) {
                ret.add(logFromDbCursor(cursor));
                cursor.moveToNext();
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return ret;
    }

    /**
     * Constructor from a DB cursor (using the meta-data tags from above)
     */
    private static FileTransferLog logFromDbCursor(Cursor cursor) {
        return new FileTransferLog(
                cursor.getInt(cursor.getColumnIndex(FileTransferLog.ID_LABEL)),
                FileTransferLog.TYPE.valueOf(cursor.getString(cursor
                        .getColumnIndex(FileTransferLog.TYPE_LABEL))),
                cursor.getString(cursor
                        .getColumnIndex(FileTransferLog.NAME_LABEL)),
                cursor.getString(cursor
                        .getColumnIndex(FileTransferLog.DESCRIPTION_LABEL)),
                cursor.getLong(cursor
                        .getColumnIndex(FileTransferLog.SIZE_LABEL)),
                cursor.getLong(cursor
                        .getColumnIndex(FileTransferLog.TIME_LABEL)));
    }

    /**
     * Drop all files by re-creating database table
     */
    public synchronized void truncateLogs() {
        // Drop
        SQLiteDatabase db;
        try {
            db = this.getWritableDatabase();
            db.execSQL("DROP TABLE " + LOG_TABLE_NAME);
            Log.d(TAG, "dropped table: " + LOG_TABLE_NAME);
        } catch (Exception e) {
            Log.e(TAG, "Exception while dropping TABLE " + LOG_TABLE_NAME, e);
        }

        // Re-wrap
        try {
            db = this.getWritableDatabase();
            db.execSQL(LOG_TABLE_CREATE_STR);
            Log.d(TAG, "created table: " + LOG_TABLE_NAME + " with SQL: "
                    + LOG_TABLE_CREATE_STR);
        } catch (Exception e) {
            Log.e(TAG, "Exception while creating TABLE " + LOG_TABLE_NAME
                    + " with SQL: "
                    + LOG_TABLE_CREATE_STR, e);
        }
        onEvent(null, false);
    }

    public void addFileTransferListener(FileTransferLog.Listener l) {
        synchronized (_ftListeners) {
            _ftListeners.add(l);
        }
    }

    public void removeFileTransferListener(FileTransferLog.Listener l) {
        synchronized (_ftListeners) {
            _ftListeners.remove(l);
        }
    }

    private void onEvent(FileTransferLog log, boolean added) {
        List<FileTransferLog.Listener> listeners;
        synchronized (_ftListeners) {
            listeners = new ArrayList<>(_ftListeners);
        }
        for (FileTransferLog.Listener l : listeners)
            l.onEvent(log, added);
    }
}
