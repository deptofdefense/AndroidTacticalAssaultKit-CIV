
package com.atakmap.spatial.wkt;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class WktLinestring extends WktGeometry {

    public static final String TAG = "WktLinestring";

    final List<WktPoint> points;

    protected WktLinestring(List<WktPoint> points) {
        this.points = points;
    }

    static GeoPointMetaData getPoint(final double lat, final double lon) {
        try {
            return ElevationManager.getElevationMetadata(lat, lon, null);
        } catch (Exception e) {
            return GeoPointMetaData.wrap(new GeoPoint(lat, lon));
        }

    }

    public static WktLinestring fromText(String text) {
        List<WktPoint> ret = new LinkedList<>();
        String[] points = text.replaceAll("[^,\\d\\s-.]", "").trim().split(",");
        for (String point : points) {
            WktPoint geom = WktPoint.fromText(point);
            if (geom != null)
                ret.add(geom);
        }
        if (ret.isEmpty())
            return null;
        return new WktLinestring(ret);
    }

    public static int fromSpatiaLiteBlobGeometryClass(String name,
            ByteBuffer blob,
            FeatureStyle style, List<MapItem> items) {
        final int numPoints = blob.getInt();
        if (numPoints == 0)
            return 0;

        GeoPointMetaData[] pts = new GeoPointMetaData[numPoints];
        double x;
        double y;
        for (int i = 0; i < numPoints; i++) {
            x = blob.getDouble();
            y = blob.getDouble();
            pts[i] = getPoint(y, x);
        }

        Polyline poly = null;
        if (pts.length > 1) {
            poly = new Polyline(UUID.randomUUID().toString());
            poly.setType("u-d-wkt");
            poly.setPoints(pts);// getCoordinates());
            if (name != null)
                poly.setTitle(name);
            if (style != null) {
                if (style.pen != null) {
                    poly.setStrokeColor(style.pen.color);
                    poly.setStrokeWeight(style.pen.width);
                }

                // only close-off the line if a PolyStyle exists
                if (style.brush != null && isClosed(pts)) {
                    // Has to be closed or a fill won't draw.
                    // And linearrings are always closed anyway.
                    poly.addStyleBits(Polyline.STYLE_CLOSED_MASK);

                    poly.setFillColor(style.brush.foreColor);

                    if (style.brush.foreColor != 0) {
                        poly.addStyleBits(Shape.STYLE_FILLED_MASK);
                    } else {
                        poly.removeStyleBits(Shape.STYLE_FILLED_MASK);
                    }

                    if (style.pen != null && style.pen.color != 0) {
                        poly.addStyleBits(Polyline.STYLE_OUTLINE_STROKE_MASK);
                    } else {
                        poly.removeStyleBits(
                                Polyline.STYLE_OUTLINE_STROKE_MASK);
                    }
                }
            }
        }

        if (poly != null) {
            items.add(poly);
            return 1;
        } else {
            return 0;
        }
    }

    public String toString() {
        return this.getClass().getSimpleName() + " " + points;
    }

    @Override
    public void toMapItems(List<MapItem> items) {
        Polyline poly = null;
        GeoPointMetaData[] pts = getCoordinates();
        if (pts.length > 1) {
            poly = new Polyline(UUID.randomUUID().toString());
            poly.setType("u-d-wkt");
            poly.setPoints(pts);// getCoordinates());
            if (name != null)
                poly.setTitle(name);
            if (style != null) {
                if (style.pen != null) {
                    poly.setStrokeColor(style.pen.color);
                    poly.setStrokeWeight(style.pen.width);
                }

                // only close-off the line if a PolyStyle exists
                if (style.brush != null && isClosed(pts)) {
                    // Has to be closed or a fill won't draw.
                    // And linearrings are always closed anyway.
                    poly.addStyleBits(Polyline.STYLE_CLOSED_MASK);

                    poly.setFillColor(style.brush.foreColor);

                    if (style.brush.foreColor != 0) {
                        poly.addStyleBits(Shape.STYLE_FILLED_MASK);
                    } else {
                        poly.removeStyleBits(Shape.STYLE_FILLED_MASK);
                    }

                    if (style.pen.color != 0) {
                        poly.addStyleBits(Polyline.STYLE_OUTLINE_STROKE_MASK);
                    } else {
                        poly.removeStyleBits(
                                Polyline.STYLE_OUTLINE_STROKE_MASK);
                    }
                }
            }
        }

        items.add(poly);
    }

    public GeoPointMetaData[] getCoordinates() {
        GeoPointMetaData[] ret = new GeoPointMetaData[points.size()];
        for (int i = 0; i < ret.length; i++)
            ret[i] = GeoPointMetaData.wrap(points.get(i).getCoordinates()[0]);
        return ret;
    }

    private static boolean isClosed(GeoPointMetaData[] pts) {
        return pts != null && pts.length > 1
                && pts[0].equals(pts[pts.length - 1]);
    }
}
