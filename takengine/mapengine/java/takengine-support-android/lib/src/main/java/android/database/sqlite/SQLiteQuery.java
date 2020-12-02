package android.database.sqlite;

public interface SQLiteQuery {
    void bindBlob(int col, byte[] v);
    void bindDouble(int col, double v);
    void bindNull(int col);
    void bindLong(int col, long v);
    void bindString(int col, String v);
}
