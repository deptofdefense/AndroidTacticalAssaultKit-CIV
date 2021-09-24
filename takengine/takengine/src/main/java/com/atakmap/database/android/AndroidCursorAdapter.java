
package com.atakmap.database.android;

import com.atakmap.database.CursorIface;

import android.database.Cursor;

public final class AndroidCursorAdapter implements CursorIface {

    protected Cursor impl;

    public AndroidCursorAdapter(Cursor impl) {
        this.impl = impl;
    }

    public Cursor get() {
        return this.impl;
    }

    /**************************************************************************/
    // Cursor Interface

    @Override
    public boolean moveToNext() {
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
    public void close() {
        this.impl.close();
    }

    @Override
    public boolean isClosed() {
        return this.impl.isClosed();
    }

}
