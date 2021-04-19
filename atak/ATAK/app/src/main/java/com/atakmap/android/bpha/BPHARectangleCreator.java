
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
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BPHARectangleCreator {

    public static final String TYPE = "u-d-r";

    public static DrawingRectangle drawRectangle(GeoPointMetaData point,
            BattlePositionHoldingArea bPHA, MapView mapView,
            String customTitle) {
        int rows = bPHA.getRows();
        int columns = bPHA.getColumns();

        if (rows == 0 || columns == 0)
            return null;

        final String[] mgrs = CoordinateFormatUtilities.formatToStrings(point.get(),
                CoordinateFormat.MGRS);


        final int easting = nearest100(pad5(mgrs[2]));
        final int northing = nearest100(pad5(mgrs[3]));

        mgrs[2] = ""+easting;
        mgrs[3] = ""+northing;

        GeoPoint gp = CoordinateFormatUtilities.convert(mgrs,
                CoordinateFormat.MGRS);
        if (gp == null)
            return null;


        MapGroup group = getGroup();
        MapGroup childGroup = group.addGroup(_createTitle());
        DrawingRectangle.Builder builder = new DrawingRectangle.Builder(
                childGroup, Rectangle.Builder.Mode.THREE_POINTS);

        point = GeoPointMetaData.wrap(gp);


        int eastingOffset = 500 * columns;
        int northingOffset = 500 * rows;


        String[] scratch = new String[] { mgrs[0], mgrs[1], mgrs[2], mgrs[3]};


        // first point upper left
        scratch[2] = ""+ (easting - eastingOffset);
        scratch[3] = ""+ (northing + northingOffset);
        GeoPoint firstPoint = CoordinateFormatUtilities.convert(scratch, CoordinateFormat.MGRS);

        // second point upper right
        scratch[2] = ""+ (easting + eastingOffset);
        scratch[3] = ""+ (northing + northingOffset);
        GeoPoint secondPoint = CoordinateFormatUtilities.convert(scratch, CoordinateFormat.MGRS);

        // third point lower right
        scratch[2] = ""+ (easting + eastingOffset);
        scratch[3] = ""+ (northing - northingOffset);
        GeoPoint thirdPoint = CoordinateFormatUtilities.convert(scratch, CoordinateFormat.MGRS);


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
     * Pads out the MGRS easting or westing so that it is only 5 digits of precision.
     * @param s the input mgrs.
     * @return the 5 digit MGRS easting or westing
     */
    private static int pad5(String s) {
        final int len = s.length();
        for (int i = len; i < 6; ++i)
            s = s + "0";
        return Integer.parseInt(s.substring(0,5));
    }

    /**
     * Rounds a value to the nearest 100.
     * @param val the value to round.
     * @return the rounded value
     */
    private static int nearest100(int val) {
        return (int) (Math.ceil(val/100.0))*100;
    }

}
