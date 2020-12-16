
package com.atakmap.android.routes.routearound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.drawing.tools.DrawingRectangleCreationTool;
import com.atakmap.android.drawing.tools.ShapeCreationTool;
import com.atakmap.android.geofence.data.ShapeUtils;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.tools.DrawingCircleCreationTool;
import com.atakmap.android.util.AbstractMapItemSelectionTool;
import com.atakmap.coremap.log.Log;

/** Provides helper functions for running some of ATAK's shape creation tools with callbacks. */
public class ShapeToolUtils extends AbstractMapItemSelectionTool {

    private static final String CIRCLE_TOOL_FINISHED = "com.atakmap.android.vns.ui_tools.region_creation.DrawingCircleCreationTool.FINISHED";
    private static final String POLYGON_TOOL_FINISHED = "com.atakmap.android.vns.ui_tools.region_creation.ShapeCreationTool.FINISHED";
    private static final String RECTANGLE_TOOL_FINISHED = "com.atakmap.android.vns.ui_tools.region_creation.DrawingRectangleCreationTool.FINISHED";

    private static final String TOOL_ID = "com.atakmap.android.vns.ui_tools.RegionSelectionTool";
    private static final String TOOL_FINISHED = "com.atakmap.android.vns.ui_tools.RegionSelectionTool.Finished";

    private final MapView mapView;

    public ShapeToolUtils(MapView mapView) {
        super(mapView, TOOL_ID, TOOL_FINISHED, "Select a region on the screen",
                "Invalid Selection");
        this.mapView = mapView;
    }

    @Override
    protected boolean isItem(MapItem mi) {
        // Returns either the shape itself, or the center point of the shape.
        return mi instanceof Shape || ShapeUtils.getShapeUID(mi) != null;
    }

    /** Functional interface for callbacks */
    public interface Callback<A, B> {
        B apply(A x);
    }

    public <A> void runCircleCreationTool(
            final Callback<DrawingCircle, A> callback,
            final Callback<Error, A> onError) {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(CIRCLE_TOOL_FINISHED)) {
                    AtakBroadcast.getInstance().unregisterReceiver(this);
                    String uid = intent.getStringExtra("uid");
                    Log.d("TEST", uid);
                    if (uid != null) {
                        DrawingCircle circle = (DrawingCircle) mapView
                                .getMapItem(uid);
                        callback.apply(circle);
                    } else {
                        onError.apply(new Error("pointMapItem not found"));
                    }
                }
            }
        };
        AtakBroadcast.getInstance().registerReceiver(br,
                new AtakBroadcast.DocumentedIntentFilter(CIRCLE_TOOL_FINISHED));
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = new Bundle();
                bundle.putParcelable("callback",
                        new Intent(CIRCLE_TOOL_FINISHED));
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        DrawingCircleCreationTool.TOOL_IDENTIFIER, bundle);
            }
        });
    }

    public <A> void runPolygonCreationTool(final Callback<Shape, A> callback,
            final Callback<Error, A> onError) {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null && action.equals(POLYGON_TOOL_FINISHED)) {
                    AtakBroadcast.getInstance().unregisterReceiver(this);
                    String uid = intent.getStringExtra("uid");

                    if (uid != null) {
                        Shape shape = (Shape) mapView.getMapItem(uid);
                        callback.apply(shape);
                    } else {
                        onError.apply(new Error("shape not found"));
                    }
                }
            }
        };
        AtakBroadcast.getInstance().registerReceiver(br,
                new AtakBroadcast.DocumentedIntentFilter(
                        POLYGON_TOOL_FINISHED));
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = new Bundle();
                bundle.putParcelable("callback",
                        new Intent(POLYGON_TOOL_FINISHED));
                ToolManagerBroadcastReceiver.getInstance()
                        .startTool(ShapeCreationTool.TOOL_IDENTIFIER, bundle);
            }
        });
    }

    public <A> void runRectangleCreationTool(final Callback<Shape, A> callback,
            final Callback<Error, A> onError) {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null && action.equals(RECTANGLE_TOOL_FINISHED)) {
                    AtakBroadcast.getInstance().unregisterReceiver(this);
                    String uid = intent.getStringExtra("uid");
                    if (uid != null) {
                        DrawingRectangle rectangle = (DrawingRectangle) mapView
                                .getMapItem(uid);
                        callback.apply(rectangle);
                    } else {
                        onError.apply(new Error("pointMapItem not found"));
                    }
                }
            }
        };
        AtakBroadcast.getInstance().registerReceiver(br,
                new AtakBroadcast.DocumentedIntentFilter(
                        RECTANGLE_TOOL_FINISHED));
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = new Bundle();
                bundle.putParcelable("callback",
                        new Intent(RECTANGLE_TOOL_FINISHED));
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        DrawingRectangleCreationTool.TOOL_IDENTIFIER, bundle);
            }
        });
    }

    /** Start the RegionSelectionTool. Returns an observable that emits the user's selected Shape */
    public <A> void runRegionSelectionTool(final Callback<MapItem, A> callback,
            final Callback<Error, A> onError) {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null
                        && intent.getAction().equals(TOOL_FINISHED)) {
                    String uid = intent.getStringExtra("uid");
                    if (uid != null) {
                        MapItem selectedRoute = mapView.getMapItem(uid);
                        callback.apply(selectedRoute);
                    }
                    AtakBroadcast.getInstance().unregisterReceiver(this);
                }
            }
        };
        AtakBroadcast.getInstance().registerReceiver(
                br,
                new AtakBroadcast.DocumentedIntentFilter(TOOL_FINISHED));
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                ToolManagerBroadcastReceiver.getInstance().startTool(TOOL_ID,
                        new Bundle());
            }
        });
    }
}
