
package com.atakmap.database;

public class CursorWrapper extends RowIteratorWrapper implements CursorIface {
    protected CursorIface filter;

    public CursorWrapper(CursorIface filter) {
        super(filter);

        this.filter = filter;
    }

    @Override
    public int getColumnIndex(String columnName) {
        return this.filter.getColumnIndex(columnName);
    }

    @Override
    public String getColumnName(int columnIndex) {
        return this.filter.getColumnName(columnIndex);
    }

    @Override
    public String[] getColumnNames() {
        return this.filter.getColumnNames();
    }

    @Override
    public int getColumnCount() {
        return this.filter.getColumnCount();
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        return this.filter.getBlob(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
        return this.filter.getString(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        return this.filter.getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        return this.filter.getLong(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) {
        return this.filter.getDouble(columnIndex);
    }

    @Override
    public int getType(int columnIndex) {
        return this.filter.getType(columnIndex);
    }

    @Override
    public boolean isNull(int columnIndex) {
        return this.filter.isNull(columnIndex);
    }
}
