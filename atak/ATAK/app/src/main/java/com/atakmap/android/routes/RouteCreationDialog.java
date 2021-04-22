
package com.atakmap.android.routes;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.routes.routearound.RouteAroundRegionManager;
import com.atakmap.android.routes.routearound.RouteAroundRegionManagerView;
import com.atakmap.android.routes.routearound.RouteAroundRegionViewModel;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.geocode.GeocodingTask;
import com.atakmap.android.user.geocode.ReverseGeocodingTask;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * Dialog for creating routes
 */
public class RouteCreationDialog extends BroadcastReceiver implements
        View.OnClickListener {

    private static final String TAG = "RouteCreationDialog";
    private static final String MAP_CLICKED = "com.atakmap.android.maps.MAP_CLICKED";

    private static final List<Pair<String, GeoPointMetaData>> RECENT_ADDRESSES = new ArrayList<>();

    private final MapView _mapView;
    private final Context _context;
    private final SharedPreferences _prefs;
    private final CoordinateFormat _coordFormat;
    private final RouteMapReceiver _receiver;
    private final Route _route;
    private final String _unkAddr;

    // Address dialog
    private AlertDialog _addrDialog;
    private EditText _startAddr, _destAddr;
    private TextView _startCoord, _destCoord;
    private LinearLayout _routePlanOptions;

    private static RouteAroundRegionManager _routeAroundManager;
    private static RouteAroundRegionViewModel _routeAroundVM;
    private static LinearLayout _routeAroundOptions;

    private String _resolvedStartAddr, _resolvedDestAddr;
    private GeoPointMetaData _resolvedStartPoint, _resolvedDestPoint;
    private boolean _finishingLookup = false;
    private int _pendingLookups = 0;
    private List<Map.Entry<String, RoutePlannerInterface>> _planners;
    private RoutePlannerInterface _autoPlan;
    private final File recentlyUsed = FileSystemUtils
            .getItem("tools/route/recentlyused.txt");
    private final LayoutInflater _inflater;

    public RouteCreationDialog(final MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _receiver = RouteMapReceiver.getInstance();
        _route = _receiver.getNewRoute(UUID.randomUUID().toString());
        _unkAddr = _context.getString(R.string.unknown_address);
        _inflater = LayoutInflater.from(_context);
        _coordFormat = CoordinateFormat.find(_prefs.getString(
                "coord_display_pref", _context.getString(
                        R.string.coord_display_pref_default)));

        _routeAroundManager = RouteAroundRegionManager.getInstance();
        _routeAroundVM = new RouteAroundRegionViewModel(_routeAroundManager);
        _routeAroundOptions = (LinearLayout) _inflater
                .inflate(R.layout.route_around_layout, null);

        CheckBox avoidRouteAroundRegions = _routeAroundOptions
                .findViewById(R.id.chk_route_regions);
        CheckBox avoidGeofences = _routeAroundOptions
                .findViewById(R.id.chk_route_around_geo_fences);
        Button openRouteAroundManager = _routeAroundOptions
                .findViewById(R.id.btn_open_route_around);

        avoidRouteAroundRegions.setChecked(_prefs.getBoolean(
                RouteAroundRegionManagerView.OPT_AVOID_ROUTE_AROUND_REGIONS,
                false));
        avoidGeofences.setChecked(_prefs.getBoolean(
                RouteAroundRegionManagerView.OPT_AVOID_GEOFENCES, false));

        avoidRouteAroundRegions.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton x,
                            boolean checked) {
                        _prefs.edit().putBoolean(
                                RouteAroundRegionManagerView.OPT_AVOID_ROUTE_AROUND_REGIONS,
                                checked).apply();
                    }
                });

        avoidGeofences.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton x,
                            boolean checked) {
                        _prefs.edit().putBoolean(
                                RouteAroundRegionManagerView.OPT_AVOID_GEOFENCES,
                                checked).apply();
                    }
                });

        openRouteAroundManager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RouteAroundRegionManagerView regionManagerView = new RouteAroundRegionManagerView(
                        mapView,
                        new RouteAroundRegionViewModel(_routeAroundManager));
                AlertDialog dialog = new AlertDialog.Builder(_context)
                        .setTitle(R.string.manage_route_around_regions)
                        .setOnDismissListener(
                                new DialogInterface.OnDismissListener() {
                                    @Override
                                    public void onDismiss(
                                            DialogInterface dialog) {
                                        new RouteAroundRegionViewModel(
                                                _routeAroundManager)
                                                        .saveState();
                                    }
                                })
                        .setPositiveButton(R.string.done,
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        dialog.dismiss();
                                    }
                                })
                        .setView(regionManagerView.createView(_context, null))
                        .create();
                regionManagerView.setParentDialog(dialog);
                regionManagerView.setParentParentDialog(_addrDialog);
                dialog.show();
            }
        });

        loadRecentlyUsed();

        if (!IOProviderFactory.exists(recentlyUsed.getParentFile())
                && !IOProviderFactory.mkdirs(recentlyUsed
                        .getParentFile())) {
            Log.d(TAG, "error making: " + recentlyUsed.getParentFile());
        }
    }

    /**
     * Load the recently used lookups.
     */
    private void loadRecentlyUsed() {
        String line;
        RECENT_ADDRESSES.clear();

        if (IOProviderFactory.exists(recentlyUsed)) {
            try (InputStream is = IOProviderFactory
                    .getInputStream(recentlyUsed);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(is,
                                    FileSystemUtils.UTF8_CHARSET))) {
                while ((line = reader.readLine()) != null) {
                    String[] info = line.split("\t");
                    RECENT_ADDRESSES.add(new Pair<>(info[0],
                            GeoPointMetaData
                                    .wrap(GeoPoint.parseGeoPoint(info[1]))));
                }
            } catch (Exception e) {
                Log.e(TAG,
                        "Unable to load recently used lookups due to an error",
                        e);
            }
        }
    }

    private void saveRecentlyUsed() {
        // Trim down list to most recently used 10 addresses
        while (RECENT_ADDRESSES.size() > 10) {
            RECENT_ADDRESSES.remove(RECENT_ADDRESSES.size() - 1);
        }

        try (BufferedWriter bufferedWriter = new BufferedWriter(
                IOProviderFactory.getFileWriter(recentlyUsed))) {

            for (Pair<String, GeoPointMetaData> item : RECENT_ADDRESSES) {
                bufferedWriter.write(item.first + "\t" + item.second + "\n");
            }
        } catch (Exception e) {
            Log.e(TAG,
                    "Unable to save recently used addresses due to an exception: ",
                    e);
        }
        // Ignored
    }

    /** DEPRECIATED: This is the old API. Use the version with 5 arguments (which is actually static) */
    static void setupPlanSpinner(final Spinner spinner,
            final List<Entry<String, RoutePlannerInterface>> _planners,
            final LinearLayout routePlanOptions,
            final AlertDialog addrDialog) {
        setupPlanSpinner(spinner, _planners, routePlanOptions, addrDialog,
                _routeAroundOptions);
    }

    /** 
     * Sets up the action for creating a spinner for both the address dialog and the reverse dialog
     */
    static void setupPlanSpinner(final Spinner spinner,
            final List<Entry<String, RoutePlannerInterface>> _planners,
            final LinearLayout routePlanOptions,
            final AlertDialog addrDialog,
            final LinearLayout routeAroundOptions) {
        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView,
                    View selectedItemView, int position, long id) {

                if (selectedItemView instanceof TextView)
                    ((TextView) selectedItemView).setTextColor(Color.WHITE);

                LayoutParams lpView = new LayoutParams(LayoutParams.FILL_PARENT,
                        LayoutParams.WRAP_CONTENT);

                String plannerName = (String) spinner
                        .getItemAtPosition(position);
                routePlanOptions.removeAllViews();
                for (Entry<String, RoutePlannerInterface> k : _planners) {
                    if (plannerName != null && plannerName
                            .equals(k.getValue().getDescriptiveName())) {
                        routePlanOptions.addView(
                                k.getValue().getOptionsView(addrDialog),
                                lpView);
                        if (k.getValue().canRouteAroundRegions())
                            routePlanOptions.addView(routeAroundOptions);
                    }
                }
            }

        });

        String plannerName = (String) spinner.getSelectedItem();
        routePlanOptions.removeAllViews();
        for (Entry<String, RoutePlannerInterface> k : _planners) {
            if (plannerName != null
                    && plannerName.equals(k.getValue().getDescriptiveName())) {
                routePlanOptions
                        .addView(k.getValue().getOptionsView(addrDialog));
                if (k.getValue().canRouteAroundRegions())
                    routePlanOptions.addView(routeAroundOptions);
            }
        }

    }

    /**
     * Prompts the user to create a new route, then goes into edit mode
     * @param manual True for manual creation mode
     *               False to prompt user for address routing
     */
    public void show(boolean manual) {
        // Check if routing capability is installed in automatic mode
        if (!manual && showAddressDialog())
            return;

        // Show dialog to get basic details needed to start creating route
        AlertDialog.Builder b = getDetailsDialog(_route, _context, true);
        b.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        d.cancel();
                    }
                });
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                onFinish(null);
            }
        });
        AlertDialog ad = b.create();
        ad.show();
        ad.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(RouteMapReceiver.MANAGE_ACTION));
            }
        });
    }

    public void show() {
        show(false);
    }

    private void onFinish(RoutePlannerInterface rpi) {
        // Finalize route and show details
        _route.setMetaString("entry", "user");
        _route.setMetaBoolean("creating", true);
        _receiver.getRouteGroup().addItem(_route);
        _route.setVisible(true);
        _route.setColor(_prefs.getInt("route_last_selected_color",
                Color.WHITE));
        _receiver.showRouteDetails(_route, rpi, rpi == null);
        if (rpi == null) {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    DropDownManager.getInstance().hidePane();
                }
            });
        }
    }

    // Show the dialog for creating a route with a route planner.
    private boolean showAddressDialog() {
        // Load route planner interfaces into spinner
        MapComponent mc = ((MapActivity) _context).getMapComponent(
                RouteMapComponent.class);
        if (mc == null)
            return false;
        RoutePlannerManager routePlanner = ((RouteMapComponent) mc)
                .getRoutePlannerManager();
        _planners = new ArrayList<>(
                routePlanner.getRoutePlanners());
        if (FileSystemUtils.isEmpty(_planners))
            return false;

        final boolean network = RouteMapReceiver.isNetworkAvailable();
        if (!network)
            Toast.makeText(_context,
                    "network not available",
                    Toast.LENGTH_SHORT).show();

        List<String> plannerNames = new ArrayList<>();
        for (Entry<String, RoutePlannerInterface> k : _planners)
            if (!k.getValue().isNetworkRequired() || network)
                plannerNames.add(k.getValue().getDescriptiveName());

        if (FileSystemUtils.isEmpty(plannerNames))
            return false;

        Collections.sort(plannerNames, RoutePlannerView.ALPHA_SORT);

        View addressEntryView = LayoutInflater.from(_context).inflate(
                R.layout.route_address_entry, _mapView, false);
        View routePlanView = LayoutInflater.from(_context).inflate(
                R.layout.route_planner_options_layout, _mapView, false);

        LinearLayout topLevelLayout = addressEntryView
                .findViewById(R.id.top_level_layout);
        topLevelLayout.addView(routePlanView);

        _startAddr = addressEntryView.findViewById(R.id.route_start_address);
        _destAddr = addressEntryView.findViewById(R.id.route_dest_address);
        _startCoord = addressEntryView.findViewById(R.id.route_start_coord);
        _destCoord = addressEntryView.findViewById(R.id.route_dest_coord);
        _routePlanOptions = routePlanView
                .findViewById(R.id.route_plan_options);

        final Spinner planSpinner = routePlanView.findViewById(
                R.id.route_plan_method);

        if (!RouteMapReceiver.isNetworkAvailable()) {
            _startAddr.setVisibility(View.GONE);
            _destAddr.setVisibility(View.GONE);
        }
        addressEntryView.findViewById(R.id.route_start_address_clear)
                .setOnClickListener(this);
        addressEntryView.findViewById(R.id.route_dest_address_clear)
                .setOnClickListener(this);
        addressEntryView.findViewById(R.id.route_start_map_select)
                .setOnClickListener(this);
        addressEntryView.findViewById(R.id.route_dest_map_select)
                .setOnClickListener(this);
        addressEntryView.findViewById(R.id.route_start_address_history)
                .setOnClickListener(this);
        addressEntryView.findViewById(R.id.route_dest_address_history)
                .setOnClickListener(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(_context,
                R.layout.spinner_text_view_dark, plannerNames);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        planSpinner.setAdapter(adapter);

        // For resetting the text color to white after highlighting invalid address
        _startAddr.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                _startAddr.setTextColor(Color.WHITE);
                if (_resolvedStartPoint != null && s != null
                        && FileSystemUtils.isEquals(s.toString(),
                                _resolvedStartAddr)) {
                    String coordTxt = CoordinateFormatUtilities
                            .formatToString(_resolvedStartPoint.get(),
                                    _coordFormat);
                    _startCoord.setText(coordTxt);
                } else
                    _startCoord.setText("");
            }
        });
        _destAddr.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                _destAddr.setTextColor(Color.WHITE);
                if (_resolvedDestPoint != null && s != null
                        && FileSystemUtils.isEquals(s.toString(),
                                _resolvedDestAddr)) {
                    String coordTxt = CoordinateFormatUtilities
                            .formatToString(_resolvedDestPoint.get(),
                                    _coordFormat);
                    _destCoord.setText(coordTxt);
                } else
                    _destCoord.setText("");
            }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.routes_text9);
        b.setView(addressEntryView);
        b.setPositiveButton(R.string.create, null);
        b.setNeutralButton(R.string.route_plan_manual_entry,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        show(true);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);

        _addrDialog = b.create();

        setupPlanSpinner(planSpinner, _planners, _routePlanOptions,
                _addrDialog, _routeAroundOptions);

        _addrDialog.setCancelable(false);
        _addrDialog.show();
        _addrDialog.setOnDismissListener(
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dispose();
                    }
                });
        AtakBroadcast.getInstance().registerReceiver(this,
                new DocumentedIntentFilter(MAP_CLICKED,
                        "Map click returned by tool"));
        _addrDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Check input addresses
                        String startInput = _startAddr.getText().toString();
                        String destInput = _destAddr.getText().toString();
                        if (FileSystemUtils.isEmpty(startInput)) {
                            Toast.makeText(_context,
                                    R.string.route_plan_start_address_empty,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (FileSystemUtils.isEmpty(destInput)) {
                            Toast.makeText(_context,
                                    R.string.route_plan_dest_address_empty,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Find selected planner
                        String plannerName = (String) planSpinner
                                .getSelectedItem();
                        for (Entry<String, RoutePlannerInterface> k : _planners) {
                            if (plannerName.equals(k.getValue()
                                    .getDescriptiveName())) {
                                _autoPlan = k.getValue();
                                break;
                            }
                        }
                        if (_autoPlan == null) {
                            // Should never happen, but just in case
                            Toast.makeText(_context,
                                    R.string.route_plan_unknown_host,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        _finishingLookup = true;

                        // Check that input addresses still match the resolved addresses
                        if (!FileSystemUtils.isEquals(_resolvedStartAddr,
                                startInput))
                            lookupAddress(_startAddr, null, false);
                        if (!FileSystemUtils.isEquals(_resolvedDestAddr,
                                destInput))
                            lookupAddress(_destAddr, null, true);

                        if (_pendingLookups == 0)
                            onAddressResolved();
                    }
                });

        // Use self marker as default start address
        lookupAddress(_startAddr, null, false);

        return true;
    }

    private void dispose() {
        AtakBroadcast.getInstance().unregisterReceiver(this);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.route_start_address_clear) {
            _resolvedStartPoint = null;
            _resolvedStartAddr = null;
            _startAddr.setText("");

        } else if (i == R.id.route_start_map_select) {
            startMapSelect(_startAddr);

        } else if (i == R.id.route_start_address_history) {
            showRecentAddresses(_startAddr);

            // Destination address
        } else if (i == R.id.route_dest_address_clear) {
            _resolvedDestPoint = null;
            _resolvedDestAddr = null;
            _destAddr.setText("");

        } else if (i == R.id.route_dest_map_select) {
            startMapSelect(_destAddr);

        } else if (i == R.id.route_dest_address_history) {
            showRecentAddresses(_destAddr);

        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Route point clicked on map
        if (intent.getAction().equals(MAP_CLICKED)) {
            String routeUID = intent.getStringExtra("routeUID");
            if (_route == null || !FileSystemUtils.isEquals(
                    _route.getUID(), routeUID))
                return;

            _addrDialog.show();
            DropDownManager.getInstance().unHidePane();

            GeoPoint point = GeoPoint.parseGeoPoint(
                    intent.getStringExtra("point"));
            if (point == null || !point.isValid())
                return;

            int textId = intent.getIntExtra("textId", 0);
            EditText et = textId == R.id.route_start_address
                    ? _startAddr
                    : _destAddr;
            if (intent.hasExtra("accept")) {
                String address = intent.getStringExtra("address");
                if (intent.getBooleanExtra("accept", false)) {
                    // Finish route creation
                    updateAddress(et, GeoPointMetaData.wrap(point), address);
                    onAddressResolved();
                } else {
                    // Update the address and point but don't finish
                    if (et == _destAddr) {
                        _resolvedDestPoint = GeoPointMetaData.wrap(point);
                        _resolvedDestAddr = address;
                    } else {
                        _resolvedStartPoint = GeoPointMetaData.wrap(point);
                        _resolvedStartAddr = address;
                    }
                    et.setText(address);
                }
            } else
                lookupAddress(et, point, false);
        }
    }

    /**
     * Geocoding of the start/dest address
     * @param et Text input used to request this lookup
     * @param src Source point to reverse geocode (null to geocode instead)
     */
    private void lookupAddress(final EditText et, GeoPoint src,
            final boolean requireConfirmation) {

        String addrInput = et.getText().toString();
        if (src != null || addrInput.equals("")) {
            if (src == null) {
                Marker self = _mapView.getSelfMarker();
                src = (self != null && self.getGroup() != null) ? self
                        .getPoint()
                        : _mapView.getCenterPoint().get();
            }
            if (src != null) {
                final ProgressDialog pd = ProgressDialog.show(_context,
                        _context.getString(R.string.goto_dialog1),
                        _context.getString(R.string.goto_dialog2), true, false);
                final ReverseGeocodingTask rgt = new ReverseGeocodingTask(
                        src, _context, false);
                rgt.setOnResultListener(
                        new ReverseGeocodingTask.ResultListener() {
                            @Override
                            public void onResult() {
                                updateAddress(et,
                                        GeoPointMetaData.wrap(rgt.getPoint()),
                                        rgt.getHumanAddress());
                                pd.dismiss();
                                _pendingLookups--;
                                onAddressResolved();
                            }
                        });
                _pendingLookups++;
                rgt.execute();
            } else
                Toast.makeText(_context, R.string.goto_input_tip9,
                        Toast.LENGTH_SHORT).show();
            return;
        }

        final ProgressDialog pd = ProgressDialog.show(_context,
                _context.getString(R.string.goto_dialog1), addrInput,
                true, false);

        MapView view = MapView.getMapView();
        GeoBounds gb = view.getBounds();
        final GeocodingTask gt = new GeocodingTask(_context,
                gb.getSouth(), gb.getWest(), gb.getNorth(),
                gb.getEast(), false);

        gt.setOnResultListener(new GeocodingTask.ResultListener() {
            @Override
            public void onResult() {
                pd.dismiss();
                _pendingLookups--;
                if (requireConfirmation) {
                    Bundle bundle = new Bundle();
                    bundle.putString("address", gt.getHumanAddress());
                    bundle.putParcelable("point", gt.getPoint());
                    bundle.putParcelable("callback", new Intent(MAP_CLICKED)
                            .putExtra("routeUID", _route.getUID())
                            .putExtra("textId", et.getId()));
                    ToolManagerBroadcastReceiver.getInstance().startTool(
                            RouteConfirmationTool.TOOL_NAME, bundle);
                    if (ToolManagerBroadcastReceiver.getInstance()
                            .getActiveTool() instanceof RouteConfirmationTool) {
                        _addrDialog.hide();
                        DropDownManager.getInstance().hidePane();
                    }
                } else {
                    final GeoPoint gp = gt.getPoint();
                    if (gp != null) {
                        updateAddress(et, GeoPointMetaData.wrap(gt.getPoint()),
                                gt.getHumanAddress());
                        onAddressResolved();
                    }
                }
            }
        });
        _pendingLookups++;
        gt.execute(addrInput);
    }

    private void updateAddress(EditText et, GeoPointMetaData gp, String addr) {
        boolean failed = FileSystemUtils.isEmpty(addr) || gp == null
                || !gp.get().isValid();
        if (!failed) {
            // Update the recent addresses list
            List<Pair<String, GeoPointMetaData>> tmp = new ArrayList<>();
            for (int i = 0; i < RECENT_ADDRESSES.size(); ++i) {
                if (!RECENT_ADDRESSES.get(i).first.equals(addr)) {
                    tmp.add(RECENT_ADDRESSES.get(i));
                }
            }
            RECENT_ADDRESSES.clear();
            RECENT_ADDRESSES.addAll(tmp);
            RECENT_ADDRESSES.add(0, new Pair<>(addr, gp));
            saveRecentlyUsed();
        }

        // Show "Unknown Address" when reverse geocode lookup fails
        if (failed && gp != null && gp.get().isValid()) {
            addr = CoordinateFormatUtilities.formatToString(gp.get(),
                    _coordFormat);
        }

        // Update resolved start or destination address
        if (et.getId() == R.id.route_start_address) {
            if (failed && RouteMapReceiver.isNetworkAvailable()) {
                Toast.makeText(_context,
                        R.string.route_plan_start_address_not_found,
                        Toast.LENGTH_LONG).show();
            }

            _resolvedStartPoint = gp;
            _resolvedStartAddr = addr;
        } else {
            if (failed && RouteMapReceiver.isNetworkAvailable()) {
                Toast.makeText(_context,
                        R.string.route_plan_dest_address_not_found,
                        Toast.LENGTH_LONG).show();
            }

            _resolvedDestPoint = gp;
            _resolvedDestAddr = addr;
        }
        if (!FileSystemUtils.isEmpty(addr))
            et.setText(addr);
        else
            et.setTextColor(0xFFFF6666);
    }

    private void onAddressResolved() {
        if (_pendingLookups == 0 && _finishingLookup) {
            if (_resolvedStartPoint != null
                    && _resolvedStartPoint.get().isValid()
                    && _resolvedDestPoint != null
                    && _resolvedDestPoint.get().isValid()) {

                // One last check that the start and destination aren't the same
                if (_resolvedStartPoint.get()
                        .distanceTo(_resolvedDestPoint.get()) < 1) {
                    Toast.makeText(_context,
                            R.string.route_plan_start_dest_same,
                            Toast.LENGTH_LONG).show();
                    _finishingLookup = false;
                    return;
                }

                // Finishing up and all lookups completed successfully
                if (_addrDialog != null)
                    _addrDialog.dismiss();

                // Add start and destination way points
                _route.addMarker(Route.createWayPoint(_resolvedStartPoint,
                        UUID.randomUUID().toString()));
                _route.addMarker(Route.createWayPoint(_resolvedDestPoint,
                        UUID.randomUUID().toString()));

                // Show route details and plan route
                onFinish(_autoPlan);
            } else
                // Failed to find final address, cancel finishing state
                _finishingLookup = false;
        }
    }

    private void showRecentAddresses(final EditText et) {
        if (RECENT_ADDRESSES.isEmpty()) {
            Toast.makeText(_context, R.string.route_plan_no_recent_addresses,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final String[] addresses = new String[RECENT_ADDRESSES.size()];
        for (int i = 0; i < addresses.length; ++i)
            addresses[i] = RECENT_ADDRESSES.get(i).first;

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.route_plan_recent_addresses);
        b.setItems(addresses, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                String addr = addresses[w];
                GeoPointMetaData gp = RECENT_ADDRESSES.get(w).second;
                if (gp != null) {
                    if (et == _startAddr) {
                        _resolvedStartAddr = addr;
                        _resolvedStartPoint = gp;
                    } else if (et == _destAddr) {
                        _resolvedDestAddr = addr;
                        _resolvedDestPoint = gp;
                    }
                }
                et.setText(addr);
                d.dismiss();
            }
        });
        b.show();
    }

    private void startMapSelect(final EditText et) {
        if (_addrDialog != null && _addrDialog.isShowing()) {
            int prompt = et == _startAddr
                    ? R.string.route_plan_map_click_start
                    : R.string.route_plan_map_click_dest;
            Bundle bundle = new Bundle();
            bundle.putString("prompt", _context.getString(prompt));
            bundle.putParcelable("callback", new Intent(MAP_CLICKED)
                    .putExtra("routeUID", _route.getUID())
                    .putExtra("textId", et.getId()));
            ToolManagerBroadcastReceiver.getInstance().startTool(
                    MapClickTool.TOOL_NAME, bundle);
            if (ToolManagerBroadcastReceiver.getInstance()
                    .getActiveTool() instanceof MapClickTool) {
                _addrDialog.hide();
                DropDownManager.getInstance().hidePane();
            }
        }
    }

    /**
     * Returns a dialog builder that contains a dialog to modify the route's basic details
     * (driving/walking, infil/exfil, primary/secondary)
     *
     * @param route The route to edit
     * @param context MapView context
     * @param creation boolean indicating if the route is being created or not
     * @return Dialog builder
     */
    public static AlertDialog.Builder getDetailsDialog(final Route route,
            final Context context, boolean creation) {

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        // open route details dialog
        View view = LayoutInflater.from(context).inflate(
                R.layout.route_initialize_view, MapView.getMapView(), false);

        // get all the spinners
        final Spinner checkPointOrder = view
                .findViewById(R.id.check_point_order);
        final Spinner driveOrWalkS = view
                .findViewById(R.id.driving_walking_option);
        final Spinner infilOrExfilS = view
                .findViewById(R.id.infil_exfil_option);
        final Spinner primOrSecondaryS = view
                .findViewById(R.id.primary_secondary_option);

        /*
            checks if the tool is being created first time
            if being created grab the preference set by user
            for the travel type
         */
        if (!creation) {
            driveOrWalkS
                    .setSelection(route.getRouteMethod().id);
        } else {
            //get default route travel type from preferences
            driveOrWalkS.setSelection(Integer.parseInt(
                    prefs.getString("default_route_travel_type", "0")));
            checkPointOrder.setVisibility(View.GONE);
            infilOrExfilS.setVisibility(View.GONE);
            primOrSecondaryS.setVisibility(View.GONE);
        }

        primOrSecondaryS.setSelection(route.getRouteType().id);
        infilOrExfilS.setSelection(route.getRouteDirection().id);
        checkPointOrder.setSelection(route.getRouteOrder().id);

        driveOrWalkS.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0,
                            View arg1, int position, long id) {

                        if (position < 0)
                            return;

                        final Route.RouteMethod rm = Route.RouteMethod
                                .values()[position];
                        route.setRouteMethod(rm.text);
                        prefs.edit()
                                .putString("default_route_travel_type",
                                        position + "")
                                .apply();

                    }

                });

        infilOrExfilS.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> arg0,
                            View arg1, int position, long id) {

                        if (route.getRouteDirection().id == position)
                            return;

                        if (position < 0)
                            return;

                        final Route.RouteDirection rd = Route.RouteDirection
                                .values()[position];

                        //Log.d(TAG, "SHB - selected direction: " + rd.text);
                        route.setRouteDirection(rd.text);
                        if (rd == Route.RouteDirection.Infil) {
                            checkPointOrder
                                    .setSelection(
                                            Route.RouteOrder.Ascending.id);
                        } else {
                            checkPointOrder
                                    .setSelection(
                                            Route.RouteOrder.Descending.id);
                        }
                    }

                });

        primOrSecondaryS.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> arg0,
                            View arg1, int position, long id) {
                        if (position < 0)
                            return;

                        final Route.RouteType rt = Route.RouteType
                                .values()[position];
                        //Log.d(TAG, "SHB - selected type: " + rt.text);
                        route.setRouteType(rt.text);
                    }

                });

        checkPointOrder.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> arg0,
                            View arg1, int position, long id) {

                        if (position < 0)
                            return;

                        final Route.RouteOrder ro = Route.RouteOrder
                                .values()[position];
                        //Log.d(TAG, "SHB - selected order: " + ro.text);
                        route.setRouteOrder(ro.text);
                    }

                });

        AlertDialog.Builder adb = new AlertDialog.Builder(context);
        adb.setTitle(creation ? R.string.route_select_type
                : R.string.route_select_details);
        adb.setView(view);
        adb.setPositiveButton(R.string.ok, null);
        adb.setCancelable(false);
        return adb;
    }
}
