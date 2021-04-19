/***************************************************************************
 *  Copyright 2020 PAR Government Systems
 *
 * Unlimited Rights:
 * PAR Government retains ownership rights to this software.  The Government has Unlimited Rights
 * to use, modify, reproduce, release, perform, display, or disclose this
 * software as identified in the purchase order contract. Any
 * reproduction of computer software or portions thereof marked with this
 * legend must also reproduce the markings. Any person who has been provided
 * access to this software must be aware of the above restrictions.
 */

package com.atakmap.map.opengl;

import android.opengl.GLES30;
import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Provides a method for drawing anti aliased lines using the techniques described in
 * https://blog.mapbox.com/drawing-antialiased-lines-with-opengl-8766f34192dc
 */
public class GLAntiAliasedLine {

    /** The width, in pixels, of the anti-alias filter region on either side of the line */
    private final static float FILTER_WIDTH = 2f;

    public static final String TAG = "GLAntiAliasedLine";

    public enum ConnectionType {
        // Causes the last point to be connected to the first point
        FORCE_CLOSE,
        // Draws the line as-is based on the given vertex data
        AS_IS,
    }

    /** source coordinates, LLA (longitude, latitude, altitude) */
    private DoubleBuffer _lineStrip;
    private Feature.AltitudeMode _altMode;
    /** source coordinates, current map projection */
    private FloatBuffer _forwardSegmentVerts;
    private ByteBuffer _normals;
    private int _forwardSegmentsSrid;
    private int _forwardSegmentsTerrainVersion;

    // geometry centroid latitude,longitude
    private double _centroidLat;
    private double _centroidLng;
    // relative-to-center offsets in map projection, considered valid when
    // `_forwardSegmentsSrid == GLMapView.drawSrid`; recompute for current
    // projection otherwise
    private double _rtcX;
    private double _rtcY;
    private double _rtcZ;

    // records information for IDL crossing
    private boolean _crossesIDL;
    private int _primaryHemi;

    public GLAntiAliasedLine() {
        _forwardSegmentsSrid = -1;
        _forwardSegmentsTerrainVersion = -1;

        _rtcX = 0d;
        _rtcY = 0d;
        _rtcZ = 0d;

        _crossesIDL = false;

        _altMode = Feature.AltitudeMode.Absolute;
    }

    /**
     * Set the vertex data for the line that will be drawn.
     * @param verts The GeoPoints representing the line.
     * @param componentCount The number of components that each point contains, should be 2 or 3.
     * @param type Specifies how the line's endpoints should be connected.
     */
    public void setLineData(GeoPoint[] verts, int componentCount, ConnectionType type) {
        double[] tmp = new double[verts.length * 3];
        for (int i = 0; i < verts.length; i++) {
            tmp[i * 3] = verts[i].getLongitude();
            tmp[i * 3 + 1] = verts[i].getLatitude();
            tmp[i * 3 + 2] = 0d;
            if(componentCount == 3 && !Double.isNaN(verts[i].getAltitude()))
                tmp[i * 3 + 2] = verts[i].getAltitude();
        }
        setLineData(DoubleBuffer.wrap(tmp), 3, type, Feature.AltitudeMode.Absolute);
    }

    /**
     * Set the vertex data for the line that will be drawn.
     * @param verts The points representing the line.
     * @param componentCount The number of components that each point contains, should be 2 or 3.
     * @param type Specifies how the line's endpoints should be connected.
     */
    public void setLineData(FloatBuffer verts, int componentCount, ConnectionType type) {
        double[] tmp = new double[verts.limit()];
        for (int i = 0; i < verts.limit(); i++) {
            tmp[i] = verts.get(i);
        }
        setLineData(DoubleBuffer.wrap(tmp), componentCount, type, Feature.AltitudeMode.Absolute);
    }

    /**
     * Set the vertex data for the line that will be drawn.
     * @param verts The points representing the line.
     * @param componentCount The number of components that each point contains, should be 2 or 3.
     * @param type Specifies how the line's endpoints should be connected.
     */
    public void setLineData(DoubleBuffer verts, int componentCount, ConnectionType type, Feature.AltitudeMode altMode) {
        _altMode = altMode;
        _crossesIDL = false;
        _primaryHemi = 0;

        int capacity = verts.limit(); // > 0 ? verts.limit() : verts.capacity();
        if (capacity <= 3) {
            return;
        }
        int numPoints = capacity / componentCount;
        if (type == ConnectionType.FORCE_CLOSE) {
            numPoints++;
        }
        // capture surface MBB
        double minX = verts.get(0);
        double minY = verts.get(1);
        double maxX = minX;
        double maxY = minY;

        _allocateBuffers(numPoints);
        // prepare for write
        _lineStrip.rewind();
        for (int currVert = 0; currVert < numPoints*componentCount; currVert += componentCount) {
            final int pos = currVert % capacity;

            // start of line segment
            final double ax = verts.get(pos);
            final double ay = verts.get(pos + 1);
            final double az = componentCount == 3 ? verts.get(pos + 2) : 0d;

            _lineStrip.put(ax);
            _lineStrip.put(ay);
            _lineStrip.put(Double.isNaN(az) ? 0d : az);

            // update MBB
            final double x = ax;
            final double y = ay;
            if(x < minX)
                minX = x;
            else if(x > maxX)
                maxX = x;
            if(y < minY)
                minY = y;
            else if(y > maxY)
                maxY = y;
        }
        _lineStrip.flip();
        _forwardSegmentsSrid = -1;

        // update RTC
        _centroidLng = (minX+maxX)/2d;
        _centroidLat = (minY+maxY)/2d;

        final int idlInfo = GLAntiMeridianHelper.normalizeHemisphere(componentCount, _lineStrip, _lineStrip);
        _lineStrip.flip();

        _primaryHemi = (idlInfo&GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
        _crossesIDL = (idlInfo&GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;
    }

    /**
     * Draws the antialiased line specified by a previous call to setLineData().
     * @param view The GLMapView used for rendering.
     * @param red The red component of the color that the line will be drawn with.
     * @param green The green component of the color that the line will be drawn with.
     * @param blue The blue component of the color that the line will be drawn with.
     * @param width The width of the line to be drawn.
     */
    public void draw(GLMapView view, float red, float green, float blue,
            float alpha, float width) {
        draw(view, 1, (short)0xFFFF, red, green, blue, alpha, width);
    }

    /**
     * Draws the antialiased line specified by a previous call to setLineData().
     * @param view The GLMapView used for rendering.
     * @param red The red component of the color that the line will be drawn with.
     * @param green The green component of the color that the line will be drawn with.
     * @param blue The blue component of the color that the line will be drawn with.
     * @param width The width of the line to be drawn.
     * @param outlineRed The red component of the outline color
     * @param outlineGreen The green component of the outline color
     * @param outlineBlue The blue component of the outline color
     * @param outlineAlpha The alpha component of the outline color
     * @param outlineWidth The width of the outline, in pixels. If <code>0f</code>,
     *                     no outline is applied
     */
    public void draw(GLMapView view, float red, float green, float blue,
            float alpha, float width, float outlineRed, float outlineGreen, float outlineBlue, float outlineAlpha, float outlineWidth) {
        draw(view, 1, (short)0xFFFF, red, green, blue, alpha, width, outlineRed, outlineGreen, outlineBlue, outlineAlpha, outlineWidth);
    }

    /**
     * Draws the antialiased line specified by a previous call to
     * setLineData(), applying the specified pattern to the line.
     * @param view The GLMapView used for rendering.
     * @param factor The number of pixels to be drawn for each pattern bit
     * @param pattern The bitmask pattern. Interpreted least-significant bit
     *                first. Each bit that is toggled will be colored per
     *                the specified <code>red</code>, <code>green</code>,
     *                <code>blue</code> and <code>alpha</code>; bits that are
     *                not toggled will be output as transparent.
     * @param red The red component of the color that the line will be drawn with.
     * @param green The green component of the color that the line will be drawn with.
     * @param blue The blue component of the color that the line will be drawn with.
     * @param width The width of the line to be drawn.
     */
    public void draw(GLMapView view, int factor, short pattern, float red, float green, float blue, float alpha, float width) {
        draw(view, factor, pattern, red, green, blue, alpha, width, 0f, 0f, 0f, 0f, 0f);
    }

    /**
     * Draws the antialiased line specified by a previous call to
     * setLineData(), applying the specified pattern to the line, with an optional outline.
     * @param view The GLMapView used for rendering.
     * @param factor The number of pixels to be drawn for each pattern bit
     * @param pattern The bitmask pattern. Interpreted least-significant bit
     *                first. Each bit that is toggled will be colored per
     *                the specified <code>red</code>, <code>green</code>,
     *                <code>blue</code> and <code>alpha</code>; bits that are
     *                not toggled will be output as transparent.
     * @param red The red component of the color that the line will be drawn with.
     * @param green The green component of the color that the line will be drawn with.
     * @param blue The blue component of the color that the line will be drawn with.
     * @param width The width of the line to be drawn.
     * @param outlineRed The red component of the outline color
     * @param outlineGreen The green component of the outline color
     * @param outlineBlue The blue component of the outline color
     * @param outlineAlpha The alpha component of the outline color
     * @param outlineWidth The width of the outline, in pixels. If <code>0f</code>,
     *                     no outline is applied
     */
    public void draw(GLMapView view, int factor, short pattern, float red, float green, float blue, float alpha, float width, float outlineRed, float outlineGreen, float outlineBlue, float outlineAlpha, float outlineWidth) {
        AntiAliasingProgram program = AntiAliasingProgram.get();
        // if the projection has changed or the geometry is relative to terrain
        // and the terrain has changed, we need to refresh
        final int terrainVersion = view.getTerrainVersion();
        if (_forwardSegmentsSrid != view.drawSrid ||
                (_altMode == Feature.AltitudeMode.Relative && _forwardSegmentsTerrainVersion != terrainVersion)) {


            view.scratch.geo.set(_centroidLat, _centroidLng, 0d);
            view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);
            _rtcX = view.scratch.pointD.x;
            _rtcY = view.scratch.pointD.y;
            _rtcZ = view.scratch.pointD.z;

            // prepare to fill the buffers
            _forwardSegmentVerts.rewind();
            _normals.rewind();
            for(int i = 0; i < (_lineStrip.limit()/3)-1; i++) {
                // obtain the source position as LLA
                final double lat0 = _lineStrip.get(i*3+1);
                final double lng0 = _lineStrip.get(i*3);
                double alt0 = _lineStrip.get(i*3+2);
                if(_altMode == Feature.AltitudeMode.Relative) {
                    final double terrainEl = view.getTerrainMeshElevation(lat0, lng0);
                    if(!Double.isNaN(terrainEl))
                        alt0 += terrainEl;
                }
                view.scratch.geo.set(lat0, lng0, alt0);
                // transform to the current map projection
                view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);

                // set vertex position as map projection coordinate, relative to center
                final float ax = (float)(view.scratch.pointD.x-_rtcX);
                final float ay = (float)(view.scratch.pointD.y-_rtcY);
                final float az = (float)(view.scratch.pointD.z-_rtcZ);

                // obtain the source position as LLA
                final double lat1 = _lineStrip.get((i+1)*3+1);
                final double lng1 = _lineStrip.get((i+1)*3);
                double alt1 = _lineStrip.get((i+1)*3+2);
                if(_altMode == Feature.AltitudeMode.Relative) {
                    final double terrainEl = view.getTerrainMeshElevation(lat1, lng1);
                    if(!Double.isNaN(terrainEl))
                        alt1 += terrainEl;
                }
                view.scratch.geo.set(lat1, lng1, alt1);
                // transform to the current map projection
                view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);

                // set vertex position as map projection coordinate, relative to center
                final float bx = (float)(view.scratch.pointD.x-_rtcX);
                final float by = (float)(view.scratch.pointD.y-_rtcY);
                final float bz = (float)(view.scratch.pointD.z-_rtcZ);

                // emit the triangles

                // Triangle 1
                _forwardSegmentVerts.put(ax); _forwardSegmentVerts.put(ay); _forwardSegmentVerts.put(az);
                _forwardSegmentVerts.put(bx); _forwardSegmentVerts.put(by); _forwardSegmentVerts.put(bz);
                _normals.put((byte)0xFF); _normals.put((byte)0xFF); // normal & direction
                _normals.put((byte)0x00); _normals.put((byte)0x00); // vertex select & origin select

                _forwardSegmentVerts.put(bx); _forwardSegmentVerts.put(by); _forwardSegmentVerts.put(bz);
                _forwardSegmentVerts.put(ax); _forwardSegmentVerts.put(ay); _forwardSegmentVerts.put(az);
                _normals.put((byte)0xFF); _normals.put((byte)0x00); // normal & direction
                _normals.put((byte)0x00); _normals.put((byte)0xFF); // vertex select & origin select

                _forwardSegmentVerts.put(ax); _forwardSegmentVerts.put(ay); _forwardSegmentVerts.put(az);
                _forwardSegmentVerts.put(bx); _forwardSegmentVerts.put(by); _forwardSegmentVerts.put(bz);
                _normals.put((byte)0x00); _normals.put((byte)0xFF); // normal & direction
                _normals.put((byte)0x00);_normals.put((byte)0x00); // vertex select & origin select

                // Triangle 2
                _forwardSegmentVerts.put(bx); _forwardSegmentVerts.put(by); _forwardSegmentVerts.put(bz);
                _forwardSegmentVerts.put(ax); _forwardSegmentVerts.put(ay); _forwardSegmentVerts.put(az);
                _normals.put((byte)0xFF); _normals.put((byte)0xFF); // normal & direction
                _normals.put((byte)0xFF);_normals.put((byte)0xFF); // vertex select & origin select

                _forwardSegmentVerts.put(ax); _forwardSegmentVerts.put(ay); _forwardSegmentVerts.put(az);
                _forwardSegmentVerts.put(bx); _forwardSegmentVerts.put(by); _forwardSegmentVerts.put(bz);
                _normals.put((byte)0xFF); _normals.put((byte)0x00); // normal & direction
                _normals.put((byte)0xFF);_normals.put((byte)0x00); // vertex select & origin select

                _forwardSegmentVerts.put(bx); _forwardSegmentVerts.put(by); _forwardSegmentVerts.put(bz);
                _forwardSegmentVerts.put(ax); _forwardSegmentVerts.put(ay); _forwardSegmentVerts.put(az);
                _normals.put((byte)0x00); _normals.put((byte)0x00); // normal & direction
                _normals.put((byte)0x00); _normals.put((byte)0xFF); // vertex select & origin select
            }
            // prepare the buffers for draw
            _forwardSegmentVerts.rewind();
            _normals.rewind();

            // mark segment vertex positions valid for current SRID and terrain
            _forwardSegmentsSrid = view.drawSrid;
            _forwardSegmentsTerrainVersion = terrainVersion;
        }

        GLES30.glUseProgram(program.handle);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        GLES30.glEnableVertexAttribArray(AntiAliasingProgram.aPosition0);
        GLES30.glEnableVertexAttribArray(AntiAliasingProgram.aPosition1);
        GLES30.glEnableVertexAttribArray(AntiAliasingProgram.aNormal);

        GLES30.glVertexAttribPointer(AntiAliasingProgram.aPosition0, 3, GLES30.GL_FLOAT, false, 24, _forwardSegmentVerts.position(0));
        GLES30.glVertexAttribPointer(AntiAliasingProgram.aPosition1, 3, GLES30.GL_FLOAT, false, 24, _forwardSegmentVerts.position(3));
        _forwardSegmentVerts.position(0);
        GLES30.glVertexAttribPointer(AntiAliasingProgram.aNormal, 4, GLES30.GL_UNSIGNED_BYTE, true, 4, _normals);

        _setUniforms(view, program, factor, pattern, width, red, green, blue, alpha, outlineWidth, outlineRed, outlineGreen, outlineBlue, outlineAlpha);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, _forwardSegmentVerts.limit()/6);
        GLES30.glDisableVertexAttribArray(AntiAliasingProgram.aNormal);
        GLES30.glDisableVertexAttribArray(AntiAliasingProgram.aPosition1);
        GLES30.glDisableVertexAttribArray(AntiAliasingProgram.aPosition0);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    /**
     * Sets the uniforms for drawing the lines.
     * @param view The GLMapView.
     * @param program The program whose uniforms we want to set.
     * @param width The width of the line we're going to draw.
     * @param sr    The stroke red component.
     * @param sg    The stroke green component.
     * @param sb    The stroke blue component.
     * @param sa    The stroke alpha component.
     * @param owidth    The width of the outline
     * @param or    The stroke red component.
     * @param og    The stroke green component.
     * @param ob    The stroke blue component.
     * @param oa    The stroke alpha component.
     */
    private void _setUniforms(GLMapView view, AntiAliasingProgram program, int factor, short pattern, float width, float sr, float sg, float sb, float sa, float owidth, float or, float og, float ob, float oa) {
        // sanity check the factor
        if(factor < 1)
            factor = 1;

        // set Model-View as current scene forward
        view.scratch.matrix.set(view.scene.forward);
        // apply hemisphere shift if necessary
        final double unwrap = GLAntiMeridianHelper.getUnwrap(view, _crossesIDL, _primaryHemi);
        view.scratch.matrix.translate(unwrap, 0d, 0d);

        // apply the RTC offset to translate from local to world coordinate system (map projection)
        view.scratch.matrix.translate(_rtcX, _rtcY, _rtcZ);
        view.scratch.matrix.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for (int i = 0; i < 16; i++) {
            view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];
        }
        GLES30.glUniformMatrix4fv(program.uModelView, 1, false, view.scratch.matrixF, 0);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        GLES30.glUniformMatrix4fv(program.uProjection, 1, false, view.scratch.matrixF, 0);

        GLES30.glUniform1f(program.uHalfWidth, width / 2f);
        GLES30.glUniform4f(program.uColor, sr, sg, sb, sa);
        GLES30.glUniform1i(program.uPattern, pattern);
        GLES30.glUniform1i(program.uFactor, factor);

        GLES30.glUniform1f(program.uFilterWidth, FILTER_WIDTH);

        // apply outline
        GLES30.glUniform1f(program.uOutlineWidth, owidth);
        if(owidth > 0f)
            GLES30.glUniform4f(program.uOutlineColor, or, og, ob, oa);
        else // no outline, specify stroke color to be used in anti-alias region
            GLES30.glUniform4f(program.uOutlineColor, sr, sg, sb, sa);

        // viewport size
        {
            int[] viewport = new int[4];
            GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0);
            GLES30.glUniform2f(program.uViewportSize, (float)viewport[2] / 2.0f, (float)viewport[3] / 2.0f);
        }
    }

   public void release() {
        _freeBuffers();
   }

    private void _freeBuffers() {
        Unsafe.free(_lineStrip);
        _lineStrip = null;
        Unsafe.free(_forwardSegmentVerts);
        _forwardSegmentVerts = null;
        Unsafe.free(_normals);
        _normals = null;
    }

    /**
     * Allocate the buffers needed for drawing the line.
     * @param numPoints The number of source points.
     */
    private void _allocateBuffers(int numPoints) {
        // Vertex positions are 3 component, so make sure we allocate enough even if the data we're
        // given only has 2 components
        int segmentCount = numPoints-1;
        int vertBufferCapacity = segmentCount * 3 * 12;
        int normalBufferCapacity = segmentCount * 4 * 12;
        if (_lineStrip == null || _lineStrip.capacity() < (3*numPoints)) {
            _freeBuffers();
            _lineStrip = Unsafe.allocateDirect(3*numPoints, DoubleBuffer.class);
            _forwardSegmentVerts = Unsafe.allocateDirect(vertBufferCapacity, FloatBuffer.class);
            _normals = Unsafe.allocateDirect(normalBufferCapacity, ByteBuffer.class);
        }
        _lineStrip.limit(3*numPoints);
        _forwardSegmentVerts.limit(vertBufferCapacity);
        _normals.limit(normalBufferCapacity);
    }

    /**
     * Helper class that creates a static instance of the shader program and contains the locations
     * of uniform and attribute variables.
     */
    private static class AntiAliasingProgram {
        static AntiAliasingProgram instance;
        public static final int aPosition0 = 0;
        public static final int aPosition1 = 1;
        public static final int aNormal = 2;

        static final String VERTEX_SHADER = "" +
                "#version 300 es\n" +
                "\n" +
                "precision highp float;\n" +
                "\n" +
                "uniform mat4 uModelView;\n" +
                "uniform mat4 uProjection;\n" +
                "uniform mediump float uHalfWidth;\n" +
                "uniform mediump float uFilterWidth;\n" +
                "uniform mediump float uOutlineWidth;\n" +
                "uniform mediump vec2 uViewportSize;\n" +
                "\n" +
                "layout(location = " + aPosition0 + ") in vec3 aPosition0;\n" +
                "layout(location = " + aPosition1 + ") in vec3 aPosition1;\n" +
                "layout(location = " + aNormal + ") in vec4 aNormal;\n" +
                "\n" +
                "flat out vec2 fOrigin;\n" +
                "out vec2 vOffset;\n" +
                "flat out vec2 fNormal;\n" +
                "\n" +
                "void main() {\n" +
                "  vec4 p0 = uProjection * uModelView * vec4(aPosition0.xyz, 1.0);\n" +
                "  vec4 p1 = uProjection * uModelView * vec4(aPosition1.xyz, 1.0);\n" +
                "  vec4 screen_p0 = (p0 / p0.w)*vec4(uViewportSize, 1.0, 1.0);\n" +
                "  vec4 screen_p1 = (p1 / p1.w)*vec4(uViewportSize, 1.0, 1.0);\n" +
                "  float dist = distance(screen_p0.xy, screen_p1.xy);\n" +
                "  float flip = (2.0*aNormal.z) - 1.0;\n" +
                "  float dx = (screen_p1.x - screen_p0.x) * flip;\n" +
                "  float dy = (screen_p1.y - screen_p0.y) * flip;\n" +
                // select an origin to measure `gl_FragCoord` distance from.
                "  fOrigin = mix(screen_p0.xy, screen_p1.xy, aNormal.w);\n" +
                "  fOrigin.x = -1.0*mod(fOrigin.x, uViewportSize.x);\n" +
                "  fOrigin.y = -1.0*mod(fOrigin.y, uViewportSize.y);\n" +
                "  float normalDir = ((2.0*aNormal.x) - 1.0);\n" +
                "  float adjY = normalDir*(dy/dist)*((uHalfWidth + uFilterWidth + uOutlineWidth)/uViewportSize.x);\n" +
                "  float adjX = normalDir*(dx/dist)*((uHalfWidth + uFilterWidth + uOutlineWidth)/uViewportSize.y);\n" +
                "  gl_Position = mix(p0, p1, aNormal.z);\n" +
                "  gl_Position.x = gl_Position.x - adjY;\n" +
                "  gl_Position.y = gl_Position.y + adjX;\n" +
                // flip the normal used in the distance calculation here to avoid unnecessary per-fragment overhead
                "  fNormal = normalize(vec2(screen_p1.xy-screen_p0.xy)) * ((2.0*aNormal.y) - 1.0);\n" +
                // XXX - the offset should be specified as the signed stroke
                //       width (filter+outline+half width) scalar, but this is
                //       resulting in a seam down the adjoining triangle edges.
                "  vOffset = vec2(-normalDir*(dy/dist)*(uHalfWidth + uFilterWidth + uOutlineWidth), normalDir*(dx/dist)*(uHalfWidth + uFilterWidth + uOutlineWidth));\n" +
                "}\n";

        static final String FRAGMENT_SHADER = "" +
                "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform vec4 uColor;\n" +
                "uniform int uPattern;\n" +
                "uniform int uFactor;\n" +
                "uniform mediump float uHalfWidth;\n" +
                "uniform mediump float uFilterWidth;\n" +
                "uniform mediump float uOutlineWidth;\n" +
                "uniform vec4 uOutlineColor;\n" +
                "\n" +
                "in vec2 vOffset;\n" +
                "flat in vec2 fNormal;\n" +
                "flat in vec2 fOrigin;\n" +
                "\n" +
                "out vec4 fragmentColor;\n" +
                "\n" +
                "void main() {\n" +
                // measure the distance of the frag coordinate to the origin
                "  float d = dot(fNormal, gl_FragCoord.xy-fOrigin);\n" +
                "  int idist = int(d);\n" +
                "  float b0 = float((uPattern>>((idist/uFactor)%16))&0x1);\n" +
                "  float b1 = float((uPattern>>(((idist+1)/uFactor)%16))&0x1);\n" +
                "  float alpha = mix(b0, b1, fract(d));\n" +
                "  float offset = length(vOffset);\n" +
                "  float antiAlias = smoothstep(-uFilterWidth, 0.25, (uHalfWidth+uOutlineWidth)-offset);\n" +
                "  antiAlias *= antiAlias;\n" +
                "  float outline = smoothstep(-0.5, 0.5, uHalfWidth-offset);\n" +
                "  vec4 color = mix(uOutlineColor, uColor, outline);\n" +
                "  fragmentColor = vec4(color.rgb, color.a * alpha * antiAlias);\n" +
                "}\n";

        public int handle;

        public int uModelView;
        public int uProjection;
        public int uColor;
        public int uFilterWidth;
        public int uOutlineWidth;
        public int uOutlineColor;
        public int uHalfWidth;
        public int uViewportSize;
        public int uPattern;
        public int uFactor;

        /**
         * Compiles the antialiasing shader program.
         */
        private AntiAliasingProgram() {
            // create the programs and cache the uniform locations.
            this.handle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);

            this.uModelView = GLES30.glGetUniformLocation(this.handle, "uModelView");
            this.uProjection = GLES30.glGetUniformLocation(this.handle, "uProjection");
            this.uColor = GLES30.glGetUniformLocation(this.handle, "uColor");
            this.uHalfWidth = GLES30.glGetUniformLocation(this.handle, "uHalfWidth");
            this.uFilterWidth = GLES30.glGetUniformLocation(this.handle, "uFilterWidth");
            this.uOutlineWidth = GLES30.glGetUniformLocation(this.handle, "uOutlineWidth");
            this.uOutlineColor = GLES30.glGetUniformLocation(this.handle, "uOutlineColor");
            this.uViewportSize = GLES30.glGetUniformLocation(this.handle, "uViewportSize");
            this.uFactor = GLES30.glGetUniformLocation(this.handle, "uFactor");
            this.uPattern = GLES30.glGetUniformLocation(this.handle, "uPattern");
        }

        /**
         * Retrieves a static instance of the antialiasing program.
         * @return The static antialiasing program.
         */
        static AntiAliasingProgram get() {
            if (instance == null) {
                instance = new AntiAliasingProgram();
            }
            return instance;
        }

        /**
         * Compiles the given vertex and fragment shader sources into a shader program.
         * @param vSource The vertex shader source.
         * @param fSource The fragment shader source.
         * @return The handle to the shader program, or GL_NONE if an error occurred.
         */
        private static int createProgram(String vSource, String fSource) {
            int vsh = GLES20FixedPipeline.GL_NONE;
            int fsh = GLES20FixedPipeline.GL_NONE;
            int handle = GLES20FixedPipeline.GL_NONE;
            try {
                vsh = GLES20FixedPipeline.loadShader(GLES30.GL_VERTEX_SHADER, vSource);
                fsh = GLES20FixedPipeline.loadShader(GLES30.GL_FRAGMENT_SHADER, fSource);
                handle = GLES20FixedPipeline.createProgram(vsh, fsh);
            } finally {
                if (vsh != GLES30.GL_NONE)
                    GLES30.glDeleteShader(vsh);
                if (fsh != GLES30.GL_NONE)
                    GLES30.glDeleteShader(fsh);
            }
            return handle;
        }

    }
}
