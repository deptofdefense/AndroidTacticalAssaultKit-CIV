
package com.atakmap.android.geofence.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.geofence.alert.GeoFenceAlerting;
import com.atakmap.android.geofence.component.GeoFenceComponent;
import com.atakmap.android.geofence.component.GeoFenceReceiver;
import com.atakmap.android.geofence.data.GeoFence;
import com.atakmap.android.geofence.data.ShapeUtils;
import com.atakmap.android.geofence.monitor.GeoFenceManager;
import com.atakmap.android.geofence.monitor.GeoFenceMonitor;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.toolbars.RangeAndBearingMapComponent;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.toolbars.RangeAndBearingReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.kml.KMLUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 *
 */
public class GeoFenceListModel extends AbstractHierarchyListItem2
        implements GoTo, MapItemUser, Search, Delete, View.OnClickListener,
        View.OnLongClickListener {

    private static final String TAG = "GeoFenceListModel";

    private final static Set<Class<? extends Action>> ACTION_FILTER = new HashSet<>();
    static {
        ACTION_FILTER.add(GoTo.class);
        ACTION_FILTER.add(Search.class);
    }

    /**
     * Cache list of alerts when the GeoFenceListModel is created
     */
    private final GeoFence _fence;
    /**
     * The Shape map item for the HierarchyListItem
     */
    private final MapItem _item;

    private final MapView _view;
    private final Context _context;

    private final GeoFenceManager _manager;
    private final SimpleDateFormat _sdf;

    public GeoFenceListModel(MapView view, GeoFenceManager manager,
            GeoFence fence, MapItem item) {
        _fence = fence;
        _item = item;
        _view = view;
        _context = view.getContext();
        _manager = manager;

        _sdf = new SimpleDateFormat("HH:mm:ss'Z'", LocaleUtil.getCurrent());
        _sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.asyncRefresh = true;
        this.reusable = true;
    }

    @Override
    public String getUID() {
        return _item.getUID();
    }

    @Override
    public String getTitle() {
        String title = ATAKUtilities
                .getDisplayName(ATAKUtilities.findAssocShape(_item));
        if (FileSystemUtils.isEmpty(title))
            title = GeoFenceReceiver.GEO_FENCE;

        return "'" + title + "' " + _context.getString(R.string.alerts);
    }

    @Override
    public String getIconUri() {
        if (!_manager.isTracking(_fence)) {
            return "asset://icons/geofence_disabled.png";
        } else if (getChildCount() < 1) {
            return "asset://icons/geofence_noalerts.png";
        } else {
            return "asset://icons/geofence.png";
        }
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public MapItem getMapItem() {
        return _item;
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        ListModelExtraHolder h = v != null
                && v.getTag() instanceof ListModelExtraHolder
                        ? (ListModelExtraHolder) v.getTag()
                        : null;
        if (h == null) {
            h = new ListModelExtraHolder();
            v = LayoutInflater.from(_context).inflate(
                    R.layout.geofence_overlay_fenceitem, parent, false);
            h.details = v.findViewById(
                    R.id.geofence_overlay_fenceitem_btnDetails);
            h.delete = v.findViewById(
                    R.id.geofence_overlay_fenceitem_btnDelete);
            v.setTag(h);
        }
        h.details.setOnClickListener(this);
        h.delete.setOnClickListener(this);
        return v;
    }

    @Override
    protected void refreshImpl() {
        // Get new alerts
        final List<GeoFenceAlerting.Alert> alerts = _manager.getAlerting()
                .getAlerts(_fence.getMapItemUid());

        // Filter
        List<HierarchyListItem> filtered = new ArrayList<>();
        for (GeoFenceAlerting.Alert alert : alerts) {
            GeoFenceAlertListItem item = new GeoFenceAlertListItem(alert,
                    _item);
            if (this.filter.accept(item))
                filtered.add(item);
        }

        // Update
        updateChildren(filtered);
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    /**********************************************************************/
    // Go To

    @Override
    public boolean goTo(boolean select) {
        return MapTouchController.goTo(ShapeUtils.resolveShape(_item), select);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Geofence details
        if (id == R.id.geofence_overlay_fenceitem_btnDetails) {
            onLongClick(v);
        }

        // Delete geofence
        else if (id == R.id.geofence_overlay_fenceitem_btnDelete) {
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.confirm_delete);
            b.setMessage(_context.getString(
                    R.string.geofence_overlay_fenceitem_deletebtn_alert_inquiry,
                    ATAKUtilities.getDisplayName(
                            ATAKUtilities.findAssocShape(_item)),
                    getChildCount()));
            b.setPositiveButton(R.string.dismiss_alerts,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            final GeoFenceMonitor monitor = _manager
                                    .getMonitor(_item.getUID());
                            if (monitor == null) {
                                Log.w(TAG, "Unable to find monitor for: "
                                        + _item.getUID());
                                Toast.makeText(_context,
                                        R.string.not_monitoring,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            Log.d(TAG, "Dismissing alerts for: " + monitor);
                            _manager.dismiss(monitor, false);

                            //Refresh Overlay Manager
                            ArrayList<String> p = new ArrayList<>();
                            p.add(_context.getString(R.string.alerts));
                            p.add(_context.getString(R.string.geo_fences));
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            HierarchyListReceiver.REFRESH_HIERARCHY)
                                                    .putStringArrayListExtra(
                                                            "list_item_paths",
                                                            p));
                        }
                    });
            b.setNeutralButton(R.string.stop_monitoring,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            final GeoFenceMonitor monitor = _manager
                                    .getMonitor(
                                            _item.getUID());
                            if (monitor == null) {
                                Log.w(TAG, "Unable to find monitor for: "
                                        + _item.getUID());
                                Toast.makeText(_context,
                                        R.string.not_monitoring,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            Log.d(TAG, "Stop monitoring fence: " + monitor);
                            _manager.dismiss(monitor, true);

                            //Refresh Overlay Manager
                            ArrayList<String> p = new ArrayList<>();
                            p.add(_context.getString(R.string.alerts));
                            p.add(_context.getString(R.string.geo_fences));
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            HierarchyListReceiver.REFRESH_HIERARCHY)
                                                    .putStringArrayListExtra(
                                                            "list_item_paths",
                                                            p));
                        }
                    });
            b.setNegativeButton(R.string.list_model_delete,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            Log.d(TAG, "Deleting fence: " + _fence);
                            _manager.deleteMonitor(_fence.getMapItemUid());

                            //Refresh Overlay Manager
                            ArrayList<String> p = new ArrayList<>();
                            p.add(_context.getString(R.string.alerts));
                            p.add(_context.getString(R.string.geo_fences));
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            HierarchyListReceiver.REFRESH_HIERARCHY)
                                                    .putStringArrayListExtra(
                                                            "list_item_paths",
                                                            p));
                        }
                    });
            b.show();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        //Log.d(TAG, "onLongClick");
        goTo(false);
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(GeoFenceReceiver.EDIT)
                        .putExtra("uid", this._fence.getMapItemUid()));
        return true;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        terms = terms + "*";

        Set<Long> found = new HashSet<>();
        Set<HierarchyListItem> retval = new HashSet<>();
        List<HierarchyListItem> children = getChildren();
        if (FileSystemUtils.isEmpty(children)) {
            Log.d(TAG, "No alerts to search");
            return retval;
        }
        for (String field : GeoFenceMapOverlay.SEARCH_FIELDS) {
            for (HierarchyListItem item : children) {
                MapItem alert = ((MapItemUser) item).getMapItem();
                if (alert == null || found.contains(alert.getSerialId()))
                    continue;

                if (MapGroup.matchItemWithMetaString(alert, field,
                        terms)) {
                    retval.add(item);
                    found.add(alert.getSerialId());
                }
            }
        }

        return retval;
    }

    /**
     * HierarchyListItem for map items which are being tracked, and have breached a Geo Fence
     * Partially based on MapItemHierarchyListItem
     */
    private class GeoFenceAlertListItem extends AbstractChildlessListItem
            implements GoTo, Delete, View.OnClickListener,
            View.OnLongClickListener, MapItemUser {

        private static final String TAG = "GeoFenceAlertListItem";
        private final MapItem _fenceShape;
        private final String _uid;

        /**
         * The item we are alerting (has breached a GeoFence)
         */
        private final GeoFenceAlerting.Alert _alert;

        GeoFenceAlertListItem(GeoFenceAlerting.Alert alert,
                MapItem fenceShape) {
            this._alert = alert;
            this._fenceShape = fenceShape;
            this._uid = alert.getItem().getUID() + "."
                    + (alert.isEntered() ? "entered" : "exited")
                    + "." + alert.getTimestamp();
        }

        @Override
        public boolean goTo(final boolean select) {
            displayBreach();
            return true;
        }

        private void zoomItem(final boolean menu) {
            Intent zoomIntent = new Intent();
            String zoomTo = this._alert.getItem().getUID();
            if (zoomTo != null) {
                zoomIntent.setAction("com.atakmap.android.maps.FOCUS");
                zoomIntent.putExtra("uid", zoomTo);
                zoomIntent.putExtra("useTightZoom", true);
            } else {
                zoomIntent.setAction("com.atakmap.android.maps.ZOOM_TO_LAYER");
                zoomTo = _alert.getItem().getPoint().toString();
                zoomIntent.putExtra("point", zoomTo);
            }

            Intent localMenu = new Intent();
            Intent localDetails = new Intent();
            if (menu) {
                localMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
                localMenu.putExtra("uid", zoomTo);

                localDetails.setAction("com.atakmap.android.maps.SHOW_DETAILS");
                localDetails.putExtra("uid", zoomTo);
            }

            ArrayList<Intent> intents = new ArrayList<>(3);
            intents.add(zoomIntent);
            intents.add(localMenu);
            intents.add(localDetails);

            // broadcast intent
            AtakBroadcast.getInstance().sendIntents(intents);
        }

        @Override
        public String getTitle() {
            final String timeString = _sdf.format(new Date(_alert
                    .getTimestamp()));
            return ATAKUtilities.getDisplayName(_alert.getItem())
                    + "\n"
                    + (_alert.isEntered()
                            ? _context.getString(R.string.entered)
                            : _context.getString(R.string.exited))
                    + " @ "
                    + timeString;
        }

        @Override
        public String getUID() {
            return _uid;
        }

        @Override
        public String getIconUri() {
            String pth = null;
            if (this._alert.getItem() instanceof Marker) {
                Marker mkr = (Marker) this._alert.getItem();
                if (mkr.getIcon() != null)
                    pth = mkr.getIcon().getImageUri(mkr.getState());
            } else if (this._alert.getItem().hasMetaValue("iconUri")) {
                //Log.d(TAG, "iconUri " + this._pointMapItem.getMetaString("iconUri", null));
                return this._alert.getItem().getMetaString("iconUri", null);
            }
            return pth;
        }

        @Override
        public int getIconColor() {
            int color = Color.WHITE;
            if (this._alert.getItem() instanceof Marker) {
                Marker marker = (Marker) this._alert.getItem();
                if (marker.getIcon() != null)
                    color = marker.getIcon().getColor(marker.getState());
            }
            return color;
        }

        @Override
        public Object getUserObject() {
            return this._alert;
        }

        @Override
        public MapItem getMapItem() {
            return _alert.getItem();
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            AlertExtraHolder h = v != null
                    && v.getTag() instanceof AlertExtraHolder
                            ? (AlertExtraHolder) v.getTag()
                            : null;
            if (h == null) {
                h = new AlertExtraHolder();
                v = LayoutInflater.from(_context).inflate(
                        R.layout.geofence_overlay_alertitem, parent, false);
                h.pan = v.findViewById(
                        R.id.geofence_overlay_alertitem_btnPan);
                h.delete = v.findViewById(
                        R.id.geofence_overlay_alertitem_btnDelete);
                v.setTag(h);
            }
            final GeoPoint detectedPoint = _alert.getDetectedPoint();
            h.pan.setVisibility(detectedPoint == null ? ImageButton.GONE
                    : ImageButton.VISIBLE);
            h.pan.setOnClickListener(this);
            h.delete.setOnClickListener(this);
            return v;
        }

        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.geofence_overlay_alertitem_btnPan)
                displayBreach();
            else if (id == R.id.geofence_overlay_alertitem_btnDelete)
                onLongClick(v);
        }

        private void displayBreach() {
            if (_alert == null || !_alert.isValid()) {
                Log.w(TAG, "Cannot display breach for invalid alert");
                return;
            }

            final GeoPoint detectedPoint = _alert.getDetectedPoint();
            final String timeString = _sdf.format(new Date(_alert
                    .getTimestamp()));

            //see if we have item's current point and detected breach point
            if (_alert.getItem() != null
                    && _alert.getItem().getPoint() != null &&
                    _alert.getItem().getPoint().isValid()
                    && detectedPoint != null && detectedPoint.isValid()) {

                //see if item is still at breach point
                if (_alert.getItem().getPoint().equals(detectedPoint)
                        && _alert.getItem().getGroup() != null) {
                    String callsign = ATAKUtilities
                            .getDisplayName(_alert.getItem());
                    //still there just zoom
                    String message = _context.getString(
                            R.string.geofence_overlay_still_detected_at_breach_point,
                            callsign);
                    Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
                    Log.d(TAG, message);
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            "com.atakmap.android.maps.ZOOM_TO_LAYER")
                                    .putExtra("point",
                                            detectedPoint.toString()));
                } else {
                    //item has moved from breach point, create breach point if it does not exist
                    String uid = getBreachMarkerUID(_alert);
                    String callsign = ATAKUtilities
                            .getDisplayName(_alert.getItem());
                    String rabUUID = UUID.randomUUID().toString();

                    Marker breachMarker = null;
                    MapItem item = GeoFenceComponent.getMapGroup()
                            .deepFindUID(uid);
                    if (item instanceof Marker)
                        breachMarker = (Marker) item;
                    if (breachMarker == null) {
                        //wrap a temp marker (not persisted)
                        Log.d(TAG, "Creating alert breach marker");

                        //Note, we dont currently use PlacePointTool b/c these points dont persist
                        breachMarker = new Marker(uid);
                        //m.setMetaString("entry", "user");
                        breachMarker.setMetaBoolean("nevercot", true);
                        breachMarker.setMetaBoolean("editable", false);
                        breachMarker.setMovable(true);
                        breachMarker.setMetaBoolean("removable", true);
                        breachMarker.setMetaString("how", "m-g");
                        String title = _context.getString(
                                R.string.geofence_breach_marker_title, callsign,
                                (_alert.isEntered() ? _context.getString(
                                        R.string.entered_lower)
                                        : _context.getString(
                                                R.string.exited_lower)),
                                ATAKUtilities.getDisplayName(ATAKUtilities
                                        .findAssocShape(_fenceShape)),
                                timeString);
                        breachMarker.setTitle(title);
                        breachMarker.setMetaString("callsign", title);
                        breachMarker
                                .setMetaString(
                                        "remarks",
                                        callsign
                                                + (_alert.isEntered()
                                                        ? " entered"
                                                        : " exited")
                                                + " Geo Fence '"
                                                +
                                                ATAKUtilities.getDisplayName(
                                                        ATAKUtilities
                                                                .findAssocShape(
                                                                        _fenceShape))
                                                + "' at "
                                                + KMLUtil.KMLDateTimeFormatter
                                                        .get()
                                                        .format(
                                                                new Date(
                                                                        _alert.getTimestamp())));
                        breachMarker.setMetaString("menu",
                                "menus/geofence-breach.xml");
                        breachMarker.setMetaString("itemUid", _alert
                                .getItem().getUID());
                        breachMarker.setMetaString("randbUid", rabUUID);
                        breachMarker.setPoint(detectedPoint);
                        breachMarker.setMetaBoolean("readiness", true);
                        breachMarker.setMetaBoolean("archive", false);
                        breachMarker
                                .setType(GeoFenceComponent.BREACH_MARKER_TYPE);
                        Icon.Builder builder = new Icon.Builder();
                        builder.setImageUri(0,
                                "asset:/icons/geofence.png");
                        breachMarker.setIcon(builder.build());
                        GeoFenceComponent.getMapGroup().addItem(
                                breachMarker);
                        breachMarker.refresh(_view.getMapEventDispatcher(),
                                null,
                                this.getClass());

                        //remove pairing line when marker is deleted
                        breachMarker.addOnGroupChangedListener(
                                new MapItem.OnGroupChangedListener() {
                                    @Override
                                    public void onItemAdded(MapItem item,
                                            MapGroup group) {
                                    }

                                    @Override
                                    public void onItemRemoved(MapItem item,
                                            MapGroup group) {
                                        if (item == null)
                                            return;
                                        //also remove the R&B line
                                        AtakBroadcast.getInstance()
                                                .sendBroadcast(new Intent(
                                                        RangeAndBearingReceiver.DESTROY)
                                                                .putExtra("id",
                                                                        item.getMetaString(
                                                                                "randbUid",
                                                                                null)));

                                    }
                                });

                        //wrap transient pairing line from item to breach marker if
                        //offending marker has not been removed
                        if (_alert.getItem().getGroup() != null) {
                            RangeAndBearingMapItem rab = RangeAndBearingMapItem
                                    .createOrUpdateRABLine(rabUUID,
                                            breachMarker,
                                            _alert.getItem(), false);
                            if (rab != null) {
                                rab.setMetaBoolean("disable_polar", true);
                                RangeAndBearingMapComponent.getGroup()
                                        .addItem(rab);
                            }
                        }
                    }

                    double bearing = GeoCalculations.bearingTo(detectedPoint,
                            _alert
                                    .getItem().getPoint());

                    double distance = GeoCalculations.distanceTo(detectedPoint,
                            _alert
                                    .getItem().getPoint());
                    String dirString = SpanUtilities.formatType(
                            Span.METRIC, distance, Span.METER)
                            + " "
                            +
                            DirectionType.getDirection(bearing)
                                    .getAbbreviation();

                    String message;
                    if (_alert.getItem().getGroup() == null) {
                        //offending marker has been removed
                        if (_alert.getItem().getPoint()
                                .equals(detectedPoint)) {
                            //last seen at breach point
                            message = _context.getString(
                                    R.string.geofence_removed_marker_last_seen,
                                    callsign);
                        } else {
                            //had moved from breach point
                            message = _context.getString(
                                    R.string.geofence_removed_marker_had_moved,
                                    callsign, dirString);
                        }
                    } else {
                        message = _context.getString(
                                R.string.geofence_moved_marker,
                                callsign, dirString);
                    }

                    Toast.makeText(_context, message,
                            Toast.LENGTH_LONG).show();
                    Log.d(TAG, message);

                    //finally zoom map to breach point
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            "com.atakmap.android.maps.ZOOM_TO_LAYER")
                                    .putExtra("point",
                                            detectedPoint.toString()));
                }
            } else if (detectedPoint != null) {
                //just zoom
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        "com.atakmap.android.maps.ZOOM_TO_LAYER")
                                .putExtra("point", detectedPoint.toString()));
            }
        }

        @Override
        public boolean onLongClick(View view) {
            //Log.d(TAG, "onLongClick");
            zoomItem(false);

            final GeoFenceMonitor monitor = _manager.getMonitor(_item.getUID());
            if (monitor == null) {
                Log.w(TAG, "Unable to find monitor for: " + _item.getUID());
                return false;
            }

            long diff = new CoordinatedTime().getMilliseconds()
                    - _alert.getTimestamp();
            String timeDiff = MathUtils.GetTimeRemainingString(diff);

            final AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(this.getTitle());
            b.setMessage(_context.getString(
                    R.string.geofence_listmodel_longclick_message,
                    _alert.isEntered() ? _context.getString(R.string.entered)
                            : _context.getString(R.string.exited),
                    ATAKUtilities.getDisplayName(
                            ATAKUtilities.findAssocShape(_fenceShape)),
                    timeDiff,
                    KMLUtil.KMLDateTimeFormatter.get().format(new Date(_alert
                            .getTimestamp()))));
            b.setPositiveButton(R.string.dismiss,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            //Log.d(TAG, "Dismissing alert for: " + _item.getUID() + ", " + _pointMapItem.getUID());
                            _manager.dismiss(monitor, _alert, false);
                            GeoFenceListModel.this.requestRefresh();
                        }
                    });
            b.setNeutralButton(R.string.stop,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            //Log.d(TAG, "Dismissing alert & stop monitoring for: " + _item.getUID() + ", " + _pointMapItem.getUID());
                            _manager.dismiss(monitor, _alert, true);
                            GeoFenceListModel.this.requestRefresh();
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();

            return true;
        }

        @Override
        public boolean delete() {
            final GeoFenceMonitor monitor = _manager.getMonitor(_item.getUID());
            if (monitor == null)
                return false;
            _manager.dismiss(monitor, _alert, false);
            return true;
        }
    }

    public static String getBreachMarkerUID(GeoFenceAlerting.Alert alert) {
        if (alert == null)
            return "";

        return alert.getItem().getUID() + alert.getTimestamp();
    }

    private static class ListModelExtraHolder {
        ImageButton details, delete;
    }

    private static class AlertExtraHolder {
        ImageButton pan, delete;
    }
}
