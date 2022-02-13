
package com.atakmap.spatial.wkt;

import com.atakmap.android.maps.MapItem;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WktPolygon extends WktGeometry {

    final List<WktLinestring> lines;

    private WktPolygon(List<WktLinestring> lines) {
        this.lines = lines;
    }

    public WktLinestring getOuterBoundaryIs() {
        WktLinestring ret = null;
        if (lines.size() > 0)
            ret = lines.get(0);

        // Set style
        if (ret != null)
            ret.setStyle(style);

        return ret;
    }

    public List<WktLinestring> getInnerBoundaryIs() {
        if (lines.size() < 2)
            return null;

        List<WktLinestring> ret = new ArrayList<>(
                lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            WktLinestring line = lines.get(i);

            // Set style
            if (line != null)
                line.setStyle(style);

            ret.add(lines.get(i));
        }
        return ret;
    }

    public static WktPolygon fromText(String text) {
        List<WktLinestring> ret = new LinkedList<>();
        String[] lines = text.replaceAll("[^,\\d\\s()-.]", "").trim()
                .split("[()]");
        for (String line : lines) {
            WktLinestring geom = WktLinestring.fromText(line);
            if (geom != null)
                ret.add(geom);
        }
        if (ret.isEmpty())
            return null;
        return new WktPolygon(ret);
    }

    public static int fromSpatiaLiteBlobGeometryClass(String name,
            ByteBuffer blob,
            FeatureStyle style, List<MapItem> items) {
        final int numRings = blob.getInt();
        if (numRings == 0)
            return 0;

        int retval = 0;
        for (int i = 0; i < numRings; i++) {
            retval += WktLinestring.fromSpatiaLiteBlobGeometryClass(name, blob,
                    style, items);

            // XXX - ATAK polygons do not support holes, don't bother with inner
            // rings
            if (i == 0)
                break;
        }
        return retval;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " " + lines;
    }

    private void getOuterMapItem(List<MapItem> items) {
        WktLinestring outer = getOuterBoundaryIs();
        if (outer != null) {
            outer.toMapItems(items);
        }
    }

    private void getInnerMapItems(List<MapItem> items) {
        List<WktLinestring> inner = getInnerBoundaryIs();
        if (inner != null) {
            for (WktLinestring ls : inner) {
                ls.toMapItems(items);
            }
        }
    }

    @Override
    public void toMapItems(List<MapItem> items) {
        // XXX - inner rings are holes....
        // getInnerMapItems(items);

        final List<MapItem> outerItem = new ArrayList<>(1);
        getOuterMapItem(outerItem);
        if (outerItem.size() > 0) {
            if (name != null)
                outerItem.get(0).setTitle(name);
            items.addAll(outerItem);
        }
    }
}
