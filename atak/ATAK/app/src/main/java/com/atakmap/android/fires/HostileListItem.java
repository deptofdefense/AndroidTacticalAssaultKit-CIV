
package com.atakmap.android.fires;

import android.graphics.Color;

import com.atakmap.android.hierarchy.items.MapItemHierarchyListItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.DirectionType;

class HostileListItem extends MapItemHierarchyListItem {

    public static final String TAG = "HostileListItem";

    final static int WHITE = Color.WHITE;
    final static int RED = Color.RED;
    final static int GREEN = Color.GREEN;
    final static int YELLOW = Color.YELLOW;
    final static int BLUE = Color.BLUE;

    private int state;

    HostileListItem(final MapView mapView, final MapItem item) {
        super(mapView, item);
        state = item.getMetaInteger("textColor", WHITE);
    }

    public int getTextColor() {
        return state;
    }

    public void setTextColor(int color) {
        state = color;
        item.setMetaInteger("textColor", state);
    }

    String getClosestFriendly() {
        // decouple but must match NineLineMarkerConstants.NINELINE_CLOSEST_UID
        String uid = item.getMetaString("closestUID", null);
        if (uid != null) {
            MapItem closestObject = mapView.getMapItem(uid);
            if (closestObject instanceof PointMapItem
                    && item instanceof PointMapItem) {
                final double dist = ((PointMapItem) item).getPoint()
                        .distanceTo(
                                ((PointMapItem) closestObject).getPoint());
                final double bearing = ((PointMapItem) item).getPoint()
                        .bearingTo(
                                ((PointMapItem) closestObject).getPoint());

                // doctrine indicates line eight is always in meters.
                // do not perform the span utilities conversion.
                // SpanUtilities.formatType(Span.METRIC, dist, Span.METER));
                return DirectionType.getDirection(bearing)
                        .getAbbreviation() + " " + (int) Math.round(dist)
                        + " m";
            }
        }
        return mapView.getContext().getString(R.string.none_caps);

    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        HostileListItem that = (HostileListItem) o;

        return state == that.state;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + state;
        return result;
    }
}
