
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
import com.atakmap.android.maps.Shape.OnBasicLineStyleChangedListener;
import com.atakmap.android.maps.Polyline.OnLabelsChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.Globe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPolygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLExtrude;

import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.PatternStrokeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLAntiAliasedLine;
import com.atakmap.map.opengl.GLAntiMeridianHelper;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLLabelManager;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import org.apache.commons.lang.StringUtils;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GLPolyline extends GLShape2 implements
        Shape.OnPointsChangedListener, OnBasicLineStyleChangedListener,
        OnLabelsChangedListener, Polyline.OnLabelTextSizeChanged,
        MapItem.OnAltitudeModeChangedListener,
        Polyline.OnHeightStyleChangedListener,
        Shape.OnHeightChangedListener {

    public static final String TAG = "GLPolyline";

    private final static double DEFAULT_MIN_RENDER_SCALE = (1.0d / 100000.0d);

    private static final double threshold = 160000;

    private final Polyline _subject;
    private boolean _closed;
    private DoubleBuffer _points;
    protected int numPoints;
    protected int _pointsSize = 2;
    private GeoPoint[] origPoints;
    protected boolean _needsUpdate;
    protected FloatBuffer _verts2;
    /** XY = 2, XYZ = 3; subclasses may set in constructor */
    protected int _verts2Size = 3;
    protected long _verts2Ptr;
    private boolean _outlineStroke, _outlineHalo;

    protected int basicLineStyle;
    private int _labelTextSize;
    private Typeface _labelTypeface;
    private static final float div_2 = 1f / 2f;
    protected static final double div_180_pi = 180d / Math.PI;
    private final List<SegmentLabel> _segmentLabels = new ArrayList<>();
    private boolean _segmentLabelsDirty = true;
    private SegmentLabel _floatingLabel = null;
    private SegmentLabel _centerLabel = null;
    private boolean labelsOnValue = false;
    private int labelsVersion = -1;

    // Line label
    private String _lineLabel;

    private GeoPoint centerPoint;
    protected long currentDraw = 0;
    protected boolean recompute = true;

    private Map<String, Object> segmentLabels;

    private String centerLabelText = null;
    private int middle = -1;

    public AltitudeMode altitudeMode;

    protected final GLBatchLineString impl;
    private boolean implInvalid;

    protected boolean needsProjectVertices;

    // Height extrusion
    /** extrusion vertices, LLA */
    private DoubleBuffer _3dPointsPreForward;
    /** extrusion vertices, map projection; relative-to-center */
    private FloatBuffer _3dPoints;
    private final GLBatchLineString _3dOutline;
    private boolean _shouldReextrude;
    private double _height = Double.NaN;
    private boolean _hasHeight;
    private int _heightStyle;
    private int _extrudeMode;
    private boolean _extrudeCrossesIDL;
    private int _extrudePrimaryHemi;
    private final GeoPoint _extrusionCentroid = GeoPoint.createMutable();
    private final PointD _extrusionCentroidProj = new PointD(0d, 0d, 0d);
    private int _extrusionCentroidSrid = -1;
    private int _extrusionTerrainVersion = -1;
    /**
     * Raw bounds of the geometry, does not account for height; `z` interpreted per `_altitudeMode`
     */
    private Envelope _geomBounds;

    // NADIR clamp toggle updated during render pass
    protected boolean _nadirClamp;

    public GLPolyline(MapRenderer surface, Polyline subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES
                | GLMapView.RENDER_PASS_SURFACE
                | GLMapView.RENDER_PASS_SCENES);
        this.impl = new GLBatchPolygon(surface);
        this.impl.setTesselationThreshold(threshold);

        // Outline rendered when the shape is extruded
        _3dOutline = new GLBatchLineString(surface);
        _3dOutline.setTessellationEnabled(false);
        _3dOutline.setAltitudeMode(AltitudeMode.Absolute);
        _3dOutline.setConnectionType(GLAntiAliasedLine.ConnectionType.SEGMENTS);

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

        this.needsProjectVertices = (this.getClass() != GLPolyline.class);
        onHeightChanged(_subject);
    }

    @Override
    public void startObserving() {
        super.startObserving();
        onPointsChanged(_subject);
        onLabelsChanged(_subject);
        altitudeMode = _subject.getAltitudeMode();
        refreshStyle();
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
        runOnGLThread(new Runnable() {
            public void run() {
                segmentLabels = null;
                removeLabels();
            }
        });
    }

    @Override
    public void onAltitudeModeChanged(final AltitudeMode altitudeMode) {
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (altitudeMode != GLPolyline.this.altitudeMode) {
                    GLPolyline.this.altitudeMode = altitudeMode;
                    updatePointsImpl();
                }
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
        boolean fill = this.fill && (_nadirClamp || !_hasHeight
                || !MathUtils.hasBits(
                        _heightStyle, Polyline.HEIGHT_STYLE_POLYGON));

        boolean closed = (style & Polyline.STYLE_CLOSED_MASK) != 0;
        if (closed != _closed) {
            _closed = closed;
            updatePointsImpl();
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
        else if (basicStyle == Polyline.BASIC_LINE_STYLE_OUTLINED) {
            BasicStrokeStyle bg = new BasicStrokeStyle(
                    0xFF000000 & this.strokeColor, this.strokeWeight + 2f);
            s = new CompositeStyle(new Style[] {
                    bg,
                    new BasicStrokeStyle(this.strokeColor, this.strokeWeight)
            });
        } else {
            s = new BasicStrokeStyle(this.strokeColor, this.strokeWeight);
        }
        final Style strokeStyle = s;

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

        final Style fs = s;
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                impl.setStyle(fs);
                _3dOutline.setStyle(strokeStyle);
                markSurfaceDirty(true);
            }
        });
    }

    @Override
    public void onPointsChanged(Shape polyline) {
        final GeoPoint center = polyline.getCenter().get();
        final GeoPoint[] points = polyline.getPoints();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                GLPolyline.this.updatePointsImpl(center, points);
            }
        });
    }

    protected void updatePointsImpl(GeoPoint center, GeoPoint[] points) {
        if (points == null)
            points = new GeoPoint[0];
        _pointsSize = 3;
        centerPoint = center;
        int pLen = points.length * _pointsSize;
        if (_points == null || _points.capacity() < pLen) {
            Unsafe.free(_points);
            _points = Unsafe.allocateDirect(pLen, DoubleBuffer.class);
        }

        final LineString ls = new LineString(3);

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

        _points.flip();
        this.origPoints = points;
        this.numPoints = points.length;

        _needsUpdate = true;

        // force a redraw
        currentDraw = 0;
        labelsVersion = -1;

        // Need to update the height extrusion
        if (_hasHeight)
            _shouldReextrude = true;

        // Update points and bounds on the GL thread
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (impl instanceof GLBatchPolygon)
                    ((GLBatchPolygon) impl).setGeometry(new Polygon(ls));
                else
                    impl.setGeometry(ls);

                MapView mv = MapView.getMapView();
                if (mv != null) {
                    _geomBounds = impl.getBounds(mv.getProjection()
                            .getSpatialReferenceID());
                    if (_geomBounds != null) {
                        bounds.setWrap180(mv.isContinuousScrollEnabled());
                        bounds.set(_geomBounds.minY, _geomBounds.minX,
                                _geomBounds.maxY, _geomBounds.maxX);
                        updateBoundsZ();

                        // XXX - naive implementation, will need to handle IDL better
                        bounds.getCenter(_extrusionCentroid);

                        dispatchOnBoundsChanged();
                    }
                    _extrusionCentroidSrid = -1;
                }

                // update/invalidate labels
                if (_centerLabel != null
                        && _centerLabel.id != GLLabelManager.NO_ID)
                    _centerLabel.setGeoPoint(centerPoint);
                if (_floatingLabel != null
                        && _floatingLabel.id != GLLabelManager.NO_ID) {
                    ((GLMapView) context).getLabelManager()
                            .setGeometry(_floatingLabel.id, ls);
                }
                for (SegmentLabel lbl : _segmentLabels)
                    lbl.release();
                _segmentLabels.clear();
                _segmentLabelsDirty = true;

                labelsVersion = -1;
            }
        });
    }

    protected void updatePointsImpl() {
        updatePointsImpl(this.centerPoint, this.origPoints);
    }

    /**
     * Get the current altitude mode which takes into account the
     * {@link ClampToGroundControl}
     * @return Altitude mode
     */
    protected AltitudeMode getAltitudeMode() {
        return _nadirClamp ? AltitudeMode.ClampToGround : altitudeMode;
    }

    /**
     * Whether points should be stored in a 2D or 3D point buffer
     * (if altitude should be ignored or not)
     *
     * XXX - We always need 3D points for hit testing to work properly
     * with perspective rendering
     *
     * @return True to use a 2D point buffer
     */
    protected boolean uses2DPointBuffer() {
        //return getAltitudeMode() == AltitudeMode.ClampToGround && !_hasHeight;
        return true;
    }

    @Override
    public void onBasicLineStyleChanged(Shape polyline) {
        final int style = polyline.getBasicLineStyle();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                basicLineStyle = style;
                refreshStyle();
                recompute = true;
            }
        });
    }

    @Override
    public void onLabelsChanged(Polyline polyline) {
        final Map<String, Object> labels = polyline.getLabels();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                // specifically for the center label logic
                centerLabelText = null;
                segmentLabels = labels;
                if (segmentLabels != null) {
                    Map<String, Object> labelBundle = null;
                    for (Map.Entry<String, Object> e : segmentLabels
                            .entrySet()) {
                        Object v = e.getValue();
                        if (v instanceof Map)
                            labelBundle = (Map<String, Object>) e.getValue();
                    }
                    if (labelBundle != null)
                        centerLabelText = (String) labelBundle.get("text");
                }
                removeLabels();
            }
        });

        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                for (SegmentLabel lbl : _segmentLabels)
                    lbl.release();
                _segmentLabels.clear();
                _segmentLabelsDirty = true;

                labelsVersion = -1;
            }
        });
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
        final int extrudeMode = _subject.getHeightExtrudeMode();
        final boolean hasHeight = !Double.isNaN(height)
                && Double.compare(height, 0) != 0
                && heightStyle != Polyline.HEIGHT_STYLE_NONE;
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                setHeightEnabled(hasHeight);
                _height = height;
                _extrudeMode = extrudeMode;
                if (_heightStyle != heightStyle) {
                    _heightStyle = heightStyle;
                    if (_hasHeight)
                        refreshStyle();
                }
                if (_hasHeight)
                    _shouldReextrude = true;

                updateBoundsZ();
                dispatchOnBoundsChanged();
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
            if (!_hasHeight)
                freeExtrusionBuffers();
        }
    }

    /**
     * Free the various buffers used to render extruded shapes in 3D
     */
    private void freeExtrusionBuffers() {
        Unsafe.free(_3dPoints);
        _3dPoints = null;

        Unsafe.free(_3dPointsPreForward);
        _3dPointsPreForward = null;

        _3dOutline.release();
    }

    /**
     * Get the current extrude mode
     * @return Current extrude mode
     */
    private int getExtrudeMode() {
        int extrudeMode = _extrudeMode;

        // Extrude mode based on shape properties
        if (extrudeMode == Polyline.HEIGHT_EXTRUDE_DEFAULT) {
            // By default closed shapes use "building style" extrusion
            // where the top/bottom of the polygon is flat
            // Open shapes use per-point extrusion like a fence or wall
            if (_closed)
                extrudeMode = Polyline.HEIGHT_EXTRUDE_CENTER_ALT;
            else
                extrudeMode = Polyline.HEIGHT_EXTRUDE_PER_POINT;
        }

        return extrudeMode;
    }

    private void updateBoundsZ() {
        double minZ = _geomBounds != null ? _geomBounds.minZ : Double.NaN;
        double maxZ = _geomBounds != null ? _geomBounds.maxZ : Double.NaN;

        AltitudeMode mode = (altitudeMode != null) ? altitudeMode
                : AltitudeMode.ClampToGround;
        switch (mode) {
            case ClampToGround:
                // geometry is clamped to ground/always surface; assume maximum and minimum surface
                // altitudes
                minZ = DEFAULT_MIN_ALT;
                maxZ = DEFAULT_MAX_ALT;
                break;
            case Absolute:
                // no additional interpretation
                break;
            case Relative:
                // geometry is relative to terrain; offset from maximum and minimum surface
                // altitudes
                minZ += DEFAULT_MIN_ALT;
                maxZ += DEFAULT_MAX_ALT;
                break;
            default:
                minZ = Double.NaN;
                maxZ = Double.NaN;
                break;
        }

        // apply extrusion. This is not strict, however, it should be
        // sufficient to cover the various permutations.
        if (_hasHeight) {
            maxZ += _height;
        }

        bounds.setMinAltitude(minZ);
        bounds.setMaxAltitude(maxZ);
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
        if (ortho.currentPass.drawMapResolution > _subject.getMetaDouble(
                "maxLineRenderResolution",
                Polyline.DEFAULT_MAX_LINE_RENDER_RESOLUTION))
            return;

        else if (ortho.currentPass.drawMapResolution < _subject.getMetaDouble(
                "minLineRenderResolution",
                Polyline.DEFAULT_MIN_LINE_RENDER_RESOLUTION))
            return;

        // XXX - note for future: tilt for rendered _scene_ can be obtained
        //       from `GLMapView.currentScene.drawTilt`. Leaving as-is for
        //       now to minimize unrelated changes.

        // Check if we need to update the points based on if the map is tilted
        // Since the map tilt is always zero during the surface pass, we have
        // to ignore the check and wait until the sprite/scenes pass
        if (!surface)
            updateNadirClamp(ortho);

        AltitudeMode altitudeMode = getAltitudeMode();

        // Update the line's altitude mode in case we just switched NADIR clamping
        impl.setAltitudeMode(altitudeMode);

        if (_hasHeight && scenes && numPoints > 0 && !_nadirClamp) {

            boolean renderPolygon = MathUtils.hasBits(_heightStyle,
                    Polyline.HEIGHT_STYLE_POLYGON);
            boolean simpleOutline = MathUtils.hasBits(_heightStyle,
                    Polyline.HEIGHT_STYLE_OUTLINE_SIMPLE);
            boolean renderOutline = MathUtils.hasBits(_heightStyle,
                    Polyline.HEIGHT_STYLE_OUTLINE) || simpleOutline;
            boolean topOnly = MathUtils.hasBits(_heightStyle,
                    Polyline.HEIGHT_STYLE_TOP_ONLY);

            int extOptions = GLExtrude.OPTION_NONE;
            if (_closed)
                extOptions |= GLExtrude.OPTION_CLOSED
                        | GLExtrude.OPTION_TRIANGULATE_TOP;
            if (simpleOutline)
                extOptions |= GLExtrude.OPTION_SIMPLIFIED_OUTLINE;
            if (topOnly)
                extOptions |= GLExtrude.OPTION_TOP_ONLY;

            // if terrain is modified
            final int terrainVersion = ortho.getTerrainVersion();
            _shouldReextrude |= (_extrusionTerrainVersion != terrainVersion);
            if (_shouldReextrude) {
                _extrusionTerrainVersion = terrainVersion;
                updatePointsImpl();

                // Find min/max altitude
                boolean clampToGround = altitudeMode == AltitudeMode.ClampToGround;
                double minAlt = Double.MAX_VALUE;
                double maxAlt = -Double.MAX_VALUE;
                double[] alts = new double[origPoints.length];
                for (int i = 0; i < origPoints.length; i++) {
                    GeoPoint gp = origPoints[i];
                    double alt = gp.getAltitude();
                    if (clampToGround || !gp.isAltitudeValid())
                        alt = ortho.getTerrainMeshElevation(gp.getLatitude(),
                                gp.getLongitude());
                    minAlt = Math.min(alt, minAlt);
                    maxAlt = Math.max(alt, maxAlt);
                    alts[i] = alt;
                }

                // Center altitude is meant to be (min + max) / 2 based on
                // how KMLs render relative height
                double centerAlt = (maxAlt + minAlt) / 2;

                int extrudeMode = getExtrudeMode();
                double height = _height;
                double baseAltitude = minAlt;

                if (extrudeMode == Polyline.HEIGHT_EXTRUDE_MAX_ALT)
                    baseAltitude = maxAlt;
                else if (extrudeMode == Polyline.HEIGHT_EXTRUDE_CENTER_ALT)
                    baseAltitude = centerAlt; // KML style

                // Update point buffer with terrain elevations if we're clamped
                if (clampToGround) {
                    // XXX - Dirty hack for ATAK-14494
                    // Use the lowest valid altitude value as the base of the
                    // extrusion
                    if (ortho.currentPass.drawTilt > 0) {
                        Arrays.fill(alts, GeoPoint.MIN_ACCEPTABLE_ALTITUDE);
                        if (extrudeMode == Polyline.HEIGHT_EXTRUDE_PER_POINT)
                            height += baseAltitude
                                    - GeoPoint.MIN_ACCEPTABLE_ALTITUDE;
                    }

                    // Store terrain elevation in point buffer
                    int p = 0;
                    for (double alt : alts) {
                        _points.put(p + 2, alt);
                        p += 3;
                    }
                }

                // Generate height offsets to create flat top/bottom effect
                double[] heights;
                if (extrudeMode != Polyline.HEIGHT_EXTRUDE_PER_POINT) {
                    heights = new double[alts.length];
                    for (int i = 0; i < alts.length; i++)
                        heights[i] = (baseAltitude + height) - alts[i];
                } else
                    heights = new double[] {
                            height
                    };

                if (renderPolygon) {
                    _3dPointsPreForward = GLExtrude.extrudeRelative(
                            Double.NaN, _points, 3, extOptions, heights);

                    _3dPoints = Unsafe.allocateDirect(
                            _3dPointsPreForward.limit(), FloatBuffer.class);
                    _3dPointsPreForward.rewind();

                    final int idlInfo = GLAntiMeridianHelper
                            .normalizeHemisphere(3, _3dPointsPreForward,
                                    _3dPointsPreForward);
                    _3dPointsPreForward.flip();
                    _extrudePrimaryHemi = (idlInfo
                            & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
                    _extrudeCrossesIDL = (idlInfo
                            & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;
                }

                if (renderOutline) {
                    // Extrude the shape outline
                    DoubleBuffer extruded = GLExtrude.extrudeOutline(
                            Double.NaN, _points, 3, extOptions, heights);
                    extruded.rewind();

                    // Normalize IDL crossing
                    final int idlInfo = GLAntiMeridianHelper
                            .normalizeHemisphere(3, extruded,
                                    extruded);
                    extruded.flip();
                    _extrudePrimaryHemi = (idlInfo
                            & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
                    _extrudeCrossesIDL = (idlInfo
                            & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;

                    // Copy and release buffer to regular double array
                    double[] pts = new double[extruded.limit()];
                    extruded.get(pts);
                    Unsafe.free(extruded);

                    // Convert to line string and pass to 3D outline renderer
                    LineString ls = new LineString(3);
                    ls.addPoints(pts, 0, pts.length / 3, 3);
                    _3dOutline.setGeometry(ls);
                }

                _shouldReextrude = false;
            }

            // extrusion vertices (fill+outline) need to be rebuilt when projection changes
            final boolean rebuildExtrusionVertices = (_extrusionCentroidSrid != ortho.currentPass.drawSrid);
            if (rebuildExtrusionVertices) {
                ortho.currentPass.scene.mapProjection
                        .forward(_extrusionCentroid, _extrusionCentroidProj);
                _extrusionCentroidSrid = ortho.currentPass.drawSrid;
            }

            // set up model-view matrix
            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadIdentity();

            ortho.scratch.matrix.set(ortho.currentPass.scene.forward);
            // apply hemisphere shift if necessary
            final double unwrap = GLAntiMeridianHelper.getUnwrap(ortho,
                    _extrudeCrossesIDL, _extrudePrimaryHemi);
            ortho.scratch.matrix.translate(unwrap, 0d, 0d);
            // translate relative-to-center for extrusion geometry
            ortho.scratch.matrix.translate(_extrusionCentroidProj.x,
                    _extrusionCentroidProj.y, _extrusionCentroidProj.z);
            // upload model-view transform
            ortho.scratch.matrix.get(ortho.scratch.matrixD,
                    Matrix.MatrixOrder.COLUMN_MAJOR);
            for (int i = 0; i < 16; i++)
                ortho.scratch.matrixF[i] = (float) ortho.scratch.matrixD[i];
            GLES20FixedPipeline.glLoadMatrixf(ortho.scratch.matrixF, 0);

            GLES20FixedPipeline
                    .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            if (renderPolygon) {
                // validate the render vertices
                if (rebuildExtrusionVertices) {
                    _3dPoints.clear();
                    for (int i = 0; i < _3dPointsPreForward.limit(); i += 3) {
                        final double lng = _3dPointsPreForward.get(i);
                        final double lat = _3dPointsPreForward.get(i + 1);
                        final double alt = _3dPointsPreForward.get(i + 2);
                        ortho.scratch.geo.set(lat, lng, alt);
                        ortho.currentPass.scene.mapProjection.forward(
                                ortho.scratch.geo, ortho.scratch.pointD);
                        _3dPoints.put((float) (ortho.scratch.pointD.x
                                - _extrusionCentroidProj.x));
                        _3dPoints.put((float) (ortho.scratch.pointD.y
                                - _extrusionCentroidProj.y));
                        _3dPoints.put((float) (ortho.scratch.pointD.z
                                - _extrusionCentroidProj.z));
                    }
                    _3dPoints.flip();
                }

                int color = fill ? fillColor : strokeColor;
                float r = Color.red(color) / 255.0f;
                float g = Color.green(color) / 255.0f;
                float b = Color.blue(color) / 255.0f;
                float a = fill ? Color.alpha(color) / 255.0f : 0.5f;

                GLES20FixedPipeline.glColor4f(r, g, b, a);

                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
                GLES30.glPolygonOffset(1.0f, 1.0f);

                GLES20FixedPipeline.glVertexPointer(3,
                        GLES20FixedPipeline.GL_FLOAT, 0, _3dPoints);

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
            if (renderOutline)
                _3dOutline.draw(ortho);

            GLES20FixedPipeline
                    .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glPopMatrix();
        }

        // Altitude mode toggle
        final boolean drawGeom = !(sprites
                && altitudeMode == AltitudeMode.ClampToGround
                || surface && altitudeMode != AltitudeMode.ClampToGround);

        if (drawGeom) {
            if (stroke || (fill && _closed)) {

                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glLoadIdentity();

                if (implInvalid) {
                    updatePointsImpl();
                    impl.setAltitudeMode(altitudeMode);

                    implInvalid = false;
                }
                this.impl.draw(ortho);

                GLES20FixedPipeline.glPopMatrix();
            }
        }
        if (sprites) {
            if (currentDraw != ortho.currentScene.drawVersion)
                recompute = true;
            currentDraw = ortho.currentScene.drawVersion;

            if (this.needsProjectVertices)
                _projectVerts(ortho);

            this.recompute = false;
        }

        validateLabels(ortho);
    }

    /**
     * Sync the NADIR clamp boolean with the current clamp to ground setting
     * @param ortho Map view
     */
    protected void updateNadirClamp(GLMapView ortho) {
        boolean nadirClamp = getClampToGroundAtNadir()
                && Double.compare(ortho.currentPass.drawTilt, 0) == 0;
        if (_nadirClamp != nadirClamp) {
            _nadirClamp = nadirClamp;
            refreshStyle();
            updatePointsImpl();
        }
    }

    private void getRenderPoint(GLMapView ortho, int idx, GeoPoint geo) {
        if (idx < 0 || _points == null || idx >= _points.limit() / _pointsSize)
            return;
        final double lat = _points.get(idx * _pointsSize + 1);
        final double lng = _points.get(idx * _pointsSize);
        geo.set(lat, lng);

        AltitudeMode altitudeMode = getAltitudeMode();

        // source altitude is populated for absolute or relative IF 3 elements specified
        final double alt = (_pointsSize == 3
                && altitudeMode != AltitudeMode.ClampToGround)
                        ? _points.get(idx * _pointsSize + 2)
                        : 0d;
        // terrain is populated for clamp-to-ground or relative
        final double terrain = (altitudeMode != AltitudeMode.Absolute)
                ? ortho.getTerrainMeshElevation(lat, lng)
                : 0d;

        geo.set(alt + terrain);
    }

    private SegmentLabel buildTextLabel(
            GLMapView ortho,
            GeoPoint startGeo,
            GeoPoint endGeo,
            String text) {

        MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                _labelTextSize);
        SegmentLabel lbl = new SegmentLabel(ortho.getLabelManager(), text,
                textFormat, this.visible);

        ortho.getLabelManager().setText(lbl.id, GLText.localize(text));
        lbl.setAltitudeMode(getAltitudeMode());
        lbl.setGeoPoints(startGeo, endGeo);

        return lbl;
    }

    private void validateFloatingLabel(GLMapView ortho) {
        if (_floatingLabel != null && !_floatingLabel.dirty)
            return;
        MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                _labelTextSize);

        if (_floatingLabel == null) {
            _floatingLabel = new SegmentLabel(ortho.getLabelManager(),
                    _lineLabel, textFormat, this.visible);

            double[] points = new double[_points.limit()];
            _points.duplicate().get(points);

            LineString ls = null;
            try {
                ls = new LineString(_pointsSize);
                ls.addPoints(points, 0, _points.limit() / _pointsSize,
                        _pointsSize);
                ortho.getLabelManager().setGeometry(_floatingLabel.id, ls);
            } finally {
                if (ls != null)
                    ls.dispose();
            }

            ortho.getLabelManager().setHints(_floatingLabel.id,
                    ortho.getLabelManager().getHints(_floatingLabel.id)
                            | GLLabelManager.HINT_DUPLICATE_ON_SPLIT);
            _floatingLabel.setAltitudeMode(getAltitudeMode());
        }

        // XXX - refresh text format

        ortho.getLabelManager().setText(_floatingLabel.id,
                GLText.localize(_lineLabel));
        _floatingLabel.text = _lineLabel;
        _floatingLabel.dirty = false;
    }

    /**
     * Display the label on the center of the middle visible segment
     *
     * @param ortho
     */
    private void validateCenterSegmentLabel(GLMapView ortho) {
        // get the text to display, or return if there is none

        final String clt = centerLabelText;

        if (clt == null || clt.length() == 0)
            return;

        if (recompute) {
            middle = findMiddleVisibleSegment(ortho);
        }

        if (middle == -1)
            return;

        GeoPoint lastPoint = GeoPoint.createMutable();
        GeoPoint curPoint = GeoPoint.createMutable();
        getRenderPoint(ortho, middle, lastPoint);
        getRenderPoint(ortho, middle + 1, curPoint);

        /*final float centerLabelWidth = _label.getStringWidth(clt);
        
        // see comment on label code, use a larger number for how this is used.
        if ((Math.abs(startPoint.x - endPoint.x) * 8.0) < centerLabelWidth
                && (Math.abs(startPoint.y - endPoint.y)
                * 8.0) < centerLabelWidth) {
        
            return;
        }*/

        _segmentLabels.add(buildTextLabel(ortho, origPoints[middle],
                origPoints[middle + 1], clt));
    }

    private void validateSegmentLabels(GLMapView ortho) {
        // XXX - support recomputing `middle`
        _segmentLabelsDirty |= recompute;
        if (!_segmentLabelsDirty)
            return;
        _segmentLabelsDirty = false;
        // check for show middle label flag. This will signal to draw the label in the middle
        // visible line segment
        if (_subject.hasMetaValue("centerLabel")) {
            validateCenterSegmentLabel(ortho);
            return;
        }

        if (segmentLabels != null) {
            Map<String, Object> labelBundle;
            int segment;
            String text;
            GeoPoint curPoint = GeoPoint.createMutable();
            GeoPoint lastPoint = GeoPoint.createMutable();
            PointF startPoint = new PointF();
            PointF endPoint = new PointF();
            final double mapGSD = ortho.currentScene.drawMapResolution;
            double minGSD;
            for (Map.Entry e : segmentLabels.entrySet()) {
                labelBundle = (Map<String, Object>) e.getValue();
                Number segNumber = ((Number) labelBundle.get("segment"));
                if (segNumber != null)
                    segment = segNumber.intValue();
                else
                    segment = -1;

                if (segment < 0 || segment >= this.numPoints - 1)
                    continue;

                minGSD = Double.MAX_VALUE;
                if (labelBundle.containsKey("min_gsd")) {
                    Number number = ((Number) labelBundle.get("min_gsd"));
                    if (number != null)
                        minGSD = number.doubleValue();
                }
                if (mapGSD > minGSD)
                    continue;

                text = (String) labelBundle.get("text");

                if (text == null || text.length() == 0)
                    continue;

                getRenderPoint(ortho, segment, lastPoint);
                getRenderPoint(ortho, segment + 1, curPoint);

                // only draw the text if the label fits within the distance between the end points
                // of the segment. This number is multiplied by 2.5 because circles are polylines
                // and it
                // keeps it so that text can be shown when displaying circles.
                // 4 was chosen because of the number of polylines that make up a circle at this
                // time.
                // It would probably be a good idea to construct a GLCircle in the future?
                // XXX - revisit for the next version

                SegmentLabel lbl = buildTextLabel(ortho, origPoints[segment],
                        origPoints[segment + 1], text);

                Rectangle rect = new Rectangle(0, 0, 0, 0);
                ortho.getLabelManager().getSize(lbl.id, rect);
                if (false)
                    if ((Math.abs(startPoint.x - endPoint.x) * 2.5) < rect.Width
                            &&
                            (Math.abs(startPoint.y - endPoint.y)
                                    * 2.5) < rect.Width) {
                        lbl.release();
                        continue;
                    }

                _segmentLabels.add(lbl);
            }
        }
    }

    protected void validateLabels(GLMapView ortho) {

        boolean labelsOn = _subject.hasMetaValue("labels_on");

        if (labelsVersion == ortho.currentScene.drawVersion &&
                this.labelsOnValue == labelsOn)
            return;
        labelsOnValue = labelsOn;
        labelsVersion = ortho.currentScene.drawVersion;

        boolean drawCenterLabel = _subject.hasMetaValue("minRenderScale")
                && Globe.getMapScale(ortho.getSurface().getDpi(),
                        ortho.currentScene.drawMapResolution) >= _subject
                                .getMetaDouble(
                                        "minRenderScale",
                                        DEFAULT_MIN_RENDER_SCALE);

        double minRes = _subject.getMetaDouble("minLabelRenderResolution",
                Polyline.DEFAULT_MIN_LABEL_RENDER_RESOLUTION);
        double maxRes = _subject.getMetaDouble("maxLabelRenderResolution",
                Polyline.DEFAULT_MAX_LABEL_RENDER_RESOLUTION);

        drawCenterLabel |= ortho.currentScene.drawMapResolution > minRes
                && ortho.currentScene.drawMapResolution < maxRes;

        try {
            if (drawCenterLabel && _subject.hasMetaValue("centerPointLabel")) {
                final String _text = _subject.getMetaString(
                        "centerPointLabel", "");
                if (_centerLabel == null) {
                    MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                            _labelTextSize);
                    _centerLabel = new SegmentLabel(ortho.getLabelManager(),
                            _text, textFormat, this.visible);
                    _centerLabel.setGeoPoint(centerPoint);
                } else if (!_centerLabel.text.equals(_text)) {
                    ortho.getLabelManager().setText(_centerLabel.id, _text);
                    _centerLabel.text = _text;
                }
            } else if (_centerLabel != null) {
                _centerLabel.release();
                _centerLabel = null;
            }

            validateSegmentLabels(ortho);
            if (numPoints > 1 && labelsOn) {
                String lineLabel = _subject.getLineLabel();
                if (!FileSystemUtils.isEquals(_lineLabel, lineLabel)) {
                    _lineLabel = lineLabel;
                    if (_floatingLabel != null)
                        _floatingLabel.dirty = true;
                }
                if (!StringUtils.isBlank(_lineLabel))
                    validateFloatingLabel(ortho);
            } else if (_floatingLabel != null) {
                _floatingLabel.release();
                _floatingLabel = null;
            }
        } catch (Exception cme) {
            // catch and ignore - without adding performance penalty to the whole
            // metadata arch. It will clean up on the next draw.
            Log.e(TAG,
                    "concurrent modification of the segment labels occurred during display");
        }
    }

    private static class SegmentLabel {
        int id;
        String text;
        double textAngle;
        boolean visible;
        GLLabelManager _labelManager;
        boolean dirty;

        SegmentLabel(GLLabelManager labelManager, String text,
                MapTextFormat mapTextFormat, boolean visible) {
            this.text = text;
            if (text != null)
                text = GLText.localize(text);
            _labelManager = labelManager;
            id = _labelManager.addLabel(text);
            this.visible = visible;
            _labelManager.setTextFormat(id, mapTextFormat);
            _labelManager.setFill(id, true);
            _labelManager.setBackgroundColor(id, Color.argb(153, 0, 0, 0));
            _labelManager.setVerticalAlignment(id,
                    GLLabelManager.VerticalAlignment.Middle);
            _labelManager.setVisible(id, visible);
        }

        void setGeoPoint(GeoPoint geo) {
            Point geom = new Point(geo.getLongitude(),
                    geo.getLatitude(),
                    geo.getAltitude());
            _labelManager.setGeometry(id, geom);
        }

        void setGeoPoints(GeoPoint start, GeoPoint end) {
            LineString geom = new LineString(3);
            geom.addPoints(new double[] {
                    start.getLongitude(), start.getLatitude(),
                    start.getAltitude(),
                    end.getLongitude(), end.getLatitude(), end.getAltitude(),
            }, 0, 2, 3);
            _labelManager.setGeometry(id, geom);
        }

        void setAltitudeMode(AltitudeMode altMode) {
            _labelManager.setAltitudeMode(id, altMode);
        }

        void setTextAngle(double textAngle) {
            this.textAngle = textAngle;
            _labelManager.setRotation(id, (float) textAngle, false);
        }

        void updateText(String t) {
            if (t == null && text != null ||
                    t != null && !t.equals(text)) {
                _labelManager.setText(id, GLText.localize(t));
                text = t;
            }
        }

        void release() {
            if (id != GLLabelManager.NO_ID) {
                _labelManager.removeLabel(id);
                id = GLLabelManager.NO_ID;
            }
        }

        public void setVisible(boolean visible) {
            if (id != GLLabelManager.NO_ID && visible != this.visible) {
                _labelManager.setVisible(id, visible);
            }
            this.visible = visible;
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
     * TODO: Currently a direct copy from GLMapItem - but will be rewritten / removed when the final
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
        return new RectF(0f, top, right, 0);
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
            int pointCount = this.numPoints + (_closed ? 1 : 0);
            if (_verts2 == null
                    || _verts2.capacity() < pointCount * _verts2Size) {
                // Allocate enough space for the number of points + 1 in case we want to draw a
                // closed polygon
                _verts2 = Unsafe.allocateDirect(pointCount * _verts2Size,
                        FloatBuffer.class);
                _verts2Ptr = Unsafe.getBufferPointer(_verts2);
            }
            _verts2.clear();
        } else if (this.numPoints == 0) {
            Unsafe.free(_verts2);
            _verts2 = null;
            _verts2Ptr = 0L;
        }
    }

    /**
     * TODO 4.4: Utilize the batch line string we already have instead of
     *  creating a duplicate vertex buffer (_verts2). Also should be noted this
     *  buffer is only used for hit testing and drawing vertices in editable mode.
     */
    protected void _projectVerts(final GLMapView ortho) {
        if (recompute && this.numPoints > 0) {
            _ensureVertBuffer();

            if (_closed)
                _verts2.limit(this.numPoints * _verts2Size);

            AltitudeMode altitudeMode = getAltitudeMode();

            // If we're using ground-based altitude we need to pass in the
            // terrain elevation here so the forward result is correct
            if (_pointsSize == 3 && altitudeMode != AltitudeMode.Absolute) {
                int pIdx = 2;
                for (int i = 0; i < this.numPoints; i++) {
                    GeoPoint gp = origPoints[i];
                    double alt = gp.getAltitude();
                    double terrain = ortho.getTerrainMeshElevation(
                            gp.getLatitude(), gp.getLongitude());
                    if (altitudeMode == AltitudeMode.ClampToGround)
                        alt = terrain;
                    else if (altitudeMode == AltitudeMode.Relative)
                        alt = terrain
                                + (GeoPoint.isAltitudeValid(alt) ? alt : 0);
                    _points.put(pIdx, alt);
                    pIdx += 3;
                }
            }

            // Forward points to vertices
            _points.limit(numPoints * _pointsSize);
            _verts2.limit(numPoints * _verts2Size);
            ortho.forward(_points, _pointsSize, _verts2, _verts2Size);

            // close the line if necessary
            if (_closed) {
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
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {

        // Code that can be used to perform hit detection on the extruded part
        // of the shape
        // Disabled for now - Results in wonky behavior when editing.
        // Possibly requires a new metadata field or correction to properly
        // move the top outline of the shape relative to its bottom point
        /*final int hs = _heightStyle;
        if (_hasHeight && !_nadirClamp && (
                MathUtils.hasBits(hs, Polyline.HEIGHT_STYLE_OUTLINE_SIMPLE) ||
                MathUtils.hasBits(hs, Polyline.HEIGHT_STYLE_OUTLINE))) {
            result = _3dOutline.hitTest(renderer, params);
            if (result != null) {
                int count2 = numPoints * 2;
                if (result.index >= count2)
                    result.index -= count2;
                result.index /= 2;
            }
        }*/

        HitTestResult result = impl.hitTest(renderer, params);
        if (result == null)
            return null;

        // Check if we touched the last point in the closed shape
        if (_closed && result.index == this.numPoints) {
            // Redirect to the first point in the shape
            result.index = 0;
            result.count = 1;
        }

        return new HitTestResult(_subject, result);
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

                // force labels to be rebuilt with new format
                removeLabels();
            }
        });
    }

    @Override
    public void release() {
        removeLabels();

        super.release();

        impl.release();
        implInvalid = true;
        Unsafe.free(_verts2);
        _verts2 = null;
        _verts2Ptr = 0L;

        needsProjectVertices = true;
        freeExtrusionBuffers();
        _shouldReextrude = true;
        _extrusionCentroidSrid = -1;
        _extrusionTerrainVersion = -1;
    }

    private void removeLabels() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_centerLabel != null) {
                    _centerLabel.release();
                    _centerLabel = null;
                }
                if (_floatingLabel != null) {
                    _floatingLabel.release();
                    _floatingLabel = null;
                }
                for (SegmentLabel lbl : _segmentLabels)
                    lbl.release();
                _segmentLabels.clear();
                _segmentLabelsDirty = true;

                labelsVersion = -1;
            }
        });
    }
}
