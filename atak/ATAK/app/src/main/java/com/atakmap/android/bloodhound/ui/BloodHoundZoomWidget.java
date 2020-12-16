
package com.atakmap.android.bloodhound.ui;

import com.atakmap.android.bloodhound.BloodHoundTool;

import android.graphics.Color;
import android.graphics.Point;
import android.view.MotionEvent;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.assets.Icon;

/**
 * A button that zooms in on the bloodhound link when pressed.
 */
public class BloodHoundZoomWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener {

    /****************************** FIELDS *************************/
    public static final String TAG = "BloodHoundButtonTool";

    private final MapView _mapView;
    private final LinearLayoutWidget layoutWidget;
    private final BloodHoundTool _bloodhoundTool;

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.toolbars.BloodHoundButtonTool";

    public BloodHoundZoomWidget(MapView mapView, BloodHoundTool toolbarButton) {
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
        MarkerIconWidget iconWidget = new MarkerIconWidget();
        iconWidget.setName("Bloodhound Icon");

        final String imageUri = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.bloodhound_widget;

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

        final Icon icon = builder.build();
        iconWidget.setIcon(icon);
        iconWidget.addOnClickListener(this);

        // Add the widget to the appropriate position of the layout
        this.layoutWidget.addWidget(iconWidget);
    }

    public void zoom() {
        // when the video dropdown is open it sets the focus point of the map
        Point focus = _mapView.getMapController().getFocusPoint();
        if (_bloodhoundTool.getSpiItem() == null
                || _bloodhoundTool.getUser() == null)
            return;
        ATAKUtilities.scaleToFit(_mapView, new MapItem[] {
                _bloodhoundTool.getUser(), _bloodhoundTool.getSpiItem()
        },
                (int) (focus.x * 1.75), (int) (focus.y * 1.75));
    }

    public void stop() {
        this.layoutWidget.setVisible(false);
    }

    /****************************** INHERITED METHODS *************************/

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        zoom();
    }
}
