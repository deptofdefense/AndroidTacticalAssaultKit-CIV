
package com.atakmap.database.android;

import java.io.File;
import java.util.Iterator;

import com.atakmap.coremap.locale.LocaleUtil;

import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;

public final class AndroidDatabaseAdapter implements DatabaseIface {

    private final SQLiteDatabase impl;

    public AndroidDatabaseAdapter(SQLiteDatabase impl) {
        this.impl = impl;
    }

    @Override
    public boolean isReadOnly() {
        return this.impl.isReadOnly();
    }

    @Override
    public void execute(String sql, String[] args) {
        if (args == null)
            this.impl.execSQL(sql);
        else
            this.impl.execSQL(sql, args);
    }

    @Override
    public CursorIface query(String sql, String[] args) {
        return new AndroidCursorAdapter(this.impl.rawQuery(sql, args));
    }

    @Override
    public StatementIface compileStatement(String sql) {
        int type = AndroidStatementAdapter.STMT_TYPE_OTHER;

        String scratch = sql.trim().toUpperCase(LocaleUtil.getCurrent());
        if (scratch.startsWith("SELECT"))
            throw new IllegalArgumentException();

        if (scratch.startsWith("INSERT"))
            type = AndroidStatementAdapter.STMT_TYPE_INSERT;
        else if (scratch.startsWith("UPDATE"))
            type = AndroidStatementAdapter.STMT_TYPE_UPDATE;
        else if (scratch.startsWith("DELETE"))
            type = AndroidStatementAdapter.STMT_TYPE_DELETE;

        return new AndroidStatementAdapter(this.impl.compileStatement(sql),
                type);
    }

    @Override
    public QueryIface compileQuery(String sql) {
        String scratch = sql.trim().toUpperCase(LocaleUtil.getCurrent());
        if (scratch.startsWith("SELECT"))
            return new AndroidQueryStatement(this.impl, sql);
        else
            throw new IllegalArgumentException();
    }

    @Override
    public void close() {
        this.impl.close();
    }

    @Override
    public void beginTransaction() {
        this.impl.beginTransaction();
    }

    @Override
    public void setTransactionSuccessful() {
        this.impl.setTransactionSuccessful();
    }

    @Override
    public void endTransaction() {
        this.impl.endTransaction();
    }

    @Override
    public int getVersion() {
        return this.impl.getVersion();
    }

    @Override
    public void setVersion(int version) {
        this.impl.setVersion(version);
    }

    @Override
    public boolean inTransaction() {
        return this.impl.inTransaction();
    }

    /**************************************************************************/

    public static AndroidDatabaseAdapter openOrCreateDatabase(String path) {
        return openDatabase(path, SQLiteDatabase.OPEN_READWRITE
                | SQLiteDatabase.CREATE_IF_NECESSARY);
    }

    public static AndroidDatabaseAdapter openDatabase(String path, int flags) {
        return new AndroidDatabaseAdapter(
                SQLiteDatabase.openDatabase(
                        path,
                        null,
                        flags | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                        new DatabaseErrorHandler() {
                            @Override
                            public void onCorruption(SQLiteDatabase dbObj) {
                                dbObj.close();
                            }
                        }));
    }

    public static int update(DatabaseIface db, String table,
            ContentValues contentValues,
            String where, String[] selectionArgs) {
        if (db instanceof AndroidDatabaseAdapter) {
            return ((AndroidDatabaseAdapter) db).impl.update(table,
                    contentValues, where,
                    selectionArgs);
        }

        if (contentValues == null || contentValues.size() < 1)
            return 0;

        Object[] args = new String[contentValues.size()
                + (selectionArgs != null ? selectionArgs.length : 0)];

        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(table);
        sql.append(" SET ");

        int idx = 0;
        Iterator<String> iter = contentValues.keySet().iterator();
        String key;
        Object value;
        while (iter.hasNext()) {
            key = iter.next();
            sql.append(key + " = ?");
            if (iter.hasNext())
                sql.append(", ");
            args[idx++] = contentValues.get(key);
        }

        if (where != null && where.length() > 0)
            sql.append(" WHERE " + where);

        if (selectionArgs != null)
            for (int i = 0; i < selectionArgs.length; i++)
                args[idx++] = selectionArgs[i];

        StatementIface stmt = null;
        try {
            stmt = db.compileStatement(sql.toString());

            for (int i = 0; i < args.length; i++) {
                value = args[i];
                if (value instanceof String)
                    stmt.bind(i + 1, (String) value);
                else if (value instanceof Integer)
                    stmt.bind(i + 1, ((Integer) value).intValue());
                else if (value instanceof Long)
                    stmt.bind(i + 1, ((Long) value).longValue());
                else if (value instanceof Double)
                    stmt.bind(i + 1, ((Double) value).doubleValue());
                else if (value instanceof byte[])
                    stmt.bind(i + 1, (byte[]) value);
                else if (value == null)
                    stmt.bindNull(i + 1);
                else if (value instanceof Float)
                    stmt.bind(i + 1, (double) ((Float) value).floatValue());
                else if (value instanceof Byte)
                    stmt.bind(i + 1, ((Byte) value).byteValue());
                else if (value instanceof Short)
                    stmt.bind(i + 1, ((Short) value).shortValue());
                else
                    stmt.bind(i + 1, value.toString()); // XXX - ???
            }

            stmt.execute();
        } finally {
            if (stmt != null)
                stmt.close();
        }

        return Databases.lastChangeCount(db);
    }

    public static int delete(DatabaseIface db, String table, String where,
            String[] selectionArgs) {
        if (db instanceof AndroidDatabaseAdapter) {
            return ((AndroidDatabaseAdapter) db).impl.delete(table, where,
                    selectionArgs);
        }

        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(table);

        if (where != null && where.length() > 0)
            sql.append(" WHERE " + where);

        db.execute(sql.toString(), selectionArgs);
        return Databases.lastChangeCount(db);
    }

    public static long insert(DatabaseIface db, String table,
            String nullColumnHack,
            ContentValues values) {
        if (db instanceof AndroidDatabaseAdapter) {
            return ((AndroidDatabaseAdapter) db).impl.insert(table,
                    nullColumnHack, values);
        }

        if (values == null || values.size() < 1)
            return 0L;

        Object[] args = new Object[values.size()];

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(table);
        sql.append(" (");

        int idx = 0;
        Iterator<String> iter = values.keySet().iterator();
        String key;
        Object value;
        while (iter.hasNext()) {
            key = iter.next();
            sql.append(key);
            if (iter.hasNext())
                sql.append(", ");
            args[idx++] = values.get(key);
        }

        sql.append(") VALUES (");
        for (int i = 0; i < (args.length - 1); i++)
            sql.append("?, ");
        sql.append("?)");

        StatementIface stmt = null;
        try {
            stmt = db.compileStatement(sql.toString());

            for (int i = 0; i < args.length; i++) {
                value = args[i];
                if (value instanceof String)
                    stmt.bind(i + 1, (String) value);
                else if (value instanceof Integer)
                    stmt.bind(i + 1, ((Integer) value).intValue());
                else if (value instanceof Long)
                    stmt.bind(i + 1, ((Long) value).longValue());
                else if (value instanceof Double)
                    stmt.bind(i + 1, ((Double) value).doubleValue());
                else if (value instanceof byte[])
                    stmt.bind(i + 1, (byte[]) value);
                else if (value == null)
                    stmt.bindNull(i + 1);
                else if (value instanceof Float)
                    stmt.bind(i + 1, (double) ((Float) value).floatValue());
                else if (value instanceof Byte)
                    stmt.bind(i + 1, ((Byte) value).byteValue());
                else if (value instanceof Short)
                    stmt.bind(i + 1, ((Short) value).shortValue());
                else
                    stmt.bind(i + 1, value.toString()); // XXX - ???
            }

            stmt.execute();
        } finally {
            if (stmt != null)
                stmt.close();
        }

        return Databases.lastInsertRowId(db);
    }

    public static Cursor query(SQLiteQueryBuilder queryBuilder,
            DatabaseIface db,
            String[] projection, String selection, String[] selectionArgs,
            String groupBy,
            String having, String sortOrder) {
        return query(queryBuilder, db, projection, selection, selectionArgs,
                groupBy, having,
                sortOrder, null);
    }

    public static Cursor query(SQLiteQueryBuilder queryBuilder,
            DatabaseIface db, String[] projection,
            String selection, String[] selectionArgs, String groupBy,
            String having,
            String sortOrder, String limit) {
        if (db instanceof AndroidDatabaseAdapter) {
            return queryBuilder.query(((AndroidDatabaseAdapter) db).impl,
                    projection, selection,
                    selectionArgs, groupBy, having, sortOrder, limit);
        }

        final String sql = queryBuilder.buildQuery(projection, selection,
                groupBy, having,
                sortOrder, limit);
        return new CursorIfaceAdapter(db.query(sql, selectionArgs));
    }

    public static Cursor query(DatabaseIface db, String[] projection,
            String selection, String[] selectionArgs, String groupBy,
            String having,
            String sortOrder, String limit) {

        return query(new SQLiteQueryBuilder(),
                db,
                projection,
                selection,
                selectionArgs,
                groupBy,
                having,
                sortOrder,
                limit);
    }

    public static Cursor query(DatabaseIface db,
            String[] projection, String selection, String[] selectionArgs,
            String groupBy,
            String having, String sortOrder) {
        return query(new SQLiteQueryBuilder(), db, projection, selection,
                selectionArgs, groupBy, having,
                sortOrder, null);
    }

    public static CursorIface query(DatabaseIface db, String table,
            String[] columns, String where, String[] selectionArgs,
            String having, String orderBy, String limit) {
        return query(db, table, columns, where, selectionArgs, null, having,
                orderBy, limit);
    }

    public static CursorIface query(DatabaseIface db, String table,
            String[] columns, String where, String[] selectionArgs,
            String groupBy, String having, String orderBy, String limit) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        final String sql = queryBuilder.buildQueryString(false, table, columns,
                where, groupBy, having, orderBy, limit);
        return db.query(sql, selectionArgs);
    }
}
