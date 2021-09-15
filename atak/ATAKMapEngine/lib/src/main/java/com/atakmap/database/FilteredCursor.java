
package com.atakmap.database;

public abstract class FilteredCursor extends CursorWrapper {

    public FilteredCursor(CursorIface cursor) {
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
