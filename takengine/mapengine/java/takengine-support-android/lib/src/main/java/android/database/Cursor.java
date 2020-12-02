package android.database;

public interface Cursor {
    public final static int FIELD_TYPE_BLOB = 0;
    public final static int FIELD_TYPE_NULL = 1;
    public final static int FIELD_TYPE_STRING = 2;
    public final static int FIELD_TYPE_INTEGER = 3;
    public final static int FIELD_TYPE_FLOAT = 4;

    boolean moveToNext();
    long getLong(int col);
    String getString(int col);
    double getDouble(int col);
    int getInt(int col);
    int getType(int col);
    int getColumnIndex(String name);
    byte[] getBlob(int col);
    boolean isNull(int col);
    int getColumnCount();
    String[] getColumnNames();
    String getColumnName(int col);
    int getCount();
    boolean isClosed();
    void close();
}
