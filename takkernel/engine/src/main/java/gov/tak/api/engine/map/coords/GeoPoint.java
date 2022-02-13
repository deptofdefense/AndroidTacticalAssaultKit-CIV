package gov.tak.api.engine.map.coords;

import java.util.Objects;

import gov.tak.api.engine.map.MapSceneModel;
import gov.tak.api.marshal.IMarshal;
import gov.tak.platform.marshal.MarshalManager;

public final class GeoPoint {

    static {
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                switch((com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference)in) {
                    case AGL:   return (T)gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.AGL;
                    case HAE:   return (T)gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.HAE;
                    default:    return null;
                }
            }
        }, com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.class, gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                switch((gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference)in) {
                    case AGL:   return (T)com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.AGL;
                    case HAE:   return (T)com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.HAE;
                    default:    return null;
                }
            }
        }, gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.class, com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                final com.atakmap.coremap.maps.coords.GeoPoint src = (com.atakmap.coremap.maps.coords.GeoPoint)in;
                return (T)new gov.tak.api.engine.map.coords.GeoPoint(
                        src.getLatitude(),
                        src.getLongitude(),
                        src.getAltitude(),
                        MarshalManager.marshal(src.getAltitudeReference(), com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.class, gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.class),
                        src.getCE(),
                        src.getLE());
            }
        }, com.atakmap.coremap.maps.coords.GeoPoint.class, gov.tak.api.engine.map.coords.GeoPoint.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                final gov.tak.api.engine.map.coords.GeoPoint src = (gov.tak.api.engine.map.coords.GeoPoint)in;
                return (T)new com.atakmap.coremap.maps.coords.GeoPoint(
                        src.getLatitude(),
                        src.getLongitude(),
                        src.getAltitude(),
                        MarshalManager.marshal(src.getAltitudeReference(), gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.class, com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.class),
                        src.getCE(),
                        src.getLE());
            }
        }, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class);
    }

    public final static String TAG = "GeoPoint";

    public enum Access {
        READ_ONLY,
        READ_WRITE
    }

    public final static double UNKNOWN = Double.NaN;

    public final static GeoPoint ZERO_POINT = new GeoPoint(0d, 0d, UNKNOWN,
            GeoPoint.AltitudeReference.HAE, UNKNOWN, UNKNOWN, GeoPoint.Access.READ_ONLY);
    public final static GeoPoint UNKNOWN_POINT = new GeoPoint(UNKNOWN, UNKNOWN,
            UNKNOWN, GeoPoint.AltitudeReference.HAE, UNKNOWN, UNKNOWN, GeoPoint.Access.READ_ONLY);

    public static final double MIN_ACCEPTABLE_ALTITUDE = -3600;
    private static final double MAX_ACCEPTABLE_ALTITUDE = 76000;

    public enum AltitudeReference {
        HAE,
        AGL;

        public static GeoPoint.AltitudeReference get(String name) {
            for (GeoPoint.AltitudeReference ref : GeoPoint.AltitudeReference.values()) {
                if (ref.name().equals(name))
                    return ref;
            }
            return HAE;
        }
    }

    private double latitude;
    private double longitude;
    private double altitude;
    private GeoPoint.AltitudeReference altitudeReference;
    private double ce90;
    private double le90;
    private GeoPoint.Access access;

    /**
     * Full constructor for a GeoPoint.
     *
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude provided in meters [-3600, 78000]   
     * @param altitudeReference the altitude refernece in either AltitudeReference.HAE or AltitudeReference.AGL
     * @param ce90 circular error of 90 percent for the horizontal. A CE90 value is the minimum
     *            diameter of the horizontal circle that can be centered on all photo-identifiable
     *            Ground Control Points (GCPs) and also contain 90% of their respective twin
     *            counterparts acquired in an independent geodetic survey.
     * @param le90 linear error of 90 percent for the vertical. A LE90 value represents the linear
     *            vertical distance that 90% of control points and their respective twin matching
     *            counterparts acquired in an independent geodetic survey should be found from each
     *            other.
     * @param access the access for this geopoint as either Access.READ_ONLY or Access.READ_WRITE
     */
    public GeoPoint(final double latitude,
                    final double longitude,
                    final double altitude,
                    final GeoPoint.AltitudeReference altitudeReference,
                    final double ce90,
                    final double le90,
                    final GeoPoint.Access access) {
        this.latitude = latitude;
        this.longitude = longitude;
        if (altitude < MIN_ACCEPTABLE_ALTITUDE
                || altitude > MAX_ACCEPTABLE_ALTITUDE) {
            this.altitude = UNKNOWN;
        } else {
            this.altitude = altitude;
        }

        this.altitudeReference = altitudeReference;
        this.ce90 = ce90;
        this.le90 = le90;
        this.access = access;
    }

    /**
     * Constructs a read only geopoint with all of the parameter.
     *
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude provided in meters
     * @param altitudeReference the altitude refernece in either AltitudeReference.HAE or AltitudeReference.AGL
     * @param ce90 circular error of 90 percent for the horizontal. A CE90 value is the minimum
     *            diameter of the horizontal circle that can be centered on all photo-identifiable
     *            Ground Control Points (GCPs) and also contain 90% of their respective twin
     *            counterparts acquired in an independent geodetic survey.
     * @param le90 linear error of 90 percent for the vertical. A LE90 value represents the linear
     *            vertical distance that 90% of control points and their respective twin matching
     *            counterparts acquired in an independent geodetic survey should be found from each
     *            other.
     */
    public GeoPoint(final double latitude,
                    final double longitude,
                    final double altitude,
                    final GeoPoint.AltitudeReference altitudeReference,
                    final double ce90,
                    final double le90) {
        this(latitude, longitude, altitude, altitudeReference, ce90, le90,
                GeoPoint.Access.READ_ONLY);
    }

    /**
     *  Constructs a read only geopoint with the altitude specified a @see AltitudeReference.HAE.
     *
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude in HAE METERS
     */
    public GeoPoint(final double latitude,
                    final double longitude,
                    final double altitude,
                    final double ce90,
                    final double le90) {

        this(latitude, longitude, altitude, GeoPoint.AltitudeReference.HAE,
                ce90, le90, GeoPoint.Access.READ_ONLY);
    }

    /**
     * Constructs a GeoPoint with an unknown GeoPoint.ALTITUDE_UNKNOWN and CE90 and LE90 set to
     *             UNKNOWN.
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     */
    public GeoPoint(final double latitude, final double longitude) {
        this(latitude, longitude, UNKNOWN, GeoPoint.AltitudeReference.HAE,
                UNKNOWN, UNKNOWN, GeoPoint.Access.READ_ONLY);
    }

    /**
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude in HAE METERS
     */
    public GeoPoint(final double latitude,
                    final double longitude,
                    final double altitude) {

        this(latitude, longitude, altitude, GeoPoint.AltitudeReference.HAE,
                UNKNOWN, UNKNOWN, GeoPoint.Access.READ_ONLY);
    }

    /**
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude in METERS specified by the altitudeReference
     * @param altitudeReference the altitude reference 
     */
    public GeoPoint(final double latitude,
                    final double longitude,
                    final double altitude,
                    final GeoPoint.AltitudeReference altitudeReference) {

        this(latitude, longitude, altitude, altitudeReference,
                UNKNOWN, UNKNOWN, GeoPoint.Access.READ_ONLY);
    }

    /**
     * Do not allow for empty construction of a GeoPoint. Mark as private.
     */
    private GeoPoint() {
        longitude = Double.NaN;
        latitude = Double.NaN;
        altitude = UNKNOWN;
        altitudeReference = GeoPoint.AltitudeReference.HAE;
        ce90 = UNKNOWN;
        le90 = UNKNOWN;
        access = GeoPoint.Access.READ_ONLY;
    }

    /**
     * Copy constructor for a GeoPoint which allows for modifying the access of the copy.
     * @param other the geopoint to copy.
     * @param access the access for the GeoPoint of either Access.READ_ONLY or Access.READ_WRITE
     */
    public GeoPoint(GeoPoint other, GeoPoint.Access access) {
        this(other.latitude, other.longitude, other.altitude,
                other.altitudeReference, other.ce90, other.le90, access);
    }

    /**
     * Copy constructor for a GeoPoint which perserves the access permissions of the original geopoint.
     * @param other the geopoint to copy.
     */
    public GeoPoint(GeoPoint other) {
        this(other.latitude, other.longitude, other.altitude,
                other.altitudeReference, other.ce90, other.le90, other.access);
    }

    /**
     * Creates a mutable Geopoint which is initially invalid.
     * @return the mutable geopoint.
     */
    public static GeoPoint createMutable() {
        GeoPoint ret = new GeoPoint();
        ret.access = GeoPoint.Access.READ_WRITE;
        return ret;
    }

    /**
     * Is the geopoint capable of of being changed with the appropriate set methods.
     * @return true if it is mutable.
     */
    public boolean isMutable() {
        return access == GeoPoint.Access.READ_WRITE;
    }

    /**
     * Get the latitude position
     *
     * @return latitude in decimal degrees
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Get the longitude position
     *
     * @return decimal degrees
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Returns the altitude for the GeoPoint.
     */
    public double getAltitude() {
        return altitude;
    }

    /**
     * Returns the altitude reference for the GeoPoint.
     */
    public GeoPoint.AltitudeReference getAltitudeReference() {
        return altitudeReference;
    }

    /**
     * Returns the circular error of 90 percent for the horizontal.
     * @return the circular error, Double.NaN if invalid
     */
    public double getCE() {
        return ce90;
    }

    /**
     * Returns the linear error of 90 percent for the vertical.
     * @return the linear error, Double.NaN if invalid
     */
    public double getLE() {
        return le90;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @param altitude the altitude in as specified by the altitude reference
     * @param altitudeReference the altitude reference for the provided altitude.
     * @param ce90 the circular error
     * @param le90 the linear error.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public boolean set(final double latitude, final double longitude,
                       final double altitude,
                       final GeoPoint.AltitudeReference altitudeReference, final double ce90,
                       final double le90) {
        if (access == GeoPoint.Access.READ_ONLY)
            return false;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.altitudeReference = altitudeReference;
        this.ce90 = ce90;
        this.le90 = le90;
        return true;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param gp the geopoint to use as the source to copy from.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(GeoPoint gp) {
        if (access == GeoPoint.Access.READ_ONLY)
            return null;
        this.latitude = gp.getLatitude();
        this.longitude = gp.getLongitude();
        this.altitude = gp.getAltitude();
        this.altitudeReference = gp.getAltitudeReference();
        this.ce90 = gp.getCE();
        this.le90 = gp.getLE();
        return this;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double latitude, final double longitude) {
        if (access == GeoPoint.Access.READ_ONLY)
            return null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ce90 = UNKNOWN;
        this.le90 = UNKNOWN;
        return this;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @param altitude the altitude in HAE.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double latitude, final double longitude,
                                                        final double altitude) {
        if (access == GeoPoint.Access.READ_ONLY)
            return null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.altitudeReference = GeoPoint.AltitudeReference.HAE;
        this.ce90 = UNKNOWN;
        this.le90 = UNKNOWN;
        return this;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @param altitude the altitude in HAE.
     * @param ce90 the circular error
     * @param le90 the linear error.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double latitude, final double longitude,
                                                        final double altitude,
                                                        final double ce90, final double le90) {
        if (access == GeoPoint.Access.READ_ONLY)
            return null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.altitudeReference = GeoPoint.AltitudeReference.HAE;
        this.ce90 = ce90;
        this.le90 = le90;
        return this;
    }

    /**
     * Sets the altitude of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param altitude the altitude in HAE.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double altitude) {
        if (access == GeoPoint.Access.READ_ONLY)
            return null;

        this.altitude = altitude;
        this.altitudeReference = GeoPoint.AltitudeReference.HAE;
        this.ce90 = UNKNOWN;
        this.le90 = UNKNOWN;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GeoPoint geoPoint = (GeoPoint) o;
        return Double.compare(geoPoint.latitude, latitude) == 0 &&
                Double.compare(geoPoint.longitude, longitude) == 0 &&
                Double.compare(geoPoint.altitude, altitude) == 0 &&
                Double.compare(geoPoint.ce90, ce90) == 0 &&
                Double.compare(geoPoint.le90, le90) == 0 &&
                altitudeReference == geoPoint.altitudeReference;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, altitude, altitudeReference,
                ce90, le90);
    }
}
