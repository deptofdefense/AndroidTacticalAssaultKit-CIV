
package com.atakmap.database.android;

import com.atakmap.database.CursorIface;

import android.database.AbstractWindowedCursor;
import android.database.CursorWindow;

public final class CursorIfaceAdapter extends AbstractWindowedCursor {

    private CursorIface iface;
    private int count;
    private int cursorPos;

    public CursorIfaceAdapter(CursorIface iface) {
        this.iface = iface;
        this.count = -1;
        this.cursorPos = -1;
    }

    @Override
    public String[] getColumnNames() {
        return this.iface.getColumnNames();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        // create a new local window if no window has been set
        if (mWindow == null) {
            mWindow = new CursorWindow(false);
            mWindow.setNumColumns(this.getColumnCount());
        }

        // fill the window with data we haven't seen yet
        if (newPosition >= (mWindow.getStartPosition()
                + mWindow.getNumRows())) {
            while (this.cursorPos <= newPosition) {
                if (!this.iface.moveToNext()) {
                    // we've reached the end of the result set; if 'count'
                    // hasn't been set yet, set it
                    if (this.count == -1)
                        this.count = ++this.cursorPos;
                    // we can advance to the index after the last row, but not
                    // past it
                    return (newPosition == this.count);
                }
                // advance our cursor index
                this.cursorPos++;
                // set the row data in the window
                this.fillWindowRow();
            }
        }

        return true;
    }

    @Override
    public int getCount() {
        // fill the window with the result set to obtain the count if necessary
        if (this.count == -1)
            this.onMove(0, Integer.MAX_VALUE);
        return this.count;
    }

    @Override
    public void setWindow(CursorWindow window) {
        if (this.mWindow != null) {
            // XXX - copy contents of current window into new window???
            this.mWindow.close();
        }

        this.mWindow = window;
        this.mWindow.clear();
        this.mWindow.setNumColumns(this.getColumnCount());
        if (this.mPos > 0 && this.mPos != this.count) {
            this.mWindow.setStartPosition(this.mPos);
            this.fillWindowRow();
        }
    }

    private boolean fillWindowRow() {
        if (!this.mWindow.allocRow())
            return false;

        for (int j = 0; j < this.getColumnCount(); j++) {
            switch (this.iface.getType(j)) {
                case CursorIface.FIELD_TYPE_BLOB:
                    this.mWindow.putBlob(this.iface.getBlob(j), this.cursorPos,
                            j);
                    break;
                case CursorIface.FIELD_TYPE_FLOAT:
                    this.mWindow.putDouble(this.iface.getDouble(j),
                            this.cursorPos, j);
                    break;
                case CursorIface.FIELD_TYPE_INTEGER:
                    this.mWindow.putLong(this.iface.getLong(j), this.cursorPos,
                            j);
                    break;
                case CursorIface.FIELD_TYPE_NULL:
                    this.mWindow.putNull(this.cursorPos, j);
                    break;
                case CursorIface.FIELD_TYPE_STRING:
                    this.mWindow.putString(this.iface.getString(j),
                            this.cursorPos, j);
                    break;
            }
        }

        return true;
    }
}
