
package com.atakmap.spatial.kml;

import android.graphics.Color;

import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.track.crumb.CrumbPoint;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.util.zip.IoUtils;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;
import com.ekito.simpleKML.model.Boundary;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.ExtendedData;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LinearRing;
import com.ekito.simpleKML.model.Link;
import com.ekito.simpleKML.model.NetworkLink;
import com.ekito.simpleKML.model.Pair;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Polygon;
import com.ekito.simpleKML.model.SchemaData;
import com.ekito.simpleKML.model.SimpleArrayData;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleMap;
import com.ekito.simpleKML.model.StyleSelector;
import com.ekito.simpleKML.model.Track;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Utilities for extracting relevant data from a KML Convert data to/from KML
 * 
 * 
 */
public class KMLUtil {

    private static final String TAG = "KMLUtil";

    public static final long MIN_NETWORKLINK_INTERVAL_SECS = 10; // 10 seconds
    public static final long DEFAULT_NETWORKLINK_INTERVAL_SECS = 300; // 5 minutes

    private static final String XML_PROLOG = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>";
    public static final String ATAK_KML_TRACK_EXTENDED_SCHEMA = "trackschema";

    /**
     * ISO 8601 Time formatter. Used by KML, GPX, XML xs:dateTime, etc
     */
    public static final ThreadLocal<SimpleDateFormat> KMLDateTimeFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", LocaleUtil.getCurrent());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    public static final ThreadLocal<SimpleDateFormat> KMLDateTimeFormatterMillis = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.S'Z'", LocaleUtil.getCurrent());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    public static final ThreadLocal<SimpleDateFormat> KMLDateFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd", LocaleUtil.getCurrent());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    private static final DecimalFormat _doubleDecimalFormat = LocaleUtil
            .getDecimalFormat(
                    "0.00");

    static {
        KMLDateTimeFormatter.get().setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    // Static only
    private KMLUtil() {
    }

    /**
     * Get all matching features of the specified type Recurse Document and all Folders Similar to
     * MapGroup.deepForEachItem pattern If handler returns true for any feature, processing stops.
     * You may use this if have already found the feature you are looking for, or you just want the
     * first feature we happen to find We support this pattern for better performance on KML files
     * with large numbers of Placemarks
     * 
     * @param kml the kml object to search
     * @param clazz
     * @return the matching feature
     */
    public static <T extends Feature> void deepFeatures(Kml kml,
            FeatureHandler<T> handler,
            Class<T> clazz) {
        if (kml == null) {
            Log.w(TAG, "Unable to parse null KML");
            return;
        }

        deepFeatures(kml.getFeature(), handler, clazz);
    }

    /**
     * Get all matching features of the specified type Recurse Document and all Folders Similar to
     * MapGroup.deepForEachItem pattern If handler returns true for any feature, processing stops.
     * You may use this if have already found the feature you are looking for, or you just want the
     * first feature we happen to find We support this pattern for better performance on KML files
     * with large numbers of Placemarks
     * 
     * @param feature the feature to deep search
     * @param clazz
     * @return the first instance of a feature found
     */
    public static <T extends Feature> boolean deepFeatures(Feature feature,
            FeatureHandler<T> handler, Class<T> clazz) {
        if (feature == null) {
            Log.w(TAG, "Unable to parse null Feature");
            return false;
        }

        if (handler == null) {
            Log.w(TAG, "Unable to process Feature with no handler");
            return false;
        }

        if (clazz.isInstance(feature)) {
            if (handler.process((T) feature))
                return true;
        }

        if (feature instanceof Document) {
            Document document = (Document) feature;
            if (document.getFeatureList() != null) {
                for (Feature f : document.getFeatureList()) {
                    if (deepFeatures(f, handler, clazz))
                        return true;
                }
            }
        } else if (feature instanceof Folder) {
            Folder folder = (Folder) feature;
            if (folder.getFeatureList() != null) {
                for (Feature f : folder.getFeatureList()) {
                    if (deepFeatures(f, handler, clazz))
                        return true;
                }
            }
        }

        return false;
    }

    /**
     * Add the specified feature to kml/document Create document and document feature list if
     * necessary Fail if kml top level feature is not a document
     * 
     * @param kml
     * @param feature
     * @return success flag
     */
    public static boolean addFeatureToDoc(Kml kml, Feature feature) {
        if (kml == null) {
            Log.w(TAG, "Unable to add feature to null KML");
            return false;
        }

        Feature f = kml.getFeature();
        if (f == null) {
            Document d = new Document();
            kml.setFeature(d);
            f = d;
        }

        if (!(f instanceof Document)) {
            Log.w(TAG,
                    "Unable to add Feature to missing KML Document, found feature type: "
                            + f.getClass().getSimpleName());
            return false;
        }

        Document document = (Document) f;
        List<Feature> features = document.getFeatureList();
        if (features == null) {
            features = new ArrayList<>();
            document.setFeatureList(features);
        }

        Log.d(TAG, "Adding feature: " + feature.getClass().getSimpleName());
        return features.add(feature);
    }

    public static boolean addFeatureToFolder(Folder folder, Feature feature) {
        if (folder == null) {
            Log.w(TAG, "Unable to add feature to null Folder");
            return false;
        }

        List<Feature> features = folder.getFeatureList();
        if (features == null) {
            features = new ArrayList<>();
            folder.setFeatureList(features);
        }
        return features.add(feature);
    }

    /**
     * Parse a list of network links given an XML input stream
     * Note: Due to a bug with SimpleKML (ATAK-7343) this is performed with
     * an XML parser as opposed to their built-in parser
     * @param is Input stream
     * @param handler Feature handler (null to ignore)
     * @return List of network links
     */
    public static List<NetworkLink> parseNetworkLinks(InputStream is,
            FeatureHandler<NetworkLink> handler) {
        List<NetworkLink> ret = new ArrayList<>();

        XmlPullParser parser = null;
        NetworkLink nl = null;
        Link link = null;
        try {
            parser = XMLUtils.getXmlPullParser();
            parser.setInput(is, null);
            int eventType;
            do {
                eventType = parser.next();
                String tag = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        // New network link
                        if (tag.equals("NetworkLink")) {
                            nl = new NetworkLink();
                            for (int i = 0; i < parser
                                    .getAttributeCount(); i++) {
                                String attr = parser.getAttributeName(i);
                                String value = parser.getAttributeValue(i);
                                if (attr.equals("id"))
                                    nl.setId(value);
                            }
                            continue;
                        }

                        // No NetworkLink read - skip
                        if (nl == null)
                            continue;

                        // Top-level attributes/elements for NetworkLink
                        switch (tag) {
                            case "name":
                                nl.setName(getValue(parser, null));
                                break;
                            case "visibility":
                                nl.setVisibility(
                                        getValue(parser, "1").equals("1"));
                                break;
                            case "open":
                                nl.setOpen(getValue(parser, "1").equals("1"));
                                break;
                            case "Link":
                                nl.setLink(link = new Link());
                                break;
                        }

                        // No Link read - skip
                        if (link == null)
                            continue;

                        switch (tag) {
                            case "href":
                                link.setHref(getValue(parser, null));
                                break;
                            case "httpQuery":
                                link.setHttpQuery(
                                        fixEncoding(getValue(parser, null)));
                                break;
                            case "refreshInterval":
                                link.setRefreshInterval(Float.parseFloat(
                                        getValue(parser, "-1")));
                                break;
                            case "refreshMode":
                                link.setRefreshMode(getValue(parser, null));
                                break;
                        }

                        break;

                    // Finish parsing network link
                    case XmlPullParser.END_TAG:
                        if (nl != null && tag.equals("NetworkLink")) {
                            ret.add(nl);
                            // True = stop processing
                            if (handler.process(nl))
                                return ret;
                            nl = null;
                        }
                        break;

                    // Unhandled
                    case XmlPullParser.TEXT:
                    case XmlPullParser.END_DOCUMENT:
                    default:
                        break;
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);

        } catch (Exception e) {
            Log.e(TAG, "Error parsing XML", e);
        }
        return ret;
    }

    private static String getValue(XmlPullParser parser, String def)
            throws IOException, XmlPullParserException {
        int eventType = parser.next();
        if (eventType == XmlPullParser.TEXT)
            return parser.getText();
        return def;
    }

    /**
     * Simple encoding fix for kml httpQuery (note this is very simple)
     * @param orig the original string
     * @return the fixed encoding
     */
    private static String fixEncoding(String orig) {
        return orig.replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&apos;", "'").replaceAll("&quot;", "\"")
                .replaceAll("&amp;", "&");
    }

    /**
     * Get first geometry of the specified type
     * 
     * @param placemark
     * @param clazz
     * @return
     */
    public static <T extends Geometry> T getFirstGeometry(Placemark placemark,
            Class<T> clazz) {
        List<T> matching = getGeometries(placemark, clazz);
        if (matching == null || matching.size() < 1)
            return null;

        return matching.get(0);
    }

    /**
     * Get all matching geometries of the specified type Note, we dont currently expect a large
     * number of geometries for a single Placemark If this proves to be incorrect in the future, we
     * should look at migrated to an approach similar to deepFeatures() to improve performance
     * 
     * @param placemark
     * @param clazz
     * @return
     */
    public static <T extends Geometry> List<T> getGeometries(
            Placemark placemark, Class<T> clazz) {
        if (placemark == null) {
            Log.w(TAG, "Unable to parse null Placmeark");
            return null;
        }

        List<Geometry> geometries = placemark.getGeometryList();
        if (geometries == null || geometries.size() < 1) {
            Log.w(TAG, "Unable to parse Geometry List");
            return null;
        }

        List<T> matching = new ArrayList<>();
        for (Geometry geometry : geometries) {
            if (clazz.isInstance(geometry))
                matching.add((T) geometry);
        }

        return matching;
    }

    /**
     * Get first style
     * 
     * @param kml
     * @return
     */
    public static <T extends StyleSelector> T getFirstStyle(Kml kml,
            Class<T> clazz) {
        List<T> matching = getStyles(kml, clazz);
        if (matching == null || matching.size() < 1)
            return null;

        return matching.get(0);
    }

    /**
     * Note, we dont currently expect a large number of Styles If this proves to be incorrect in the
     * future, we should look at migrated to an approach similar to deepFeatures() to improve
     * performance
     * 
     * @param kml
     * @param clazz
     * @return
     */
    public static <T extends StyleSelector> List<T> getStyles(Kml kml,
            Class<T> clazz) {
        if (kml == null || kml.getFeature() == null) {
            Log.w(TAG, "Unable to parse null KML");
            return null;
        }

        List<T> output = new ArrayList<>();
        getStyles(kml.getFeature(), output, clazz);
        return output;
    }

    /**
     * Recursively get styles from document and nested Folders
     * 
     * @param feature
     * @param output
     * @param clazz
     */
    public static <T extends StyleSelector> void getStyles(Feature feature,
            List<T> output,
            Class<T> clazz) {
        if (feature == null) {
            Log.w(TAG, "Unable to parse null feature");
            return;
        }

        if (output == null)
            output = new ArrayList<>();

        if (feature instanceof Document) {
            Document document = (Document) feature;
            List<StyleSelector> ss = document.getStyleSelector();
            if (ss != null && ss.size() > 0) {
                for (StyleSelector s : ss) {
                    if (clazz.isInstance(s))
                        output.add((T) s);
                }
            }

            List<Feature> features = document.getFeatureList();
            if (features != null && features.size() > 0) {
                for (Feature child : features)
                    getStyles(child, output, clazz);
            }
        } else if (feature instanceof Folder) {
            Folder folder = (Folder) feature;
            List<StyleSelector> ss = folder.getStyleSelector();
            if (ss != null && ss.size() > 0) {
                for (StyleSelector s : ss) {
                    if (clazz.isInstance(s))
                        output.add((T) s);
                }

            }

            List<Feature> features = folder.getFeatureList();
            if (features != null && features.size() > 0) {
                for (Feature child : features)
                    getStyles(child, output, clazz);
            }
        }

    }

    public static <T extends StyleSelector> T getFirstStyle(Feature feature,
            Class<T> clazz) {
        if (feature == null) {
            Log.w(TAG, "Unable to parse null Feature");
            return null;
        }

        List<StyleSelector> ss = feature.getStyleSelector();
        if (ss == null || ss.size() < 1) {
            // Log.d(TAG, "Unable to parse KML Feature Styles");
            return null;
        }

        for (StyleSelector s : ss) {
            if (clazz.isInstance(s))
                return (T) s;
        }

        return null;
    }

    /**
     * Follow styleUrl from top of KML Follow StyleMapping if necessary TODO Support Style or
     * StyleSelector defined on the Placemark directly rather than referenced by styleUrl
     * 
     * @param kml
     * @param placemark
     * @return
     */
    public static Style getStyle(Kml kml, Placemark placemark) {
        if (kml == null || placemark == null) {
            Log.w(TAG, "Unable to get style from empty KML");
            return null;
        }

        if (FileSystemUtils.isEmpty(placemark.getStyleUrl())) {
            Log.w(TAG, "Unable to get style URL");
            return null;
        }

        // mine out the styles and maps
        List<StyleMap> styleMaps = getStyles(kml, StyleMap.class);
        List<Style> styles = getStyles(kml, Style.class);

        // strip '#' from the style URL
        String placemarkStyleUrl = placemark.getStyleUrl();
        if (placemarkStyleUrl.charAt(0) == '#')
            placemarkStyleUrl = placemarkStyleUrl.substring(1);

        // see if Placemark references a style directly
        if (styles != null && styles.size() > 0) {
            for (Style style : styles) {
                if (placemarkStyleUrl.equals(style.getId())) {
                    Log.d(TAG, "Using Style:" + placemarkStyleUrl);
                    return style;
                }
            }
        }

        // see if we can find the mapped style, use "normal"
        if (styleMaps != null && styleMaps.size() > 0) {
            for (StyleMap styleMap : styleMaps) {
                if (styleMap.getId() == null
                        || !styleMap.getId().equals(placemarkStyleUrl))
                    continue;

                List<Pair> stylePairs = styleMap.getPairList();
                if (stylePairs != null && stylePairs.size() > 0) {
                    for (Pair stylePair : stylePairs) {
                        if ("normal".equals(stylePair.getKey())) {

                            String normalStyleId = stylePair.getStyleUrl();
                            if (!FileSystemUtils.isEmpty(normalStyleId)) {
                                // found correct "normal" style, try to find the style
                                if (styles != null && styles.size() > 0) {
                                    for (Style style : styles) {
                                        if (placemarkStyleUrl
                                                .equals(normalStyleId)) {
                                            Log.d(TAG, "Using Mapped Style:"
                                                    + normalStyleId);
                                            return style;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * KML calls for AABBGGRR (not AARRGGBB)
     * 
     * @param argb
     * @return abgr
     */
    public static String convertKmlColor(int argb) {

        // pull out argb
        int b = (argb) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int a = (argb >> 24) & 0xFF;

        // shuffle to abgr
        String kmlColor = String.format("%s%s%s%s",
                toHex(a),
                toHex(b),
                toHex(g),
                toHex(r));

        return kmlColor;
    }

    /**
     * Convert int to 2 digit hex
     * 
     * @param color
     * @return
     */
    public static String toHex(int color) {
        String s = Integer.toHexString(color).toUpperCase(
                LocaleUtil.getCurrent());
        if (s == null || s.length() == 0)
            return "00";
        else if (s.length() == 1)
            return "0" + s;
        else if (s.length() > 2)
            return s.substring(0, 2);
        else
            return s;
    }

    /**
     * Android calls for AARRGGBB (not AABBGGRR)
     * 
     * @param kmlColor (abgr)
     * @return argb, default to WHITE
     */
    public static int parseKMLColor(String kmlColor) {

        if (kmlColor == null || kmlColor.length() < 1)
            return Color.WHITE;

        if (!kmlColor.startsWith("#"))
            kmlColor = "#" + kmlColor;
        int abgr = Color.WHITE;
        try {
            abgr = Color.parseColor(kmlColor);
        } catch (Exception e) {
            Log.d(TAG, "Unknown color encountered: " + kmlColor);
        }

        // pull out abgr
        int r = (abgr) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int a = (abgr >> 24) & 0xFF;

        // shuffle to argb
        return Color.argb(a, r, g, b);
    }

    /**
     * Convert to KML time specification
     * 
     * @param stringMillis
     * @return
     */
    public static String convertKmlTime(String stringMillis) {
        if (stringMillis == null || stringMillis.length() < 1)
            return null;

        try {
            long timeMillis = Long.parseLong(stringMillis);
            if (timeMillis < 1)
                return null;

            return KMLDateTimeFormatter.get().format(new Date(timeMillis));
        } catch (Exception e) {
            Log.e(TAG, "Unable to convert to KML Time: " + stringMillis, e);
        }

        return null;
    }

    /**
     * Parse KML Date (with millis)
     * 
     * @param time
     * @return
     */
    public static Date parseKMLDate(String time) {
        if (time == null || time.length() < 1) {
            Log.e(TAG, "Unable to parse TimeSpan for track");
            return null;
        }
        // Log.d(TAG, "Parse KML time: " + time);

        try {
            return KMLDateTimeFormatterMillis.get().parse(time);
        } catch (ParseException e) {
            Log.e(TAG, "Failed to parse KML time: " + time, e);
        }

        return null;
    }

    public static GeoPointMetaData parseKMLCoord(String coord) {
        return parseKMLCoord(coord, GeoPoint.UNKNOWN,
                GeoPoint.UNKNOWN);
    }

    public static GeoPointMetaData parseKMLCoord(String coord, double ce,
            double le) {
        if (FileSystemUtils.isEmpty(coord)) {
            Log.w(TAG, "Unable to parse KML coord");
            return null;
        }

        String[] tokens = coord.split(" ");
        if (tokens == null || tokens.length < 2) {
            Log.w(TAG, "Unable to parse KML coord tokens");
            return null;
        }

        String lon = tokens[0].trim();
        String lat = tokens[1].trim();
        String alt = null;

        GeoPointMetaData gpm = new GeoPointMetaData();
        if (tokens.length > 2) {
            alt = tokens[2].trim();
            //TODO pass in altitudeMode and handle accordingly for alt/ref
            //TODO what to use for alt & point source?

            gpm.set(new GeoPoint(Double.parseDouble(lat),
                    Double.parseDouble(lon),
                    Double.parseDouble(alt), ce, le));
            gpm.setGeoPointSource(GeoPointMetaData.UNKNOWN);
            gpm.setGeoPointSource(GeoPointMetaData.CALCULATED);
            return gpm;
        } else {
            gpm.set(new GeoPoint(Double.parseDouble(lat),
                    Double.parseDouble(lon),
                    GeoPoint.UNKNOWN));
            return gpm;
        }
    }

    /**
     * Supports any class that has SimpleKML annotations
     * 
     * @param s
     * @param clazz
     * @return
     */
    public static <T> T fromString(String s, Class clazz) {

        if (s == null) {
            Log.w(TAG, "Unable to de-serialize null String");
            return null;
        }

        // now serialize basic KML data out to string for Spatialite
        Serializer serializer = new Persister(new KMLMatcher());
        T out = null;
        try {
            out = (T) serializer.read(clazz, s);
        } catch (Exception e) {
            Log.e(TAG, "Failed to de-serialize string: " + s, e);
            return null;
        }

        if (out == null) {
            Log.e(TAG, "Unable to de-serialize string: " + s);
            return null;
        }

        return out;
    }

    // TODO basic testing/profiling support
    // public static long time_toSpatialiteString = 0;
    // public static long time_insertSql = 0;

    /**
     * Note Serializer is passed in for performance while hundred/thousands of objects may be
     * serialized in a short period of time (e.g. for storing a large KML in a database). Still
     * performance is terrible
     * 
     * @param o
     * @param serializer
     * @return
     */
    public static String toString(Object o, Serializer serializer) {
        // long start = SystemClock.elaspedRealtime();
        if (o == null) {
            Log.e(TAG, "Unable to serialize null Object");
            return null;
        }

        StringWriter sw = new StringWriter();
        try {
            serializer.write(o, sw);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize object", e);
            return null;
        }

        String xml = sw.toString();
        if (xml == null) {
            Log.e(TAG, "Unable to serialize object");
            return null;
        }

        // time_toSpatialiteString += SystemClock.elapsedRealtime() - start;
        return xml;
    }

    /**
     * Create a Polygon element containing an outer LinearRing Automatically closes ring if not
     * already so
     *
     * @param points points making up the polygon
     * @param id shape id attribute
     * @param idlWrap180 True if this polygon crosses the IDL but requires point unwrapping
     */
    public static Polygon createPolygonWithLinearRing(GeoPointMetaData[] points,
            String id, boolean excludeAltitude, boolean idlWrap180) {
        return createPolygonWithLinearRing(points, id, excludeAltitude,
                idlWrap180, Double.NaN);
    }

    /**
     * Create a Polygon element containing an outer LinearRing Automatically closes ring if not
     * already so
     * 
     * @param points points making up the polygon
     * @param id shape id attribute
     * @param idlWrap180 True if this polygon crosses the IDL but requires point unwrapping
     * @param height the height in meters for the Polygon, Double.NaN if no height known.
     */
    public static Polygon createPolygonWithLinearRing(GeoPointMetaData[] points,
            String id, boolean excludeAltitude, boolean idlWrap180,
            double height) {
        Polygon polygon = new Polygon();
        polygon.setId(id);
        if (excludeAltitude)
            polygon.setAltitudeMode("clampToGround");
        else if (Double.isNaN(height)) {
            polygon.setAltitudeMode("absolute");
        } else {
            polygon.setAltitudeMode("relativeToGround");
            polygon.setExtrude(true);
        }

        Boundary outerBoundaryIs = new Boundary();
        polygon.setOuterBoundaryIs(outerBoundaryIs);
        LinearRing linearRing = new LinearRing();
        outerBoundaryIs.setLinearRing(linearRing);

        // /convert coords
        GeoPointMetaData firstPoint = points[0];
        GeoPointMetaData lastPoint = points[points.length - 1];
        String coordsString = convertKmlCoords(points, excludeAltitude,
                idlWrap180, height);
        // Close shape by inserting first point again if it is not already there
        if (firstPoint.get().getLongitude() != lastPoint.get().getLongitude() ||
                firstPoint.get().getLatitude() != lastPoint.get()
                        .getLatitude()) {
            double unwrap = 0;
            if (idlWrap180)
                unwrap = firstPoint.get().getLongitude() > 0 ? 360 : -360;
            Coordinate c = convertKmlCoord(firstPoint, excludeAltitude, unwrap,
                    height);
            if (c != null)
                coordsString += c;
        }

        Coordinates coordinates = new Coordinates(coordsString);
        linearRing.setCoordinates(coordinates);

        return polygon;
    }

    public static Polygon createPolygonWithLinearRing(GeoPointMetaData[] points,
            String id, boolean excludeAltitude) {
        return createPolygonWithLinearRing(points, id, excludeAltitude, false);
    }

    public static Polygon createPolygonWithLinearRing(GeoPointMetaData[] points,
            String id) {
        return createPolygonWithLinearRing(points, id, true);
    }

    /**
     * Create a LineString
     * 
     * @param points points making up the polygon
     * @param id shape id attribute
     * @param idlWrap180 True if this line crosses the IDL but requires point unwrapping
     */
    public static LineString createLineString(GeoPointMetaData[] points,
            String id,
            boolean excludeAltitude, boolean idlWrap180) {
        LineString line = new LineString();
        line.setId(id);
        if (excludeAltitude)
            line.setAltitudeMode("clampToGround");
        else
            line.setAltitudeMode("absolute");

        final String coordsString = convertKmlCoords(points,
                excludeAltitude, idlWrap180);
        Coordinates coordinates = new Coordinates(coordsString);
        line.setCoordinates(coordinates);
        return line;
    }

    public static LineString createLineString(GeoPointMetaData[] points,
            String id,
            boolean excludeAltitude) {
        return createLineString(points, id, excludeAltitude, false);
    }

    public static LineString createLineString(GeoPointMetaData[] points,
            String string) {
        return createLineString(points, string, true);
    }

    /*************************************************
     * Coordinates
     */

    public static GeoPointMetaData[] convertCoordinates(
            Coordinates coordinates) {
        if (coordinates == null) {
            Log.e(TAG, "Unable to parse Coordinates");
            return null;
        }

        ArrayList<Coordinate> coordinateList = coordinates.getList();
        if (coordinateList == null || coordinateList.size() < 1) {
            Log.e(TAG, "Unable to parse Coordinates list");
            return null;
        }

        ArrayList<GeoPointMetaData> ptArr = new ArrayList<>();
        for (Coordinate coord : coordinateList) {
            if (coord == null) {
                Log.w(TAG, "Skipping null Coordinate");
                continue;
            }

            ptArr.add(convertPoint(coord));
        } // end coordinate loop

        if (ptArr.size() < 1) {
            Log.e(TAG, "Unable to convert any Coordinates");
            return null;
        }

        // address isssue where the coordinates is length 1
        if (ptArr.size() == 1) {
            ptArr.add(ptArr.get(0));
        }

        Log.d(TAG, "Convert Coordinates of length: " + ptArr.size());
        return ptArr.toArray(new GeoPointMetaData[0]);
    }

    public static GeoPointMetaData convertPoint(Coordinate coord) {
        double lat = coord.getLatitude();
        double lng = coord.getLongitude();

        if (lng > 180)
            lng -= 360;
        else if (lng < -180)
            lng += 360;

        Double altitude = coord.getAltitude();
        if (altitude == null || Double.isNaN(altitude))
            return GeoPointMetaData.wrap(new GeoPoint(lat, lng));
        else
            return GeoPointMetaData.wrap(new GeoPoint(lat, lng, altitude));
    }

    public static String convertKmlCoords(GeoPointMetaData[] points,
            boolean excludeAltitude, boolean idlWrap180) {
        return convertKmlCoords(points, excludeAltitude, idlWrap180,
                Double.NaN);
    }

    public static String convertKmlCoords(GeoPointMetaData[] points,
            boolean excludeAltitude, boolean idlWrap180, double height) {
        if (points == null || points.length < 1)
            return "";

        double unwrap = 0;
        if (idlWrap180)
            unwrap = points[0].get().getLongitude() > 0 ? 360 : -360;

        StringBuilder sb = new StringBuilder();
        for (GeoPointMetaData point : points) {
            Coordinate c = convertKmlCoord(point, excludeAltitude, unwrap,
                    height);
            if (c != null)
                sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Convert a geopoint with height into a KML Coordinate.  With KML, the representation is the
     * top of the item so the altitude used is actually the altitude + height which differs from
     * TAK where the altitude is the bottom and the height is relative to the altitude.
     * @param point the point to use for the latitude, longitude, and altitude.
     * @param excludeAltitude the boolean to exclude the altitude from the kml string
     * @param unwrap if the idl wrap is to be applied
     * @return the KML Coordinate
     */
    public static Coordinate convertKmlCoord(GeoPointMetaData point,
            boolean excludeAltitude, double unwrap) {
        return convertKmlCoord(point, excludeAltitude, unwrap, Double.NaN);
    }

    /**
     * Convert a geopoint with height into a KML Coordinate.  With KML, the representation is the
     * top of the item so the altitude used is actually the altitude + height which differs from
     * TAK where the altitude is the bottom and the height is relative to the altitude.
     * @param point the point to use for the latitude, longitude, and altitude.
     * @param excludeAltitude the boolean to exclude the altitude from the kml string.  If the altitude
     *                        is not excluded, then the result will be either the altitude
     *                        or the height if the height is specified.
     * @param unwrap if the idl wrap is to be applied
     * @param height the height to be used as the altitude if the altitude is not excluded.
     *               Double.NaN is to be used when the height is not known.
     * @return the KML Coordinate
     */
    private static Coordinate convertKmlCoord(GeoPointMetaData point,
            boolean excludeAltitude, double unwrap, double height) {
        if (point == null)
            return null;

        double alt = point.get().getAltitude();
        double lng = point.get().getLongitude();
        double lat = point.get().getLatitude();

        if (unwrap > 0 && lng < 0 || unwrap < 0 && lng > 0)
            lng += unwrap;

        if (excludeAltitude) {
            // do not include any altitude
            return new Coordinate(lng, lat, null);
        } else {
            if (!Double.isNaN(height))
                // height is set, use that instead of altitude
                return new Coordinate(lng, lat, height);
            else if (point.get().isAltitudeValid()) {
                // height is not set, but altitude is valid
                return new Coordinate(lng, lat, alt);
            } else {
                // altitude is invalid and heightr is not set
                return new Coordinate(lng, lat, null);
            }
        }
    }

    public static Coordinate convertKmlCoord(GeoPointMetaData point,
            boolean excludeAltitude) {
        return convertKmlCoord(point, excludeAltitude, 0);
    }

    /**
     * Convert crumbs to KML gx:Track
     * 
     * @param crumbs
     * @param clampToGround
     * @return
     */
    public static Track convertKmlCoords(List<CrumbPoint> crumbs,
            boolean clampToGround) {

        //lists of spec data
        List<String> when = new ArrayList<>();
        List<String> coords = new ArrayList<>();
        List<String> angles = new ArrayList<>();

        //setup extended data containers
        ExtendedData extendedData = new ExtendedData();
        List<SchemaData> schemaDataList = new ArrayList<>();
        extendedData.setSchemaDataList(schemaDataList);
        SchemaData schemaData = new SchemaData();
        schemaDataList.add(schemaData);
        schemaData.setSchemaUrl("#" + ATAK_KML_TRACK_EXTENDED_SCHEMA);
        List<SimpleArrayData> schemaDataExtensions = new ArrayList<>();
        schemaData.setSchemaDataExtension(schemaDataExtensions);

        //lists for custom extended schema data
        SimpleArrayData speedList = new SimpleArrayData();
        speedList.setName("speed");
        speedList.setValue(new ArrayList<String>());
        schemaDataExtensions.add(speedList);

        SimpleArrayData ceList = new SimpleArrayData();
        ceList.setName("ce");
        ceList.setValue(new ArrayList<String>());
        schemaDataExtensions.add(ceList);

        SimpleArrayData leList = new SimpleArrayData();
        leList.setName("le");
        leList.setValue(new ArrayList<String>());
        schemaDataExtensions.add(leList);

        SimpleArrayData geoPointSrcList = new SimpleArrayData();
        geoPointSrcList.setName("geopointsrc");
        geoPointSrcList.setValue(new ArrayList<String>());
        schemaDataExtensions.add(geoPointSrcList);

        SimpleArrayData altSrcList = new SimpleArrayData();
        altSrcList.setName("altsrc");
        altSrcList.setValue(new ArrayList<String>());
        schemaDataExtensions.add(altSrcList);

        for (CrumbPoint crumb : crumbs) {
            String w = KMLDateTimeFormatter.get().format(
                    new Date(crumb.timestamp));
            Coordinate c = convertKmlCoord(crumb.gpm, clampToGround);

            if (FileSystemUtils.isEmpty(w) || c == null) {
                Log.w(TAG, "Skipping track conversion of invalid crumb");
                continue;
            }

            when.add(w);
            coords.add(c.toString());
            if (!Double.isNaN(crumb.bearing)) {
                angles.add(_doubleDecimalFormat.format(crumb.bearing));
            } else {
                angles.add("");
            }

            //convert m/s to MPH
            if (!Double.isNaN(crumb.speed)) {
                speedList.getValue().add(_doubleDecimalFormat.format(
                        crumb.speed
                                * ConversionFactors.METERS_PER_S_TO_MILES_PER_H));
            } else {
                speedList.getValue().add("");
            }

            if (!Double.isNaN(crumb.gp.getCE())) {
                ceList.getValue().add(
                        _doubleDecimalFormat.format(crumb.gp.getCE()));
            } else {
                ceList.getValue().add("");
            }

            if (!Double.isNaN(crumb.gp.getLE())) {
                leList.getValue().add(
                        _doubleDecimalFormat.format(crumb.gp.getLE()));
            } else {
                leList.getValue().add("");
            }

            geoPointSrcList.getValue().add(
                    crumb.gpm.getGeopointSource());
            altSrcList.getValue().add(
                    crumb.gpm.getAltitudeSource());
        }

        Track t = new Track();
        t.setId(UUID.randomUUID().toString());
        t.setWhen(when);
        t.setCoord(coords);
        t.setAngles(angles);
        t.setExtendedData(extendedData);

        if (clampToGround)
            t.setAltitudeMode("clampToGround");
        else
            t.setAltitudeMode("absolute");

        return t;
    }

    /**
     * Convert altitude mode enum to KML-compatible string
     * @param altitudeMode Altitude mode
     * @return Altitude mode string
     */
    public static String convertAltitudeMode(AltitudeMode altitudeMode) {
        switch (altitudeMode) {
            case Absolute:
                return "absolute";
            case Relative:
                return "relativeToGround";
            case ClampToGround:
                return "clampToGround";
        }
        return null;
    }

    /**
     * Creates a CDATA section containing a list.
     * 
     * @param type
     * @param items
     */
    public static String createCDataList(String type, String[] items) {
        if (type == null || items == null || items.length < 1)
            return null;

        StringBuilder b = new StringBuilder();
        // Note Simple KML encodes these tags automatically
        // b.append("<![CDATA[");
        b.append("<ul type=\"");
        b.append(type);
        b.append("\">");
        for (String item : items) {
            b.append("<li>");
            b.append(item);
            b.append("</li>");
        }
        b.append("</ul>");
        // Note Simple KML encodes these tags automatically
        // b.append("]]>");
        return b.toString();
    }

    /**
     * Extract from the KMZ file, the first .kml, and store in temp file
     * 
     * @param kmzFile
     * @return
     * @throws IOException
     */
    public static File getKmlFileFromKmzFile(File kmzFile, File tmpDir)
            throws IOException {
        File tempFile = null;

        ZipFile zip = null;
        try {
            zip = new ZipFile(kmzFile);
        } catch (IOException e) {
            // Log.d(TAG, "Failed to open KMZ file", e);
            zip = null;
        }

        if (zip != null) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.getName().endsWith(".kml")) {

                    String[] tempNameArr = ze.getName().split("/");// split around file seps
                    String tempName = tempNameArr[tempNameArr.length - 1];// take the last one
                    tempNameArr = tempName.split("\\.");// split around .
                    int i = 0;
                    StringBuilder sb = new StringBuilder();
                    do {
                        if (i > 0) {
                            tempName = tempName + ".";// put non-filename strings back together
                        }
                        tempName = sb.append(tempName).append(tempNameArr[i])
                                .toString();
                        ++i;
                    } while (i < tempNameArr.length - 2);

                    try (InputStream is = zip.getInputStream(ze)) {
                        tempFile = IOProviderFactory.createTempFile(tempName,
                                ".kml",
                                tmpDir);
                        try (OutputStream os = IOProviderFactory
                                .getOutputStream(tempFile)) {
                            FileSystemUtils.copy(is, os);
                        }
                    } finally {
                        IoUtils.close(zip);
                    }
                    break;
                }
            }

            try {
                zip.close();
            } catch (Exception e) {
                Log.d(TAG, "error occurred closing the zip file", e);
            }

        }
        return tempFile;
    }

    public static String getNetworkLinkName(String parentName,
            NetworkLink link) {
        String label = link.getName();
        if (label == null)
            label = link.getAddress();
        if (label == null)
            label = link.getId();
        if (label == null)
            label = link.getLink().getId();
        if (label == null)
            label = UUID.randomUUID().toString();

        return (parentName + "-" + label).trim();
    }

    /**
     * Given a link, support the href and httpQuery if provided.
     * @param link the Link specified by the KML
     * @return the entire uri from the link
     */
    public static String getURL(final Link link) {
        if (link == null) {
            Log.e(TAG, "Empty KML Link");
            return null;
        }

        // only support HTTP(s) links for now
        // TODO link could be to local file or relative to parent URL
        String url = link.getHref();
        if (FileSystemUtils.isEmpty(url)
                || !((url.contains("http://") || url.contains("https://")))) {
            Log.e(TAG, "Unsupported NetworkLink URL: : " + url);
            return null;
        }

        String query = link.getHttpQuery();
        if (!FileSystemUtils.isEmpty(query)) {
            url += "?" + query;
        }

        // TODO any reason to support link ViewFormat?

        return url;
    }

    /**
     * Write KML to file, if necessary, prepend the XML Prolog
     * 
     * @param kml
     * @param file
     * @throws Exception
     */
    public static void write(String kml, File file) {
        if (FileSystemUtils.isEmpty(kml) || file == null) {
            Log.w(TAG, "Unable to write empty KML file");
            return;
        }

        File parent = file.getParentFile();
        if (!IOProviderFactory.exists(parent))
            if (!IOProviderFactory.mkdirs(parent))
                Log.w(TAG,
                        "Failed to create directory(s)"
                                + parent.getAbsolutePath());

        try (PrintWriter out = new PrintWriter(new BufferedWriter(
                IOProviderFactory.getFileWriter(file)))) {
            if (!kml.startsWith("<?xml")) {
                out.println(XML_PROLOG);
            }

            out.println(kml);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write KML: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * create a repeatable hash for the specified Style
     * 
     * @param style
     * @return hash
     */
    public static String hash(Style style) {
        if (style == null)
            return null;

        int hashCode = 1;
        if (style.getBalloonStyle() != null) {
            if (style.getBalloonStyle().getBgColor() != null)
                hashCode += style.getBalloonStyle().getBgColor().hashCode();
            if (style.getBalloonStyle().getColor() != null)
                hashCode += style.getBalloonStyle().getColor().hashCode();
            if (style.getBalloonStyle().getColorMode() != null)
                hashCode += style.getBalloonStyle().getColorMode().hashCode();
            if (style.getBalloonStyle().getDisplayMode() != null)
                hashCode += style.getBalloonStyle().getDisplayMode().hashCode();
            if (style.getBalloonStyle().getId() != null)
                hashCode += style.getBalloonStyle().getId().hashCode();
            if (style.getBalloonStyle().getText() != null)
                hashCode += style.getBalloonStyle().getText().hashCode();
            if (style.getBalloonStyle().getTextColor() != null)
                hashCode += style.getBalloonStyle().getTextColor().hashCode();
        }

        if (style.getIconStyle() != null) {
            if (style.getIconStyle().getColor() != null)
                hashCode += style.getIconStyle().getColor().hashCode();
            if (style.getIconStyle().getColorMode() != null)
                hashCode += style.getIconStyle().getColorMode().hashCode();
            if (style.getIconStyle().getHeading() != null)
                hashCode += style.getIconStyle().getHeading().hashCode();
            //TODO getHotSpot()
            if (style.getIconStyle().getId() != null)
                hashCode += style.getIconStyle().getId().hashCode();
            if (style.getIconStyle().getIcon() != null) {
                if (style.getIconStyle().getIcon().getHref() != null)
                    hashCode += style.getIconStyle().getIcon().getHref()
                            .hashCode();
                if (style.getIconStyle().getIcon().getId() != null)
                    hashCode += style.getIconStyle().getIcon().getId()
                            .hashCode();
                if (style.getIconStyle().getIcon().getState() != null)
                    hashCode += style.getIconStyle().getIcon().getState()
                            .hashCode();
            }
            if (style.getIconStyle().getScale() != null)
                hashCode += style.getIconStyle().getScale().hashCode();
        }

        if (style.getLabelStyle() != null) {
            if (style.getLabelStyle().getColor() != null)
                hashCode += style.getLabelStyle().getColor().hashCode();
            if (style.getLabelStyle().getColorMode() != null)
                hashCode += style.getLabelStyle().getColorMode().hashCode();
            if (style.getLabelStyle().getId() != null)
                hashCode += style.getLabelStyle().getId().hashCode();
            if (style.getLabelStyle().getScale() != null)
                hashCode += style.getLabelStyle().getScale().hashCode();
        }

        if (style.getLineStyle() != null) {
            if (style.getLineStyle().getColor() != null)
                hashCode += style.getLineStyle().getColor().hashCode();
            if (style.getLineStyle().getColorMode() != null)
                hashCode += style.getLineStyle().getColorMode().hashCode();
            if (style.getLineStyle().getId() != null)
                hashCode += style.getLineStyle().getId().hashCode();
            if (style.getLineStyle().getLabelVisibility() != null)
                hashCode += style.getLineStyle().getLabelVisibility()
                        .hashCode();
            if (style.getLineStyle().getOuterColor() != null)
                hashCode += style.getLineStyle().getOuterColor().hashCode();
            if (style.getLineStyle().getOuterWidth() != null)
                hashCode += style.getLineStyle().getOuterWidth().hashCode();
            if (style.getLineStyle().getPhysicalWidth() != null)
                hashCode += style.getLineStyle().getPhysicalWidth().hashCode();
            if (style.getLineStyle().getWidth() != null)
                hashCode += style.getLineStyle().getWidth().hashCode();
        }

        if (style.getListStyle() != null) {
            if (style.getListStyle().getBgColor() != null)
                hashCode += style.getListStyle().getBgColor().hashCode();
            if (style.getListStyle().getColor() != null)
                hashCode += style.getListStyle().getColor().hashCode();
            if (style.getListStyle().getColorMode() != null)
                hashCode += style.getListStyle().getColorMode().hashCode();
            if (style.getListStyle().getId() != null)
                hashCode += style.getListStyle().getId().hashCode();
            if (style.getListStyle().getListItemType() != null)
                hashCode += style.getListStyle().getListItemType().hashCode();
            //TODO  getItemIcon()
        }

        if (style.getPolyStyle() != null) {
            if (style.getPolyStyle().getFill() != null)
                hashCode += style.getPolyStyle().getFill().hashCode();
            if (style.getPolyStyle().getColor() != null)
                hashCode += style.getPolyStyle().getColor().hashCode();
            if (style.getPolyStyle().getColorMode() != null)
                hashCode += style.getPolyStyle().getColorMode().hashCode();
            if (style.getPolyStyle().getId() != null)
                hashCode += style.getPolyStyle().getId().hashCode();
            if (style.getPolyStyle().getOutline() != null)
                hashCode += style.getPolyStyle().getOutline().hashCode();
        }

        return String.valueOf(hashCode * 31);
    }

    public static boolean isValid(Track track) {
        if (track == null)
            return false;

        //TODO KML/GX XSD/spec actually allows these to be 0 length, but for our purposes, do 
        //not export KML if we have no data
        if (FileSystemUtils.isEmpty(track.getWhen())
                || FileSystemUtils.isEmpty(track.getCoord()) ||
                track.getWhen().size() != track.getCoord().size())
            return false;

        return true;
    }

    public static Folder exportKMLMapGroup(MapGroup group,
            ExportFilters filters)
            throws FormatNotSupportedException {
        Folder f = new Folder();
        f.setName(group.getFriendlyName());
        f.setFeatureList(new ArrayList<Feature>());
        for (MapItem item : group.getItems()) {
            if (item instanceof Exportable
                    && ((Exportable) item).isSupported(Folder.class)) {
                Folder itemFolder = (Folder) ((Exportable) item).toObjectOf(
                        Folder.class, filters);
                if (itemFolder != null && itemFolder.getFeatureList() != null
                        && itemFolder.getFeatureList().size() > 0) {
                    f.getFeatureList().add(itemFolder);
                }
            }
        }

        for (MapGroup childGroup : group.getChildGroups()) {
            Folder groupFolder = exportKMLMapGroup(childGroup, filters);
            if (groupFolder != null && groupFolder.getFeatureList() != null
                    && groupFolder.getFeatureList().size() > 0) {
                f.getFeatureList().add(groupFolder);
            }
        }

        if (f.getFeatureList().size() < 1) {
            return null;
        }

        return f;
    }

    public static KMZFolder exportKMZMapGroup(MapGroup group,
            ExportFilters filters) throws FormatNotSupportedException {
        final KMZFolder f = new KMZFolder();
        f.setName(group.getFriendlyName());
        for (MapItem item : group.getItems()) {
            //Attempt KMZ, fall back on KML
            if (item instanceof Exportable
                    && ((Exportable) item).isSupported(KMZFolder.class)) {
                KMZFolder itemFolder = (KMZFolder) ((Exportable) item)
                        .toObjectOf(KMZFolder.class, filters);
                if (itemFolder != null && !itemFolder.isEmpty()) {
                    f.getFeatureList().add(itemFolder);
                    if (itemFolder.hasFiles()) {
                        f.getFiles().addAll(itemFolder.getFiles());
                    }
                }
            } else if (item instanceof Exportable
                    && ((Exportable) item).isSupported(Folder.class)) {
                Folder itemFolder = (Folder) ((Exportable) item).toObjectOf(
                        Folder.class, filters);
                if (itemFolder != null && itemFolder.getFeatureList() != null
                        && itemFolder.getFeatureList().size() > 0) {
                    f.getFeatureList().add(itemFolder);
                }
            }
        }

        for (MapGroup childGroup : group.getChildGroups()) {
            KMZFolder groupFolder = exportKMZMapGroup(childGroup, filters);
            if (groupFolder != null && !groupFolder.isEmpty()) {
                f.getFeatureList().add(groupFolder);
                if (groupFolder.hasFiles()) {
                    f.getFiles().addAll(groupFolder.getFiles());
                }
            }
        }

        if (f.isEmpty()) {
            return null;
        }

        return f;
    }
}
