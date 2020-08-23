
package com.atakmap.android.cot.detail;

import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.track.BreadcrumbReceiver;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Responsible for handling the <crumbs enabled="true"/> tag.
 */
class TracksDetailHandler extends CotDetailHandler {

    TracksDetailHandler() {
        super(new HashSet<>(
                Arrays.asList("__bread_crumbs", "__track_logging")));
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String name = detail.getElementName();
        String enabled = detail.getAttribute("enabled");
        boolean shouldBeEnabled = enabled != null && (enabled.equals("1")
                || Boolean.parseBoolean(enabled));

        if (name.equals("__track_logging")) {
            boolean currentState = item.getMetaBoolean("trackLoggingEnabled",
                    false);
            if (currentState != shouldBeEnabled) {
                // need to toggle
                item.setMetaBoolean("trackLoggingEnabled", shouldBeEnabled);
                Intent toggleIntent = new Intent();
                toggleIntent.setAction("com.atakmap.android.bread.LOG_TRACKS");
                toggleIntent.putExtra("enable_log_tracks", shouldBeEnabled);
                AtakBroadcast.getInstance().sendBroadcast(toggleIntent);
            }
            return ImportResult.SUCCESS;
        } else if (name.equals("__bread_crumbs")) {
            boolean currentState = item.getMetaBoolean("tracks_on", false);
            if (shouldBeEnabled != currentState) {
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        BreadcrumbReceiver.TOGGLE_BREAD)
                                .putExtra("uid", event.getUID()));
            }
            return ImportResult.SUCCESS;
        }
        return ImportResult.IGNORE;
    }
}
