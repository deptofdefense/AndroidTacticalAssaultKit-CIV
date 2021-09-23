
package com.atakmap.database;

public class RowIteratorWrapper {
    protected RowIterator filter;

    public RowIteratorWrapper(RowIterator filter) {
        this.filter = filter;
    }

    public boolean moveToNext() {
        return this.filter.moveToNext();
    }

    public void close() {
        this.filter.close();
    }

    public boolean isClosed() {
        return this.filter.isClosed();
    }
}
