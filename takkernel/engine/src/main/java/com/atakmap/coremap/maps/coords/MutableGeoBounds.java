
package com.atakmap.coremap.maps.coords;

public class MutableGeoBounds extends GeoBounds {
    public MutableGeoBounds(GeoPoint a, GeoPoint b) {
        super(a, b);
    }

    public MutableGeoBounds() {
        this(0, 0, 0, 0);
    }

    public MutableGeoBounds(double lat0, double lng0, double lat1,
            double lng1) {
        super(lat0, lng0, lat1, lng1);
    }

    public MutableGeoBounds(GeoBounds other) {
        super(other);
    }

    public void set(GeoPoint a, GeoPoint b) {
        this.set(a.getLatitude(), a.getLongitude(), b.getLatitude(),
                b.getLongitude());
    }

    public void set(GeoBounds other) {
        this.setImpl(other.getSouth(), other.getWest(), other.getNorth(),
                other.getEast());
        this.setWrap180(other._wrap180);
        this.setMinAltitude(other._minAltitude);
        this.setMaxAltitude(other._maxAltitude);
    }

    public void set(GeoPoint[] pts, boolean wrap180) {
        this.set(pts, 0, pts.length, wrap180);
    }

    public void set(GeoPoint[] pts) {
        set(pts, false);
    }

    public void set(GeoPoint[] pts, int off, int len, boolean wrap180) {
        boolean crossesIDL = wrap180
                && GeoCalculations.crossesIDL(pts, off, len);
        double N = 0, W = 0, S = 0, E = 0;
        for (int i = off; i < pts.length && i < len + off; i++) {
            double lat = pts[i].getLatitude();
            double lng = pts[i].getLongitude();
            if (crossesIDL) {
                if (lng > 0)
                    lng -= 360d;
                else if (lng < 0)
                    lng += 360d;
            }
            if (i == 0) {
                N = S = lat;
                E = W = lng;
            } else {
                if (lat > N)
                    N = lat;
                else if (lat < S)
                    S = lat;
                if (lng > E)
                    E = lng;
                else if (lng < W)
                    W = lng;
            }
        }
        if (E > 180 || crossesIDL && Double.compare(E, 180) == 0)
            E -= 360;
        if (W < -180 || crossesIDL && Double.compare(W, -180) == 0)
            W += 360;
        this.setImpl(S, Math.min(E, W), N, Math.max(E, W));
        this.setWrap180(wrap180);
    }

    public void set(GeoPoint[] pts, int off, int len) {
        set(pts, off, len, false);
    }

    public void set(double lat0, double lng0, double lat1, double lng1) {
        double south = lat0;
        double west = lng0;
        double north = lat0;
        double east = lng0;

        if (lat1 < south)
            south = lat1;
        else if (lat1 > north)
            north = lat1;
        if (lng1 < west)
            west = lng1;
        else if (lng1 > east)
            east = lng1;
        this.setImpl(south, west, north, east);
    }

    /**
     * Set the minimum defined altitude for the bounds.   It is strongly recommended that the
     * altitude used here be in HAE.
     * @param altitude the altitude, or Double.NaN is the altitude is not to be used.
     */
    public void setMinAltitude(double altitude) {
        _minAltitude = altitude;
    }

    /**
     * Set the maximum defined altitude for the bounds.   It is strongly recommended that the
     * altitude used here be in HAE.
     * @param altitude the altitude, or Double.NaN is the altitude is not to be used.
     */
    public void setMaxAltitude(double altitude) {
        _maxAltitude = altitude;
    }



    private void setImpl(double south, double west, double north, double east) {
        _south = south;
        _west = west;
        _north = north;
        _east = east;
    }

    public void clear() {
        this.setImpl(0, 0, 0, 0);
    }
}
