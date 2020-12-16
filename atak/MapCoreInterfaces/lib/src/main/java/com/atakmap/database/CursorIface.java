
package com.atakmap.database;

public interface CursorIface extends RowIterator {
    public final static int FIELD_TYPE_BLOB = android.database.Cursor.FIELD_TYPE_BLOB;
    public final static int FIELD_TYPE_NULL = android.database.Cursor.FIELD_TYPE_NULL;
    public final static int FIELD_TYPE_STRING = android.database.Cursor.FIELD_TYPE_STRING;
    public final static int FIELD_TYPE_INTEGER = android.database.Cursor.FIELD_TYPE_INTEGER;
    public final static int FIELD_TYPE_FLOAT = android.database.Cursor.FIELD_TYPE_FLOAT;

    /**************************************************************************/

    public final static CursorIface EMPTY = new CursorIface() {
        @Override
        public int getColumnIndex(String columnName) {
            return -1;
        }

        @Override
        public String getColumnName(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public String[] getColumnNames() {
            return new String[0];
        }

        @Override
        public int getColumnCount() {
            return 0;
        }

        @Override
        public byte[] getBlob(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public String getString(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public int getInt(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public long getLong(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public double getDouble(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public int getType(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public boolean isNull(int columnIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public boolean moveToNext() {
            return false;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    };

    /**************************************************************************/

    public int getColumnIndex(String columnName);

    public String getColumnName(int columnIndex);

    public String[] getColumnNames();

    public int getColumnCount();

    public byte[] getBlob(int columnIndex);

    public String getString(int columnIndex);

    public int getInt(int columnIndex);

    public long getLong(int columnIndex);

    public double getDouble(int columnIndex);

    public int getType(int columnIndex);

    public boolean isNull(int columnIndex);
}
