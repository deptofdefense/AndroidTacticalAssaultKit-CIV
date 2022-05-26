
package com.atakmap.coremap.maps.coords;

import java.util.Objects;

public class GeoBounds {

    protected double _south, _west;
    protected double _north, _east;
    protected double _minAltitude, _maxAltitude;
    protected boolean _wrap180;

    public GeoBounds(GeoPoint a, GeoPoint b) {
        this(a.getLatitude(), a.getLongitude(), b.getLatitude(), b
                .getLongitude());

        double aHAE = Double.NaN;
        double bHAE = Double.NaN;

        // only support non-agl altitudes at this point //
        if (a.isAltitudeValid() && a.getAltitudeReference().equals(GeoPoint.AltitudeReference.HAE))
            aHAE = a.getAltitude();
        if (b.isAltitudeValid() && b.getAltitudeReference().equals(GeoPoint.AltitudeReference.HAE))
            bHAE = b.getAltitude();

        setAltitudes(aHAE,bHAE);
    }

    /**
     * Collapses the computation logic for setting the altitudes.
     * @param a the first altitude
     * @param b the second altitude
     */
    protected void setAltitudes(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            // no concept defined for min and max since one of the altitudes is not defined, just set
            // them in order
            _minAltitude = a;
            _maxAltitude = b;
        } else if (Double.compare(a, b) == 0){
            // concept that that two values are the same, right now set them to be the same value
            _minAltitude = a;
            _maxAltitude = b;
        } else {
            _minAltitude = Math.min(a, b);
            _maxAltitude = Math.max(a, b);
        }
    }

    public GeoBounds(GeoBounds other) {
        this(other.getSouth(), other.getWest(), other.getNorth(), other
                .getEast(), true, other.getMinAltitude(), other.getMaxAltitude());
        setWrap180(other._wrap180);
    }

    public GeoBounds(double lat0, double lng0, double lat1, double lng1) {
        this((lat0 < lat1) ? lat0 : lat1,
                (lng0 < lng1) ? lng0 : lng1,
                (lat0 > lat1) ? lat0 : lat1,
                (lng0 > lng1) ? lng0 : lng1,
                true);
    }

    public GeoBounds(double lat0, double lng0, double alt0, double lat1, double lng1, double alt1)
    {
        this(lat0, lng0, lat1, lng1);
        setAltitudes(alt0, alt1);
    }

    protected GeoBounds(double south, double west, double north, double east,
            boolean ignored, double minHAE, double maxHAE) {
        _south = south;
        _west = west;
        _north = north;
        _east = east;
        _minAltitude = minHAE;
        _maxAltitude = maxHAE;
    }

    protected GeoBounds(double south, double west, double north, double east, boolean ignored)
    {
        this(south, west, north, east, ignored, Double.NaN, Double.NaN);
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

    /**
     * Returns the minimum altitude for a geobound and could be the same as the maximum altitude.
     * Please note, this could also be Double.NaN which means the altitude is not valid and should
     * be ignored during calculations.
     * @return the minimum altitude.
     */
    public double getMinAltitude() { return _minAltitude; }

    /**
     * Returns the maximum altitude for a geobound and could be the same as the maximum altitude.
     * Please note, this could also be Double.NaN which means the altitude is not valid and should
     * be ignored during calculations.
     * @return the maximum altitude.
     */
    public double getMaxAltitude() { return _maxAltitude; }

    public boolean intersects(GeoBounds b) {
        return this.intersects(b._north, b._west, b._south, b._east,
                b._wrap180, b._minAltitude, b._maxAltitude);
    }

    /**
     * Perform intersection between a geobound and a set of values that define a geometry.
     * @param north the north value
     * @param west the west value
     * @param south the south value
     * @param east the east value
     * @param wrap180 if the geometry wraps the IDL line
     * @param minAltitude the minimum altitude preferably in HAE.  If it is NaN, the altitudes
     *                    will not be used.
     * @param maxAltitude the maximum altitude preferably in HAE.  If it is NaN, the altitudes
     *                    will not be used.
     * @return
     */
    public boolean intersects(double north, double west, double south,
            double east, boolean wrap180, double minAltitude, double maxAltitude) {

        if (Double.isNaN(_maxAltitude) || Double.isNaN(_minAltitude) ||
                Double.isNaN(maxAltitude) || Double.isNaN(minAltitude)) {
            // since one or more of the altitude values is not defined, skip the altitude check
            // for intersection
        } else {
            if (_maxAltitude < minAltitude || _minAltitude > maxAltitude)
                return false;
        }
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
                              double east, boolean wrap180) {
        return intersects(north, west, south, east, wrap180, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public boolean intersects(double north, double west, double south,
                              double east) {
        return intersects(north, west, south, east, false);
    }

    public boolean contains(GeoBounds b) {
        if (Double.isNaN(_maxAltitude) || Double.isNaN(_minAltitude) ||
                Double.isNaN(b._maxAltitude) || Double.isNaN(b._minAltitude)) {
            // since one or more of the altitude values is not defined, skip the altitude check
            // for intersection
        } else {
            if (b._maxAltitude > _maxAltitude || b._minAltitude < _minAltitude)
                return false;
        }

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
        if (Double.isNaN(_maxAltitude) || Double.isNaN(_minAltitude) ||
                Double.isNaN(p.getAltitude()) ) {
            // since one or more of the altitude values is not defined, skip the altitude check
            // for intersection
        } else {
            if (_maxAltitude < p.getAltitude() || _minAltitude > p.getAltitude())
                return false;
        }

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
        double alt = (_maxAltitude + _minAltitude) / 2.0d;
        if (crossesIDL()) {
            lng = ((_west + 360) + _east) / 2.0d;
            if (lng > 180)
                lng -= 360;
        }
        GeoPoint retval = r;
        if (r != null && r.isMutable())
            r.set(lat, lng, alt);
        else
            retval = new GeoPoint(lat, lng, alt);
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


    /**
     * Returns true if the GeoBounds has been constructed so that it crosses the IDL (ie the most
     * easterly point and the most westerly point are greater than 180 degrees.
     * @return true if bounds crosses the IDL
     */
    public boolean crossesIDL() {
        return _wrap180 && Math.abs(_east - _west) > 180;
    }

    @Override
    public String toString() {
        return "GeoBounds {north=" + _north + ", west=" + _west + ", south="
                + _south + ", east="
                + _east + ", minHAE=" + _minAltitude +", maxHAE=" + _maxAltitude + "}";
    }

    public static GeoBounds createFromPoints(GeoPoint[] points,
            boolean wrap180) {
        if (points.length == 0)
            return new GeoBounds(0, 0, 0, 0);
        MutableGeoBounds mgb = new MutableGeoBounds(0, 0, 0, 0);
        mgb.set(points, wrap180);
        GeoBounds bounds = new GeoBounds(mgb);
        bounds.setWrap180(wrap180);


        double minAlt = Double.MAX_VALUE;
        double maxAlt = -1 * Double.MAX_VALUE;
        GeoPoint.AltitudeReference reference = null;
        boolean invalid = false;

        for (GeoPoint geoPoint : points) {
            if (reference == null)
                reference = geoPoint.getAltitudeReference();
            else {
                if (reference != geoPoint.getAltitudeReference())
                    invalid = true;
            }
            double geoPointAltitude = geoPoint.getAltitude();
            if (Double.isNaN(geoPointAltitude))
                invalid = true;
            else if (geoPointAltitude < minAlt)
                minAlt = geoPointAltitude;
            else if (geoPointAltitude > maxAlt)
                maxAlt = geoPointAltitude;

        }
        if (invalid) {
            bounds._minAltitude = Double.NaN;
            bounds._maxAltitude = Double.NaN;
        } else {
            bounds._minAltitude = minAlt;
            bounds._maxAltitude = maxAlt;
        }
        return bounds;
    }

    public static GeoBounds createFromPoints(GeoPoint[] points) {
        return createFromPoints(points, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GeoBounds geoBounds = (GeoBounds) o;
        return Double.compare(geoBounds._south, _south) == 0 &&
                Double.compare(geoBounds._west, _west) == 0 &&
                Double.compare(geoBounds._north, _north) == 0 &&
                Double.compare(geoBounds._east, _east) == 0 &&
                Double.compare(geoBounds._minAltitude, _minAltitude) == 0 &&
                Double.compare(geoBounds._maxAltitude, _maxAltitude) == 0 &&
                _wrap180 == geoBounds._wrap180;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_south, _west, _north, _east, _wrap180, _minAltitude, _maxAltitude);
    }

    // For dynamically building a GeoBounds instance from points or other bounds
    public static class Builder {

        private GeoBounds b = null;

        // For IDL calculations
        private double eastMinLng = Double.MAX_VALUE;
        private double westMaxLng = -Double.MAX_VALUE;
        private boolean handleIdlCross = true;

        public void setHandleIdlCross(boolean b) {
            handleIdlCross = b;
        }

        public void add(double lat, double lng, double alt) {
            if (b == null) {
                b = new GeoBounds(lat, lng, alt, lat, lng, alt);
                return;
            }
            if (lng < b._west) b._west = lng;
            if (lat < b._south) b._south = lat;
            if (lng > b._east) b._east = lng;
            if (lat > b._north) b._north = lat;
            if (lng > 0 && lng < eastMinLng)
                eastMinLng = lng;
            if (lng < 0 && lng > westMaxLng)
                westMaxLng = lng;
            if (!Double.isNaN(alt)) {
                if (Double.isNaN(b._minAltitude) || alt < b._minAltitude)
                    b._minAltitude = alt;
                if (Double.isNaN(b._maxAltitude) || alt > b._maxAltitude)
                    b._maxAltitude = alt;
            }
        }

        public void add(double lat, double lng) {
            add(lat, lng, Double.NaN);
        }

        public void add(GeoBounds other) {
            if (other == null)
                return;
            add(other._south, other._west, other._minAltitude);
            add(other._north, other._east, other._maxAltitude);
        }

        public void reset() {
            b = null;
            eastMinLng = Double.MAX_VALUE;
            westMaxLng = -Double.MAX_VALUE;
        }

        public GeoBounds build() {
            if (b == null)
                return null;

            // IDL bounds correction
            if(handleIdlCross) {
                if (b._west < -180 && westMaxLng > b._west)
                    b._east = westMaxLng;
                else if (b._east > 180 && eastMinLng < b._east)
                    b._west = eastMinLng;
            }
            return b;
        }
    }
}
