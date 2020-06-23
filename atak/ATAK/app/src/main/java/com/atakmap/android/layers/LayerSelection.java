
package com.atakmap.android.layers;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.math.Rectangle;

import java.util.Collection;

import com.atakmap.coremap.locale.LocaleUtil;

public class LayerSelection {

    private final Geometry coverage;
    private final double minResolution;
    private final double maxResolution;
    private Object tag;
    private final String _specialName;
    private final double _west, _east, _south, _north;
    private final String _name;
    public static final String MULTI_TYPE = "Multiple Types";

    public LayerSelection(String tsInfoName, Geometry coverage, double minRes,
            double maxRes) {
        _name = tsInfoName;
        this.coverage = coverage;
        this.minResolution = minRes;
        this.maxResolution = maxRes;

        _specialName = _determineSpecialName(tsInfoName);

        Envelope mbb = this.coverage.getEnvelope();

        _north = mbb.maxY;
        _east = mbb.maxX;
        _south = mbb.minY;
        _west = mbb.minX;

    }

    Object getTag() {
        return this.tag;
    }

    void setTag(Object o) {
        this.tag = o;
    }

    public String getName() {
        return _name;
    }

    public double getMaxRes() {
        return maxResolution;
    }

    public double getMinRes() {
        return minResolution;
    }

    public double getEast() {
        return _east;
    }

    public double getNorth() {
        return _north;
    }

    public double getSouth() {
        return _south;
    }

    public double getWest() {
        return _west;
    }

    public Geometry getBounds() {
        return this.coverage;
    }

    private String _determineSpecialName(String name) {

        String n = name.toLowerCase(LocaleUtil.getCurrent());
        if (n.contains("ctpc")) {
            return "TPC";
        } else if (n.contains("ctlm50")) {
            return "TLM50";
        } else if (n.contains("ctlm100")) {
            return "TLM100";
        } else if (n.contains("cjga")) {
            return "JGA";
        } else if (n.contains("cjnc")) {
            return "JNC";
        } else if (n.contains("cib1")) {
            return "CIB1M";
        } else if (n.contains("cib5")) {
            return "CIB5M";
        } else if (n.contains("cgnc")) {
            return "GNC";
        } else if (n.contains("geotiff")) {
            if (n.contains("1m")) {
                return "GEOTIFF 1M";
            }
            return "GEOTIFF";
        } else if (n.contains("cmim50")) {
            return "MIM50";
        } else if (n.contains("clc")) {
            return "LC";
        } else if (n.contains("mm50")) {
            return "MIL50M";
        } else if (n.endsWith("sqlite")) {
            return "SQLite";
        } else if (n.endsWith("sid")) {
            return "MRSID";
        } else if (n.endsWith("tiff") || n.endsWith("tif")) {
            return "GEOTIFF";
        } else if (n.endsWith("nfw") || n.endsWith("ntf")) {
            return "NITF";
        }

        return "---";
    }

    public String toString() {
        return this.getName();
    }

    /**************************************************************************/

    public static boolean boundsContains(LayerSelection ls, GeoPoint g) {
        return contains(ls.getBounds(),
                new Point(g.getLongitude(), g.getLatitude()));
    }

    private static boolean contains(Geometry geom, Point p) {
        final Envelope mbb = geom.getEnvelope();
        if (!Rectangle.contains(mbb.minX, mbb.minY,
                mbb.maxX, mbb.maxY,
                p.getX(), p.getY())) {
            return false;
        }

        if (geom instanceof GeometryCollection) {
            Collection<Geometry> children = ((GeometryCollection) geom)
                    .getGeometries();
            for (Geometry child : children) {
                if (contains(child, p))
                    return true;
            }
            return false;
        } else {
            // XXX - linestring
            // XXX - polygon
            return true;
        }
    }

    public static GeoPoint boundsGetCenterNearest(LayerSelection ls,
            GeoPoint g) {
        final Geometry geom = ls.getBounds();
        if (g == null)
            g = getCenter(geom);

        Geometry closest = getCoverageNearest(ls, g);
        if (closest == null)
            closest = geom;
        return getCenter(closest);
    }

    private static GeoPoint getCenter(Geometry geom) {
        final Envelope mbb = geom.getEnvelope();
        return new GeoPoint((mbb.maxY + mbb.minY) / 2.0d,
                (mbb.maxX + mbb.minX) / 2.0d);
    }

    public static Geometry getCoverageNearest(LayerSelection ls, GeoPoint p) {
        final Geometry g = ls.getBounds();
        if (ls.getBounds() instanceof GeometryCollection) {
            return getCoverageNearest((GeometryCollection) g, p, new double[] {
                    Double.MAX_VALUE
            });
        } else {
            return g;
        }
    }

    public static Geometry getCoverageNearest(GeometryCollection gc, GeoPoint p,
            double[] dsq) {
        Geometry candidate = null;
        double minDist = Double.MAX_VALUE;
        for (Geometry child : gc.getGeometries()) {
            Envelope mbb = child.getEnvelope();
            if (Rectangle.contains(mbb.minX, mbb.minY,
                    mbb.maxX, mbb.maxY,
                    p.getLongitude(), p.getLatitude())) {

                // geometry contains the point, we're done
                dsq[0] = 0d;
                return child;
            } else if (child instanceof GeometryCollection) {
                // recurse for the nearest descendant
                Geometry nearestChild = getCoverageNearest(
                        (GeometryCollection) child, p, dsq);
                // if closer than the current candidate, update
                if (dsq[0] < minDist) {
                    minDist = dsq[0];
                    candidate = nearestChild;

                    // the new candidate contains, we're done
                    if (minDist == 0d)
                        break;
                }
            } else {
                // XXX - we could compute the point for comparison in a couple
                //       of ways -- I think that the closest center is probably
                //       the most intuitive in terms of how the downstream code
                //       will be utilizing
                // compute closest point of coverage to POI
                //final double closestX = MathUtils.clamp(p.getLongitude(), mbb.minX, mbb.maxX);
                //final double closestY = MathUtils.clamp(p.getLongitude(), mbb.minX, mbb.maxX);

                final double closestX = (mbb.minX + mbb.maxX) / 2d;
                final double closestY = (mbb.minY + mbb.maxY) / 2d;

                // compute distance
                dsq[0] = p.distanceTo(new GeoPoint(closestY, closestX));
                if (dsq[0] < minDist) {
                    minDist = dsq[0];
                    candidate = child;
                }
            }
        }

        dsq[0] = minDist;
        return candidate;
    }
}
