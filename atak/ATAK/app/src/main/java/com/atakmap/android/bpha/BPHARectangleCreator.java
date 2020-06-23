
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
        String[] mgrs = CoordinateFormatUtilities.formatToStrings(point.get(),
                CoordinateFormat.MGRS);

        if (rows == 1 && columns == 1) {
            mgrs[2] = roundToKm(mgrs[2], false);
            mgrs[3] = roundToKm(mgrs[3], true);
        } else {
            mgrs[2] = roundToKm(mgrs[2]);
            mgrs[3] = roundToKm(mgrs[3]);
        }

        MapGroup group = getGroup();
        MapGroup childGroup = group.addGroup(_createTitle());
        DrawingRectangle.Builder builder = new DrawingRectangle.Builder(
                childGroup, Rectangle.Builder.Mode.START_END_CORNERS);
        GeoPoint gp = CoordinateFormatUtilities.convert(mgrs,
                CoordinateFormat.MGRS);
        if (gp == null)
            return null;
        point = GeoPointMetaData.wrap(gp);
        if (rows % 2 == 0 && columns % 2 == 0) {
            GeoPoint firstPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), (rows / 2f) * 1000, 0);
            firstPoint = DistanceCalculations.metersFromAtBearing(firstPoint,
                    (columns / 2f) * 1000, 270);
            builder.setFirstPoint(GeoPointMetaData.wrap(firstPoint));

            GeoPoint secondPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), (rows / 2f) * 1000, 180);
            secondPoint = DistanceCalculations.metersFromAtBearing(secondPoint,
                    (columns / 2f) * 1000, 90);
            builder.setSecondPoint(GeoPointMetaData.wrap(secondPoint));
        } else if (rows % 2 == 0) {
            GeoPoint firstPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), (rows / 2f) * 1000, 0);
            firstPoint = DistanceCalculations.metersFromAtBearing(firstPoint,
                    ((columns - 1) / 2f) * 1000, 270);
            builder.setFirstPoint(GeoPointMetaData.wrap(firstPoint));

            GeoPoint secondPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), (rows / 2f) * 1000, 180);
            secondPoint = DistanceCalculations.metersFromAtBearing(secondPoint,
                    ((columns + 1) / 2f) * 1000, 90);
            builder.setSecondPoint(GeoPointMetaData.wrap(secondPoint));
        } else if (columns % 2 == 0) {
            GeoPoint firstPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), ((rows - 1) / 2f) * 1000, 0);
            firstPoint = DistanceCalculations.metersFromAtBearing(firstPoint,
                    (columns / 2f) * 1000, 270);
            builder.setFirstPoint(GeoPointMetaData.wrap(firstPoint));

            GeoPoint secondPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), ((rows + 1) / 2f) * 1000, 180);
            secondPoint = DistanceCalculations.metersFromAtBearing(secondPoint,
                    (columns / 2f) * 1000, 90);
            builder.setSecondPoint(GeoPointMetaData.wrap(secondPoint));

        } else {
            GeoPoint firstPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), ((rows - 1) / 2f) * 1000, 0);
            firstPoint = DistanceCalculations.metersFromAtBearing(firstPoint,
                    ((columns - 1) / 2f) * 1000, 270);
            builder.setFirstPoint(GeoPointMetaData.wrap(firstPoint));

            GeoPoint secondPoint = DistanceCalculations.metersFromAtBearing(
                    point.get(), ((rows + 1) / 2f) * 1000, 180);
            secondPoint = DistanceCalculations.metersFromAtBearing(secondPoint,
                    ((columns + 1) / 2f) * 1000, 90);
            builder.setSecondPoint(GeoPointMetaData.wrap(secondPoint));
        }
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

    private static String roundToKm(String value) {
        String roundedValue;
        String subString = value.substring(0, 2);
        int numberToCheck = Integer.parseInt(value.substring(2, 3));
        int kmValue = Integer.parseInt(subString);
        if (numberToCheck >= 5) {
            kmValue++;
        }
        if (kmValue <= 9) {
            roundedValue = "0" + kmValue + "000";
        } else {
            roundedValue = kmValue + "000";
        }
        return roundedValue;
    }

    private static String roundToKm(String value, boolean ceiling) {
        String roundedValue;
        String subString = value.substring(0, 2);
        int kmValue = Integer.parseInt(subString);
        if (ceiling) {
            kmValue++;
        }
        if (kmValue <= 9) {
            roundedValue = "0" + kmValue + "000";
        } else {
            roundedValue = kmValue + "000";
        }
        return roundedValue;

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
}
