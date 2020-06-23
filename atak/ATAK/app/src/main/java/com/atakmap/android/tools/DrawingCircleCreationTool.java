
package com.atakmap.android.tools;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.tools.CircleCreationTool;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.Rings;
import com.atakmap.app.R;

/**
 * Tool for creating new drawing circles
 */
public class DrawingCircleCreationTool extends CircleCreationTool {

    protected static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.tools.DrawingCircleCreationTool";

    public DrawingCircleCreationTool(MapView mapView, MapGroup drawingGroup) {
        super(mapView, drawingGroup, TOOL_IDENTIFIER);
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
    public static Rings getRingsFromMarker(MapView mapView, Marker marker) {
        return null;
    }
}
