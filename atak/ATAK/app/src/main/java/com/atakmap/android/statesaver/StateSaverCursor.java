
package com.atakmap.android.statesaver;

import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.database.CursorIface;
import com.atakmap.database.RowIterator;

/**
 * Database cursor specifically intended for the {@link StateSaver}
 */
public class StateSaverCursor implements RowIterator {

    private final CursorIface _cursor;

    StateSaverCursor(CursorIface cursor) {
        _cursor = cursor;
    }

    @Override
    public boolean moveToNext() {
        return _cursor.moveToNext();
    }

    @Override
    public void close() {
        _cursor.close();
    }

    @Override
    public boolean isClosed() {
        return _cursor.isClosed();
    }

    /**
     * Get the index of this entry in the statesaver table
     * @return Event ID
     */
    public long getID() {
        return getLong(StateSaver.COLUMN_ID);
    }

    /**
     * Get the UID of this CoT event
     * @return CoT event UID
     */
    public String getUID() {
        return getString(StateSaver.COLUMN_UID);
    }

    /**
     * Get the CoT type for this row
     * @return CoT type
     */
    public String getType() {
        return getString(StateSaver.COLUMN_TYPE);
    }

    /**
     * Check if the item tied to this CoT event is visible
     * @return True if visible
     */
    public boolean isVisible() {
        return getLong(StateSaver.COLUMN_VISIBLE) == 1;
    }

    /**
     * Get the CoT event for this row
     * @return CoT event
     */
    public CotEvent getEvent() {
        String xml = getString(StateSaver.COLUMN_EVENT);
        if (FileSystemUtils.isEmpty(xml))
            return null;
        return CotEvent.parse(xml);
    }

    /**
     * Get the last time this CoT message was persisted to the statesaver
     * @return Last update time in milliseconds (UNIX)
     */
    public long getLastUpdateTime() {
        return getLong(StateSaver.COLUMN_LAST_UPDATE);
    }

    private String getString(String colName) {
        int colIdx = _cursor.getColumnIndex(colName);
        return colIdx > -1 ? _cursor.getString(colIdx) : null;
    }

    private long getLong(String colName) {
        int colIdx = _cursor.getColumnIndex(colName);
        return colIdx > -1 ? _cursor.getLong(colIdx) : -1;
    }
}
