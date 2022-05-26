
package com.atakmap.android.resection;

import java.util.List;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.routes.MapClickTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

/**
 * The default resection workflow - shown in a drop-down
 */
public class ResectionDropDownReceiver extends DropDownReceiver
        implements ResectionWorkflow, View.OnClickListener {

    private static final String TAG = "ResectionDropDownReceiver";

    public static final String SHOW_DROPDOWN = "com.atakmap.android.resection.SHOW_DROPDOWN";
    public static final String SET_LANDMARK = "com.atakmap.android.resection.SET_LANDMARK";
    public static final String SET_BEARING = "com.atakmap.android.resection.SET_BEARING";

    private ResectionMapComponent.BackButtonCallback backButtonCallback = null;

    private final MapView _mapView;
    private final Context _context;
    private final ResectionMapManager _manager;

    private ImageButton _addBtn;
    private final ResectionLandmarkAdapter _adapter;

    private OnResectionResult resectionResultCallback;

    ResectionDropDownReceiver(MapView mapView) {
        super(mapView);
        setRetain(true);
        _mapView = mapView;
        _context = mapView.getContext();
        _manager = new ResectionMapManager(mapView);
        _adapter = new ResectionLandmarkAdapter(mapView, _manager);
        _manager.setAdapter(_adapter);

        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(SHOW_DROPDOWN, "Show the resection drop-down");
        f.addAction(SET_LANDMARK,
                "Broadcast when a landmark point has been selected",
                new DocumentedExtra[] {
                        new DocumentedExtra("point", "Landmark point string",
                                false, String.class)
                });
        f.addAction(SET_BEARING, "Set landmark bearing via dialog",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid", "Landmark UID",
                                false, String.class)
                });
        f.addAction(ToolManagerBroadcastReceiver.END_TOOL);
        AtakBroadcast.getInstance().registerReceiver(this, f);
    }

    @Override
    public String getName() {
        return "Resection";
    }

    @Override
    public String getDescription() {
        return _context.getString(R.string.resection_classic_tool_description);
    }

    @Override
    public String getIdealConditions() {
        return _context
                .getString(R.string.resection_classic_tool_ideal_conditions);
    }

    @Override
    public String getRelativeAccuracy() {
        return _context
                .getString(R.string.resection_classic_tool_relative_accuracy);
    }

    @Override
    public String getRequiredData() {
        return _context
                .getString(R.string.resection_classic_tool_required_data);
    }

    @Override
    public String getRequiredHardware() {
        return _context
                .getString(R.string.resection_classic_tool_required_hardware);
    }

    @Override
    public void start(OnResectionResult callback) {
        resectionResultCallback = callback;
        showDropDown();
    }

    @Override
    protected void disposeImpl() {
        AtakBroadcast.getInstance().unregisterReceiver(this);
        if (_manager != null)
            _manager.dispose();
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        String callsign = _mapView.getDeviceCallsign();

        // Show the drop-down
        switch (action) {
            case SHOW_DROPDOWN:
                showDropDown();
                break;

            // Add new landmark
            case SET_LANDMARK: {
                GeoPoint point = GeoPoint.parseGeoPoint(intent
                        .getStringExtra("point"));
                if (point == null || !point.isValid())
                    return;
                Marker m = _manager.addLandmark(point);
                ResectionBearingDialog d = new ResectionBearingDialog(_mapView);
                d.setTitle(_context.getString(R.string.resection_bearing_title,
                        callsign, _context.getString(
                                R.string.resection_new_landmark)));
                d.setTargetPoint(m.getPoint());
                d.setTag(m);
                d.show(_adapter);
                break;
            }
            case SET_BEARING: {
                String uid = intent.getStringExtra("uid");
                if (FileSystemUtils.isEmpty(uid))
                    return;
                MapItem item = _mapView.getRootGroup().deepFindUID(uid);
                if (!(item instanceof Marker))
                    return;
                Marker m = (Marker) item;
                ResectionBearingDialog d = new ResectionBearingDialog(_mapView);
                d.setTitle(_context.getString(R.string.resection_bearing_title,
                        callsign, m.getTitle()));
                d.setTargetPoint(m.getPoint());
                d.setTag(item);
                d.show(item.getMetaDouble("landmarkBearing", 0), _adapter);
                break;
            }

            // Stop highlighting button when tool ends
            case ToolManagerBroadcastReceiver.END_TOOL:
                if (FileSystemUtils.isEquals(intent.getStringExtra("tool"),
                        MapClickTool.TOOL_NAME)) {
                    if (_addBtn != null)
                        _addBtn.setSelected(false);
                }

                break;
        }
    }

    private void showDropDown() {
        if (isVisible())
            return;
        else if (!isClosed())
            closeDropDown();
        View v = LayoutInflater.from(_context).inflate(
                R.layout.resection_dropdown, null, false);

        ListView list = v.findViewById(R.id.landmark_list);
        list.setAdapter(_adapter);

        _addBtn = v.findViewById(R.id.add_landmark);
        _addBtn.setOnClickListener(this);

        v.findViewById(R.id.resection).setOnClickListener(this);

        v.findViewById(R.id.clear_resection).setOnClickListener(this);

        v.findViewById(R.id.panto_resection).setOnClickListener(this);

        TextView estLoc = v.findViewById(R.id.resection_location);
        _adapter.setEstimatedTV(estLoc);

        final ImageButton pantoResection = v
                .findViewById(R.id.panto_resection);

        // enable/disable panto button based off estimated location
        estLoc.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.toString().equals(""))
                    pantoResection.setEnabled(false);
                else
                    pantoResection.setEnabled(true);
            }
        });

        setRetain(true);

        if (isTablet())
            showDropDown(v, FIVE_TWELFTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    FIVE_TWELFTHS_HEIGHT);
        else
            showDropDown(v, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT);

        _adapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Create resection marker
        if (id == R.id.resection) {
            _manager.createInterMarker();
        }

        // Start landmark tool
        else if (id == R.id.add_landmark) {
            if (ToolManagerBroadcastReceiver.getInstance()
                    .getActiveTool() instanceof MapClickTool) {
                ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
                _addBtn.setSelected(false);
            } else {
                Bundle extras = new Bundle();
                extras.putParcelable("callback", new Intent(SET_LANDMARK));
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        MapClickTool.TOOL_NAME, extras);
                _addBtn.setSelected(true);
            }
        }

        // center map on intersection
        else if (id == R.id.panto_resection) {
            GeoPoint intersectionPoint = _manager.getIntersectionPoint();
            if (intersectionPoint != null) {
                CameraController.Programmatic.panTo(
                        _mapView.getRenderer3(), intersectionPoint, true);
            }
        }

        // Clear all landmarks (and lines)
        else if (id == R.id.clear_resection) {
            final List<Marker> landmarks = _manager.getLandmarks();
            if (landmarks.size() > 0) {

                AlertDialog.Builder b = new AlertDialog.Builder(MapView
                        .getMapView().getContext());
                b.setTitle(_context.getString(R.string.confirmation_dialogue))
                        // todo put this string in @strings
                        .setMessage("Remove all Landmarks?")
                        .setPositiveButton(R.string.yes,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface arg0,
                                            int arg1) {
                                        for (Marker lm : landmarks) {
                                            MapGroup group = lm.getGroup();
                                            if (group != null)
                                                group.removeItem(lm);
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null);

                AlertDialog d = b.create();
                d.show();
            }
        }
    }

    /**
     * Shows the available location estimates in the drop down
     */
    protected void showEstimatesDropDown(View view,
            ResectionMapComponent.BackButtonCallback callback) {
        backButtonCallback = callback;
        if (isTablet())
            showDropDown(view, DropDownReceiver.FIVE_TWELFTHS_WIDTH,
                    DropDownReceiver.FULL_HEIGHT,
                    DropDownReceiver.FULL_WIDTH,
                    DropDownReceiver.FIVE_TWELFTHS_HEIGHT);
        else
            showDropDown(view, DropDownReceiver.HALF_WIDTH,
                    DropDownReceiver.FULL_HEIGHT,
                    DropDownReceiver.FULL_WIDTH, DropDownReceiver.HALF_HEIGHT);
    }

    @Override
    protected boolean onBackButtonPressed() {
        if (backButtonCallback != null) {
            backButtonCallback.backButtonPressed();
        }

        // TODO: Should we only fire this callback explicitly with the resection button?
        if (isVisible() && resectionResultCallback != null) {
            ResectionLocationEstimate estimate = new ResectionLocationEstimate();
            estimate.setSource(getName());

            GeoPoint point = _manager.getIntersectionPoint();
            estimate.setPoint(point);

            resectionResultCallback.result(this, estimate);
            resectionResultCallback = null;
        }

        return super.onBackButtonPressed();
    }
}
