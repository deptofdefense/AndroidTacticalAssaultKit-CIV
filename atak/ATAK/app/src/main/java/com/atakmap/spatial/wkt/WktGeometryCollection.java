
package com.atakmap.spatial.wkt;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * TODO: implement this!
 * 
 * 
 */
public class WktGeometryCollection extends WktGeometry {

    public static final String TAG = "WktGeometryCollection";

    private final List<WktGeometry> entities;

    private WktGeometryCollection() {
        this.entities = new LinkedList<>();
    }

    public static WktGeometryCollection fromText(String text) {
        String stripped = text.replaceFirst("GEOMETRYCOLLECTION", "");
        // XXX: NOT WORKING!
        Scanner scanner = new Scanner(stripped).useDelimiter("^[\\p{Alnum}]+");
        while (scanner.hasNext())
            Log.d(TAG, scanner.next());
        return null;
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
                            0x00, items);
        }
        return retval;
    }

    @Override
    public void toMapItems(List<MapItem> items) {
        // XXX: NOT WORKING!
    }

}
