package com.atakmap.map.hittest;

import android.graphics.PointF;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapRenderer2.DisplayOrigin;
import com.atakmap.map.MapRenderer2.InverseResult;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.RenderSurface;
import com.atakmap.math.PointD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
     *               Android coordinates use {@link DisplayOrigin#UpperLeft}
     *               OpenGL coordinates use {@link DisplayOrigin#Lowerleft}
     */
    public HitTestQueryParameters(RenderSurface surface, float x, float y, float size, DisplayOrigin origin) {

        // Need to flip to lower-left
        if (origin == DisplayOrigin.UpperLeft)
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
     *               Android coordinates use {@link DisplayOrigin#UpperLeft}
     *               OpenGL coordinates use {@link DisplayOrigin#Lowerleft}
     */
    public HitTestQueryParameters(RenderSurface surface, float x, float y, DisplayOrigin origin) {
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

        bounds.clear();

        List<GeoPoint> pts = new ArrayList<>();
        PointD pt = new PointD();
        GeoPoint geo = GeoPoint.createMutable();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                pt.x = point.x + (x * size);
                pt.y = point.y + (y * size);
                InverseResult result = renderer.inverse(pt, geo,
                        MapRenderer2.InverseMode.RayCast, 0,
                        DisplayOrigin.Lowerleft);

                // Ignore invalid inverse returns
                if (result != InverseResult.None) {
                    pts.add(new GeoPoint(geo));

                    // Set center point
                    if (x == 0 && y == 0)
                        this.geo.set(geo);
                }
            }
        }

        // No points had valid inverse returns
        if (pts.isEmpty())
            return;

        // Update bounds
        bounds.set(GeoBounds.createFromPoints(pts.toArray(new GeoPoint[0])));
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
