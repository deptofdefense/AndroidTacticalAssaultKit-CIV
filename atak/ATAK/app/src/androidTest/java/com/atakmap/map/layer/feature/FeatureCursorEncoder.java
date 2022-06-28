
package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.cursor.FeatureCursorWrapper;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.spatial.SpatialCalculator;

import java.nio.ByteBuffer;

public class FeatureCursorEncoder extends FeatureCursorWrapper {
    final int geomCoding;
    final int styleCoding;

    public FeatureCursorEncoder(FeatureCursor impl, int geomCoding,
            int styleCoding) {
        super(impl);

        this.geomCoding = geomCoding;
        this.styleCoding = styleCoding;
    }

    @Override
    public int getGeomCoding() {
        return this.geomCoding;
    }

    @Override
    public int getStyleCoding() {
        return this.styleCoding;
    }

    @Override
    public Object getRawGeometry() {
        if (this.geomCoding == this.impl.getGeomCoding())
            return this.impl.getRawGeometry();

        final Geometry srcGeom = this.impl.get().getGeometry();
        if (srcGeom == null)
            return null;

        switch (this.geomCoding) {
            case GEOM_ATAK_GEOMETRY:
                return srcGeom;
            case GEOM_SPATIALITE_BLOB:
                return toSpatiaLiteBlob(srcGeom);
            case GEOM_WKB:
                return toWkb(srcGeom);
            case GEOM_WKT:
                return toWkt(srcGeom);
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public Object getRawStyle() {
        if (this.styleCoding == this.impl.getStyleCoding())
            return this.impl.getRawStyle();

        final Style srcStyle = this.impl.get().getStyle();
        if (srcStyle == null)
            return null;

        switch (this.styleCoding) {
            case STYLE_ATAK_STYLE:
                return srcStyle;
            case STYLE_OGR:
                return FeatureStyleParser.pack(srcStyle);
            default:
                throw new IllegalStateException();
        }
    }

    static byte[] toWkb(Geometry geom) {
        if (geom == null)
            return null;
        byte[] wkb = new byte[geom.computeWkbSize()];
        geom.toWkb(ByteBuffer.wrap(wkb));
        return wkb;
    }

    static byte[] toSpatiaLiteBlob(Geometry geom) {
        if (geom == null)
            return null;
        SpatialCalculator calc = null;
        try {
            calc = new SpatialCalculator.Builder().inMemory().build();
            return calc.getGeometryAsBlob(calc.createGeometry(geom));
        } finally {
            if (calc != null)
                calc.dispose();
        }
    }

    static String toWkt(Geometry geom) {
        if (geom == null)
            return null;
        SpatialCalculator calc = null;
        try {
            calc = new SpatialCalculator.Builder().inMemory().build();
            return calc.getGeometryAsWkt(calc.createGeometry(geom));
        } finally {
            if (calc != null)
                calc.dispose();
        }
    }
}
