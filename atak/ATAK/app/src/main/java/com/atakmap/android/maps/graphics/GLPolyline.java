
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Polyline.OnBasicLineStyleChangedListener;
import com.atakmap.android.maps.Polyline.OnLabelsChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPolygon;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.PatternStrokeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import org.apache.commons.lang.StringUtils;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GLPolyline extends GLShape implements
        Shape.OnPointsChangedListener, OnBasicLineStyleChangedListener,
        OnLabelsChangedListener, Polyline.OnLabelTextSizeChanged {

    public static final String TAG = "GLPolyline";

    private final static double DEFAULT_MIN_RENDER_SCALE = (1.0d / 100000.0d);

    private final Polyline _subject;
    private int _closed = 0;
    private DoubleBuffer _points;
    protected int numPoints;
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

    boolean _clampToGround = true;

    private GLBatchLineString impl;

    protected boolean needsProjectVertices;

    public GLPolyline(MapRenderer surface, Polyline subject) {
        super(surface, subject);
        this.impl = new GLBatchPolygon(surface);
        //this.impl = new GLBatchLineString(surface);
        _subject = subject;
        GeoPoint[] points = subject.getPoints();
        this.updatePointsImpl(points);
        basicLineStyle = subject.getBasicLineStyle();
        super.onStyleChanged(_subject);
        super.onFillColorChanged(_subject);
        super.onStrokeColorChanged(_subject);
        super.onStrokeWeightChanged(_subject);
        refreshStyle();
        segmentLabels = subject.getLabels();
        centerPoint = subject.getCenter().get();
        _labelTextSize = subject.getLabelTextSize();
        _labelTypeface = subject.getLabelTypeface();
        //synchronized(subject) {
        //    unwrapLng = subject.getMetaBoolean("unwrapLongitude", false);
        //}

        this.needsProjectVertices = (this.getClass() != GLPolyline.class);

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
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.removeOnPointsChangedListener(this);
        _subject.removeOnBasicLineStyleChangedListener(this);
        _subject.removeOnLabelsChangedListener(this);
        _subject.removeOnLabelTextSizeChangedListner(this);
    }

    @Override
    protected void OnVisibleChanged() {
        super.OnVisibleChanged();
        this.recompute = true;
    }

    @Override
    public void onStyleChanged(Shape shape) {
        super.onStyleChanged(shape);
        post(new Runnable() {
            @Override
            public void run() {
                refreshStyle();
            }
        });
    }

    @Override
    public void onFillColorChanged(Shape shape) {
        super.onFillColorChanged(shape);
        post(new Runnable() {
            @Override
            public void run() {
                refreshStyle();
            }
        });
    }

    @Override
    public void onStrokeColorChanged(Shape shape) {
        super.onStrokeColorChanged(shape);
        post(new Runnable() {
            @Override
            public void run() {
                refreshStyle();
            }
        });
    }

    @Override
    public void onStrokeWeightChanged(Shape shape) {
        super.onStrokeWeightChanged(shape);
        post(new Runnable() {
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

        int closed = ((style & Polyline.STYLE_CLOSED_MASK) != 0) ? 0x1 : 0x0;
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
        if (this.fill) {
            composite.add(new BasicFillStyle(this.fillColor));
        }
        if (_outlineStroke) {
            composite.add(new BasicStrokeStyle(argb(0f, 0f, 0f, strokeAlpha),
                    strokeWeight + 2f));
        }
        if (_outlineHalo) {
            composite.add(new BasicStrokeStyle(
                    argb(strokeRed, strokeGreen, strokeBlue, strokeAlpha / 8f),
                    strokeWeight + 10f));
            composite.add(new BasicStrokeStyle(
                    argb(strokeRed, strokeGreen, strokeBlue, strokeAlpha / 4f),
                    strokeWeight + 4f));
        }

        if (composite != null) {
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
        if (renderContext.isRenderThread())
            this.updatePointsImpl(center, points);
        else
            renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLPolyline.this.updatePointsImpl(center, points);
                }
            });
    }

    /** @deprecated */
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
        centerPoint = center;
        if (_points == null || _points.capacity() < points.length * 2) {
            Unsafe.free(_points);
            _points = Unsafe.allocateDirect(2 * points.length,
                    DoubleBuffer.class);
        }

        LineString ls = new LineString(3);

        MapView mv = MapView.getMapView();
        boolean wrap180 = mv != null && mv.isContinuousScrollEnabled();
        bounds.set(points, wrap180);
        _points.clear();
        for (GeoPoint gp : points) {
            _points.put(gp.getLongitude());
            _points.put(gp.getLatitude());

            ls.addPoint(gp.getLongitude(), gp.getLatitude(),
                    Double.isNaN(gp.getAltitude()) ? 0d : gp.getAltitude());
        }
        if (points.length > 0 && _closed != 0)
            ls.addPoint(points[0].getLongitude(), points[0].getLatitude(),
                    Double.isNaN(points[0].getAltitude()) ? 0d
                            : points[0].getAltitude());
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

        this.OnBoundsChanged();
    }

    @Override
    public void onBasicLineStyleChanged(Polyline polyline) {
        final int style = polyline.getBasicLineStyle();
        if (renderContext.isRenderThread()) {
            basicLineStyle = style;
            refreshStyle();
        } else
            renderContext.queueEvent(new Runnable() {
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
    public void draw(GLMapView ortho) {
        if (currentDraw != ortho.drawVersion)
            recompute = true;
        currentDraw = ortho.drawVersion;

        if (this.needsProjectVertices)
            _projectVerts(ortho);

        if (ortho.drawMapResolution > _subject.getMetaDouble(
                "maxLineRenderResolution",
                Polyline.DEFAULT_MAX_LINE_RENDER_RESOLUTION))
            return;

        else if (ortho.drawMapResolution < _subject.getMetaDouble(
                "minLineRenderResolution",
                Polyline.DEFAULT_MIN_LINE_RENDER_RESOLUTION))
            return;

        if (stroke || (fill && _closed != 0)) {
            this.impl.draw(ortho);

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
        GLNinePatch smallNinePatch = GLRenderGlobals.get(this.renderContext)
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

        List<List<Vector2D>> polys = Vector2D
                .clipPolylineCohenSutherland(new RectF(ortho._left, ortho._top,
                        ortho._right, ortho._bottom), _verts2, _verts2Size);
        double[] segLengths = new double[numPoints + _closed];
        double xmin = Double.MAX_VALUE, ymin = Double.MAX_VALUE,
                xmax = Double.MIN_VALUE, ymax = Double.MIN_VALUE;
        GLNinePatch ninePatch = GLRenderGlobals.get(this.renderContext)
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

    private void drawSegmentLabels(GLMapView ortho) {

        //check for show middle label flag. This will signal to draw the label in the middle visible line segment
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
            final double mapGSD = ortho.getSurface().getMapView()
                    .getMapResolution(ortho.drawMapScale);
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
                // of the segment.   This number is multiplied by 2.5 because circles are polylines and it 
                // keeps it so that text can be shown when displaying circles.   
                // 4 was chosen because of the number of polylines that make up a circle at this time.
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
        GLNinePatch _ninePatch = GLRenderGlobals.get(this.renderContext)
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
                        //Log.d("SHB", "bottom is clipped");
                        xmid = (ip[0].x + origin.x) / 2.0f;
                        ymid = (ip[0].y + origin.y) / 2.0f;
                    } else {
                        //Log.d("SHB", "top is clipped");
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
        //get the text to display, or return if there is none

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
     * Determines which line segments are currently visible and then 
     * returns the segment in the middle.
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

            AbstractGLMapItem2.forward(ortho, _points, 2, _verts2, _verts2Size,
                    bounds);
            // close the line if necessary
            if (_closed != 0) {
                _verts2.limit(_verts2.limit() + _verts2Size);
                int idx = this.numPoints * _verts2Size;
                _verts2.put(idx++, _verts2.get(0));
                _verts2.put(idx++, _verts2.get(1));
                if (_verts2Size == 3)
                    _verts2.put(idx++, _verts2.get(2));
            }

        }
    }

    @Override
    public void onLabelTextSizeChanged(Polyline p) {
        final int labelTextSize = p.getLabelTextSize();
        final Typeface labelTypeface = p.getLabelTypeface();
        renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                _labelTextSize = labelTextSize;
                _labelTypeface = labelTypeface;
            }
        });
    }
}
