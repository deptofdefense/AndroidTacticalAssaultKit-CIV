
package com.atakmap.android.cotdelete;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.importexport.AbstractCotEventImporter;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent;

import java.io.InputStream;

/**
 * Supports making markers stale based on a CoT Delete task
 * <?xml version='1.0' standalone='yes' ?><event start='2012-01-01T00:00:00Z' time='2012-01-01T00:00:00Z' stale='2020-01-01T00:00:00Z' how='m-g' type='t-x-d-d' uid="55555-XXX-XXX' version='2.0'><detail><link uid="ANDROID-78:4b:87:f3:5b:0c" relation="none" type="none"/><__forcedelete/></detail><point ce="9999999" le="9999999" lat="36.789783" lon="-115.471535" hae="4433.086151" /></event>
 * The key parts of this message are the type (t-x-d-d) the link attribute populated and the optional __forcedelete tag.
 */
public class CotDeleteImporter extends AbstractCotEventImporter {
    private final static String TAG = "CotDeleteImporter";
    private final SharedPreferences prefs;

    private final MapView mapView;

    public CotDeleteImporter(final MapView mapView) {
        super(mapView.getContext(), CotDeleteEventMarshal.CONTENT_TYPE);
        this.mapView = mapView;
        prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
    }

    @Override
    public CommsMapComponent.ImportResult importData(CotEvent event,
            Bundle extra) {

        //Log.d(TAG, "received event: " + event);
        if (!event.getType().equals(
                CotDeleteEventMarshal.COT_TASK_DISPLAY_DELETE_TYPE))
            return CommsMapComponent.ImportResult.FAILURE;

        // Ignore any route that's internal, it's already in the system
        final String from = extra.getString("from");
        if (from != null && from.equals("internal"))
            return CommsMapComponent.ImportResult.FAILURE;

        //Log.d(TAG, "importData: " + event.getUID());
        CotDetail detail = event.getDetail();
        if (detail == null)
            return CommsMapComponent.ImportResult.FAILURE;

        CotDetail link = detail.getFirstChildByName(0, "link");
        if (link == null)
            return CommsMapComponent.ImportResult.FAILURE;

        String uid = link.getAttribute("uid");
        String relation = link.getAttribute("relation");
        String type = link.getAttribute("type");
        if (FileSystemUtils.isEmpty(uid) || FileSystemUtils.isEmpty(relation)
                || FileSystemUtils.isEmpty(type))
            return CommsMapComponent.ImportResult.FAILURE;

        CotDetail force = detail.getFirstChildByName(0, "__forcedelete");
        if (force != null) {
            Log.d(TAG, "received task forcedelete for: " + uid + " of type: "
                    + type);
            MapItem deletedItem = mapView.getMapItem(uid);

            // find the associated shape so that the appropriate map item is removed.
            deletedItem = com.atakmap.android.util.ATAKUtilities
                    .findAssocShape(deletedItem);

            if (deletedItem != null) {
                MapGroup mg = deletedItem.getGroup();
                if (mg != null) {
                    mg.removeItem(deletedItem);
                    return CommsMapComponent.ImportResult.SUCCESS;
                }
            }

        }

        //Here we set stale so CoTMarkerRefresher will delete per settings
        Log.d(TAG, "received task stale for: " + uid + " of type: " + type);
        if (prefs.getBoolean("staleRemoteDisconnects", true)) {
            MapItem deletedItem = mapView.getMapItem(uid);
            if (deletedItem instanceof Marker &&
                    !deletedItem.getMetaBoolean("stale", false)) {
                Log.d(TAG, "Setting marker stale: " + uid);
                Marker deleted = (Marker) deletedItem;
                deleted.setMetaBoolean("forceStale", true);
            }
        }

        return CommsMapComponent.ImportResult.SUCCESS;
    }

    @Override
    protected CommsMapComponent.ImportResult importNonCotData(
            InputStream source,
            String mime) {
        return CommsMapComponent.ImportResult.FAILURE;
    }
}
