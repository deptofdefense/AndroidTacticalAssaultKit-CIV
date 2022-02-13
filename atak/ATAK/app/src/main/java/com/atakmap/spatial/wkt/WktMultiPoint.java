
package com.atakmap.spatial.wkt;

import com.atakmap.android.maps.MapItem;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.ByteBuffer;
import java.util.List;

public class WktMultiPoint extends WktGeometry {

    final List<WktPoint> points;

    protected WktMultiPoint(List<WktPoint> points) {
        this.points = points;
    }

    public static WktMultiPoint fromText(String text) {
        WktLinestring parseLine = WktLinestring.fromText(text);
        if (parseLine == null)
            return null;
        return new WktMultiPoint(parseLine.points);
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
                            0x01, items);
        }
        return retval;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " " + points;
    }

    @Override
    public void toMapItems(List<MapItem> items) {
        for (WktPoint point : points) {
            point.toMapItems(items);
        }
    }

}
