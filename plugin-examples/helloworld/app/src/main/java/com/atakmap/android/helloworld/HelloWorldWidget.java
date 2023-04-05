
package com.atakmap.android.helloworld;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget.OnPressListener;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.android.widgets.MarkerIconWidget;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.map.opengl.GLRenderGlobals;


import android.content.BroadcastReceiver;
import com.atakmap.coremap.log.Log;

public class HelloWorldWidget extends AbstractWidgetMapComponent implements
        OnPressListener, MapWidget.OnUnpressListener, MapWidget.OnMoveListener,
        MapWidget.OnClickListener {

    private MapView mapView;

    private final static int ICON_WIDTH = 96;
    private final static int ICON_HEIGHT = 64;
    public static final String TAG = "HelloWorldWidget";

    @Override
    protected void onCreateWidgets(final Context context, final Intent intent,
            final MapView view) {

        mapView = view;
        Log.d(TAG, "registering my wacky search");
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("SHOW_MY_WACKY_SEARCH");
        this.registerReceiver(view.getContext(), wacky, filter);
        Log.d(TAG, "registered my wacky search");

    }

    MarkerIconWidget widget = null;

    private final BroadcastReceiver wacky = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "wacky search");

            if (widget == null) {
                widget = new MarkerIconWidget();

                String imageUri = "android.resource://"
                        + mapView.getContext().getPackageName() + "/"
                        + com.atakmap.app.R.drawable.sync_search;

                //imageUri = "file:///sdcard/custom_marker.png";

                widget.setPoint((mapView.getWidth() / 2f)
                        - (GLRenderGlobals.getRelativeScaling() * ICON_WIDTH / 2) + 72, 160f);

                Icon.Builder builder = new Icon.Builder();
                builder.setAnchor(0, 0);
                builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
                builder.setSize(ICON_WIDTH, ICON_HEIGHT);
                builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

                Icon icon = builder.build();
                widget.setIcon(icon);

                widget.addOnClickListener(HelloWorldWidget.this);
                widget.addOnPressListener(HelloWorldWidget.this);
                widget.addOnUnpressListener(HelloWorldWidget.this);
                widget.addOnMoveListener(HelloWorldWidget.this);

            }
            if (!getRootLayoutWidget().getChildWidgets().contains(widget))
                getRootLayoutWidget().addWidget(widget);
        }
    };

    private PointF pointDown = null;
    private boolean dragging;

    @Override
    public boolean onMapWidgetMove(MapWidget widget, MotionEvent event) {
        if (dragging || (pointDown != null &&
                Math.abs(pointDown.x - event.getX()) < 2 &&
                Math.abs(pointDown.y - event.getY()) < 2)) {
            dragging = true;
        }

        Log.d(TAG, event.getX() + " " + event.getY());
        return true;
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (dragging) {
            return;
        }

        Log.d(TAG, "onClickListener called");
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(
                mapView.getContext());
        builderSingle.setTitle("Wacky Search");
        builderSingle.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {
                        dialog.dismiss();
                    }
                });
        builderSingle.show();

    }

    @Override
    public void onMapWidgetUnpress(MapWidget widget, MotionEvent event) {
        Log.d(TAG, "onMapUnpressed called");
    }

    @Override
    public void onMapWidgetPress(MapWidget widget, MotionEvent event) {

        Log.d(TAG, "onMapPressed called");
        dragging = false;
        pointDown = new PointF(event.getX(), event.getY());
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        if (widget != null)
            getRootLayoutWidget().removeWidget(widget);

    }

    @Override
    public void onStart(Context context, MapView view) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onStop(Context context, MapView view) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onPause(Context context, MapView view) {
    }

    @Override
    public void onResume(Context context, MapView view) {
        // TODO Auto-generated method stub

    }
}
