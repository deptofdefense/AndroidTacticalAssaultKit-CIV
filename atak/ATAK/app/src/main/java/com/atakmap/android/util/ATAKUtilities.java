
package com.atakmap.android.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.conversion.GeomagneticField;
import android.net.Uri;
import android.util.Base64;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import com.atakmap.android.icons.IconsMapComponent;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbars.BullseyeTool;
import com.atakmap.android.user.icon.SpotMapPalletFragment;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.MutableMGRSPoint;

import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableUTMPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.elevation.ElevationManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.util.List;
import java.util.Map;

/**
 * Home for utility functions that don't have a better home yet. Should consolidate functions like
 * findSelf that otherwise will be copy-pasted 20 times.
 */
public class ATAKUtilities {

    private static final String TAG = "ATAKUtilities";

    // For decoding base-64 images
    private static final String BASE64 = "base64,";
    private static final String BASE64_2 = "base64";
    private static final String BASE64_PNG = "iVBORw0K";

    /**
     * Uses logic to obtain the best display name for a specific MapItem in the system.
     * @param item The map item to get the display name from.
     * @return the display name.
     */
    public static String getDisplayName(final MapItem item) {
        if (item == null)
            return "";

        String title = item.getTitle();
        if (!FileSystemUtils.isEmpty(title))
            return title;

        if (item instanceof Marker) {
            // For markers that don't have a set title but an underlying callsign
            if (FileSystemUtils.isEmpty(title))
                title = item.getMetaString("callsign", "");

            // Shape markers without a title
            if (FileSystemUtils.isEmpty(title))
                title = item.getMetaString("shapeName", "");

            // Do not perform shape lookup here (see ATAK-10593)
            // If "shapeName" isn't specified then assume it's untitled
            // or address the issue separately
        }

        if (FileSystemUtils.isEmpty(title)) {
            // dont display gross ATAK UUID's, just other ones from
            // systems that just put a callsign as the unique indentifer
            MapView mv = MapView.getMapView();
            String uid = item.getUID();
            if (!isUUID(uid))
                title = uid;
            else if (mv != null)
                title = mv.getContext().getString(R.string.untitled_item);
        }
        return title;
    }

    /**
     * Determine if a string supplied is a valid UUID.
     * @param string the unknown string
     * @return true if it is a UUID.
     */
    public static boolean isUUID(String string) {
        try {
            UUID u = UUID.fromString(string);
            return (u != null);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Allow for application registration of decoders.
     */
    public interface BitmapDecoder {
        /**
         * Provided a URI, allow for an application level decoding of the uri into a bitmap.
         * @param uriStr the uri for the bitmap, it should be assumed that this bitmap is 
         * can be actively recycled by the caller. 
         * @return Bitmap a valid bitmap based on the uriStr or null if unable to process the uri.
         */
        Bitmap decodeUri(String uriStr);
    }

    private final static Map<String, BitmapDecoder> decoders = new ConcurrentHashMap<>();

    /**
     * Finds self marker even if it has not been placed on the map.
     * 
     * @param mapView the mapView to use
     * @return Self marker if it exists, otherwise null.
     */
    static public Marker findSelfUnplaced(MapView mapView) {
        return mapView.getSelfMarker();
    }

    /**
     * Finds self marker but only if it has been placed on the map
     * 
     * @param mapView the mapView to use
     * @return Self marker if it exists, otherwise null.
     */
    static public Marker findSelf(MapView mapView) {
        if (mapView.getSelfMarker().getGroup() != null)
            return mapView.getSelfMarker();
        else
            return null;
    }

    /**
     * Method that will return true if the map item provided is the self marker.
     *
     * @param mapView the mapView used
     * @param item the item passed in
     * @return true if the item is the self marker.
     */
    public static boolean isSelf(MapView mapView, PointMapItem item) {
        if (!(item instanceof Marker))
            return false;

        return isSelf(mapView, item.getUID());
    }

    /**
     * Method that will return true if the map item provided is the self marker.
     *
     * @param mapView the mapView used
     * @param uid the uid of the item to check
     * @return true if the item is the self marker.
     */
    public static boolean isSelf(MapView mapView, String uid) {
        Marker self = findSelfUnplaced(mapView);
        if (self == null)
            return false;

        return FileSystemUtils.isEquals(self.getUID(), uid);
    }

    /**
     * Scales map to fit item and, if includeself is true, the self marker, within a space width by
     * height around the focus point.
     * 
     * @param _mapView the mapview to use when scaling to fit around the item.
     * @param item the item to use
     * @param includeSelf if the scaleToFit should also make use of the self marker
     * @param widthPad the padding to be used for the width around the best fit.
     * @param heightPad the padding to be used for the height of the bet fit.
     */
    public static void scaleToFit(MapView _mapView, MapItem item,
            boolean includeSelf, int widthPad,
            int heightPad) {
        if (includeSelf) {
            scaleToFit(_mapView, new MapItem[] {
                    item, findSelf(_mapView)
            }, widthPad, heightPad);
        } else {
            scaleToFit(_mapView, new MapItem[] {
                    item
            }, widthPad, heightPad);
        }
    }

    /**
     * Scales map to fit items within a space width by height around the focus point.
     *
     * @param _mapView the mapview to use when scaling to fit around the items.
     * @param items the array of items to provide a best fit for
     * @param widthPad the padding to be used for the width around the best fit.
     * @param heightPad the padding to be used for the height of the bet fit.
     */
    public static void scaleToFit(MapView _mapView, MapItem[] items,
            int widthPad,
            int heightPad) {
        ArrayList<GeoPoint> pointList = new ArrayList<>(items.length);
        for (MapItem i : items) {
            if (i instanceof Shape) {
                Collections.addAll(pointList,
                        ((Shape) i).getPoints());
            } else if (i instanceof PointMapItem) {
                pointList.add(((PointMapItem) i).getPoint());
            } else if (i instanceof AnchoredMapItem) {
                pointList.add(((AnchoredMapItem) i).getAnchorItem().getPoint());
            }
        }
        scaleToFit(_mapView,
                pointList.toArray(new GeoPoint[0]),
                widthPad,
                heightPad);
    }

    /**
     * Scales map to fit items within a space width by height around the focus point.
     *
     * @param _mapView the mapview to use when scaling to fit around the items.
     * @param target the target point to include
     * @param friendly the friendly point to include
     */
    public static void scaleToFit(MapView _mapView, PointMapItem target,
            PointMapItem friendly) {
        //not quite sure why this method does not just make use of one of the other myriad of methods.
        if (_mapView != null) {
            AtakMapController ctrl = _mapView.getMapController();

            GeoPoint[] points = new GeoPoint[2];
            points[0] = target.getPoint();
            points[1] = friendly.getPoint();

            GeoPoint center = GeoCalculations.centerOfExtremes(points, 0,
                    points.length);
            ctrl.panTo(center, true);

            PointF tvp = _mapView.forward(points[0]);
            PointF fvp = _mapView.forward(points[1]);

            double viewWidth = 2 * _mapView.getWidth() / (double) 3;
            double padding = viewWidth / 4;
            viewWidth -= padding;
            double viewHeight = _mapView.getHeight() - padding;
            double modelWidth = Math.abs(tvp.x - fvp.x);
            double modelHeight = Math.abs(tvp.y - fvp.y);

            double zoomFactor = viewWidth / modelWidth;
            if (zoomFactor * modelHeight > viewHeight) {
                zoomFactor = viewHeight / modelHeight;
            }

            if (center != null) {
                PointF p = _mapView.forward(center);
                ctrl.zoomBy(zoomFactor, p.x, p.y, true);
            }
        }
    }

    /**
     * Scales map to fit item and, if includeself is true, the self marker, within a space width by
     * height around the focus point.
     *
     * @param mv the mapview to use when scaling to fit.
     * @param points the  array of geopoints to use
     * @param widthPad the padding to be used for the width around the best fit.
     * @param heightPad the padding to be used for the height of the bet fit.
     * @return true if the scale process was successful.
     */
    public static boolean scaleToFit(MapView mv, GeoPoint[] points,
            int widthPad, int heightPad) {
        return scaleToFit(mv, points, widthPad, heightPad, false);
    }

    public static boolean scaleToFit(MapView mv, GeoPoint[] points,
            int widthPad, int heightPad, boolean terrainAdj) {
        if (points.length == 0) {
            Log.d(TAG, "Points are empty");
            return false;
        }

        // get the extremes in pixel-size so we can zoom to that size
        int[] e = GeoCalculations.findExtremes(points, 0, points.length,
                mv.isContinuousScrollEnabled());
        if (e.length < 4) {
            Log.d(TAG,
                    "cannot find the extremes for: " + Arrays.toString(points));
            return false;
        }

        GeoPoint north = points[e[1]], south = points[e[3]];
        GeoPoint west = points[e[0]], east = points[e[2]];
        double minLat = south.getLatitude(), maxLat = north.getLatitude();
        double minLng = west.getLongitude(), maxLng = east.getLongitude();
        boolean crossesIDL = mv.isContinuousScrollEnabled()
                && Math.abs(maxLng - minLng) > 180 && maxLng <= 180
                && minLng >= -180;
        if (crossesIDL)
            minLng = west.getLongitude() - 360;
        GeoBounds bounds = new GeoBounds(minLat, minLng, maxLat, maxLng);
        return scaleToFit(mv, bounds, widthPad, heightPad, terrainAdj);
    }

    public static boolean scaleToFit(MapView mv, GeoBounds bounds,
            int widthPad, int heightPad) {
        return scaleToFit(mv, bounds, widthPad, heightPad, false);
    }

    public static boolean scaleToFit(MapView mv, GeoBounds bounds,
            int widthPad, int heightPad, boolean terrainAdj) {
        double minLat = bounds.getSouth();
        double maxLat = bounds.getNorth();
        double minLng = bounds.getWest();
        double maxLng = bounds.getEast();
        GeoPoint center = bounds.getCenter(null);
        double cLat = center.getLatitude();
        double cLng = center.getLongitude();

        // IDL corrections
        if (bounds.crossesIDL()) {
            if (cLng < 0) {
                minLng = bounds.getEast() - 360;
                maxLng = bounds.getWest();
            } else {
                minLng = bounds.getEast();
                maxLng = bounds.getWest() + 360;
            }
        }
        double unwrap = 0;
        if (minLng < -180 || maxLng > 180)
            unwrap = mv.getLongitude() > 0 ? 360 : -360;

        final AtakMapController ctrl = mv.getMapController();
        final GeoPoint panTo = new GeoPoint(
                cLat, GeoCalculations.wrapLongitude(cLng),
                ElevationManager.getElevation(
                        cLat, GeoCalculations.wrapLongitude(cLng), null));

        // XXX - this looks really wrong, preserving legacy for now
        if (mv.getProjection().getSpatialReferenceID() == 4326) {
            PointF cp = mv.forward(panTo, unwrap);
            ctrl.panBy(cp.x - ctrl.getFocusX(), cp.y - ctrl.getFocusY(), true);
        } else {
            // pan to the point -- whether or not the location is wrapped or
            // unwrapped, repositioning the map at the unwrapped location
            // should preserve any necessary IDL wrapping by the view
            ctrl.panTo(panTo, true);
        }

        double spread = 0.0;
        if (Double.compare(minLat, maxLat) == 0
                || Double.compare(minLng, maxLng) == 0)
            spread = 0.0001d;

        if (minLng < -180 || maxLng > 180)
            unwrap = mv.getLongitude() > 0 ? 360 : -360;

        PointF northWest = mv.forward(new GeoPoint(maxLat + spread,
                minLng - spread), unwrap);
        PointF southEast = mv.forward(new GeoPoint(minLat - spread,
                maxLng + spread), unwrap);

        double padding = widthPad / 4d;
        widthPad -= padding;
        heightPad -= padding;

        double modelWidth = Math.abs(northWest.x - southEast.x);
        double modelHeight = Math.abs(northWest.y - southEast.y);

        double zoomFactor = widthPad / modelWidth;
        if (zoomFactor * modelHeight > heightPad)
            zoomFactor = heightPad / modelHeight;

        // Clamp tilt to max at new zoom level
        double maxTilt = mv.getMaxMapTilt(mv.getMapScale() * zoomFactor);
        if (mv.getMapTilt() > maxTilt)
            ctrl.tiltTo(maxTilt, true);

        // Zoom to area
        ctrl.zoomBy(zoomFactor, ctrl.getFocusX(), ctrl.getFocusY(), true);

        return true;
    }

    /**
     * Scales map to fit item.
     *
     * @param item the item to use
     */
    public static void scaleToFit(final MapItem item) {
        MapView mv = MapView.getMapView();
        if (mv != null)
            scaleToFit(mv, item, false, mv.getWidth(), mv.getHeight());
    }

    /**
     * Given a location, scale to fit the bounds provided by the location
     * @param loc the location
     */
    public static void scaleToFit(ILocation loc) {
        MapView mv = MapView.getMapView();
        if (mv != null) {
            GeoBounds bounds = loc.getBounds(null);
            if (bounds == null)
                return;
            bounds = new GeoBounds(bounds);
            bounds.setWrap180(mv.isContinuousScrollEnabled());
            scaleToFit(mv, bounds, mv.getWidth(), mv.getHeight());
        }
    }

    /**
     * For a specific GeoPoint, derive the actual magnetic declination that should be used as 
     * double offset.
     * Note: Prior to August 7, 2017 Android devices utilized WMM 2010.  See the change id I36f26086b1e2f62f81974d81d90c9a9c315a3445 in the Google Android Source code     Peng Xu <pengxu@google.com> in response to bug 31216311.
     * https://android.googlesource.com/platform/frameworks/base/+/63bf36a2117ca0338d7d4fdd3c5612a9e6091c04
     */
    public static double getCurrentMagneticVariation(GeoPoint point) {
        Date d = CoordinatedTime.currentDate();

        // Use the GMF around the user to find the declination, according to the Jump master 
        double altmsl = EGM96.getMSL(point);

        GeomagneticField gmf = new GeomagneticField(
                (float) point.getLatitude(),
                (float) point.getLongitude(),
                (float) altmsl, d.getTime());
        return gmf.getDeclination();
    }

    /**
     * Obtains the grid convergence used for a specific line of bearing.
     * @param sPoint the starting point for the line of bearing.
     * @param ePoint the end point for the line of bearing.
     * @return the grid deviation (grid convergence) for the provided line of 
     * bearing.      The value is in angular degrees between [-180.0, 180.0)
     */
    public static double computeGridConvergence(GeoPoint sPoint,
            GeoPoint ePoint) {
        final double d = sPoint.distanceTo(ePoint);

        MutableMGRSPoint alignedend = new MutableMGRSPoint(
                sPoint.getLatitude(), sPoint.getLongitude());
        alignedend.offset(0, d);
        double[] enddd = alignedend.toLatLng(null);
        return sPoint
                .bearingTo(new GeoPoint(enddd[0], enddd[1]));

    }

    /**
     * Obtains the grid convergence used for a specific line of bearing.
     * @param sPoint the starting point for the line of bearing.
     * @param angle the angle of the line of bearing.
     * @param distance the length of the line of bearing. 
     * @return the grid deviation (grid convergence) for the provided line of 
     * bearing.   It is important to note that grid convergence cannot be acheived
     * unless the line of bearing has some length.  This will determine which grid 
     * line is used to converge against.   The value is in angular degrees between [-180.0, 180.0)
     */
    public static double computeGridConvergence(final GeoPoint sPoint,
            final double angle, final double distance) {
        final GeoPoint ePoint = DistanceCalculations.metersFromAtBearing(sPoint,
                distance, angle);
        return computeGridConvergence(sPoint, ePoint);
    }

    /**
     * Convert a bearing from Magnetic North to True North
     * 
     * @param point GeoPoint for the location of the Compass.
     * @param angle Bearing in degrees (Magnetic North)
     * @return Bearing in degrees (True North)
     */
    public static double convertFromMagneticToTrue(final GeoPoint point,
            final double angle) {
        Date d = CoordinatedTime.currentDate();
        // Use the GMF around the initial point to find the declination
        GeomagneticField gmf = new GeomagneticField(
                (float) point.getLatitude(),
                (float) point.getLongitude(), 0f, d.getTime());
        float dec = gmf.getDeclination();
        // Convert Azimuth into True North
        // Heading would be less than 0 or greater than 360!
        double truth = angle + (double) dec;
        if (truth >= 360d) {
            return truth - 360d;
        } else if (truth < 0d) {
            return truth + 360d;
        } else {
            return truth;
        }
    }

    /**
     * Convert a bearing from True North to Magnetic North
     * 
     * @param point GeoPoint for the location of the Compass.
     * @param angle Bearing in degrees (True North)
     * @return Bearing in degrees (Magnetic North)
     */
    public static double convertFromTrueToMagnetic(final GeoPoint point,
            final double angle) {
        Date d = CoordinatedTime.currentDate();
        // Use the GMF around the initial point to find the declination
        GeomagneticField gmf = new GeomagneticField(
                (float) point.getLatitude(),
                (float) point.getLongitude(), 0f, d.getTime());
        float dec = gmf.getDeclination();
        // Convert Azimuth into True North
        // Heading would be less than 0 or greater than 360!
        double mag = angle - (double) dec;
        if (mag >= 360d) {
            return mag - 360d;
        } else if (mag < 0d) {
            return mag + 360d;
        } else {
            return mag;
        }
    }

    /**
     * Check if a GeoPoint resides within a polygon represented by a GeoPoint Array The polygon does
     * not have to convex in order for this function to work, but the first and last GeoPoint must
     * be equivalent
     * 
     * @param point GeoPoint to be tested
     * @param polygon Array of GeoPoint that represents the polygon (does not have to be convex)
     * @return point resides in polygon?
     */
    public static boolean pointInsidePolygon(GeoPoint point,
            GeoPoint[] polygon) {
        Vector2D vPoint = geo2Vector(point);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = geo2Vector(polygon[i]);
        return Vector2D.polygonContainsPoint(vPoint, vPolygon);
    }

    public static boolean segmentInsidePolygon(GeoPoint point0,
            GeoPoint point1, GeoPoint[] polygon) {
        Vector2D vPoint0 = geo2Vector(point0);
        Vector2D vPoint1 = geo2Vector(point1);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = geo2Vector(polygon[i]);
        return Vector2D.segmentIntersectsOrContainedByPolygon(vPoint0, vPoint1,
                vPolygon);
    }

    public static boolean segmentArrayIntersectsOrContainedByPolygon(
            GeoPoint[] segments,
            GeoPoint[] polygon) {
        Vector2D[] vSegments = new Vector2D[segments.length];
        for (int i = 0; i < segments.length; i++)
            vSegments[i] = geo2Vector(segments[i]);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = geo2Vector(polygon[i]);
        return Vector2D.segmentArrayIntersectsOrContainedByPolygon(vSegments,
                vPolygon);
    }

    public static ArrayList<GeoPoint> segmentArrayIntersectionsWithPolygon(
            GeoPoint[] segments,
            GeoPoint[] polygon) {
        Vector2D[] vSegments = new Vector2D[segments.length];
        UTMPoint z = UTMPoint.fromGeoPoint(polygon[0]);
        String zone = z.getZoneDescriptor();
        for (int i = 0; i < segments.length; i++)
            vSegments[i] = geo2Vector(segments[i]);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = geo2Vector(polygon[i]);
        ArrayList<Vector2D> vIntersections = Vector2D
                .segmentArrayIntersectionsWithPolygon(
                        vSegments, vPolygon);
        ArrayList<GeoPoint> intersections = new ArrayList<>();
        for (Vector2D v : vIntersections) {
            intersections.add(vector2Geo(v, zone));
        }
        return intersections;
    }

    public static double computeDistanceOfRayToSegment(GeoPoint p,
            double azimuth, GeoPoint seg1,
            GeoPoint seg0) {
        UTMPoint uP = MutableUTMPoint.fromLatLng(Ellipsoid.WGS_84,
                p.getLatitude(),
                p.getLongitude(), null);
        Vector2D vP = geo2Vector(p);
        Vector2D vSeg1 = geo2Vector(seg1);
        Vector2D vSeg0 = geo2Vector(seg0);
        double adjusted;
        if (azimuth >= 0d && azimuth <= 180d) {
            adjusted = Math.toRadians(azimuth);
        } else {
            adjusted = Math.toRadians(azimuth - 360d);
        }
        Vector2D vDir = new Vector2D(Math.sin(adjusted), Math.cos(adjusted));
        Vector2D intersect = Vector2D.rayToSegmentIntersection(vP, vDir, vSeg1,
                vSeg0);
        if (intersect != null) {
            UTMPoint uIntersect = new UTMPoint(uP.getZoneDescriptor(),
                    intersect.x, intersect.y);
            double[] ll = uIntersect.toLatLng(null);
            GeoPoint gpIntersect = new GeoPoint(ll[0], ll[1]);
            double[] DA = DistanceCalculations.computeDirection(p, gpIntersect);
            return DA[0];
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Turn a geopoint into a vector2d.
     * @param p the geopoint to use
     * @return the vector2D representation.
     */
    public static Vector2D geo2Vector(GeoPoint p) {
        UTMPoint uP = UTMPoint.fromGeoPoint(p);
        double alt = !p.isAltitudeValid() ? 0d
                : p.getAltitude();
        return new Vector2D(uP.getEasting(), uP.getNorthing(), alt);
    }

    /**
     * Given a vector from the start of the zone and a zone - utilize UTM to generate a geopoint.
     * @param v the vector offset from the zone described by easting (x) and northing (y).
     * @param zone the zone descriptor
     * @return the geopoint.
     */
    public static GeoPoint vector2Geo(final Vector2D v, final String zone) {
        UTMPoint uP = new UTMPoint(zone, v.x, v.y);
        double[] ll = uP.toLatLng(null);
        return new GeoPoint(ll[0], ll[1], v.alt);
    }

    /**
     * Find the shape, if any, associated with this map item
     * @param mi Map item to search
     * @return The associated shape or the input map item if none found
     */
    public static MapItem findAssocShape(MapItem mi) {
        MapView mv = MapView.getMapView();
        if (mi == null || mv == null)
            return mi;

        // General > Associated > R&B Line > Bullseye
        String shapeUID = mi.getMetaString("shapeUID", null);
        if (FileSystemUtils.isEmpty(shapeUID))
            shapeUID = mi.getMetaString("assocSetUID", null);
        if (FileSystemUtils.isEmpty(shapeUID))
            shapeUID = mi.getMetaString("rabUUID", null);
        if (FileSystemUtils.isEmpty(shapeUID))
            shapeUID = mi.getMetaString("bullseyeUID", null);

        if (!FileSystemUtils.isEmpty(shapeUID)) {
            MapItem shape = mv.getRootGroup().deepFindUID(shapeUID);
            if (shape instanceof Shape)
                mi = shape;
        }
        return mi;
    }

    /**
     * Get the Icon URI based on marker icon or 'iconUri' meta string
     * Meant to be used when displaying the map item in UI (not in MapView)
     * @param mi Map item
     * @return Map item icon URI
     */
    public static String getIconUri(MapItem mi) {
        if (mi == null)
            return null;

        String uri = null;

        // Hack for bullseye items
        // TODO: Refactor bullseye to work like every other shape - no more marker-centrism
        if (mi.getType().equals(BullseyeTool.BULLSEYE_COT_TYPE))
            return getResourceUri(R.drawable.bullseye);

        // Look for Marker icon
        if (mi instanceof Marker) {
            Marker mkr = (Marker) mi;
            // Use label icon for label markers
            if (mkr.getMetaString(UserIcon.IconsetPath, "").equals(
                    SpotMapPalletFragment.LABEL_ONLY_ICONSETPATH))
                uri = getResourceUri(R.drawable.enter_location_label_icon);
            else if (mkr.getIcon() != null)
                uri = mkr.getIcon().getImageUri(mkr.getState());
        }

        // Last look for general icon URI
        if (FileSystemUtils.isEmpty(uri) && mi.hasMetaValue("iconUri"))
            uri = mi.getMetaString("iconUri", null);
        return uri;
    }

    /**
     * Convert resource ID to URI string
     * @param context Resource context
     * @param resId Resource ID
     * @return Resource URI
     */
    public static String getResourceUri(Context context, int resId) {
        return "android.resource://" + context.getPackageName() + "/" + resId;
    }

    /**
     * Provided a resource identifier, return the full resource URI describing 
     * the resource.
     */
    public static String getResourceUri(int resId) {
        MapView mv = MapView.getMapView();
        return mv != null ? getResourceUri(mv.getContext(), resId) : null;
    }

    /**
     * Get color from a map item icon
     * @param mi the map item to use for finding the currently set icon color.
     * @return the color, WHITE if no color is found.
     */
    public static int getIconColor(MapItem mi) {
        int color = Color.WHITE;
        if (mi != null) {
            if (mi.getType().equals(BullseyeTool.BULLSEYE_COT_TYPE)) {
                mi = findAssocShape(mi);
            }

            if (mi instanceof Marker)
                color = mi.getIconColor();
            else if (mi instanceof Shape)
                color = mi.getIconColor();
            else {
                try {
                    if (mi != null)
                        color = mi.getMetaInteger("color", Color.WHITE);
                } catch (Exception ignore) {
                }
            }
        }
        return (color & 0xFFFFFF) + 0xFF000000;
    }

    /**
     * Set the icon for the user.
     *  Use current marker's icon
     *  Use self icon if item is 'self'
     *  Use friendly default
     *
     * @param view the map view to use when searching or the user.
     * @param icon the view to populate with the found icon.
     * @param uid the uid of the user to be found.
     */
    public static void SetUserIcon(MapView view, ImageView icon,
            String uid) {
        SetUserIcon(view, icon, uid, R.drawable.friendly);
    }

    /**
     * Set the icon for the user
     *  Use current marker's icon
     *  Use self icon if item is 'self'
     *  Use specified default
     *
     * @param view the mapView to use to find the online users.
     * @param icon the view to set with the found default icon.
     * @param uid the uid of the user.
     * @param defaultResource if the user is not online or on the map, use the defaultResource to represent the user.
     */
    public static void SetUserIcon(final MapView view, final ImageView icon,
            String uid,
            int defaultResource) {
        MapItem item = view.getRootGroup().deepFindUID(uid);
        if (item instanceof PointMapItem) {
            PointMapItem pmi = (PointMapItem) item;
            ATAKUtilities.SetIcon(view.getContext(), icon, pmi);
        } else {
            icon.clearColorFilter();
            boolean bSelf = item != null && FileSystemUtils
                    .isEquals(item.getUID(), MapView.getDeviceUid());
            String iconUri = "android.resource://"
                    + view.getContext().getPackageName()
                    + "/"
                    + (bSelf ? R.drawable.ic_self : defaultResource);
            ATAKUtilities.SetIcon(view.getContext(), icon, iconUri,
                    Color.WHITE);
        }

        //TODO use ComMapServerListener/ServerContact to get last known icon? And color it grey if currently offline
    }

    public static void SetUserIcon(MapView context, ImageView icon,
            PointMapItem item) {
        SetUserIcon(context, icon, item, R.drawable.friendly);
    }

    /**
     * Set the icon for the user
     *  Use current marker's icon
     *  Use specified default
     *
     * @param context the context to use when setting the icon
     * @param icon the icon to use
     * @param item the item to set the icon for
     * @param defaultResource the default resource
     */
    public static void SetUserIcon(MapView context, ImageView icon,
            PointMapItem item, int defaultResource) {
        if (item != null) {
            ATAKUtilities.SetIcon(context.getContext(), icon, item);
        } else {
            icon.clearColorFilter();
            icon.setImageResource(defaultResource);
        }
    }

    /**
     * Set the icon based on the map item's icon drawable
     *
     * @param view  The ImageView to display the icon
     * @param mi    The Map Item to load the icon from
     */
    public static void setIcon(ImageView view, MapItem mi) {
        Drawable dr = mi.getIconDrawable();
        if (dr != null) {
            view.setImageDrawable(dr);
            view.setColorFilter(mi.getIconColor(), PorterDuff.Mode.MULTIPLY);
            view.setVisibility(View.VISIBLE);
        } else
            view.setVisibility(View.INVISIBLE);
    }

    /**
     * Set the icon based on the iconUriStr
     * Parses all the support uri formats
     *
     * @param context the context to use
     * @param icon  The ImageView to display the icon
     * @param mi    The Map Item to load the icon from
     */
    public static void SetIcon(final Context context, final ImageView view,
            final MapItem mi) {
        setIcon(view, mi);
    }

    /**
     * Set the icon based on the iconUriStr, must be run on the ui thread 
     * since it directly manipulates the passed in ImageView
     * Parses all the support uri formats
     *
     * @param context
     * @param icon  The ImageView to display the icon
     * @param iconUriStr    The URI to load the icon from
     * @param color The color to be applied to the icon
     * @return Bitmap if one was created
     */
    public static Bitmap SetIcon(final Context context, final ImageView icon,
            final String iconUriStr, final int color) {
        if (icon == null) {
            return null;
        }
        Bitmap ret = null;
        if (iconUriStr != null && !iconUriStr.trim().equals("")) {

            icon.setVisibility(View.VISIBLE);

            final Uri iconUri = Uri.parse(iconUriStr);

            String scheme = iconUri.getScheme();
            String path = getUriPath(iconUriStr);
            if (scheme == null)
                scheme = "";

            switch (scheme) {
                case "resource":
                    String properResourceUri = "android.resource://"
                            + context.getPackageName() + "/"
                            + path;
                    icon.setImageURI(Uri.parse(properResourceUri));
                    break;
                case "android.resource":
                    List<String> segs = iconUri.getPathSegments();
                    if (segs.size() == 0) {
                        // support for:       android.resource://id_number
                        String[] split = path.split("/");
                        String properAndroidResourceUri = "android.resource://"
                                + context.getPackageName() + "/"
                                + FileSystemUtils.sanitizeWithSpacesAndSlashes(
                                        split[split.length - 1]);
                        icon.setImageURI(Uri.parse(properAndroidResourceUri));
                    } else {
                        icon.setImageURI(iconUri);
                    }
                    break;
                case "base64":
                    String image = iconUriStr
                            .substring(
                                    (scheme != null) ? scheme.length() + 2 : 0);
                    if (image.startsWith("/")) {
                        image = image.substring(1);
                    }
                    try {
                        byte[] buf = Base64.decode(
                                image.getBytes(FileSystemUtils.UTF8_CHARSET),
                                Base64.URL_SAFE | Base64.NO_WRAP);
                        ret = BitmapFactory.decodeByteArray(buf, 0, buf.length);
                        icon.setImageBitmap(ret);
                    } catch (Exception e) {
                        icon.setVisibility(View.INVISIBLE);
                    }
                    break;
                default:
                    ret = getUriBitmap(iconUriStr);
                    if (ret != null)
                        setIconBitmap(icon, ret);
                    else
                        icon.setVisibility(View.INVISIBLE);
                    break;
            }
            icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        } else {
            //Log.w(TAG, "item: " + item.getTitle() + ", no iconUri");
            icon.setVisibility(View.INVISIBLE);
        }

        return ret;
    }

    /**
     * Generate a bitmap for this map item's icon
     * @param item Map item
     * @return Icon bitmap
     */
    public static Bitmap getIconBitmap(final MapItem item) {
        return getUriBitmap(getIconUri(item));
    }

    public static String getUriPath(String uriStr) {
        String path = uriStr;
        String scheme = Uri.parse(uriStr).getScheme();
        if (scheme != null && !scheme.isEmpty()) {
            path = uriStr.substring(scheme.length() + 1);
            // Takes care of cases where there's only one slash
            // i.e. asset:/icons/icon.png
            while (!path.isEmpty() && path.charAt(0) == '/')
                path = path.substring(1);
            // File requires a leading slash
            if (scheme.equals("file"))
                path = "/" + path;
        }
        return path;
    }

    /**
     * Register a bitmap decoder with the system that will be called when the uri scheme is encountered.
     * @param scheme the uri scheme supported.
     * @param uriBitmapDecoder the decoder used to process the uri.
     */
    public static void registerBitmapDecoder(String scheme,
            BitmapDecoder uriBitmapDecoder) {
        decoders.put(scheme, uriBitmapDecoder);
    }

    /**
     * Unregister the uriBitmapDecoder for a specified scheme.
     * @param scheme the uri scheme for the bitmap decoder to remove.
     */
    public static void unregisterBitmapDecoder(final String scheme) {
        decoders.remove(scheme);
    }

    /**
     * Decode URI to its matching bitmap
     * @param uriStr URI string to decode
     * @return Icon bitmap the bitmap that can be recycled by the user at any time.
     */
    public static Bitmap getUriBitmap(String uriStr) {
        return getUriBitmap(MapView.getMapView().getContext(), uriStr);
    }

    /**
     * Decode URI to its matching bitmap
     * @param uriStr    URI string to decode
     * @param ctx       The application context
     * @return Icon bitmap the bitmap that can be recycled by the user at any time.
     */
    public static Bitmap getUriBitmap(Context ctx, String uriStr) {
        if (uriStr == null)
            return null;

        Uri iconUri = Uri.parse(uriStr);
        String scheme = iconUri.getScheme();
        String path = getUriPath(uriStr);
        if (scheme == null)
            scheme = "";
        if (scheme.equals("asset") || scheme.equals("root")) {
            try {
                InputStream in = ctx.getAssets().open(path);
                Bitmap b = BitmapFactory.decodeStream(in);
                in.close();
                return b;
            } catch (IOException e) {
                return null;
            }
        } else if ((scheme.equals("resource")
                || scheme.equals("android.resource"))
                && path.contains("/")) {
            try {
                String packageName = path.substring(0, path.indexOf("/"));
                Context resCtx = ctx;
                try {
                    resCtx = ctx.createPackageContext(packageName, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Failed to find context for icon: "
                            + packageName);
                }
                int resId = Integer.parseInt(path
                        .substring(path.lastIndexOf("/") + 1));
                return BitmapFactory.decodeResource(
                        resCtx.getResources(), resId);
            } catch (NumberFormatException nfe) {
                Log.d(TAG,
                        "unable to extract ID from the provided Uri: "
                                + uriStr);
                return null;
            }
        } else if (scheme.equals("sqlite")) {
            return UserIcon.GetIconBitmap(uriStr, ctx);
        } else if (scheme.equals("http")) {
            return IconsMapComponent.getInstance().getRemoteIcon(uriStr);
        } else if (uriStr.startsWith(BASE64)
                || uriStr.startsWith(BASE64_PNG)) {
            // Base-64 image
            if (uriStr.startsWith(BASE64))
                uriStr = uriStr.substring(uriStr.indexOf(BASE64)
                        + BASE64.length());
            try {
                byte[] b64 = Base64.decode(uriStr, Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(b64, 0, b64.length);
            } catch (Exception e) {
                Log.d(TAG, "Failed to decode base-64 PNG", e);
            }
            return null;
        } else if (uriStr.startsWith(BASE64_2)) {
            uriStr = uriStr.substring(uriStr.indexOf(BASE64_2)
                    + BASE64_2.length());
            try {
                byte[] b64 = Base64.decode(
                        uriStr.getBytes(FileSystemUtils.UTF8_CHARSET),
                        Base64.URL_SAFE | Base64.NO_WRAP);
                return BitmapFactory.decodeByteArray(b64, 0, b64.length);
            } catch (Exception e) {
                Log.d(TAG, "Failed to decode base-64 regular icon", e);
            }

            return null;

        } else {
            final BitmapDecoder decoder = decoders.get(scheme);
            if (decoder != null) {
                try {
                    Bitmap r = decoder.decodeUri(uriStr);
                    // if the bitmap return is null, then continue on.
                    if (r != null)
                        return r;
                } catch (Exception e) {
                    Log.d(TAG, "error decoding: " + uriStr + " by: " + decoder);
                }
            }
            try (FileInputStream fis = IOProviderFactory
                    .getInputStream(new File(FileSystemUtils
                            .validityScan(path)))) {
                return BitmapFactory.decodeStream(fis);
            } catch (IOException ioe) {
                return null;
            }
        }
    }

    /**
     * Convert list of strings to a JSON array
     * @param strings the list of string
     * @return a list of strings into a JSON string array.
     */
    public static String toJSON(List<String> strings) {
        JSONArray a = new JSONArray();
        for (int i = 0; i < strings.size(); i++) {
            a.put(strings.get(i));
        }
        return a.toString();
    }

    /**
     * Convert JSON string into a list of strings
     * @param json the original encoded json document.
     * @return the list of Strings
     */
    public static List<String> fromJSON(String json) {
        List<String> strings = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(json)) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    String s = a.optString(i);
                    if (FileSystemUtils.isEmpty(s)) {
                        Log.w(TAG, "Unable to parse string");
                        continue;
                    }
                    strings.add(s);
                }
            } catch (JSONException e) {
                Log.w(TAG, "Unable to get string", e);
            }
        }
        return strings;
    }

    /**
     * Given a list of names, find the next non-duplicate name based on newName
     * @param newName New name - i.e. ItemName
     * @param names List of names - i.e. ItemName, ItemName (1), ItemName (2)
     * @param format Format where '#' is replaced by the dupe number - i.e. " (#)"
     * @return New non-duplicate name - i.e. ItemName (3)
     */
    public static String getNonDuplicateName(String newName,
            List<String> names, String format) {
        if (FileSystemUtils.isEmpty(format) || !format.contains("#")) {
            Log.w(TAG,
                    "getNonDuplicateName invalid format: missing \"#\" in string");
            format = " (#)";
        }
        String fStart = format.substring(0, format.indexOf("#"));
        String fEnd = format.substring(format.lastIndexOf("#") + 1);
        int maxNum = 1;
        SparseArray<String> dupes = new SparseArray<>();
        for (String name : names) {
            if (name.startsWith(newName)) {
                if (name.equals(newName))
                    // This is the first duplicate
                    dupes.put(1, name);
                else {
                    String diff = name.substring(newName.length());
                    if (diff.startsWith(fStart) && diff.endsWith(fEnd)) {
                        try {
                            // Get the dupe number in parentheses
                            int v = Integer.parseInt(diff.substring(
                                    fStart.length(),
                                    diff.length() - fEnd.length()));
                            dupes.put(v, name);
                            maxNum = Math.max(maxNum, v);
                        } catch (Exception ignore) {
                            Log.d(TAG,
                                    "parsing error typing to get a non-duplicative number");
                        }
                    }
                }
            }
        }
        for (int i = 1; i <= maxNum + 1; i++) {
            if (dupes.get(i) == null) {
                if (i > 1)
                    // Only include number if not the first duplicate
                    newName += fStart + i + fEnd;
                break;
            }
        }
        return newName;
    }

    /**
     * Given a file and a imageview, set the image from the file as part of the icon and then if
     * sucessful, return the bitmap constructed from the file.
     * @param icon the imageview used for the bitmap from the file.
     * @param iconFile the file containing the image.
     * @return the bitmap if the file is loaded correctly.
     */
    public static Bitmap setIconFromFile(ImageView icon, File iconFile) {
        Bitmap bitmap = null;
        if (IOProviderFactory.exists(iconFile)) {
            try(InputStream is = IOProviderFactory.getInputStream(iconFile)) {
                BitmapFactory.decodeStream(is);
            } catch (IOException ioe) {
                return null;
            } catch (RuntimeException ignored) {
            }
        }
        setIconBitmap(icon, bitmap);
        return bitmap;
    }

    /**
     * Given a bitmap and a imageview, set the imageview from the bitmap.
     * @param icon the imageview used for the bitmap from the file.
     * @param bitmap the  the image.
     */
    private static void setIconBitmap(ImageView icon, Bitmap bitmap) {
        if (bitmap == null)
            icon.setVisibility(View.INVISIBLE);
        else
            icon.setImageBitmap(bitmap);
    }

    /**
     * Create a Bitmap from the specified drawable
     *
     * @param drawable the drawable to turn into a bitmap.
     * @return the bitmap representation of the drawable.
     */
    public static Bitmap getBitmap(Drawable drawable) {
        Bitmap result;
        if (drawable instanceof BitmapDrawable) {
            result = ((BitmapDrawable) drawable).getBitmap();
        } else {
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            // Some drawables have no intrinsic width - e.g. solid colours.
            if (width <= 0) {
                width = 1;
            }
            if (height <= 0) {
                height = 1;
            }

            result = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return result;
    }

    /**
     * Method to assist in the copying of text data to the clipboard supporting cut and paste.
     * @param label the label defining the data
     * @param text the actual data to be copied
     * @param bToast if true, displays a toast.
     * @since 3.8
     */
    public static void copyClipboard(final String label, final String text,
            final boolean bToast) {
        Context c = MapView.getMapView().getContext();
        ClipboardManager clipboard = (ClipboardManager) c
                .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null)
            clipboard.setPrimaryClip(clip);

        if (bToast)
            Toast.makeText(c, R.string.copied_to_clipboard_generic,
                    Toast.LENGTH_SHORT)
                    .show();
    }

    // Content type -> drawable icon based on import resolver
    private static final Map<String, Drawable> contentIcons = new HashMap<>();

    /**
     * Get an icon for a given file content type
     * @param contentType Content type
     * @return Icon or null if N/A
     */
    public static Drawable getContentIcon(String contentType) {
        if (contentType == null)
            return null;

        // Import resolver icons
        // To avoid calling this method excessively, cache results where possible
        synchronized (contentIcons) {
            // Cached icon
            Drawable icon = contentIcons.get(contentType);
            if (icon != null)
                return icon;

            Collection<ImportResolver> resolvers = null;

            if (contentIcons.isEmpty()) {
                // Build icons for default resolvers
                MapView mv = MapView.getMapView();
                if (mv != null)
                    resolvers = ImportFilesTask.GetSorters(mv.getContext(),
                            false, false, false, false);
            } else {
                // New resolver icons that may have been registered since
                ImportExportMapComponent iemc = ImportExportMapComponent
                        .getInstance();
                if (iemc != null)
                    resolvers = iemc.getImporterResolvers();
            }

            if (resolvers == null || resolvers.isEmpty())
                return null;

            // Read icons from resolvers
            for (ImportResolver res : resolvers) {
                icon = res.getIcon();
                if (icon == null)
                    continue;
                Pair<String, String> p = res.getContentMIME();
                if (p == null || p.first == null)
                    continue;
                contentIcons.put(p.first, icon);
            }

            // Last attempt to read from cached icons
            return contentIcons.get(contentType);
        }
    }

    /**
     * Get an icon for a file
     * Note: If the file does not exist it's recommended to use
     * {@link #getContentIcon(String)} provided you have the content type
     * @param f File
     * @return File icon or null if N/A
     */
    public static Drawable getFileIcon(File f) {
        if (f == null)
            return null;

        MapView mv = MapView.getMapView();
        Context ctx = mv != null ? mv.getContext() : null;

        // Content handler icon
        URIContentHandler handler = URIContentManager.getInstance()
                .getHandler(f);
        if (handler != null)
            return handler.getIcon();

        // Images
        if (ImageDropDownReceiver.ImageFileFilter.accept(null, f.getName()))
            return ctx != null ? ctx.getDrawable(R.drawable.camera) : null;

        ResourceFile.MIMEType mime = ResourceFile
                .getMIMETypeForFile(f.getName());
        return (mime != null && ctx != null)
                ? new BitmapDrawable(ctx.getResources(),
                        getUriBitmap(mime.ICON_URI))
                : null;
    }
}
