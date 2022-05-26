
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
import com.atakmap.map.layer.opengl.GLAbstractLayer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

/**
 * Render custom extends grid on map
 */
public class GLCustomGrid extends GLAbstractLayer2
        implements CustomGrid.OnChangedListener {

    private final CustomGrid subject;
    private int _currentDraw = 0;
    private boolean _recompute = true, _drawLabels, _visible;
    private FloatBuffer _gridVerts;
    private FloatBuffer _labelVerts;
    private String[] _labels;
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
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE
                | GLMapView.RENDER_PASS_SPRITES);
        this.subject = subject;
    }

    @Override
    protected void init() {
        super.init();
        this.subject.addOnChangedListener(this);
    }

    @Override
    public void release() {
        Unsafe.free(_gridVerts);
        Unsafe.free(_labelVerts);
        _gridVerts = _labelVerts = null;
        super.release();
        this.subject.removeOnChangedListener(this);
    }

    @Override
    protected void drawImpl(GLMapView view, int renderPass) {
        if (!_visible)
            return;

        _recompute = _currentDraw != view.currentPass.drawVersion;
        _currentDraw = view.currentPass.drawVersion;

        projectVerts(view);
        if (_gridVerts == null)
            return;

        if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE)) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glVertexPointer(2,
                    GLES20FixedPipeline.GL_FLOAT, 0, _gridVerts);
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
        }

        if (_drawLabels && MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES)) {

            // Draw labels
            if (_glText == null)
                _glText = GLText.getInstance(MapView.getDefaultTextFormat());

            for (int i = 0, lbl = 0; i < _labels.length; i++, lbl += 2) {
                if (_labels[i] != null) {
                    float x = _labelVerts.get(lbl);
                    float y = _labelVerts.get(lbl + 1);
                    GLES20FixedPipeline.glPushMatrix();
                    GLES20FixedPipeline.glTranslatef(x, y, 0);
                    _glText.draw(_labels[i], _strokeRed, _strokeGreen,
                            _strokeBlue, _strokeAlpha);
                    GLES20FixedPipeline.glPopMatrix();
                }
            }
        }
    }

    private boolean withinView(PointF p, GLMapView view) {
        return p.x >= view.getLeft() && p.x < view.getRight()
                && p.y >= view.getBottom()
                && p.y <= view.getTop();
    }

    private void projectVerts(final GLMapView view) {
        if (!_recompute)
            return;

        // Free previous buffers
        Unsafe.free(_gridVerts);
        Unsafe.free(_labelVerts);
        _gridVerts = _labelVerts = null;

        // Grid points buffer
        DoubleBuffer points = this.subject.getPointBuffer();

        // No points to draw
        if (points == null)
            return;

        GeoBounds bounds = this.subject.getBounds();

        // if bounds is null, then forward will fail, just treat this as if there
        // is no point buffer.
        if (bounds == null)
            return;

        _xLines = this.subject.getVerticalLineCount();
        _yLines = this.subject.getHorizontalLineCount();

        GLMapView.State state = view.currentPass;

        // Build labels
        _drawLabels = this.subject.isDrawingLabels(state.drawLat,
                state.drawMapResolution);
        _labels = this.subject.getLabels(true);
        if (_labels != null) {
            for (int i = 0; i < _labels.length; i++)
                _labels[i] = GLText.localize(_labels[i]);
        }

        // Population terrain elevation for grid vertices
        int capacity = points.capacity();
        int numPoints = 0;
        for (int i = 0; i < capacity; i += 3) {
            double lng = points.get(i);
            double lat = points.get(i + 1);
            double alt = view.getTerrainMeshElevation(lat, lng);
            points.put(i + 2, alt);
            numPoints++;
        }

        // Grid line vertices
        ByteBuffer bb = Unsafe.allocateDirect(numPoints * 2 * 4);
        bb.order(ByteOrder.nativeOrder());
        _gridVerts = bb.asFloatBuffer();
        AbstractGLMapItem2.forward(view, points, 3, _gridVerts, 2, bounds);

        // Label position vertices
        bb = Unsafe.allocateDirect(_labels.length * 2 * 4);
        bb.order(ByteOrder.nativeOrder());
        _labelVerts = bb.asFloatBuffer();

        if (_glText == null)
            _glText = GLText.getInstance(MapView.getDefaultTextFormat());

        // Calculate label positions
        PointF p = new PointF();
        float labelHeight = _glText.getStringHeight();
        for (int i = 0; i < _labels.length; i++) {
            int labelIndex = this.subject.getLabelPositionIndex(i);
            float labelWidth = _glText.getStringWidth(_labels[i]);
            int ind1 = labelIndex * 2;
            p.set(_gridVerts.get(ind1), _gridVerts.get(ind1 + 1));
            boolean vertical = i < _xLines + 2;
            boolean inside = withinView(p, view);
            if (!inside) {
                int ind2 = ind1 + 2;
                if (labelIndex == 0 && i == 0)
                    ind2 = ind1 + 6;
                else if (labelIndex == 3)
                    ind2 = ind1 - 2;
                PointF[] l = new PointF[] {
                        new PointF(p.x, p.y),
                        new PointF(_gridVerts.get(ind2),
                                _gridVerts.get(ind2 + 1))
                };
                boolean endInside = withinView(l[1], view);
                if (endInside)
                    p.set(l[1].x, l[1].y);
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

            _labelVerts.put(p.x);
            _labelVerts.put(p.y);

            if (!withinView(p, view))
                _labels[i] = null;
        }

        _labelVerts.clear();

        int col = this.subject.getColor();
        _strokeRed = Color.red(col) / 255f;
        _strokeGreen = Color.green(col) / 255f;
        _strokeBlue = Color.blue(col) / 255f;
        _strokeAlpha = Color.alpha(col) / 255f;
    }

    @Override
    public void onChanged(CustomGrid grid) {
        _currentDraw = 0;
        _visible = grid.isVisible();
        renderContext.requestRefresh();
    }
}
