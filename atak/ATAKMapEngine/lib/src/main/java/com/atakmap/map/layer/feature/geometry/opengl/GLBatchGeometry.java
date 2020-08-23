
package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.opengl.GLMapBatchable;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.opengl.GLText;

public abstract class GLBatchGeometry implements GLMapRenderable,
                                                 GLMapBatchable2 {
    final MapRenderer renderContext;

    String name;
    final int zOrder;
    public long featureId;
    public int lod;
    public int subid;
    public long version;

    GLBatchGeometry(MapRenderer surface, int zOrder) {
        this.renderContext = surface;
        this.zOrder = zOrder;
        this.subid = 0;
        this.version = 0;
    }

    /** may be called on any thread */
    public void init(long featureId, String name) {
        this.featureId = featureId;
        this.name = GLText.localize(name);
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
}
