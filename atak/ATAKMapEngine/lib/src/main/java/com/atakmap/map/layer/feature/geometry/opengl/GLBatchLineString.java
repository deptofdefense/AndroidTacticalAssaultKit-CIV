
package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
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
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.map.opengl.GLAntiAliasedLine;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.Tessellate;

public class GLBatchLineString extends GLBatchGeometry {

    final static String PATTERN_VERTEX_SHADER =
            "uniform mat4 uProjection;\n" +
            "uniform mat4 uModelView;\n" +
            "uniform float uViewportWidth;\n" +
            "uniform float uViewportHeight;\n" +
            "attribute vec3 aVertexCoords;\n" +
            "uniform vec3 uStartVertexCoord;\n" +
            "varying vec2 vTexPos;\n" +
            "void main() {\n" +
            "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n" +
            "  vec4 startVertexPos = uProjection * uModelView * vec4(uStartVertexCoord.xyz, 1.0);\n" +
            "  float dx = (gl_Position.x-startVertexPos.x)*uViewportWidth/2.0;\n" +
            "  float dy = (gl_Position.y-startVertexPos.y)*uViewportHeight/2.0;\n" +
            "  float mag = sqrt(dx*dx + dy*dy);\n" +
            "  vTexPos = vec2(0.5, mag / 8.0);\n" +
            "}";
    final static String PATTERN_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform vec4 uColor;\n" +
            "varying vec2 vTexPos;\n" +
            "void main(void) {\n" +
            "  gl_FragColor = texture2D(uTexture, vTexPos) * uColor;\n" +
            "}";

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

    /** the source points, xyz triplets */
    protected DoubleBuffer points;
    /** the source point count */
    protected int numPoints;

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

    protected Feature.AltitudeMode altitudeMode;
    protected double extrude = 0d;

    private boolean _aalineDirty;
    private GLAntiAliasedLine _lineRenderer;

    protected boolean hasBeenExtruded;

    protected DoubleBuffer extrudedPolygons;
    protected FloatBuffer projectedExtrudedPolygons;

    protected DoubleBuffer extrudedOutline;
    protected FloatBuffer projectedExtrudedOutline;

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

        this.altitudeMode = Feature.AltitudeMode.ClampToGround;

        this.needsTessellate = false;
        this.tessellated = false;
        this.tessellationEnabled = true;
        this.tessellationMode = Tessellate.Mode.WGS84;
        _aalineDirty = true;
    }

    @Override
    public void setAltitudeMode(Feature.AltitudeMode altitudeMode) {
        this.altitudeMode = altitudeMode;
    }

    @Override
    public void setExtrude(double value) {
        this.extrude = value;
    }

    public boolean isExtruded() {
        return Double.compare(this.extrude, 0.0) != 0;
    }

    public void setTessellationEnabled(boolean e) {
        this.tessellationEnabled = e;
    }

    public void setTessellationMode(Tessellate.Mode mode) {
        this.tessellationMode = mode;
    }

    @Override
    public synchronized void setStyle(Style style) {
        final List<RenderState> states;
        if(style instanceof CompositeStyle)
            states = new ArrayList<>(((CompositeStyle)style).getNumStyles());
        else
            states = new ArrayList<>(1);
        getRenderStates(this.renderContext, style, states);

        if(states.isEmpty())
            this.renderStates = null;
        else
            this.renderStates = states.toArray(new RenderState[0]);
    }

    private static void getRenderStates(MapRenderer ctx, Style s, List<RenderState> states) {
        if(s instanceof CompositeStyle) {
            CompositeStyle c = (CompositeStyle)s;
            final int numStyles = c.getNumStyles();
            // XXX - stroke definition should reside as a single style
            //       definition. The composition based approach offers some
            //       nice flexibility, but consumption and rendering would
            //       be much better served with a unified representation.

            final int olen = states.size();
            for (int i = 0; i < numStyles; i++)
                getRenderStates(ctx, c.getStyle(i), states);

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
                if(a.pattern == b.pattern && // same pattern definition
                   a.factor == b.factor &&
                   a.outlineWidth == 0f && // neither are outlined
                   b.outlineWidth == 0 &&
                   a.strokeWidth > b.strokeWidth // `a` is stroked wider than `b`
                    ) {

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
            if (rs.fillColorA <= 0)
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
        if (blob == null && ls == null)
            return;

        final int numPoints, skip;
        final boolean compressed;
        final int dim;
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

        this.numPoints = numPoints;

        if(this.points == null || this.points.capacity() < (this.numPoints*3)) {
            if(this.renderPoints == this.points)
                this.renderPoints = null;
            Unsafe.free(this.points);

            this.points = Unsafe.allocateDirect(this.numPoints*3, DoubleBuffer.class);
        }

        this.points.clear();

        // while iterating points, obtain rough estimate of max inter-point
        // distance to determine if tessellation should be performed
        this.tessellatable = false;

        this.crossesIDL = false;
        this.primaryHemi = 0;

        if(this.numPoints > 0) {
            final double thresholdSq;
            final boolean tessellateWgs84 = this.tessellationMode == Tessellate.Mode.WGS84;
            if(tessellateWgs84)
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

            for (int i = 1; i < this.numPoints; i++) {
                double dx, dy;
                if (compressed) {
                    dx = blob.getFloat();
                    dy = blob.getFloat();
                    double dz = 0d;
                    if (dim > 2)
                        dz = blob.getFloat();
                    x += dx;
                    y += dy;
                    z += dz;
                } else if (blob != null) {
                    final double nx = blob.getDouble();
                    final double ny = blob.getDouble();
                    dx = nx - x;
                    dy = ny - y;
                    x = nx;
                    y = ny;
                    z = (dim > 2) ? blob.getDouble() : 0d;
                } else {
                    final double nx = ls.getX(i);
                    final double ny = ls.getY(i);
                    dx = nx - x;
                    dy = ny - y;
                    x = nx;
                    y = ny;
                    z = (dim > 2) ? ls.getZ(i) : 0d;
                }

                // approximate different in lat,lng between current and last point
                // as meters. update tessellation required flag
                if(tessellateWgs84) {
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

            this.primaryHemi = (idlInfo&GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
            this.crossesIDL = (idlInfo&GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;
        } else {
            this.points.limit(0);
        }

        // force tessellation sync
        this.tessellated = !this.needsTessellate;

        this.validateGeometry();
        _aalineDirty = true;
    }

    protected boolean validateGeometry() {
        if(this.points != null && ((this.needsTessellate != this.tessellated) || (this.renderPoints == null))) {
            if(this.numPoints > 0 && this.needsTessellate && this.tessellatable) {
                Buffer result = Tessellate.linestring(Double.TYPE,
                                                      this.points,
                                                      24,
                                                      3,
                                                      this.numPoints,
                                                      this.tessellationMode == Tessellate.Mode.WGS84 ? threshold : thresholdxyz,
                                                      this.tessellationMode == Tessellate.Mode.WGS84);
                if(result == this.points) {
                    // no tessellation occurred
                    if(this.renderPoints != this.points)
                        Unsafe.free(this.renderPoints);
                    this.renderPoints = this.points;
                    this.numRenderPoints = this.numPoints;
                } else {
                    // tessellation occurred
                    if(this.renderPoints != this.points)
                        Unsafe.free(this.renderPoints);
                    this.renderPoints = ((ByteBuffer) result).asDoubleBuffer();
                    this.numRenderPoints = this.renderPoints.limit() / 3;
                }
            } else {
                // no need to tessellate
                if(this.renderPoints != this.points)
                    Unsafe.free(this.renderPoints);
                this.renderPoints = this.points;
                this.numRenderPoints = this.numPoints;
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
            for(int i = 0; i < numRenderPoints * 3; i += 3) {
                double lat = renderPoints.get(i+1);
                double lng = renderPoints.get(i);
                eb.add(lng, lat);
            }
            this.renderBounds = eb.build();
            // if the geometry crosses the IDL, the minimum and maximum will be
            // reversed and need to be wrapped
            if(this.crossesIDL) {
                final double minX = this.renderBounds.minX;
                final double maxX = this.renderBounds.maxX;
                this.renderBounds.minX = GeoCalculations.wrapLongitude(maxX);
                this.renderBounds.maxX = GeoCalculations.wrapLongitude(minX);
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
        this.needsTessellate = srid == 4978 && this.tessellationEnabled;
        this.validateGeometry();
        return this.renderBounds;
    }

    public void setTesselationThreshold(double threshold) {
        this.threshold = threshold;
        this.thresholdxyz = threshold / 10_000_000D;
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
        this.needsTessellate = (view.drawSrid == 4978) && this.tessellationEnabled;
        this.validateGeometry();

        boolean retval = false;

        // project the vertices
        final double unwrap = GLAntiMeridianHelper.getUnwrap(view, this.crossesIDL, this.primaryHemi);
        switch(vertices) {
            case GLGeometry.VERTICES_PIXEL :
                if(this.verticesDrawVersion != view.drawVersion
                        || this.vertexType != vertices
                        || !isTerrainValid(view)) {

                    projectVerticesImpl(view,
                            this.renderPoints,
                            this.numRenderPoints,
                            vertices,
                            altitudeMode,
                            unwrap,
                            this.vertices,
                            centroidProj);

                    retval = true;
                }
                this.verticesDrawVersion = view.drawVersion;

                break;
            case GLGeometry.VERTICES_PROJECTED :
                if(view.drawSrid != this.projectedVerticesSrid
                        || this.vertexType != vertices
                        || !isTerrainValid(view)) {

                    projectVerticesImpl(view,
                                        this.renderPoints,
                                        this.numRenderPoints,
                                        vertices,
                                        altitudeMode,
                                        unwrap,
                                        this.vertices,
                                        centroidProj);

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


    private boolean isTerrainValid(GLMapView ortho) {
        if(ortho.drawTilt > 0d) {
            final int renderTerrainVersion = ortho.terrain.getTerrainVersion();
            if(this.terrainVersion != renderTerrainVersion) {
                this.terrainVersion = renderTerrainVersion;
                return false;
            }
        }
        return true;
    }

    final static void projectVerticesImpl(GLMapView view, DoubleBuffer points, int numPoints, int type, Feature.AltitudeMode altitudeMode, double unwrap, FloatBuffer vertices, PointD centroidProj) {
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
            case GLGeometry.VERTICES_PROJECTED :
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

        this.draw(view, GLGeometry.VERTICES_PIXEL);
    }
    
    /**
     * Draws the linestring using vertices in the projected map space.
     * 
     * @param view
     */
    public void draw(GLMapView view, int vertices) {
        FloatBuffer v = this.vertices;
        if(v == null)
            return;

        this.needsTessellate = (view.drawSrid == 4978) && this.tessellationEnabled;
        this.validateGeometry();

        this.drawImpl(view, v, 3);
    }
    
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
        if(_aalineDirty) {
            if(_lineRenderer == null)
                _lineRenderer = new GLAntiAliasedLine();
            _lineRenderer.setLineData(this.renderPoints, 3, GLAntiAliasedLine.ConnectionType.AS_IS, altitudeMode);
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
    }

    @Override
    public void release() {
        if(this.renderPoints != this.points) {
            Unsafe.free(this.renderPoints);
            this.renderPoints = null;
        }

        this.numRenderPoints = 0;

        Unsafe.free(this.points);
        this.points = null;
        Unsafe.free(this.vertices);
        this.vertices = null;
        this.verticesPtr = 0L;
        this.projectedVerticesSrid = -1;
        this.numPoints = 0;
        this.verticesDrawVersion = -1;
        this.vertexType = -1;

        this.tessellated = !this.needsTessellate;

        _aalineDirty = true;
        if(_lineRenderer != null) {
            _lineRenderer.release();
            _lineRenderer = null;
        }

        Unsafe.free(extrudedPolygons);
        extrudedPolygons = null;

        Unsafe.free(projectedExtrudedPolygons);
        projectedExtrudedPolygons = null;

        Unsafe.free(extrudedOutline);
        extrudedOutline = null;

        Unsafe.free(projectedExtrudedOutline);
        projectedExtrudedOutline = null;
    }

    @Override
    public final void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        this.batch(view, batch, renderPass, GLGeometry.VERTICES_PIXEL);
    }

    public final void batch(GLMapView view, GLRenderBatch2 batch, int renderPass, int vertices) {
        if(!MathUtils.hasBits(getRenderPass(), renderPass))
            return;

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
        drawExtrusion(view, batch, renderPass, vertices, rs);
        batch.setLineWidth(rs.strokeWidth);
        batch.batch(-1,
                GLES20FixedPipeline.GL_LINE_STRIP,
                size,
                0, v,
                0, null,
                rs.strokeColorR,
                rs.strokeColorG,
                rs.strokeColorB,
                rs.strokeColorA);
    }

    public void drawExtrusion(GLMapView view, GLRenderBatch2 batch, int renderPass, int vertices, RenderState rs) {
        if (!isExtruded() || !MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
            return;

        if (!hasBeenExtruded || extrudedPolygons == null
                || extrudedOutline == null) {
            int last = points.limit() - 3;
            boolean closed = points.get(0) == points.get(last)
                    && points.get(1) == points.get(last + 1)
                    && points.get(2) == points.get(last + 2);
            hasBeenExtruded = true;

            DoubleBuffer extPoints = Unsafe.allocateDirect(this.numPoints*3,
                    DoubleBuffer.class);
            extPoints.put(points);

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
                heights[h++] = altitudeMode == Feature.AltitudeMode.Absolute
                        ? height - alt : (centerAlt + height) - alt;
            }

            Unsafe.free(extrudedPolygons);
            extrudedPolygons = GLExtrude.extrudeRelative(Double.NaN, extPoints, 3, false, heights);

            Unsafe.free(projectedExtrudedPolygons);
            projectedExtrudedPolygons = Unsafe.allocateDirect(extrudedPolygons.limit(), FloatBuffer.class);
            extrudedPolygons.rewind();

            Unsafe.free(extrudedOutline);
            extrudedOutline = GLExtrude.extrudeOutline(Double.NaN, extPoints, 3, false, closed, heights);

            Unsafe.free(projectedExtrudedOutline);
            projectedExtrudedOutline = Unsafe.allocateDirect(extrudedOutline.limit(), FloatBuffer.class);
            extrudedOutline.rewind();

            Unsafe.free(extPoints);
        }
        final double unwrap = view.idlHelper.getUnwrap(this.mbb);
        projectVerticesImpl(view, extrudedPolygons, extrudedPolygons.limit()/3, vertices, Feature.AltitudeMode.Absolute, unwrap, projectedExtrudedPolygons, this.centroidProj);
        batch.batch(-1,
                GLES20FixedPipeline.GL_TRIANGLES,
                3,
                0, projectedExtrudedPolygons,
                0, null,
                rs.fillColorR,
                rs.fillColorG,
                rs.fillColorB,
                rs.fillColorA);

        projectVerticesImpl(view, extrudedOutline, extrudedOutline.limit() / 3, vertices, Feature.AltitudeMode.Absolute, unwrap, projectedExtrudedOutline, centroidProj);

        // It would be preferable to do this using glPolygonOffset, although that (would?)
        // require changes to how batching works.
        // This constant seems to work well for offsetting the outlines so they render on top of
        // the mesh, just a bit awkward to do it here.
        // NOTE: when multiple objects are near each other, the outlines of background objects
        // might appear above the nearby objects, this looks weird and is not preferable.
        double zOffset = -1.0e-4;
        view.scratch.matrix.setToTranslation(0, 0, zOffset);
        view.scratch.matrix.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < view.scratch.matrixF.length; ++i) {
            view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];
        }

        // Make sure the lines appear relatively linear in size regardless of zoom level
        // using drawMapResolution on it's own (or it's reciprocal) had issues at the extremes,
        // raising to a power < 1/2 seems to work well.
        batch.setLineWidth(4.0f * (float)Math.pow(1.0 / view.drawMapResolution, 0.2));

        batch.pushMatrix(GLES20FixedPipeline.GL_MODELVIEW);
        batch.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
        // TODO: mess around with the outline color, maybe just make this white/black like the
        // the non-extruded KMLs?
        batch.batch(-1, GLES20FixedPipeline.GL_LINES,
                3,
                0, projectedExtrudedOutline,
                0, null,
                rs.strokeColorR,
                rs.strokeColorG,
                rs.strokeColorB,
                rs.strokeColorA);
        batch.popMatrix(GLES20FixedPipeline.GL_MODELVIEW);
        batch.setLineWidth(1);
    }
    
    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE | GLMapView.RENDER_PASS_SPRITES;
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
}
