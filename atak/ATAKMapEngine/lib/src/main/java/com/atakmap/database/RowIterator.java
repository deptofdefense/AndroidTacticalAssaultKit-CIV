
package com.atakmap.database;

public interface RowIterator {
    public boolean moveToNext();

    public void close();

    public boolean isClosed();
}
