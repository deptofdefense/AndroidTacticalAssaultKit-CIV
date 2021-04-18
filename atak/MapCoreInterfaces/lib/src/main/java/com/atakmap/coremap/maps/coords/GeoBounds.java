
package com.atakmap.coremap.maps.coords;

import java.util.Objects;

public class GeoBounds {

    protected double _south, _west;
    protected double _north, _east;
    protected boolean _wrap180;

    public GeoBounds(GeoPoint a, GeoPoint b) {
        this(a.getLatitude(), a.getLongitude(), b.getLatitude(), b
                .getLongitude());
    }

    public GeoBounds(GeoBounds other) {
        this(other.getSouth(), other.getWest(), other.getNorth(), other
                .getEast(), true);
        setWrap180(other._wrap180);
    }

    public GeoBounds(double lat0, double lng0, double lat1, double lng1) {
        this((lat0 < lat1) ? lat0 : lat1,
                (lng0 < lng1) ? lng0 : lng1,
                (lat0 > lat1) ? lat0 : lat1,
                (lng0 > lng1) ? lng0 : lng1,
                true);
    }

    protected GeoBounds(double south, double west, double north, double east,
            boolean ignored) {
        _south = south;
        _west = west;
        _north = north;
        _east = east;

    }

    public double getWest() {
        return _west;
    }

    public double getSouth() {
        return _south;
    }

    public double getNorth() {
        return _north;
    }

    public double getEast() {
        return _east;
    }

    public boolean intersects(GeoBounds b) {
        return this.intersects(b._north, b._west, b._south, b._east,
                b._wrap180);
    }

    public boolean intersects(double north, double west, double south,
            double east, boolean wrap180) {
        if (_north < south || _south > north)
            return false;
        boolean bCross = wrap180 && Math.abs(east - west) > 180;
        if (crossesIDL()) {
            if (bCross)
                return true;
            if (west > _west && east < _east)
                return false;
            return true;
        } else if (bCross) {
            if (_west > west && _east < east)
                return false;
            return true;
        }
        return !(_east < west || _west > east);
    }

    public boolean intersects(double north, double west, double south,
            double east) {
        return intersects(north, west, south, east, false);
    }

    public boolean contains(GeoBounds b) {
        if (b._south < _south || b._north > _north)
            return false;
        boolean bCross = b.crossesIDL();
        if (crossesIDL()) {
            if (bCross) {
                return b._east >= _east && b._west <= _west;
            } else {
                return b._west >= _east && b._east <= _west + 360
                        || b._west >= _east - 360 && b._east <= _west;
            }
        } else if (bCross)
            return false;
        return b._east <= _east && b._west >= _west;
    }

    public boolean contains(GeoPoint p) {
        if (p.getLatitude() < _south || p.getLatitude() > _north)
            return false;
        if (crossesIDL()) {
            return p.getLongitude() >= _east
                    && p.getLongitude() <= _west + 360
                    || p.getLongitude() >= _east - 360
                            && p.getLongitude() <= _west;
        }
        return p.getLongitude() >= _west &&
                p.getLongitude() <= _east;
    }

    public GeoPoint getCenter(GeoPoint r) {
        double lat = (_north + _south) / 2.0d;
        double lng = (_east + _west) / 2.0d;
        if (crossesIDL()) {
            lng = ((_west + 360) + _east) / 2.0d;
            if (lng > 180)
                lng -= 360;
        }
        GeoPoint retval = r;
        if (r != null && r.isMutable())
            r.set(lat, lng);
        else
            retval = new GeoPoint(lat, lng);
        return retval;
    }

    /**
     * Set whether these bounds should wrap around the IDL if the longitudinal
     * span is >180 degrees
     * Should be used for bounds that are typically small
     * @param wrap180 True to wrap around backwards
     *                False to leave as is (default)
     */
    public void setWrap180(boolean wrap180) {
        _wrap180 = wrap180;
    }

    public boolean crossesIDL() {
        return _wrap180 && Math.abs(_east - _west) > 180;
    }

    @Override
    public String toString() {
        return "GeoBounds {north=" + _north + ", west=" + _west + ", south="
                + _south + ", east="
                + _east + "}";
    }

    public static GeoBounds createFromPoints(GeoPoint[] points,
            boolean wrap180) {
        if (points.length == 0)
            return new GeoBounds(0, 0, 0, 0);
        MutableGeoBounds mgb = new MutableGeoBounds(0, 0, 0, 0);
        mgb.set(points, wrap180);
        GeoBounds bounds = new GeoBounds(mgb);
        bounds.setWrap180(wrap180);
        return bounds;
    }

    public static GeoBounds createFromPoints(GeoPoint[] points) {
        return createFromPoints(points, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeoBounds geoBounds = (GeoBounds) o;
        return Double.compare(geoBounds._south, _south) == 0 &&
                Double.compare(geoBounds._west, _west) == 0 &&
                Double.compare(geoBounds._north, _north) == 0 &&
                Double.compare(geoBounds._east, _east) == 0 &&
                _wrap180 == geoBounds._wrap180;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_south, _west, _north, _east, _wrap180);
    }
}
