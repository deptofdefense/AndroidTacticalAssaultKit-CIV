package android.database.sqlite;

import android.database.SQLException;

public class SQLiteException extends SQLException {
    public SQLiteException() {}
    public SQLiteException(String msg) {
        super(msg);
    }
}
