
package transapps.geom;

import androidx.annotation.NonNull;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.DistanceCalculations;

/**
 * Do not make use of this class.  It only exists for the purposes of getting a few legacy plugins to compile.
 */
@Deprecated
@DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
final public class GeoPoint extends Coordinate {

    final double lat;
    final double lon;

    public GeoPoint(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public GeoPoint(GeoPoint gp) {
        this.lat = gp.lat;
        this.lon = gp.lon;
    }

    public int getLongitudeE6() {
        return (int) (lon * 1e6);
    }

    public int getLatitudeE6() {
        return (int) (lon * 1e6);
    }

    public int getX() {
        return getLatitudeE6();
    }

    public int getY() {
        return getLongitudeE6();
    }

    public int distanceTo(GeoPoint gp) {
        com.atakmap.coremap.maps.coords.GeoPoint start = new com.atakmap.coremap.maps.coords.GeoPoint(
                lat, lon);
        com.atakmap.coremap.maps.coords.GeoPoint end = new com.atakmap.coremap.maps.coords.GeoPoint(
                gp.getLatitudeE6(), gp.getLatitudeE6());
        return (int) start.distanceTo(end);

    }

    public int bearingTo(GeoPoint gp) {
        com.atakmap.coremap.maps.coords.GeoPoint start = new com.atakmap.coremap.maps.coords.GeoPoint(
                lat, lon);
        com.atakmap.coremap.maps.coords.GeoPoint end = new com.atakmap.coremap.maps.coords.GeoPoint(
                gp.getLatitudeE6(), gp.getLongitudeE6());
        return (int) start.bearingTo(end);

    }

    public GeoPoint destinationPoint(double meters, double angle) {
        com.atakmap.coremap.maps.coords.GeoPoint retval = DistanceCalculations
                .computeDestinationPoint(
                        new com.atakmap.coremap.maps.coords.GeoPoint(lat, lon),
                        meters, angle, 0);
        return new GeoPoint(retval.getLatitude(), retval.getLongitude());
    }

    @NonNull
    @Override
    public Object clone() {

        return new GeoPoint(lat, lon);
    }
}
