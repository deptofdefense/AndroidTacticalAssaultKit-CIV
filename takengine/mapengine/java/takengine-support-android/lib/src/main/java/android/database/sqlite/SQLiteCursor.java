package android.database.sqlite;

import android.database.AbstractWindowedCursor;
import android.database.Cursor;
import android.database.CursorWindow;

public final class SQLiteCursor extends AbstractWindowedCursor {
    public SQLiteCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return false;
    }

    @Override
    public void setWindow(CursorWindow window) {

    }

    @Override
    public String[] getColumnNames() {
        return new String[0];
    }

    @Override
    public int getCount() {
        return 0;
    }
}
