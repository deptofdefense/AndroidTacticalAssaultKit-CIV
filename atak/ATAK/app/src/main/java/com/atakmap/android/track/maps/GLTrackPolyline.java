
package com.atakmap.android.track.maps;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi3;
import com.atakmap.android.maps.graphics.GLPolyline;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.opengl.GLMapBatchable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;

import java.nio.FloatBuffer;

/**
 * For drawing a crumb trail polyline
 * Note: Crumb trail polylines cannot be filled
 */
public class GLTrackPolyline extends GLPolyline implements GLMapBatchable {

    public static final String TAG = "GLTrackPolyline";

    public static final GLMapItemSpi3 SPI = new GLMapItemSpi3() {
        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> arg) {
            if (!(arg.second instanceof TrackPolyline))
                return null;
            return new GLTrackPolyline(arg.first, (TrackPolyline) arg.second);
        }
    };

    private final TrackPolyline _subject;

    private FloatBuffer _arrowSrcBuffer;
    private FloatBuffer _arrowDstBuffer;
    private long _arrowSrcBufferPtr, _arrowDstBufferPtr;
    private float _radius;

    private final static Matrix BATCH_XFORM = Matrix.getIdentity();

    public GLTrackPolyline(MapRenderer surface, TrackPolyline subject) {
        super(surface, subject);
        _subject = subject;
        _verts2Size = 3;
        altitudeMode = Feature.AltitudeMode.Absolute;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        Unsafe.free(_arrowSrcBuffer);
        Unsafe.free(_arrowDstBuffer);
    }

    @Override
    protected boolean uses2DPointBuffer() {
        return basicLineStyle != TrackPolyline.BASIC_LINE_STYLE_ARROWS
                && super.uses2DPointBuffer();
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (basicLineStyle != TrackPolyline.BASIC_LINE_STYLE_ARROWS) {
            super.draw(ortho, renderPass);
            return;
        }

        // Don't render anything on the surface
        if (!MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
            return;

        updateNadirClamp(ortho);

        if (currentDraw != ortho.currentPass.drawVersion)
            recompute = true;
        currentDraw = ortho.currentPass.drawVersion;

        if (_needsUpdate) {
            _ensureVertBuffer();
            _needsUpdate = false;
        }

        // _verts2 may still be null if we have a zero point Polyline
        if (_verts2 == null)
            return;

        // software project the vertices
        _projectVerts(ortho);

        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        _projectArrows(ortho, null);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        validateLabels(ortho);

        this.recompute = false;
    }

    // calculates and draws arrows
    private void _projectArrows(final GLMapView ortho, GLRenderBatch batch) {

        float radius = _subject.getCrumbSize() * MapView.DENSITY;

        // Update base arrow when radius changes
        if (_arrowSrcBuffer == null || _radius != radius) {
            // 3 (3 verts) * 3 (x and y) * 4 (floats) + closed
            if (_arrowSrcBuffer == null) {
                _arrowSrcBuffer = Unsafe.allocateDirect(48, FloatBuffer.class);
                _arrowSrcBufferPtr = Unsafe.getBufferPointer(_arrowSrcBuffer);
            }
            Unsafe.setFloats(_arrowSrcBufferPtr, 0, radius, 0);
            Unsafe.setFloats(_arrowSrcBufferPtr + 12, radius / 2, radius / -2,
                    0);
            Unsafe.setFloats(_arrowSrcBufferPtr + 24, radius / -2, radius / -2,
                    0);
            Unsafe.setFloats(_arrowSrcBufferPtr + 36, 0, radius, 0);
        }
        _radius = radius;

        if (_arrowDstBuffer == null) {
            _arrowDstBuffer = Unsafe.allocateDirect(48, FloatBuffer.class);
            _arrowDstBufferPtr = Unsafe.getBufferPointer(_arrowDstBuffer);
        }

        PointD p0 = new PointD(0d, 0d, 0d);
        PointD p1 = new PointD(0d, 0d, 0d);
        double angle;

        final int numArrows = this.numPoints - 1;
        // don't draw last arrow (-1)
        final float alpha = Color.alpha(strokeColor) / 255f;
        final float red = Color.red(strokeColor) / 255f;
        final float green = Color.green(strokeColor) / 255f;
        final float blue = Color.blue(strokeColor) / 255f;

        p1.x = Unsafe.getFloat(_verts2Ptr + 0);
        p1.y = Unsafe.getFloat(_verts2Ptr + 4);
        p1.z = (_verts2Size == 3) ? Unsafe.getFloat(_verts2Ptr + 8) : 0f;

        final float threshold = 16f;

        float lastX = -(threshold + 1);
        float lastY = -(threshold + 1);

        for (int i = 0; i < numArrows; i++) {
            // NOTE: points have already been projected to vertices

            p0.x = p1.x;
            p0.y = p1.y;
            p0.z = p1.z;

            p1.x = Unsafe.getFloat(_verts2Ptr + ((i + 1) * _verts2Size) * 4);
            p1.y = Unsafe
                    .getFloat(_verts2Ptr + ((i + 1) * _verts2Size + 1) * 4);
            p1.z = (_verts2Size == 3)
                    ? Unsafe.getFloat(
                            _verts2Ptr + ((i + 1) * _verts2Size + 2) * 4)
                    : 0f;

            if (p0.x < ortho._left || p0.x > ortho._right
                    || p0.y < ortho._bottom || p0.y > ortho._top)
                continue;

            if (MathUtils.distance(p0.x, p0.y, lastX, lastY) < threshold)
                continue;

            lastX = (float) p0.x;
            lastY = (float) p0.y;

            angle = Math.atan2(p1.y - p0.y, p1.x - p0.x) * div_180_pi;

            // transform the vertices
            BATCH_XFORM.setToTranslation(p0.x, p0.y, p0.z);
            BATCH_XFORM.rotate(Math.toRadians(ortho.currentScene.drawTilt), 1.0,
                    0.0, 0.0);
            BATCH_XFORM.rotate(Math.toRadians(angle - 90));

            long srcPtr = _arrowSrcBufferPtr;
            long dstPtr = _arrowDstBufferPtr;
            for (int v = 0; v < 4; v++) {
                ortho.scratch.pointD.x = Unsafe.getFloat(srcPtr);
                ortho.scratch.pointD.y = Unsafe.getFloat(srcPtr + 4);
                ortho.scratch.pointD.z = Unsafe.getFloat(srcPtr + 8);
                BATCH_XFORM.transform(ortho.scratch.pointD,
                        ortho.scratch.pointD);
                Unsafe.setFloats(dstPtr, (float) ortho.scratch.pointD.x,
                        (float) ortho.scratch.pointD.y, 7e-8f);
                srcPtr += 12;
                dstPtr += 12;
            }

            if (batch != null) {
                // crumb
                batch.addTriangleFan(_arrowDstBuffer, red, green, blue, alpha);

                // outline
                batch.addLineStrip(_arrowDstBuffer, 2f, 0f, 0f, 0f, alpha);
            } else {
                GLES20FixedPipeline.glVertexPointer(3,
                        GLES20FixedPipeline.GL_FLOAT,
                        0, _arrowDstBuffer);

                GLES20FixedPipeline.glColor4f(red, green, blue, alpha);
                GLES20FixedPipeline.glDrawArrays(
                        GLES20FixedPipeline.GL_TRIANGLE_FAN,
                        0, 3);

                GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 1f);
                GLES20FixedPipeline.glLineWidth(1f);
                GLES20FixedPipeline.glDrawArrays(
                        GLES20FixedPipeline.GL_LINE_STRIP,
                        0, 4);
            }
        }

        // Update impl for hit testing
        impl.setAltitudeMode(getAltitudeMode());
        impl.updateScreenVertices(ortho);
    }

    @Override
    public boolean isBatchable(GLMapView view) {
        if (basicLineStyle != TrackPolyline.BASIC_LINE_STYLE_ARROWS)
            return false;
        if (view.drawSrid == 4978)
            return false;
        if (_needsUpdate) {
            _ensureVertBuffer();
            _needsUpdate = false;
        }
        return _verts2 != null;
    }

    //@Override
    @Override
    public void batch(GLMapView view, GLRenderBatch batch) {
        if (!isBatchable(view)) {
            batch.end();
            try {
                this.draw(view, renderPass);
                return;
            } finally {
                batch.begin();
            }
        }

        final int numArrows = this.numPoints - 1;
        if (numArrows < 1)
            return;

        updateNadirClamp(view);

        if (this.currentDraw != view.currentPass.drawVersion)
            this.recompute = true;
        this.currentDraw = view.currentPass.drawVersion;

        if (_needsUpdate) {
            _ensureVertBuffer();
            _needsUpdate = false;
        }

        _projectVerts(view);
        _projectArrows(view, batch);

        this.recompute = false;
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        HitTestResult result = super.hitTestImpl(renderer, params);

        // Only "point" hit results are allowed when rendering arrows
        return result != null
                && (basicLineStyle != TrackPolyline.BASIC_LINE_STYLE_ARROWS
                        || result.type == HitTestResult.Type.POINT) ? result
                                : null;
    }
}
