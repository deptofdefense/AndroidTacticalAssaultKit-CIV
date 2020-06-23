
package com.atakmap.android.cotdelete;

import com.atakmap.android.importexport.AbstractCotEventMarshal;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * TAK capability to process the t-x-d-d CoT event to delete a map item
 * within the system.
 */
public class CotDeleteEventMarshal extends AbstractCotEventMarshal {

    static final String CONTENT_TYPE = "Delete Task";

    public final static String COT_TASK_DISPLAY_DELETE_TYPE = "t-x-d-d";

    public CotDeleteEventMarshal() {
        super(CONTENT_TYPE);
    }

    @Override
    protected boolean accept(CotEvent event) {
        return event.getType().startsWith(COT_TASK_DISPLAY_DELETE_TYPE);

    }

    @Override
    public int getPriorityLevel() {
        return 2;
    }
}
