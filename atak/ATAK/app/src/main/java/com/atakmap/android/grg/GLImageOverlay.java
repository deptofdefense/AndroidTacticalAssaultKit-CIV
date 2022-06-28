
package com.atakmap.android.grg;

import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi2;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLImageOverlay extends AbstractGLMapItem2 {

    public final static GLMapItemSpi2 SPI2 = new GLMapItemSpi2() {

        @Override
        public int getPriority() {
            //  ImageOverlay : Shape : MapItem
            return 1;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> arg) {
            final MapRenderer surface = arg.first;
            final MapItem item = arg.second;
            if (item instanceof ImageOverlay)
                return new GLImageOverlay(surface, (ImageOverlay) item);
            return null;
        }
    };

    private final static int NUM_OVERLAY_LEVELS = 5;
    private final static float MBB_STROKE_WIDTH = 4.0f;

    private boolean initialized;
    private GLMapRenderable impl;
    private double minMbbGsd;
    private double minRasterGsd;
    private FloatBuffer mbbVerts;

    private final ImageOverlay overlay;

    private GLImageOverlay(MapRenderer surface, ImageOverlay subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE);
        this.overlay = subject;

        this.initialized = false;
        this.impl = null;

        this.minMbbGsd = Double.NaN;
        this.minRasterGsd = Double.NaN;

        this.mbbVerts = null;
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!MathUtils.hasBits(renderPass, getRenderPass()))
            return;
        if (!this.initialized) {
            final DatasetDescriptor info = this.overlay.getLayerInfo();

            this.impl = GLMapLayerFactory.create3(ortho, info);

            double layerGsd = info.getMaxResolution(null);
            Envelope mbb = info.getMinimumBoundingBox();
            int width = (int) Math.ceil(GeoCalculations.distanceTo(
                    new GeoPoint((mbb.maxY + mbb.minY) / 2d, mbb.minX),
                    new GeoPoint((mbb.maxY + mbb.minY) / 2d, mbb.maxX))
                    / layerGsd);
            int height = (int) Math.ceil(GeoCalculations.distanceTo(
                    new GeoPoint(mbb.maxY, (mbb.maxX + mbb.minX) / 2d),
                    new GeoPoint(mbb.minY, (mbb.maxX + mbb.minX) / 2d))
                    / layerGsd);

            this.minMbbGsd = layerGsd / (100.0d / (width * height));
            this.minRasterGsd = Math.min(this.minMbbGsd, layerGsd
                    * NUM_OVERLAY_LEVELS);

            ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect(8 * 4);
            buf.order(ByteOrder.nativeOrder());

            this.mbbVerts = buf.asFloatBuffer();

            this.initialized = true;
        }

        final double mapGsd = ortho.currentPass.drawMapResolution;

        // XXX - consider icon/label when we're zoomed out sufficient far

        // zoom is sufficient to show coverage box
        if (mapGsd <= this.minMbbGsd) {
            final DatasetDescriptor info = this.overlay.getLayerInfo();

            Envelope mbb = info.getMinimumBoundingBox();

            ortho.scratch.geo.set(mbb.maxY, mbb.minX);
            ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);
            this.mbbVerts.put(0, ortho.scratch.pointF.x
                    - (2.0f + MBB_STROKE_WIDTH / 2.0f));
            this.mbbVerts.put(1, ortho.scratch.pointF.y
                    + (2.0f + MBB_STROKE_WIDTH / 2.0f));
            ortho.scratch.geo.set(mbb.maxY, mbb.maxX);
            ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);
            this.mbbVerts.put(2, ortho.scratch.pointF.x
                    + (2.0f + MBB_STROKE_WIDTH / 2.0f));
            this.mbbVerts.put(3, ortho.scratch.pointF.y
                    + (2.0f + MBB_STROKE_WIDTH / 2.0f));
            ortho.scratch.geo.set(mbb.minY, mbb.maxX);
            ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);
            this.mbbVerts.put(4, ortho.scratch.pointF.x
                    + (2.0f + MBB_STROKE_WIDTH / 2.0f));
            this.mbbVerts.put(5, ortho.scratch.pointF.y
                    - (2.0f + MBB_STROKE_WIDTH / 2.0f));
            ortho.scratch.geo.set(mbb.minY, mbb.minX);
            ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);
            this.mbbVerts.put(6, ortho.scratch.pointF.x
                    - (2.0f + MBB_STROKE_WIDTH / 2.0f));
            this.mbbVerts.put(7, ortho.scratch.pointF.y
                    - (2.0f + MBB_STROKE_WIDTH / 2.0f));

            GLES20FixedPipeline
                    .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(2,
                    GLES20FixedPipeline.GL_FLOAT, 0, this.mbbVerts);
            GLES20FixedPipeline.glColor4f(0.0f, 0.0f, 1.0f, 1.0f);
            GLES20FixedPipeline.glLineWidth(MBB_STROKE_WIDTH);
            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_LOOP,
                    0, 4);
            GLES20FixedPipeline
                    .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        }

        // zoom is sufficient to show raster data
        if (!this.overlay.getMetaBoolean("mbbOnly", false))
            if (mapGsd <= this.minRasterGsd && this.impl != null)
                this.impl.draw(ortho);

        if (!this.overlay.getMetaBoolean("mbbOnly", true))
            throw new OutOfMemoryError();
    }

    @Override
    public void release() {
        if (this.impl != null) {
            this.impl.release();
            this.impl = null;
        }

        this.minMbbGsd = Double.NaN;
        this.minRasterGsd = Double.NaN;

        this.mbbVerts = null;

        this.initialized = false;
    }

    // XXX - need to override for instantiation in GLMapGroup
    // -- consider making requirement non-declared method there
    // -- do we actually need to do anything here ???

    @Override
    public void startObserving() {
        final ImageOverlay imageOverlay = (ImageOverlay) this.subject;
        super.startObserving();

        this.initialized = false;

        imageOverlay.getBounds(this.bounds);
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        this.initialized = false;

        if (this.impl != null) {
            this.impl.release();
            this.impl = null;
        }

        this.mbbVerts = null;
    }
}
