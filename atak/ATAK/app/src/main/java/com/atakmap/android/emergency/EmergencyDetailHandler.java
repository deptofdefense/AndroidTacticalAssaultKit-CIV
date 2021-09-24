
package com.atakmap.android.emergency;

import android.content.Intent;
import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

/**
 * Handles emergency messages with the following structure:
 *
 * <emergency type="911"/>
 *
 * or
 *
 * <emergency cancel="true"/>
 *
 * ...by dropping a /slightly obnoxious/ marker or removing it from the map.
 *
 */
public class EmergencyDetailHandler extends CotDetailHandler {

    public static final String TAG = "EmergencyDetailHandler";

    static final String EMERGENCY_TYPE_META_FIELD = "emergency";
    public static final String EMERGENCY_TYPE_PREFIX = "b-a";

    public EmergencyDetailHandler() {
        super("emergency");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (item.hasMetaValue(EMERGENCY_TYPE_META_FIELD)) {
            CotDetail emergency = new CotDetail("emergency");
            emergency.setAttribute("type",
                    item.getMetaString(EMERGENCY_TYPE_META_FIELD, "911"));
            detail.addChild(emergency);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        if (!event.getType().startsWith(EMERGENCY_TYPE_PREFIX))
            return ImportResult.IGNORE;

        String type = detail.getAttribute("type");
        boolean cancel = Boolean.parseBoolean(detail.getAttribute("cancel"));
        if (cancel && item != null) {
            Log.d(TAG, "Removing a cancelled emergency marker.");
            item.setVisible(false);
            item.removeFromGroup();

            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    EmergencyAlertReceiver.CANCEL_EVENT)
                            .putExtra("cotevent", event));
            return ImportResult.SUCCESS;
        } else if (type != null && item != null) {
            Log.d(TAG, "Adding an emergency marker with type=" + type);
            item.setMetaString(EMERGENCY_TYPE_META_FIELD, type);
            item.setVisible(true);
            item.setEditable(false);
            item.setMetaString("menu", "menus/alert_menu.xml");

            item.setMovable(false);

            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    EmergencyAlertReceiver.ALERT_EVENT)
                            .putExtra("cotevent", event));
            return ImportResult.SUCCESS;
        } else {
            Log.w(TAG,
                    "Got an emergency message that I didn't know how to handle: "
                            + event + " with marker: " + item);
            return ImportResult.FAILURE;
        }
    }

}
