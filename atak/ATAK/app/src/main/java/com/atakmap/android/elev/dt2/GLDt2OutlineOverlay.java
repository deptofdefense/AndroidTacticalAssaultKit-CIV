
package com.atakmap.android.elev.dt2;

import android.opengl.GLES30;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLAbstractLayer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLAntiAliasedLine;
import com.atakmap.map.opengl.GLAntiAliasedLine.ConnectionType;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class GLDt2OutlineOverlay extends GLAbstractLayer2
        implements Dt2FileWatcher.Listener {

    private static final float[][] DTED_COLORS = {
            {
                    1.0f, 1.0f, 0.0f
            },
            {
                    1.0f, 0.667f, 0.0f
            },
            {
                    1.0f, 0.333f, 0.0f
            },
            {
                    1.0f, 0.0f, 0.0f
            },
    };

    public static class Instance extends AbstractLayer {
        public Instance(MapView mapView) {
            super(mapView.getContext().getString(R.string.elevation_data));
        }
    }

    private final Instance _subject;
    private final Dt2FileWatcher _dtedWatcher;
    private final GLAntiAliasedLine _line;
    private final GeoPoint[] _corners;
    private final DoubleBuffer _quadBufLL;
    private final FloatBuffer _quadBufGL;

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof Instance)
                return new GLDt2OutlineOverlay(surface, (Instance) layer);
            return null;
        }
    };

    private GLDt2OutlineOverlay(MapRenderer surface, Instance subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE);
        _subject = subject;
        _dtedWatcher = Dt2FileWatcher.getInstance();
        _line = new GLAntiAliasedLine();
        _corners = new GeoPoint[] {
                GeoPoint.createMutable(),
                GeoPoint.createMutable(),
                GeoPoint.createMutable(),
                GeoPoint.createMutable()
        };

        ByteBuffer bb = Unsafe.allocateDirect(4 * 2 * 8);
        bb.order(ByteOrder.nativeOrder());
        _quadBufLL = bb.asDoubleBuffer();

        bb = Unsafe.allocateDirect(4 * 2 * 4);
        bb.order(ByteOrder.nativeOrder());
        _quadBufGL = bb.asFloatBuffer();
    }

    @Override
    public void release() {
        Unsafe.free(_quadBufLL);
        Unsafe.free(_quadBufGL);
    }

    @Override
    public void start() {
        super.start();
        _dtedWatcher.addListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        _dtedWatcher.removeListener(this);
    }

    @Override
    public Layer getSubject() {
        return _subject;
    }

    @Override
    public void onDtedFilesUpdated() {
        invalidate();
    }

    @Override
    public void drawImpl(GLMapView ortho, int renderPass) {

        // Only draw on surface
        if (!MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
            return;

        // Don't draw past a certain resolution
        if (ortho.currentPass.drawMapResolution > 10000)
            return;

        // East and west bound correction
        GLMapView.ScratchPad s = ortho.scratch;
        s.pointF.x = ortho.getLeft();
        s.pointF.y = ortho.getTop();
        ortho.inverse(s.pointF, _corners[0]);

        s.pointF.x = ortho.getRight();
        ortho.inverse(s.pointF, _corners[1]);

        s.pointF.y = ortho.getBottom();
        ortho.inverse(s.pointF, _corners[2]);

        s.pointF.x = ortho.getLeft();
        ortho.inverse(s.pointF, _corners[3]);

        double westBound = 360, eastBound = -360;
        for (GeoPoint gp : _corners) {
            double lng = gp.getLongitude();
            if (ortho.currentPass.crossesIDL) {
                if (ortho.currentPass.drawLng > 0 && lng < 0)
                    lng += 360;
                else if (ortho.currentPass.drawLng < 0 && lng > 0)
                    lng -= 360;
            }
            westBound = Math.min(westBound, lng);
            eastBound = Math.max(eastBound, lng);
        }

        int interval = (int) Math
                .ceil(ortho.currentPass.drawMapResolution / 2000);
        int minLng = (int) Math.floor(westBound / interval) * interval;
        int maxLng = (int) Math.floor(eastBound / interval) * interval;
        int minLat = (int) Math.floor(ortho.currentPass.southBound / interval)
                * interval;
        int maxLat = (int) Math.floor(ortho.currentPass.northBound / interval)
                * interval;

        BitSet[] coverages = _dtedWatcher.getCoverages();
        BitSet coverage = _dtedWatcher.getFullCoverage();

        // Draw fill cells
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        GLES20FixedPipeline.glEnable(GLES30.GL_CULL_FACE);
        GLES20FixedPipeline.glCullFace(GLES30.GL_FRONT);

        for (int lat = minLat; lat <= maxLat; lat += interval) {
            for (int lng = minLng; lng <= maxLng; lng += interval) {
                for (int level = coverages.length - 1; level >= 0; level--) {
                    final BitSet bs = coverages[level];
                    final int coverageIndex = Dt2FileWatcher
                            .getCoverageIndex(lat, lng);
                    if (coverageIndex < bs.length() && bs.get(coverageIndex)) {
                        final float[] c = DTED_COLORS[level];
                        GLES20FixedPipeline.glColor4f(c[0], c[1], c[2], 0.3f);
                        drawQuad(ortho, lat, lng, lat + interval,
                                lng + interval);
                        break;
                    }
                }
            }
        }

        GLES20FixedPipeline.glCullFace(GLES30.GL_BACK);
        GLES20FixedPipeline.glDisable(GLES30.GL_CULL_FACE);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        // Draw latitudinal lines
        boolean hadTile = false;
        boolean lastLat = false;
        List<GeoPoint> line = new ArrayList<>();
        for (int lat = minLat; lat <= maxLat; lat += interval) {
            boolean hasLat = false;
            for (int lng = minLng; lng <= maxLng; lng += interval) {
                boolean hasTile = coverage.get(Dt2FileWatcher.getCoverageIndex(
                        lat, lng));
                if (!hasTile && lastLat) {
                    hasTile = coverage.get(Dt2FileWatcher.getCoverageIndex(
                            lat - interval, lng));
                }
                if (hasTile != hadTile) {
                    if (hasTile)
                        line.add(new GeoPoint(lat, lng));
                    else
                        drawLine(ortho, line);
                    hadTile = hasTile;
                }
                if (hasTile) {
                    line.add(new GeoPoint(lat, lng + interval));
                    hasLat = true;
                }
            }
            drawLine(ortho, line);
            hadTile = false;
            lastLat = hasLat;
        }

        // Draw longitudinal lines
        boolean lastLng = false;
        for (int lng = minLng; lng <= maxLng; lng += interval) {
            boolean hasLng = false;
            for (int lat = minLat; lat <= maxLat; lat += interval) {
                boolean hasTile = coverage.get(Dt2FileWatcher.getCoverageIndex(
                        lat, lng));
                if (!hasTile && lastLng) {
                    hasTile = coverage.get(Dt2FileWatcher.getCoverageIndex(
                            lat, lng - interval));
                }
                if (hasTile != hadTile) {
                    if (hasTile)
                        line.add(new GeoPoint(lat, lng));
                    else
                        drawLine(ortho, line);
                    hadTile = hasTile;
                }
                if (hasTile) {
                    line.add(new GeoPoint(lat + interval, lng));
                    hasLng = true;
                }
            }
            drawLine(ortho, line);
            hadTile = false;
            lastLng = hasLng;
        }
    }

    private void drawLine(GLMapView ortho, List<GeoPoint> line) {
        if (!line.isEmpty()) {
            _line.setLineData(line.toArray(new GeoPoint[0]), 2,
                    ConnectionType.AS_IS);
            _line.draw(ortho, 1f, 1f, 1f, 1f, 4);
        }
        line.clear();
    }

    private void drawQuad(GLMapView ortho, double lat1, double lng1,
            double lat2, double lng2) {
        _quadBufLL.clear();

        // Bottom-right
        _quadBufLL.put(lng2);
        _quadBufLL.put(lat1);

        // Top-right
        _quadBufLL.put(lng2);
        _quadBufLL.put(lat2);

        // Top-left
        _quadBufLL.put(lng1);
        _quadBufLL.put(lat2);

        // Bottom-left
        _quadBufLL.put(lng1);
        _quadBufLL.put(lat1);

        _quadBufLL.clear();

        ortho.forward(_quadBufLL, 2, _quadBufGL, 2);

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _quadBufGL);
        GLES20FixedPipeline.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4);
    }
}
