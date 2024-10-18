package com.atakmap.android.helloworld.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LayoutHelper;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.ATAKActivity;

import java.util.List;

/**
 * An example of a view that is overlaid onto the map view alongside
 * other views and widgets
 */
public class ViewOverlayExample extends AbstractMapComponent
        implements RootLayoutWidget.OnLayoutChangedListener {

    // Intent action for toggling the overlay view from outside this component
    public static final String TOGGLE_OVERLAY_VIEW =
            "com.atakmap.android.helloworld.view.TOGGLE_OVERLAY_VIEW";

    private MapView mapView;
    private RelativeLayout mapParent;
    private View overlayView;
    private RootLayoutWidget rootLayoutWidget;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        mapView = view;

        // Get the map parent layout
        mapParent = ((ATAKActivity) view.getContext()).findViewById(
                com.atakmap.app.R.id.map_parent);

        // Inflate the overlay view and attach to the map parent
        // The view itself is just an icon and some text
        overlayView = LayoutInflater.from(context).inflate(
                R.layout.view_overlay_example, mapParent, false);
        mapParent.addView(overlayView);

        // Hide the view by default
        overlayView.setVisibility(View.INVISIBLE);

        // Get the root layout widget used for controlling view/widget placement
        rootLayoutWidget = (RootLayoutWidget) mapView.getComponentExtra(
                "rootLayoutWidget");

        // Add a listener that is called any time a view/widget changes position
        // so we can update the placement of the overlay view
        rootLayoutWidget.addOnLayoutChangedListener(this);

        // Finally setup the intent receiver so the drop-down can communicate
        // with this class
        registerReceiver(context, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Toggle visibility of the overlay
                if (overlayView.getVisibility() != View.VISIBLE)
                    overlayView.setVisibility(View.VISIBLE);
                else
                    overlayView.setVisibility(View.INVISIBLE);
            }
        }, new DocumentedIntentFilter(TOGGLE_OVERLAY_VIEW));
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        // Make sure we clean up when the plugin is unloaded
        rootLayoutWidget.removeOnLayoutChangedListener(this);
        mapParent.removeView(overlayView);
    }

    @Override
    public void onLayoutChanged() {
        // Root layout has been changed (i.e. nav buttons, widgets, etc.)
        updateOverlayPosition();
    }

    /**
     * Update the position of our overlay view based on the space available
     */
    private void updateOverlayPosition() {

        // Establish the maximum usable bounds (the entire map view)
        Rect mapRect = new Rect(0, 0, mapView.getWidth(), mapView.getHeight());

        // Get the bounds that are currently occupied
        // "true" = include widgets and views | "false" = only include views
        List<Rect> bounds = rootLayoutWidget.getOccupiedBounds(true);

        // Setup the layout helper using the max usable bounds and rectangle
        LayoutHelper layoutHelper = new LayoutHelper(mapRect, bounds);

        // Get the bounds for the view you want to place
        // If the bounds change based on placement then alternatively establish
        // a minimum acceptable bounds rectangle
        Rect viewBounds = LayoutHelper.getBounds(overlayView);

        // Find the best position aligned to the top-right
        viewBounds = layoutHelper.findBestPosition(viewBounds,
                RootLayoutWidget.TOP_RIGHT);

        // OPTIONAL: If you want to extend the view to take up as much space as
        // possible in its current position then call
        // findMaxWidth or findMaxHeight
        //viewBounds = layoutHelper.findMaxWidth(viewBounds);

        // Update the placement for this view using the top-left point of the bounds
        // Using a RelativeLayout parent for this example
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)
                overlayView.getLayoutParams();
        lp.topMargin = viewBounds.top;
        lp.leftMargin = viewBounds.left;
        overlayView.setLayoutParams(lp);
    }
}
