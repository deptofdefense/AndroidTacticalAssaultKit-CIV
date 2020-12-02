package android.database.sqlite;

import android.database.Cursor;

public final class SQLiteQueryBuilder {
    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder) {
        throw new UnsupportedOperationException();
    }
    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit) {
        throw new UnsupportedOperationException();
    }
    public String buildQuery(String[] projectionIn, String selection, String groupBy, String having, String sortOrder, String limit) {
        throw new UnsupportedOperationException();
    }
    public String buildQuery(String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit) {
        throw new UnsupportedOperationException();
    }
    public static String buildQueryString(boolean b, String tableName, String[] columns, String where, String groupBy, String having, String orderBy, String limit ){
        throw new UnsupportedOperationException();
    }
}
