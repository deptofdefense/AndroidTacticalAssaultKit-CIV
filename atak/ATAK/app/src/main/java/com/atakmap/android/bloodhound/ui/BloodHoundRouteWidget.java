
package com.atakmap.android.bloodhound.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.core.util.Consumer;

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
import java.util.Timer;
import java.util.TimerTask;

/**
 * A button to press that toggles the bloodhound tool
 * from "R&B line" mode to "route mode".
 */
public class BloodHoundRouteWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener {

    /****************************** FIELDS *************************/
    public static final String TAG = "BloodHoundButtonTool";

    private final MapView _mapView;
    private final Context _context;
    private final LinearLayoutWidget layoutWidget;
    private final MapComponent mc;
    private final RoutePlannerManager _routeManager;
    private final List<Map.Entry<String, RoutePlannerInterface>> routePlanners = new ArrayList<>();
    private final BloodHoundTool _bloodhoundTool;
    private BloodHoundNavWidget _navWidget;

    // Icon names
    private final String selected;
    private final String unselected;
    private final String loading0;
    private final String loading1;
    private final String loading2;
    private final String loading3;

    private Timer timer;
    private IconBlinkTimer blinkTimer;

    private WidgetState widgetState = WidgetState.Disabled;

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

        // Configure the layout of the widget
        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");
        this.layoutWidget = root.getLayout(RootLayoutWidget.BOTTOM_LEFT)
                .getOrCreateLayout("BL_H/BL_V/Bloodhound_V/BH_V/BH_H");
        this.layoutWidget.setVisible(false);
        this.layoutWidget.setMargins(16f, 0f, 0f, 16f);

        // Construct the widget, initially unselected.
        unselected = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.bloodhound_widget_route_disabled;

        selected = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.bloodhound_widget_route;

        loading0 = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.loading_dot_0;

        loading1 = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.loading_dot_1;

        loading2 = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.loading_dot_2;

        loading3 = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.loading_dot_3;

        timer = new Timer();
        blinkTimer = new IconBlinkTimer();

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, unselected);
        final Icon icon = builder.build();

        this.setIcon(icon);
        this.addOnClickListener(this);
        this.addOnLongPressListener(this);

        this.layoutWidget.addWidget(this);
    }

    public void setNavWidget(BloodHoundNavWidget widget) {
        _navWidget = widget;
    }

    public void stop() {
        this.layoutWidget.setVisible(false);
        iconDisable();
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {

        final BloodHoundToolLink link = _bloodhoundTool.getlink();

        switch (widgetState) {
            case Disabled: {
                final boolean network = RouteMapReceiver.isNetworkAvailable();

                routePlanners.clear();
                for (Map.Entry<String, RoutePlannerInterface> k : _routeManager
                        .getRoutePlanners()) {
                    if (!k.getValue().isNetworkRequired() || network) {
                        routePlanners.add(k);
                    }
                }

                if (routePlanners.size() != 0) {
                    // Prompt the user for their preferred route planner
                    new RouteConfigurationDialog(_context, _mapView,
                            new RouteConfigurationDialog.Callback<RoutePlannerInterface>() {
                                @Override
                                public void accept(
                                        RoutePlannerInterface planner) {
                                    startIconLoading();
                                    link.setPlanner(planner);
                                    link.toggleRoute(new Runnable() {
                                        @Override
                                        public void run() {
                                            iconEnable();
                                            if (_mapView
                                                    .getSelfMarker() == _bloodhoundTool
                                                            .getStartItem()) {
                                                _navWidget.enableWidget();
                                            } else {
                                                _navWidget.disableWidget();
                                            }
                                        }
                                    }, new Runnable() {
                                        @Override
                                        public void run() {
                                            iconDisable();
                                            _navWidget.disableWidget();
                                        }
                                    }, new Consumer<Exception>() {
                                        @Override
                                        public void accept(Exception e) {
                                            iconDisable();
                                            _navWidget.disableWidget();
                                        }
                                    });
                                }
                            }).show();
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
                break;
            }
            case Loading:
            case Enabled: {
                link.toggleRoute();
                _navWidget.disableWidget();
                iconDisable();
                break;
            }
        }
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        // TODO: Replace with resource
        Toast.makeText(_mapView.getContext(), "Toggle route mode",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean setVisible(boolean visible) {
        Log.d(TAG, "setVisible");
        return this.layoutWidget.setVisible(visible);
    }

    /****************************** PRIVATE METHODS *************************/

    private void startIconLoading() {
        widgetState = WidgetState.Loading;

        timer = new Timer();
        blinkTimer = new IconBlinkTimer();
        timer.schedule(blinkTimer, 300, 300);
    }

    public boolean isEnabled() {
        return widgetState.equals(WidgetState.Enabled);
    }

    private void iconEnable() {
        if (widgetState.equals(WidgetState.Loading)) {
            try {
                if (timer != null && blinkTimer != null) {
                    blinkTimer.cancel();
                    timer.purge();
                }
            } catch (Exception ignored) {
            }

            widgetState = WidgetState.Enabled;
            Icon.Builder builder = new Icon.Builder();
            builder.setAnchor(0, 0);
            builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
            builder.setSize(48, 48);
            builder.setImageUri(Icon.STATE_DEFAULT, selected);
            final Icon icon = builder.build();
            this.setIcon(icon);
        }
    }

    private void iconDisable() {
        try {
            if (timer != null && blinkTimer != null) {
                blinkTimer.cancel();
                timer.purge();
            }
        } catch (Exception ignored) {
        }

        widgetState = WidgetState.Disabled;
        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, unselected);
        final Icon icon = builder.build();
        this.setIcon(icon);
    }

    private enum WidgetState {
        Enabled,
        Disabled,
        Loading
    }

    private class IconBlinkTimer extends TimerTask {

        private int iconState;

        @Override
        public void run() {
            Icon.Builder builder = new Icon.Builder();
            builder.setAnchor(0, 0);
            builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
            builder.setSize(48, 48);
            switch (iconState) {
                case 0:
                    builder.setImageUri(Icon.STATE_DEFAULT, loading0);
                    break;
                case 1:
                    builder.setImageUri(Icon.STATE_DEFAULT, loading1);
                    break;
                case 2:
                    builder.setImageUri(Icon.STATE_DEFAULT, loading2);
                    break;
                default: // 2
                    builder.setImageUri(Icon.STATE_DEFAULT, loading3);
                    break;
            }
            final Icon icon = builder.build();
            setIcon(icon);
            iconState = (iconState + 1) % 4;
        }
    }

}
