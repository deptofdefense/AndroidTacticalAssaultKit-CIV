
package com.atakmap.android.geofence.data;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.geofence.component.GeoFenceComponent;
import com.atakmap.android.geofence.data.GeoFence.MonitoredTypes;
import com.atakmap.android.geofence.data.GeoFence.Trigger;
import com.atakmap.android.geofence.monitor.GeoFenceMonitor;
import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;

/**
 * Implementation that allows for Geofence tags to appear within any marker.
 */
public class GeoFenceDetailHandler extends CotDetailHandler {

    private static final String TAG = "GeoFenceDetailHandler";

    public GeoFenceDetailHandler() {
        super("__geofence");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (!item.hasMetaValue(GeoFenceConstants.MARKER_TRIGGER))
            return false;

        CotDetail geofence = new CotDetail("__geofence");
        geofence.setAttribute(GeoFenceConstants.COT_TRIGGER,
                item.getMetaString(GeoFenceConstants.MARKER_TRIGGER,
                        Trigger.Entry.toString()));

        geofence.setAttribute(GeoFenceConstants.COT_TRACKING,
                Boolean.toString(item.getMetaBoolean(
                        GeoFenceConstants.MARKER_TRACKING, false)));

        String monitoredTypes = item.getMetaString(
                GeoFenceConstants.MARKER_MONITOR,
                MonitoredTypes.All.toString());
        geofence.setAttribute(GeoFenceConstants.COT_MONITOR, monitoredTypes);

        geofence.setAttribute(GeoFenceConstants.COT_BOUNDING_SPHERE,
                String.valueOf(item.getMetaDouble(
                        GeoFenceConstants.MARKER_BOUNDING_SPHERE,
                        GeoFenceConstants.DEFAULT_ENTRY_RADIUS_METERS)));

        geofence.setAttribute(GeoFenceConstants.COT_ELEVATION_MONITORED,
                String.valueOf(item.getMetaBoolean(
                        GeoFenceConstants.MARKER_ELEVATION_MONITORED, false)));

        geofence.setAttribute(GeoFenceConstants.COT_ELEVATION_MIN,
                String.valueOf(item.getMetaDouble(
                        GeoFenceConstants.MARKER_ELEVATION_MIN,
                        GeoPoint.UNKNOWN)));

        geofence.setAttribute(GeoFenceConstants.COT_ELEVATION_MAX,
                String.valueOf(item.getMetaDouble(
                        GeoFenceConstants.MARKER_ELEVATION_MAX,
                        GeoPoint.UNKNOWN)));

        if (MonitoredTypes.Custom.toString().equals(monitoredTypes)) {

            ArrayList<String> monitorUids = item.getMetaStringArrayList(
                    GeoFenceConstants.MARKER_MONITOR_UIDS);

            if (FileSystemUtils.isEmpty(monitorUids)) {
                // For some reason the monitor UIDs are missing from the meta string list
                // Rather than go on a wild goose chase looking for the one execution stack
                // where the meta string isn't being copied over or updated to the map item,
                // just read it from the monitor directly
                // XXX - What's wrong with a single central data store such as the GeoFence class?
                // Why copy stuff over to metadata? It's confusing, redundant, and leaves room for error.

                GeoFenceMonitor monitor = GeoFenceComponent.getInstance()
                        .getManager()
                        .getMonitor(item.getUID());
                if (monitor != null)
                    monitorUids = monitor.getSelectedItemUids();
            }

            if (!FileSystemUtils.isEmpty(monitorUids)) {
                for (String uid : monitorUids) {
                    CotDetail m = new CotDetail(GeoFenceConstants.COT_MONITOR);
                    m.setAttribute("uid", uid);
                    geofence.addChild(m);
                }
            }
        }

        detail.addChild(geofence);
        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String triggerString = detail
                .getAttribute(GeoFenceConstants.COT_TRIGGER);
        if (triggerString != null)
            item.setMetaString(GeoFenceConstants.MARKER_TRIGGER, triggerString);

        String tracking = detail
                .getAttribute(GeoFenceConstants.COT_TRACKING);
        item.setMetaBoolean(GeoFenceConstants.MARKER_TRACKING,
                tracking == null || Boolean.parseBoolean(tracking));

        String monitoredString = detail
                .getAttribute(GeoFenceConstants.COT_MONITOR);
        if (monitoredString != null)
            item.setMetaString(GeoFenceConstants.MARKER_MONITOR,
                    monitoredString);

        String boundingSphere = detail
                .getAttribute(GeoFenceConstants.COT_BOUNDING_SPHERE);
        if (boundingSphere != null)
            item.setMetaDouble(GeoFenceConstants.MARKER_BOUNDING_SPHERE,
                    parseDouble(boundingSphere,
                            GeoFenceConstants.DEFAULT_ENTRY_RADIUS_METERS));

        String elevMonitored = detail
                .getAttribute(GeoFenceConstants.COT_ELEVATION_MONITORED);
        if (elevMonitored != null)
            item.setMetaBoolean(GeoFenceConstants.MARKER_ELEVATION_MONITORED,
                    Boolean.parseBoolean(elevMonitored));

        //the following are expressed in M HAE
        String minElevationMHAE = detail
                .getAttribute(GeoFenceConstants.COT_ELEVATION_MIN);
        if (minElevationMHAE != null)
            item.setMetaDouble(GeoFenceConstants.MARKER_ELEVATION_MIN,
                    parseDouble(minElevationMHAE, GeoPoint.UNKNOWN));

        String maxElevationMHAE = detail
                .getAttribute(GeoFenceConstants.COT_ELEVATION_MAX);
        if (maxElevationMHAE != null)
            item.setMetaDouble(GeoFenceConstants.MARKER_ELEVATION_MAX,
                    parseDouble(maxElevationMHAE, GeoPoint.UNKNOWN));

        // List of item UIDs to monitor (custom monitor only)
        int childCount = detail.childCount();
        if (childCount > 0 && MonitoredTypes.Custom.toString()
                .equals(monitoredString)) {
            ArrayList<String> uids = new ArrayList<>();
            for (int i = 0; i < childCount; i++) {
                CotDetail m = detail.getChild(i);
                if (m == null || !GeoFenceConstants.COT_MONITOR
                        .equals(m.getElementName()))
                    continue;
                String uid = m.getAttribute("uid");
                if (!FileSystemUtils.isEmpty(uid))
                    uids.add(uid);
            }
            if (!uids.isEmpty())
                item.setMetaStringArrayList(
                        GeoFenceConstants.MARKER_MONITOR_UIDS, uids);
        }

        Log.d(TAG, "loading fence from marker: " + item);

        GeoFence gf = GeoFence.fromMapItem(item);

        if (gf != null) {
            Log.d(TAG, "dispatching fence from item: " + item);
            item.setMetaBoolean(GeoFenceConstants.GEO_FENCE_IMPORTED, true);
            GeoFenceComponent.getInstance().dispatch(gf, item);
            return ImportResult.SUCCESS;
        } else {
            Log.d(TAG,
                    "fence construction failed for marker: " + item);
            return ImportResult.FAILURE;
        }
    }
}
