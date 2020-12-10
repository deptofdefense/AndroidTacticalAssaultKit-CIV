
package com.atakmap.coremap.maps.coords;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Wrapper for a GeoPoint which exposes additional metadata associated with the point.    When used
 * in conjunction with higher level items such as PointMapItem, the information is stored within
 * the MapItems metadata bag by using the appropriate setPoint(GeoPointMetaData) and getGeoPointMetaData();
 */
public class GeoPointMetaData {

    public static final String TAG = "GeoPointMetaData";

    private static final Pattern comma = Pattern.compile(","); // thread safe

    public final static String CHECKSUM = "geopoint.check";
    public final static String GEOPOINT_SOURCE = "geopointsrc";
    public final static String ALTITUDE_SOURCE = "altsrc";

    // Commonly Used Altitude Sources //

    public final static String DTED0 = "DTED0";
    public final static String DTED1 = "DTED1";
    public final static String DTED2 = "DTED2";
    public final static String DTED3 = "DTED3";
    public final static String LIDAR = "LIDAR";
    public final static String PFI = "PFI";
    public final static String USER = "USER";
    public final static String UNKNOWN = "???";
    public final static String GPS = "GPS";
    public final static String SRTM1 = "SRTM1";
    public final static String COT = "COT";
    public final static String PRI = "PRI";
    public final static String CALCULATED = "CALC";
    public final static String ESTIMATED = "ESTIMATED";
    public final static String RTK = "RTK";
    public final static String DGPS = "DGPS";
    public final static String PPS = "GPS_PPS";

    public final static String PRECISE_IMAGE_FILE = "PRECISE_IMAGE_FILE";
    public final static String PRECISE_IMAGE_FILE_X = "PRECISE_IMAGE_FILE_X";
    public final static String PRECISE_IMAGE_FILE_Y = "PRECISE_IMAGE_FILE_Y";

    private GeoPoint geopoint = new GeoPoint(Double.NaN, Double.NaN);
    private final Map<String, Object> metadata = new HashMap<>();

    public GeoPointMetaData() {
    }

    public GeoPointMetaData(GeoPointMetaData other) {
        set(other);
    }

    public GeoPointMetaData(GeoPoint point) {
        set(point);
    }

    /**
     * Set the GeoPoint for the specific GeoPointMetaData class.   Calling set on any other GeoPoint
     * will invalidate the metadata.
     *
     * @param gp the geopoint that will be associated with metadata.
     * @return the GeoPointMetaData instance for chaining calls.
     */
    public GeoPointMetaData set(final GeoPoint gp) {
        if (gp.equals(geopoint))
            return this;

        if (gp.isMutable())
            geopoint = new GeoPoint(gp, GeoPoint.Access.READ_ONLY);
        else
            geopoint = gp;

        metadata.clear();
        metadata.put(CHECKSUM, gp.toStringRepresentation());
        return this;
    }

    /**
     * Obtain the GeoPoint that is described by this GeoPointMetaData instance.
     * @return the geopoint.   The resulting GeoPoint is not mutable.
     */
    public GeoPoint get() {
        return geopoint;
    }

    /**
     * Shortcut for setting the metadata for the altitude source.
     * @param source the altitude source as a string.
     * @return the instance of GeoPointMetaData for chaining
     */
    public GeoPointMetaData setAltitudeSource(String source) {
        metadata.put(ALTITUDE_SOURCE, source);
        return this;
    }

    /**
     * Shortcut for setting the metadata for the altitude source.
     * @param source the altitude source as a string.
     * @return the instance of GeoPointMetaData for chaining
     */
    public GeoPointMetaData setGeoPointSource(final String source) {
        metadata.put(GEOPOINT_SOURCE, source);
        return this;
    }

    /**
     * Allow for additional metadata to be associated with the GeoPoint.
     * @param key the metadata key
     * @param val the metadata value
     * @return the instance of GeoPointMetaData for chaining
     */
    public GeoPointMetaData setMetaValue(final String key, final Object val) {
        if (val == null)
            metadata.remove(key);
        else
            metadata.put(key, val);

        return this;
    }

    /**
     * Get the associated GeoPoint source information.
     * @return the String representing the source.
     */
    public String getGeopointSource() {
        Object source = metadata.get(GEOPOINT_SOURCE);
        if (!(source instanceof String))
            return UNKNOWN;
        return (String) source;

    }

    /**
     * Get the associated Altitude source information.
     * @return the String representing the altitude source or if that has not been set, assume it
     * it is the same as the geopoint source.
     */
    public String getAltitudeSource() {
        Object source = metadata.get(ALTITUDE_SOURCE);
        if (!(source instanceof String)) {
            source = metadata.get(GEOPOINT_SOURCE);
            if (!(source instanceof String))
                return UNKNOWN;
        }
        return (String) source;

    }

    /**
     * Returns the metadata for a specific point, please note - this method returns a unmodifiable
     * map that is not thread safe.  The most common use case is as an input to
     * MetaDataHolder.copyMetadata()
     */
    public Map<String, Object> getMetaData() {
        return new HashMap<>(metadata);
    }

    /**
     * Returns a metadata field that is associate with this GeoPoint.
     * @param key the key used to look up the metadata.
     * @return an object described by the key, null if not found.
     */
    public Object getMetaData(final String key) {
        return metadata.get(key);
    }

    /**
     * Ability to copy the contents of another GeoPointMetaData instance.
     * @param gpm the GeoPointMetaData instance.
     * @return updates to the GeoPointMetaData that at least have the keys described by the passed
     * in GeoPointMetaData object but could have more if the GeoPoints are identical.   This is
     * used to aggregate data.
     */
    public GeoPointMetaData set(final GeoPointMetaData gpm) {
        this.set(gpm.get());
        this.metadata.putAll(gpm.getMetaData());
        return this;
    }

    /**
     * Ability to wrap an existing GeoPoint as a GeoPointMetdata holder.
     * @param gp the geopoint
     * @return an empty GeoPointMetdataHolder.
     */
    public static GeoPointMetaData wrap(final GeoPoint gp) {
        return new GeoPointMetaData(gp);
    }

    /**
     * Ability to wrap an existing GeoPoint as a GeoPointMetdata holder with a defined geopointSource
     * and altitudeSource..
     * @param gp the geopoint
     * @param geopointSource the geopoint source
     * @param altitudeSource the altitude source
     * @return a GeoPointMetdataHolder with preset geopointSource and altitudeSource values.
     */
    public static GeoPointMetaData wrap(final GeoPoint gp,
            final String geopointSource, final String altitudeSource) {
        return new GeoPointMetaData(gp)
                .setGeoPointSource(geopointSource)
                .setAltitudeSource(altitudeSource);
    }

    /**
     * Ability to wrap an existing GeoPoint array as a GeoPointMetdata array.
     * @param pts the geopoint array
     * @return the geopoint array
     */
    public static GeoPointMetaData[] wrap(GeoPoint[] pts) {
        GeoPointMetaData[] ret = new GeoPointMetaData[pts.length];
        for (int i = 0; i < pts.length; ++i)
            ret[i] = new GeoPointMetaData(pts[i]);
        return ret;
    }

    /**
     * Given a metadata rich array of GeoPointMetaData elements, discard the metadata in order and just return
     * the array of GeoPoints.
     * @param pts the array of GeoPointMetaData items.
     * @return the GeoPoints.
     */
    public static GeoPoint[] unwrap(GeoPointMetaData[] pts) {
        GeoPoint[] ret = new GeoPoint[pts.length];
        for (int i = 0; i < pts.length; ++i) { 
            if (pts[i] != null) { 
                ret[i] = pts[i].get();
            }
        }
        return ret;
    }

    /**
     * Ability to wrap an existing GeoPoint list as a GeoPointMetdata list.
     * @param pts the geopoint list
     * @return the GeoPointMetadata list
     */
    public static List<GeoPointMetaData> wrap(List<GeoPoint> pts) {
        List<GeoPointMetaData> ret = new ArrayList<>(pts.size());
        for (int i = 0; i < pts.size(); ++i)
            ret.add(new GeoPointMetaData(pts.get(i)));
        return ret;
    }

    /**
     * Given a metadata rich list of GeoPointMetaData elements, discard the metadata in order and just return
     * the list of GeoPoints.
     * @param pts the list of GeoPointMetaData items.
     * @return the list of GeoPoints.
     */
    public static List<GeoPoint> unwrap(List<GeoPointMetaData> pts) {
        List<GeoPoint> ret = new ArrayList<>(pts.size());
        for (int i = 0; i < pts.size(); ++i)
            ret.add(pts.get(i).get());
        return ret;
    }

    /**
     * Get a human readable representation of the GeoPoint in the format:
     *      latitude,longitude,altitudeHAE_meters,ce90,le90,altitude_source,geopoint_source
     * @deprecated use {@link #toString()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public String toStringRepresentation() {
        return geopoint.getLatitude() + "," +
                geopoint.getLongitude() + "," +
                geopoint.getAltitude() + "," +
                geopoint.getCE() + "," +
                geopoint.getLE() + "," +
                getAltitudeSource().replace(",", "") + "," +
                getGeopointSource().replace(",", "");

    }

    /**
     * Get a human readable representation of the GeoPoint in the format:
     *      altitude, altitudeReference, ce, le
     * @return Geo point string
     */
    @Override
    public String toString() {
        return this.geopoint.toString();
    }

    /**
     * Check equality between this point and another point
     * Metadata is ignored
     * XXX - Should we ignore metadata such as sources? Historically we did
     *
     * @param o {@link GeoPointMetaData} or {@link GeoPoint}
     * @return True if equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        GeoPoint other;
        if (o instanceof GeoPoint)
            other = (GeoPoint) o;
        else if (o instanceof GeoPointMetaData)
            other = ((GeoPointMetaData) o).get();
        else
            return false;
        return Objects.equals(get(), other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(geopoint);
    }

    /**
     * Given a String of format lat, lon or lat, lon, alt where all values are stored in the double
     * type, this method returns a GeoPointMetadata instance.
     *
     * @param str - A String containing a coordinate pair or a coordinate pair plus altitude. ALl
     *            values are to be delimited by a comma. Example: "34.831, -112.243" or
     *            "34.831, -112.243, 543.2" are valid values. The ALTITUDE, if present is in HAE /
     *            METERS.   There is also a 6 parameter serialization produced by the toString
     *            functionality in this class.
     *
     * @return A GeoPoint instance or null if the String cannot be parsed.
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public static GeoPointMetaData parseGeoPoint(String str) {
        if (str == null) {
            return null;
        }
        String[] parts = comma.split(str, 0);

        // for some reason the point has an invalid // format
        if (!(parts.length >= 2 && parts.length <= 4 || parts.length == 6)) {
            Log.e(TAG, "unable to parse: " + str);
            return null;
        }

        if ("".equals(parts[0]) || "".equals(parts[1])) {
            Log.e(TAG, "unable to parse: " + str);
            return null;
        }

        double lat;
        double lon;

        try {
            lat = Double.parseDouble(parts[0].trim());
            lon = Double.parseDouble(parts[1].trim());
        } catch (Exception e) {
            Log.d(TAG, "error occurred parsing the lat/lon", e);
            return null;
        }
        double altHae = GeoPoint.UNKNOWN;
        double ce = GeoPoint.UNKNOWN;
        double le = GeoPoint.UNKNOWN;
        String altSrc = UNKNOWN;
        String geopointSrc = UNKNOWN;
        try {
            if (parts.length > 2)
                altHae = Double.parseDouble(parts[2].trim());
            if (parts.length == 4)
                altSrc = parts[3].trim();
            else if (parts.length == 6) {
                ce = Double.parseDouble(parts[3].trim());
                le = Double.parseDouble(parts[4].trim());
                altSrc = parts[5];
            } else if (parts.length == 7) {
                ce = Double.parseDouble(parts[3].trim());
                le = Double.parseDouble(parts[4].trim());
                altSrc = parts[5];
                geopointSrc = parts[6];
            }
        } catch (Exception e) {
            Log.d(TAG, "error occurred parsing the geopoint: " + str, e);
            return null;
        }
        return GeoPointMetaData.wrap(new GeoPoint(lat, lon, altHae, ce, le))
                .setAltitudeSource(altSrc).setGeoPointSource(geopointSrc);

    }

}
