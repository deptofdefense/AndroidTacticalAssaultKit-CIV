
package com.atakmap.android.geofence.component;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import android.view.View;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.AdapterView;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.geofence.data.GeoFenceConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.geofence.alert.GeoFenceAlerting;
import com.atakmap.android.geofence.data.GeoFence;
import com.atakmap.android.geofence.data.GeoFence.MonitoredTypes;
import com.atakmap.android.geofence.data.GeoFenceDatabase;
import com.atakmap.android.geofence.data.ShapeUtils;
import com.atakmap.android.geofence.monitor.GeoFenceMonitor;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Map;
import java.util.Set;

/**
 * Intent receiver for Geo Fence Component
 */
public class GeoFenceReceiver extends BroadcastReceiver implements
        GeoFenceComponent.GeoFenceListener {
    private static final String TAG = "GeoFenceReceiver";

    public static final String EDIT = "com.atakmap.android.geofence.EDIT";
    public static final String DISPLAY_ALERTING = "com.atakmap.android.geofence.DISPLAY_ALERTING";
    public static final String ITEMS_SELECTED = "com.atakmap.android.geofence.ITEMS_SELECTED";

    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = false)
    public static final String ADD = "com.atakmap.android.geofence.ADD";
    public static final String GEO_FENCE = "Geo Fence";
    private final SharedPreferences _prefs;
    private final MapGroup _group;

    private static final int GEO_FENCE_WITHIN_KM = 75;
    private int _rangeKM;

    private GeoFenceDatabase _database;
    private GeoFenceComponent _component;

    //UI components for current GeoFence dialog
    private TextView textViewGeoFenceTitle;
    private TextView textViewGeoFenceName;
    private EditText minElevFtMSL;
    private EditText maxElevFtMSL;
    private Switch switchGeoFenceStatus;
    private Spinner spinnerGeoFenceTrigger;
    private Spinner spinnerGeoFenceTrackedTypes;

    /**
     * Track fences with which the user has interacted with since starting ATAK
     */
    private Set<String> _processedFences;

    public GeoFenceReceiver(GeoFenceComponent component,
            GeoFenceDatabase database) {
        _component = component;
        _group = component.getMapView().getRootGroup();
        _component.addGeoFenceChangedListener(this);
        _database = database;
        _processedFences = new HashSet<>();
        _prefs = PreferenceManager.getDefaultSharedPreferences(component
                .getMapView().getContext());
    }

    public void dispose() {
        if (_processedFences != null) {
            _processedFences.clear();
            _processedFences = null;
        }
        _component = null;
        _database = null;
    }

    @Override
    public void onReceive(Context ignoreCtx, Intent intent) {

        final Context context = _component.getMapView().getContext();

        if (EDIT.equals(intent.getAction())) {
            String uid = intent.getStringExtra("uid");
            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Unable to edit GeoFence w/no UID");
                return;
            }

            MapItem item = ShapeUtils.getReferenceShape(
                    _component.getMapView(), true, uid,
                    _group, ShapeUtils.getShapeUID(intent));
            if (item == null) {
                Log.w(TAG, "Unable to find fence map item: " + uid);
                return;
            }

            displayGeofence(context, item);
        } else if (DISPLAY_ALERTING.equals(intent.getAction())) {
            if (_database.getCount() < 1) {
                Log.d(TAG, "No alerts to display");
                Toast.makeText(context,
                        R.string.no_geo_fences_found,
                        Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(TAG, "DISPLAY_ALERTING");

            //Have Overlay Manager auto nav down into GeoFences
            ArrayList<String> overlayPaths = new ArrayList<>();
            overlayPaths.add(MapView.getMapView().getContext()
                    .getString(R.string.alerts));
            overlayPaths.add(MapView.getMapView().getContext()
                    .getString(R.string.geo_fences));
            //if there is a single geofence alerting, go ahead an nav into it, otherwise Nav
            //to list of fences
            Map<GeoFenceMonitor, List<GeoFenceAlerting.Alert>> alerts = _component
                    .getAlerting().getAlerts();
            if (alerts != null && alerts.size() == 1) {
                Map.Entry<GeoFenceMonitor, List<GeoFenceAlerting.Alert>> monitor = alerts
                        .entrySet().iterator().next();
                if (monitor != null) {
                    List<GeoFenceAlerting.Alert> alertItems = monitor
                            .getValue();
                    if (!FileSystemUtils.isEmpty(alertItems)) {
                        String mapItemUid = monitor.getKey().getMapItemUid();
                        Log.d(TAG, "Displaying alerting on: " + mapItemUid);
                        overlayPaths.add(mapItemUid);
                    }
                }
            }

            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    HierarchyListReceiver.MANAGE_HIERARCHY)
                            .putStringArrayListExtra("list_item_paths",
                                    overlayPaths)
                            .putExtra("isRootList", true));

        } else if (ITEMS_SELECTED.equals(intent.getAction())) {
            Log.d(TAG, "ITEMS_SELECTED");
            String monitorUid = intent.getStringExtra("monitorUid");
            if (FileSystemUtils.isEmpty(monitorUid)) {
                Log.w(TAG, "ITEMS_SELECTED but no monitor UID");
                return;
            }
            List<String> uids;
            if (intent.hasExtra("itemUIDs"))
                uids = new ArrayList<>(Arrays.asList(intent
                        .getStringArrayExtra("itemUIDs")));
            else
                uids = intent.getStringArrayListExtra("uids");
            if (!FileSystemUtils.isEmpty(uids))
                _component.getManager().onItemsSelected(monitorUid, uids);
        } else if (ADD.equals(intent.getAction())) {
            //get event and convert to a GeoFence
            CotEvent event = intent.getParcelableExtra("cotevent");
            if (event == null || !event.isValid()) {
                Log.w(TAG, "Unable to add GeoFence without CoT Event");
                return;
            }

            GeoFence fence = GeoFence.fromCot(event);
            if (fence == null || !fence.isValid()) {
                Log.w(TAG, "Unable to add invalid received GeoFence");
                return;
            }

            //do not start auto monitoring for received fences
            fence.setTracking(false);

            MapItem item = ShapeUtils.getReferenceShape(
                    _component.getMapView(), true,
                    fence.getMapItemUid(), _group,
                    ShapeUtils.getShapeUID(intent));
            if (item == null) {
                Log.w(TAG, "Unable to find fence map item for received fence: "
                        + fence.getMapItemUid());
                return;
            }

            //Store in DB
            Log.d(TAG, "Adding received GeoFence: " + fence);
            GeoFenceDatabase.InsertOrUpdateResult result = _database
                    .insertOrUpdate(fence);
            switch (result) {
                case Insert:
                    _component.dispatchGeoFenceAdded(fence, item);
                    break;
                case Updated:
                    //TODO what if geofence already exists and is being monitored?
                    _component.dispatchGeoFenceChanged(fence, item);
                    break;
                case AlreadyUpToDate:
                    break;
                default:
                case Failure:
                    Log.w(TAG,
                            "Failed to insert or update: " + fence);
            }

            //now notify user
            switch (result) {
                case Insert:
                case Updated:
                    String message = String
                            .format(
                                    context.getString(
                                            R.string.geofence_received_shape),
                                    ATAKUtilities.getDisplayName(ATAKUtilities
                                            .findAssocShape(item)));
                    NotificationUtil.getInstance().postNotification(
                            R.drawable.ic_menu_geofence, NotificationUtil.WHITE,
                            context.getString(R.string.geo_fence_received),
                            message, message,
                            ShapeUtils.getZoomShapeIntent(item));
            }
        }
    }

    ClearContentRegistry.ClearContentListener ccl = new ClearContentRegistry.ClearContentListener() {
        @Override
        public void onClearContent(boolean clearmaps) {
            _database.clearAll();
        }
    };

    private void displayGeofence(final Context context, final MapItem item) {
        //see if this is a new GeoFence, or an edit
        GeoFence fence = _database.getGeoFence(item.getUID(), false);
        GeoFenceMonitor monitor = _component.getManager().getMonitor(
                item.getUID());

        // Treat fence as new if it's custom without any monitored items
        if (fence != null && fence.getMonitoredTypes() == MonitoredTypes.Custom
                && (monitor == null || !monitor.hasTrackedItems())) {
            _component.getManager().deleteMonitor(fence.getMapItemUid());
            fence = null;
            monitor = null;
        }

        final boolean bEdit;
        if (fence == null || !fence.isValid()) {
            Log.d(TAG, "displayGeofence new: " + item.getUID());
            bEdit = false;
            fence = new GeoFence(item, _prefs.getInt(
                    "numKmEntryRadiusComparison",
                    GeoFence.DEFAULT_ENTRY_RADIUS_KM));
        } else {
            Log.d(TAG, "displayGeofence edit: " + item.getUID());
            bEdit = true;
            fence.setTracking(_component.getManager().isTracking(fence));
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        final View view = inflater.inflate(R.layout.geo_fence, null);

        textViewGeoFenceTitle = view
                .findViewById(R.id.textViewGeoFenceTitle);
        textViewGeoFenceTitle.setText(bEdit ? context
                .getString(R.string.edit_geo_fence)
                : context.getString(R.string.create_geo_fence));
        textViewGeoFenceName = view
                .findViewById(R.id.textViewGeoFenceName);
        textViewGeoFenceName.setText(ATAKUtilities
                .getDisplayName(ATAKUtilities.findAssocShape(item)));
        switchGeoFenceStatus = view
                .findViewById(R.id.switchGeoFenceStatus);
        switchGeoFenceStatus.setChecked(fence.isTracking());
        spinnerGeoFenceTrigger = view
                .findViewById(R.id.spinnerGeoFenceTrigger);

        spinnerGeoFenceTrigger.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view,
                            int position, long id) {

                        if (view instanceof TextView)
                            ((TextView) view).setTextColor(Color.WHITE);
                    }
                });

        GeoFence.Trigger[] geofence_triggers = GeoFence.Trigger.values();
        for (int i = 0; i < geofence_triggers.length; i++) {
            if (fence.getTrigger().toString()
                    .equals(geofence_triggers[i].toString())) {
                spinnerGeoFenceTrigger.setSelection(i);
                break;
            }
        }

        spinnerGeoFenceTrackedTypes = view
                .findViewById(R.id.spinnerGeoFenceTrackedTypes);

        spinnerGeoFenceTrackedTypes.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view,
                            int position, long id) {

                        if (view instanceof TextView)
                            ((TextView) view).setTextColor(Color.WHITE);
                    }
                });

        MonitoredTypes[] geofence_tracked_types = MonitoredTypes.values();
        for (int i = 0; i < geofence_tracked_types.length; i++) {
            if (fence.getMonitoredTypes().toString()
                    .equals(geofence_tracked_types[i].toString())) {
                spinnerGeoFenceTrackedTypes.setSelection(i);
                break;
            }
        }

        //Detect the type of shape and get its furthest point from
        // center add 75km and make that the new range
        int range = GEO_FENCE_WITHIN_KM;
        if (item instanceof Rectangle) {
            Rectangle rect = (Rectangle) item;
            double length = rect.getLength() / 2;
            double width = rect.getWidth() / 2;
            range += (Math.sqrt((length * length) + (width * width)) / 1000L);
        } else if (item instanceof DrawingCircle) {
            DrawingCircle circle = (DrawingCircle) item;
            int numRings = circle.getNumRings();
            if (numRings < 1 || numRings > DrawingCircle.MAX_RINGS) {
                Log.w(TAG, "Changing default number of rings to 1 from: "
                        + numRings);
                numRings = 1;
            }
            // get radius of outer circle
            range += (circle.getRadius() * numRings) / 1000L;
        } else if (item instanceof DrawingShape) {
            DrawingShape freeform = (DrawingShape) item;
            double furthestRange = 0;
            for (GeoPoint point : freeform.getPoints()) {
                double distance = GeoCalculations
                        .distanceTo(freeform.getCenter().get(), point);
                if (distance > furthestRange) {
                    furthestRange = distance;
                }
            }
            range += furthestRange / 1000L;
        } else {
            Log.w(TAG, "displayGeofence: Shape Type Not Matched");
        }
        _rangeKM = range;
        fence.setRangeKM(range);

        GeoPointMetaData center = ShapeUtils.getShapeCenter(item);

        minElevFtMSL = view.findViewById(
                R.id.editTextGeoFenceAboveFt);
        setElevationText(minElevFtMSL, fence.getMinElevation(), center);

        maxElevFtMSL = view.findViewById(
                R.id.editTextGeoFenceBelowFt);
        setElevationText(maxElevFtMSL, fence.getMaxElevation(), center);

        final TextView textViewNumTracking = view
                .findViewById(R.id.textViewNumTracking);
        int size = monitor == null ? 0 : monitor.size();
        textViewNumTracking
                .setText(context.getString(R.string.number_tracking, size));
        final TextView textViewNumAlerts = view
                .findViewById(R.id.textViewNumAlerts);
        final Button buttonView = view
                .findViewById(R.id.buttonViewAlerts);
        final ImageButton buttonDismiss = view
                .findViewById(R.id.buttonDismissAlerts);

        //TODO update icon Grey/Green/Red based on isTracking & #alerts
        //final ImageView imageViewGeoFenceStatus = (ImageView) view.findViewById(R.id.imageViewGeoFenceStatus);

        final List<GeoFenceAlerting.Alert> numAlerts = _component.getAlerting()
                .getAlerts(item.getUID());
        if (FileSystemUtils.isEmpty(numAlerts)) {
            textViewNumAlerts.setVisibility(TextView.GONE);
            buttonView.setVisibility(Button.GONE);
            buttonDismiss.setVisibility(Button.GONE);
        } else {
            textViewNumAlerts.setText(context.getString(R.string.number_alerts,
                    numAlerts.size()));
            textViewNumAlerts.setTextColor(Color.RED);
            textViewNumAlerts.setVisibility(TextView.VISIBLE);
            buttonView.setVisibility(Button.VISIBLE);
            buttonDismiss.setVisibility(Button.VISIBLE);
        }

        final GeoFence finalFence = fence;
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setNeutralButton(R.string.send,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        save(finalFence, item);

                        //get fence from UI
                        GeoFence updated = getUIFence(item.getUID(), true);
                        if (updated == null || !updated.isValid()) {
                            Log.w(TAG, "Unable to load fence from UI");
                            return;
                        }

                        Log.d(TAG, "Sending CoT GeoFence via Contact List");
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                ContactPresenceDropdown.SEND_LIST)
                                        .putExtra("targetUID", item.getUID()));
                    }
                });
        b.setNegativeButton(R.string.delete2,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        AlertDialog.Builder b = new AlertDialog.Builder(
                                context);
                        b.setTitle(R.string.confirm_delete);
                        b.setMessage(R.string.delete_geo_fence_inquiry);
                        b.setPositiveButton(R.string.delete2,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d,
                                            int i) {
                                        GeoFence updated = getUIFence(
                                                item.getUID(), false);
                                        if (updated == null
                                                || !updated.isValid()) {
                                            Log.w(TAG,
                                                    "Unable to load fence from UI");
                                            return;
                                        }
                                        _component.getManager()
                                                .deleteMonitor(item.getUID());
                                    }
                                });
                        b.setNegativeButton(R.string.cancel, null);
                        b.show();
                    }
                });
        b.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        save(finalFence, item);
                    }
                });

        b.setView(view);
        final AlertDialog d = b.show();

        buttonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                d.dismiss();

                //Have Overlay Manager auto nav down into GeoFences
                ArrayList<String> overlayPaths = new ArrayList<>();
                overlayPaths.add(context.getString(R.string.alerts));
                overlayPaths.add(context.getString(R.string.geo_fences));
                overlayPaths.add(item.getUID());
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        HierarchyListReceiver.MANAGE_HIERARCHY)
                                .putStringArrayListExtra("list_item_paths",
                                        overlayPaths)
                                .putExtra("isRootList", true));
            }
        });

        buttonDismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                d.dismiss();

                final GeoFenceMonitor monitor = _component.getManager()
                        .getMonitor(item.getUID());
                if (monitor == null) {
                    Log.w(TAG,
                            "Unable to find monitor for: "
                                    + item.getUID());
                    Toast.makeText(_component.getMapView().getContext(),
                            context.getString(R.string.not_monitoring),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                //dismiss or stop monitoring
                AlertDialog.Builder b = new AlertDialog.Builder(context);
                b.setTitle(R.string.quick_dismiss);
                b.setMessage(context.getString(
                        R.string.geofence_quick_dismiss_inquiry,
                        ATAKUtilities.getDisplayName(
                                ATAKUtilities.findAssocShape(item)),
                        numAlerts.size()));
                b.setPositiveButton(R.string.dismiss_alerts,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                Log.d(TAG, "Dismissing alerts for: "
                                        + monitor);
                                _component.getManager().dismiss(monitor, false);
                            }
                        });
                b.setNeutralButton(R.string.view_alerts,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                //Have Overlay Manager auto nav down into GeoFences
                                ArrayList<String> p = new ArrayList<>();
                                p.add(context.getString(R.string.alerts));
                                p.add(context.getString(R.string.geo_fences));
                                p.add(item.getUID());
                                AtakBroadcast.getInstance()
                                        .sendBroadcast(new Intent(
                                                HierarchyListReceiver.MANAGE_HIERARCHY)
                                                        .putStringArrayListExtra(
                                                                "list_item_paths",
                                                                p)
                                                        .putExtra("isRootList",
                                                                true));
                            }
                        });
                b.setNegativeButton(R.string.cancel, null);
                b.show();
            }
        });

        d.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // Delete temp fence
                if (!bEdit)
                    _component.getManager().deleteMonitor(item.getUID());
            }
        });
    }

    /**
     * Show elevation in ft MSL
     * @param tv Text view
     * @param elevation Elevation in meters HAE
     */
    private void setElevationText(TextView tv, double elevation,
            GeoPointMetaData center) {
        if (center == null || Double.isNaN(elevation)) {
            tv.setText("");
            return;
        }
        double msl = EGM96.getMSL(center.get().getLatitude(),
                center.get().getLongitude(), elevation);
        tv.setText(String.valueOf(Math.round(SpanUtilities.convert(msl,
                Span.METER, Span.FOOT))));
    }

    // Reverse of above
    private double getElevationText(TextView tv, GeoPointMetaData center) {
        String text = tv.getText().toString();
        if (FileSystemUtils.isEmpty(text) || center == null)
            return Double.NaN;
        try {
            double msl = SpanUtilities.convert(Double.parseDouble(text),
                    Span.FOOT, Span.METER);
            return EGM96.getHAE(center.get().getLatitude(),
                    center.get().getLongitude(), msl);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * Save fence currently in UI/dialog
     * @param fence
     * @param item
     */
    private void save(GeoFence fence, MapItem item) {
        GeoFence updated = getUIFence(item.getUID(), true);
        if (updated == null || !updated.isValid()) {
            Log.w(TAG, "Unable to load fence from UI");
            return;
        }

        //if unchanged, no work to do. Unless this is the first time user
        //has accessed the fence, in that case need to pass it off for
        //monitoring
        if (updated.equals(fence) && _processedFences.contains(item.getUID())) {
            Log.d(TAG, "Geofence unchanged: " + fence);
            return;
        }

        _processedFences.add(item.getUID());
        GeoFenceDatabase.InsertOrUpdateResult result = _database
                .insertOrUpdate(updated);

        switch (result) {
            case Insert:
                _component.dispatchGeoFenceAdded(updated, item);
                break;
            case Updated:
                _component.dispatchGeoFenceChanged(updated, item);
                break;
            default:
            case Failure:
                Log.w(TAG, "Failed to insert or update: " + updated);
        }

        Log.d(TAG, "persisting uid: " + item.getUID());
        Log.d(TAG, "persisting type: " + item.getType());

        item.persist(_component.getMapView().getMapEventDispatcher(), null,
                this.getClass());

        //now update pref
        _prefs.edit()
                .putInt("numKmEntryRadiusComparison", updated.getRangeKM())
                .apply();
    }

    /**
     * UI thread only as it toasts
     * @param uid
     * @param bToast
     * @return
     */
    private GeoFence getUIFence(String uid, boolean bToast) {
        MapItem item = _component.getMapView().getRootGroup().deepFindUID(uid);
        if (item == null)
            return null;

        String temp = (String) spinnerGeoFenceTrigger.getSelectedItem();
        GeoFence.Trigger trigger = GeoFence.Trigger.Entry;
        try {
            trigger = GeoFence.Trigger.valueOf(temp);
        } catch (Exception e) {
            Log.w(TAG, "Invalid trigger: " + temp, e);
        }

        temp = (String) spinnerGeoFenceTrackedTypes.getSelectedItem();
        MonitoredTypes tt = MonitoredTypes.All;
        try {
            tt = MonitoredTypes.valueOf(temp.replaceAll(" ", ""));
        } catch (Exception e) {
            Log.w(TAG, "Invalid TrackedTypes: " + temp, e);
        }

        Context ctx = _component.getMapView().getContext();

        int rangeKM = _rangeKM;
        try {
            if (rangeKM < 1) {
                Log.w(TAG, "Using default Entry radius for: " + rangeKM);
                rangeKM = GeoFence.DEFAULT_ENTRY_RADIUS_KM;
                if (bToast)
                    Toast.makeText(ctx, ctx.getString(
                            R.string.using_default_radius, rangeKM),
                            Toast.LENGTH_LONG).show();
            }
            if (rangeKM > GeoFence.MAX_ENTRY_RADIUS_KM) {
                Log.w(TAG, "Using max Entry radius for: " + rangeKM);
                rangeKM = GeoFence.MAX_ENTRY_RADIUS_KM;
                if (bToast)
                    Toast.makeText(ctx, ctx.getString(
                            R.string.using_max_radius, rangeKM),
                            Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.w(TAG, "Invalid withinKM: "
                    + GEO_FENCE_WITHIN_KM, e);

            rangeKM = GeoFence.DEFAULT_ENTRY_RADIUS_KM;
            if (bToast)
                Toast.makeText(ctx, ctx.getString(
                        R.string.using_default_radius, rangeKM),
                        Toast.LENGTH_LONG).show();
        }

        GeoPointMetaData center = ShapeUtils.getShapeCenter(item);
        double minElev = getElevationText(minElevFtMSL, center);
        double maxElev = getElevationText(maxElevFtMSL, center);

        if (minElev < GeoFence.MIN_ELEVATION) {
            minElev = Double.NaN;
            if (bToast)
                Toast.makeText(ctx, R.string.geofence_value_too_low,
                        Toast.LENGTH_LONG).show();
        }
        if (maxElev > GeoFence.MAX_ELEVATION) {
            maxElev = Double.NaN;
            if (bToast)
                Toast.makeText(ctx, R.string.geofence_value_too_high,
                        Toast.LENGTH_LONG).show();
        }
        if (!Double.isNaN(minElev) && !Double.isNaN(maxElev)
                && minElev > maxElev) {
            double tmp = maxElev;
            maxElev = minElev;
            minElev = tmp;
            if (bToast)
                Toast.makeText(ctx, R.string.geofence_value_min_higher_than_max,
                        Toast.LENGTH_LONG).show();
        }

        // Mark monitor UIDs as empty so we know to prompt
        if (tt == MonitoredTypes.Custom)
            item.setMetaStringArrayList(
                    GeoFenceConstants.MARKER_MONITOR_UIDS,
                    new ArrayList<String>());

        // set elevationMonitored to always be true so that the appropriate details will be set
        // when sending for sending to a legacy device
        return new GeoFence(item, switchGeoFenceStatus.isChecked(), trigger,
                tt, rangeKM, minElev, maxElev);
    }

    @Override
    public void onFenceAdded(GeoFence fence, MapItem item) {
        //no-op
    }

    @Override
    public void onFenceChanged(GeoFence fence, MapItem item) {
        //no-op
    }

    @Override
    public void onFenceRemoved(String mapItemUid) {
        if (!FileSystemUtils.isEmpty(mapItemUid)
                && _processedFences.remove(mapItemUid)) {
            Log.d(TAG, "onFenceRemoved: " + mapItemUid);
        }
    }
}
