package android.database.sqlite;

public interface SQLiteOpenHelper {
    SQLiteDatabase getReadableDatabase();
    void close();
}
