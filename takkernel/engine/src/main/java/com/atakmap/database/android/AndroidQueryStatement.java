
package com.atakmap.database.android;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

import com.atakmap.database.CursorIface;
import com.atakmap.database.QueryIface;

final class AndroidQueryStatement
        implements QueryIface, SQLiteDatabase.CursorFactory {

    final SQLiteDatabase database;
    final String sql;
    BindArgument[] args;
    boolean closed;

    CursorIface impl;

    AndroidQueryStatement(SQLiteDatabase database, String sql) {
        this.database = database;
        this.sql = sql;

        this.closed = false;

        int argCount = 0;
        int idx = -1;

        idx = this.sql.indexOf('?', idx);
        while (idx >= 0) {
            argCount++;
            idx = this.sql.indexOf('?', idx + 1);
        }

        this.args = new BindArgument[argCount];
        for (int i = 0; i < this.args.length; i++)
            this.args[i] = new BindArgument();

        this.impl = null;
    }

    @Override
    public void reset() {
        if (this.impl != null) {
            this.impl.close();
            this.impl = null;
        }
    }

    @Override
    public void bind(int idx, byte[] value) {
        this.args[idx - 1].set(value);
    }

    @Override
    public void bind(int idx, int value) {
        this.args[idx - 1].set(value);
    }

    @Override
    public void bind(int idx, long value) {
        this.args[idx - 1].set(value);
    }

    @Override
    public void bind(int idx, double value) {
        this.args[idx - 1].set(value);
    }

    @Override
    public void bind(int idx, String value) {
        this.args[idx - 1].set(value);
    }

    @Override
    public void bindNull(int idx) {
        this.args[idx - 1].clear();
    }

    @Override
    public void clearBindings() {
        for (int i = 0; i < this.args.length; i++)
            this.args[i].clear();
    }

    @Override
    public void close() {
        this.closed = true;
        this.reset();
        this.clearBindings();
    }

    /*************************************************************************/

    @Override
    public Cursor newCursor(SQLiteDatabase arg0, SQLiteCursorDriver arg1,
            String arg2, SQLiteQuery arg3) {
        for (int i = 0; i < this.args.length; i++)
            bindArg(arg3, i + 1, this.args[i]);
        return new SQLiteCursor(arg1, arg2, arg3);
    }

    /**************************************************************************/

    private static void bindArg(SQLiteQuery query, int idx, BindArgument arg) {
        switch (arg.getType()) {
            case CursorIface.FIELD_TYPE_BLOB:
                query.bindBlob(idx, (byte[]) arg.getValue());
                break;
            case CursorIface.FIELD_TYPE_FLOAT:
                query.bindDouble(idx, ((Number) arg.getValue()).doubleValue());
                break;
            case CursorIface.FIELD_TYPE_INTEGER:
                query.bindLong(idx, ((Number) arg.getValue()).longValue());
                break;
            case CursorIface.FIELD_TYPE_NULL:
                query.bindNull(idx);
                break;
            case CursorIface.FIELD_TYPE_STRING:
                query.bindString(idx, (String) arg.getValue());
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean moveToNext() {
        if (this.impl == null)
            this.impl = new AndroidCursorAdapter(this.database
                    .rawQueryWithFactory(this, this.sql, null, null));
        return this.impl.moveToNext();
    }

    @Override
    public int getColumnIndex(String columnName) {
        return this.impl.getColumnIndex(columnName);
    }

    @Override
    public String getColumnName(int columnIndex) {
        return this.impl.getColumnName(columnIndex);
    }

    @Override
    public String[] getColumnNames() {
        return this.impl.getColumnNames();
    }

    @Override
    public int getColumnCount() {
        return this.impl.getColumnCount();
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        return this.impl.getBlob(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
        return this.impl.getString(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        return this.impl.getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        return this.impl.getLong(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) {
        return this.impl.getDouble(columnIndex);
    }

    @Override
    public int getType(int columnIndex) {
        return this.impl.getType(columnIndex);
    }

    @Override
    public boolean isNull(int columnIndex) {
        return this.impl.isNull(columnIndex);
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }
}
