
package com.atakmap.database;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
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

    /**
     * Returns the zero-based index for the given column name, or -1 if the column doesn't exist.
     * @param columnName the column name
     * @return the index
     */
    public int getColumnIndex(String columnName);

    /**
     * Returns the column name at the given zero-based column index.
     * @param columnIndex the index
     * @return the column name
     */
    public String getColumnName(int columnIndex);

    /**
     * Returns a string array holding the names of all of the columns in the result set in the order
     * in which they were listed in the result.
     * @return the array of names
     */
    public String[] getColumnNames();

    /**
     * Return total number of columns
     * @return the number
     */
    public int getColumnCount();

    /**
     * Returns the value of the requested column as a byte array.
     * @param columnIndex the zero-based index of the target column. Value is 0 or greater
     * @return the value of that column as a byte array.
     */
    public byte[] getBlob(int columnIndex);

    /**
     * Returns the value of the requested column as a String.
     * @param columnIndex the zero-based index of the target column. Value is 0 or greater
     * @return the value of that column as a String.
     */
    public String getString(int columnIndex);

    /**
     * Returns the value of the requested column as an int.
     * @param columnIndex the zero-based index of the target column. Value is 0 or greater
     * @return the value of that column as an int.
     */
    public int getInt(int columnIndex);

    /**
     * Returns the value of the requested column as an long.
     * @param columnIndex the zero-based index of the target column. Value is 0 or greater
     * @return the value of that column as an long.
     */
    public long getLong(int columnIndex);

    /**
     * Returns the value of the requested column as an double.
     * @param columnIndex the zero-based index of the target column. Value is 0 or greater
     * @return the value of that column as an double.
     */
    public double getDouble(int columnIndex);

    /**
     * Returns data type of the given column's value. The preferred type of the column is returned but
     * the data may be converted to other types as documented in the get-type methods such as getInt(int),
     * getFloat(int) etc.
     * @param columnIndex the zero-based index of the target column. Value is 0 or greater
     * @return column value type Value is FIELD_TYPE_NULL, FIELD_TYPE_INTEGER, FIELD_TYPE_FLOAT,
     * FIELD_TYPE_STRING, or FIELD_TYPE_BLOB
     */
    public int getType(int columnIndex);

    /**
     * Returns true if the value in the indicated column is null.
     * @param columnIndex  the zero-based index of the target column. Value is 0 or greater
     * @return whether the column value is null.
     */
    public boolean isNull(int columnIndex);
}
