
package com.atakmap.android.tools;

import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.tools.CircleCreationTool;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.Rings;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;

/**
 * Tool for the creation of new drawing circles.
 *
 * Once the user has finished creating a circle with the tool, returns the uid
 * of the newly created DrawingCircle in an intent specified by the user by setting
 * the "callback" parcelable when starting the tool.   This is an
 * intent to fire when the tool is completed.
 */
public class DrawingCircleCreationTool extends CircleCreationTool {

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.tools.DrawingCircleCreationTool";
    private Intent _callback;

    public DrawingCircleCreationTool(MapView mapView, MapGroup drawingGroup) {
        super(mapView, drawingGroup, TOOL_IDENTIFIER);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _callback = extras.getParcelable("callback");
        return super.onToolBegin(extras);
    }

    @Override
    public void onToolEnd() {
        if (_callback != null && _circle.getCenterMarker() != null) {
            Intent i = new Intent(_callback);
            i.putExtra("uid", _circle.getUID());
            AtakBroadcast.getInstance().sendBroadcast(i);
        }
        super.onToolEnd();
    }

    @Override
    protected void addCircle(DrawingCircle circle) {
        circle.setTitle(getDefaultName(_context.getString(
                R.string.circle_prefix_name)));
        circle.setStrokeColor(_prefs.getShapeColor());
        circle.setFillColor(_prefs.getFillColor());
        circle.setStrokeWeight(_prefs.getStrokeWeight());
        circle.setMetaString("entry", "user");
        _mapGroup.addItem(circle);
        circle.persist(_mapView.getMapEventDispatcher(), null, getClass());
    }

    /**
     * @deprecated Drawing circles are no longer marker-centric
     * Create a new {@link DrawingCircle} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.1")
    public static Rings getRingsFromMarker(MapView mapView, Marker marker) {
        return null;
    }
}
