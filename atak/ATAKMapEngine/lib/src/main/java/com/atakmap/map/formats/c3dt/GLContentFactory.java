package com.atakmap.map.formats.c3dt;

import android.opengl.GLES30;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.Matrix;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.WeakHashMap;

final class GLContentFactory {
    private final static String TAG = "GLContentFactory";

    private final static Matrix Y_UP_TO_Z_UP = new Matrix(1d, 0d, 0d, 0d,
            0d, 0d, -1d, 0d,
            0d, 1d, 0d, 0d,
            0d, 0d, 0d, 1d);

    private GLContentFactory() {}

    public static GLContent create(RenderContext ctx, ResourceManager resmgr, String baseUri, Tile tile) {
        if(tile.content == null)
            return null;
        if(tile.content.uri == null)
            return null;
        Content absContent = new Content();
        absContent.uri = baseUri + tile.content.uri;
        absContent.boundingVolume = tile.content.boundingVolume;
        String absContentUri = absContent.uri.toLowerCase(LocaleUtil.getCurrent());
        if(absContentUri.endsWith(".b3dm"))
            return new GLB3DM(tile, absContent);
        else if(absContentUri.endsWith(".pnts"))
            return new GLPNTS(tile, absContent);
        else if(absContentUri.endsWith(".json"))
            return new GLExternalTileset(tile, resmgr, absContent);
        else
            return null;
    }

    final static class GLB3DM implements GLContent {
        Tile tile;
        Content content;
        B3DM b3dm;
        PointD rtc_center = new PointD(0d, 0d, 0d);
        Matrix ecef2lla;
        Matrix transform;
        State state;

        GLB3DM(Tile tile, Content content) {
            this.content = content;
            this.tile = tile;
            this.transform = Tile.accumulate(tile);
            this.state = State.Loading;
        }

        @Override
        public boolean draw(MapRendererState view) {
            if(b3dm == null || b3dm.gltf == null)
                return false;

            // pull the projection matrix from the graphics state
            view.scratch.matrix.set(view.projection);
            // concatenate the scene transform
            view.scratch.matrix.concatenate(view.scene.forward);

            // convert from ECEF to LLA
            if(view.drawSrid == 4326)
                view.scratch.matrix.concatenate(ecef2lla);

            // apply tile transform, if present
            if(this.transform != null)
                view.scratch.matrix.concatenate(transform);

            // apply RTC
            view.scratch.matrix.translate(rtc_center.x, rtc_center.y, rtc_center.z);
            // apply y-up to z-up
            view.scratch.matrix.concatenate(Y_UP_TO_Z_UP);

            view.scratch.matrix.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
            b3dm.gltf.draw(!view.isDepthTest, view.uMVP, view.scratch.matrixD);
            return true;
        }

        @Override
        public void release() {
            if(b3dm != null) {
                if(b3dm.gltf != null)
                    b3dm.gltf.release();
                b3dm = null;
            }
        }

        @Override
        public boolean loadContent(RenderContext ctx, ContentSource handler) {
            if(b3dm != null)
                return true;

            try {
                // load the B3DM
                final B3DM _b3dm;
                File f = new File(content.uri);
                if (IOProviderFactory.exists(f)) {
                    _b3dm = B3DM.parse(new File(content.uri));
                } else {
                    final byte[] slurp = handler.getData(content.uri, null);
                    _b3dm = (slurp != null) ? B3DM.parse(ByteBuffer.wrap(slurp), Util.resolve(content.uri), handler) : null;
                }

                if(_b3dm == null) {
                    this.state = State.Failed;
                    return false;
                }
                if(_b3dm != null && _b3dm.featureTable != null && _b3dm.featureTable.hasProperty("RTC_CENTER")) {
                    double[] o = _b3dm.featureTable.getDoubleArray("RTC_CENTER");
                    rtc_center.x = o[0];
                    rtc_center.y = o[1];
                    rtc_center.z = o[2];
                }

                Envelope aabb = Tile.approximateBounds(tile);

                Matrix lla2ecef = lla2ecef((aabb.minY+aabb.maxY)/2d, (aabb.minX+aabb.maxX)/2d);
                try {
                    ecef2lla = lla2ecef.createInverse();
                } catch(NoninvertibleTransformException e) {
                    return false;
                }

                b3dm = _b3dm;

                // bind the GLTF
                if(_b3dm.gltf != null) {
                    if (ctx.isRenderThread()) {
                        b3dm.gltf.bind();
                        this.state = State.Loaded;
                    } else {
                        ctx.queueEvent(new Runnable() {
                            public void run() {
                                if (b3dm == _b3dm) {
                                    _b3dm.gltf.bind();
                                    state = State.Loaded;
                                }
                            }
                        });
                    }
                }


                return true;
            } catch(Throwable e) {
                this.state = State.Failed;
                return false;
            }
        }

        @Override
        public State getState() {
            return this.state;
        }
    }

    final static class GLExternalTileset implements GLContent {
        Tile tile;
        Content content;
        GLTile external;
        Matrix xform;
        ResourceManager resmgr;
        State state;

        GLExternalTileset(Tile tile, ResourceManager resmgr, Content content) {
            this.tile = tile;
            this.resmgr = resmgr;
            this.content = content;
            this.xform = Tile.accumulate(tile);
            this.state = State.Loading;
        }

        @Override
        public boolean draw(MapRendererState view) {
            if(external == null)
                return false;
            return external.draw(view, false);
        }

        @Override
        public void release() {
            if(external != null)
                external.release();
            external = null;
        }

        @Override
        public boolean loadContent(RenderContext ctx, ContentSource handler) {
            if(external != null)
                return true;

            try {
                String json;
                File f = new File(content.uri);
                if (IOProviderFactory.exists(f)) {
                    json = FileSystemUtils.copyStreamToString(f);
                } else {
                    final byte[] result = handler.getData(content.uri, null);
                    if (result != null) {
                        json = new String(result, FileSystemUtils.UTF8_CHARSET);
                    } else {
                        this.state = State.Failed;
                        return false;
                    }
                }

                JSONObject o = new JSONObject(json);
                Tileset ts = Tileset.parse(o, this.tile);
                external = new GLTile(ts, ts.root, 0, content.uri.substring(0, content.uri.lastIndexOf('/')+1));
                external.resmgr = resmgr;
                external.source = handler;
                this.state = State.Loaded;
                return true;
            } catch(Throwable t) {
                this.state = State.Failed;
                return false;
            }
        }

        @Override
        public State getState() {
            return this.state;
        }
    }

    private static Matrix lla2ecef(double lat, double lng) {
        final Matrix mx = Matrix.getIdentity();

        GeoPoint llaOrigin = new GeoPoint(lat, lng, 0d);
        PointD ecefOrgin = ECEFProjection.INSTANCE.forward(llaOrigin, null);

        // if draw projection is ECEF and source comes in as LLA, we can
        // transform from LLA to ECEF by creating a local ENU CS and
        // chaining the following conversions (all via matrix)
        // 1. LCS -> LLA
        // 2. LLA -> ENU
        // 3. ENU -> ECEF
        // 4. ECEF -> NDC (via MapSceneModel 'forward' matrix)

        // construct ENU -> ECEF
        final double phi = Math.toRadians(llaOrigin.getLatitude());
        final double lambda = Math.toRadians(llaOrigin.getLongitude());

        mx.translate(ecefOrgin.x, ecefOrgin.y, ecefOrgin.z);

        Matrix enu2ecef = new Matrix(
                -Math.sin(lambda), -Math.sin(phi)*Math.cos(lambda), Math.cos(phi)*Math.cos(lambda), 0d,
                Math.cos(lambda), -Math.sin(phi)*Math.sin(lambda), Math.cos(phi)*Math.sin(lambda), 0d,
                0, Math.cos(phi), Math.sin(phi), 0d,
                0d, 0d, 0d, 1d
        );

        mx.concatenate(enu2ecef);

        // construct LLA -> ENU
        final double metersPerDegLat = GeoCalculations.approximateMetersPerDegreeLatitude(llaOrigin.getLatitude());
        final double metersPerDegLng = GeoCalculations.approximateMetersPerDegreeLongitude(llaOrigin.getLatitude());

        mx.scale(metersPerDegLng, metersPerDegLat, 1d);
        mx.translate(-llaOrigin.getLongitude(), -llaOrigin.getLatitude(), -llaOrigin.getAltitude());

        return mx;
    }

    final static class GLPNTS implements GLContent {

        static Map<RenderContext, Integer> programs = new WeakHashMap<>();

        Tile tile;
        Content content;
        PNTS pnts;
        PointD rtc_center = new PointD(0d, 0d, 0d);
        Matrix ecef2lla;
        Matrix transform;

        int program;
        int uMVP;
        int uPointSize;
        int aVertexCoords;
        int aColors;
        int aAlpha;
        int[] vbo;
        int normalIdx;
        Matrix quantizedPositionMapping;
        int numPoints;
        int aColorsSize;

        int aVertexCoordsOffset = -1;
        int aVertexCoordsType;
        int aColorsOffset = -1;

        private State state;


        GLPNTS(Tile tile, Content content) {
            this.content = content;
            this.tile = tile;
            this.transform = Tile.accumulate(tile);
            this.state = State.Loading;
        }

        @Override
        public boolean draw(MapRendererState view) {
            if(pnts == null || vbo == null)
                return false;

            // pull the projection matrix from the graphics state
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            for(int i = 0; i < 16; i++)
                view.scratch.matrix.set(i%4, i/4, view.scratch.matrixF[i]);

            view.scratch.matrix.concatenate(view.scene.forward);

            // convert from ECEF to LLA
            if(view.drawSrid == 4326)
                view.scratch.matrix.concatenate(ecef2lla);

            // apply tile transform, if present
            if(this.transform != null)
                view.scratch.matrix.concatenate(transform);

            // apply RTC
            view.scratch.matrix.translate(rtc_center.x, rtc_center.y, rtc_center.z);

            // dequantize, if applicable
            if(quantizedPositionMapping != null)
                view.scratch.matrix.concatenate(quantizedPositionMapping);

            view.scratch.matrix.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
            for(int i = 0; i < 16; i++)
                view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];

            GLES30.glUseProgram(program);
            GLES30.glUniformMatrix4fv(uMVP, 1, false, view.scratch.matrixF, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);

            // XXX - with refine mode of "add" need to use min error for all added datasets
            GLES30.glUniform1f(uPointSize, 8f);

            GLES30.glEnableVertexAttribArray(aVertexCoords);
            GLES30.glVertexAttribPointer(aVertexCoords, 3, aVertexCoordsType, false, 0, aVertexCoordsOffset);

            if(aColorsOffset >= 0) {
                GLES30.glEnableVertexAttribArray(aColors);
                GLES30.glVertexAttribPointer(aColors, aColorsSize, GLES30.GL_UNSIGNED_BYTE, true, 0, aColorsOffset);
            } else {
                GLES30.glVertexAttrib4f(aColors, 1f, 1f, 1f, 1f);
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);

            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, numPoints);

            GLES30.glDisableVertexAttribArray(aVertexCoords);
            GLES30.glDisableVertexAttribArray(aColors);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
            return true;
        }

        @Override
        public void release() {
            if(pnts != null) {
                if(vbo != null) {
                    GLES30.glDeleteBuffers(vbo.length, vbo, 0);
                    vbo = null;
                }
                pnts = null;
            }
        }

        @Override
        public boolean loadContent(final RenderContext ctx, ContentSource handler) {
            if(pnts != null)
                return true;

            try {
                // load the B3DM
                final PNTS _pnts;
                File f = new File(content.uri);
                if (IOProviderFactory.exists(f)) {
                    _pnts = PNTS.parse(new File(content.uri));
                } else {
                    final byte[] slurp = handler.getData(content.uri, null);
                    _pnts = (slurp != null) ? PNTS.parse(ByteBuffer.wrap(slurp)) : null;
                }
                if(_pnts == null) {
                    this.state = State.Failed;
                    return false;
                }
                if(_pnts != null && _pnts.featureTable != null && _pnts.featureTable.hasProperty("RTC_CENTER")) {
                    double[] o = _pnts.featureTable.getDoubleArray("RTC_CENTER");
                    rtc_center.x = o[0];
                    rtc_center.y = o[1];
                    rtc_center.z = o[2];
                }

                Envelope aabb = Tile.approximateBounds(tile);

                Matrix lla2ecef = lla2ecef((aabb.minY+aabb.maxY)/2d, (aabb.minX+aabb.maxX)/2d);
                try {
                    ecef2lla = lla2ecef.createInverse();
                } catch(NoninvertibleTransformException e) {
                    return false;
                }

                pnts = _pnts;

                // bind the GLTF
                if(_pnts.featureTable != null) {
                    if (ctx.isRenderThread()) {
                        bind(ctx, GLPNTS.this, pnts);
                    } else {
                        ctx.queueEvent(new Runnable() {
                            public void run() {
                                if (pnts == _pnts)
                                    bind(ctx, GLPNTS.this, _pnts);
                            }
                        });
                    }
                }

                this.state = State.Loaded;
                return true;
            } catch(Throwable e) {
                this.state = State.Failed;
                return false;
            }
        }

        @Override
        public State getState() {
            return state;
        }

        static void bind(RenderContext ctx, GLPNTS gl, PNTS pnts) {
            if(pnts.featureTable == null)
                return;
            if(pnts.featureTable.json == null)
                return;
            gl.numPoints = pnts.featureTable.json.optInt("POINTS_LENGTH", 0);
            if(gl.numPoints < 1)
                return;

            gl.vbo = new int[1];
            GLES30.glGenBuffers(gl.vbo.length, gl.vbo, 0);
            {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gl.vbo[0]);
                ByteBuffer data = Unsafe.allocateDirect(pnts.featureTable.binary.length);
                data.put(pnts.featureTable.binary);
                data.flip();
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, pnts.featureTable.binary.length, data, GLES30.GL_STATIC_DRAW);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
            }

            int vbo = 0;
            // position data

            JSONObject position = pnts.featureTable.json.optJSONObject("POSITION");
            JSONObject position_quantized = pnts.featureTable.json.optJSONObject("POSITION_QUANTIZED");
            if(position != null) {
                gl.aVertexCoordsType = GLES30.GL_FLOAT;
                gl.aVertexCoordsOffset = position.optInt("byteOffset", -1);
                if(gl.aVertexCoordsOffset < 0)
                    return;
            } else if(position_quantized != null) {
                final JSONArray quantizedVolumeScale = pnts.featureTable.json.optJSONArray("QUANTIZED_VOLUME_SCALE");
                final JSONArray quantizedVolumeOffset = pnts.featureTable.json.optJSONArray("QUANTIZED_VOLUME_OFFSET");
                if(quantizedVolumeOffset == null || quantizedVolumeScale == null)
                    return;
                gl.aVertexCoordsType = GLES30.GL_UNSIGNED_SHORT;
                gl.aVertexCoordsOffset = position_quantized.optInt("byteOffset", -1);
                if(gl.aVertexCoordsOffset < 0)
                    return;

                gl.quantizedPositionMapping = Matrix.getIdentity();
                gl.quantizedPositionMapping.translate(quantizedVolumeOffset.optDouble(0, 0d), quantizedVolumeOffset.optDouble(1, 0d), quantizedVolumeOffset.optDouble(2, 0d));
                gl.quantizedPositionMapping.scale(quantizedVolumeScale.optDouble(0, 1d)/65535d, quantizedVolumeOffset.optDouble(1, 1d)/65535d, quantizedVolumeOffset.optDouble(2, 1d)/65535d);
            } else {
                // one of POSITION or POSITION_QUANTIZED must be specified
                return;
            }

            JSONObject rgba = pnts.featureTable.json.optJSONObject("RGBA");
            JSONObject rgb = pnts.featureTable.json.optJSONObject("RGB");
            if(rgba != null) {
                gl.aColorsSize = 4;
                gl.aColorsOffset = rgba.optInt("byteOffset", -1);
                if(gl.aColorsOffset < 0)
                    return;
            } else if(rgb != null) {
                gl.aColorsSize = 3;
                gl.aColorsOffset = rgb.optInt("byteOffset", -1);
                if(gl.aColorsOffset < 0)
                    return;
            }

            Integer program = programs.get(ctx);
            if(program == null) {
                final String VERTEX_SHADER_SRC =             // vertex shader source
                        "uniform mat4 uMVP;\n" +
                        "uniform float uPointSize;\n" +
                        "attribute vec3 aVertexCoords;\n" +
                        "attribute vec4 aColors;\n" +
                        "varying vec4 vColor;\n" +
                        "void main() {\n" +
                        "  vColor = aColors;\n" +
                        "  gl_PointSize = uPointSize;\n" +
                        "  gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);\n" +
                        "}";

                final String FRAGMENT_SHADER_SRC = // fragment shader source
                        "precision mediump float;\n" +
                        "varying vec4 vColor;\n" +
                        "void main(void) {\n" +
                        "  gl_FragColor = vColor;\n" +
                        "}";

                int vsh = GLES20FixedPipeline.GL_NONE;
                int fsh = GLES20FixedPipeline.GL_NONE;
                try {
                    vsh = GLES20FixedPipeline.loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_SRC);
                    fsh = GLES20FixedPipeline.loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC);
                    program = Integer.valueOf(GLES20FixedPipeline.createProgram(vsh, fsh));
                } finally {
                    if (vsh != GLES30.GL_NONE)
                        GLES30.glDeleteShader(vsh);
                    if (fsh != GLES30.GL_NONE)
                        GLES30.glDeleteShader(fsh);
                }
                programs.put(ctx, program);
            }
            gl.program = program;
            gl.aVertexCoords = GLES30.glGetAttribLocation(gl.program, "aVertexCoords");
            gl.aColors = GLES30.glGetAttribLocation(gl.program, "aColors");
            gl.uMVP = GLES30.glGetUniformLocation(gl.program, "uMVP");
            gl.uPointSize = GLES30.glGetUniformLocation(gl.program, "uPointSize");
        }
    }

}
