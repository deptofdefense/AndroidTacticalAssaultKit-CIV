
package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.hittest.HitTestable;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.map.layer.control.LollipopControl;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.HitTestQueryParameters;

public abstract class GLBatchGeometry implements GLMapRenderable,
        GLMapBatchable2, HitTestable, LollipopControl, ClampToGroundControl {

    final MapRenderer renderContext;

    String name;
    final int zOrder;
    public long featureId;
    public int lod;
    public int subid;
    public long version;

    private boolean clampToGroundAtNadir;
    private boolean lollipopsVisible;
    private boolean nadirClamp;

    GLBatchGeometry(MapRenderer surface, int zOrder) {
        this.renderContext = surface;
        this.zOrder = zOrder;
        this.subid = 0;
        this.version = 0;
    }

    /** may be called on any thread */
    public void init(long featureId, String name) {
        this.featureId = featureId;
        this.name = name;
    }

    /*** may be called from any thread */
    public abstract void setStyle(Style style);

    /** may be called from any thread */
    public void setGeometry(final ByteBuffer blob, final int type, int lod) {
        this.lod = lod;
        if (this.renderContext.isRenderThread())
            this.setGeometryImpl(blob, type);
        else
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLBatchGeometry.this.setGeometryImpl(blob, type);
                }
            });
    }
    
    public void setGeometry(final Geometry geom, int lod) {
        this.lod = lod;
        if (this.renderContext.isRenderThread())
            this.setGeometryImpl(geom);
        else
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLBatchGeometry.this.setGeometryImpl(geom);
                }
            });
    }

    /** always invoked on GL thread */
    protected abstract void setGeometryImpl(ByteBuffer blob, int type);
    /** always invoked on GL thread */
    protected abstract void setGeometryImpl(Geometry geom);


    /**
     * Sets the altitude mode associated with this batch geometry.   Can be one of
     * ClampToGround, Relative, or Absolute.
     * @param altitudeMode the altitude mode.
     */
    public abstract void setAltitudeMode(Feature.AltitudeMode altitudeMode);

    /**
     * Sets the extrusion associated with this batch geometry.
     * @param value the extrude value.
     */
    public abstract void setExtrude(double value);

    @Override
    public HitTestResult hitTest(MapRenderer3 renderer, HitTestQueryParameters params) {
        return null;
    }

    /**
     * Soft release for labels
     *
     * @deprecated least intrusive change based on expectation of deprecation
     *             of Java renderers
     */
    public void releaseLabel() {}

    @Override
    public void setClampToGroundAtNadir(boolean v) {
        clampToGroundAtNadir = v;
    }

    @Override
    public boolean getClampToGroundAtNadir() {
        return clampToGroundAtNadir;
    }


    @Override
    public boolean getLollipopsVisible() {
        return lollipopsVisible;
    }

    @Override
    public void setLollipopsVisible(boolean v) {
        lollipopsVisible = v;
    }

    /**
     * Update the display state of the NADIR clamping based on the value of
     * {@link #getClampToGroundAtNadir()} and the current map tilt
     * @param view Map view
     * @return True if the value was changed
     */
    protected boolean updateNadirClamp(GLMapView view) {
        boolean nadirClamp = Double.compare(view.currentScene.drawTilt, 0) == 0
                && getClampToGroundAtNadir();
        if (this.nadirClamp != nadirClamp) {
            this.nadirClamp = nadirClamp;
            return true;
        }
        return false;
    }

    /**
     * Check if NADIR clamping is enabled
     * This is different from {@link #getClampToGroundAtNadir()} in that
     * it takes into account the latest map tilt
     * @return True if NADIR clamping is enabled
     */
    protected boolean isNadirClampEnabled() {
        return this.nadirClamp;
    }


}
