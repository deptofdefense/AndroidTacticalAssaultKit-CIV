
package com.atakmap.spatial.kml;

import android.graphics.Rect;
import android.util.Xml;

import com.atakmap.android.maps.ArchiveEntryMapDataRef;
import com.atakmap.android.maps.FileMapDataRef;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.ekito.simpleKML.model.Boundary;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Data;
import com.ekito.simpleKML.model.ExtendedData;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.LinearRing;
import com.ekito.simpleKML.model.MultiGeometry;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.PolyStyle;
import com.ekito.simpleKML.model.Polygon;
import com.ekito.simpleKML.model.Style;

import org.simpleframework.xml.Serializer;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Convert KML data to WKT and MapItem Intended to support ATAK Spatial DB, not general conversion
 * to/from KML
 * 
 * 
 */
public class KMLConversion {

    private static final String TAG = "KMLConversion";

    /***************************
     * Placemark
     */
    public static MapItem[] toMapItems(Placemark placemark) {
        Style style = KMLUtil.getFirstStyle(placemark, Style.class);
        List<Geometry> geometries = KMLUtil.getGeometries(placemark,
                Geometry.class);
        List<MapItem> items = toMapItems(placemark, geometries, style);

        if (items == null || items.size() < 1) {
            Log.w(TAG, placemark.getName()
                    + " Placemark unable to convert any Map Items");
            return null;
        }

        // TODO check and make sure this covers whatever we supported previously for extended data
        ExtendedData ext = placemark.getExtendedData();
        List<Data> data = null;
        if (ext != null)
            data = ext.getDataList();

        for (MapItem item : items) {
            if (item == null)
                continue;

            Map<String, Object> kmlBundle = new HashMap<>();
            if (data != null && data.size() > 0) {
                for (Data d : data) {
                    kmlBundle.put(d.getName(), d.getValue());
                }
            }

            item.setMetaMap("kmlExtendedData", kmlBundle);
        }

        return items.toArray(new MapItem[0]);
    }

    private static List<MapItem> toMapItems(Placemark placemark,
            List<Geometry> geometries,
            Style style) {

        if (geometries == null || geometries.size() < 1) {
            Log.w(TAG, "Placemark: " + placemark.getName()
                    + " has no geometries");
            return null;
        }

        List<MapItem> items = new ArrayList<>();

        for (Geometry geometry : geometries) {
            if (geometry instanceof Point) {
                MapItem[] i = toMapItems(placemark, (Point) geometry, style);
                if (i != null && i.length > 0)
                    items.addAll(Arrays.asList(i));
                else
                    Log.w(TAG, placemark.getName()
                            + " failed to convert Geometry Point");
            } else if (geometry instanceof LineString) {
                MapItem[] i = toMapItems(placemark, (LineString) geometry,
                        style);
                if (i != null && i.length > 0)
                    items.addAll(Arrays.asList(i));
                else
                    Log.w(TAG, placemark.getName()
                            + " failed to convert Geometry LineString");
            } else if (geometry instanceof Polygon) {
                MapItem[] i = toMapItems(placemark, (Polygon) geometry, style);
                if (i != null && i.length > 0)
                    items.addAll(Arrays.asList(i));
                else
                    Log.w(TAG, placemark.getName()
                            + " failed to convert Geometry Polygon");
            } else if (geometry instanceof MultiGeometry) {
                MapItem[] i = toMapItems(placemark, (MultiGeometry) geometry,
                        style);
                if (i != null && i.length > 0)
                    items.addAll(Arrays.asList(i));
                else
                    Log.w(TAG, placemark.getName()
                            + " failed to convert Geometry MultiGeometry");
            } else
                Log.w(TAG,
                        "Cannot convert to MapItems, unsupported geometry type: "
                                + geometry.getClass().getSimpleName());
        }

        return items;
    }

    /*************************************************
     * Point
     */
    public static MapItem[] toMapItems(Placemark placemark, Point point,
            Style style) {

        int styleColor = -1;
        com.ekito.simpleKML.model.Icon icon = null;

        if (style != null) {
            IconStyle istyle = style.getIconStyle();
            if (istyle != null && istyle.getColor() != null)
                styleColor = KMLUtil.parseKMLColor(istyle.getColor());
            if (istyle != null) {
                icon = istyle.getIcon();
            }
        }

        Coordinate coord = point.getCoordinates();
        if (coord == null) {
            Log.w(TAG, placemark.getName() + " Point has no coordinates");
            return null;
        }

        Marker m;

        Double alt = coord.getAltitude();

        final String uid = UUID.randomUUID().toString();
        if (alt == null || alt.isNaN()) {
            m = new Marker(new GeoPoint(coord.getLatitude(),
                    coord.getLongitude()), uid);
        } else {
            m = new Marker(new GeoPoint(coord.getLatitude(),
                    coord.getLongitude(), alt), uid);
        }

        m.setTitle(placemark.getName());

        m.setMetaString("menu", "menus/immutable_point.xml");
        m.setMarkerHitBounds(new Rect(-32, -32, 32, 32));
        m.setClickable(true);

        if (styleColor != -1)
            m.setMetaInteger("color", styleColor);

        String test = null;
        if (icon != null && !FileSystemUtils.isEmpty(icon.getHref()))
            test = resolveReference(icon.getHref());

        if (test != null) {
            test = FileSystemUtils.getItem(test).toString();

            // now have a complete path from root to file. Separate at .kmz or .zip and use arch,
            // otherwise use file
            String kmzstr = ".kmz/";
            String zipstr = ".zip/";

            String[] splKmz = test.split(kmzstr);
            String[] splZip = test.split(zipstr);

            if (splKmz.length == 2 && splZip.length == 1) { // there was s single .kml/ instance
                m.setIcon(new Icon.Builder()
                        .setImageUri(0, "arc://" + splKmz[0] + "!/" + splKmz[1])
                        .setAnchor(0, 0).setColor(0, styleColor).build());
            } else if (splZip.length == 2 && splKmz.length == 1) { // there was s single .kml/
                                                                   // instance
                ArchiveEntryMapDataRef aemdr = new ArchiveEntryMapDataRef(
                        splZip[0] + ".zip",
                        splZip[1]);
                Icon mi = new Icon.Builder().setImageUri(0, aemdr.toUri())
                        .setAnchor(0, 0)
                        .setColor(0, styleColor).build();
                m.setIcon(mi);
            } else if (splZip.length == 1 && splKmz.length == 1) { // found neither. Treat it as a
                                                                   // file reference
                FileMapDataRef fmdr = new FileMapDataRef(test);
                Icon mi = new Icon.Builder().setImageUri(0, fmdr.toUri())
                        .setAnchor(0, 0)
                        .setColor(0, styleColor).build();
                m.setIcon(mi);
            } else {
                m.setIcon(new Icon.Builder()
                        .setImageUri(0, "asset:/icons/reference_point.png")
                        .setColor(0, styleColor).build());
            }
        } else {
            m.setIcon(new Icon.Builder()
                    .setImageUri(0, "asset:/icons/reference_point.png")
                    .setColor(0, styleColor).build());
        }

        MapItem[] mapItems = new MapItem[1];
        mapItems[0] = m;
        return mapItems;
    }

    // TODO review this method
    private static String resolveReference(String userRef) {
        if (userRef != null) {
            // Log.d(TAG, userRef);
            String[] test = userRef.split("!/");

            if (test.length != 2) {
                return null;
            }

            //TODO should this be "overlays/" now?
            if (test[0].endsWith(".kmz")) {
                test[0] = "kml/" + test[0]; // path relative to the kmz folder
            } else {
                test[0] = "kml"; // path relative to the kml folder
            }

            int slashIndx = test[1].indexOf('/');
            while (slashIndx > -1) {
                String part = test[1].substring(0, slashIndx);

                if (part.equals("..")) {
                    // go up one dir
                    int lstIndx = test[0].lastIndexOf('/');
                    if (lstIndx > -1) {
                        test[0] = test[0].substring(0, lstIndx);
                    } else {
                        test[0] = "";
                    }
                    test[1] = test[1].substring(slashIndx + 1);
                } else {
                    // test[0] = test[0] + "/" + part;
                    break;
                }

                slashIndx = test[1].indexOf('/');
            }

            if (test[0].equals("")) {
                return test[1];
            }
            return test[0] + "/" + test[1];
        }
        return null;
    }

    /*************************************************
     * LineString
     */
    public static MapItem[] toMapItems(Placemark placemark,
            LineString lineString, Style style) {

        // get styling
        int styleColor = -1;
        float styleWidth = 2;

        if (style != null) {
            LineStyle lstyle = style.getLineStyle();
            if (lstyle != null && lstyle.getColor() != null)
                styleColor = KMLUtil.parseKMLColor(lstyle.getColor());
            if (lstyle != null && lstyle.getWidth() != null)
                styleWidth = lstyle.getWidth();
        }

        GeoPointMetaData[] ptArray = KMLUtil.convertCoordinates(lineString
                .getCoordinates());
        if (ptArray == null || ptArray.length < 1) {
            Log.w(TAG, placemark.getName()
                    + " LineString could not convert any coordinates");
            return null;
        }

        // create Map Item
        Polyline poly = new Polyline(UUID.randomUUID().toString());
        poly.setStrokeColor(styleColor);
        poly.setStrokeWeight(styleWidth);
        poly.setPoints(ptArray);

        // set name from the container KML Placemark's name
        poly.setTitle(placemark.getName());

        return new MapItem[] {
                poly
        };
    }

    /*************************************************
     * LinearRing
     */

    public static MapItem[] toMapItems(Placemark placemark,
            LinearRing linearRing, Style style) {
        MapItem[] mapItems = new MapItem[1];
        mapItems[0] = toMapItem(placemark, linearRing, style);
        return mapItems;
    }

    public static MapItem toMapItem(Placemark placemark, LinearRing linearRing,
            Style style) {
        GeoPointMetaData[] ptArray = KMLUtil.convertCoordinates(linearRing
                .getCoordinates());
        if (ptArray == null || ptArray.length < 1) {
            Log.w(TAG, placemark.getName()
                    + " LineString could not convert any coordinates");
            return null;
        }

        return toMapItem(placemark, ptArray, style);
    }

    /**
     * Given a placemark, point array and style - turn it into a MapItem.
     * @param placemark the KML Placemark
     * @param ptArray the point array
     * @param style the style
     * @return a map item representative of the above composition.
     */
    public static MapItem toMapItem(Placemark placemark,
            GeoPointMetaData[] ptArray,
            Style style) {

        // get styling
        int styleColor = -1;
        float styleWidth = 2;
        int polyColor = -1;
        PolyStyle polyStyle = null;

        if (style != null) {
            LineStyle lstyle = style.getLineStyle();
            if (lstyle != null && lstyle.getColor() != null)
                styleColor = KMLUtil.parseKMLColor(lstyle.getColor());
            if (lstyle != null && lstyle.getWidth() != null)
                styleWidth = lstyle.getWidth();
            polyStyle = style.getPolyStyle();
            if (polyStyle != null && polyStyle.getColor() != null)
                polyColor = KMLUtil.parseKMLColor(polyStyle.getColor());
        }

        Polyline poly = new Polyline(UUID.randomUUID().toString());
        poly.setStrokeColor(styleColor);
        poly.setStrokeWeight(styleWidth);
        poly.setFillColor(polyColor);
        poly.addStyleBits(Polyline.STYLE_CLOSED_MASK); // Has to be closed or a fill won't draw. And
                                                       // linearrings are always closed anyway.
        poly.setPoints(ptArray);

        if (polyStyle != null && polyStyle.getFill())
            poly.addStyleBits(Shape.STYLE_FILLED_MASK);
        else
            poly.removeStyleBits(Shape.STYLE_FILLED_MASK);

        if (polyStyle != null && polyStyle.getOutline())
            poly.addStyleBits(Shape.STYLE_STROKE_MASK);
        else
            poly.removeStyleBits(Shape.STYLE_STROKE_MASK);

        return poly;
    }

    /*************************************************
     * Polygon
     */
    public static MapItem[] toMapItems(Placemark placemark, Polygon polygon,
            Style style) {

        List<Boundary> innerBoundaryIs = polygon.getInnerBoundaryIs();
        Boundary outerBoundaryIs = polygon.getOuterBoundaryIs();

        if ((innerBoundaryIs == null || innerBoundaryIs.size() < 1
                || innerBoundaryIs
                        .get(0)
                        .getLinearRing() == null)
                &&
                (outerBoundaryIs == null
                        || outerBoundaryIs.getLinearRing() == null)) {
            Log.w(TAG, placemark.getName() + " Polygon has no boundary data");
            return null;
        }

        List<MapItem> mapItems = new ArrayList<>();

        if (innerBoundaryIs != null && innerBoundaryIs.size() > 0) {
            for (Boundary curInnerBoundaryIs : innerBoundaryIs) {
                if (curInnerBoundaryIs != null
                        && curInnerBoundaryIs.getLinearRing() != null) {
                    GeoPointMetaData[] ptArray = KMLUtil
                            .convertCoordinates(curInnerBoundaryIs
                                    .getLinearRing().getCoordinates());
                    if (ptArray == null || ptArray.length < 1) {
                        Log.d(TAG,
                                placemark.getName()
                                        + " Polygon inner could not convert any coordinates");
                    } else {
                        MapItem innerItem = toMapItem(placemark, ptArray,
                                style);
                        if (innerItem == null) {
                            Log.w(TAG,
                                    placemark.getName()
                                            + " Polygon inner could not converted to Map Item");
                        } else {
                            innerItem.setTitle(placemark.getName());
                            mapItems.add(innerItem);
                        }
                    }
                }
            }
        }

        if (outerBoundaryIs != null
                && outerBoundaryIs.getLinearRing() != null) {
            GeoPointMetaData[] ptArray = KMLUtil
                    .convertCoordinates(outerBoundaryIs
                            .getLinearRing()
                            .getCoordinates());
            if (ptArray == null || ptArray.length < 1) {
                Log.d(TAG, placemark.getName()
                        + " Polygon outer could not convert any coordinates");
            } else {
                MapItem outerItem = toMapItem(placemark, ptArray, style);
                if (outerItem == null) {
                    Log.w(TAG, placemark.getName()
                            + " Polygon outer could not converted to Map Item");
                } else {
                    outerItem.setTitle(placemark.getName());
                    mapItems.add(outerItem);
                }
            }
        }

        if (mapItems.size() < 1) {
            Log.w(TAG, placemark.getName()
                    + " Polygon unable to convert any Map Items");
            return null;
        }

        return mapItems.toArray(new MapItem[0]);
    }

    /*************************************************
     * MultiGeometry
     */

    public static MapItem[] toMapItems(Placemark placemark,
            MultiGeometry multiGeom, Style style) {

        List<MapItem> items = toMapItems(placemark,
                multiGeom.getGeometryList(), style);

        if (items == null || items.size() < 1) {
            Log.w(TAG, placemark.getName()
                    + " MultiGeometry unable to convert any Map Items");
            return null;
        }

        return items.toArray(new MapItem[0]);
    }

    /*************************************************
     * Conversion to String
     */

    /**
     * Convert KML Style to a basic (stripped down) KML string for Spatialite Clear "id" attribute
     * and "gx" extensions (on LineStyle)
     * 
     * @param style the kml style to turn into a string
     * @return the valid kml string.
     */
    public static String toSpatialiteString(Style style,
            Serializer serializer) {
        Style s = new Style();
        s.setBalloonStyle(style.getBalloonStyle());
        s.setIconStyle(style.getIconStyle());
        if (s.getIconStyle() != null) {
            s.getIconStyle().setHotSpot(null);
            if (s.getIconStyle().getIcon() != null
                    && s.getIconStyle().getIcon().getHref() != null) {
                String iconUrl = s.getIconStyle().getIcon().getHref();
                if (iconUrl.contains("http://")
                        || iconUrl.contains("https://")) {
                    s.getIconStyle().getIcon().setHref(null);
                    Log.d(TAG,
                            "Ignoring icon referencing HTTP(S) resource...  These are not yet supported: "
                                    + iconUrl);
                }
            }
        }

        s.setLabelStyle(style.getLabelStyle());
        s.setPolyStyle(style.getPolyStyle());
        s.setListStyle(style.getListStyle());
        if (s.getListStyle() != null) {
            List<com.ekito.simpleKML.model.Icon> itemIcons = s.getListStyle()
                    .getItemIcon();
            if (itemIcons != null && itemIcons.size() > 0) {
                for (com.ekito.simpleKML.model.Icon itemIcon : itemIcons) {
                    if (itemIcon != null && itemIcon.getHref() != null) {
                        String iconUrl = itemIcon.getHref();
                        if (iconUrl.contains("http://")
                                || iconUrl.contains("https://")) {
                            itemIcon.setHref(null);
                            Log.d(TAG,
                                    "Ignoring ItemIcon referencing HTTP(S) resource...  These are not yet supported: "
                                            + iconUrl);
                        }
                    }
                }
            }
        }

        if (style.getLineStyle() != null) {
            LineStyle lstyle = new LineStyle();
            lstyle.setWidth(style.getLineStyle().getWidth());
            lstyle.setColor(style.getLineStyle().getColor());
            lstyle.setColorMode(style.getLineStyle().getColorMode());
            s.setLineStyle(lstyle);
        }

        return KMLUtil.toString(s, serializer);
    }

    /**
     * Convert KML Geometry to a basic (stripped down) KML string for Spatialite implements old code
     * from these methods: removeAllChildElementsExceptCoordinates xmlElemToString
     * 
     * @param geometry the geometry to convert into a spatial lite string.
     * @return the appropriate spatial lite string.
     */
    public static String toSpatialiteString(Geometry geometry) {
        // TODO more efficient to reuse serializer?
        XmlSerializer serializer = Xml.newSerializer();
        StringWriter sw = new StringWriter();
        BufferedWriter writer = new BufferedWriter(sw, 1000);

        try {
            serializer.setOutput(writer);

            if (geometry instanceof Point) {
                appendPoint(serializer, (Point) geometry);
                writer.flush();
                return sw.toString();
            } else if (geometry instanceof LineString) {
                appendLineString(serializer, (LineString) geometry);
                writer.flush();
                return sw.toString();
            } else if (geometry instanceof LinearRing) {
                appendLinearRing(serializer, (LinearRing) geometry);
                writer.flush();
                return sw.toString();
            } else if (geometry instanceof Polygon) {
                appendPolygonLinearRing(serializer, (Polygon) geometry);
                writer.flush();
                return sw.toString();
            }
            // Note individual Geometry currently being pulled from MultiGeometry prior to coming
            // into this method
            else {
                Log.w(TAG, "Ignoring unsupported Geometry: "
                        + geometry.getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize Geometry to string: "
                    + geometry.getClass().getSimpleName(), e);
        }

        return null;
    }

    /**
     * Appends a Point element to a KML document, stripped down for Spatialite
     * 
     * @param serializer XmlSerializer with output set
     * @param point point to write
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     * @throws IOException if the serialization fails.
     */
    private static void appendPoint(XmlSerializer serializer, Point point)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        serializer.startTag("", "Point");
        serializer.startTag("", "coordinates");
        serializer.text(serializePoint(point.getCoordinates()));
        serializer.endTag("", "coordinates");
        serializer.endTag("", "Point");
    }

    private static String serializePoint(Coordinate coordinate) {
        // TODO optimize this method, less string creation...
        if (coordinate == null)
            return null;
        String ret = coordinate.getLongitude() + "," + coordinate.getLatitude();
        if (coordinate.getAltitude() != null &&
                !Double.isNaN(coordinate.getAltitude())) {
            ret += ","
                    + (Double.isInfinite(coordinate.getAltitude()) ? 0
                            : coordinate.getAltitude());
        }
        ret += " ";
        return ret;
    }

    /**
     * Appends a LineString element to a KML document, stripped down for Spatialite
     * 
     * @param serializer XmlSerializer with output set
     * @param lineString the line string to convert into a kml element
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     * @throws IOException
     */
    private static void appendLineString(XmlSerializer serializer,
            LineString lineString) throws IllegalArgumentException,
            IllegalStateException, IOException {
        serializer.startTag("", "LineString");
        serializer.startTag("", "coordinates");
        serializer.text(toString(lineString.getCoordinates(), false));
        serializer.endTag("", "coordinates");
        serializer.endTag("", "LineString");
    }

    /**
     * Appends a LineRing element to a KML document, stripped down for Spatialite
     * 
     * @param serializer XmlSerializer with output set
     * @param linearRing
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     * @throws IOException
     */
    private static void appendLinearRing(XmlSerializer serializer,
            LinearRing linearRing) throws IllegalArgumentException,
            IllegalStateException, IOException {
        serializer.startTag("", "LinearRing");
        serializer.startTag("", "coordinates");
        serializer.text(toString(linearRing.getCoordinates(), true));
        serializer.endTag("", "coordinates");
        serializer.endTag("", "LinearRing");
    }

    /**
     * Appends a Polygon element containing a LinearRing element to a KML document, stripped down
     * for Spatialite
     * 
     * @param serializer XmlSerializer with output set
     * @param polygon
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     * @throws IOException
     */
    private static void appendPolygonLinearRing(XmlSerializer serializer,
            Polygon polygon) throws IllegalArgumentException,
            IllegalStateException, IOException {

        serializer.startTag("", "Polygon");
        serializer.startTag("", "outerBoundaryIs");
        serializer.startTag("", "LinearRing");
        serializer.startTag("", "coordinates");
        serializer.text(toString(polygon.getOuterBoundaryIs().getLinearRing()
                .getCoordinates(),
                true));
        serializer.endTag("", "coordinates");
        serializer.endTag("", "LinearRing");
        serializer.endTag("", "outerBoundaryIs");

        // TODO test this serialization and ATAK handling of inner boundary(ies)
        if (polygon.getInnerBoundaryIs() != null
                && polygon.getInnerBoundaryIs().size() > 0) {
            for (Boundary curInnerBoundaryIs : polygon.getInnerBoundaryIs()) {
                if (curInnerBoundaryIs != null
                        && curInnerBoundaryIs.getLinearRing() != null
                        && curInnerBoundaryIs.getLinearRing()
                                .getCoordinates() != null
                        && curInnerBoundaryIs.getLinearRing().getCoordinates()
                                .getList() != null
                        && curInnerBoundaryIs.getLinearRing().getCoordinates()
                                .getList().size() > 0) {
                    serializer.startTag("", "innerBoundaryIs");
                    serializer.startTag("", "LinearRing");
                    serializer.startTag("", "coordinates");
                    serializer.text(toString(curInnerBoundaryIs.getLinearRing()
                            .getCoordinates(),
                            true));
                    serializer.endTag("", "coordinates");
                    serializer.endTag("", "LinearRing");
                    serializer.endTag("", "innerBoundaryIs");
                }
            }
        }

        serializer.endTag("", "Polygon");
    }

    /*************************************************
     * Conversion to String
     * 
     * @throws IOException
     */
    public static String toString(Coordinates coordinates, boolean bCloseShape)
            throws IOException {
        if (coordinates == null || coordinates.getList() == null
                || coordinates.getList().size() < 1)
            throw new IOException("No coordinates specified");

        StringBuilder s = new StringBuilder();

        ArrayList<Coordinate> points = coordinates.getList();
        for (Coordinate p : points)
            s.append(serializePoint(p));

        // Close shape by inserting first point again if it is not already there
        if (bCloseShape) {
            Coordinate firstPoint = points.get(0);
            Coordinate lastPoint = points.get(points.size() - 1);
            if ((Double.compare(firstPoint.getLongitude(),
                    lastPoint.getLongitude()) != 0)
                    ||
                    (Double.compare(firstPoint.getLatitude(),
                            lastPoint.getLatitude()) != 0))
                s.append(serializePoint(firstPoint));
        }

        return s.toString();
    }
}
