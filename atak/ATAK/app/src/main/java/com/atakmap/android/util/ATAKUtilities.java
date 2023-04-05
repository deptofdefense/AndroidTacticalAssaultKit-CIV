
package com.atakmap.android.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
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
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.conversion.GeomagneticField;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
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
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.projection.MapProjectionDisplayModel;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.spatial.SpatialCalculator;

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

import gov.tak.api.annotation.ModifierApi;

/**
 * Home for utility functions that don't have a better home yet. Should consolidate functions like
 * findSelf that otherwise will be copy-pasted 20 times.
 */
public class ATAKUtilities {
    private final static char[] HEX_DIGITS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c',
            'd', 'e', 'f'
    };

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
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "@Nullable", "public"
    })
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

        // Need to compute the max altitude for a proper perspective fit
        double maxAlt = -Double.MAX_VALUE;
        ArrayList<GeoPoint> pointList = new ArrayList<>(items.length);
        for (MapItem i : items) {
            if (i == null)
                continue;

            double alt = Double.NaN;

            // Rectangles, circles, polylines, etc.
            if (i instanceof Shape) {
                GeoPoint[] points = ((Shape) i).getPoints();
                Collections.addAll(pointList, points);
                if (i instanceof EditablePolyline) {
                    // Max altitude has been pre-computed
                    alt = ((EditablePolyline) i).getMaxAltitude().get()
                            .getAltitude();
                } else {
                    // Compute max point altitude
                    alt = -Double.MAX_VALUE;
                    for (GeoPoint p : points) {
                        if (p.isAltitudeValid())
                            alt = Math.max(alt, p.getAltitude());
                    }
                }

                // Include shape center/anchor altitude
                if (i instanceof AnchoredMapItem) {
                    PointMapItem pmi = ((AnchoredMapItem) i).getAnchorItem();
                    if (pmi != null) {
                        GeoPoint p = pmi.getPoint();
                        if (p.isAltitudeValid())
                            alt = Math.max(alt, p.getAltitude());
                    }
                }
            }

            // Anchored map items that aren't shapes - redirect to anchor
            else if (i instanceof AnchoredMapItem) {
                MapItem anchor = ((AnchoredMapItem) i).getAnchorItem();
                if (anchor != null)
                    i = anchor;
            }

            // Markers
            if (i instanceof PointMapItem) {
                GeoPoint p = ((PointMapItem) i).getPoint();
                pointList.add(p);
                alt = p.getAltitude();
            }

            // Include the height in the max altitude
            double height = i.getHeight();
            if (!Double.isNaN(alt) && !Double.isNaN(height))
                alt += height;

            // Add to max altitude if valid
            if (GeoPoint.isAltitudeValid(alt))
                maxAlt = Math.max(maxAlt, alt);
        }
        scaleToFit(_mapView, pointList.toArray(new GeoPoint[0]), maxAlt,
                widthPad, heightPad);
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
            GeoPoint[] points = new GeoPoint[2];
            points[0] = target.getPoint();
            points[1] = friendly.getPoint();

            GeoPoint center = GeoCalculations.centerOfExtremes(points, 0,
                    points.length);
            if (center == null)
                return;

            MapSceneModel sm = _mapView.getRenderer3().getMapSceneModel(false,
                    MapRenderer2.DisplayOrigin.UpperLeft);
            sm.set(_mapView.getDisplayDpi(),
                    sm.width,
                    sm.height,
                    sm.mapProjection,
                    center,
                    sm.focusx,
                    sm.focusy,
                    sm.camera.azimuth,
                    0d,
                    sm.gsd,
                    true);

            PointF tvp = sm.forward(points[0], (PointF) null);
            PointF fvp = sm.forward(points[1], (PointF) null);

            double viewWidth = 2 * sm.width / (double) 3;
            double padding = viewWidth / 4;
            viewWidth -= padding;
            double viewHeight = sm.height - padding;
            double modelWidth = Math.abs(tvp.x - fvp.x);
            double modelHeight = Math.abs(tvp.y - fvp.y);

            double zoomFactor = viewWidth / modelWidth;
            if (zoomFactor * modelHeight > viewHeight) {
                zoomFactor = viewHeight / modelHeight;
            }

            _mapView.getMapController().dispatchOnPanRequested();
            _mapView.getRenderer3().lookAt(
                    center,
                    sm.gsd / zoomFactor,
                    sm.camera.azimuth,
                    90d + sm.camera.elevation,
                    true);
        }
    }

    /**
     * Scales map to fit item and, if includeself is true, the self marker, within a space width by
     * height around the focus point.
     *
     * @param mv the mapview to use when scaling to fit.
     * @param points the  array of geopoints to use
     * @param altitude Altitude to zoom to (perspective mode only; NaN to ignore)
     * @param widthPad the padding to be used for the width around the best fit.
     * @param heightPad the padding to be used for the height of the bet fit.
     * @return true if the scale process was successful.
     */
    public static boolean scaleToFit(MapView mv, GeoPoint[] points,
            double altitude, int widthPad, int heightPad) {
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

        // Get highest altitude so everything is in frame
        if (!GeoPoint.isAltitudeValid(altitude)) {
            double maxAlt = -Double.MAX_VALUE;
            for (GeoPoint p : points) {
                if (p.isAltitudeValid())
                    maxAlt = Math.max(maxAlt, p.getAltitude());
            }
        }

        return scaleToFit(mv, bounds, altitude, widthPad, heightPad);
    }

    public static boolean scaleToFit(MapView mv, GeoPoint[] points,
            int width, int height) {
        return scaleToFit(mv, points, Double.NaN, width, height);
    }

    public static boolean scaleToFit(MapView mv, GeoBounds bounds,
            int widthPad, int heightPad) {
        return scaleToFit(mv, bounds, Double.NaN, widthPad, heightPad);
    }

    public static boolean scaleToFit(MapView mv, GeoBounds bounds,
            double altitude, int widthPad, int heightPad) {
        double minLat = bounds.getSouth();
        double maxLat = bounds.getNorth();
        double minLng = bounds.getWest();
        double maxLng = bounds.getEast();
        GeoPoint center = bounds.getCenter(null);
        double cLat = center.getLatitude();
        double cLng = center.getLongitude();

        MapSceneModel scaleToModel = mv.getRenderer3().getMapSceneModel(false,
                MapRenderer2.DisplayOrigin.UpperLeft);

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

        if (!GeoPoint.isAltitudeValid(altitude))
            altitude = ElevationManager.getElevation(
                    cLat, GeoCalculations.wrapLongitude(cLng), null);
        final GeoPoint panTo = new GeoPoint(
                cLat, GeoCalculations.wrapLongitude(cLng),
                Double.isNaN(altitude) ? 0d : altitude);

        // pan to the point -- whether or not the location is wrapped or
        // unwrapped, repositioning the map at the unwrapped location
        // should preserve any necessary IDL wrapping by the view
        scaleToModel.set(
                scaleToModel.dpi,
                scaleToModel.width, scaleToModel.height,
                scaleToModel.mapProjection,
                panTo,
                scaleToModel.focusx, scaleToModel.focusy,
                scaleToModel.camera.azimuth,
                90d + scaleToModel.camera.elevation,
                scaleToModel.gsd,
                true);

        // the perspective camera has additional effective zoom based on the
        // AGL -- we will need to adjust the scale factor to account for this.
        double zoomFactor = 1d;
        if (!scaleToModel.camera.perspective) {
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

            zoomFactor = widthPad / modelWidth;
            if (zoomFactor * modelHeight > heightPad)
                zoomFactor = heightPad / modelHeight;
        } else {
            // NOTE: we cannot rely on screen space ratio to compute zoom with
            // the perspective camera on the surface plane due to perspective
            // skew. Screenspace ratios may only be used to compute zoom for
            // planes that are tangent to the camera line of sight. We will
            // first compute the minimum view required to contain the entire
            // bounding sphere. Then the bounding box will be computed in
            // screenspace. A plane will be constructed at the `z` of the
            // screenspace bounding box closest to the camera. World space
            // vectors on that plane corresponding to the screen positions will
            // be computed to determine final screenspace to world space
            // ratios.

            // compute bounding sphere, set a reasonable minimum limit
            final double radius = MathUtils.max(
                    GeoCalculations.distanceTo(
                            new GeoPoint(maxLat, (minLng + maxLng) / 2d),
                            new GeoPoint(maxLat, (minLng + maxLng) / 2d)) / 2d,
                    GeoCalculations.distanceTo(
                            new GeoPoint((minLat + maxLat) / 2d, minLng),
                            new GeoPoint((minLat + maxLat) / 2d, maxLng)) / 2d,
                    GeoCalculations.distanceTo(new GeoPoint(maxLat, minLng),
                            new GeoPoint(minLat, maxLng)) / 2d,
                    10d // minimum of 10m radius for zoom purposes
            );

            double padding = widthPad / 4d;
            widthPad -= padding;
            heightPad -= padding;

            // compute the dimension, in pixels, to capture the object radius
            final double pixelsAtD = Math.min(heightPad, widthPad);
            // compute the local GSD of the object
            final double gsdAtD = radius / (pixelsAtD / 2d);

            // compute camera range to centroid at target GSD. Adjust by altitude to account for AGL relative scaling.
            final double camRange = MapSceneModel.range(gsdAtD,
                    scaleToModel.camera.fov, scaleToModel.height)
                    + panTo.getAltitude();
            // compute GSD based on AGL adjusted range
            final double gsd = MapSceneModel.gsd(camRange,
                    scaleToModel.camera.fov, scaleToModel.height);

            // create a model such that the major-radius adheres to the minor dimension
            scaleToModel.set(
                    scaleToModel.dpi,
                    scaleToModel.width, scaleToModel.height,
                    scaleToModel.mapProjection,
                    scaleToModel.mapProjection
                            .inverse(scaleToModel.camera.target, null),
                    scaleToModel.focusx, scaleToModel.focusy,
                    scaleToModel.camera.azimuth,
                    90d + scaleToModel.camera.elevation,
                    gsd,
                    true);

            // XXX - there are some issues with very close zoom on small
            //       objects and `panTo` locations with negative altitude. The
            //       code below is observed to generally improve experience
            //       with very small objects (e.g. vehicle models) and also
            //       mitigates the aforementioned issue with negative
            //       altitudes. If the below condition is removed, issues
            //       related to negative altitudes will need to be resolved as
            //       well

            // for larger objects, try to closely fit the AABB against the
            // padded dimensions. small objects will zoom in too far.
            if (radius > 10d) {
                // since the AABB of the content should now be in view, obtain the
                // screenspace coordinates for the extents to do a final zoom
                double spread = 0.0;
                if (Double.compare(minLat, maxLat) == 0
                        || Double.compare(minLng, maxLng) == 0)
                    spread = 0.0001d;

                // shift longitude for IDL for flat projection
                if (scaleToModel.mapProjection
                        .getSpatialReferenceID() == 4326) {
                    if (Math.abs(panTo.getLongitude() - minLng) > 180d)
                        minLng += (panTo.getLongitude() < 0d) ? -360d : 360d;
                    if (Math.abs(panTo.getLongitude() - maxLng) > 180d)
                        maxLng += (panTo.getLongitude() < 0d) ? -360d : 360d;
                }

                PointD northWest = new PointD(0d, 0d, 0d);
                scaleToModel.forward(
                        new GeoPoint(maxLat + spread, minLng - spread,
                                ElevationManager.getElevation(maxLat + spread,
                                        minLng - spread, null)),
                        northWest);
                PointD northEast = new PointD(0d, 0d, 0d);
                scaleToModel.forward(
                        new GeoPoint(maxLat + spread, minLng + spread,
                                ElevationManager.getElevation(maxLat + spread,
                                        minLng + spread, null)),
                        northEast);
                PointD southEast = new PointD(0d, 0d, 0d);
                scaleToModel.forward(
                        new GeoPoint(minLat - spread, minLng + spread,
                                ElevationManager.getElevation(maxLat - spread,
                                        minLng + spread, null)),
                        southEast);
                PointD southWest = new PointD(0d, 0d, 0d);
                scaleToModel.forward(
                        new GeoPoint(minLat - spread, maxLng - spread,
                                ElevationManager.getElevation(minLat - spread,
                                        maxLng - spread, null)),
                        southWest);

                // establish the plane that the camera will zoom relative to
                final double zoomPlaneZ = MathUtils.min(northWest.z,
                        northEast.z, southEast.z, southWest.z);

                PointD ss_ul = new PointD(
                        MathUtils.min(northWest.x, northEast.x, southEast.x,
                                southWest.x),
                        MathUtils.min(northWest.y, northEast.y, southEast.y,
                                southWest.y),
                        zoomPlaneZ);
                PointD ss_ll = new PointD(
                        MathUtils.min(northWest.x, northEast.x, southEast.x,
                                southWest.x),
                        MathUtils.max(northWest.y, northEast.y, southEast.y,
                                southWest.y),
                        zoomPlaneZ);
                PointD ss_lr = new PointD(
                        MathUtils.max(northWest.x, northEast.x, southEast.x,
                                southWest.x),
                        MathUtils.max(northWest.y, northEast.y, southEast.y,
                                southWest.y),
                        zoomPlaneZ);

                // transform screenspace corners into WCS
                PointD wcs_ul = new PointD(0d, 0d, 0d);
                PointD wcs_ll = new PointD(0d, 0d, 0d);
                PointD wcs_lr = new PointD(0d, 0d, 0d);
                PointD wcs_c = new PointD(0d, 0d, 0d);

                scaleToModel.inverse.transform(ss_ul, wcs_ul);
                scaleToModel.inverse.transform(ss_ll, wcs_ll);
                scaleToModel.inverse.transform(ss_lr, wcs_lr);

                // compute target point on plane
                scaleToModel.inverse.transform(new PointD(scaleToModel.focusx,
                        scaleToModel.focusy, zoomPlaneZ), wcs_c);

                // compute vertical and horizontal axes on zoom-to plane in WCS
                final double verticalAxis = wcs_distance(wcs_ul, wcs_ll,
                        scaleToModel.displayModel);
                final double horizontalAxis = wcs_distance(wcs_lr, wcs_ll,
                        scaleToModel.displayModel);

                // compute vertical and horizontal GSDs on zoom plane
                final double gsdVertical = verticalAxis / heightPad;
                final double gsdHorizontal = horizontalAxis / widthPad;
                final double vpixelsHgsd = verticalAxis / gsdHorizontal;
                final double hpixelsVgsd = horizontalAxis / gsdVertical;

                // select GSD that will most closely correspond to padded width or
                // height
                final double gsdAtZoomPlane;
                if (vpixelsHgsd > heightPad)
                    gsdAtZoomPlane = gsdVertical;
                else if (hpixelsVgsd > widthPad)
                    gsdAtZoomPlane = gsdHorizontal;
                else
                    gsdAtZoomPlane = Math.min(gsdVertical, gsdHorizontal);

                // compute the camera range from the zoom plane
                final double rangeAtZoomPlane = MapSceneModel.range(
                        gsdAtZoomPlane, scaleToModel.camera.fov,
                        scaleToModel.height);

                // compute camera location
                double dx = (scaleToModel.camera.location.x - wcs_c.x)
                        * scaleToModel.displayModel.projectionXToNominalMeters;
                double dy = (scaleToModel.camera.location.y - wcs_c.y)
                        * scaleToModel.displayModel.projectionYToNominalMeters;
                double dz = (scaleToModel.camera.location.z - wcs_c.z)
                        * scaleToModel.displayModel.projectionZToNominalMeters;
                final double m = MathUtils.distance(dx, dy, dz, 0d, 0d, 0d);
                dx /= m;
                dy /= m;
                dz /= m;

                PointD camera = new PointD(
                        wcs_c.x + (dx * rangeAtZoomPlane)
                                / scaleToModel.displayModel.projectionXToNominalMeters,
                        wcs_c.y + (dy * rangeAtZoomPlane)
                                / scaleToModel.displayModel.projectionYToNominalMeters,
                        wcs_c.z + (dz * rangeAtZoomPlane)
                                / scaleToModel.displayModel.projectionZToNominalMeters);

                final double rangeToTarget = wcs_distance(camera,
                        scaleToModel.camera.target, scaleToModel.displayModel);

                scaleToModel.set(
                        scaleToModel.dpi,
                        scaleToModel.width, scaleToModel.height,
                        scaleToModel.mapProjection,
                        panTo,
                        scaleToModel.focusx, scaleToModel.focusy,
                        scaleToModel.camera.azimuth,
                        90d + scaleToModel.camera.elevation,
                        MapSceneModel.gsd(rangeToTarget + panTo.getAltitude(),
                                scaleToModel.camera.fov, scaleToModel.height),
                        true);
            }
            // zoom is already adjusted
        }

        // Clamp tilt to max at new zoom level
        double maxTilt = mv.getMaxMapTilt(mv.getMapScale() * zoomFactor);
        if (mv.getMapTilt() > maxTilt) {
            scaleToModel.set(
                    scaleToModel.dpi,
                    scaleToModel.width, scaleToModel.height,
                    scaleToModel.mapProjection,
                    scaleToModel.mapProjection
                            .inverse(scaleToModel.camera.target, null),
                    scaleToModel.focusx, scaleToModel.focusy,
                    scaleToModel.camera.azimuth,
                    maxTilt,
                    scaleToModel.gsd,
                    true);
        }

        // Zoom to area
        scaleToModel.set(
                scaleToModel.dpi,
                scaleToModel.width, scaleToModel.height,
                scaleToModel.mapProjection,
                scaleToModel.mapProjection.inverse(scaleToModel.camera.target,
                        null),
                scaleToModel.focusx, scaleToModel.focusy,
                scaleToModel.camera.azimuth,
                90d + scaleToModel.camera.elevation,
                scaleToModel.gsd / zoomFactor,
                true);

        mv.getRenderer3().lookAt(
                scaleToModel.mapProjection.inverse(scaleToModel.camera.target,
                        null),
                scaleToModel.gsd,
                scaleToModel.camera.azimuth,
                90d + scaleToModel.camera.elevation,
                MapRenderer3.CameraCollision.Ignore,
                true);

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
     * Computes the maximum GSD that should be specified when focusing on a
     * point. This ensures an appropriate minimum camera offset. In general,
     * this method is only recommended for points that are elevated above the
     * terrain surface.
     *
     * <P>NOTE: this is NOT a recommended focus distance.
     *
     * @param location          The focus location
     * @param localElevation    The local elevation at the point location
     * @param vfov              The vertical FOV of the camera
     * @param heightPx          The height of the display viewport, in pixels
     *
     * @return  The maximum recommended GSD that should be associated when
     *          requesting the camera to focus on the specified point, when
     *          taking into consideration the local elevation.
     */
    public static double getMaximumFocusResolution(GeoPoint location,
            double localElevation, double vfov, int heightPx) {
        final double alt = location.getAltitude();
        // if the point is at or below the terrain surface, any GSD selected
        // will be acceptable
        if (Double.isNaN(alt))
            return 0d;
        if (location.getAltitudeReference() == GeoPoint.AltitudeReference.AGL
                && alt <= 0d)
            return 0d;
        if (alt <= localElevation)
            return 0d;

        final double minOffset = 25d;
        return MapSceneModel.gsd(alt + minOffset, vfov, heightPx);
    }

    public static double getMaximumFocusResolution(GeoPoint location) {
        final MapSceneModel sm = MapView.getMapView().getRenderer3()
                .getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
        return getMaximumFocusResolution(location,
                ElevationManager.getElevation(location.getLatitude(),
                        location.getLongitude(), null),
                sm.camera.fov, sm.height);
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
        double hae = EGM96.getHAE(point);

        // Default to zero so we don't get a NaN result
        if (!GeoPoint.isAltitudeValid(hae))
            hae = 0;

        GeomagneticField gmf = new GeomagneticField(
                (float) point.getLatitude(),
                (float) point.getLongitude(),
                (float) hae, d.getTime());
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
        Vector2D vPoint = FOVFilter.geo2Vector(point);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = FOVFilter.geo2Vector(polygon[i]);
        return Vector2D.polygonContainsPoint(vPoint, vPolygon);
    }

    public static boolean segmentInsidePolygon(GeoPoint point0,
            GeoPoint point1, GeoPoint[] polygon) {
        Vector2D vPoint0 = FOVFilter.geo2Vector(point0);
        Vector2D vPoint1 = FOVFilter.geo2Vector(point1);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = FOVFilter.geo2Vector(polygon[i]);
        return Vector2D.segmentIntersectsOrContainedByPolygon(vPoint0, vPoint1,
                vPolygon);
    }

    public static boolean segmentArrayIntersectsOrContainedByPolygon(
            GeoPoint[] segments,
            GeoPoint[] polygon) {
        Vector2D[] vSegments = new Vector2D[segments.length];
        for (int i = 0; i < segments.length; i++)
            vSegments[i] = FOVFilter.geo2Vector(segments[i]);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = FOVFilter.geo2Vector(polygon[i]);
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
            vSegments[i] = FOVFilter.geo2Vector(segments[i]);
        Vector2D[] vPolygon = new Vector2D[polygon.length];
        for (int i = 0; i < polygon.length; i++)
            vPolygon[i] = FOVFilter.geo2Vector(polygon[i]);
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
        Vector2D vP = FOVFilter.geo2Vector(p);
        Vector2D vSeg1 = FOVFilter.geo2Vector(seg1);
        Vector2D vSeg0 = FOVFilter.geo2Vector(seg0);
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
            return GeoCalculations.distanceTo(p, gpIntersect);
        }
        return Double.POSITIVE_INFINITY;
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
     * @param view  The ImageView to display the icon
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

    /**
     * Convert a uriStr into a path keeping legacy behavior.   Does not account
     * for the hostname being included in the path.  We also do not support file uri
     * with two slashes "used to access files in a remote system."
     * @param uriStr the uri string { file, android, android.resource, base64 or
     *               no scheme at all}
     * @return the path of the uriStr
     */
    public static String getUriPath(String uriStr) {
        String path = uriStr;

        if (uriStr.startsWith("file://") && !uriStr.startsWith("file:///"))
            uriStr = uriStr.replace("file://", "file:///");
        else if (uriStr.startsWith("/"))
            uriStr = "file://" + uriStr;

        final Uri uri = Uri.parse(uriStr);
        final String scheme = uri.getScheme();

        if (scheme != null && !scheme.isEmpty()) {

            // properly decode file uri (with 3 slashes / not 2)
            if (scheme.equals("file")) {
                String retval = Uri.decode(uri.getPath());
                return retval;
            }

            // Old method
            path = uriStr.substring(scheme.length() + 1);
            // Takes care of cases where there's only one slash
            // i.e. asset:/icons/icon.png
            while (!path.isEmpty() && path.charAt(0) == '/')
                path = path.substring(1);
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
     * Turn a byte array into a hex string representation
     * @param arr the byte array
     * @return the corresponding string
     */
    public static String bytesToHex(byte[] arr) {
        StringBuilder retval = new StringBuilder();
        retval.ensureCapacity(arr.length * 2);
        for (int i = 0; i < arr.length; i++) {
            final int v = arr[i];
            retval.append(HEX_DIGITS[(v >> 4) & 0xF]);
            retval.append(HEX_DIGITS[(v & 0xF)]);
        }
        return retval.toString();
    }

    /**
     * Given a hex string, decode it into a byte array.
     * @param hex a hex string containing characters [0-F]
     * @return the corresponding byte array
     * @throws IllegalArgumentException if the string contains a non hex character
     */
    public static byte[] hexToBytes(String hex) {
        byte[] retval = new byte[(hex.length() + 1) / 2];
        for (int i = hex.length() - 1; i >= 0; i -= 2) {
            int v = 0;
            v |= decodeHex(hex.charAt(i));
            if (i > 0)
                v |= decodeHex(hex.charAt(i - 1)) << 4;
            retval[i / 2] = (byte) v;
        }
        return retval;
    }

    private static int decodeHex(char c) {
        if (c >= '0' && c <= '9') {
            return (int) (c - '0');
        } else if ((c & ~0x20) >= 'A' && (c & ~0x20) <= 'F') {
            return (int) ((char) (c & ~0x20) - 'A') + 10;
        } else {
            throw new IllegalArgumentException("Not a hex character: " + c);
        }
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
            try (InputStream is = IOProviderFactory.getInputStream(iconFile)) {
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

    /**
     * Returns the appropriate starting directory for file dialogs. If the "defaultDirectory" shared
     * preference exists, it will use the value. Otherwise, the "lastDirectory" shared preference
     * value will be used.
     *
     * @param sharedPrefContext The context of the shared preferences to use
     * @return the "defaultDirectory" shared preference, if exists, it will use the value.
     * Otherwise, the "lastDirectory" shared preference value will be used.
     */
    public static String getStartDirectory(Context sharedPrefContext) {
        SharedPreferences Prefs = PreferenceManager
                .getDefaultSharedPreferences(sharedPrefContext);
        String defaultDirectory = Prefs.getString("defaultDirectory", "");
        final String lastDirectory = Prefs.getString("lastDirectory",
                Environment.getExternalStorageDirectory().getPath());
        if (defaultDirectory.isEmpty()
                || !IOProviderFactory.exists(new File(defaultDirectory))) {
            return lastDirectory;
        } else {
            return defaultDirectory;
        }
    }

    private static double wcs_distance(PointD a, PointD b,
            MapProjectionDisplayModel displayModel) {
        return MathUtils.distance(
                a.x * displayModel.projectionXToNominalMeters,
                a.y * displayModel.projectionYToNominalMeters,
                a.z * displayModel.projectionZToNominalMeters,
                b.x * displayModel.projectionXToNominalMeters,
                b.y * displayModel.projectionYToNominalMeters,
                b.z * displayModel.projectionZToNominalMeters);
    }

    /**
     * Get the estimated meters per pixel at a given point on the screen
     * @param x X coordinate on the screen
     * @param y Y coordinate on the screen
     * @return Meters or {@link Double#NaN} if could not be calculated
     */
    public static double getMetersPerPixel(float x, float y) {
        MapView mapView = MapView.getMapView();
        if (mapView == null)
            return Double.NaN;
        GeoPoint p1 = mapView.inverse(x, y).get();
        GeoPoint p2 = mapView.inverse(x + 1, y + 1).get();
        return p1.isValid() && p2.isValid() ? p1.distanceTo(p2) : Double.NaN;
    }

    /**
     * Get the estimated meters per pixel at a given point on the screen
     * @param point Point on the screen (x, y)
     * @return Meters or {@link Double#NaN} if could not be calculated
     */
    public static double getMetersPerPixel(PointF point) {
        return getMetersPerPixel(point.x, point.y);
    }

    /**
     * Get the estimated meters per pixel at a given point on the map
     * @param point Point on the map
     * @return Meters or {@link Double#NaN} if could not be calculated
     */
    public static double getMetersPerPixel(GeoPoint point) {
        MapView mapView = MapView.getMapView();
        if (mapView == null)
            return Double.NaN;
        return getMetersPerPixel(mapView.forward(point));
    }

    /**
     * Get the estimated meters per pixel at the default focus point
     * @return Meters or {@link Double#NaN} if could not be calculated
     */
    public static double getMetersPerPixel() {
        MapView mapView = MapView.getMapView();
        if (mapView == null)
            return Double.NaN;
        MapSceneModel mdl = mapView.getSceneModel();
        return getMetersPerPixel(mdl.focusx, mdl.focusy);
    }

    /**
     * Helper method for {@link SpatialCalculator#simplify(Collection, double, boolean)}
     * Automatically calculates degree threshold using the current DPI
     * @param calc Spatial calcualtor
     * @param points List of points
     * @return List of simplified points
     */
    public static List<GeoPoint> simplifyPoints(SpatialCalculator calc,
            Collection<GeoPoint> points) {
        double thresh = 0;
        MapView mapView = MapView.getMapView();
        if (mapView != null) {
            float dp = mapView.getResources().getDisplayMetrics().density;
            MapSceneModel scene = mapView.getSceneModel();
            GeoPoint p1 = mapView.inverse(scene.focusx, scene.focusy).get();
            GeoPoint p2 = mapView.inverse(scene.focusx, scene.focusy + dp)
                    .get();
            double threshX = Math.abs(p1.getLongitude() - p2.getLongitude());
            double threshY = Math.abs(p1.getLatitude() - p2.getLatitude());
            thresh = Math.max(threshX, threshY);
        }
        Collection<GeoPoint> simplified = calc.simplify(points, thresh, true);
        return simplified instanceof List ? (List<GeoPoint>) simplified
                : new ArrayList<>(simplified);
    }
}
