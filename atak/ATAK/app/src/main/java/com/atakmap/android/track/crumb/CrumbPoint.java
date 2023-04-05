
package com.atakmap.android.track.crumb;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.lang.Objects;

/**
 * Geo point with speed, bearing, and timestamp fields
 */
public class CrumbPoint {

    public final float speed, bearing;
    public final long timestamp;
    public final GeoPointMetaData gpm = new GeoPointMetaData();
    public final GeoPoint gp;

    public CrumbPoint(double latitude, double longitude, double altitude,
            double ce90, double le90, float speed,
            float bearing, long timestamp, String altSource,
            String geopointSource) {
        this(new GeoPoint(latitude, longitude, altitude,
                GeoPoint.AltitudeReference.HAE, ce90, le90),
                speed, bearing, timestamp, altSource, geopointSource);
    }

    public CrumbPoint(GeoPoint gp, float speed, float bearing, long timestamp,
            String altitudeSource, String geopointSource) {
        this.gp = gp;
        this.speed = speed;
        this.bearing = bearing;
        this.timestamp = timestamp;
        gpm.set(gp);
        gpm.setAltitudeSource(altitudeSource);
        gpm.setGeoPointSource(geopointSource);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Double.valueOf(speed).hashCode();
        result = 31 * result + Double.valueOf(bearing).hashCode();
        result = 31 * result + Long.valueOf(timestamp).hashCode();
        return result;
    }

    @Override
    public boolean equals(Object other) {

        if (other == null)
            return false;

        // true equality
        if (other == this) {
            return true;
        }

        if (!(other instanceof CrumbPoint))
            return false;

        CrumbPoint ogp = (CrumbPoint) other;

        return Objects.equals(gpm, ogp.gpm) &&
                Double.compare(speed, ogp.speed) == 0 &&
                Double.compare(bearing, ogp.bearing) == 0 &&
                timestamp == ogp.timestamp;
    }

    @Override
    public String toString() {
        return gpm + " " + speed + " " + bearing + " " + timestamp;
    }
}
