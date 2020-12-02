
package com.atakmap.android.track.maps;

import android.graphics.Color;
import android.graphics.PointF;
import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLCrumb;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi3;
import com.atakmap.android.maps.graphics.GLPolyline;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapBatchable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
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
            return 0;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> arg) {
            if (!(arg.second instanceof TrackPolyline))
                return null;
            return new GLTrackPolyline(arg.first, (TrackPolyline) arg.second);
        }
    };

    private final TrackPolyline _subject;

    private FloatBuffer _arrowBuffer;
    private long _arrowBufferPtr;

    public GLTrackPolyline(MapRenderer surface, TrackPolyline subject) {
        super(surface, subject);
        _subject = subject;
        _verts2Size = 3;
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (basicLineStyle != TrackPolyline.BASIC_LINE_STYLE_ARROWS) {
            super.draw(ortho, renderPass);
            return;
        }

        if (currentDraw != ortho.drawVersion)
            recompute = true;
        currentDraw = ortho.drawVersion;

        if (_needsUpdate) {
            _ensureVertBuffer();
            _needsUpdate = false;
        }

        // _verts2 may still be null if we have a zero point Polyline
        if (_verts2 == null)
            return;

        GLES20FixedPipeline.glPushMatrix();

        // software project the vertices
        _projectVerts(ortho);

        GLES20FixedPipeline.glVertexPointer(_verts2Size,
                GLES20FixedPipeline.GL_FLOAT, 0, _verts2);
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glVertexPointer(_verts2Size,
                GLES20FixedPipeline.GL_FLOAT, 0, _verts2);

        _projectArrows(ortho);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glPopMatrix();

        drawLabels(ortho);

        this.recompute = false;
    }

    // calculates and draws arrows
    private void _projectArrows(final GLMapView ortho) {
        if (_arrowBuffer == null) {
            _arrowBuffer = Unsafe.allocateDirect(8, FloatBuffer.class);
            _arrowBufferPtr = Unsafe.getBufferPointer(_arrowBuffer);
        }

        // XXX - ortho.drawTilt is always 0 at this point
        // Need to read from the map subject
        final double tiltSkew = Math.sin(Math.toRadians(ortho.drawTilt));
        final double scaleAdj = 1d + (tiltSkew * 2d);

        float radius = (float) ((_subject.getCrumbSize() * MapView.DENSITY)
                / scaleAdj);

        PointD p0 = new PointD(0d, 0d, 0d);
        PointD p1 = new PointD(0d, 0d, 0d);
        double angle;

        PointF t1 = new PointF();
        PointF t2 = new PointF();
        PointF t3 = new PointF();

        final int numArrows = this.numPoints - 1;
        // don't draw last arrow (-1)
        final float alpha = Color.alpha(strokeColor) / 255f;
        final float red = Color.red(strokeColor) / 255f;
        final float green = Color.green(strokeColor) / 255f;
        final float blue = Color.blue(strokeColor) / 255f;

        p1.x = Unsafe.getFloat(_verts2Ptr + 0);
        p1.y = Unsafe.getFloat(_verts2Ptr + 4);
        p1.z = (_verts2Size == 3) ? Unsafe.getFloat(_verts2Ptr + 8) : 0f;

        final float threshold = (float) (16 / scaleAdj);

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

            // triangle, with original point as center
            t1.set((float) p0.x, (float) (p0.y + radius)); // top
            t2.set((float) (p0.x + (radius / 2)),
                    (float) (p0.y - (radius / 2))); // botton left
            t3.set((float) (p0.x - (radius / 2)),
                    (float) (p0.y - (radius / 2))); // bottom right

            // add triangle points
            Unsafe.setFloat(_arrowBufferPtr + 0, t1.x);
            Unsafe.setFloat(_arrowBufferPtr + 4, t1.y);
            Unsafe.setFloat(_arrowBufferPtr + 8, t2.x);
            Unsafe.setFloat(_arrowBufferPtr + 12, t2.y);
            Unsafe.setFloat(_arrowBufferPtr + 16, t3.x);
            Unsafe.setFloat(_arrowBufferPtr + 20, t3.y);
            // close outline
            Unsafe.setFloat(_arrowBufferPtr + 24, t1.x);
            Unsafe.setFloat(_arrowBufferPtr + 28, t1.y);

            GLES20FixedPipeline.glPushMatrix();

            // move to origin, rotate, move back, then draw
            GLES20FixedPipeline.glTranslatef((float) p0.x, (float) p0.y, 0f);
            GLES20FixedPipeline.glRotatef((float) angle - 90, 0f, 0f, 1f);
            GLES20FixedPipeline.glTranslatef((float) -p0.x, (float) -p0.y, 0f);

            GLES20FixedPipeline.glTranslatef(0f, 0f, (float) p0.z);

            GLES20FixedPipeline.glVertexPointer(2,
                    GLES20FixedPipeline.GL_FLOAT, 0, _arrowBuffer);

            GLES20FixedPipeline.glColor4f(
                    red,
                    green,
                    blue,
                    alpha);
            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLES,
                    0, 3);

            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 1f);
            GLES20FixedPipeline.glLineWidth((float) (1f / scaleAdj));
            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP,
                    0, 4);

            GLES20FixedPipeline.glPopMatrix();
        }
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

        if (this.currentDraw != view.drawVersion)
            this.recompute = true;
        this.currentDraw = view.drawVersion;

        if (_needsUpdate) {
            _ensureVertBuffer();
            _needsUpdate = false;
        }

        _projectVerts(view);

        PointD p0 = new PointD(0d, 0d, 0d);
        PointD p1 = new PointD(0d, 0d, 0d);
        double angle;

        // don't draw last arrow (-1)
        final float alpha = Color.alpha(strokeColor) / 255f;
        final float red = Color.red(strokeColor) / 255f;
        final float green = Color.green(strokeColor) / 255f;
        final float blue = Color.blue(strokeColor) / 255f;

        p1.x = Unsafe.getFloat(_verts2Ptr + 0);
        p1.y = Unsafe.getFloat(_verts2Ptr + 4);
        p1.z = (_verts2Size == 3) ? Unsafe.getFloat(_verts2Ptr + 8) : 0f;

        final float threshold = 16;

        float lastX = -(threshold + 1);
        float lastY = -(threshold + 1);
        for (int i = 0; i < numArrows; i++) {
            // NOTE: points have already been projected to vertices

            p0.x = p1.x;
            p0.y = p1.y;

            p1.x = Unsafe.getFloat(_verts2Ptr + ((i + 1) * _verts2Size) * 4);
            p1.y = Unsafe
                    .getFloat(_verts2Ptr + ((i + 1) * _verts2Size + 1) * 4);
            p1.z = (_verts2Size == 3)
                    ? Unsafe.getFloat(
                            _verts2Ptr + ((i + 1) * _verts2Size + 2) * 4)
                    : 0f;

            if (p0.x < view._left || p0.x > view._right || p0.y < view._bottom
                    || p0.y > view._top)
                continue;

            if (MathUtils.distance(p0.x, p0.y, lastX, lastY) < threshold)
                continue;

            lastX = (float) p0.x;
            lastY = (float) p0.y;

            angle = Math.atan2(p1.y - p0.y, p1.x - p0.x) * div_180_pi;

            GLCrumb.batch(view, batch, (float) p0.x, (float) p0.y,
                    _subject.getCrumbSize() * MapView.DENSITY,
                    (float) (angle - 90 - view.drawRotation), 2f,
                    red, green, blue, alpha);

        }
        this.recompute = false;
    }
}
