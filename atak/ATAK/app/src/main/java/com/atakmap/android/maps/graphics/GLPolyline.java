
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.opengl.GLES30;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Polyline.OnBasicLineStyleChangedListener;
import com.atakmap.android.maps.Polyline.OnLabelsChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.hittest.PartitionRect;
import com.atakmap.android.maps.hittest.ShapeHitTestControl;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPolygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLExtrude;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.PatternStrokeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import org.apache.commons.lang.StringUtils;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GLPolyline extends GLShape2 implements
        Shape.OnPointsChangedListener, OnBasicLineStyleChangedListener,
        OnLabelsChangedListener, Polyline.OnLabelTextSizeChanged,
        Polyline.OnAltitudeModeChangedListener,
        Polyline.OnHeightStyleChangedListener,
        Shape.OnHeightChangedListener,
        ShapeHitTestControl {

    public static final String TAG = "GLPolyline";

    private final static double DEFAULT_MIN_RENDER_SCALE = (1.0d / 100000.0d);
    private static final int PARTITION_SIZE = 25;

    private final Polyline _subject;
    private boolean _closed;
    private DoubleBuffer _points;
    protected int numPoints;
    protected int _pointsSize = 2;
    private GeoPoint[] origPoints;
    protected boolean _needsUpdate;
    protected FloatBuffer _verts2;
    /** XY = 2, XYZ = 3; subclasses may set in constructor */
    protected int _verts2Size = 2;
    protected long _verts2Ptr;
    private boolean _outlineStroke, _outlineHalo;

    protected int basicLineStyle;
    private int _labelTextSize;
    private Typeface _labelTypeface;
    private static final float div_2 = 1f / 2f;
    protected static final double div_180_pi = 180d / Math.PI;
    private float _textAngle;
    private final float[] _textPoint = new float[2];
    private GLText _label;

    // Line label
    private String _lineLabel;
    private String[] _lineLabelArr;
    private float _lineLabelWidth, _lineLabelHeight;

    private GeoPoint centerPoint;
    private GLText _cplabel;
    protected long currentDraw = 0;
    protected boolean recompute = true;

    private Map<String, Object> segmentLabels = null;

    private String centerLabelText = null;
    private int middle = -1;

    public AltitudeMode altitudeMode;

    private final GLBatchLineString impl;

    protected boolean needsProjectVertices;

    // Height extrusion
    protected DoubleBuffer _3dPointsPreForward;
    protected FloatBuffer _3dPoints;
    protected DoubleBuffer _outlinePointsPreForward;
    protected FloatBuffer _outlinePoints;
    protected boolean _shouldReextrude;
    protected double _height = Double.NaN;
    protected boolean _hasHeight;
    protected int _heightStyle;

    // Hit testing
    protected RectF _screenRect = new RectF();
    protected List<PartitionRect> _partitionRects = new ArrayList<>();

    public GLPolyline(MapRenderer surface, Polyline subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES
                | GLMapView.RENDER_PASS_SURFACE
                | GLMapView.RENDER_PASS_SCENES);
        this.impl = new GLBatchPolygon(surface);

        _subject = subject;
        GeoPoint[] points = subject.getPoints();
        centerPoint = subject.getCenter().get();
        this.updatePointsImpl(centerPoint, points);

        basicLineStyle = subject.getBasicLineStyle();
        _heightStyle = subject.getHeightStyle();
        super.onStyleChanged(_subject);
        super.onFillColorChanged(_subject);
        super.onStrokeColorChanged(_subject);
        super.onStrokeWeightChanged(_subject);
        refreshStyle();
        segmentLabels = subject.getLabels();
        _labelTextSize = subject.getLabelTextSize();
        _labelTypeface = subject.getLabelTypeface();
        onAltitudeModeChanged(subject.getAltitudeMode());
        // synchronized(subject) {
        // unwrapLng = subject.getMetaBoolean("unwrapLongitude", false);
        // }

        this.needsProjectVertices = (this.getClass() != GLPolyline.class);
        onHeightChanged(_subject);
    }

    @Override
    public void startObserving() {
        super.startObserving();
        this.onPointsChanged(_subject);
        this.onLabelsChanged(_subject);
        this.refreshStyle();
        _subject.addOnPointsChangedListener(this);
        _subject.addOnBasicLineStyleChangedListener(this);
        _subject.addOnLabelsChangedListener(this);
        _subject.addOnLabelTextSizeChangedListener(this);
        _subject.addOnAltitudeModeChangedListener(this);
        _subject.addOnHeightChangedListener(this);
        _subject.addOnHeightStyleChangedListener(this);
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.removeOnPointsChangedListener(this);
        _subject.removeOnBasicLineStyleChangedListener(this);
        _subject.removeOnLabelsChangedListener(this);
        _subject.removeOnLabelTextSizeChangedListner(this);
        _subject.removeOnAltitudeModeChangedListener(this);
        _subject.removeOnHeightChangedListener(this);
        _subject.removeOnHeightStyleChangedListener(this);
    }

    @Override
    public void onAltitudeModeChanged(final AltitudeMode altitudeMode) {
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (altitudeMode != GLPolyline.this.altitudeMode) {
                    GLPolyline.this.altitudeMode = altitudeMode;
                    updatePointsImpl(centerPoint, origPoints);
                }
                impl.setAltitudeMode(altitudeMode);
            }
        });
    }

    @Override
    public void onStyleChanged(Shape shape) {
        super.onStyleChanged(shape);

        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                refreshStyle();
            }
        });
    }

    @Override
    public void onFillColorChanged(Shape shape) {
        super.onFillColorChanged(shape);
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                refreshStyle();
            }
        });
    }

    @Override
    public void onStrokeColorChanged(Shape shape) {
        super.onStrokeColorChanged(shape);
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                refreshStyle();
            }
        });
    }

    @Override
    public void onStrokeWeightChanged(Shape shape) {
        super.onStrokeWeightChanged(shape);
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                refreshStyle();
            }
        });
    }

    private static int argb(float r, float g, float b, float a) {
        return Color.argb((int) (a * 255), (int) (r * 255), (int) (g * 255),
                (int) (b * 255));
    }

    private void refreshStyle() {
        final int style = _subject.getStyle();
        final int basicStyle = _subject.getBasicLineStyle();
        boolean fill = this.fill && (!_hasHeight || !MathUtils.hasBits(
                _heightStyle, Polyline.HEIGHT_STYLE_POLYGON));

        boolean closed = (style & Polyline.STYLE_CLOSED_MASK) != 0;
        if (closed != _closed) {
            _closed = closed;
            updatePointsImpl(this.centerPoint, this.origPoints);
        }

        _outlineStroke = ((style & Polyline.STYLE_OUTLINE_STROKE_MASK) != 0);
        _outlineHalo = ((style & Polyline.STYLE_OUTLINE_HALO_MASK) != 0);
        basicLineStyle = basicStyle;

        Style s;
        if (basicStyle == Polyline.BASIC_LINE_STYLE_DASHED)
            s = new PatternStrokeStyle(0x3F, 8, this.strokeColor,
                    this.strokeWeight);
        else if (basicStyle == Polyline.BASIC_LINE_STYLE_DOTTED)
            s = new PatternStrokeStyle(0x03, 8, this.strokeColor,
                    this.strokeWeight);
        else
            s = new BasicStrokeStyle(this.strokeColor, this.strokeWeight);

        int numStyles = 0;
        if (fill)
            numStyles++;
        if (_outlineStroke)
            numStyles++;
        if (_outlineHalo)
            numStyles += 2;

        ArrayList<Style> composite = (numStyles > 0)
                ? new ArrayList<Style>(numStyles + 1)
                : null;

        if (composite != null) {
            if (fill) {
                composite.add(new BasicFillStyle(this.fillColor));
            }
            if (_outlineStroke) {
                composite
                        .add(new BasicStrokeStyle(argb(0f, 0f, 0f, strokeAlpha),
                                strokeWeight + 2f));
            }
            if (_outlineHalo) {
                composite.add(new BasicStrokeStyle(
                        argb(strokeRed, strokeGreen, strokeBlue,
                                strokeAlpha / 8f),
                        strokeWeight + 10f));
                composite.add(new BasicStrokeStyle(
                        argb(strokeRed, strokeGreen, strokeBlue,
                                strokeAlpha / 4f),
                        strokeWeight + 4f));
            }

            composite.add(s);
            s = new CompositeStyle(
                    composite.toArray(new Style[0]));
        }

        impl.setStyle(s);
    }

    @Override
    public void onPointsChanged(Shape polyline) {
        final GeoPoint center = polyline.getCenter().get();
        final GeoPoint[] points = polyline.getPoints();
        if (context.isRenderThread())
            this.updatePointsImpl(center, points);
        else
            context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLPolyline.this.updatePointsImpl(center, points);
                }
            });
    }

    /**
     * @deprecated use {@link #updatePointsImpl(GeoPoint, GeoPoint[])}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    protected void updatePointsImpl(GeoPoint[] points) {
        final GeoPoint center = GeoCalculations.centerOfExtremes(points, 0,
                points.length);
        if (center != null) {
            updatePointsImpl(center, points);
        }
    }

    protected void updatePointsImpl(GeoPoint center, GeoPoint[] points) {

        if (points == null)
            points = new GeoPoint[0];
        _pointsSize = altitudeMode == AltitudeMode.ClampToGround ? 2 : 3;
        centerPoint = center;
        int pLen = points.length * _pointsSize;
        if (_points == null || _points.capacity() < pLen) {
            Unsafe.free(_points);
            _points = Unsafe.allocateDirect(pLen, DoubleBuffer.class);
        }

        LineString ls = new LineString(3);

        MapView mv = MapView.getMapView();
        boolean wrap180 = mv != null && mv.isContinuousScrollEnabled();
        bounds.set(points, wrap180);
        _points.clear();
        for (GeoPoint gp : points) {
            _points.put(gp.getLongitude());
            _points.put(gp.getLatitude());
            if (_pointsSize == 3)
                _points.put(gp.getAltitude());

            ls.addPoint(gp.getLongitude(), gp.getLatitude(),
                    Double.isNaN(gp.getAltitude()) ? 0d : gp.getAltitude());
        }
        if (points.length > 0 && _closed) {
            ls.addPoint(points[0].getLongitude(), points[0].getLatitude(),
                    Double.isNaN(points[0].getAltitude()) ? 0d
                            : points[0].getAltitude());
        }

        if (this.impl instanceof GLBatchPolygon)
            ((GLBatchPolygon) this.impl).setGeometry(new Polygon(ls));
        else
            this.impl.setGeometry(ls);

        _points.flip();
        this.origPoints = points;
        this.numPoints = points.length;

        _needsUpdate = true;

        // force a redraw
        currentDraw = 0;

        this.dispatchOnBoundsChanged();
        if (_hasHeight) {
            _shouldReextrude = true;
        }
    }

    @Override
    public void onBasicLineStyleChanged(Polyline polyline) {
        final int style = polyline.getBasicLineStyle();
        if (context.isRenderThread()) {
            basicLineStyle = style;
            refreshStyle();
        } else
            context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    basicLineStyle = style;
                    refreshStyle();
                }
            });

        this.recompute = true;
    }

    @Override
    public void onLabelsChanged(Polyline polyline) {
        this.segmentLabels = polyline.getLabels();
        // specifically for the center label logic
        centerLabelText = null;
        if (segmentLabels != null) {
            Map<String, Object> labelBundle = null;
            for (Map.Entry e : segmentLabels.entrySet()) {
                labelBundle = (Map<String, Object>) e.getValue();

            }
            if (labelBundle != null)
                centerLabelText = (String) labelBundle.get("text");

        }
    }

    @Override
    public void onHeightChanged(MapItem item) {
        refreshHeight();
    }

    @Override
    public void onHeightStyleChanged(Polyline p) {
        refreshHeight();
    }

    private void refreshHeight() {
        final double height = _subject.getHeight();
        final int heightStyle = _subject.getHeightStyle();
        final boolean hasHeight = !Double.isNaN(height)
                && Double.compare(height, 0) != 0
                && heightStyle != Polyline.HEIGHT_STYLE_NONE;
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                setHeightEnabled(hasHeight);
                _height = height;
                if (_heightStyle != heightStyle) {
                    _heightStyle = heightStyle;
                    if (_hasHeight)
                        refreshStyle();
                }
                if (_hasHeight)
                    _shouldReextrude = true;
            }
        });
    }

    private void setHeightEnabled(boolean hasHeight) {
        if (_hasHeight != hasHeight) {
            _hasHeight = hasHeight;

            // Refresh fill style so we only show the terrain fill
            // when the height extrusion hasn't already taken care of it
            refreshStyle();

            // Free unused buffers
            if (!_hasHeight) {
                Unsafe.free(_3dPoints);
                _3dPoints = null;

                Unsafe.free(_3dPointsPreForward);
                _3dPointsPreForward = null;

                Unsafe.free(_outlinePoints);
                _outlinePoints = null;

                Unsafe.free(_outlinePointsPreForward);
                _outlinePointsPreForward = null;
            }
        }
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if ((renderPass & this.renderPass) == 0)
            return;

        boolean sprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);
        boolean surface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);
        boolean scenes = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SCENES);

        // Not in resolution to draw
        if (ortho.drawMapResolution > _subject.getMetaDouble(
                "maxLineRenderResolution",
                Polyline.DEFAULT_MAX_LINE_RENDER_RESOLUTION))
            return;

        else if (ortho.drawMapResolution < _subject.getMetaDouble(
                "minLineRenderResolution",
                Polyline.DEFAULT_MIN_LINE_RENDER_RESOLUTION))
            return;

        if (_hasHeight && scenes && numPoints > 0) {

            boolean renderPolygon = MathUtils.hasBits(_heightStyle,
                    Polyline.HEIGHT_STYLE_POLYGON);
            boolean simpleOutline = MathUtils.hasBits(_heightStyle,
                    Polyline.HEIGHT_STYLE_OUTLINE_SIMPLE);
            boolean renderOutline = MathUtils.hasBits(_heightStyle,
                    Polyline.HEIGHT_STYLE_OUTLINE) || simpleOutline;

            if (_shouldReextrude) {
                updatePointsImpl(centerPoint, origPoints);
                double altitude = ortho.getTerrainMeshElevation(
                        centerPoint.getLatitude(), centerPoint.getLongitude());

                if (renderPolygon) {
                    _3dPointsPreForward = GLExtrude.extrudeRelative(
                            altitude, _height, origPoints, _closed);
                    _3dPoints = Unsafe.allocateDirect(
                            _3dPointsPreForward.limit(), FloatBuffer.class);
                    _3dPointsPreForward.rewind();
                }

                if (renderOutline) {
                    _outlinePointsPreForward = GLExtrude.extrudeOutline(
                            altitude, _height, origPoints,
                            _closed, simpleOutline);
                    _outlinePoints = Unsafe.allocateDirect(
                            _outlinePointsPreForward.limit(),
                            FloatBuffer.class);
                    _outlinePointsPreForward.rewind();
                }

                _shouldReextrude = false;
            }

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadIdentity();

            GLES20FixedPipeline
                    .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
            int color = fill ? fillColor : strokeColor;
            float r = Color.red(color) / 255.0f;
            float g = Color.green(color) / 255.0f;
            float b = Color.blue(color) / 255.0f;
            float a = fill ? Color.alpha(color) / 255.0f : 0.5f;

            if (renderPolygon) {
                GLES20FixedPipeline.glColor4f(r, g, b, a);

                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
                GLES30.glPolygonOffset(1.0f, 1.0f);

                GLES20FixedPipeline.glVertexPointer(3,
                        GLES20FixedPipeline.GL_FLOAT, 0, _3dPoints);

                ortho.forward(_3dPointsPreForward, 3, _3dPoints, 3);
                int pCount = _3dPoints.limit() / 3;

                // Simple order independent transparency, only apply when needed
                if (a < 1.0) {
                    // Assumes shape is convex, although this also works for concave shapes that use the
                    // same color throughout the mesh. In practice you can't tell the difference between
                    // this and _much_ more expensive OIT implementations for _this_ use case.
                    // Works under the assumption that back facing polygons are behind front facing,
                    // preserving correct back to front ordering.
                    GLES20FixedPipeline.glEnable(GLES30.GL_CULL_FACE);
                    GLES20FixedPipeline.glCullFace(GLES30.GL_FRONT);
                    GLES20FixedPipeline.glDrawArrays(GLES30.GL_TRIANGLES, 0,
                            pCount);

                    GLES20FixedPipeline.glCullFace(GLES30.GL_BACK);
                    GLES20FixedPipeline.glDrawArrays(GLES30.GL_TRIANGLES, 0,
                            pCount);
                    GLES20FixedPipeline.glDisable(GLES30.GL_CULL_FACE);
                } else {
                    GLES20FixedPipeline.glDrawArrays(GLES30.GL_TRIANGLES, 0,
                            pCount);
                }

                GLES30.glPolygonOffset(0.0f, 0.0f);
                GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
            }

            // Outline around height polygon (only when map is tilted)
            if (renderOutline && ortho.drawTilt > 0) {
                ortho.forward(_outlinePointsPreForward, 3, _outlinePoints, 3);
                GLES20FixedPipeline.glLineWidth(this.strokeWeight);
                GLES20FixedPipeline.glVertexPointer(3, GLES30.GL_FLOAT, 0,
                        _outlinePoints);
                GLES20FixedPipeline.glColor4f(r * .9f, g * .9f, b * .9f, 1.0f);
                GLES20FixedPipeline.glDrawArrays(GLES30.GL_LINES, 0,
                        _outlinePoints.limit() / 3);
            }

            GLES20FixedPipeline
                    .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glPopMatrix();
        }

        // Altitude mode toggle
        if (sprites && altitudeMode == AltitudeMode.ClampToGround
                || surface && altitudeMode != AltitudeMode.ClampToGround)
            return;

        if (currentDraw != ortho.drawVersion)
            recompute = true;
        currentDraw = ortho.drawVersion;

        if (this.needsProjectVertices)
            _projectVerts(ortho);

        if (stroke || (fill && _closed)) {

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadIdentity();

            this.impl.draw(ortho);

            GLES20FixedPipeline.glPopMatrix();

            // TODO look at GLArrow2 to see how the text is being drawin on the line.
            drawLabels(ortho);
        }
        this.recompute = false;
    }

    protected void drawLabels(GLMapView ortho) {

        boolean drawCenterLabel = _subject.hasMetaValue("minRenderScale")
                && ortho.drawMapScale >= _subject.getMetaDouble(
                        "minRenderScale", DEFAULT_MIN_RENDER_SCALE);

        double minRes = _subject.getMetaDouble("minLabelRenderResolution",
                Polyline.DEFAULT_MIN_LABEL_RENDER_RESOLUTION);
        double maxRes = _subject.getMetaDouble("maxLabelRenderResolution",
                Polyline.DEFAULT_MAX_LABEL_RENDER_RESOLUTION);

        drawCenterLabel |= ortho.drawMapResolution > minRes
                && ortho.drawMapResolution < maxRes;

        try {
            if (drawCenterLabel && _subject.hasMetaValue("centerPointLabel")) {
                if (_cplabel == null) {
                    MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                            _labelTextSize);
                    _cplabel = GLText.getInstance(textFormat);
                }
                String _text = _subject.getMetaString(
                        "centerPointLabel", "");
                float _textWidth = _cplabel.getStringWidth(_text);
                float _textHeight = _cplabel.getStringHeight();
                ortho.forward(centerPoint, ortho.scratch.pointF);
                float xpos = ortho.scratch.pointF.x
                        - (_textWidth / 2.0f);
                float ypos = ortho.scratch.pointF.y;
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(xpos, ypos, 0f);
                drawLabelNoMarquee(_textWidth, _textHeight, _text,
                        _cplabel);
                GLES20FixedPipeline.glPopMatrix();
            }
            drawSegmentLabels(ortho);
            if (numPoints > 1 && _subject.hasMetaValue("labels_on")) {
                String lineLabel = _subject.getLineLabel();
                if (!FileSystemUtils.isEquals(_lineLabel, lineLabel)) {
                    _lineLabel = lineLabel;
                    _lineLabelArr = lineLabel.split("\n");
                    if (_label == null) {
                        MapTextFormat textFormat = new MapTextFormat(
                                _labelTypeface, _labelTextSize);
                        _label = GLText.getInstance(textFormat);
                    }
                    _lineLabelWidth = _label.getStringWidth(lineLabel);
                    _lineLabelHeight = _label.getStringHeight(lineLabel);
                }
                if (!StringUtils.isBlank(_lineLabel))
                    drawFloatingLabel(ortho);
            }
        } catch (Exception cme) {
            // catch and ignore - without adding performance penalty to the whole
            // metadata arch. It will clean up on the next draw.
            Log.e(TAG,
                    "concurrent modification of the segment labels occurred during display");
        }
    }

    private void drawLabelNoMarquee(float textWidth,
            float textHeight, String _text, GLText label) {
        if (_text.length() == 0)
            return;
        GLNinePatch smallNinePatch = GLRenderGlobals.get(this.context)
                .getSmallNinePatch();
        if (smallNinePatch != null) {
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(-4f, -label.getDescent(), 0f);
            smallNinePatch.draw(textWidth + 8f, textHeight);
            GLES20FixedPipeline.glPopMatrix();
        }
        label.draw(GLText.localize(_text), 1, 1, 1, 1);
    }

    private void drawFloatingLabel(GLMapView ortho) {
        _projectVerts(ortho);

        // _ensureVerts over-allocates, but doesn't set the limit, this causes sutherland to emit an
        // extra <0, 0>, which results in a "floating" floating-label.
        // The following line is dependent on the behavior of _ensureVerts and _projectVerts.
        _verts2.limit(_verts2.capacity() - _verts2Size);
        List<List<Vector2D>> polys = Vector2D
                .clipPolylineCohenSutherland(new RectF(ortho._left, ortho._top,
                        ortho._right, ortho._bottom), _verts2, _verts2Size);
        double[] segLengths = new double[numPoints + (_closed ? 1 : 0)];
        double xmin = Double.MAX_VALUE, ymin = Double.MAX_VALUE,
                xmax = Double.MIN_VALUE, ymax = Double.MIN_VALUE;
        GLNinePatch ninePatch = GLRenderGlobals.get(this.context)
                .getMediumNinePatch();
        PointF start = new PointF(0, 0);
        PointF end = new PointF(0, 0);
        for (int i = 0; i < polys.size(); i++) {
            if (polys.get(i).size() > 1) {
                double totalLength = 0;
                Vector2D ps = polys.get(i).get(0);
                xmax = xmin = ps.x;
                ymax = ymin = ps.y;
                for (int j = 1; j < polys.get(i).size(); j++) {
                    Vector2D pd = polys.get(i).get(j);
                    if (pd.x > xmax) {
                        xmax = pd.x;
                    }
                    if (pd.x < xmin) {
                        xmin = pd.x;
                    }
                    if (pd.y > ymax) {
                        ymax = pd.y;
                    }
                    if (pd.y < ymin) {
                        ymin = pd.y;
                    }
                    totalLength += pd.distance(ps);
                    segLengths[j] = totalLength;
                    ps = pd;
                }
                if (_label == null) {
                    MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                            _labelTextSize);
                    _label = GLText
                            .getInstance(textFormat);
                }

                if (Math.max(xmax - xmin, ymax - ymin) >= _lineLabelWidth) {
                    double halfLength = totalLength * 0.5;
                    int seg = -1;
                    for (int j = 0; j < segLengths.length; j++) {
                        if (segLengths[j] > halfLength) {
                            seg = j - 1;
                            break;
                        }
                    }
                    double segPercent = (halfLength - segLengths[seg])
                            / (segLengths[seg + 1] - segLengths[seg]);
                    Vector2D segOffset = polys.get(i).get(seg + 1)
                            .subtract(polys.get(i).get(seg)).scale(segPercent);
                    Vector2D center = polys.get(i).get(seg).add(segOffset);
                    segOffset = segOffset.normalize();

                    boolean behindGlobe = false;
                    if (ortho
                            .getDisplayMode() == MapRenderer2.DisplayMode.Globe) {

                        // find the middle segment that lines up with label center
                        int middle = this.findMiddleVisibleSegment(ortho);
                        if (middle >= 0 && middle < origPoints.length - 1) {
                            GeoPoint a = origPoints[middle];
                            GeoPoint b = origPoints[middle + 1];

                            // interpolate the middle
                            ortho.scratch.geo.set(
                                    (a.getLatitude() + b.getLatitude()) * 0.5,
                                    (a.getLongitude() + b.getLongitude()) * 0.5,
                                    (a.getAltitude() + b.getAltitude()) * 0.5);

                            ortho.scene.forward(ortho.scratch.geo,
                                    ortho.scratch.pointD);

                            // beyond the far depth range means behind globe
                            behindGlobe = (ortho.scratch.pointD.z > 1.0);
                        }
                    }

                    if (!behindGlobe) {
                        start.x = (float) (center.x - segOffset.x);
                        start.y = (float) (center.y - segOffset.y);
                        end.x = (float) (center.x + segOffset.x);
                        end.y = (float) (center.y + segOffset.y);
                        buildLabel(ortho, start, end);

                        GLES20FixedPipeline.glPushMatrix();
                        GLES20FixedPipeline.glTranslatef(_textPoint[0],
                                _textPoint[1], 0f);
                        GLES20FixedPipeline.glRotatef(_textAngle, 0f, 0f, 1f);
                        GLES20FixedPipeline.glTranslatef(-_lineLabelWidth / 2,
                                -_lineLabelHeight / 2 + _label.getDescent(), 0);
                        GLES20FixedPipeline.glPushMatrix();
                        GLES20FixedPipeline.glTranslatef(-8f,
                                -_label.getDescent() - 4f, 0f);
                        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.8f);
                        if (ninePatch != null)
                            ninePatch.draw(_lineLabelWidth + 16f,
                                    _lineLabelHeight + 8f);
                        GLES20FixedPipeline.glPopMatrix();
                        for (int j = 0; j < _lineLabelArr.length; j++) {
                            GLES20FixedPipeline.glPushMatrix();
                            GLES20FixedPipeline.glTranslatef(0f,
                                    ((_lineLabelArr.length - 1) - j)
                                            * _label.getCharHeight(),
                                    0f);
                            _label.draw(_lineLabelArr[j], 1, 1, 1, 1);
                            GLES20FixedPipeline.glPopMatrix();
                        }
                        GLES20FixedPipeline.glPopMatrix();
                    }
                }
            }
        }
    }

    private void drawSegmentLabels(GLMapView ortho) {

        // check for show middle label flag. This will signal to draw the label in the middle
        // visible line segment
        if (_subject.hasMetaValue("centerLabel")) {
            showCenterLabel(ortho);
            return;
        }

        if (segmentLabels == null) {
        } else {
            _projectVerts(ortho);

            Map<String, Object> labelBundle;
            int segment = 0;
            String text = "";
            PointF startPoint = new PointF();
            PointF endPoint = new PointF();
            final double mapGSD = ortho.drawMapResolution;
            double minGSD;
            for (Map.Entry e : segmentLabels.entrySet()) {
                labelBundle = (Map<String, Object>) e.getValue();
                segment = ((Number) labelBundle.get("segment")).intValue();
                if (segment < 0 || segment >= this.numPoints - 1)
                    continue;

                minGSD = Double.MAX_VALUE;
                if (labelBundle.containsKey("min_gsd"))
                    minGSD = ((Number) labelBundle.get("min_gsd"))
                            .doubleValue();
                if (mapGSD > minGSD)
                    continue;

                text = (String) labelBundle.get("text");

                if (text == null || text.length() == 0)
                    continue;

                final int stride = (4 * _verts2Size);
                startPoint.x = Unsafe.getFloat(_verts2Ptr + segment * stride);
                startPoint.y = Unsafe
                        .getFloat(_verts2Ptr + segment * stride + 4);
                endPoint.x = Unsafe.getFloat(_verts2Ptr + segment * stride + 8);
                endPoint.y = Unsafe
                        .getFloat(_verts2Ptr + segment * stride + 12);

                // now set up the GL label
                if (_label == null) {
                    MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                            _labelTextSize);
                    _label = GLText.getInstance(textFormat);
                }

                // only draw the text if the label fits within the distance between the end points
                // of the segment. This number is multiplied by 2.5 because circles are polylines
                // and it
                // keeps it so that text can be shown when displaying circles.
                // 4 was chosen because of the number of polylines that make up a circle at this
                // time.
                // It would probably be a good idea to construct a GLCircle in the future?
                // XXX - revisit for the next version

                if ((Math.abs(startPoint.x - endPoint.x) * 2.5) < _label
                        .getStringWidth(text)
                        && (Math.abs(startPoint.y - endPoint.y) * 2.5) < _label
                                .getStringWidth(text)) {

                } else {
                    drawTextLabel(ortho, startPoint, endPoint, _label, text);
                }

            }
        }
    }

    private void drawTextLabel(GLMapView ortho, PointF startVert,
            PointF endVert, GLText theLabel, String text) {

        text = GLText.localize(text);

        buildLabel(ortho, startVert, endVert);
        GLNinePatch _ninePatch = GLRenderGlobals.get(this.context)
                .getMediumNinePatch();

        final float labelWidth = _label.getStringWidth(text);
        final float labelHeight = _label.getStringHeight();

        GLES20FixedPipeline.glPushMatrix();

        GLES20FixedPipeline.glTranslatef(_textPoint[0], _textPoint[1], 0);
        GLES20FixedPipeline.glRotatef(_textAngle, 0f, 0f, 1f);
        GLES20FixedPipeline.glTranslatef(-labelWidth / 2, -labelHeight / 2 + 4,
                0);
        GLES20FixedPipeline.glPushMatrix();
        float outlineOffset = -((GLText.getLineCount(text) - 1) * _label
                .getBaselineSpacing())
                - 4;
        GLES20FixedPipeline.glTranslatef(-8f, outlineOffset - 4f, 0f);
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.8f);
        if (_ninePatch != null) {
            _ninePatch.draw(labelWidth + 16f, labelHeight + 8f);
        }
        GLES20FixedPipeline.glPopMatrix();
        GLES20FixedPipeline.glColor4f(1, 1, 1, 1);
        _label.draw(text, 1, 1, 1, 1);
        GLES20FixedPipeline.glPopMatrix();
    }

    /**
     * find the location the label should be placed and the angle it should be rotated.
     */
    private void buildLabel(GLMapView ortho, PointF startVert, PointF endVert) {

        final float p0x = startVert.x;
        final float p0y = startVert.y;

        final float p1x = endVert.x;
        final float p1y = endVert.y;

        final float xmin = (p0x < p1x) ? p0x : p1x;
        final float ymin = (p0x < p1x) ? p0y : p1y;
        final float xmax = (p0x > p1x) ? p0x : p1x;
        final float ymax = (p0x > p1x) ? p0y : p1y;

        float xmid = (int) (xmin + xmax) * div_2;
        float ymid = (int) (ymin + ymax) * div_2;

        if (!_subject.hasMetaValue("staticLabel")) {
            startVert = new PointF(xmin, ymin);
            endVert = new PointF(xmax, ymax);

            RectF _view = this.getWidgetViewF();
            PointF[] ip = _getIntersectionPoint(_view, startVert, endVert);

            if (ip[0] != null || ip[1] != null) {
                if (ip[0] != null && ip[1] != null) {
                    xmid = (ip[0].x + ip[1].x) / 2.0f;
                    ymid = (ip[0].y + ip[1].y) / 2.0f;
                } else {

                    PointF origin = startVert;
                    if (_view.left < endVert.x && endVert.x < _view.right &&
                            _view.bottom < endVert.y && endVert.y < _view.top) {
                        origin = endVert;
                    }

                    if (ip[0] != null) {
                        // Log.d("SHB", "bottom is clipped");
                        xmid = (ip[0].x + origin.x) / 2.0f;
                        ymid = (ip[0].y + origin.y) / 2.0f;
                    } else {
                        // Log.d("SHB", "top is clipped");
                        xmid = (ip[1].x + origin.x) / 2.0f;
                        ymid = (ip[1].y + origin.y) / 2.0f;
                    }
                }
            }
        }

        _textAngle = (float) (Math.atan2(p0y - p1y, p0x
                - p1x) * div_180_pi);
        _textPoint[0] = xmid;
        _textPoint[1] = ymid;

        if (_textAngle > 90 || _textAngle < -90)
            _textAngle += 180;
    }

    /**
     * Display the label on the center of the middle visible segment
     * 
     * @param ortho
     */
    private void showCenterLabel(GLMapView ortho) {
        // get the text to display, or return if there is none

        final String clt = centerLabelText;

        if (clt == null || clt.length() == 0)
            return;

        if (recompute) {
            middle = findMiddleVisibleSegment(ortho);
        }

        if (middle == -1)
            return;

        if (_label == null) {
            MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                    _labelTextSize);
            _label = GLText.getInstance(textFormat);
        }

        PointF startPoint = new PointF();
        PointF endPoint = new PointF();

        _projectVerts(ortho);

        final int stride = (4 * _verts2Size);
        startPoint.x = Unsafe.getFloat(_verts2Ptr + middle * stride);
        startPoint.y = Unsafe.getFloat(_verts2Ptr + middle * stride + 4);
        endPoint.x = Unsafe.getFloat(_verts2Ptr + middle * stride + 8);
        endPoint.y = Unsafe.getFloat(_verts2Ptr + middle * stride + 12);

        final float centerLabelWidth = _label.getStringWidth(clt);

        // see comment on label code, use a larger number for how this is used.
        if ((Math.abs(startPoint.x - endPoint.x) * 8.0) < centerLabelWidth
                && (Math.abs(startPoint.y - endPoint.y)
                        * 8.0) < centerLabelWidth) {

        } else {
            drawTextLabel(ortho, startPoint, endPoint, _label, clt);
        }
    }

    private boolean segContained(final PointF endPt1, final PointF endPt2,
            final RectF visibleView) {
        if (endPt1 == null)
            return false;
        float p1x = endPt1.x;
        float p1y = endPt1.y;
        if (endPt2 == null)
            return false;
        float p2x = endPt2.x;
        float p2y = endPt2.y;
        return ((p1y < visibleView.top &&
                p1x < visibleView.right &&
                p1x > 0 && p1y > 0)
                || (p2y < visibleView.top &&
                        p2x < visibleView.right &&
                        p2x > 0 && p2y > 0));
    }

    /**
     * TODO: Currently a direct copy from GLMapItem - but will be reritten / removed when the final
     * version is done. Retrieve the bounding RectF of the current state of the Map. This accounts
     * for the OrthoMapView's focus, so DropDowns will be accounted for. NOTE- the RectF this
     * returns is not a valid RectF since the origin coordinate is in the lower left (ll is 0,0).
     * Therefore the RectF.contains(PointF) method will not work to determine if a point falls
     * inside the bounds.
     *
     * @return The bounding RectF
     */
    protected RectF getWidgetViewF() {
        return getDefaultWidgetViewF(context);
    }

    /**
     * TODO: Currently a direct copy from GLMapItem - but will be reritten / removed when the final
     * version is done.
     * 
     * @param ctx
     * @return
     */
    protected static RectF getDefaultWidgetViewF(MapRenderer ctx) {
        // Could be in half or third display of dropdown, so use the offset;
        float right = ((GLMapView) ctx).focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        float top = ((GLMapView) ctx).focusy * 2;
        return new RectF(0f, top - MapView.getMapView().getActionBarHeight(),
                right, 0);
    }

    /**
     * TODO: Currently a direct copy from GLMapItem - but will be reritten / removed when the final
     * version is done. Provides the top and the bottom most intersection points.
     */
    public static PointF[] _getIntersectionPoint(RectF r, PointF cF,
            PointF vF) {

        if (r.left < cF.x && cF.x < r.right && r.bottom < cF.y && cF.y < r.top
                &&
                r.left < vF.x && vF.x < r.right && r.bottom < vF.y
                && vF.y < r.top) {
            return new PointF[] {
                    cF, vF
            };
        }

        PointF[] ret = new PointF[2];
        Vector2D[] rets = new Vector2D[4];
        Vector2D c = new Vector2D(cF.x, cF.y);
        Vector2D v = new Vector2D(vF.x, vF.y);

        Vector2D topLeft = new Vector2D(r.left, r.top);
        Vector2D topRight = new Vector2D(r.right, r.top);
        Vector2D botRight = new Vector2D(r.right, r.bottom);
        Vector2D botLeft = new Vector2D(r.left, r.bottom);

        // Start at top line and go clockwise

        rets[0] = Vector2D
                .segmentToSegmentIntersection(topLeft, topRight, c, v);
        rets[1] = Vector2D.segmentToSegmentIntersection(topRight, botRight, c,
                v);
        rets[2] = Vector2D
                .segmentToSegmentIntersection(botRight, botLeft, c, v);
        rets[3] = Vector2D.segmentToSegmentIntersection(botLeft, topLeft, c, v);

        // Check the returned values - returns both the top and the bottom intersection points.
        for (int i = 0; i < 4; i++) {
            // Check to see if it intersected
            if (rets[i] != null) {
                if (i < 2) {
                    // Log.d("SHB", "interesection detected entry #" + i);
                    if (ret[0] == null)
                        ret[0] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                    else
                        ret[1] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                } else {
                    // Log.d("SHB", "interesection detected entry #" + i);
                    if (ret[1] == null)
                        ret[1] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                    else
                        ret[0] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                }
            }
        }

        return ret;
    }

    /**
     * Determines which line segments are currently visible and then returns the segment in the
     * middle.
     * 
     * @param ortho
     * @return - The index of the starting point of middle segment
     */
    private int findMiddleVisibleSegment(GLMapView ortho) {
        RectF visibleView = this.getWidgetViewF();
        GeoPoint[] points = origPoints;

        // middle, easiest to find.
        if (points.length > 0) {
            int middle = points.length / 2;
            PointF endPt1 = ortho.forward(points[middle], ortho.scratch.pointF);
            PointF endPt2 = ortho.forward(points[middle + 1],
                    ortho.scratch.pointF);
            boolean contained = segContained(endPt1, endPt2, visibleView);
            if (contained)
                return middle;
        }

        // first quarter or last quarter
        if (points.length > 3) {
            int midmid = points.length / 4;
            PointF endPt1 = ortho.forward(points[midmid], ortho.scratch.pointF);
            PointF endPt2 = ortho.forward(points[midmid + 1],
                    ortho.scratch.pointF);
            boolean contained = segContained(endPt1, endPt2, visibleView);
            if (contained)
                return midmid;

            endPt1 = ortho.forward(points[midmid * 3 - 1],
                    ortho.scratch.pointF);
            endPt2 = ortho
                    .forward(points[midmid * 3], ortho.scratch.pointF);
            contained = segContained(endPt1, endPt2, visibleView);
            if (contained)
                return midmid * 3 - 1;
        }

        // walk till we find.
        for (int i = 0; i < points.length - 1; i++) {
            if (points[i] == null || points[i + 1] == null)
                return -1;

            PointF endPt1 = ortho.forward(points[i], ortho.scratch.pointF);
            PointF endPt2 = ortho.forward(points[i + 1], ortho.scratch.pointF);
            boolean contained = segContained(endPt1, endPt2, visibleView);
            if (contained)
                return i;

        }

        return -1;
    }

    protected void _ensureVertBuffer() {
        if (this.numPoints > 0) {
            if (_verts2 == null
                    || _verts2.capacity() < (this.numPoints + 1)
                            * _verts2Size) {
                // Allocate enough space for the number of points + 1 in case we want to draw a
                // closed polygon
                _verts2 = com.atakmap.lang.Unsafe
                        .allocateDirect(((this.numPoints + 1) * _verts2Size),
                                FloatBuffer.class); // +2 wrap points
                _verts2Ptr = Unsafe.getBufferPointer(_verts2);
            }
            _verts2.clear();
        } else if (this.numPoints == 0) {
            _verts2 = null;
            _verts2Ptr = 0L;
        }
    }

    protected void _projectVerts(final GLMapView ortho) {
        if (recompute && this.numPoints > 0) {
            _ensureVertBuffer();

            AbstractGLMapItem2.forward(ortho, _points, _pointsSize, _verts2,
                    _verts2Size, bounds);

            // close the line if necessary
            if (_closed) {
                _verts2.limit(_verts2.limit() + _verts2Size);
                int idx = this.numPoints * _verts2Size;
                _verts2.put(idx++, _verts2.get(0));
                _verts2.put(idx++, _verts2.get(1));
                if (_verts2Size == 3)
                    _verts2.put(idx++, _verts2.get(2));
            }

            // Build bounds for hit testing
            recomputeScreenRectangles(ortho);
        }
    }

    protected void recomputeScreenRectangles(GLMapView ortho) {
        int maxIdx = (this.numPoints + (_closed ? 1 : 0)) * _verts2Size;
        _verts2.clear();
        int partIdx = 1;
        int partCount = 0;
        PartitionRect partition = !_partitionRects.isEmpty()
                ? _partitionRects.get(0)
                : new PartitionRect();
        int idx = 0;
        for (int i = 0; i < maxIdx; i += _verts2Size) {
            float x = _verts2.get(i);
            float y = _verts2.get(i + 1);

            y = ortho.getTop() - y;

            // Update main bounding rectangle
            if (i == 0) {
                _screenRect.set(x, y, x, y);
            } else {
                _screenRect.set(Math.min(_screenRect.left, x),
                        Math.min(_screenRect.top, y),
                        Math.max(_screenRect.right, x),
                        Math.max(_screenRect.bottom, y));
            }

            // Update partition bounding rectangle
            if (partIdx == 0) {
                partition.set(x, y, x, y);
            } else {
                partition.set(Math.min(partition.left, x),
                        Math.min(partition.top, y),
                        Math.max(partition.right, x),
                        Math.max(partition.bottom, y));
            }

            if (partIdx == PARTITION_SIZE || i == maxIdx - _verts2Size) {
                if (partCount >= _partitionRects.size())
                    _partitionRects.add(partition);
                partition.endIndex = idx;
                partCount++;
                partIdx = 0;
                partition = partCount < _partitionRects.size()
                        ? _partitionRects.get(partCount)
                        : new PartitionRect();
                partition.startIndex = idx + 1;
            }
            partIdx++;
            idx++;
        }
        while (partCount < _partitionRects.size())
            _partitionRects.remove(partCount);
    }

    @Override
    public Result hitTest(float screenX, float screenY, float radius) {

        RectF hitRect = new RectF(screenX - radius, screenY - radius,
                screenX + radius, screenY + radius);

        // First check hit on bounding rectangle
        if (!RectF.intersects(_screenRect, hitRect))
            return null;

        // Now check partitions
        List<PartitionRect> hitRects = new ArrayList<>();
        for (PartitionRect r : _partitionRects) {

            // Check hit on partition
            if (!RectF.intersects(r, hitRect))
                continue;

            // Keep track of rectangles we've already hit tested
            hitRects.add(r);

            // Point hit test
            for (int i = r.startIndex; i <= r.endIndex && i < numPoints; i++) {
                int vIdx = i * _verts2Size;
                float x = _verts2.get(vIdx);
                float y = _verts2.get(vIdx + 1);

                // Point not contained in hit rectangle
                if (!hitRect.contains(x, y))
                    continue;

                // Found a hit - return result
                int pIdx = i * 3;
                double lng = _points.get(pIdx);
                double lat = _points.get(pIdx + 1);
                double hae = _points.get(pIdx + 2);
                Result res = new Result();
                res.screenPoint = new PointF(x, y);
                res.geoPoint = new GeoPoint(lat, lng, hae);
                res.hitType = HitType.POINT;
                res.hitIndex = i;
                return res;
            }
        }

        // No point detections and no hit partitions
        if (hitRects.isEmpty())
            return null;

        // Line hit test
        Vector2D touch = new Vector2D(screenX, screenY);
        for (PartitionRect r : hitRects) {
            float lastX = 0, lastY = 0;
            for (int i = r.startIndex; i <= r.endIndex && i <= numPoints; i++) {

                int vIdx = i * _verts2Size;
                float x = _verts2.get(vIdx);
                float y = _verts2.get(vIdx + 1);

                if (i > r.startIndex) {

                    // Find the nearest point on this line based on the point we touched
                    Vector2D nearest = Vector2D.nearestPointOnSegment(touch,
                            new Vector2D(lastX, lastY),
                            new Vector2D(x, y));
                    float nx = (float) nearest.x;
                    float ny = (float) nearest.y;

                    // Check if the nearest point is within rectangle
                    if (!hitRect.contains(nx, ny))
                        continue;

                    Result res = new Result();
                    res.screenPoint = new PointF(nx, ny);
                    res.geoPoint = null; // XXX - Need to lookup inverse later
                    res.hitType = HitType.LINE;
                    res.hitIndex = i - 1;
                    return res;
                }

                lastX = x;
                lastY = y;
            }
        }

        return null;
    }

    @Override
    public void onLabelTextSizeChanged(Polyline p) {
        final int labelTextSize = p.getLabelTextSize();
        final Typeface labelTypeface = p.getLabelTypeface();
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _labelTextSize = labelTextSize;
                _labelTypeface = labelTypeface;
            }
        });
    }
}
