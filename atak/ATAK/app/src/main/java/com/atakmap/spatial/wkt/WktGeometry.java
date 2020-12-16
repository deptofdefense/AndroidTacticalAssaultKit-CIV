
package com.atakmap.spatial.wkt;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.UUID;

public abstract class WktGeometry {

    private final static String TAG = "WktGeometry";

    String name = null;
    FeatureStyle style = null;

    public void setName(String name) {
        this.name = name;
    }

    public void setStyle(FeatureStyle style) {
        this.style = style;
    }

    /**
     * Parses a well known text geometry and creates a list of WktGeometry objects
     * @param text the well known text
     * @return the list of WtkGeometry objects
     */
    public static List<? extends WktGeometry> parse(String text) {
        LinkedList<WktGeometry> ret = new LinkedList<>();
        String geomType = getGeomType(text);
        if ("POINT".equalsIgnoreCase(geomType)) {
            ret.add(WktPoint.fromText(text));
        } else if ("LINESTRING".equalsIgnoreCase(geomType)) {
            ret.add(WktLinestring.fromText(text));
        } else if ("MULTIPOINT".equalsIgnoreCase(geomType)) {
            ret.add(WktMultiPoint.fromText(text));
        } else if ("POLYGON".equalsIgnoreCase(geomType)) {
            ret.add(WktPolygon.fromText(text));
        } else if ("MULTILINESTRING".equalsIgnoreCase(geomType)) {
            ret.add(WktMultiLineString.fromText(text));
        } else if ("MULTIPOLYGON".equalsIgnoreCase(geomType)) {
            ret.add(WktMultiPolygon.fromText(text));
            // } else if("GEOMETRYCOLLECTION".equalsIgnoreCase(geomType)) {
            // ret.add(WktGeometryCollection.fromText(text));
        } else {
            Log.e(TAG, "Unrecognized type: " + geomType);
        }
        return ret;
    }

    /**
     * Parses a well known binary geometry and creates a list of WktGeometry objects
     * @param wkb the well known binary
     * @return the list of WtkGeometry objects
     */
    public static List<? extends WktGeometry> fromWkb(ByteBuffer wkb) {
        throw new UnsupportedOperationException();
    }

    /**
     * Implementation from http://www.gaia-gis.it/gaia-sins/BLOB-Geometry.html
     * @param name the name of the spatial lite blob
     * @param blob the blob
     * @param style the styling for the blob
     * @param retval the list of map items to return
     * @return the number of items.
     */
    public static int fromSpatiaLiteBlob(String name, ByteBuffer blob,
            FeatureStyle style,
            List<MapItem> retval) {
        skip(blob, 1);
        switch (blob.get() & 0xFF) {
            case 0x00:
                blob.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01:
                blob.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default:
                Log.e(TAG, "Invalid endian");
                return 0;
        }

        skip(blob, 37);
        switch (blob.getInt()) {
            case 0x01: // POINT
                return WktPoint.fromSpatiaLiteBlobGeometryClass(name, blob,
                        style, retval);
            case 0x02: // LINESTRING
                return WktLinestring.fromSpatiaLiteBlobGeometryClass(name,
                        blob, style, retval);
            case 0x03: // POLYGON
                return WktPolygon.fromSpatiaLiteBlobGeometryClass(name, blob,
                        style, retval);
            case 0x04: // MULTIPOINT
                return WktMultiPoint.fromSpatiaLiteBlobGeometryClass(name,
                        blob, style, retval);
            case 0x05: // MULTILINESTRING
                return WktMultiLineString
                        .fromSpatiaLiteBlobGeometryClass(name, blob, style,
                                retval);
            case 0x06: // MULTIPOLYGON
                return WktMultiPolygon.fromSpatiaLiteBlobGeometryClass(name,
                        blob, style, retval);
            case 0x07: // GEOMETRYCOLLECTION
                return WktGeometryCollection.fromSpatiaLiteBlobGeometryClass(
                        name, blob, style,
                        retval);
            default:
                Log.e(TAG, "Unrecognized type");
                break;
        }

        return 0;
    }

    /**
     * Implementation from http://www.gaia-gis.it/gaia-sins/BLOB-Geometry.html
     * @param name the name of the spatial lite blob
     * @param blob the blob
     * @param style the styling for the blob
     * @param requiredClass the classtype to restrict to.
     * @param retval the list of map items to return
     * @return the number of items.
     */
    public static int fromSpatiaLiteBlobCollectionEntity(String name,
            ByteBuffer blob,
            FeatureStyle style, int requiredClass, List<MapItem> retval) {
        if ((blob.get() & 0xFF) != 0x69)
            return 0;

        final int classType = blob.getInt();
        if (requiredClass != 0x00 && requiredClass != classType) {
            Log.e(TAG, "Class type " + requiredClass + " required, "
                    + classType + " encountered.");
            return 0;
        }

        switch (classType) {
            case 0x01: // POINT
                return WktPoint.fromSpatiaLiteBlobGeometryClass(name, blob,
                        style, retval);
            case 0x02: // LINESTRING
                return WktLinestring.fromSpatiaLiteBlobGeometryClass(name,
                        blob, style, retval);
            case 0x03: // POLYGON
                return WktPolygon.fromSpatiaLiteBlobGeometryClass(name, blob,
                        style, retval);
            default:
                Log.w(TAG, "Unrecognized type: " + classType);
                return 0;
        }
    }

    static String getGeomType(String text) {
        return text.split("[(|\\s]")[0].trim().toUpperCase(
                LocaleUtil.getCurrent());
    }

    public abstract void toMapItems(List<MapItem> items);

    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    static Buffer skip(ByteBuffer buffer, int num) {
        return buffer.position(buffer.position() + num);
    }
}
