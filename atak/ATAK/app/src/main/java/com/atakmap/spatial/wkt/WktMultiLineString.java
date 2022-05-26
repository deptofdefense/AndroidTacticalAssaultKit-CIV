
package com.atakmap.spatial.wkt;

import com.atakmap.android.maps.MapItem;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.ByteBuffer;
import java.util.List;

public class WktMultiLineString extends WktGeometry {

    final List<WktLinestring> lines;

    private WktMultiLineString(List<WktLinestring> lines) {
        this.lines = lines;
    }

    public static WktMultiLineString fromText(String text) {
        WktPolygon parsePoly = WktPolygon.fromText(text);
        if (parsePoly == null)
            return null;
        return new WktMultiLineString(parsePoly.lines);
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
                            0x02, items);
        }
        return retval;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " " + lines;
    }

    @Override
    public void toMapItems(List<MapItem> items) {
        for (WktLinestring line : lines) {
            if (name != null)
                line.setName(name);
            if (style != null)
                line.setStyle(style);
            line.toMapItems(items);
        }
    }
}
