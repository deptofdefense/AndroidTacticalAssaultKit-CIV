package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;

public abstract class SQLiteDatabase {
     public final static int CREATE_IF_NECESSARY = 0x01;
     public final static int OPEN_READONLY = 0x02;
     public final static int OPEN_READWRITE = 0x04;
     public final static int NO_LOCALIZED_COLLATORS = 0x08;

     public static interface CursorFactory {
          Cursor newCursor(SQLiteDatabase arg0, SQLiteCursorDriver arg1, String arg2, SQLiteQuery arg3);
     }

     public abstract Cursor rawQuery(String sql, String[] args);
     public abstract Cursor rawQueryWithFactory(CursorFactory factory, String sql, String[] args, String editTable);
     public abstract Cursor query(String table, String[] cols, String selection, String[] selectionArgs, String groupBy, String having, String orderBy);
     public abstract void execSQL(String sql);
     public abstract void execSQL(String sql, String[] args);
     public abstract long insert(String table, String nullColumnHack, ContentValues v);
     public abstract int update(String table, ContentValues contentValues, String where, String[] selectionArgs);
     public abstract int delete(String table, String where, String[] args);
     public abstract String getPath();
     public abstract int getVersion();
     public abstract void setVersion(int v);
     public abstract boolean isReadOnly();
     public abstract void close();
     public abstract SQLiteStatement compileStatement(String sql);
     public abstract void beginTransaction();
     public abstract void endTransaction();
     public abstract boolean inTransaction();
     public abstract void setTransactionSuccessful();

     public static SQLiteDatabase openDatabase(String path, SQLiteDatabase.CursorFactory factory, int flags) {
          return openDatabase(path, factory, flags);
     }

     public static SQLiteDatabase openDatabase(String path, SQLiteDatabase.CursorFactory factory, int flags, DatabaseErrorHandler errorHandler) {
          throw new UnsupportedOperationException();
     }
}
