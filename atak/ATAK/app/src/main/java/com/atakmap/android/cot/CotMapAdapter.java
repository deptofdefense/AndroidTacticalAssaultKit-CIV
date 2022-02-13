
package com.atakmap.android.cot;

import android.os.Bundle;

import com.atakmap.android.cotdelete.CotDeleteEventMarshal;
import com.atakmap.android.emergency.EmergencyDetailHandler;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.http.datamodel.FileTransfer;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.overlay.NonExportableMapGroupOverlay;
import com.atakmap.android.user.FilterMapOverlay;

import com.atakmap.android.user.icon.SpotMapReceiver;

import com.atakmap.android.vehicle.VehicleMapComponent;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CotMapAdapter {

    public static final String TAG = "CotMapAdapter";
    public static final String FLOWTAG = "_flow-tags_";
    public static final String CHAT = "__chat";
    private static final Object lock = new Object();

    private static final double ZORDER_SPI = -1002d;
    private static final double ZORDER_EMERGENCY = -1001d;
    private static final double ZORDER_FRIENDLY = -999d;
    private static final double ZORDER_HOSTILE = -998d;
    private static final double ZORDER_NEUTRAL = -997d;
    private static final double ZORDER_UNKNOWN = -996d;

    private final CotMarkerRefresher _markers;
    private final MapView _mapView;
    private final MapGroup _cotGroup;
    private final MapGroup _spiGroup;
    private final MapGroup _hostileGroup;
    private final MapGroup _friendlyGroup;
    private final MapGroup _neutralGroup;
    private final MapGroup _unknownGroup;
    private final MapGroup _waypointGroup;
    private final MapGroup _dipsGroup;
    private final MapGroup _routeGroup;
    private MapGroup _drawingGroup;
    private MapGroup _quickPickMapGroup;
    private final MapGroup _altDipsGroup;
    private final MapGroup _vehicleGroup;
    private final MapGroup _otherGroup;
    private final MapGroup _spotMapGroup, _airspaceGroup, _casevacGroup;

    CotMapAdapter(MapView mapView) {
        _mapView = mapView;

        // TODO: Why are we creating a Hostile group under both "Cursor on Target"
        // TODO: and "User Objects"??? (see UserMapComponent)
        _cotGroup = new DefaultMapGroup("Cursor on Target");
        _cotGroup.setMetaBoolean("permaGroup", true);
        _cotGroup.setMetaBoolean("addToObjList", false);
        _mapView.getMapOverlayManager().addOtherOverlay(
                new NonExportableMapGroupOverlay(mapView, _cotGroup,
                        FilterMapOverlay
                                .getRejectFilter(mapView)));

        _spiGroup = new DefaultMapGroup("SPIs");
        _spiGroup.setMetaBoolean("permaGroup", true);
        _spiGroup.setMetaString("iconUri", "asset://icons/b-m-p-s-p-i.png");
        _spiGroup.setDefaultZOrder(ZORDER_SPI);

        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp == null || !fp.hasMilCapabilities())
            _spiGroup.setMetaString("omNameOverride", "DPs");

        _mapView.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(mapView, _spiGroup, FilterMapOverlay
                        .getRejectFilter(mapView)));

        _hostileGroup = new DefaultMapGroup("Hostile", "hostile", true,
                ZORDER_HOSTILE);
        _cotGroup.addGroup(_hostileGroup);

        _friendlyGroup = new DefaultMapGroup("Friendly", "friendly", true,
                ZORDER_FRIENDLY);
        _cotGroup.addGroup(_friendlyGroup);

        _neutralGroup = new DefaultMapGroup("Neutral", "neutral", true,
                ZORDER_NEUTRAL);
        _cotGroup.addGroup(_neutralGroup);

        _unknownGroup = new DefaultMapGroup("Unknown", "unknown", true,
                ZORDER_UNKNOWN);
        _cotGroup.addGroup(_unknownGroup);

        _waypointGroup = new DefaultMapGroup("Waypoint", "waypoint", false);
        _cotGroup.addGroup(_waypointGroup);

        //configured via overlays_hier.png
        _routeGroup = new DefaultMapGroup("Route", "route", true);
        _mapView.getRootGroup().addGroup(_routeGroup);

        _dipsGroup = new DefaultMapGroup("DIPs", "dip", true);
        _dipsGroup.setMetaBoolean("ignoreOffscreen", true);
        _mapView.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(mapView, _dipsGroup,
                        "asset://icons/dip.png"));

        // create alt dip group
        _altDipsGroup = _dipsGroup.addGroup(_mapView.getResources().getString(
                R.string.alternate_dips));
        _altDipsGroup.setMetaBoolean("ignoreOffscreen", true);
        _altDipsGroup.setMetaString("overlay", "alternate DIPs");
        _altDipsGroup.setMetaBoolean("permaGroup", true);
        _altDipsGroup.setMetaString("iconUri", "asset://icons/alt_dip.png");

        MapGroup alertGroup = new DefaultMapGroup("Emergency");
        alertGroup.setMetaBoolean("ignoreOffscreen", true);
        alertGroup.setMetaBoolean("permaGroup", true);
        alertGroup.setDefaultZOrder(ZORDER_EMERGENCY);
        _mapView.getRootGroup().addGroup(alertGroup);

        _drawingGroup = _mapView.getRootGroup().findMapGroup("Drawing Objects");
        if (_drawingGroup == null) {
            _drawingGroup = new DefaultMapGroup("Drawing Objects");
            _drawingGroup.setMetaBoolean("ignoreOffscreen", true);
            _drawingGroup.setMetaBoolean("permaGroup", true);
            String iconUri = "android.resource://"
                    + _mapView.getContext().getPackageName()
                    + "/" + R.drawable.ic_menu_drawing;
            _mapView.getMapOverlayManager().addShapesOverlay(
                    new DefaultMapGroupOverlay(_mapView, _drawingGroup,
                            iconUri));
        }

        _otherGroup = new DefaultMapGroup("Other");
        _otherGroup.setMetaString("overlay", "other");
        _otherGroup.setMetaBoolean("permaGroup", true);
        //_cotGroup.addGroup(_otherGroup);
        _mapView.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(mapView, _otherGroup,
                        "asset://icons/unknown-type.png"));

        _vehicleGroup = VehicleMapComponent.getVehicleGroup(_mapView);

        _quickPickMapGroup = _mapView.getRootGroup().findMapGroup("Quick Pic");
        if (_quickPickMapGroup == null) {
            _quickPickMapGroup = new DefaultMapGroup("Quick Pic");
            _quickPickMapGroup.setMetaString("overlay", "quickpic");
            //hide from default Overlay Manager map group, managed by AttachmentMapOverlay
            _quickPickMapGroup.setMetaBoolean("addToObjList", false);
            _mapView.getRootGroup().addGroup(_quickPickMapGroup);
        }

        _spotMapGroup = _mapView.getRootGroup().findMapGroup("Spot Map");
        _airspaceGroup = _mapView.getRootGroup().findMapGroup("Airspace");
        _casevacGroup = _mapView.getRootGroup().findMapGroup("CASEVAC");

        _markers = new CotMarkerRefresher(_mapView);
    }

    public void dispose() {

        _markers.dispose();

        for (MapGroup m : getCategoryGroups().values()) {
            m.clearGroups();
        }
    }

    public CotMarkerRefresher getCotMarkerSet() {
        return _markers;
    }

    public Map<String, MapGroup> getCategoryGroups() {
        HashMap<String, MapGroup> groups = new HashMap<>();
        groups.put("Hostile", _hostileGroup);
        groups.put("Unknown", _unknownGroup);
        groups.put("Friendly", _friendlyGroup);
        groups.put("Neutral", _neutralGroup);
        groups.put("Other", _otherGroup);
        groups.put("Waypoint", _waypointGroup);
        groups.put("Spot Map", _spotMapGroup);
        groups.put("Vehicles", _vehicleGroup);
        groups.put("Airspace", _airspaceGroup);
        groups.put("Route", _routeGroup);
        groups.put("CASEVAC", _casevacGroup);
        //groups.put("DIPs", _dipsGroup);                    // do not clear the sub groups because this contains 
        // could contain a DIP.    If the DIP has been calculated
        // then the ACTUAL DIP is in the group that is being cleared
        // When local broadcast was not being used, this seemed not to
        // remove the DIP
        groups.put("DIPs/alternate", _altDipsGroup);
        return groups;
    }

    public ImportResult processCotEvent(CotEvent event, Bundle bundle) {
        try {
            return ImporterManager.importData(event, bundle);
        } catch (IOException e) {
            Log.e(TAG, "Failed to process: " + event);
            return ImportResult.FAILURE;
        }
    }

    public ImportResult adaptCotEvent(CotEvent event, Bundle extra) {
        String type = event.getType();

        if (type == null)
            return ImportResult.FAILURE;

        //check some special cases, and then defer to ImporterManager
        // Plugins should NEVER add anything to this if statement.
        // If you want to block a CoT event from being processed then just
        // register a no-op importer via CotImporterManager.registerImporter
        // which always returns ImportResult.FAILURE

        if (type.equals("a-u-X")) {
            return ImportResult.FAILURE;
        } else if (type.startsWith("a-f-G-E-S-rad")) {
            // exclude radsensor measurement/permissions/db events
            return ImportResult.FAILURE;
        } else if (event.getDetail() != null
                && event.getDetail().getFirstChildByName(0,
                        "rad-event") != null) {
            return ImportResult.FAILURE;
        } else if (type.equals("effects_event")) {
            // exclude Effects cot messages
            return ImportResult.FAILURE;
        } else if (type.startsWith("y")) {
            return ImportResult.FAILURE;
        } else if (type.startsWith("t")
                && !type.equals("t-k-d")
                && !type.equals("t-x-a-m-Geofence") // permanently ignore
                && !type.equals(
                        CotDeleteEventMarshal.COT_TASK_DISPLAY_DELETE_TYPE)
                && !type.equals("t-s-v-e")) {
            //TODO, do we really have to know about these here...?
            // exclude tasking except nine line & geofence & delete task
            return ImportResult.FAILURE;
        } else if (type.startsWith("b-t-f")
                || type.equals(FileTransfer.COT_TYPE)) {
            return ImportResult.FAILURE;
        } else {
            //defer to ImporterManager
            return processCotEvent(event, extra);
        }
    }

    /**
     * Rewrite the isAtakSpecialType(CotEvent) to work on MapItems.
     */
    public static boolean isAtakSpecialType(final MapItem m) {
        if (m.getMetaString("legacy_cot_event", null) != null) {
            // enterprise sync
            return true;
        } else if (m.getMetaBoolean("archive", false)) {
            return true;
        } else if (m.getType().startsWith("b-r-f-h-c")) {
            return true;
        } else if (m.getType().startsWith("a-f-G") &&
                m.hasMetaValue("rad_unit_type")) {
            return true;
        }

        final String type = m.getType();

        if (type != null && type.length() > 2 && type.charAt(1) == '-') {
            final char typeStart = type.charAt(0);

            if (typeStart == 'u') {
                if (type.equals("u-d-f") || // Free-form
                        type.equals("u-d-f-m") || //MultiPolyline Group
                        type.equals("u-d-r") || // Rectangle
                        type.equals("u-d-k") || // keyhole
                        type.equals("u-d-p") || // Point
                        type.equals("u-d-v") || // Vehicle
                        type.startsWith("u-rb")) { // Range & Bearing Objects
                    return true;
                }

            } else if (typeStart == 'b') {
                if (type.startsWith("b-m-p-j")
                        || // JumpMaster
                        type.equals("b-m-r")
                        || // routes
                        type.startsWith("b-m-p-w")
                        || // Way points
                        type.startsWith(SpotMapReceiver.SPOT_MAP_POINT_COT_TYPE)
                        || // Spot Map points
                        type.startsWith(
                                EmergencyDetailHandler.EMERGENCY_TYPE_PREFIX)
                        || // Emergency alerts points
                        type.startsWith("b-e-r")
                        || // Zone Protected point
                        type.startsWith("b-m-p-c-z")) { // Enterprise Sync Tool ResourceMarker and
                                                        // MapBoundingBox
                    return true;
                }

            }
        } else if (type != null && !type.equals(MapItem.EMPTY_TYPE)) {
            // catch all for drawing and survey objects
            return true;
        }

        return false;
    }
}
