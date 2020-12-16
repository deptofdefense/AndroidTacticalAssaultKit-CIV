
package com.atakmap.android.gridlines.graphics;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.MutableMGRSPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.opengl.GLAntiAliasedLine;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

class GLGridTile {

    private GeoPoint[] _actualPolygon;
    private FloatBuffer _polyBuf;
    private FloatBuffer _polyBufProjected;
    private final MutableGeoBounds _bounds = new MutableGeoBounds(0, 0, 0, 0);

    public static final String TAG = "GLGridTile";

    static int debugDrawCount;

    MGRSPoint mgrsRef;
    GeoPoint sw, nw, ne, se;
    private Vector2D swv, nwv, sev;
    int subResolution;
    private GLText glText;

    private void updatePolyBuf() {
        float[] data = new float[_actualPolygon.length * 2];
        int j = 0;
        double northBound = -Double.MAX_VALUE, eastBound = -Double.MAX_VALUE;
        double southBound = Double.MAX_VALUE, westBound = Double.MAX_VALUE;
        for (GeoPoint p : _actualPolygon) {
            data[j++] = (float) p.getLongitude();
            data[j++] = (float) p.getLatitude();
            northBound = Math.max(northBound, p.getLatitude());
            southBound = Math.min(southBound, p.getLatitude());
            eastBound = Math.max(eastBound, p.getLongitude());
            westBound = Math.min(westBound, p.getLongitude());
        }
        _bounds.set(northBound, westBound, southBound, eastBound);

        // Need to recalculate the northwest, southwest, and southeast
        // points for proper label placement
        GeoPoint nw = new GeoPoint(southBound, eastBound);
        GeoPoint sw = new GeoPoint(northBound, eastBound);
        GeoPoint se = new GeoPoint(northBound, westBound);
        for (GeoPoint p : _actualPolygon) {
            if (Math.hypot(p.getLongitude() - westBound,
                    p.getLatitude() - northBound) < Math.hypot(
                            nw.getLongitude() - westBound,
                            nw.getLatitude() - northBound))
                nw = p;
            if (Math.hypot(p.getLongitude() - westBound,
                    p.getLatitude() - southBound) < Math.hypot(
                            sw.getLongitude() - westBound,
                            sw.getLatitude() - southBound))
                sw = p;
            if (Math.hypot(p.getLongitude() - eastBound,
                    p.getLatitude() - southBound) < Math.hypot(
                            se.getLongitude() - eastBound,
                            se.getLatitude() - southBound))
                se = p;
        }
        this.nwv = new Vector2D(nw.getLongitude(), nw.getLatitude());
        this.swv = new Vector2D(sw.getLongitude(), sw.getLatitude());
        this.sev = new Vector2D(se.getLongitude(), se.getLatitude());

        ByteBuffer bb = com.atakmap.lang.Unsafe.allocateDirect(data.length
                * (Float.SIZE / 8));
        bb.order(ByteOrder.nativeOrder());
        _polyBuf = bb.asFloatBuffer();
        _polyBuf.put(data);
    }

    GeoPoint[] setActualPolygon(GeoPoint[] value) {
        _actualPolygon = value;
        updatePolyBuf();
        _antiAliasedLineRenderer.setLineData(_actualPolygon, 2,
                GLAntiAliasedLine.ConnectionType.FORCE_CLOSE);
        return _actualPolygon;
    }

    private GeoBounds getBounds() {
        if (_actualPolygon == null)
            _bounds.set(
                    Math.max(nw.getLatitude(), ne.getLatitude()),
                    Math.min(nw.getLongitude(), sw.getLongitude()),
                    Math.min(sw.getLatitude(), se.getLatitude()),
                    Math.max(ne.getLongitude(), se.getLongitude()));
        return _bounds;
    }

    boolean inView(GLMapView ortho) {
        GeoBounds bounds = getBounds();
        if (bounds.getNorth() < ortho.southBound
                || bounds.getSouth() > ortho.northBound)
            return false;
        if (ortho.crossesIDL) {
            if (ortho.eastBoundUnwrapped > 180 && bounds.getWest() < 0
                    && bounds.getWest() + 360 <= ortho.eastBoundUnwrapped)
                return true;
            if (ortho.westBoundUnwrapped < -180 && bounds.getEast() > 0
                    && bounds.getEast() - 360 >= ortho.westBoundUnwrapped)
                return true;
        }
        return !(bounds.getWest() > ortho.eastBoundUnwrapped
                || bounds.getEast() < ortho.westBoundUnwrapped);
    }

    void drawOrtho(GLMapSurface surface, GLMapView ortho, float red,
            float green, float blue) {

        if (glText == null) {
            glText = GLText.getInstance(MapView.getDefaultTextFormat());
        }

        if (lastDrawVersionS != ortho.drawVersion) {
            lastDrawVersionS = ortho.drawVersion;

            float left = ortho.getLeft();
            float right = ortho.getRight();
            float top = ortho.getTop()
                    - MapView.getMapView().getActionBarHeight()
                    - glText.getStringHeight();
            float bottom = ortho.getBottom();

            float[] indata = {
                    left, bottom, left, top, right, top
            };
            FloatBuffer inbuf = FloatBuffer.wrap(indata);
            FloatBuffer outbuf = FloatBuffer.allocate(6);
            ortho.inverse(inbuf, outbuf);

            bottomLeft = new Vector2D(outbuf.get(0), outbuf.get(1));
            topLeft = new Vector2D(outbuf.get(2), outbuf.get(3));
            topRight = new Vector2D(outbuf.get(4), outbuf.get(5));

            int smallDim = Math.min(
                    Math.abs(ortho.getTop() - ortho.getBottom()),
                    Math.abs(ortho.getRight() - ortho.getLeft()));
            screenSpanMeters = ortho.drawMapResolution * smallDim
                    * Math.cos(ortho.drawLat * Math.PI / 180);
        }

        boolean drawSelf = true;

        if (screenSpanMeters < subResolution * 5.25 && subResolution >= 100) { //5.25 = Maximum number of grid squares along the small axis of the screen
            drawSelf = !_drawSubs(surface, ortho, red, green, blue);
        } else {
            dumpSubs();
        }

        if (drawSelf) {
            debugDrawCount++;

            double unwrap = ortho.idlHelper.getUnwrap(getBounds());
            if (_polyBufProjected == null
                    || lastDrawVersionM != ortho.drawVersion) {
                lastDrawVersionM = ortho.drawVersion;

                _setupCorners(ortho);

                if (this.swv == null)
                    this.swv = new Vector2D(this.sw.getLongitude(),
                            this.sw.getLatitude());
                if (this.sev == null)
                    this.sev = new Vector2D(this.se.getLongitude(),
                            this.se.getLatitude());
                if (this.nwv == null)
                    this.nwv = new Vector2D(this.nw.getLongitude(),
                            this.nw.getLatitude());

                double swvx = this.swv.x;
                double sevx = this.sev.x;
                double nwvx = this.nwv.x;

                if (unwrap > 0 && swvx < 0 || unwrap < 0 && swvx > 0)
                    swvx += unwrap;
                if (unwrap > 0 && sevx < 0 || unwrap < 0 && sevx > 0)
                    sevx += unwrap;
                if (unwrap > 0 && nwvx < 0 || unwrap < 0 && nwvx > 0)
                    nwvx += unwrap;

                Vector2D swv = new Vector2D(swvx, this.swv.y);
                Vector2D sev = new Vector2D(sevx, this.sev.y);
                Vector2D nwv = new Vector2D(nwvx, this.nwv.y);

                tiEasting = Vector2D.segmentToSegmentIntersection(topLeft,
                        topRight, swv, nwv);
                tiNorthing = Vector2D.segmentToSegmentIntersection(topLeft,
                        topRight, swv, sev);
                liEasting = Vector2D.segmentToSegmentIntersection(topLeft,
                        bottomLeft, swv, nwv);
                liNorthing = Vector2D.segmentToSegmentIntersection(topLeft,
                        bottomLeft, swv, sev);
            }

            _antiAliasedLineRenderer.draw(ortho, 0f, 0f, 0f, 1f, 4f);
            _antiAliasedLineRenderer.draw(ortho, red, green, blue, 1f, 2f);

            String text;

            if (tiEasting != null || liEasting != null) {
                text = mgrsRef.getEastingDescriptor();
                if (subResolution == 10000) {
                    text = mgrsRef.getGridDescriptor();
                }
                GLText.localize(text);
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glLoadIdentity();
                if (tiEasting != null) {
                    ortho.scratch.geo.set(tiEasting.y, tiEasting.x);
                    AbstractGLMapItem2.forward(ortho, ortho.scratch.geo,
                            ortho.scratch.pointF, unwrap);
                    float xoffset = -glText.getStringWidth(text) / 2f;
                    GLES20FixedPipeline.glTranslatef(xoffset
                            + ortho.scratch.pointF.x, ortho.scratch.pointF.y,
                            0f);
                } else {
                    ortho.scratch.geo.set(liEasting.y, liEasting.x);
                    AbstractGLMapItem2.forward(ortho, ortho.scratch.geo,
                            ortho.scratch.pointF, unwrap);
                    GLES20FixedPipeline.glTranslatef(ortho.scratch.pointF.x,
                            ortho.scratch.pointF.y, 0f);
                }
                GLNinePatch smallNinePatch = GLRenderGlobals.get(surface)
                        .getSmallNinePatch();
                if (smallNinePatch != null) {
                    GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
                    GLES20FixedPipeline.glPushMatrix();
                    GLES20FixedPipeline.glTranslatef(-4f, -glText.getDescent(),
                            0f);
                    smallNinePatch.draw(glText.getStringWidth(text) + 8f,
                            Math.max(16f, glText.getStringHeight()));
                    GLES20FixedPipeline.glPopMatrix();
                }
                glText.draw(text, red, green, blue, 1f);
                GLES20FixedPipeline.glPopMatrix();
            }

            if (tiNorthing != null || liNorthing != null) {
                text = mgrsRef.getNorthingDescriptor();
                if (subResolution == 10000) {
                    text = mgrsRef.getGridDescriptor();
                }
                text = GLText.localize(text);
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glLoadIdentity();
                if (tiNorthing != null) {
                    ortho.scratch.geo.set(tiNorthing.y, tiNorthing.x);
                    AbstractGLMapItem2.forward(ortho, ortho.scratch.geo,
                            ortho.scratch.pointF, unwrap);
                    float xoffset = -glText.getStringWidth(text) / 2f;
                    GLES20FixedPipeline.glTranslatef(xoffset
                            + ortho.scratch.pointF.x, ortho.scratch.pointF.y,
                            0f);
                } else {
                    ortho.scratch.geo.set(liNorthing.y, liNorthing.x);
                    AbstractGLMapItem2.forward(ortho, ortho.scratch.geo,
                            ortho.scratch.pointF, unwrap);
                    GLES20FixedPipeline.glTranslatef(ortho.scratch.pointF.x,
                            ortho.scratch.pointF.y, 0f);
                }
                GLNinePatch smallNinePatch = GLRenderGlobals.get(surface)
                        .getSmallNinePatch();
                if (smallNinePatch != null) {
                    GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
                    GLES20FixedPipeline.glPushMatrix();
                    GLES20FixedPipeline.glTranslatef(-4f, -glText.getDescent(),
                            0f);
                    smallNinePatch.draw(glText.getStringWidth(text) + 8f,
                            Math.max(16f, glText.getStringHeight()));
                    GLES20FixedPipeline.glPopMatrix();
                }
                glText.draw(text, red, green, blue, 1f);
                GLES20FixedPipeline.glPopMatrix();
            }
        }
    }

    private void dumpSubs() {
        _subs = null; // release a hunk of memory
        if (_loadingSubs != null) {
            _loadingSubs.cancel(true);
            _loadingSubs = null;
        }
    }

    private void _startLoadingSubs(final GLMapSurface surface) {
        GeoBounds bounds = getBounds();
        final double south = bounds.getSouth();
        final double west = bounds.getWest();
        final double north = bounds.getNorth();
        final double east = bounds.getEast();
        final int subRes = subResolution;
        final MGRSPoint ref = mgrsRef;
        Callable<GLGridTile[][]> loadJob = new Callable<GLGridTile[][]>() {
            @Override
            public GLGridTile[][] call() {
                try {
                    return _genTileGrid(south, west, north, east, ref, subRes);
                } finally {
                    surface.requestRender();
                }
            }
        };
        FutureTask<GLGridTile[][]> task = new FutureTask<>(
                loadJob);
        surface.getBackgroundMathExecutor().execute(task);
        _loadingSubs = task;
    }

    private boolean _drawSubs(GLMapSurface surface, GLMapView ortho, float red,
            float green,
            float blue) {

        boolean result = false;

        if (_subs == null && _loadingSubs == null) {
            _startLoadingSubs(surface);
        }

        if (_loadingSubs != null && _loadingSubs.isDone()) {
            try {
                _subs = _loadingSubs.get();
            } catch (Exception e) {
                Log.e(TAG, "error: ", e);
            }
            _loadingSubs = null;
        }

        if (_subs != null) {
            result = true;
            for (GLGridTile[] _sub : _subs) {
                for (GLGridTile t : _sub) {
                    if (t.inView(ortho)) {
                        t.drawOrtho(surface, ortho, red, green, blue);
                    } else {
                        t.dumpSubs();
                    }
                }
            }
        }

        return result;
    }

    private void _setupCorners(GLMapView map) {

        ByteBuffer bb = com.atakmap.lang.Unsafe
                .allocateDirect(_actualPolygon.length * 2
                        * (Float.SIZE / 8));
        bb.order(ByteOrder.nativeOrder());
        //        _polyBufProjected = bb.asFloatBuffer();
        _polyBufProjected = Unsafe.allocateDirect(_polyBuf.limit(),
                FloatBuffer.class);//bb.asFloatBuffer();
        _polyBuf.rewind();
        AbstractGLMapItem2.forward(map, _polyBuf, _polyBufProjected,
                getBounds());
    }

    private static GLGridTile[][] _genTileGrid(double south, double west,
            double north,
            double east, MGRSPoint mgrsRef, int subRes) {
        ArrayList<GLGridTile[]> grid = new ArrayList<>();

        GLGridTile[] row;

        MutableMGRSPoint currRef = new MutableMGRSPoint(mgrsRef);
        currRef.alignMeters(subRes, subRes);

        for (int j = 0; j < 10; ++j) {
            row = _genTileRow(new MutableMGRSPoint(currRef), west, east, south,
                    north, subRes);
            if (row.length > 0) {
                grid.add(row);
            }
            currRef.offset(0, subRes);
            currRef.alignYMeters(subRes);
        }

        return grid.toArray(new GLGridTile[grid.size()][]);
    }

    private static GLGridTile[] _genTileRow(MutableMGRSPoint currRef,
            double west, double east, double south, double north,
            int subRes) {

        ArrayList<GLGridTile> row = new ArrayList<>();
        double[] ll = {
                0d, 0d
        };

        GLGridTile p = null;
        for (int i = 0; i < 10; ++i) {
            GLGridTile t;
            t = new GLGridTile();
            t.subResolution = subRes / 10;

            t.mgrsRef = new MGRSPoint(currRef);
            if (p != null) {
                t.sw = p.se;
            } else {
                t.sw = _toGeo(currRef, ll);
            }

            currRef.offset(0, subRes);
            currRef.alignYMeters(subRes);

            if (p != null) {
                t.nw = p.ne;
            } else {
                t.nw = _toGeo(currRef, ll);
            }

            currRef.offset(subRes, 0);
            currRef.alignXMeters(subRes);
            t.ne = _toGeo(currRef, ll);

            currRef.offset(0, -subRes);

            t.se = _toGeo(currRef, ll);

            p = t;

            GLGridTile clipped = GLZoneRegion._clipTile(t, south, west, north,
                    east);

            if (clipped != null) {
                row.add(clipped);
            }
        }
        return row.toArray(new GLGridTile[0]);
    }

    private static GeoPoint _toGeo(MGRSPoint mgrsPoint, double[] out) {
        double[] ll = mgrsPoint.toLatLng(out);
        return new GeoPoint(ll[0], ll[1]);
    }

    private static double screenSpanMeters;
    private static int lastDrawVersionS;
    private static Vector2D bottomLeft;
    private static Vector2D topLeft;
    private static Vector2D topRight;

    private int lastDrawVersionM;
    private Vector2D tiEasting;
    private Vector2D tiNorthing;
    private Vector2D liEasting;
    private Vector2D liNorthing;

    private GLGridTile[][] _subs; // 10x10, except near zone boundaries
    private Future<GLGridTile[][]> _loadingSubs;
    private final GLAntiAliasedLine _antiAliasedLineRenderer = new GLAntiAliasedLine();
}
