
package com.atakmap.android.cot.importer;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.CotMapAdapter;
import com.atakmap.android.cot.CotModificationManager;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.emergency.EmergencyDetailHandler;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Collections;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * CoT importer specifically for markers
 * Most of this implementation was moved from {@link CotMapAdapter}
 */
public class MarkerImporter extends MapItemImporter {

    public MarkerImporter(MapView mapView, MapGroup group, Set<String> types) {
        super(mapView, group, types);
    }

    public MarkerImporter(MapView mapView, MapGroup group, Set<String> types,
            boolean prefixOnly) {
        this(mapView, group, types);
        setPrefixOnly(prefixOnly);
    }

    public MarkerImporter(MapView mapView, MapGroup group, String type,
            boolean prefixOnly) {
        this(mapView, group, Collections.singleton(type), prefixOnly);
    }

    public MarkerImporter(MapView mapView, String groupName, Set<String> types,
            boolean prefixOnly) {
        this(mapView, mapView.getRootGroup().findMapGroup(groupName), types,
                prefixOnly);
    }

    public MarkerImporter(MapView mapView, String groupName,
            Set<String> types) {
        this(mapView, groupName, types, false);
    }

    public MarkerImporter(MapView mapView, String groupName, String type,
            boolean prefixOnly) {
        this(mapView, groupName, Collections.singleton(type), prefixOnly);
    }

    public MarkerImporter(MapView mapView, String groupName, String type) {
        this(mapView, groupName, type, false);
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        if (existing != null && !(existing instanceof Marker))
            return ImportResult.IGNORE;

        String serverFromExtra = extras.getString("serverFrom");

        boolean localImport = isLocalImport(extras);
        boolean fromStateSaver = isStateSaverImport(extras);

        boolean needsRefresh = false;
        boolean needsPersist = false;
        GeoPoint pointBefore = null;
        Marker marker = (Marker) existing;
        if (marker == null) {
            // The marker was most likely just removed
            if (extras.getBoolean("doNotRecreate"))
                return ImportResult.FAILURE;
            marker = createMarker(event, extras);
            needsRefresh = true;
        } else
            pointBefore = marker.getPoint();

        if (marker == null)
            return ImportResult.FAILURE;

        //if this came in on a streaming connection, lets tag the marker as such
        if (!localImport && !FileSystemUtils.isEmpty(serverFromExtra)) {
            //Log.d(TAG, marker.getUID() + " serverFrom=" + serverFromExtra);
            marker.setMetaString("serverFrom", serverFromExtra);
        }

        GeoPoint gp = event.getGeoPoint();

        // Modification o blocked until the user approves it
        if (CotModificationManager.process(event, extras)) {
            return ImportResult.SUCCESS;
        }

        // calculate instant speed based on points received from the network and store it as a double "est.speed", "est.course", "est.dist", and "est.time" as a long
        // dr.speed is in meters per second
        // dr.course is in degrees
        // this is only useful for the instantaneous time between the last two points.
        if (pointBefore != null) {
            final long lastUpdateTime = marker.getMetaLong("lastUpdateTime",
                    -1);
            if (lastUpdateTime >= 0) {
                final long currTime = new CoordinatedTime().getMilliseconds();
                final double d = pointBefore.distanceTo(gp);
                final double s = d / ((currTime - lastUpdateTime) / 1000d);
                final double b = pointBefore.bearingTo(gp);
                marker.setMetaLong("est.time", currTime);
                marker.setMetaDouble("est.speed", s);
                marker.setMetaDouble("est.course", b);
                marker.setMetaDouble("est.dist", d);
                //Log.d(TAG, "received network item speed=" + s + " bearing=" + b);
                //Log.d(TAG, "distance=" + pointBefore.distanceTo(gp) + " time=" + (currTime - lastUpdateTime) / 1000d);
            }
        }

        // revisit
        marker.setPoint(gp);
        marker.setMetaString("access", event.getAccess());
        marker.setMetaString("qos", event.getQos());
        marker.setMetaString("opex", event.getOpex());

        String markerType = marker.getType();
        String eventType = event.getType();

        // Update marker type based on event type (ignore file transfer events)
        if (!eventType.startsWith("b-f-t-") && (markerType == null
                || !markerType.equals(eventType))) {
            marker.setType(eventType);
            needsRefresh = true;
        }

        if (eventType.startsWith("b-d") || eventType.startsWith(
                EmergencyDetailHandler.EMERGENCY_TYPE_PREFIX)) {
            marker.setStyle(marker.getStyle() | Marker.STYLE_ALERT_MASK);
        }

        String teamBefore = marker.getMetaString("team", null);
        int colorBefore = marker.getMetaInteger("color", Color.WHITE);
        String roleBefore = marker.getMetaString("atakRoleType", null);
        String radRoleBefore = marker.getMetaString("rad_unit_type", null);
        String iconsetBefore = marker.getMetaString(UserIcon.IconsetPath, null);
        String callsignBefore = marker.getTitle();

        final String mhow = marker.getMetaString("how", null);
        if (!FileSystemUtils.isEquals(mhow, event.getHow())) {
            marker.setMetaString("how", event.getHow());
            needsRefresh = true;
        }

        // If the marker is machine generated, do not allow for it
        // to be moved.
        if (event.getHow() != null) {
            if (!event.getHow().equals("m-g"))
                marker.setMovable(true);
            else
                marker.removeMetaData("movable");
        }

        /**
         * If the statesaver had recorded this marker as being visible or invisible.
         * set the value, before the handlers do their thing, otherwise there's no way
         * to set the visibility until well after marker establishment.
         */
        boolean visible = extras.getBoolean("visible", marker.getVisible(true));
        //Log.d(TAG, "setting visibility of: " + marker.getUID() + " " + visible);
        marker.setVisible(visible, false);

        CotDetailManager.getInstance().processDetails(marker, event);

        // XXX - Consider the fact that a GeoPointMetaData changes but the GeoPoint stays the same
        // for 3.13.

        GeoPoint pointAfter = marker.getPoint();
        if (!FileSystemUtils.isEquals(pointBefore, pointAfter))
            needsPersist = true;

        String callsignAfter = marker.getTitle();

        // In previous versions of ATAK before 3.12, the uid was used as the title.    
        // When improving this workflow dramatically in 3.12, Markers with just a 
        // UID no longer have their title set.
        // There are still cases where UUID's are still sent without the callsign set.
        if (FileSystemUtils.isEmpty(callsignAfter)
                && !ATAKUtilities.isUUID(marker.getUID()))
            marker.setTitle(callsignAfter = marker.getUID());

        if (!FileSystemUtils.isEquals(callsignBefore, callsignAfter))
            needsRefresh = true;

        String teamAfter = marker.getMetaString("team", null);
        if (!FileSystemUtils.isEquals(teamBefore, teamAfter))
            needsRefresh = true;

        String radRoleAfter = marker.getMetaString("rad_unit_type", null);
        if (!FileSystemUtils.isEquals(radRoleBefore, radRoleAfter))
            needsRefresh = true;

        String iconsetAfter = marker.getMetaString(UserIcon.IconsetPath, null);
        if (!FileSystemUtils.isEquals(iconsetBefore, iconsetAfter))
            needsRefresh = true;

        int colorAfter = marker.getMetaInteger("color", Color.WHITE);
        if (colorBefore != colorAfter)
            needsRefresh = true;

        String roleAfter = marker.getMetaString("atakRoleType", null);
        if (!FileSystemUtils.isEquals(roleBefore, roleAfter))
            needsRefresh = true;

        // Marker details open
        if (marker.hasMetaValue("focused"))
            needsRefresh = true;

        // Update details CRC
        CRC32 crc = new CRC32();
        crcDetails(event.getDetail(), crc);
        long newCRC = crc.getValue();
        long oldCRC = marker.getMetaLong("__detailsCRC", 0L);
        marker.setMetaLong("__detailsCRC", newCRC);

        // If the details have changed in some way then a refresh/persist is needed
        if (newCRC != oldCRC)
            needsRefresh = true;

        // Add the marker to the map if isn't already
        addToGroup(marker);

        //String clazz = extra.getString("fromClass");
        if (fromStateSaver) {
            //event from State Saver
            final long lastUpdateTime = extras.getLong("lastUpdateTime", -1);
            marker.setMetaLong("lastUpdateTime", lastUpdateTime);
        } else {
            //not from Saver
            if (!extras.containsKey("lastUpdateTime")) {
                //not sent via MapItem.refresh/persist (e.g. in via network/CoTService)
                extras.putLong("lastUpdateTime",
                        new CoordinatedTime().getMilliseconds());
                marker.setMetaLong("lastUpdateTime",
                        extras.getLong("lastUpdateTime", -1));
            }

            // autoStale support
            if (!CotMapAdapter.isAtakSpecialType(marker)) {
                long autoStaleDuration = (3 * event.getStale().millisecondDiff(
                        event.getStart())) / 2;

                // some systems are generating a negative number, although this should be
                // benign, just make sure by setting the value to 0.
                if (autoStaleDuration < 0)
                    autoStaleDuration = 0;

                marker.setMetaLong("autoStaleDuration", autoStaleDuration);
                //Log.d(TAG, marker.getUID() + " Setting autoStaleDuration: " + autoStaleDuration + " for " + event.getType());
            }
        }

        if (needsRefresh) {
            //Log.d(TAG, "needsRefresh: " + marker.getUID());
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(_mapView.getContext());
            if (prefs.getBoolean("showTadiljIds", false)) {
                marker.setSummaryFlag(true);
            } else {
                marker.setSummaryFlag(false);
            }
            marker.refresh(_mapView.getMapEventDispatcher(), extras,
                    this.getClass()); // notify listeners of the change

            if (!fromStateSaver) {
                if (prefs.getBoolean("showTadiljIds", false)) {
                    marker.setSummaryFlag(true);
                } else {
                    marker.setSummaryFlag(false);
                }
                persist(marker, extras);
            }
        } else if (needsPersist && !fromStateSaver) {
            persist(marker, extras);
        }

        extras.putShort("CotMapProcessed", (short) 1);
        return ImportResult.SUCCESS;
    }

    /**
     * Create a new marker given a CoT event and extras
     *
     * @param event CoT event
     * @param extras Import extras
     * @return Newly created marker
     */
    protected Marker createMarker(CotEvent event, Bundle extras) {
        //Log.d(TAG, "creating a new marker for: "+event.getUID());
        Marker m = new Marker(event.getUID());
        m.setType(event.getType());
        // push CoT markers up the Z-order stack using 1 higher than the default order.
        m.setZOrder(m.getZOrder() - 1d);

        m.setStyle(m.getStyle() | Marker.STYLE_MARQUEE_TITLE_MASK);
        m.setMetaString("how", event.getHow());
        m.setMetaString("entry", "CoT");

        // XXX: HACK HACK HACK HACK HACK HACK HACK HACK HACK
        if (isLocalImport(extras)) {
            m.setMetaBoolean("transient", false);
            m.setMetaBoolean("archive", true);

            // XXX - items need to have an 'entry' of "user" in order to
            // have elevation auto-populated from DTED. User items
            // persisted between invocations of ATAK lose their
            // metadata and are no registered for elevation
            // auto-population
            // marker.setMetaString("entry", "user");
        }

        // check to see if this is ourself
        final String deviceUID = _mapView.getSelfMarker().getUID();
        if (deviceUID.equals(event.getUID())) {
            m.setMetaBoolean("self", true);
            m.setIcon(new Icon.Builder().setImageUri(0,
                    ATAKUtilities.getResourceUri(R.drawable.friendlydir))
                    .setAnchor(32, 32).build());
        }

        // Give it full mutability.
        m.setMovable(true);
        m.setMetaBoolean("removable", true);
        m.setMetaBoolean("editable", true);

        // flag a marker as interesting at this time.  this will be used by the offscreen
        // search mechanism to weed out offscreen markers that are close but may no longer be
        // interesting.   New markers are interesting....
        if (!isStateSaverImport(extras))
            m.setMetaLong("offscreen_interest", SystemClock.elapsedRealtime());

        return m;
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        String type = item.getType();
        if (type.startsWith("a-f") || type.startsWith("a-a"))
            return R.drawable.friendly;
        if (type.startsWith("a-h") || type.startsWith("a-j")
                || type.startsWith("a-k")
                || type.startsWith("a-s"))
            return R.drawable.hostile;
        else if (type.startsWith("a-n"))
            return R.drawable.neutral;
        else if (type.startsWith("b-m-p-i"))
            return R.drawable.piicon;
        else if (type.startsWith("b-m-p-c-cp"))
            return R.drawable.piicon_contact;
        else if (type.startsWith("b-m-p-c-ip"))
            return R.drawable.piicon_initial;
        else if (type.startsWith("b-m-p-c"))
            return R.drawable.piicon;
        else if (type.equals("b-m-p-w-GOTO"))
            return R.drawable.pointtype_waypoint_default;
        else if (type.startsWith("b-m-p-w"))
            return R.drawable.generic;
        else if (type.startsWith("b-i-x-i"))
            return R.drawable.camera;
        else if (type.startsWith("b-d"))
            return R.drawable.ic_menu_emergency;
        else if (type.startsWith("u-r-b-bullseye"))
            return R.drawable.bullseye;
        else if (type.equals("b-m-p-s-p-loc"))
            return R.drawable.sensor;
        else if (type.equals("b-m-p-s-p-op"))
            return R.drawable.ic_menu_binos;

        return R.drawable.unknown;
    }
}
