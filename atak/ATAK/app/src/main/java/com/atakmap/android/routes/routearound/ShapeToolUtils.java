
package com.atakmap.android.routes.routearound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.tools.DrawingRectangleCreationTool;
import com.atakmap.android.drawing.tools.ShapeCreationTool;
import com.atakmap.android.geofence.data.ShapeUtils;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.tools.DrawingCircleCreationTool;
import com.atakmap.android.util.AbstractMapItemSelectionTool;
import com.atakmap.coremap.filesystem.FileSystemUtils;

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

    /**
     * Run the circle creation tool
     * @param callback Callback invoked when the circle is successfully created
     * @param onError Error callback
     * @param <A> Callback return type (unused)
     */
    public <A> void runCircleCreationTool(
            final Callback<DrawingCircle, A> callback,
            final Callback<Error, A> onError) {
        runTool(DrawingCircleCreationTool.TOOL_IDENTIFIER,
                CIRCLE_TOOL_FINISHED, DrawingCircle.class, callback, onError);
    }

    /**
     * Run the shape creation tool
     * @param callback Callback invoked when the shape is successfully created
     * @param onError Error callback
     * @param <A> Callback return type (unused)
     */
    public <A> void runPolygonCreationTool(final Callback<Shape, A> callback,
            final Callback<Error, A> onError) {
        runTool(ShapeCreationTool.TOOL_IDENTIFIER,
                POLYGON_TOOL_FINISHED, Shape.class, callback, onError);
    }

    /**
     * Run the rectangle creation tool
     * @param callback Callback invoked when the rectangle is successfully created
     * @param onError Error callback
     * @param <A> Callback return type (unused)
     */
    public <A> void runRectangleCreationTool(final Callback<Shape, A> callback,
            final Callback<Error, A> onError) {
        runTool(DrawingRectangleCreationTool.TOOL_IDENTIFIER,
                RECTANGLE_TOOL_FINISHED, Shape.class, callback, onError);
    }

    /**
     * Run the region select tool
     * @param callback Callback invoked when an item is selected
     * @param onError Error callback
     * @param <A> Callback return type (unused)
     */
    public <A> void runRegionSelectionTool(final Callback<MapItem, A> callback,
            final Callback<Error, A> onError) {
        runTool(TOOL_ID, TOOL_FINISHED, MapItem.class, callback, onError);
    }

    /**
     * Run a tool with the given callbacks
     * @param toolId Tool identifier
     * @param callbackId Callback intent action
     * @param itemClass Map item sub-class
     * @param callback Callback to invoke on success
     * @param onError Error callback
     * @param <A> Return type (unused)
     * @param <B> Map item sub-class
     */
    private <A, B> void runTool(
            final String toolId,
            final String callbackId,
            final Class<B> itemClass,
            final Callback<B, A> callback,
            final Callback<Error, A> onError) {

        // Register intent receiver for our callbacks
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (FileSystemUtils.isEquals(intent.getAction(), callbackId)) {

                    // Unregister this receiver now that the tool is finished
                    AtakBroadcast.getInstance().unregisterReceiver(this);

                    // Map item UID that was created by the tool
                    String uid = intent.getStringExtra("uid");

                    // Tool was ended prematurely - neither success nor error
                    if (FileSystemUtils.isEmpty(uid))
                        return;

                    // Find the map item
                    MapItem item = mapView.getMapItem(uid);
                    if (item == null) {
                        onError.apply(new Error("Item not found"));
                        return;
                    }

                    // Make sure the item is the correct class
                    if (itemClass.isInstance(item))
                        callback.apply(itemClass.cast(item));
                    else
                        onError.apply(new Error("Item is not the right type"));
                }
            }
        };
        AtakBroadcast.getInstance().registerReceiver(br,
                new DocumentedIntentFilter(callbackId));

        // Start the tool on the UI thread
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = new Bundle();
                bundle.putParcelable("callback", new Intent(callbackId));
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        toolId, bundle);
            }
        });
    }
}
