package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.PatternStrokeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.opengl.GLAntiMeridianHelper;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.hittest.HitRect;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.map.opengl.GLAntiAliasedLine;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.Tessellate;

public class GLBatchLineString extends GLBatchGeometry {

    final static RenderState DEFAULT_RS = new RenderState();
    static {
        DEFAULT_RS.strokeWidth = 1.0f;
        DEFAULT_RS.strokeColorR = 1.0f;
        DEFAULT_RS.strokeColorG = 1.0f;
        DEFAULT_RS.strokeColorB = 1.0f;
        DEFAULT_RS.strokeColorA = 1.0f;
        DEFAULT_RS.strokeColor = 0xFFFFFFFF;
        DEFAULT_RS.pattern = (short)0xFFFF;
        DEFAULT_RS.factor = 1;
    }
    private final static String TAG = "GLLineString";

    protected double threshold = 1_250_000D;
    protected double thresholdxyz = GLMapView.recommendedGridSampleDistance;
    private int partitionSize = 25;

    /** the source points, xyz triplets */
    protected DoubleBuffer points;
    /** the source point count */
    protected int numPoints;

    // These are 3D vertices relative to the center of the earth, NOT screen vertices
    FloatBuffer vertices;
    long verticesPtr;
    int vertexType;
    int projectedVerticesSrid;
    int verticesDrawVersion;
    int terrainVersion;

    /**
     * The points to be rendered, tessellated if necessary. 'z' values will be
     * populated with actual and should be adjusted when converting to vertices
     */
    DoubleBuffer renderPoints;
    Envelope renderBounds;
    /** the render point count */
    int numRenderPoints;
    int renderPointsDrawMode = GLES30.GL_LINE_STRIP;

    // The index of each render point that matches an original point (null if same)
    protected IntBuffer renderPointIndices;

    DoubleBuffer polyTriangles;
    FloatBuffer polyVertices;

    RenderState[] renderStates;
    
    Envelope mbb;

    PointD centroidProj = new PointD(0d, 0d, 0d);

    boolean crossesIDL;
    int primaryHemi;
    private boolean needsTessellate;
    protected boolean tessellated;
    protected boolean tessellatable;
    private boolean tessellationEnabled;
    private Tessellate.Mode tessellationMode;

    protected AltitudeMode altitudeMode;
    protected double extrude = 0d;

    private boolean _aalineDirty;
    private GLAntiAliasedLine _lineRenderer;

    protected boolean hasBeenExtruded;
    protected boolean _hasInnerRings;

    protected int[] _startIndices;
    protected int[] _numVertices;
    protected int _numPolygons = 1;

    // These are the line vertices projected to the screen
    // Used for hit testing non-surface lines
    private FloatBuffer _screenVertices;
    private int _screenVerticesVersion;

    // Screen hit testing (non-surface)
    private final HitRect _screenRect = new HitRect();
    private final List<HitRectPartition> _partitionRects = new ArrayList<>();

    // Surface line hit-testing
    private final MutableGeoBounds _hitBounds = new MutableGeoBounds();
    private final List<GeoBoundsPartition> _partitionBounds = new ArrayList<>();

    public GLBatchLineString(GLMapSurface surface) {
        this(surface.getGLMapView());
    }

    public GLBatchLineString(MapRenderer surface) {
        this(surface, 1);
    }

    protected GLBatchLineString(MapRenderer surface, int zOrder) {
        super(surface, zOrder);

        this.renderStates = null;
        
        this.points = null;
        this.vertices = null;
        this.verticesPtr = 0L;
        this.verticesDrawVersion = -1;
        this.projectedVerticesSrid = -1;
        this.numPoints = 0;
        this.numRenderPoints = 0;
        this.renderPoints = null;
        
        this.mbb = new Envelope(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

        this.altitudeMode = AltitudeMode.ClampToGround;

        this.needsTessellate = false;
        this.tessellated = false;
        this.tessellationEnabled = true;
        this.tessellationMode = Tessellate.Mode.WGS84;
        _aalineDirty = true;
        _hasInnerRings = false;
    }

    @Override
    public void setAltitudeMode(AltitudeMode altitudeMode) {
        this.altitudeMode = altitudeMode;
    }

    protected AltitudeMode getAltitudeMode() {
        return isNadirClampEnabled() ? AltitudeMode.ClampToGround : altitudeMode;
    }

    @Override
    public void setExtrude(double value) {
        this.extrude = value;
    }

    public boolean isExtruded() {
        return !isNadirClampEnabled() && Double.compare(this.extrude, 0.0) != 0;
    }

    public void setTessellationEnabled(boolean e) {
        this.tessellationEnabled = e;
    }

    public void setTessellationMode(Tessellate.Mode mode) {
        this.tessellationMode = mode;
    }

    @Override
    public void setStyle(final Style style) {
        if(renderContext.isRenderThread())
            setStyle(style, true);
        else
            renderContext.queueEvent(new Runnable() {
                public void run() {
                    setStyle(style, true);
                }
            });
    }

    void setStyle(final Style style, final boolean convertTransparentFillToOpaque) {
        final boolean wasFill = hasFill();
        final List<RenderState> states;
        if(style instanceof CompositeStyle)
            states = new ArrayList<>(((CompositeStyle)style).getNumStyles());
        else
            states = new ArrayList<>(1);
        getRenderStates(this.renderContext, style, states, convertTransparentFillToOpaque);

        if(states.isEmpty())
            this.renderStates = null;
        else
            this.renderStates = states.toArray(new RenderState[0]);
        final boolean isFill = hasFill();

        hasBeenExtruded &= (wasFill==isFill);
    }

    /**
     *
     * @param ctx
     * @param s
     * @param states
     * @param convertTransFillToOpaque  If <code>true</code> fill values with
     *                                  an alpha value less-than-or-equal-to
     *                                  zero will be interpreted to be opaque
     */
    private static void getRenderStates(MapRenderer ctx, Style s, List<RenderState> states, boolean convertTransFillToOpaque) {
        if(s instanceof CompositeStyle) {
            CompositeStyle c = (CompositeStyle)s;
            final int numStyles = c.getNumStyles();
            // XXX - stroke definition should reside as a single style
            //       definition. The composition based approach offers some
            //       nice flexibility, but consumption and rendering would
            //       be much better served with a unified representation.

            final int olen = states.size();
            for (int i = 0; i < numStyles; i++)
                getRenderStates(ctx, c.getStyle(i), states, convertTransFillToOpaque);

            // Consolidate fill color with matching stroke color
            int fillColorIdx = -1;
            RenderState stroke = null;
            for (int i = olen; i < states.size(); i++) {
                RenderState rs = states.get(i);
                if (rs.fillColor != 0)
                    fillColorIdx = i;
                else if (rs.strokeColor != 0)
                    stroke = rs;
            }
            if (fillColorIdx != -1 && stroke != null) {
                RenderState f = states.remove(fillColorIdx);
                stroke.fillColor = f.fillColor;
                stroke.fillColorR = f.fillColorR;
                stroke.fillColorG = f.fillColorG;
                stroke.fillColorB = f.fillColorB;
                stroke.fillColorA = f.fillColorA;
            }

            // look for outline emulation
            if(states.size() == olen+2) {
                final RenderState a = states.get(olen);
                final RenderState b = states.get(olen+1);
                if(a.pattern == b.pattern // Same pattern
                        && a.factor == b.factor // Same "factor"
                        && a.fillColor == 0f && b.fillColor == 0f // Neither have a fill
                        && a.strokeColorA > 0f && b.strokeColorA > 0f // Both stroke colors visible
                        && a.outlineWidth == 0f && b.outlineWidth == 0f // Neither have outlines
                        && a.strokeWidth > b.strokeWidth) { // A must be wider than B

                    RenderState outlined = new RenderState();
                    // stroke
                    outlined.strokeWidth = b.strokeWidth;
                    outlined.strokeColor = b.strokeColor;
                    outlined.strokeColorR = b.strokeColorR;
                    outlined.strokeColorG = b.strokeColorG;
                    outlined.strokeColorB = b.strokeColorB;
                    outlined.strokeColorA = b.strokeColorA;
                    // outline
                    outlined.outlineWidth = (a.strokeWidth-b.strokeWidth) / 2f;
                    outlined.outlineColor = a.strokeColor;
                    outlined.outlineColorR = a.strokeColorR;
                    outlined.outlineColorG = a.strokeColorG;
                    outlined.outlineColorB = a.strokeColorB;
                    outlined.outlineColorA = a.strokeColorA;
                    // pattern
                    outlined.pattern = b.pattern;
                    outlined.factor = b.factor;

                    states.remove(states.size()-1);
                    states.set(states.size()-1, outlined);
                }
            }
        } else if(s instanceof BasicStrokeStyle) {
            BasicStrokeStyle basicStroke = (BasicStrokeStyle) s;
            RenderState rs = new RenderState();
            rs.strokeWidth = basicStroke.getStrokeWidth();
            rs.strokeColor = basicStroke.getColor();
            rs.strokeColorR = Color.red(rs.strokeColor) / 255f;
            rs.strokeColorG = Color.green(rs.strokeColor) / 255f;
            rs.strokeColorB = Color.blue(rs.strokeColor) / 255f;
            rs.strokeColorA = Color.alpha(rs.strokeColor) / 255f;
            states.add(rs);
        } else if (s instanceof BasicFillStyle) {
            BasicFillStyle bs = (BasicFillStyle) s;
            RenderState rs = new RenderState();
            rs.fillColor = bs.getColor();
            rs.fillColorR = Color.red(rs.fillColor) / 255f;
            rs.fillColorG = Color.green(rs.fillColor) / 255f;
            rs.fillColorB = Color.blue(rs.fillColor) / 255f;
            rs.fillColorA = Color.alpha(rs.fillColor) / 255f;
            if (rs.fillColorA <= 0 && convertTransFillToOpaque)
                rs.fillColorA = 1f;
            states.add(rs);
        } else if(s instanceof PatternStrokeStyle) {
            final PatternStrokeStyle ps = (PatternStrokeStyle) s;
            final RenderState rs = new RenderState();
            rs.strokeWidth = ps.getStrokeWidth();
            rs.strokeColor = ps.getColor();
            rs.strokeColorR = Color.red(rs.strokeColor) / 255f;
            rs.strokeColorG = Color.green(rs.strokeColor) / 255f;
            rs.strokeColorB = Color.blue(rs.strokeColor) / 255f;
            rs.strokeColorA = Color.alpha(rs.strokeColor) / 255f;
            rs.factor = ps.getFactor();
            rs.pattern = (short)ps.getPattern();
            states.add(rs);
        }
    }

    @Override
    protected void setGeometryImpl(final ByteBuffer blob, final int type) {
        hasBeenExtruded = false;
        setGeometryImpl(blob, type, null);
    }
    
    public void setGeometry(final LineString linestring) {
        this.setGeometry(linestring, -1);
    }
    
    @Override
    protected void setGeometryImpl(Geometry geometry) {
        hasBeenExtruded = false;
        setGeometryImpl(null, -1, (LineString) geometry);
    }

    private void setGeometryImpl(ByteBuffer blob, int type, LineString ls) {
        setGeometryImpl(blob, type, ls, 1);
    }

    protected void setGeometryImpl(ByteBuffer blob, int type, LineString ls, int numRings) {
        if (blob == null && ls == null)
            return;

        final int numPoints, skip;
        final boolean compressed;
        final int dim;
        _hasInnerRings = numRings > 1;
        if (blob != null) {
            // hi == 1 for Z
            // hi == 2 for M
            // hi == 3 for ZM
            compressed = ((type / 1000000) == 1);
            final int hi = ((type / 1000) % 1000);
            final int size = 2 + (hi/2) + (hi%2);
            if (hi == 0 || hi == 2)
                dim = 2;
            else if (hi == 1 || hi == 3)
                dim = 3;
            else
                throw new IllegalStateException();

            numPoints = blob.getInt();
            final int maxPoints;
            if (!compressed)
                maxPoints = blob.remaining() / (size * 8);
            else
                maxPoints = 1 + ((blob.remaining() - (size * 8)) / (size * 4));

            // XXX - really not sure why this is happening, but sometimes the
            //       simplified geometry appears to come back with a bad number of
            //       points and point data. if we detect this situation, report it
            //       and quietly retain the previous (valid) geometry
            if (numPoints > maxPoints) {
                Log.w(TAG, "Invalid simplified geometry for " + name + "; field=" + numPoints
                        + " available=" + maxPoints);
                return;
            }
            // skipping 'M'
            skip = MathUtils.hasBits(hi, 0x2) ? (compressed ? 4 : 8) : 0;
        } else {
            dim = ls.getDimension();
            numPoints = ls.getNumPoints();
            skip = 0;
            compressed = false;
        }

        if (_hasInnerRings && blob != null) {
            blob.position(blob.position() - 4);
            _extractRings(blob, compressed, dim, numRings);

            if(numPoints > 0) {
                double x, y;
                x = points.get(0);
                y = points.get(1);
                this.mbb.minX = x;
                this.mbb.maxX = x;
                this.mbb.minY = y;
                this.mbb.maxY = y;
                for (int i = 1; i < this.numPoints; i++) {
                    x = points.get(i*3);
                    y = points.get(i*3+1);
                    if (x < this.mbb.minX)
                        this.mbb.minX = x;
                    else if (x > this.mbb.maxX)
                        this.mbb.maxX = x;
                    if (y < this.mbb.minY)
                        this.mbb.minY = y;
                    else if (y > this.mbb.maxY)
                        this.mbb.maxY = y;
                }
            } else {
                mbb.minX = Double.NaN;
                mbb.minY = Double.NaN;
                mbb.minZ = Double.NaN;
                mbb.maxX = Double.NaN;
                mbb.maxY = Double.NaN;
                mbb.maxZ = Double.NaN;
            }
        } else {
            _allocatePoints(numPoints);
            _startIndices = new int[] {0};
            _numVertices = new int[] {numPoints - 1}; // ignore the last point since it's a duplicate of the first


        // while iterating points, obtain rough estimate of max inter-point
        // distance to determine if tessellation should be performed
        this.tessellatable = false;

        this.crossesIDL = false;
        this.primaryHemi = 0;

            if (this.numPoints > 0) {
            final double thresholdSq;
            final boolean tessellateWgs84 = this.tessellationMode == Tessellate.Mode.WGS84;
                if (tessellateWgs84)
                thresholdSq = threshold * threshold;
            else
                thresholdSq = thresholdxyz * thresholdxyz;

            long pointsPtr = Unsafe.getBufferPointer(this.points);
            int pointsPos = 0;

            double x = blob != null ? blob.getDouble() : ls.getX(0);
            double y = blob != null ? blob.getDouble() : ls.getY(0);
            double z = (dim > 2) ? (blob != null ? blob.getDouble() : ls.getZ(0)) : 0d;

            if (skip > 0)
                blob.position(blob.position() + skip);

            Unsafe.setDoubles(pointsPtr + pointsPos, x, y, z);
            pointsPos += 24;

            this.mbb.minX = x;
            this.mbb.maxX = x;
            this.mbb.minY = y;
            this.mbb.maxY = y;
                double[] point = new double[]{x, y, z, 0, 0};
            for (int i = 1; i < this.numPoints; i++) {
                    _extractPoint(blob, ls, i, compressed, dim, point);
                    x = point[0];
                    y = point[1];
                    z = point[2];
                    double dx = point[3], dy = point[4];

                // approximate different in lat,lng between current and last point
                // as meters. update tessellation required flag
                    if (tessellateWgs84) {
                    dx *= GeoCalculations.approximateMetersPerDegreeLongitude(y - dy / 2d);
                    dy *= GeoCalculations.approximateMetersPerDegreeLatitude(y - dy / 2d);
                }

                this.tessellatable |= (((dx * dx) + (dy * dy)) > thresholdSq);

                Unsafe.setDoubles(pointsPtr + pointsPos, x, y, z);
                pointsPos += 24;

                if (x < this.mbb.minX)
                    this.mbb.minX = x;
                else if (x > this.mbb.maxX)
                    this.mbb.maxX = x;
                if (y < this.mbb.minY)
                    this.mbb.minY = y;
                else if (y > this.mbb.maxY)
                    this.mbb.maxY = y;

                if (skip > 0)
                    blob.position(blob.position() + skip);
            }

            this.points.limit(pointsPos / 8);

            final int idlInfo = GLAntiMeridianHelper.normalizeHemisphere(3, this.points, this.points);
            this.points.flip();

                this.primaryHemi = (idlInfo & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
                this.crossesIDL = (idlInfo & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;
        } else {
            this.points.limit(0);
        }
        }

        // force tessellation sync
        this.tessellated = !this.needsTessellate;

        // Invalidate screen vertices version for hit-testing
        _screenVerticesVersion = -1;

        this.validateGeometry();
        _aalineDirty = true;
    }

    /**
     * Allocate the points buffer with the given number of points.
     * @param numPoints The number of points to allocate.
     */
    private void _allocatePoints(int numPoints) {
        this.numPoints = numPoints;

        int limit = numPoints * 3;
        if (this.points == null || this.points.capacity() < limit) {
            if (this.renderPoints == this.points)
                this.renderPoints = null;
            Unsafe.free(this.points);

            this.points = Unsafe.allocateDirect(limit, DoubleBuffer.class);
        }
        this.points.clear();
        this.points.limit(limit);
    }

    protected boolean validateGeometry() {
        if(this.points != null && ((this.needsTessellate != this.tessellated) || (this.renderPoints == null))) {
            this.points.rewind();

            // Free/reset buffers
            if(this.renderPoints != this.points)
                Unsafe.free(this.renderPoints);
            Unsafe.free(this.renderPointIndices);
            this.renderPointIndices = null;
            this.renderPoints = this.points;
            this.numRenderPoints = this.numPoints;
            this.renderPointsDrawMode = GLES30.GL_LINE_STRIP;

            if(this.numPoints > 0 && this.needsTessellate && this.tessellatable) {
                Buffer result = Tessellate.linestring(Double.TYPE,
                                                      this.points,
                                                      24,
                                                      3,
                                                      this.numPoints,
                                                      this.tessellationMode == Tessellate.Mode.WGS84 ? threshold : thresholdxyz,
                                                      this.tessellationMode == Tessellate.Mode.WGS84);
                if(result != this.points) {
                    // tessellation occurred
                    this.renderPoints = ((ByteBuffer) result).asDoubleBuffer();
                    this.numRenderPoints = this.renderPoints.limit() / 3;

                    // For hit testing we need to know which point indices map to which original point index
                    // XXX - It'd be more efficient if the tessellate function took care of this as well
                    this.renderPointIndices = Unsafe.allocateDirect(this.numPoints, IntBuffer.class);
                    int destPtIdx = 0, rpiLimit = 0;
                    int srcLimit = this.numPoints * 3;
                    int dstLimit = this.numRenderPoints * 3;
                    for (int i = 0; i < srcLimit; i += 3) {

                        // Source (original) point
                        double srcLat = points.get(i+1);
                        double srcLng = points.get(i);

                        for (int j = destPtIdx * 3; j < dstLimit; j += 3, destPtIdx++) {

                            // Output (tessellated) point
                            double dstLat = renderPoints.get(j+1);
                            double dstLng = renderPoints.get(j);

                            // If the tessellated point matches the original
                            // then record the index
                            if (Double.compare(srcLat, dstLat) == 0
                                    && Double.compare(srcLng, dstLng) == 0) {
                                this.renderPointIndices.put(destPtIdx++);
                                rpiLimit++;
                                break;
                            }
                        }
                    }
                    this.renderPointIndices.clear();
                    this.renderPointIndices.limit(rpiLimit);
                }
            }

            // allocate/grow 'vertices' if necessary
            if(this.vertices == null || this.vertices.capacity() / 3 < (this.numRenderPoints)) {
                if(this.vertices != null)
                    Unsafe.free(this.vertices);

                this.vertices = Unsafe.allocateDirect(this.numRenderPoints * 3, FloatBuffer.class);
                this.verticesPtr = Unsafe.getBufferPointer(this.vertices);
            }

            // Update render bounds
            Envelope.Builder eb = new Envelope.Builder();
            eb.setHandleIdlCross(false);

            Envelope.Builder ebPart = new Envelope.Builder();
            ebPart.setHandleIdlCross(false);

            int partIdx = 1;
            int partCount = 0;
            int idx = 0, startIndex = 0;
            for(int i = 0; i < numRenderPoints * 3; i += 3) {
                double lat = renderPoints.get(i+1);
                double lng = renderPoints.get(i);
                double hae = renderPoints.get(i+2);
                eb.add(lng, lat, hae);

                // Update partition bounding rectangle
                ebPart.add(lng, lat, hae);

                if (partIdx == partitionSize || idx == numRenderPoints - 1) {
                    Envelope e = ebPart.build();
                    GeoBoundsPartition gbp = partCount < _partitionBounds.size()
                            ? _partitionBounds.get(partCount)
                            : new GeoBoundsPartition();
                    gbp.set(e.minY, e.minX, e.maxY, e.maxX);
                    gbp.setMinAltitude(e.minZ);
                    gbp.setMaxAltitude(e.maxZ);
                    gbp.startIndex = startIndex;
                    gbp.endIndex = idx;
                    if (partCount >= _partitionBounds.size())
                        _partitionBounds.add(gbp);
                    partCount++;
                    partIdx = 0;
                    startIndex = idx;
                    ebPart.reset();
                    ebPart.add(lng, lat, hae);
                }
                partIdx++;
                idx++;
            }
            while (partCount < _partitionBounds.size())
                _partitionBounds.remove(partCount);

            this.renderBounds = eb.build();

            if (this.renderBounds != null) {

                // if the geometry crosses the IDL, the minimum and maximum will be
                // reversed and need to be wrapped
                if (this.crossesIDL) {
                    final double minX = this.renderBounds.minX;
                    final double maxX = this.renderBounds.maxX;
                    this.renderBounds.minX = GeoCalculations.wrapLongitude(maxX);
                    this.renderBounds.maxX = GeoCalculations.wrapLongitude(minX);
                }

                // Hit testing data
                _hitBounds.set(this.renderBounds.minY, this.renderBounds.minX,
                        this.renderBounds.maxY, this.renderBounds.maxX);
                _hitBounds.setMinAltitude(this.renderBounds.minZ);
                _hitBounds.setMaxAltitude(this.renderBounds.maxZ);
            } else {
                _hitBounds.clear();
            }

            this.tessellated = this.needsTessellate;

            this.vertices.clear();

            // force reprojection
            this.projectedVerticesSrid = -1;
            this.verticesDrawVersion = -1;
            this.vertexType = -1;
            _aalineDirty = true;

            return true;
        }

        return false;
    }

    public Envelope getBounds(int srid) {
        this.needsTessellate |= srid == 4978 && this.tessellationEnabled;
        this.validateGeometry();
        return this.renderBounds;
    }

    public void setTesselationThreshold(double threshold) {
        this.threshold = threshold;
        this.thresholdxyz = threshold / 10_000_000D;
    }

    private AltitudeMode extrudeGeometry(GLMapView view) {
        AltitudeMode altMode = isExtruded() ? AltitudeMode.Absolute : getAltitudeMode();
        if(isExtruded() && !hasBeenExtruded) {
            // XXX - recompute render points as extrusion outline
            int last = points.limit() - 3;
            boolean closed = points.get(0) == points.get(last)
                    && points.get(1) == points.get(last + 1)
                    && points.get(2) == points.get(last + 2);
            hasBeenExtruded = true;

            DoubleBuffer extPoints = Unsafe.allocateDirect(this.numPoints*3,
                    DoubleBuffer.class);
            extPoints.put(points.duplicate());

            // Fetch terrain elevation for each point and find the minimum
            double minAlt = Double.MAX_VALUE;
            double maxAlt = -Double.MAX_VALUE;
            for (int i = 0; i < points.limit(); i+= 3) {
                double lng = points.get(i);
                double lat = points.get(i + 1);
                double alt = view.getTerrainMeshElevation(lat, lng);
                extPoints.put(i + 2, alt);
                minAlt = Math.min(minAlt, alt);
                maxAlt = Math.max(maxAlt, alt);
            }

            double centerAlt = (maxAlt + minAlt) / 2;

            // Calculate relative height per point
            int h = 0;
            double[] heights = new double[points.limit() / 3];
            for (int i = 0; i < points.limit(); i+= 3) {
                // Height/top altitude (depending on altitude mode)
                double height = points.get(i + 2);

                // (Minimum altitude + height) - point elevation
                double alt = extPoints.get(i + 2);
                heights[h++] = altitudeMode == AltitudeMode.Absolute
                        ? height - alt : (centerAlt + height) - alt;
            }

            extrudeGeometryImpl(view, extPoints, heights, closed);
            Unsafe.free(extPoints);

            // vertices are now invalid
            projectedVerticesSrid = -1;
            verticesDrawVersion = -1;
        }
        return altMode;
    }

    /**
     *
     * @param view
     * @param extPoints
     * @param closed    <code>true</code> if the linestring is a closed ring
     * @return
     */
    void extrudeGeometryImpl(GLMapView view, DoubleBuffer extPoints, double[] heights, boolean closed) {
        // `renderPoints` will hold the extruded linestring
        if(renderPoints != this.points)
            Unsafe.free(renderPoints);
        renderPoints = GLExtrude.extrudeOutline(Double.NaN, extPoints, 3, closed, false, heights);
        renderPoints.rewind();
        numRenderPoints = renderPoints.limit() / 3;
        // extrusion is always `GL_LINES`
        renderPointsDrawMode = GLES30.GL_LINES;

        Unsafe.free(polyTriangles);
        polyTriangles = null;

        if(hasFill()) {
            polyTriangles = GLExtrude.extrudeRelative(Double.NaN, extPoints, 3, closed, false, heights);
            polyTriangles.rewind();
        }
    }

    boolean hasFill() {
        if(renderStates == null)
            return false;
        for(RenderState rs : renderStates)
            if(rs.fillColorA > 0f)
                return true;
        return false;
    }

    boolean projectVertices(GLMapView view, int vertices) {
        if(this.points == null || this.numPoints < 2) {
            final boolean cleared = this.vertices != null;
            Unsafe.free(this.vertices);
            this.vertices = null;
            this.vertexType = vertices;
            return cleared;
        }

        // tessellation is required if using spheroid geometry model and the
        this.needsTessellate = needsTessellate(view, this);
        this.validateGeometry();

        final boolean terrainValid = isTerrainValid(view);

        // re-extrusion required if terrain is updated
        hasBeenExtruded &= terrainValid;

        // extrude as necessary
        AltitudeMode altMode = extrudeGeometry(view);

        // validate buffers
        if(this.vertices == null || (this.vertices.capacity() < renderPoints.limit())) {
            Unsafe.free(this.vertices);
            this.vertices = Unsafe.allocateDirect(renderPoints.limit(), FloatBuffer.class);

            verticesDrawVersion = -1;
            projectedVerticesSrid = -1;
        }
        this.vertices.clear();
        this.vertices.limit(renderPoints.limit());

        if(polyTriangles != null) {
            if (this.polyVertices == null || (this.polyVertices.capacity() < polyTriangles.limit())) {
                Unsafe.free(polyVertices);
                this.polyVertices = Unsafe.allocateDirect(polyTriangles.limit(), FloatBuffer.class);

                verticesDrawVersion = -1;
                projectedVerticesSrid = -1;
            }
            this.polyVertices.clear();
            this.polyVertices.limit(polyTriangles.limit());
        }

        boolean retval = false;

        // project the vertices
        double unwrap = GLAntiMeridianHelper.getUnwrap(view, this.crossesIDL, this.primaryHemi);
        switch(vertices) {
            case GLGeometry.VERTICES_PIXEL :
                if(this.verticesDrawVersion != view.drawVersion
                        || this.vertexType != vertices
                        || !terrainValid) {

                    projectVerticesImpl(view,
                            this.renderPoints,
                            this.numRenderPoints,
                            vertices,
                            altMode,
                            unwrap,
                            this.vertices,
                            centroidProj);

                    if(polyTriangles != null) {
                        projectVerticesImpl(view,
                                this.polyTriangles,
                                this.polyTriangles.limit() / 3,
                                vertices,
                                altMode,
                                unwrap,
                                this.polyVertices,
                                centroidProj);
                    }
                    retval = true;
                }
                this.verticesDrawVersion = view.drawVersion;

                break;
            case GLGeometry.VERTICES_PROJECTED :
                // no unwrap is applied now, it will be applied as part of the
                // transform stack at draw time
                unwrap = 0;
            case GLGeometry.VERTICES_BATCH :
                if(view.drawSrid != this.projectedVerticesSrid
                        || this.vertexType != vertices
                        || !terrainValid) {

                    projectVerticesImpl(view,
                                        this.renderPoints,
                                        this.numRenderPoints,
                                        vertices,
                                        altMode,
                                        unwrap,
                                        this.vertices,
                                        centroidProj);
                    if(polyTriangles != null) {
                        projectVerticesImpl(view,
                                this.polyTriangles,
                                this.polyTriangles.limit() / 3,
                                vertices,
                                altMode,
                                unwrap,
                                this.polyVertices,
                                centroidProj);
                    }
                    this.projectedVerticesSrid = view.drawSrid;
                    retval = true;
                }
                break;
            default :
                throw new IllegalArgumentException();
        }
        
        this.vertexType = vertices;
        return retval;
    }

    /**
     * Extracts a single point and its delta values from the given spatiaLite blob or LineString.
     * @param blob The spatiaLite blob containing the points to be extracted, may be null.
     * @param ls The LineString containing the point data, may be null.
     * @param i The index that the point should be extracted from, only used if ls is null.
     * @param compressed If true then the spatiaLite blob is compressed.
     * @param dim The dimmensions of the point, either 2 or 3.
     * @param point The array of the point data and its x and y delta values [x, y, z, dx, dy].
     */
    private void _extractPoint(ByteBuffer blob, LineString ls, int i, boolean compressed, int dim, double[] point) {
        int x = 0, y = 1, z = 2;
        int dx = 3, dy = 4;
        if (compressed) {
            point[dx] = blob.getFloat();
            point[dy] = blob.getFloat();
            double dz = 0d;
            if (dim > 2)
                dz = blob.getFloat();
            point[x] += point[dx];
            point[y] += point[dy];
            point[z] += dz;
        } else if (blob != null) {
            final double nx = blob.getDouble();
            final double ny = blob.getDouble();
            point[dx] = nx - x;
            point[dy] = ny - y;
            point[x] = nx;
            point[y] = ny;
            point[z] = (dim > 2) ? blob.getDouble() : 0d;
        } else {
            final double nx = ls.getX(i);
            final double ny = ls.getY(i);
            point[dx] = nx - x;
            point[dy] = ny - y;
            point[x] = nx;
            point[y] = ny;
            point[z] = (dim > 2) ? ls.getZ(i) : 0d;
        }
    }

    private boolean _ringIsNotClosed(ArrayList<Double> ring) {
        int lastPoint = ring.size() - 3;
        return (!ring.get(0).equals(ring.get(lastPoint))) || (!ring.get(1).equals(ring.get(lastPoint + 1)));
    }

    /**
     * Extracts the rings of the polygon contained in the spatiaLite blob and stores them in the points buffer.
     * @param blob The spatiaLite blob that contains the polygon.
     * @param compressed If true, then the spatiaLite blob contains a compressed polygon.
     * @param dim The number of dimensions each point has, either 2 or 3.
     * @param numRings The number of rings the polygon has.
     */
    private void _extractRings(ByteBuffer blob, boolean compressed, int dim, int numRings) {
        int numberOfPoints = 0;
        ArrayList<ArrayList<Double>> rings = new ArrayList<>();
        _numPolygons = numRings;
        _startIndices = new int[numRings];
        _numVertices = new int[numRings];
        // Extract the rings from the blob
        for (int i = 0; i < numRings; i++) {
            int ringSize = blob.getInt();
            ArrayList<Double> ring = new ArrayList<>();
            ring.add(blob.getDouble());
            ring.add(blob.getDouble());
            ring.add(dim > 2 ? blob.getDouble() : 0);
            double[] point = new double[]{ring.get(0), ring.get(1), ring.get(2), 0, 0};
            for (int p = 1; p < ringSize; p++) {
                _extractPoint(blob, null, 0, compressed, dim, point);
                ring.add(point[0]);
                ring.add(point[1]);
                ring.add(point[2]);
            }
            numberOfPoints += ring.size() / 3;
            if (_ringIsNotClosed(ring)) {
                _numVertices[i] = (ring.size() / 3) - 1;
                _startIndices[i] = numberOfPoints - (_numVertices[i] + 1);
            } else {
                _numVertices[i] = ring.size() / 3;
                _startIndices[i] = numberOfPoints - _numVertices[i];
            }
            rings.add(ring);
        }
        _allocatePoints(numberOfPoints);

        long bufferPtr = Unsafe.getBufferPointer(this.points);
        // Make sure inner rings are opposite winding to the outer ring so they can be tessellated correctly later on
        for (int i = 0; i < numRings; i++) {
            ArrayList<Double> ring = rings.get(i);
            if (i > 0) {
                for (int p = (ring.size() / 3) - 1; p >= 0; p--) {
                    Unsafe.setDoubles(bufferPtr, ring.get(p * 3), ring.get((p * 3) + 1), ring.get((p * 3) + 2));
                    bufferPtr += 24;
                }
            } else {
                for (int p = 0; p < ring.size() / 3; p++) {
                    Unsafe.setDoubles(bufferPtr, ring.get(p * 3), ring.get((p * 3) + 1), ring.get((p * 3) + 2));
                    bufferPtr += 24;
                }

            }
        }
    }


    private boolean isTerrainValid(GLMapView ortho) {
        if(ortho.currentPass.drawTilt > 0d || ortho.scene.camera.perspective) {
            final int renderTerrainVersion = ortho.terrain.getTerrainVersion();
            if(this.terrainVersion != renderTerrainVersion) {
                // XXX - accessors should not be mutators
                this.terrainVersion = renderTerrainVersion;
                return false;
            }
        }
        return true;
    }

    final static void projectVerticesImpl(GLMapView view, DoubleBuffer points, int numPoints, int type, AltitudeMode altitudeMode, double unwrap, FloatBuffer vertices, PointD centroidProj) {
        switch(type) {
            case GLGeometry.VERTICES_PIXEL :
                vertices.clear();
                for(int i = 0; i < numPoints; i++) {
                    final double lat = points.get(i*3+1);
                    final double lng = points.get(i*3) + unwrap;
                    double alt = 0d;
                    switch (altitudeMode) {
                        case Absolute:
                            alt = points.get(i*3+2);
                            break;
                        case Relative:
                            alt = points.get(i*3+2) + view.getTerrainMeshElevation(lat, lng);
                            break;
                    }

                    view.scratch.geo.set(lat, lng, alt);
                    view.forward(view.scratch.geo, view.scratch.pointD);

                    vertices.put((float)view.scratch.pointD.x);
                    vertices.put((float)view.scratch.pointD.y);
                    vertices.put((float)view.scratch.pointD.z);
                }

                vertices.flip();
                break;
            case GLGeometry.VERTICES_PROJECTED:
                unwrap = 0d;
            case GLGeometry.VERTICES_BATCH :
                vertices.clear();
                for(int i = 0; i < numPoints; i++) {
                    final double lat = points.get(i*3+1);
                    final double lng = points.get(i*3) + unwrap;
                    double alt = 0d;
                    switch (altitudeMode) {
                        case Absolute:
                            alt = points.get(i*3+2);
                            break;
                        case Relative:
                            alt = points.get(i*3+2) + view.getTerrainMeshElevation(lat, lng);
                            break;
                    }

                    view.scratch.geo.set(lat, lng, alt);
                    view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);
                    vertices.put((float)(view.scratch.pointD.x-centroidProj.x));
                    vertices.put((float)(view.scratch.pointD.y-centroidProj.y));
                    vertices.put((float)(view.scratch.pointD.z-centroidProj.z));
                }

                vertices.flip();
                break;
            default :
                throw new IllegalArgumentException();
        }
    }

    @Override
    public final void draw(GLMapView view) {

        this.draw(view, GLGeometry.VERTICES_PROJECTED);
    }
    
    /**
     * Draws the linestring using vertices in the projected map space.
     * 
     * @param view
     */
    public void draw(GLMapView view, int vertices) {
        updateNadirClamp(view);
        this.needsTessellate = (view.currentScene.drawSrid == 4978) && this.tessellationEnabled;
        this.validateGeometry();

        // update the projection centroid
        if(vertices != GLGeometry.VERTICES_PIXEL && projectedVerticesSrid != view.currentPass.drawSrid) {
            view.scratch.geo.set((mbb.minY+mbb.maxY)/2d, (mbb.minX+mbb.maxX)/2d, 0d);
            view.currentPass.scene.mapProjection.forward(view.scratch.geo, centroidProj);
        }
        // if extrude or fill the vertices need to be projected;
        // `GLAntiAliasedLine` will handle independently for the stroke
        if(isExtruded() || hasFill())
            projectVertices(view, vertices);
        FloatBuffer v = this.vertices;
        if(v == null)
            return;

        // if no well-formed primitives, return
        if(this.numRenderPoints < 2 && (this.polyTriangles == null || this.polyTriangles.limit() < 9))
            return;

        // Get screen vertex coordinates for hit-testing
        if (getAltitudeMode() != AltitudeMode.ClampToGround)
            updateScreenVertices(view);

        GLES20FixedPipeline.glPushMatrix();
        if(vertices != GLGeometry.VERTICES_PIXEL) {
            // set Model-View as current scene forward
            view.scratch.matrix.set(view.scene.forward);
            // apply hemisphere shift if necessary
            final double unwrap = (vertices == GLGeometry.VERTICES_BATCH) ?
                    0d : GLAntiMeridianHelper.getUnwrap(view, crossesIDL, primaryHemi);
            view.scratch.matrix.translate(unwrap, 0d, 0d);
            // apply the RTC offset to translate from local to world coordinate system (map projection)
            view.scratch.matrix.translate(this.centroidProj.x, this.centroidProj.y, this.centroidProj.z);
            view.scratch.matrix.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
            for (int i = 0; i < 16; i++) {
                view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];
            }
            GLES20FixedPipeline.glLoadMatrixf(view.scratch.matrixF, 0);
        }
        // render any fill
        if(hasFill() && this.polyVertices != null) {
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, this.polyVertices);

            if(isExtruded()) {
                // if extrusion, offset the polygon to minimize z-fighting with
                // outlines
                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
                GLES30.glPolygonOffset(1.0f, 1.0f);
            }
            for(RenderState rs : renderStates) {
                if(rs.fillColorA > 0f) {
                    GLES20FixedPipeline.glColor4f(rs.fillColorR,
                            rs.fillColorG,
                            rs.fillColorB,
                            rs.fillColorA);
                    GLES20FixedPipeline.glDrawArrays(GLES30.GL_TRIANGLES, 0,
                            this.polyVertices.limit()/3);
                }
            }
            if(isExtruded()) {
                // restore polygon offset
                GLES30.glPolygonOffset(0.0f, 0.0f);
                GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
            }

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        }
        // render stroke
        this.drawImpl(view, v, 3);
        GLES20FixedPipeline.glPopMatrix();
    }

    /**
     * Draws the line. Any render state should have been configured previously.
     * @param view
     * @param v     vertices to draw
     * @param size  number of elements per vertex
     */
    /**
     * Draws the line. Any render state should have been configured previously.
     * @param view
     * @param v     vertices to draw
     * @param size  number of elements per vertex
     */
    protected void drawImpl(GLMapView view, FloatBuffer v, int size) {
        final RenderState[] toDraw = this.renderStates;
        if(toDraw == null) {
            drawImpl(view, DEFAULT_RS);
        } else {
            for(RenderState rs : toDraw) {
                drawImpl(view, rs);
            }
        }
    }

    private void drawImpl(GLMapView view, RenderState rs) {
        if(!isExtruded()) {
            if(_aalineDirty) {
                if(_lineRenderer == null)
                    _lineRenderer = new GLAntiAliasedLine();
                _lineRenderer.setLineData(this.renderPoints, 3, GLAntiAliasedLine.ConnectionType.AS_IS, getAltitudeMode());
                _aalineDirty = false;
            }
            _lineRenderer.draw(view,
                               // pattern
                               rs.factor, rs.pattern,
                               // stroke
                               rs.strokeColorR,
                               rs.strokeColorG,
                               rs.strokeColorB,
                               rs.strokeColorA,
                               rs.strokeWidth,
                               // outline
                               rs.outlineColorR,
                               rs.outlineColorG,
                               rs.outlineColorB,
                               rs.outlineColorA,
                               rs.outlineWidth);
        } else {
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, vertices);
            if(rs.outlineColorA > 0f) {
                GLES20FixedPipeline.glColor4f(rs.outlineColorR,
                                   rs.outlineColorG,
                                   rs.outlineColorB,
                                   rs.outlineColorA);
                GLES20FixedPipeline.glLineWidth(rs.strokeWidth+2f);
            }
            GLES20FixedPipeline.glColor4f(rs.strokeColorR,
                               rs.strokeColorG,
                               rs.strokeColorB,
                               rs.strokeColorA);
            GLES20FixedPipeline.glLineWidth(rs.strokeWidth);
            GLES20FixedPipeline.glDrawArrays(renderPointsDrawMode, 0, numRenderPoints);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        }
    }

    @Override
    public void release() {
        if(this.renderPoints != this.points) {
            Unsafe.free(this.renderPoints);
            this.renderPoints = null;
        }

        this.numRenderPoints = 0;

        Unsafe.free(this.renderPointIndices);
        this.renderPointIndices = null;
        Unsafe.free(this.points);
        this.points = null;
        Unsafe.free(this.vertices);
        this.vertices = null;
        this.verticesPtr = 0L;
        this.projectedVerticesSrid = -1;
        this.numPoints = 0;
        this.verticesDrawVersion = -1;
        this.vertexType = -1;
        Unsafe.free(polyTriangles);
        polyTriangles = null;
        Unsafe.free(polyVertices);
        polyVertices = null;
        Unsafe.free(_screenVertices);
        _screenVertices = null;
        _screenRect.setEmpty();

        this.tessellated = !this.needsTessellate;

        _aalineDirty = true;
        if(_lineRenderer != null) {
            _lineRenderer.release();
            _lineRenderer = null;
        }
    }

    @Override
    public final void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        this.batch(view, batch, renderPass, GLGeometry.VERTICES_PIXEL);
    }

    public final void batch(GLMapView view, GLRenderBatch2 batch, int renderPass, int vertices) {
        if(!MathUtils.hasBits(getRenderPass(), renderPass))
            return;

        // Only update NADIR clamp in sprite pass (tilt is always zero in surface pass)
        if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
            updateNadirClamp(view);

        this.projectVertices(view, vertices);
        FloatBuffer v = this.vertices;
        if (v == null)
            return;
        this.batchImpl(view, batch, renderPass, vertices, 3, v);
    }
        
    /**
     * Adds the linestring to the batch using the specified pre-projected
     * vertices.
     * 
     * @param view
     * @param batch
     * @param vertices
     * @param v
     */
    protected void batchImpl(GLMapView view, GLRenderBatch2 batch, int renderPass, int vertices, int size, FloatBuffer v) {
        if (!MathUtils.hasBits(getRenderPass(), renderPass))
            return;

        boolean sprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);
        boolean surface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);

        AltitudeMode altMode = getAltitudeMode();
        if (surface && altMode != AltitudeMode.ClampToGround
                || sprites && altMode == AltitudeMode.ClampToGround)
            return;

        // Update screen vertex forwards if we're rendering a line above terrain
        if (sprites)
            updateScreenVertices(view);

        if (this.hasFill() && this.polyVertices != null) {
            for(RenderState rs : renderStates) {
                if(rs.fillColorA > 0f) {
                    batch.batch(-1,
                            GLES20FixedPipeline.GL_TRIANGLES,
                            size,
                            0, this.polyVertices,
                            0, null,
                            rs.fillColorR,
                            rs.fillColorG,
                            rs.fillColorB,
                            rs.fillColorA);
                }
            }
        }

        final RenderState[] toDraw = this.renderStates;
        if(toDraw == null) {
            batchImpl(view, batch, renderPass, vertices, size, v, DEFAULT_RS);
        } else {
            for(RenderState rs : toDraw)
                batchImpl(view, batch, renderPass, vertices, size, v, rs);
        }
    }

    private void batchImpl(GLMapView view, GLRenderBatch2 batch, int renderPass,
            int vertices, int size, FloatBuffer v, RenderState rs) {


        batch.setLineWidth(rs.strokeWidth);
        batch.batch(-1,
                renderPointsDrawMode,
                size,
                0, v,
                0, null,
                rs.strokeColorR,
                rs.strokeColorG,
                rs.strokeColorB,
                rs.strokeColorA);
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE | GLMapView.RENDER_PASS_SPRITES;
    }

    /**
     * Returns <code>true</code> if the geometry should be tessellated before
     * being rendered for this pass, returns <code>false</code> otherwise.
     *
     * @param view
     * @param geom
     * @return
     */
    static boolean needsTessellate(GLMapView view, GLBatchLineString geom) {
        // if tessellation is disabled, no need to tessellate
        if(!geom.tessellationEnabled)
            return false;
        // the geometry is not tessellatable, no need to tessellate
        if(!geom.tessellatable)
            return false;
        // the globe isn't being rendered with ECEF, no need to tessellate
        if(view.currentScene.drawSrid != 4978)
            return false;
        // check if the current pass is ECEF or if the geometry is being
        // tessellated along LOBs -- no need to tessellate if planar projection
        // and cartesian tessellation
        return view.currentPass.drawSrid == 4978 ||
                (geom.tessellationMode == Tessellate.Mode.WGS84);
    }

    static class RenderState {
        // stroke
        float strokeWidth;
        float strokeColorR;
        float strokeColorG;
        float strokeColorB;
        float strokeColorA;
        int strokeColor;
        // fill (used for extrusions)
        float fillColorR;
        float fillColorG;
        float fillColorB;
        float fillColorA;
        int fillColor;
        // outline
        float outlineWidth;
        float outlineColorR;
        float outlineColorG;
        float outlineColorB;
        float outlineColorA;
        int outlineColor;
        // pattern
        short pattern = (short)0xFFFF;
        int factor = 1;
    }

    /**
     * Update the screen coordinates for this line
     * This MUST be called for hit-testing to work properly
     *
     * @param view Map view
     */
    public void updateScreenVertices(GLMapView view) {

        // Render points are not set
        if (renderPoints == null || numRenderPoints < 2)
            return;

        // Screen vertices are not used when clamping to ground
        if (getAltitudeMode() == AltitudeMode.ClampToGround)
            return;

        // Check if an update is required
        boolean terrainValid = isTerrainValid(view);
        if (_screenVerticesVersion == view.currentScene.drawVersion && terrainValid)
            return;

        // Update draw version for screen vertices
        _screenVerticesVersion = view.currentScene.drawVersion;

        // Allocate screen vertices buffer
        int limit = numRenderPoints * 3;
        if(_screenVertices == null || _screenVertices.capacity() < limit) {
            Unsafe.free(_screenVertices);
            _screenVertices = Unsafe.allocateDirect(limit, FloatBuffer.class);
        }

        // Forward render points (geodetic) to OpenGL screen coordinates
        renderPoints.clear();
        renderPoints.limit(limit);
        _screenVertices.clear();
        _screenVertices.limit(limit);
        view.forward(renderPoints, 3, _screenVertices, 3);

        // Update screen bounds
        updateScreenBounds();
    }

    /**
     * Update the screen boundaries taken up by this line
     */
    private void updateScreenBounds() {
        _screenVertices.clear();
        int partIdx = 1;
        int partCount = 0;
        HitRectPartition partition = !_partitionRects.isEmpty()
                ? _partitionRects.get(0)
                : new HitRectPartition();
        int idx = 0;
        int endLoop = (this.numRenderPoints - 1) * 3;
        for (int i = 0; i <= endLoop; i += 3) {
            float x = _screenVertices.get(i);
            float y = _screenVertices.get(i + 1);

            // Update main bounding rectangle
            if (i == 0) {
                _screenRect.set(x, y, x, y);
            } else {
                _screenRect.set(Math.min(_screenRect.left, x),
                        Math.min(_screenRect.bottom, y),
                        Math.max(_screenRect.right, x),
                        Math.max(_screenRect.top, y));
            }

            // Update partition bounding rectangle
            if (partIdx == 0) {
                partition.set(x, y, x, y);
            } else {
                partition.set(Math.min(partition.left, x),
                        Math.min(partition.bottom, y),
                        Math.max(partition.right, x),
                        Math.max(partition.top, y));
            }

            if (partIdx == partitionSize || i == endLoop) {
                if (partCount >= _partitionRects.size())
                    _partitionRects.add(partition);
                partition.endIndex = idx;
                partCount++;
                partIdx = 0;
                partition = partCount < _partitionRects.size()
                        ? _partitionRects.get(partCount)
                        : new HitRectPartition();
                partition.startIndex = idx;
                partition.set(x, y, x, y);
            }
            partIdx++;
            idx++;
        }
        while (partCount < _partitionRects.size())
            _partitionRects.remove(partCount);
    }

    @Override
    public HitTestResult hitTest(MapRenderer3 renderer, HitTestQueryParameters params) {
        if (getAltitudeMode() == AltitudeMode.ClampToGround)
            return surfaceHitTest(params);
        else
            return screenHitTest(params);
    }

    /**
     * Hit test against the surface-rendered line
     *
     * @param params Hit test parameters
     * @return Result if hit, null otherwise
     */
    private HitTestResult surfaceHitTest(HitTestQueryParameters params) {
        if (!_hitBounds.intersects(params.bounds))
            return null;

        GeoPoint geo = GeoPoint.createMutable();

        // Now check partitions
        int numHits = 0;
        int hitIndex = -1;
        List<GeoBoundsPartition> hitBounds = new ArrayList<>();
        for (GeoBoundsPartition b : _partitionBounds) {

            // Check hit on partition
            if (!b.intersects(params.bounds))
                continue;

            // Keep track of rectangles we've already hit tested
            hitBounds.add(b);

            // Point hit test
            for (int i = b.startIndex; i <= b.endIndex && i < numRenderPoints; i++) {
                int vIdx = i * 3;
                double lng = renderPoints.get(vIdx);
                double lat = renderPoints.get(vIdx + 1);

                // Found a hit
                geo.set(lat, lng);
                if (params.bounds.contains(geo)) {
                    hitIndex = i;
                    numHits++;
                }
            }
        }

        if (hitIndex > -1) {
            HitTestResult result = getHitTestResult(hitIndex, getRenderPoint(hitIndex));
            result.count = numHits;
            return result;
        }

        // No point detections and no hit partitions
        if (hitBounds.isEmpty())
            return null;

        // Line hit test
        Vector2D touch = new Vector2D(params.geo.getLongitude(),
                params.geo.getLatitude());
        MutableGeoBounds lineBounds = new MutableGeoBounds();
        for (GeoBoundsPartition r : hitBounds) {
            double lastLat = 0, lastLng = 0;
            for (int i = r.startIndex; i <= r.endIndex && i <= numRenderPoints; i++) {

                int vIdx = i * 3;
                double lng = renderPoints.get(vIdx);
                double lat = renderPoints.get(vIdx + 1);

                lineBounds.set(lastLat, lastLng, lat, lng);

                if (i > r.startIndex && params.bounds.intersects(lineBounds)) {

                    // Find the nearest point on this line based on the point we touched
                    Vector2D nearest = Vector2D.nearestPointOnSegment(touch,
                            new Vector2D(lastLng, lastLat),
                            new Vector2D(lng, lat));
                    float nx = (float) nearest.x;
                    float ny = (float) nearest.y;

                    // Check if the nearest point is within rectangle
                    GeoPoint pt = new GeoPoint(ny, nx);
                    if (params.bounds.contains(pt)) {

                        GeoPoint p1 = getRenderPoint(i - 1);
                        GeoPoint p2 = getRenderPoint(i);

                        double segLen = p1.distanceTo(p2);
                        double touchLen = p1.distanceTo(pt);
                        double segRatio = touchLen / segLen;

                        // Altitude correction
                        if (p1.isAltitudeValid() && p2.isAltitudeValid()) {
                            // Compute altitude at the touched point on the line
                            double touchAlt = p1.getAltitude() + (p2.getAltitude()
                                    - p1.getAltitude()) * segRatio;
                            pt = new GeoPoint(pt.getLatitude(), pt.getLongitude(), touchAlt);
                        } else {
                            // Remove altitude if either point is missing it
                            pt = new GeoPoint(pt.getLatitude(), pt.getLongitude());
                        }

                        HitTestResult result = getHitTestResult(i - 1, pt);
                        result.type = HitTestResult.Type.LINE;
                        return result;
                    }
                }

                lastLng = lng;
                lastLat = lat;
            }
        }

        return null;
    }

    /**
     * Hit test against the screen rendered line (non-surface
     *
     * @param params Hit test parameters
     * @return Result if hit, null otherwise
     */
    private HitTestResult screenHitTest(HitTestQueryParameters params) {

        // First check hit on bounding rectangle
        if (_screenVertices == null || !_screenRect.intersects(params.rect))
            return null;

        _screenVertices.clear();

        // Now check partitions
        int numHits = 0;
        int hitIndex = -1;
        List<HitRectPartition> hitRects = new ArrayList<>();
        for (HitRectPartition r : _partitionRects) {

            // Check hit on partition
            if (!r.intersects(params.rect))
                continue;

            // Keep track of rectangles we've already hit tested
            hitRects.add(r);

            // Point hit test
            for (int i = r.startIndex; i <= r.endIndex && i < numRenderPoints; i++) {
                int vIdx = i * 3;
                float x = _screenVertices.get(vIdx);
                float y = _screenVertices.get(vIdx + 1);

                // Found a hit
                if (params.rect.contains(x, y)) {
                    hitIndex = i;
                    numHits++;
                }
            }
        }

        if (hitIndex > -1) {
            HitTestResult result = getHitTestResult(hitIndex, getRenderPoint(hitIndex));
            result.count = numHits;
            return result;
        }

        // No point detections and no hit partitions
        if (hitRects.isEmpty())
            return null;

        // Line hit test
        Vector2D touch = new Vector2D(params.point.x, params.point.y);
        HitRect lineRect = new HitRect();
        for (HitRectPartition r : hitRects) {
            float lastX = 0, lastY = 0;
            for (int i = r.startIndex; i <= r.endIndex && i <= numRenderPoints; i++) {

                int vIdx = i * 3;
                float x = _screenVertices.get(vIdx);
                float y = _screenVertices.get(vIdx + 1);

                lineRect.set(Math.min(x, lastX), Math.min(y, lastY),
                        Math.max(x, lastX), Math.max(y, lastY));

                if (i > r.startIndex && params.rect.intersects(lineRect)) {

                    // Find the nearest point on this line based on the point we touched
                    Vector2D nearest = Vector2D.nearestPointOnSegment(touch,
                            new Vector2D(lastX, lastY),
                            new Vector2D(x, y));
                    float nx = (float) nearest.x;
                    float ny = (float) nearest.y;

                    // Check if the nearest point is within rectangle
                    if (params.rect.contains(nx, ny)) {

                        // Approximate geo point on the line that was touched
                        GeoPoint p1 = getRenderPoint(i - 1);
                        GeoPoint p2 = getRenderPoint(i);
                        double segLen = MathUtils.distance(lastX, lastY, x, y);
                        double touchLen = MathUtils.distance(lastX, lastY, nx, ny);
                        double segRatio = touchLen / segLen;
                        GeoPoint pt = GeoCalculations.pointAtDistance(p1,
                                p1.bearingTo(p2), p1.distanceTo(p2) * segRatio);

                        // Altitude correction
                        if (p1.isAltitudeValid() && p2.isAltitudeValid()) {
                            // Compute altitude at the touched point on the line
                            double touchAlt = p1.getAltitude() + (p2.getAltitude()
                                    - p1.getAltitude()) * segRatio;
                            pt = new GeoPoint(pt.getLatitude(), pt.getLongitude(), touchAlt);
                        } else {
                            // Remove altitude if either point is missing it
                            pt = new GeoPoint(pt.getLatitude(), pt.getLongitude());
                        }

                        HitTestResult result = getHitTestResult(i - 1, pt);
                        result.type = HitTestResult.Type.LINE;
                        return result;
                    }
                }

                lastX = x;
                lastY = y;
            }
        }

        return null;
    }

    /**
     * Get the hit test result for the line given the render point index that was hit
     *
     * @param rpIndex Render point index that was hit
     * @param point Geo point that was hit
     * @return Hit test result with corrected hit index
     */
    private HitTestResult getHitTestResult(int rpIndex, GeoPoint point) {

        HitTestResult result = new HitTestResult(featureId, point);
        result.index = rpIndex;

        // If the indices is null then the render points are the same as the original points
        if (this.renderPointIndices == null)
            return result;

        // Find original point index given the render point index
        int rpiLimit = this.renderPointIndices.limit();
        for (int i = 0; i < rpiLimit; i++) {
            int ind1 = this.renderPointIndices.get(i);
            int ind2 = i < rpiLimit - 1 ? this.renderPointIndices.get(i + 1) : ind1 + 1;
            if (rpIndex >= ind1 && rpIndex < ind2) {
                result.index = i;
                result.type = rpIndex == ind1 ? HitTestResult.Type.POINT : HitTestResult.Type.LINE;
                break;
            }
        }

        return result;
    }

    private GeoPoint getRenderPoint(int index) {
        int i = index * 3;
        return new GeoPoint(renderPoints.get(i + 1), renderPoints.get(i),
                renderPoints.get(i + 2));
    }

    // A hit rectangle with line start and end indices
    private static class HitRectPartition extends HitRect {

        // Start and end point indices
        public int startIndex, endIndex;

        public HitRectPartition() {
        }

        public void set(HitRectPartition other) {
            super.set(other);
            this.startIndex = other.startIndex;
            this.endIndex = other.endIndex;
        }
    }

    private static class GeoBoundsPartition extends MutableGeoBounds {

        public int startIndex, endIndex;

        public GeoBoundsPartition() {
        }

        public void set(GeoBoundsPartition other) {
            super.set(other);
            this.startIndex = other.startIndex;
            this.endIndex = other.endIndex;
        }
    }
}
