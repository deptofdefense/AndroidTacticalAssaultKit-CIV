
package com.atakmap.android.routes;

import android.content.Context;
import android.content.SharedPreferences;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.Serializer;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Data;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.ExtendedData;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.UUID;

/**
 * Support converting ATAK <code>Route</code> objects to and from KML Supported KML convention: Top
 * level Document/Folder name will determine route name First LineString Placemark will determine
 * points along the route LineStyle/color will determine route color Zero or more Point Placemarks
 * which touch the route Each will become a route Checkpoint KML name will be used as ATAK label All
 * other KML data will be ignored
 * 
 * 
 */
public class RouteKmlIO {

    private static final String TAG = "RouteKmlIO";

    /**
     * While importing a KML route, use this value to determine if a Point Placemark is a route
     * checkpoint
     */
    public static final double CHECKPOINT_TOLERANCE_DISTANCE_METERS = 20;

    /**
     * Points: Exports a list of Points for each checkpoint
     * Line: Exports a LineString containing only checkpoints (not all route vertices)
     * Both: Exports a LineString containing all route vertices, and a list of Points for each checkpoint 
     */
    public enum CheckpointExportMode {
        Points,
        Line,
        Both
    }

    /**
     * Convert route to KML
     * 
     * @param route
     * @param mode
     * @param clampToGround
     * @return
     */
    public static Folder toKml(Context context,
            Route route,
            CheckpointExportMode mode,
            boolean clampToGround) {

        Log.d(TAG,
                "Exporting route: " + route.getTitle() + ", in mode "
                        + mode.toString() + ", with ground clamp: "
                        + clampToGround);

        List<StyleSelector> styleSelector = new ArrayList<>();
        Placemark lineStringPlacemark = null; //holds the Line
        Folder pointFolder = null; //holds the points

        //set style
        LineStyle lineStyle = new LineStyle();
        lineStyle.setWidth(4F);
        lineStyle.setColor(KMLUtil.convertKmlColor(route.getColor()));
        Style style = new Style();
        style.setLineStyle(lineStyle);

        //set style
        IconStyle iconStyle = new IconStyle();
        iconStyle.setColor(KMLUtil.convertKmlColor(route.getColor()));
        //set white pushpin and Google Earth will tint based on color above
        com.ekito.simpleKML.model.Icon icon = new com.ekito.simpleKML.model.Icon();
        String whtpushpin = context
                .getString(R.string.whtpushpin);
        icon.setHref(whtpushpin);

        iconStyle.setIcon(icon);
        style.setIconStyle(iconStyle);

        String styleId = KMLUtil.hash(style);
        style.setId(styleId);
        styleSelector.add(style);

        GeoPointMetaData[] routePoints = route.getMetaDataPoints();
        MapView mv = MapView.getMapView();
        boolean idlWrap180 = mv != null && mv.isContinuousScrollEnabled()
                && GeoCalculations.crossesIDL(routePoints, 0,
                        routePoints.length);

        //first prepare the LineString
        if (mode == CheckpointExportMode.Line
                || mode == CheckpointExportMode.Both) {
            // add all route vertices/points to the route LineString
            Log.d(TAG,
                    "Adding KML route path with all points, count: "
                            + route.getNumPoints());
            lineStringPlacemark = new Placemark();
            lineStringPlacemark.setVisibility(route.getVisible() ? 1 : 0);
            Coordinates coordinates = new Coordinates(KMLUtil.convertKmlCoords(
                    routePoints, clampToGround, idlWrap180));
            LineString lineString = new LineString();
            lineString.setCoordinates(coordinates);
            if (clampToGround)
                lineString.setAltitudeMode("clampToGround");
            else
                lineString.setAltitudeMode("absolute");

            lineStringPlacemark.setId(route.getUID() + route.getTitle()
                    + " Path");
            lineStringPlacemark.setName(route.getTitle() + " Path");
            lineStringPlacemark.setStyleUrl("#" + styleId);
            List<Geometry> geometryList = new ArrayList<>();
            geometryList.add(lineString);
            lineStringPlacemark.setGeometryList(geometryList);

            //now set extended data (route details)
            List<Data> dataList = new ArrayList<>();

            Data data = new Data();
            data.setName("RouteOrder");
            data.setValue(route.getRouteOrder().text);
            dataList.add(data);

            data = new Data();
            data.setName("RouteMethod");
            data.setValue(route.getRouteMethod().text);
            dataList.add(data);

            data = new Data();
            data.setName("RouteDirection");
            data.setValue(route.getRouteDirection().text);
            dataList.add(data);

            data = new Data();
            data.setName("RouteType");
            data.setValue(route.getRouteType().text);
            dataList.add(data);

            if (!FileSystemUtils.isEmpty(route.getPlanningMethod())) {
                data = new Data();
                data.setName("PlanningMethod");
                data.setValue(route.getPlanningMethod());
                dataList.add(data);
            }
            if (dataList.size() > 0) {
                ExtendedData extendedData = new ExtendedData();
                extendedData.setDataList(dataList);
                lineStringPlacemark.setExtendedData(extendedData);
            }
        }

        // Setup Folder containing Point Placemarks for each checkpoint
        if (mode == CheckpointExportMode.Points
                || mode == CheckpointExportMode.Both) {
            //add points
            List<Feature> featureList = new ArrayList<>();
            pointFolder = new Folder();
            pointFolder.setName(route.getTitle() + " Checkpoints");
            pointFolder.setFeatureList(featureList);

            // find all checkpoints
            for (int i = 0; i < route.getNumPoints(); i++) {
                PointMapItem marker = route.getMarker(i);

                // perhaps we could wrap this as a method in Route...
                if (marker == null) {
                    continue;
                }

                Point placemarkPoint = new Point();
                if (clampToGround)
                    placemarkPoint.setAltitudeMode("clampToGround");
                else
                    placemarkPoint.setAltitudeMode("absolute");

                Coordinate coord = KMLUtil.convertKmlCoord(route.getPoint(i),
                        false);
                if (coord == null) {
                    Log.w(TAG, "No checkpoint location set");
                } else {
                    placemarkPoint.setCoordinates(coord);

                    Placemark checkpointPlacemark = new Placemark();
                    checkpointPlacemark.setId(marker.getUID());
                    checkpointPlacemark.setName(marker.getMetaString(
                            "callsign", null));
                    checkpointPlacemark.setStyleUrl("#" + styleId);
                    checkpointPlacemark.setVisibility(marker.getVisible() ? 1
                            : 0);
                    ArrayList<Geometry> geometryList = new ArrayList<>();
                    geometryList.add(placemarkPoint);
                    checkpointPlacemark.setGeometryList(geometryList);
                    featureList.add(checkpointPlacemark);

                    Log.d(TAG,
                            "Adding KML route placemark: "
                                    + marker.getMetaString("callsign", null));
                }
            } //end checkpoint loop

            if (featureList == null || featureList.size() < 1) {
                Log.d(TAG, "Did not export any route checkpoints");
                pointFolder = null;
            }
        } //end process points

        //be sure we were able to create some KML data
        List<Feature> featureList = new ArrayList<>();
        if (lineStringPlacemark != null)
            featureList.add(lineStringPlacemark);
        if (pointFolder != null)
            featureList.add(pointFolder);

        if (featureList == null || featureList.size() < 1) {
            Log.w(TAG, "Failed to export any route features");
            return null;
        }

        Folder folder = new Folder();
        folder.setName(route.getTitle());
        folder.setFeatureList(featureList);
        folder.setStyleSelector(styleSelector);
        return folder;
    }

    /**
     * Write KML route to specified file
     * 
     * @param kml
     * @param file
     * @throws Exception
     */
    public static void write(Kml kml, File file) throws Exception {
        File parent = file.getParentFile();
        if (!IOProviderFactory.exists(parent)) {
            if (!IOProviderFactory.mkdirs(parent)) {
                Log.d(TAG, "Failed to make dir at " + parent.getAbsolutePath());
            }
        }

        // TODO for performance reuse Serializer? Or use PullParser
        Serializer serializer = new Serializer();
        StringWriter sw = new StringWriter();
        serializer.write(kml, sw);
        KMLUtil.write(sw.toString(), file);
    }

    /**
     * Find a single LineString and all Point Placemarks
     * 
     * 
     */
    static class RoutePlacemarkHandler implements FeatureHandler<Placemark> {
        Placemark lineString = null;
        final List<Placemark> points = new ArrayList<>();

        @Override
        public boolean process(Placemark placemark) {
            if (placemark == null) {
                Log.e(TAG, "Unable to parse Route Placemark");
                return false;
            }

            List<Geometry> geometries = KMLUtil.getGeometries(placemark,
                    Geometry.class);
            if (geometries == null || geometries.size() < 1) {
                Log.e(TAG, "Unable to parse Route Placemark Geometry");
                return false;
            }

            // loop geometries for this Placemark
            for (Geometry geometry : geometries) {
                if (geometry == null) {
                    Log.e(TAG, "Unable to parse null Route Placemark Geometry");
                    continue;
                }

                if (lineString == null && geometry instanceof LineString) {
                    lineString = placemark;
                } else if (geometry instanceof Point) {
                    points.add(placemark);
                }
            }

            return false;
        }
    }

    /**
     * Convert KML to route
     * 
     * @param kml
     * @return
     */
    public static Route toRoute(MapView mapView, Kml kml, MapGroup routeGroup,
            MapGroup waypointGroup, SharedPreferences prefs) {

        // pull out all relevant Placemarks
        RoutePlacemarkHandler routeHandler = new RoutePlacemarkHandler();
        KMLUtil.deepFeatures(kml, routeHandler, Placemark.class);

        // process LineString
        if (routeHandler.lineString == null) {
            Log.w(TAG, "KML does not contain a LineString");
            return null;
        }

        LineString lineString = KMLUtil.getFirstGeometry(
                routeHandler.lineString, LineString.class);
        if (lineString == null || lineString.getCoordinates() == null
                || lineString.getCoordinates().getList() == null ||
                lineString.getCoordinates().getList().size() < 1) {
            Log.w(TAG, "KML does not contain a valid LineString");
            return null;
        }

        ArrayList<Coordinate> points = lineString.getCoordinates().getList();
        Log.d(TAG, "Route points: " + points.size()
                + ", potential checkpoints: " + routeHandler.points.size());

        // create route
        String prefix = prefs.getString("waypointPrefix", "CP");
        String routeName = getRouteName(kml, routeHandler.lineString);
        MapGroup group = routeGroup.addGroup(routeName);
        group.setMetaBoolean("addToObjList", false);
        int color = Integer.parseInt(prefs.getString("defaultRouteColor",
                String.valueOf(Route.DEFAULT_ROUTE_COLOR)));

        Route route = new Route(mapView, routeName, color, prefix, UUID
                .randomUUID().toString());

        // XXX - hack for bug 2331 -- disable refreshes
        route.setMetaBoolean("__ignoreRefresh", true);

        Style style = KMLUtil.getStyle(kml, routeHandler.lineString);
        if (style != null && style.getLineStyle() != null
                && !FileSystemUtils.isEmpty(style.getLineStyle().getColor())) {
            route.setColor(KMLUtil.parseKMLColor(style.getLineStyle()
                    .getColor()));
        }
        if (style != null && style.getLineStyle() != null
                && style.getLineStyle().getWidth() != null) {
            double weight = (double) style.getLineStyle().getWidth();
            if (weight <= 0D)
                weight = 1D;
            else if (weight >= 10D || Double.isNaN(weight))
                weight = 10D;
            route.setStrokeWeight(weight);
        }

        // see if route parameters are set as ExtendedData
        if (routeHandler.lineString.getExtendedData() != null
                && routeHandler.lineString.getExtendedData()
                        .getDataList() != null
                &&
                routeHandler.lineString.getExtendedData().getDataList()
                        .size() > 0) {
            for (Data data : routeHandler.lineString.getExtendedData()
                    .getDataList()) {
                if ("RouteMethod".equals(data.getName())
                        && !FileSystemUtils.isEmpty(data.getValue())
                        && Route.isRouteMethod(data.getValue()))
                    route.setRouteMethod(data.getValue());
                else if ("RouteOrder".equals(data.getName())
                        && !FileSystemUtils.isEmpty(data.getValue())
                        && Route.isRouteOrder(data.getValue()))
                    route.setRouteOrder(data.getValue());
                else if ("RouteDirection".equals(data.getName())
                        && !FileSystemUtils.isEmpty(data.getValue())
                        && Route.isRouteDirection(data.getValue()))
                    route.setRouteDirection(data.getValue());
                else if ("RouteType".equals(data.getName())
                        && !FileSystemUtils.isEmpty(data.getValue())
                        && Route.isRouteType(data.getValue()))
                    route.setRouteType(data.getValue());
                else if ("PlanningMethod".equals(data.getName())
                        && !FileSystemUtils.isEmpty(data.getValue()) /*
                                                                      * &&
                                                                      * Route.isPlanningMethod(data
                                                                      * .getValue())
                                                                      */)
                    route.setPlanningMethod(data.getValue());
            }
        }

        // maintain a list of checkpoints already included in route, include each only once
        List<Placemark> checkpoints = new ArrayList<>();

        // https://developers.google.com/kml/documentation/altitudemode#clamptoground
        // Per Google's documentation, any KML feature with no altitude mode 
        // specified will default to clampToGround.

        String altitudeMode = lineString.getAltitudeMode();
        if (altitudeMode == null)
            altitudeMode = "clampToGround";

        // walk all route points
        GeoPoint geoPoint = null;

        List<PointMapItem> pmiList = new ArrayList<>();

        for (Coordinate point : points) {
            if (point == null)
                continue;

            geoPoint = KMLUtil.convertPoint(point).get();

            if (altitudeMode.equals("clampToGround") &&
                    geoPoint.isAltitudeValid()) {

                geoPoint = new GeoPoint(geoPoint.getLatitude(),
                        geoPoint.getLongitude(),
                        GeoPoint.UNKNOWN,
                        geoPoint.getCE(),
                        geoPoint.getLE());
            } // XXX - not sure how ATAK interprets AGL, may be best to add in
              //       elevation here???
            else if (altitudeMode.equals("relativeToGround") &&
                    geoPoint.isAltitudeValid()) {

                geoPoint = new GeoPoint(geoPoint.getLatitude(),
                        geoPoint.getLongitude(), geoPoint.getAltitude(),
                        AltitudeReference.AGL,
                        geoPoint.getCE(),
                        geoPoint.getLE());
            }

            // see if point is a checkpoint
            Placemark pointPlacemark = match(routeHandler.points, point);
            if (pointPlacemark != null
                    && !checkpoints.contains(pointPlacemark)) {
                checkpoints.add(pointPlacemark);
                Marker wayPoint = Route.createWayPoint(
                        GeoPointMetaData.wrap(geoPoint),
                        UUID.randomUUID().toString());
                String callsign = pointPlacemark.getName();
                if (!FileSystemUtils.isEmpty(callsign)) {
                    wayPoint.setMetaString("callsign", callsign);
                    wayPoint.setTitle(callsign);
                }
                Log.d(TAG, "Adding route waypoint: " + callsign);
                pmiList.add(wayPoint);
            } else {
                PointMapItem controlPoint = Route.createControlPoint(geoPoint,
                        UUID.randomUUID().toString());
                // just add as point (non checkpoint)
                pmiList.add(controlPoint);
            }
        }

        PointMapItem[] pmiArray = new PointMapItem[pmiList.size()];
        pmiList.toArray(pmiArray);
        route.addMarkers(0, pmiArray);

        if (route.getNumPoints() < 2) {
            Log.w(TAG, "Unable to add at least 2 points to route " + routeName);
            return null;
        }

        // XXX - hack for bug 2331 -- reenable refreshes
        route.setMetaBoolean("__ignoreRefresh", false);

        Log.d(TAG, "Added " + route.getNumPoints() + " points from KML");
        return route;
    }

    /**
     * See if the specified point matches one of the Point Placemarks (within 20 meters) TODO
     * currently a checkpoint must be within 20 meters of a point in the route, instead allow a
     * checkpoint to be within 20 meters of the line between any two points in the route
     * 
     * @param points
     * @param point
     * @return
     */
    private static Placemark match(List<Placemark> points, Coordinate point) {
        if (points == null || points.size() < 1 || point == null)
            return null;

        for (Placemark placemark : points) {
            Point pointGeometry = KMLUtil.getFirstGeometry(placemark,
                    Point.class);
            if (pointGeometry == null)
                continue;

            if (GeoCalculations.distanceTo(
                    KMLUtil.convertPoint(pointGeometry.getCoordinates()).get(),
                    KMLUtil.convertPoint(
                            point)
                            .get()) < CHECKPOINT_TOLERANCE_DISTANCE_METERS)
                return placemark;
        }

        return null;
    }

    /**
     * Get name of route based on KML Use KML top level Document or Folder name, if available
     * Otherwise use name or ID of Placemark
     * 
     * @param kml
     * @param placemark
     * @return
     */
    private static String getRouteName(Kml kml, Placemark placemark) {
        if (kml.getFeature() != null && kml.getFeature() instanceof Document) {
            Document d = (Document) kml.getFeature();
            if (!FileSystemUtils.isEmpty(d.getName()))
                return d.getName();
        }

        if (kml.getFeature() != null && kml.getFeature() instanceof Folder) {
            Folder f = (Folder) kml.getFeature();
            if (!FileSystemUtils.isEmpty(f.getName()))
                return f.getName();
        }

        if (!FileSystemUtils.isEmpty(placemark.getName()))
            return placemark.getName();

        if (!FileSystemUtils.isEmpty(placemark.getId()))
            return placemark.getId();

        // TODO iterate existing route and iterate count/name?
        return "KML Route";
    }

    /**
     * Read KML route from specified file
     * 
     * @param file
     * @return
     */
    public static Kml read(File file, Context context) {
        // TODO for performance reuse Serializer? or use DOM?
        File original = file;
        if (file.getName().toLowerCase(LocaleUtil.getCurrent())
                .endsWith("kmz")) {
            try {
                file = KMLUtil.getKmlFileFromKmzFile(file,
                        context.getCacheDir());
            } catch (IOException e) {
                Log.e(TAG,
                        "Unable to read KMZ file: "
                                + original.getAbsolutePath(),
                        e);
                return null;
            }
        }

        Serializer serializer = new Serializer();
        try (FileInputStream fis = IOProviderFactory.getInputStream(file)) {
            return serializer.read(fis);
        } catch (Exception e) {
            if (file == null)
                Log.e(TAG, "KML file is null", e);
            else
                Log.e(TAG, "Unable to read file: " + file.getAbsolutePath(), e);
        }

        return null;
    }
}
