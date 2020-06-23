
package com.atakmap.android.cot.detail;

import com.atakmap.android.cot.TaskCotReceiver;
import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.ArrayList;

/**
 * Used for tasking items - see {@link TaskCotReceiver}
 */
class RequestDetailHandler extends CotDetailHandler {

    RequestDetailHandler() {
        super("request");
    }

    @Override
    public boolean toCotDetail(MapItem marker, CotEvent event,
            CotDetail detail) {
        // Not exported?
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem marker, CotEvent event,
            CotDetail detail) {
        String notifyString = detail.getAttribute("notify");
        if (notifyString != null) {
            marker.setType(event.getType());
            marker.setMetaString("notify", notifyString);
            marker.setMetaString("taskType", "t-s");
            marker.setMetaDouble("alt", event.getCotPoint().getHae());

            // XXX: More comprehensive tasking
            ArrayList<String> tasks = new ArrayList<>();
            tasks.add("ISR");
            marker.setMetaStringArrayList("tasks", tasks);
            return ImportResult.SUCCESS;
        }
        return ImportResult.FAILURE;
    }
}
