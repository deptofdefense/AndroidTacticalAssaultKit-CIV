package com.atakmap.map.hittest;

import android.graphics.PointF;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.RenderSurface;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.PointD;

import java.util.Collection;

/**
 * Contains hit-test query parameters such as the tapped screen point,
 * geo point, hit box, etc.
 */
public class HitTestQueryParameters {

    // The point on the screen the user tapped (pixels)
    public final PointF point = new PointF();

    // The hit threshold size (pixels)
    public float size;

    // The hit box surrounding the user's tap (pixels)
    // Depends on the value of 'size'
    // Note: This is in the GL coordinate space (0 = bottom of the screen)
    public final HitRect rect = new HitRect();

    // The geodetic point on the terrain that was hit
    // Note: This should only be used when hit-testing surface-rendered items
    public final GeoPoint geo = GeoPoint.createMutable();

    // The geodetic boundaries of the hit
    // Note: This should only be used when hit-testing surface-rendered items
    public final MutableGeoBounds bounds = new MutableGeoBounds();

    // Map item query limit
    public int limit;

    // Result subject filter (null to ignore)
    public HitTestResultFilter resultFilter;

    /**
     * Create hit test parameters based on coordinate and area
     *
     * @param surface Render surface
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param size Hit area size in pixels
     * @param origin Display origin of the coordinates
     *               Android coordinates use {@link MapRenderer2.DisplayOrigin#UpperLeft}
     *               OpenGL coordinates use {@link MapRenderer2.DisplayOrigin#Lowerleft}
     */
    public HitTestQueryParameters(RenderSurface surface, float x, float y, float size, MapRenderer2.DisplayOrigin origin) {

        // Need to flip to lower-left
        if (origin == MapRenderer2.DisplayOrigin.UpperLeft)
            y = surface.getHeight() - y;

        this.point.set(x, y);
        this.size = size;
        this.rect.set(x - size, y - size, x + size, y + size);
    }

    /**
     * Create hit test parameters based on coordinate (using area = 32 px)
     *
     * @param surface Render surface
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param origin Display origin of the coordinates
     *               Android coordinates use {@link MapRenderer2.DisplayOrigin#UpperLeft}
     *               OpenGL coordinates use {@link MapRenderer2.DisplayOrigin#Lowerleft}
     */
    public HitTestQueryParameters(RenderSurface surface, float x, float y, MapRenderer2.DisplayOrigin origin) {
        this(surface, x, y, 32f, origin);
    }

    public HitTestQueryParameters(HitTestQueryParameters other) {
        this.point.set(other.point);
        this.size = other.size;
        this.rect.set(other.rect);
        this.geo.set(other.geo);
        this.bounds.set(other.bounds);
        this.limit = other.limit;
    }

    /**
     * Initialize the geo point for this query using the GL map view
     * Note: This should only be called on the GL thread
     *
     * @param renderer Map renderer instance
     */
    public void initGeoPoint(MapRenderer3 renderer) {
        // Point has already been set
        if (this.geo.isValid())
            return;

        // Clear bounds before proceeding
        bounds.clear();

        GeoPoint focus = GeoPoint.createMutable();
        MapRenderer2.InverseResult result = renderer.inverse(new PointD(point), focus,
                MapRenderer2.InverseMode.RayCast, 0,
                MapRenderer2.DisplayOrigin.Lowerleft);
        if(result == MapRenderer2.InverseResult.None)
            return;

        // Recompute the geometry model such that the surface passes through
        // the focus point. This should allow us to trivially estimate surface
        // bounds without having to ray-cast each point in the grid against
        // terrain/surface meshes
        final MapSceneModel sm = renderer.getMapSceneModel(true, renderer.getDisplayOrigin());
        final GeometryModel estimateModel = focus.isAltitudeValid() ?
                CameraController.Util.createFocusAltitudeModel(sm, focus) :
                sm.earth;

        // create 3x3 grid around focus point, intersecting with the _model_
        // surface.
        GeoPoint[] pts = new GeoPoint[9];
        PointF pt = new PointF();
        GeoPoint geo = GeoPoint.createMutable();
        int idx = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                pt.x = point.x + (x * size);
                pt.y = point.y + (y * size);

                // Ignore invalid inverse returns
                if (sm.inverse(pt, geo, estimateModel) != null) {
                    pts[idx] = new GeoPoint(geo);

                    // Set center point
                    if (x == 0 && y == 0)
                        this.geo.set(geo);
                }
                idx++;
            }
        }

        int numPoints = 0;
        // adjust the model isect points by the translation between the focus
        // and the model focus
        if(pts[4] != null) {
            final double dlat = focus.getLatitude()-pts[4].getLatitude();
            final double dlng = focus.getLongitude()-pts[4].getLongitude();
            // Only used for surface hit-testing, so altitude should be ignored

            // compact `pts`
            for(int i = 0; i < pts.length; i++) {
                if(pts[i] == null)
                    continue;
                // apply the focus shift to the point
                pts[i].set(pts[i].getLatitude()+dlat, pts[i].getLongitude()+dlng);
                // compact if necessary
                if(i != numPoints) {
                    pts[numPoints] = pts[i];
                    pts[i] = null;
                }
                numPoints++;
            }
        } else {
            // use focus as sole point
            pts[0] = focus;
            numPoints = 1;
        }

        // No points had valid inverse returns
        if (numPoints == 0)
            return;

        // Update bounds
        bounds.set(pts, 0, numPoints, false);

        // Only used for surface hit-testing, so altitude should be ignored
        bounds.setMinAltitude(Double.NaN);
        bounds.setMaxAltitude(Double.NaN);
    }

    /**
     * Check if this query accepts results of the given class
     *
     * @param cl Result type
     * @return True if acceptable (or result filter isn't set)
     */
    public boolean acceptsResult(Class<?> cl) {
        return this.resultFilter == null || this.resultFilter.acceptClass(cl);
    }

    /**
     * Check if this query accepts results of the given subject instance
     *
     * @param subject Subject instance
     * @return True if acceptable (or result filter isn't set)
     */
    public boolean acceptsResult(Object subject) {
        return this.resultFilter == null || this.resultFilter.acceptSubject(subject);
    }

    /**
     * Check if this query has hit its limit based on a list
     *
     * @param list List to check
     * @return True if limit hit
     */
    public boolean hitLimit(Collection<?> list) {
        return list != null && this.limit > 0 && list.size() >= this.limit;
    }
}
