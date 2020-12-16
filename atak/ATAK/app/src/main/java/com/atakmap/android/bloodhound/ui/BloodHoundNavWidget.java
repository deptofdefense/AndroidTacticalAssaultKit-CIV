
package com.atakmap.android.bloodhound.ui;

import android.graphics.Color;
import android.view.MotionEvent;
import android.widget.Toast;

import com.atakmap.android.bloodhound.BloodHoundTool;
import com.atakmap.android.bloodhound.util.BloodHoundToolLink;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

/** A widget to display when the bloodhound tool is in route mode
 * that starts the navigation interface for the bloodhound route. */
public class BloodHoundNavWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener {

    /****************************** FIELDS *************************/
    public static final String TAG = "BloodHoundButtonTool";

    private final MapView _mapView;
    private final LinearLayoutWidget layoutWidget;
    private final BloodHoundTool _bloodhoundTool;

    final String imageUriEnabled;
    final String imageUriDisabled;

    private WidgetState widgetState = WidgetState.Disabled;

    /****************************** CONSTRUCTOR *************************/
    public BloodHoundNavWidget(final MapView mapView,
            BloodHoundTool toolbarButton) {
        super();

        this.setName("Bloodhound Nav Widget");
        _mapView = mapView;
        _bloodhoundTool = toolbarButton;

        // Configure the layout of the widget
        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");
        this.layoutWidget = root.getLayout(RootLayoutWidget.BOTTOM_LEFT)
                .getOrCreateLayout("BL_H/BL_V/Bloodhound_V/BH_V/BH_H");
        this.layoutWidget.setVisible(false);
        this.layoutWidget.setMargins(16f, 0f, 0f, 16f);

        // Construct the widget
        imageUriEnabled = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.bloodhound_nav_lit;

        // Construct the widget
        imageUriDisabled = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.bloodhound_nav_unlit;

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUriDisabled);
        final Icon icon = builder.build();

        this.setIcon(icon);
        this.addOnClickListener(this);
        this.addOnLongPressListener(this);

        this.layoutWidget.addWidget(this);
    }

    public void stop() {
        this.layoutWidget.setVisible(false);
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (widgetState.equals(WidgetState.Disabled)) {
            if (_bloodhoundTool.getStartItem() != MapView.getMapView()
                    .getSelfMarker()
                    && _bloodhoundTool._routeWidget.isEnabled()) {
                Toast.makeText(_mapView.getContext(),
                        R.string.bloodhound_must_be_hounding_start,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(_mapView.getContext(),
                        R.string.bloodhound_must_be_in_route_mode,
                        Toast.LENGTH_SHORT).show();
            }
        } else if (widgetState.equals(WidgetState.Enabled)) {
            if (_bloodhoundTool.getStartItem() == MapView.getMapView()
                    .getSelfMarker()) {
                // Open up navigation interface for the route.
                final BloodHoundToolLink link = _bloodhoundTool.getlink();
                if (link.isRoute()) {
                    RouteNavigator.getInstance().startNavigating(link.route, 0);
                }
            } else {
                Log.e(TAG,
                        "Internal logic violated: Nav widget should not be enabled if startPoint is not self marker.");
            }
        }
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        // TODO: Replace with resource
        Toast.makeText(_mapView.getContext(),
                R.string.bloodhound_open_navigation,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean setVisible(boolean visible) {
        Log.d(TAG, "setVisible");
        return this.layoutWidget.setVisible(visible);
    }

    /****************************** PRIVATE METHODS *************************/

    public void enableWidget() {
        widgetState = WidgetState.Enabled;
        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUriEnabled);
        final Icon icon = builder.build();

        this.setIcon(icon);
    }

    public void disableWidget() {
        widgetState = WidgetState.Disabled;
        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUriDisabled);
        final Icon icon = builder.build();

        this.setIcon(icon);
    }

    private enum WidgetState {
        Enabled,
        Disabled
    }

}
