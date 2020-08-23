
package com.atakmap.android.bloodhound.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.widget.Toast;

import com.atakmap.android.bloodhound.BloodHoundTool;
import com.atakmap.android.bloodhound.util.BloodHoundToolLink;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.RouteConfigurationDialog;
import com.atakmap.android.routes.RouteMapComponent;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.android.routes.RoutePlannerInterface;
import com.atakmap.android.routes.RoutePlannerManager;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A button to press that toggles the bloodhound tool
 * from "R&B line" mode to "route mode".
 */
public class BloodHoundRouteWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener {

    /****************************** FIELDS *************************/
    public static final String TAG = "BloodHoundButtonTool";

    private MapView _mapView;
    private Context _context;
    private LinearLayoutWidget layoutWidget;
    private MapComponent mc;
    private RoutePlannerManager _routeManager;
    private List<Map.Entry<String, RoutePlannerInterface>> routePlanners = new ArrayList<>();
    private BloodHoundTool _bloodhoundTool;
    private final SharedPreferences _prefs;

    /****************************** CONSTRUCTOR *************************/
    public BloodHoundRouteWidget(final MapView mapView,
            BloodHoundTool toolbarButton) {
        super();

        this.setName("Bloodhound Icon");
        _mapView = mapView;
        _context = mapView.getContext();
        _bloodhoundTool = toolbarButton;

        mc = ((MapActivity) _context)
                .getMapComponent(RouteMapComponent.class);

        _routeManager = mc != null
                ? ((RouteMapComponent) mc)
                        .getRoutePlannerManager()
                : null;

        _prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);

        // Configure the layout of the widget
        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");
        this.layoutWidget = root.getLayout(RootLayoutWidget.BOTTOM_LEFT)
                .getOrCreateLayout("BL_H/BL_V/Bloodhound_V/BH_V/BH_H");
        this.layoutWidget.setVisible(false);
        this.layoutWidget.setMargins(16f, 0f, 0f, 16f);

        // Construct the widget
        final String imageUri = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.bloodhound_widget_route;

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);
        final Icon icon = builder.build();

        this.setIcon(icon);
        this.addOnClickListener(this);

        this.layoutWidget.addWidget(this);
    }

    public void stop() {
        this.layoutWidget.setVisible(false);
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {

        final boolean network = RouteMapReceiver.isNetworkAvailable();

        routePlanners = new ArrayList<>();
        for (Map.Entry<String, RoutePlannerInterface> k : _routeManager
                .getRoutePlanners()) {
            if (!k.getValue().isNetworkRequired() || network) {
                routePlanners.add(k);
            }
        }

        // Get the names of pre-existing route planners.
        final String[] plannerNames = new String[routePlanners.size()];
        for (int i = 0; i < routePlanners.size(); i++) {
            Map.Entry<String, RoutePlannerInterface> entry = routePlanners
                    .get(i);
            plannerNames[i] = entry.getValue().getDescriptiveName();
        }

        final BloodHoundToolLink listener = _bloodhoundTool.getlink();

        if (listener.isLine()) {
            if (routePlanners.size() != 0) {
                // Prompt the user for their preferred route planner
                new RouteConfigurationDialog(_context, _mapView, new RouteConfigurationDialog.Callback<RoutePlannerInterface>() {
                    @Override
                    public void accept(RoutePlannerInterface planner) {
                        listener.setPlanner(planner);
                        listener.toggleRoute();
                    }
                }).show();
                /*
                AlertDialog.Builder plannerPicker = new AlertDialog.Builder(
                        _context);
                plannerPicker.setTitle(R.string.route_plan_select_planner);
                plannerPicker.setItems(plannerNames,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    final int which) {



                                Log.d(TAG,
                                        "Planner \""
                                                + planner.getDescriptiveName()
                                                + "\" was selected");
                            }
                        });
                plannerPicker.show();
               */
            } else {
                new Handler(Looper.getMainLooper()).post(
                        new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(_context,
                                        R.string.bloodhound_no_planners,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        } else {
            listener.toggleRoute();
        }
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
    }

    @Override
    public boolean setVisible(boolean visible) {
        Log.d(TAG, "setVisible");
        return this.layoutWidget.setVisible(visible);
    }

    /****************************** INHERITED METHODS *************************/

    /****************************** PRIVATE METHODS *************************/

}
