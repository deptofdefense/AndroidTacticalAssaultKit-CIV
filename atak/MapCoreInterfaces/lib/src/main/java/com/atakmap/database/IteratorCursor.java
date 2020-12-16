
package com.atakmap.database;

import java.util.Iterator;

public class IteratorCursor<T> implements RowIterator {

    private T rowData;
    private Iterator<T> iter;

    public IteratorCursor(Iterator<T> iter) {
        this.iter = iter;
        this.rowData = null;
    }

    public final T getRowData() {
        return this.rowData;
    }

    /**************************************************************************/
    // Row Iterator

    @Override
    public boolean moveToNext() {
        if (!this.iter.hasNext())
            return false;
        this.rowData = this.iter.next();
        return true;
    }

    @Override
    public void close() {
        this.iter = null;
    }

    @Override
    public boolean isClosed() {
        return (this.iter == null);
    }
}
