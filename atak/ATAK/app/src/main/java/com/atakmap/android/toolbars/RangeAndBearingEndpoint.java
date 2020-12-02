
package com.atakmap.android.toolbars;

import android.graphics.PointF;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MetaMapPoint;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.math.Rectangle;

public class RangeAndBearingEndpoint extends MetaMapPoint implements
        MapEventDispatcher.OnMapEventListener {

    public static final String TAG = "RangeAndBearingEndpoint";

    public static final int PART_TAIL = 0;
    public static final int PART_HEAD = 1;

    private static RangeAndBearingEndpointMoveTool _tool = null;
    private RangeAndBearingMapItem _parent;
    private int _part;

    public RangeAndBearingEndpoint(final GeoPointMetaData point,
            final String uid) {
        super(point, uid);
        setTitle("endpoint");
        setMetaString("type", "u-r-b-endpoint");
    }

    static public void setTool(RangeAndBearingEndpointMoveTool tool) {
        _tool = tool;
    }

    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        if (_parent == null || !_parent.getVisible())
            return false;
        float hitRadius = getHitRadius(view);
        if (view.getMapTilt() > 0d) {
            PointF xy = view
                    .forward(view.getRenderElevationAdjustedPoint(getPoint()));
            return Rectangle.contains(xpos - hitRadius, ypos - hitRadius,
                    xpos + hitRadius, ypos + hitRadius,
                    xy.x, xy.y);
        } else {
            GeoBounds hitBox = view.createHitbox(point, hitRadius);
            return hitBox.contains(getPoint());
        }
    }

    @Override
    public void onMapItemMapEvent(MapItem item, MapEvent event) {
        if (_parent == null || !_parent.getVisible())
            return;
        //Log.d(TAG, "onMapItemMapEventOccurred - Event info: " + event.getType() + " " + item.getUID());
        if (event.getType().equals(MapEvent.ITEM_LONG_PRESS) && item == this) {
            if (_tool != null)
                _tool.requestBeginTool(this);
        }
    }

    public void setPart(int part) {
        _part = part;
    }

    public int getPart() {
        return _part;
    }

    public void reverse() {
        _part = (_part++) % 2;
    }

    public void setParent(RangeAndBearingMapItem parent) {
        _parent = parent;
        setTitle(_parent.getTitle() + " Endpoint");
    }

    public RangeAndBearingMapItem getParent() {
        return _parent;
    }

}
