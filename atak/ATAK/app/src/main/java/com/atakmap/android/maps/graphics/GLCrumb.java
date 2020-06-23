
package com.atakmap.android.maps.graphics;

import android.graphics.Color;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.track.crumb.Crumb;
import com.atakmap.android.track.crumb.Crumb.OnCrumbColorChangedListener;
import com.atakmap.android.track.crumb.Crumb.OnCrumbDrawLineToSurfaceChangedListener;
import com.atakmap.android.track.crumb.Crumb.OnCrumbDirectionChangedListener;
import com.atakmap.android.track.crumb.Crumb.OnCrumbSizeChangedListener;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLRenderBatch2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLCrumb extends GLPointMapItem2 implements
        OnCrumbColorChangedListener,
        OnCrumbDirectionChangedListener, OnCrumbSizeChangedListener,
        OnCrumbDrawLineToSurfaceChangedListener,
        GLMapBatchable2 {

    private final static Matrix BATCH_XFORM = Matrix.getIdentity();
    private final static ByteBuffer VERTS = com.atakmap.lang.Unsafe
            .allocateDirect(32)
            .order(ByteOrder.nativeOrder());
    private final static long VERTS_PTR = Unsafe.getBufferPointer(VERTS);
    private static float vertsRadius = -1;
    private final static FloatBuffer TRANSFORMED_VERTS = ByteBuffer
            .allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final static long TRANSFORMED_VERTS_PTR = Unsafe
            .getBufferPointer(TRANSFORMED_VERTS);

    private boolean drawLineToSurface = true;
    private int color;
    private float radius;
    private final Crumb sub;
    private double rot;
    private float xpos;
    private float ypos;
    private float zpos;

    private ByteBuffer _verts2;
    private FloatBuffer transformedVerts2;
    private static ByteBuffer tiltLineBuffer = null;
    private static long tiltLineBufferPtr = 0L;

    public GLCrumb(MapRenderer surface, Crumb subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES);
        sub = subject;

        this.color = subject.getColor();
        this.rot = subject.getDirection();
        radius = subject.getSize() * MapView.DENSITY;

        this.drawLineToSurface = subject.getDrawLineToSurface();

        initVerts2();
    }

    private void initVerts2() {
        _verts2 = com.atakmap.lang.Unsafe.allocateDirect(48);// 3 (3 verts) * 3 (x and y) * 4 (floats) + closed
        _verts2.order(ByteOrder.nativeOrder());
        FloatBuffer fb = _verts2.asFloatBuffer();

        fb.put(0);
        fb.put(radius);
        fb.put(0);

        fb.put(radius / 2);
        fb.put(-(radius / 2));
        fb.put(0);

        fb.put(-(radius / 2));
        fb.put(-(radius / 2));
        fb.put(0);

        fb.put(0);
        fb.put(radius);
        fb.put(0);

        fb.rewind();
    }

    @Override
    public void onCrumbColorChanged(Crumb crumb) {
        this.color = crumb.getColor();

    }

    @Override
    public void onCrumbDrawLineToSurfaceChanged(Crumb crumb) {
        this.drawLineToSurface = crumb.getDrawLineToSurface();

    }

    @Override
    public void onCrumbDirectionChanged(Crumb crumb) {
        this.rot = crumb.getDirection();
    }

    @Override
    public void onCrumbSizeChanged(Crumb crumb) {
        this.radius = crumb.getSize() * MapView.DENSITY;
        initVerts2();
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        forward(ortho, point, ortho.scratch.pointD);

        xpos = (float) ortho.scratch.pointD.x;
        ypos = (float) ortho.scratch.pointD.y;
        zpos = (float) ortho.scratch.pointD.z;

        if (drawLineToSurface && ortho.drawTilt > 0d) {
            ortho.scratch.geo.set(point);
            final double terrain = ortho.getTerrainMeshElevation(this.latitude,
                    this.longitude);
            double anchorEl = Math.min(terrain, 0d);
            ortho.scratch.geo.set(
                    (terrain + GLMapView.elevationOffset)
                            * ortho.elevationScaleFactor);

            ortho.scene.forward(ortho.scratch.geo, ortho.scratch.pointD);
            float xsurface = (float) ortho.scratch.pointD.x;
            float ysurface = (float) ortho.scratch.pointD.y;
            float zsurface = (float) ortho.scratch.pointD.z;

            if (tiltLineBuffer == null) {
                tiltLineBuffer = Unsafe.allocateDirect(24);
                tiltLineBuffer.order(ByteOrder.nativeOrder());
                tiltLineBufferPtr = Unsafe.getBufferPointer(tiltLineBuffer);
            }

            Unsafe.setFloats(tiltLineBufferPtr, xpos, ypos, zpos);
            Unsafe.setFloats(tiltLineBufferPtr + 12, xsurface, ysurface,
                    zsurface);

            GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f,
                    Color.alpha(color) / 255f);
            GLES20FixedPipeline.glLineWidth(2f);
            GLES20FixedPipeline
                    .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3,
                    GLES20FixedPipeline.GL_FLOAT, 0, tiltLineBuffer);
            GLES20FixedPipeline
                    .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, 2);
            GLES20FixedPipeline
                    .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        }

        if (this.transformedVerts2 == null) {
            ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect(_verts2
                    .capacity());
            buf.order(ByteOrder.nativeOrder());
            this.transformedVerts2 = buf.asFloatBuffer();
        }

        // transform the vertices
        BATCH_XFORM.setToTranslation(this.xpos, this.ypos, this.zpos);
        BATCH_XFORM.rotate(Math.toRadians(ortho.drawTilt), 1.0, 0.0, 0.0);
        BATCH_XFORM.rotate(-Math.toRadians(this.rot - ortho.drawRotation));

        this.transformedVerts2.clear();
        final int numVerts = 4;
        for (int i = 0; i < numVerts; i++) {
            ortho.scratch.pointD.x = _verts2.getFloat(i * 12);
            ortho.scratch.pointD.y = _verts2.getFloat(i * 12 + 4);
            ortho.scratch.pointD.z = _verts2.getFloat(i * 12 + 8);
            BATCH_XFORM.transform(ortho.scratch.pointD, ortho.scratch.pointD);
            this.transformedVerts2.put((float) ortho.scratch.pointD.x);
            this.transformedVerts2.put((float) ortho.scratch.pointD.y);
            this.transformedVerts2.put((float) 7e-8);
        }
        this.transformedVerts2.flip();

        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0,
                transformedVerts2);

        int alpha = Color.alpha(color);
        if (alpha < 255) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        }
        GLES20FixedPipeline.glColor4f(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                alpha / 255f);

        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN,
                0, 3);

        int color2 = Color.BLACK & 0xFFFFFF00;
        int alpha2 = Color.alpha(color);
        if (alpha2 < 255) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        }
        GLES20FixedPipeline.glColor4f(
                Color.red(color2) / 255f,
                Color.green(color2) / 255f,
                Color.blue(color2) / 255f,
                alpha2 / 255f);

        // crumbs should not be affected by relative scaling; unscale the line
        GLES20FixedPipeline.glLineWidth(2f / MapView.DENSITY);

        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0,
                4);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    @Override
    public void release() {
        super.release();

        this.transformedVerts2 = null;
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        final GeoPoint p = item.getPoint();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                point = p;

                final double N = point.getLatitude() + .0001; // about 10m
                final double S = point.getLatitude() - .0001;
                final double E = point.getLongitude() + .0001;
                final double W = point.getLongitude() - .0001;

                bounds.set(N, W, S, E);

                // IF YOU NEED TO, remove it and re-add it
                // if outside bounds of node
                dispatchOnBoundsChanged();
            }
        });
    }

    @Override
    public void startObserving() {
        Crumb crumb = (Crumb) this.subject;
        super.startObserving();
        crumb.addCrumbColorListener(this);
        crumb.addCrumbDirectionListener(this);
        crumb.addCrumbSizeListener(this);
        crumb.addCrumbDrawLineToSurfaceListener(this);
    }

    @Override
    public void stopObserving() {
        Crumb crumb = (Crumb) this.subject;
        super.stopObserving();
        crumb.removeCrumbColorListener(this);
        crumb.removeCrumbDirectionListener(this);
        crumb.removeCrumbSizeListener(this);
        crumb.removeCrumbDrawLineToSurfaceListener(this);
    }

    /**************************************************************************/
    // GL Map Batchable

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        forward(view, this.point, view.scratch.pointD);
        this.xpos = (float) view.scratch.pointD.x;
        this.ypos = (float) view.scratch.pointD.y;
        this.zpos = (float) view.scratch.pointD.z;

        // if tilted, draw a line segment from the center of the point into the
        // earth's surface
        if (drawLineToSurface && view.drawTilt > 0d) {
            view.scratch.geo.set(point);
            final double terrain = view.getTerrainMeshElevation(this.latitude,
                    this.longitude);
            //double anchorEl = Math.min(terrain, 0d);
            view.scratch.geo.set(
                    (terrain + GLMapView.elevationOffset)
                            * view.elevationScaleFactor);

            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            float xsurface = (float) view.scratch.pointD.x;
            float ysurface = (float) view.scratch.pointD.y;
            float zsurface = (float) view.scratch.pointD.z;

            batch.setLineWidth(2f);
            batch.batch(xpos, ypos, zpos,
                    xsurface, ysurface, zsurface,
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f,
                    Color.alpha(color) / 255f);
        }

        if (this.transformedVerts2 == null) {
            ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect(_verts2
                    .capacity());
            buf.order(ByteOrder.nativeOrder());
            this.transformedVerts2 = buf.asFloatBuffer();
        }

        // transform the vertices
        BATCH_XFORM.setToTranslation(this.xpos, this.ypos, this.zpos);
        BATCH_XFORM.rotate(Math.toRadians(view.drawTilt), 1.0, 0.0, 0.0);
        BATCH_XFORM.rotate(-Math.toRadians(this.rot - view.drawRotation));

        this.transformedVerts2.clear();
        final int numVerts = 4;
        for (int i = 0; i < numVerts; i++) {
            view.scratch.pointD.x = _verts2.getFloat(i * 12);
            view.scratch.pointD.y = _verts2.getFloat(i * 12 + 4);
            view.scratch.pointD.z = _verts2.getFloat(i * 12 + 8);
            BATCH_XFORM.transform(view.scratch.pointD, view.scratch.pointD);
            this.transformedVerts2.put((float) view.scratch.pointD.x);
            this.transformedVerts2.put((float) view.scratch.pointD.y);
            this.transformedVerts2.put((float) 7e-8);
        }
        this.transformedVerts2.flip();

        float alpha = Color.alpha(color) / 255f;

        // crumb
        batch.batch(-1,
                GLES20FixedPipeline.GL_TRIANGLE_FAN,
                3,
                0, this.transformedVerts2,
                0, null,
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                alpha);

        // outline
        batch.setLineWidth(2f / MapView.DENSITY);
        batch.batch(-1,
                GLES20FixedPipeline.GL_LINE_STRIP,
                3,
                0, this.transformedVerts2,
                0, null,
                0f,
                0f,
                0f,
                alpha);
    }

    /**
     * Batches an arrow crumb.
     * 
     * @param view      The map renderer
     * @param batch     The render batch
     * @param x         The x location of the crumb, in GL coordinates
     * @param y         The y location of the crumb, in GL coordinates
     * @param rotation  The rotation of the crumb, in degrees
     */
    public static void batch(GLMapView view, GLRenderBatch batch, float x,
            float y, float radius, float rotation, float strokeWidth, float r,
            float g, float b, float a) {
        if (vertsRadius != radius) {
            Unsafe.setFloat(VERTS_PTR, 0);
            Unsafe.setFloat(VERTS_PTR + 4, radius);

            Unsafe.setFloat(VERTS_PTR + 8, radius / 2);
            Unsafe.setFloat(VERTS_PTR + 12, -(radius / 2));

            Unsafe.setFloat(VERTS_PTR + 16, -(radius / 2));
            Unsafe.setFloat(VERTS_PTR + 20, -(radius / 2));

            Unsafe.setFloat(VERTS_PTR + 24, 0);
            Unsafe.setFloat(VERTS_PTR + 28, radius);

            vertsRadius = radius;
        }

        // transform the vertices
        BATCH_XFORM.setToTranslation(x, y);
        BATCH_XFORM.rotate(Math.toRadians(rotation + view.drawRotation));

        final int numVerts = 4;
        for (int i = 0; i < numVerts; i++) {
            view.scratch.pointD.x = Unsafe.getFloat(VERTS_PTR + (i * 8));
            view.scratch.pointD.y = Unsafe.getFloat(VERTS_PTR + (i * 8 + 4));
            BATCH_XFORM.transform(view.scratch.pointD, view.scratch.pointD);
            Unsafe.setFloat(TRANSFORMED_VERTS_PTR + ((i * 2) * 4),
                    (float) view.scratch.pointD.x);
            Unsafe.setFloat(TRANSFORMED_VERTS_PTR + ((i * 2 + 1) * 4),
                    (float) view.scratch.pointD.y);
        }

        // crumb
        batch.addTriangleFan(TRANSFORMED_VERTS,
                r,
                g,
                b,
                a);

        // outline
        batch.addLineStrip(TRANSFORMED_VERTS,
                strokeWidth,
                0f,
                0f,
                0f,
                a);
    }
}
