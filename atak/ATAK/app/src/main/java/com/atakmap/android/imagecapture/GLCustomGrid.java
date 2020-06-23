
package com.atakmap.android.imagecapture;

import android.graphics.Color;
import android.graphics.PointF;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLAbstractLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Render custom extends grid on map
 */
public class GLCustomGrid extends GLAbstractLayer
        implements CustomGrid.OnChangedListener {

    private final CustomGrid subject;
    private int _currentDraw = 0;
    private boolean _recompute = true, _drawLabels;
    private FloatBuffer _verts;
    private long _vertsPtr = 0;
    private String[] _labels;
    private String _res;
    private int _xLines, _yLines;
    private GLText _glText;
    private float _strokeRed, _strokeGreen, _strokeBlue, _strokeAlpha;

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if (!(object.second instanceof CustomGrid))
                return null;
            return new GLCustomGrid(object.first,
                    (CustomGrid) object.second);
        }

        @Override
        public int getPriority() {
            return 1;
        }
    };

    public GLCustomGrid(GLMapSurface surface, CustomGrid subject) {
        this(surface.getGLMapView(), subject);
    }

    protected GLCustomGrid(MapRenderer surface, CustomGrid subject) {
        super(surface, subject);
        this.subject = subject;
    }

    @Override
    protected void init() {
        super.init();
        this.subject.addOnChangedListener(this);
    }

    @Override
    public void release() {
        super.release();
        this.subject.removeOnChangedListener(this);
    }

    @Override
    protected void drawImpl(GLMapView view) {
        _recompute = _currentDraw != view.drawVersion;
        _currentDraw = view.drawVersion;

        projectVerts(view);
        if (_verts == null)
            return;

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glVertexPointer(2,
                GLES20FixedPipeline.GL_FLOAT, 0, _verts);
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glColor4f(
                _strokeRed, _strokeGreen, _strokeBlue, _strokeAlpha);
        GLES20FixedPipeline.glLineWidth(
                this.subject.getStrokeWeight());

        // Bounding box
        GLES20FixedPipeline.glDrawArrays(
                GLES20FixedPipeline.GL_LINE_STRIP, 0, 5);

        // Vertical lines
        GLES20FixedPipeline.glDrawArrays(
                GLES20FixedPipeline.GL_LINES, 5, _xLines * 2);

        // Horizontal lines
        GLES20FixedPipeline.glDrawArrays(
                GLES20FixedPipeline.GL_LINES, 5 + _xLines * 2,
                _yLines * 2);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glPopMatrix();

        if (!_drawLabels)
            return;

        // Draw labels
        if (_glText == null)
            _glText = GLText.getInstance(MapView.getDefaultTextFormat());

        float labelHeight = _glText.getStringHeight();
        for (int i = 0; i < _labels.length; i++) {
            int barHeight = view.getSurface().getMapView()
                    .getActionBarHeight();
            int labelIndex = this.subject.getLabelPositionIndex(i);
            float labelWidth = _glText.getStringWidth(_labels[i]);
            long ptr1 = _vertsPtr + labelIndex * 8;
            PointF p = new PointF(Unsafe.getFloat(ptr1),
                    Unsafe.getFloat(ptr1 + 4));
            boolean vertical = i < _xLines + 2;
            boolean inside = withinView(p, view);
            if (!inside) {
                long ptr2 = ptr1 + 8;
                if (labelIndex == 0 && i == 0)
                    ptr2 = ptr1 + 24;
                else if (labelIndex == 3)
                    ptr2 = ptr1 - 8;
                PointF[] l = new PointF[] {
                        new PointF(p.x, p.y),
                        new PointF(Unsafe.getFloat(ptr2),
                                Unsafe.getFloat(ptr2 + 4))
                };
                boolean endInside = withinView(l[1], view);
                if (endInside) {
                    p = new PointF(l[1].x, l[1].y);
                } else {
                    // Fix label position
                    double m = (l[1].y - l[0].y) / (l[1].x - l[0].x);
                    double b = l[0].y - (m * l[0].x);

                    float x = view.getRight(), y = view.getTop() - barHeight;
                    if (vertical)
                        p = new PointF(CanvasHelper.validate(
                                (float) ((y - b) / m), p.x), y);
                    else
                        p = new PointF(x, CanvasHelper.validate(
                                (float) (b + x * m), p.y));
                    CanvasHelper.clampToLine(p, l);
                }
                if (vertical) {
                    p.x += endInside ? -labelWidth / 2 : 4;
                    p.y += endInside ? -labelHeight + 4
                            : (-labelHeight / 2) - 4;
                } else {
                    p.x += endInside ? 8 : -labelWidth + 4;
                    p.y += endInside ? -(labelHeight / 2) + 4 : 4;
                }
            } else {
                if (vertical) {
                    p.x -= labelWidth / 2;
                    p.y += 4;
                } else {
                    p.x -= labelWidth;
                    p.y -= labelHeight / 2;
                }
            }

            if (withinView(p, view)) {
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(p.x, p.y, 0f);
                _glText.draw(_labels[i], _strokeRed, _strokeGreen,
                        _strokeBlue, _strokeAlpha);
                GLES20FixedPipeline.glPopMatrix();
            }

            // DEBUG: draw map resolution
            /*if (i == 0) {
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(xpos, ypos + labelHeight, 0f);
                _glText.draw(_res, _strokeRed, _strokeGreen,
                        _strokeBlue, _strokeAlpha);
                GLES20FixedPipeline.glPopMatrix();
            }*/
        }
    }

    private boolean withinView(PointF p, GLMapView view) {
        int barHeight = view.getSurface().getMapView()
                .getActionBarHeight();
        return p.x >= view.getLeft() && p.x < view.getRight()
                && p.y >= view.getBottom()
                && p.y <= view.getTop() - barHeight;
    }

    private void projectVerts(final GLMapView ortho) {
        if (_recompute) {
            DoubleBuffer points = this.subject.getPointBuffer();
            if (points != null) {
                GeoBounds bounds = this.subject.getBounds();

                // if bounds is null, then forward will fail, just treat this as if there 
                // is no point buffer.
                if (bounds == null) {
                    _verts = null;
                    return;
                }

                _xLines = this.subject.getVerticalLineCount();
                _yLines = this.subject.getHorizontalLineCount();

                // Build labels
                _drawLabels = this.subject.isDrawingLabels(ortho.drawLat,
                        ortho.drawMapResolution);
                _labels = this.subject.getLabels(true);
                if (_labels != null) {
                    for (int i = 0; i < _labels.length; i++) {
                        _labels[i] = GLText.localize(_labels[i]);
                    }
                }
                _res = String.format(LocaleUtil.getCurrent(), "%.2f",
                        ortho.drawMapResolution);

                ByteBuffer bb = com.atakmap.lang.Unsafe
                        .allocateDirect(points.capacity() * 4);
                bb.order(ByteOrder.nativeOrder());
                _verts = bb.asFloatBuffer();
                _vertsPtr = Unsafe.getBufferPointer(_verts);
                _verts.limit(points.limit());
                AbstractGLMapItem2.forward(ortho, points, _verts, bounds);

                int col = this.subject.getColor();
                _strokeRed = Color.red(col) / 255f;
                _strokeGreen = Color.green(col) / 255f;
                _strokeBlue = Color.blue(col) / 255f;
                _strokeAlpha = Color.alpha(col) / 255f;
            } else {
                _verts = null;
            }
        }
    }

    @Override
    public void onChanged(CustomGrid grid) {
        _currentDraw = 0;
    }

}
