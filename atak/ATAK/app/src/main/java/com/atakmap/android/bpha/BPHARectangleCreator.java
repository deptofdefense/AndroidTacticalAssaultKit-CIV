
package com.atakmap.android.bpha;

import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.MutableMGRSPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BPHARectangleCreator {

    public static final String TYPE = "u-d-r";

    /**
     * Create a drawing rectangle from a point and a bpha
     * @param point the point for the center
     * @param bPHA the bpha definition as rows and columns
     * @param mapView the mapview
     * @param customTitle the title
     * @return the drawing rectangle that represents the input
     */
    public static DrawingRectangle drawRectangle(GeoPointMetaData point,
            BattlePositionHoldingArea bPHA, MapView mapView,
            String customTitle) {
        int rows = bPHA.getRows();
        int columns = bPHA.getColumns();

        if (rows == 0 || columns == 0)
            return null;

        final String[] mgrs = CoordinateFormatUtilities.formatToStrings(
                point.get(),
                CoordinateFormat.MGRS);

        if (mgrs == null)
            return null;

        mgrs[2] = nearest100(mgrs[2]);
        mgrs[3] = nearest100(mgrs[3]);

        final MGRSPoint centerPoint = MGRSPoint.decode(mgrs[0], mgrs[1],
                mgrs[2], mgrs[3], Ellipsoid.WGS_84, null);

        int eastingOffset = 500 * columns;
        int northingOffset = 500 * rows;

        // lower left
        final MutableMGRSPoint ll = new MutableMGRSPoint(centerPoint);
        ll.offset(-1 * eastingOffset, -1 * northingOffset);

        // lower right
        final MutableMGRSPoint lr = new MutableMGRSPoint(centerPoint);
        lr.offset(eastingOffset, -1 * northingOffset);

        // upper right
        final MutableMGRSPoint ur = new MutableMGRSPoint(centerPoint);
        ur.offset(eastingOffset, northingOffset);

        // upper left
        final MutableMGRSPoint ul = new MutableMGRSPoint(centerPoint);
        ul.offset(-1 * eastingOffset, northingOffset);

        MapGroup group = getGroup();
        MapGroup childGroup = group.addGroup(_createTitle());
        DrawingRectangle.Builder builder = new DrawingRectangle.Builder(
                childGroup, Rectangle.Builder.Mode.THREE_POINTS);

        // first point upper left
        GeoPoint firstPoint = ul.toUTMPoint(null).toGeoPoint();

        // second point upper right
        GeoPoint secondPoint = ur.toUTMPoint(null).toGeoPoint();

        // third point lower right
        GeoPoint thirdPoint = lr.toUTMPoint(null).toGeoPoint();

        builder.setFirstPoint(GeoPointMetaData.wrap(firstPoint));
        builder.setSecondPoint(GeoPointMetaData.wrap(secondPoint));
        builder.setThirdPoint(GeoPointMetaData.wrap(thirdPoint));

        if (customTitle.equals("")) {
            builder.createCenterMarker();
        } else {
            builder.createCenterMarker(customTitle);
        }
        DrawingRectangle r = builder.build();
        r.setMetaString(DrawingRectangle.KEY_BPHA, DrawingRectangle.KEY_BPHA);
        r.setFillColor(0x267f007f);
        r.setStrokeColor(0xff7f007f);
        r.setMetaString("entry", "user");
        group.addItem(r);
        r.persist(mapView.getMapEventDispatcher(), null,
                BPHARectangleCreator.class);
        return r;
    }

    private static List<MapItem> getAllBPHAs() {
        List<MapItem> items = new ArrayList<>();
        List<MapItem> typeList = getGroup().deepFindItems("type", TYPE);
        if (!typeList.isEmpty()) {
            for (MapItem item : typeList) {
                if (item instanceof DrawingRectangle) {
                    if (item.getMetaString(DrawingRectangle.KEY_BPHA, "")
                            .equals(DrawingRectangle.KEY_BPHA))
                        items.add(item);
                }
            }
        }
        return items;
    }

    private static String _createTitle() {
        String prefix = "BP HA";
        int num = 1;
        int j = 0;
        List<MapItem> typeList = getAllBPHAs();
        if (!typeList.isEmpty()) {
            int[] numUsed = new int[typeList.size()];
            // Do this to find the lowest used number for the group
            for (MapItem item : typeList) {
                if (item instanceof DrawingRectangle) {
                    String tTitle = item.getTitle();
                    if (FileSystemUtils.isEmpty(tTitle))
                        continue;
                    String[] n = tTitle.split(" ");
                    try {
                        numUsed[j] = Integer.parseInt(n[n.length - 1]);
                    } catch (NumberFormatException e) {
                        // The title has been editedA
                        numUsed[j] = 0;
                    }
                    j++;
                }
            }
            Arrays.sort(numUsed);
            for (int aNumUsed : numUsed) {
                if (num == aNumUsed) {
                    num++;
                }
            }
        }
        return prefix + " " + num;
    }

    /**
     * Returns the ma group for the bpha rectangles, mission and if not available
     * it will put them in drawing.
     * @return the map group to be used.
     */
    public static MapGroup getGroup() {
        MapGroup group = null;
        MapView mv = MapView.getMapView();
        if (mv != null)
            group = mv.getRootGroup().findMapGroup("Mission");
        if (group == null)
            group = DrawingToolsMapComponent.getGroup();
        return group;
    }

    /**
     * Pads out the MGRS easting or westing so that it is only 5 digits of precision
     * and in addition floors the data to the nearest 100 per the MGRS guidance
     * @param s the input mgrs.
     * @return the 5 digit MGRS easting or westing floored to the nearest 100.
     */
    private static String nearest100(String s) {
        final int len = s.length();
        StringBuilder sBuilder = new StringBuilder(s);
        for (int i = len; i < 6; ++i)
            sBuilder.append("0");
        s = sBuilder.toString();
        return s.substring(0, 3) + "00";

    }

}
