package android.database;

public abstract class AbstractCursor implements Cursor {
    public String getColumnName(int idx) {
        return getColumnNames()[idx];
    }
    public int getColumnIndex(String name) {
        final String[] names = getColumnNames();
        for(int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(name))
                return i;
        }
        return -1;
    }
}
