
package com.atakmap.android.hierarchy.filters;

import android.graphics.PointF;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.SimpleRectangle;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.opengl.GLAntiMeridianHelper;
import com.atakmap.math.MathUtils;

import java.util.Arrays;

/**
 * Filtering based on items visible within map view
 */
public class FOVFilter extends HierarchyListFilter {

    /**
     * Interface for defining filter acceptance behavior
     */
    public interface Filterable {
        boolean accept(MapState fov);
    }

    private static final String TAG = "FOVFilter";
    public static final String PREF = "fov_filter";

    private final MapState fov;

    public FOVFilter(MapView mapView) {
        this(new MapState(mapView));
    }

    public FOVFilter(MapState fov, HierarchyListItem.Sort sort) {
        super(sort);
        this.fov = fov;
    }

    public FOVFilter(MapState fov) {
        super(new HierarchyListItem.SortAlphabet());
        this.fov = fov;
    }

    public FOVFilter(MapState fov, HierarchyListFilter other) {
        this(fov, other.sort);
    }

    public FOVFilter(GeoBounds bounds) {
        this(new MapState(bounds));
    }

    public boolean accept(Filterable filterable) {
        return filterable != null && filterable.accept(this.fov);
    }

    @Override
    public boolean accept(HierarchyListItem item) {

        // Object has its own filter acceptance implementation
        if (item instanceof Filterable)
            return accept((Filterable) item);

        // Meta-group contacts (All Chat Rooms
        if (item instanceof Contact && ((Contact) item)
                .getExtras().getBoolean("metaGroup", false))
            return true;

        boolean defaultReturn = true;

        // Filter by map item first (potentially more complex)
        if (item instanceof MapItemUser) {
            MapItem mi = ((MapItemUser) item).getMapItem();
            if (mi != null)
                return accept(mi);
            defaultReturn = false;
        }

        // Filter by location / geo bounds
        if (item instanceof ILocation) {
            GeoPoint loc = GeoPoint.createMutable();
            MutableGeoBounds bounds = new MutableGeoBounds(0, 0, 0, 0);
            ILocation iloc = (ILocation) item;
            iloc.getPoint(loc);
            iloc.getBounds(bounds);
            return accept(bounds) || accept(loc);
        }

        return defaultReturn;
    }

    /**
     * Filter by marker or shape boundaries
     * @param item Point-based map item or shape
     * @return True if item passes the FOV filter
     */
    public boolean accept(MapItem item) {
        if (item == null)
            return false;

        // Object has its own filter acceptance implementation
        if (item instanceof Filterable)
            return accept((Filterable) item);

        // Markers - check single point
        else if (item instanceof PointMapItem)
            return accept(((PointMapItem) item).getPoint());

        // Shapes - check bounds, points, and lines
        else if (item instanceof Shape) {
            Shape shape = (Shape) item;
            GeoPointMetaData[] shapePoints = shape.getMetaDataPoints();
            if (shapePoints == null || shapePoints.length < 2)
                return false;

            // Only include first 4 points of rectangle (corners)
            boolean isRectangle = shape instanceof Rectangle
                    || shape instanceof SimpleRectangle;
            if (isRectangle && shapePoints.length > 4)
                shapePoints = Arrays.copyOf(shapePoints, 4);

            // First check points
            if (shape instanceof AnchoredMapItem) {
                PointMapItem anchor = ((AnchoredMapItem) shape).getAnchorItem();
                if (anchor != null && accept(anchor.getPoint()))
                    return true;
            }

            // Check if any of the points are within view
            for (GeoPointMetaData p : shapePoints) {
                if (fov.contains(p.get()))
                    return true;
            }

            // Then check if shape is even near the FOV
            boolean wrap180 = fov.mapView != null && fov.mapView
                    .isContinuousScrollEnabled();
            GeoBounds shapeBounds = GeoBounds.createFromPoints(
                    GeoPointMetaData.unwrap(shapePoints),
                    wrap180);
            if (!this.fov.intersects(shapeBounds))
                return false;

            boolean closed = isRectangle || shape instanceof DrawingCircle
                    || (shape.getStyle() & Polyline.STYLE_CLOSED_MASK) > 0
                    || shape.hasMetaValue("closed_line");
            boolean closePoint = closed && shapePoints.length >= 3 &&
                    !shapePoints[0].equals(shapePoints[shapePoints.length - 1]);

            // Convert shape points to vectors to aid in upcoming calculations
            double unwrap = wrap180 ? GLAntiMeridianHelper.getUnwrap(
                    fov.eastBoundUnwrapped, fov.westBoundUnwrapped,
                    shapeBounds) : 0;
            Vector2D[] shapeVec = new Vector2D[shapePoints.length
                    + (closePoint ? 1 : 0)];
            for (int i = 0; i < shapePoints.length; i++) {
                shapeVec[i] = geo2Vector(shapePoints[i].get(), unwrap);
                if (Vector2D.polygonContainsPoint(shapeVec[i],
                        this.fov.vBounds))
                    return true;
            }
            if (closePoint)
                shapeVec[shapePoints.length] = shapeVec[0];

            // Check intersections
            if (Vector2D.segmentArraysIntersect(shapeVec, this.fov.vBounds))
                return true;

            // Finally check if we're inside the shape
            return closed && Vector2D.polygonContainsPoint(this.fov.vCenter,
                    shapeVec);
        }
        return false;
    }

    /**
     * Filter by point within boundaries
     * @param gp Point of item
     * @return True if item passes the FOV filter
     */
    public boolean accept(GeoPoint gp) {
        return this.fov.contains(gp, true);
    }

    /**
     * Filter by intersecting boundaries
     * @param itemBounds Boundaries of item to filter
     * @return True if item passes the FOV filter
     */

    public boolean accept(GeoBounds itemBounds) {
        return this.fov.intersects(itemBounds);
    }

    public MapState getMapState() {
        return this.fov;
    }

    public static Vector2D geo2Vector(GeoPoint p, double unwrap) {
        double alt = !p.isAltitudeValid() ? 0d
                : p.getAltitude();
        double x = p.getLongitude();
        if (unwrap > 0 && x < 0 || unwrap < 0 && x > 0)
            x += unwrap;
        return new Vector2D(x, p.getLatitude(), alt);
    }

    /**
     * Turn a geopoint into a UTM-based vector2d.
     * @param p the geopoint to use
     * @return the vector2D representation.
     */
    public static Vector2D geo2Vector(GeoPoint p) {
        return geo2Vector(p, 0);
    }

    public static class MapState {
        public final MapView mapView;
        public final double northBound, eastBound, southBound, westBound;
        public final double eastBoundUnwrapped, westBoundUnwrapped;
        public final double drawMapResolution, rotation, tilt;
        public final int left, right, top, bottom;
        public final int width, height;
        public final GeoPoint upperLeft = GeoPoint.createMutable();
        public final GeoPoint upperRight = GeoPoint.createMutable();
        public final GeoPoint lowerRight = GeoPoint.createMutable();
        public final GeoPoint lowerLeft = GeoPoint.createMutable();
        public final GeoPoint center = GeoPoint.createMutable();
        public final Vector2D[] vBounds = new Vector2D[5];
        public final Vector2D vCenter;
        public final boolean crossesIDL;
        public final int hemisphere;
        public final double pointUnwrap;

        private final GeoBounds _bounds, _westIDLBounds, _eastIDLBounds;

        public MapState(MapView mv, GeoBounds bounds) {
            this.mapView = mv;
            if (bounds != null) {
                crossesIDL = bounds.crossesIDL();
                upperLeft.set(bounds.getNorth(), bounds.getWest());
                upperRight.set(bounds.getNorth(), bounds.getEast());
                lowerLeft.set(bounds.getSouth(), bounds.getWest());
                lowerRight.set(bounds.getSouth(), bounds.getEast());
                northBound = bounds.getNorth();
                southBound = bounds.getSouth();
                eastBound = bounds.getEast();
                westBound = bounds.getWest();
                bounds.getCenter(center);
                hemisphere = GeoCalculations.getHemisphere(center);
                double N = bounds.getNorth(), S = bounds.getSouth(),
                        E = bounds.getEast(), W = bounds.getWest();
                if (this.crossesIDL) {
                    if (hemisphere == GeoCalculations.HEMISPHERE_WEST) {
                        W = bounds.getEast() - 360;
                        E = bounds.getWest();
                    } else {
                        E = bounds.getWest() + 360;
                        W = bounds.getEast();
                    }
                }
                eastBoundUnwrapped = E;
                westBoundUnwrapped = W;
                if (mv != null) {

                    PointF ul = mv.forward(N, W), ur = mv.forward(N, E),
                            ll = mv.forward(S, W), lr = mv.forward(S, E);
                    width = (int) Math.max(ur.x - ul.x, lr.x - ll.x);
                    height = (int) Math.max(ul.y - ll.y, ur.y - lr.y);
                } else
                    width = height = 0;
            } else {
                width = mv.getWidth();
                height = mv.getHeight();
                // XXX - should probably use raycast (with nearest if no-isect), but
                //       will wait until implementation is refined
                MapSceneModel msm = mv.getSceneModel();
                msm.inverse(new PointF(0, 0), upperLeft);
                msm.inverse(new PointF(width, 0), upperRight);
                msm.inverse(new PointF(0, height), lowerLeft);
                msm.inverse(new PointF(width, height), lowerRight);
                msm.inverse(new PointF(width / 2f, height / 2f), center);
                northBound = MathUtils.max(upperLeft.getLatitude(),
                        upperRight.getLatitude(),
                        lowerRight.getLatitude(),
                        lowerLeft.getLatitude());
                southBound = MathUtils.min(upperLeft.getLatitude(),
                        upperRight.getLatitude(),
                        lowerRight.getLatitude(),
                        lowerLeft.getLatitude());
                eastBoundUnwrapped = MathUtils.max(upperLeft.getLongitude(),
                        upperRight.getLongitude(),
                        lowerRight.getLongitude(),
                        lowerLeft.getLongitude());
                westBoundUnwrapped = MathUtils.min(upperLeft.getLongitude(),
                        upperRight.getLongitude(),
                        lowerRight.getLongitude(),
                        lowerLeft.getLongitude());
                crossesIDL = mv.isContinuousScrollEnabled()
                        && (eastBoundUnwrapped > 180d
                                && westBoundUnwrapped < 180d
                                || westBoundUnwrapped < -180d
                                        && eastBoundUnwrapped > -180d);
                hemisphere = GeoCalculations.getHemisphere(center);
                eastBound = GeoCalculations.wrapLongitude(eastBoundUnwrapped);
                westBound = GeoCalculations.wrapLongitude(westBoundUnwrapped);
            }
            left = top = 0;
            right = width;
            bottom = height;

            if (this.crossesIDL) {
                _westIDLBounds = new GeoBounds(northBound,
                        Math.max(eastBound, westBound), southBound, 180d);
                _eastIDLBounds = new GeoBounds(northBound, -180d,
                        southBound, Math.min(eastBound, westBound));
                _bounds = null;
                pointUnwrap = this.hemisphere == GeoCalculations.HEMISPHERE_EAST
                        ? 360
                        : -360;
            } else {
                _bounds = new GeoBounds(northBound, westBound,
                        southBound, eastBound);
                _westIDLBounds = _eastIDLBounds = null;
                pointUnwrap = 0;
            }

            vCenter = geo2Vector(center);
            GeoPoint[] corners = new GeoPoint[] {
                    upperLeft, upperRight,
                    lowerRight, lowerLeft,
                    new GeoPoint(upperLeft, GeoPoint.Access.READ_WRITE)
            };
            for (int i = 0; i < vBounds.length; i++) {
                vBounds[i] = geo2Vector(corners[i], pointUnwrap);
                corners[i].set(corners[i].getLatitude(), GeoCalculations
                        .wrapLongitude(corners[i].getLongitude()));
            }
            if (mv != null) {
                // Rotation, tilt, etc.
                this.drawMapResolution = mv.getMapResolution();
                this.rotation = mv.getMapRotation();
                this.tilt = mv.getMapTilt();
            } else {
                this.drawMapResolution = 1;
                this.rotation = this.tilt = 0;
            }
        }

        public MapState(MapView mapView) {
            this(mapView, null);
        }

        public MapState(GeoBounds bounds) {
            this(MapView.getMapView(), bounds);
        }

        /**
         * Check if a point is contained in the bounds of the map view
         * @param gp Point
         * @param precise True to check the precise coordinates of the map view
         *                False to only check the AABB
         * @return True if contained
         */
        public boolean contains(GeoPoint gp, boolean precise) {
            if (precise) {
                // Precise check (actual bounds of the map view)
                return gp != null && gp.isValid() && contains(gp)
                        && Vector2D.polygonContainsPoint(
                                geo2Vector(gp, pointUnwrap), vBounds);
            } else {
                // Imprecise check (axis-aligned bounds of the map view)
                if (this.crossesIDL)
                    return _westIDLBounds.contains(gp)
                            || _eastIDLBounds.contains(gp);
                return _bounds.contains(gp);
            }
        }

        public boolean contains(GeoPoint gp) {
            return contains(gp, false);
        }

        public boolean intersects(GeoBounds gb) {
            if (this.crossesIDL)
                return _westIDLBounds.intersects(gb)
                        || _eastIDLBounds.intersects(gb);
            return _bounds.intersects(gb);
        }
    }
}
