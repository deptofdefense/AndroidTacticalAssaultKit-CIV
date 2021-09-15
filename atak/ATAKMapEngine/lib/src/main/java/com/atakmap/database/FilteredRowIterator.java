
package com.atakmap.database;

public abstract class FilteredRowIterator extends RowIteratorWrapper {
    public FilteredRowIterator(RowIterator cursor) {
        super(cursor);
    }

    protected abstract boolean accept();

    protected final boolean moveToNextImpl() {
        return super.moveToNext();
    }

    /**************************************************************************/

    @Override
    public boolean moveToNext() {
        while (this.moveToNextImpl()) {
            if (this.accept())
                return true;
        }
        return false;
    }
}
