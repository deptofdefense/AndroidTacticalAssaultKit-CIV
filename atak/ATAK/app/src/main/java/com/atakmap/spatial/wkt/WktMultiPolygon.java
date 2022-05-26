
package com.atakmap.spatial.wkt;

import com.atakmap.android.maps.MapItem;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class WktMultiPolygon extends WktGeometry {

    final List<WktPolygon> polygons;

    private WktMultiPolygon(List<WktPolygon> polygons) {
        this.polygons = polygons;
    }

    public static WktMultiPolygon fromText(String text) {
        List<WktPolygon> ret = new LinkedList<>();
        String[] polys = text.replaceAll("[^,\\d\\s()-.]", "").trim()
                .split("[()]");
        for (String poly : polys) {
            WktPolygon geom = WktPolygon.fromText(poly);
            if (geom != null)
                ret.add(geom);
        }
        if (ret.isEmpty())
            return null;
        return new WktMultiPolygon(ret);
    }

    public static int fromSpatiaLiteBlobGeometryClass(String name,
            ByteBuffer blob,
            FeatureStyle style, List<MapItem> items) {
        final int numEntities = blob.getInt();
        if (numEntities == 0)
            return 0;

        int retval = 0;
        for (int i = 0; i < numEntities; i++) {
            retval += WktGeometry
                    .fromSpatiaLiteBlobCollectionEntity(name, blob, style,
                            0x03, items);
        }
        return retval;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " " + polygons;
    }

    @Override
    public void toMapItems(List<MapItem> items) {
        // TODO: How do we do this?!?!?
        // Since there is no first-class MapItem for a polygon with a center
        // missing,
        // I'm not sure if this will work properly... I'll give it a try anyway.

        if (polygons != null) {
            for (WktPolygon poly : polygons) {
                if (name != null)
                    poly.setName(name);
                if (style != null)
                    poly.setStyle(style);
                poly.toMapItems(items);
            }
        }
    }
}
