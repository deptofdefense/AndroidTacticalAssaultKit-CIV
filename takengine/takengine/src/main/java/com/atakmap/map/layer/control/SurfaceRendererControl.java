package com.atakmap.map.layer.control;

import com.atakmap.annotations.IncubatingApi;
import com.atakmap.map.MapControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Mesh;

import java.util.Collection;

@IncubatingApi(since="4.3")
public interface SurfaceRendererControl extends MapControl {
    /**
     * Marks the visible region of the surface as dirty.
     */
    void markDirty();

    /**
     * Marks the specified region of the surface as dirty. If the region is not
     * part of the visible surface, it will be marked as invalid, but may not
     * be updated until it becomes visible.
     *
     * @param region    The dirty regoin
     * @param streaming If <code>true</code>, instructs the render to attempt
     *                  to refresh the region at frame rates.
     */
    void markDirty(Envelope region, boolean streaming);
    void enableDrawMode(Mesh.DrawMode mode);
    void disableDrawMode(Mesh.DrawMode mode);
    boolean isDrawModeEnabled(Mesh.DrawMode mode);
    void setColor(Mesh.DrawMode drawMode, int color, ColorControl2.Mode colorMode);
    int getColor(Mesh.DrawMode mode);
    ColorControl2.Mode getColorMode(Mesh.DrawMode mode);

    void setCameraCollisionRadius(double radius);
    double getCameraCollisionRadius();

    /**
     * <P>May only be invoked on GL thread.
     * @return
     */
    Collection<Envelope> getSurfaceBounds();

    /**
     * Sets the minimum refresh interval for the surface, in milliseconds. The
     * visible tiles on the surface will be refreshed if the specified amount
     * of time has elapsed since the last update, whether or not they have been
     * marked dirty.
     * @param millis    The minimum refresh interval, in milliseconds. If
     *                  less-then-or-equal to <code>0</code>, the refresh
     *                  interval is disabled.
     */
    void setMinimumRefreshInterval(long millis);
    long getMinimumRefreshInterval();
}
