
package com.atakmap.android.toolbars;

import java.util.ArrayList;
import java.util.List;

import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.cot.importer.MarkerImporter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.log.Log;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;

public class RangeAndBearingMapComponent extends AbstractMapComponent {

    public static final String TAG = "RangeAndBearingMapComponent";

    private RangeAndBearingReceiver _rabReceiver;
    private BroadcastReceiver lineUnPinReceiver;
    protected RangeAndBearingDropDown _rabLineDropDown;
    protected BullseyeDropDownReceiver _rabBullseyeDropDown;
    protected RangeAndBearingCircleDropDown _rabCircleDropDown;
    private static MapGroup _rabGroup;
    private BroadcastReceiver toggle_loc;
    private final List<MapItemImporter> _importers = new ArrayList<>();

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(view.getContext());

        int ftToMiThresh = 5280;
        try {
            ftToMiThresh = Integer.parseInt(sp.getString(
                    "rng_feet_display_pref",
                    String.valueOf(5280)));
        } catch (NumberFormatException ignored) {
        }
        SpanUtilities.setFeetToMileThreshold(ftToMiThresh);

        int mToKmThresh = 2000;
        try {
            mToKmThresh = Integer.parseInt(sp.getString(
                    "rng_meters_display_pref",
                    String.valueOf(2000)));
        } catch (NumberFormatException ignored) {
        }
        SpanUtilities.setMetersToKilometersThreshold(mToKmThresh);

        synchronized (RangeAndBearingMapComponent.class) {
            _rabGroup = view.getRootGroup().findMapGroup(
                    "Range & Bearing");

            if (_rabGroup == null) {
                _rabGroup = new DefaultMapGroup("Range & Bearing");
                _rabGroup.setMetaBoolean("ignoreOffscreen", true);
                _rabGroup.setMetaBoolean("permaGroup", true);
                String iconUri = "android.resource://"
                        + view.getContext().getPackageName()
                        + "/" + R.drawable.ruler;
                view.getMapOverlayManager().addShapesOverlay(
                        new DefaultMapGroupOverlay(view, _rabGroup, iconUri));
            }
        }

        RangeAndBearingTool _rabt = new RangeAndBearingTool(view, null);

        _rabReceiver = new RangeAndBearingReceiver(view);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        //filter.addAction(RangeAndBearingReceiver.CREATE);
        //filter.addAction(RangeAndBearingReceiver.CREATE_UPDATE);
        //filter.addAction(RangeAndBearingReceiver.SHARE);
        filter.addAction(RangeAndBearingReceiver.DESTROY);
        //filter.addAction(RangeAndBearingReceiver.CIRCLE_DESTROY);
        //filter.addAction(RangeAndBearingReceiver.CIRCLE_DETAILS);
        //filter.addAction(RangeAndBearingReceiver.REVERSE);
        filter.addAction(RangeAndBearingReceiver.CHANGECOLOR);
        filter.addAction(RangeAndBearingReceiver.RANGE_UNITS);
        filter.addAction(RangeAndBearingReceiver.BEARING_UNITS);
        filter.addAction(RangeAndBearingReceiver.PIN_DYNAMIC);
        filter.addAction(RangeAndBearingReceiver.TOGGLE_SLANT_RANGE);
        AtakBroadcast.getInstance().registerReceiver(_rabReceiver, filter);

        Log.d(TAG, "Creating Listener");
        _rabLineDropDown = new RangeAndBearingDropDown(view);
        DocumentedIntentFilter rabLineDropDownFilter = new DocumentedIntentFilter();
        rabLineDropDownFilter
                .addAction("com.atakmap.android.maps.SHOW_RAB_LINE_DROPDOWN");
        AtakBroadcast.getInstance().registerReceiver(_rabLineDropDown,
                rabLineDropDownFilter);

        _rabCircleDropDown = new RangeAndBearingCircleDropDown(view);
        DocumentedIntentFilter rabCircleDropDownFilter = new DocumentedIntentFilter();
        rabCircleDropDownFilter
                .addAction("com.atakmap.android.maps.SHOW_RAB_CIRCLE_DROPDOWN");
        AtakBroadcast.getInstance().registerReceiver(_rabCircleDropDown,
                rabCircleDropDownFilter);

        _rabBullseyeDropDown = new BullseyeDropDownReceiver(view);
        DocumentedIntentFilter bullseyeDropDownFilter = new DocumentedIntentFilter();
        bullseyeDropDownFilter
                .addAction(BullseyeDropDownReceiver.DROPDOWN_TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().registerReceiver(_rabBullseyeDropDown,
                bullseyeDropDownFilter);

        DocumentedIntentFilter bullseyeRingToggleFilter = new DocumentedIntentFilter();
        bullseyeRingToggleFilter
                .addAction("com.atakmap.maps.bullseye.TOGGLE_RINGS");
        AtakBroadcast.getInstance().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                _rabBullseyeDropDown.toggleRings(intent);
            }
        }, bullseyeRingToggleFilter);

        DocumentedIntentFilter bullseyeDirectionToggleFilter = new DocumentedIntentFilter();
        bullseyeDirectionToggleFilter
                .addAction("com.atakmap.maps.bullseye.TOGGLE_DIRECTION");
        AtakBroadcast.getInstance().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                _rabBullseyeDropDown.toggleDirection(intent);
            }
        }, bullseyeDirectionToggleFilter);

        DocumentedIntentFilter bullseyeBearingToggleFilter = new DocumentedIntentFilter();
        bullseyeBearingToggleFilter
                .addAction("com.atakmap.maps.bullseye.TOGGLE_BEARING");
        AtakBroadcast.getInstance().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                _rabBullseyeDropDown.toggleBearing(intent);
            }
        }, bullseyeBearingToggleFilter);

        DocumentedIntentFilter rabLineUnpinFilter = new DocumentedIntentFilter();
        rabLineUnpinFilter
                .addAction("com.atakmap.android.maps.UNPIN_RAB_LINE");
        AtakBroadcast.getInstance().registerReceiver(
                lineUnPinReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String rabUUID = intent.getStringExtra("id");
                        if (rabUUID != null) {
                            RangeAndBearingMapItem line = RangeAndBearingMapItem
                                    .getRABLine(rabUUID);
                            if (line != null) {
                                String point = intent.getStringExtra("point");
                                if (point.equals("start")) {
                                    line.removePoint(line.getPoint1Item());
                                } else if (point.equals("end")) {
                                    line.removePoint(line.getPoint2Item());
                                }
                                try {
                                    line.persist(MapView.getMapView()
                                            .getMapEventDispatcher(), null,
                                            line.getClass());
                                } catch (Exception e) {
                                    Log.e(TAG, "error occurred", e);
                                    //Silently handle error
                                }
                            }
                        }
                    }
                }, rabLineUnpinFilter);

        DocumentedIntentFilter rabDistanceLockFilter = new DocumentedIntentFilter();
        rabDistanceLockFilter
                .addAction("com.atakmap.android.maps.RAB_DISTANCE_LOCK");
        AtakBroadcast.getInstance().registerReceiver(
                toggle_loc = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String rabUUID = intent.getStringExtra("id");
                        if (rabUUID != null) {
                            RangeAndBearingMapItem line = RangeAndBearingMapItem
                                    .getRABLine(rabUUID);
                            if (line != null)
                                line.toggleLock();
                        }
                    }
                }, rabDistanceLockFilter); //TODO Andrew Deregister this receiver!

        // Set up the Range & Bearing items to persist to the state saver.

        // R&B lines
        _importers.add(new RangeAndBearingImporter(view, _rabGroup));

        // R&B circles
        _importers.add(new RangeCircleImporter(view, _rabGroup));

        // Bullseye markers
        _importers.add(new MarkerImporter(view, _rabGroup,
                BullseyeTool.BULLSEYE_COT_TYPE, false));

        for (MapItemImporter imp : _importers)
            CotImporterManager.getInstance().registerImporter(imp);

        RangeAndBearingToolbar.getInstance(view);
    }

    @Override
    public void onDestroyImpl(Context context, MapView view) {
        for (MapItemImporter imp : _importers)
            CotImporterManager.getInstance().unregisterImporter(imp);
        AtakBroadcast.getInstance().unregisterReceiver(_rabReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(_rabLineDropDown);
        AtakBroadcast.getInstance().unregisterReceiver(_rabCircleDropDown);
        AtakBroadcast.getInstance().unregisterReceiver(_rabBullseyeDropDown);
        AtakBroadcast.getInstance().unregisterReceiver(lineUnPinReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(toggle_loc);
        try {
            RangeAndBearingToolbar.dispose();
        } catch (Exception e) {
            Log.e(TAG, "error occurred cleaning up R&B", e);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(Context context, MenuItem item) {
        return false;
    }

    @Override
    public void onOptionsMenuClosed(Context context, Menu menu) {
    }

    public static MapGroup getGroup() {
        return _rabGroup;
    }
}
