package android.database.sqlite;

public interface SQLiteStatement {
    void bindString(int index, String value);
    void bindBlob(int index, byte[] value);
    void bindNull(int col);
    void bindDouble(int col, double v);
    void bindLong(int col, long v);
    void execute();
    void executeInsert();
    void executeUpdateDelete();
    void clearBindings();
    void close();
}
