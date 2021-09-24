
package com.atakmap.database;

public interface RowIterator extends AutoCloseable {
    public boolean moveToNext();

    public void close();

    public boolean isClosed();
}
