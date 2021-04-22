
package com.atakmap.android.gridlines.graphics;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.MutableMGRSPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.Globe;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;
import com.atakmap.util.DirectExecutorService;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

class GLZoneRegion {

    public static final String TAG = "GLZoneRegion";

    GLZoneRegion(double south, double west, double north, double east) {
        _south = south;
        _west = west;
        _north = north;
        _east = east;

        final double centerLat = (south + north) / 2d;
        final double centerLng = (west + east) / 2d;
        final String zoneDescriptor = UTMPoint.getZoneDescriptorAt(centerLat,
                centerLng);
        _text = GLText.localize(zoneDescriptor);
    }

    public void draw(final GLMapSurface surface, GLMapView ortho, float red,
            float green, float blue, int renderPass) {

        // XXX - ideally we would only render label during sprites pass, but
        //       that would require occlusion detection so we can turn off
        //       depth test if the anchor point is visible

        boolean drawLabel = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);

        // only draw if close enough
        final double mapScaleReciprocal = (1.0d /
                Globe.getMapScale(ortho.currentScene.scene.dpi,
                        ortho.currentScene.drawMapResolution));
        if (mapScaleReciprocal < (2100000 * 2 * Math.PI)) {
            if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE)
                    && _grid == null && _pendingGrid == null) {
                Callable<GLGridTile[][]> loadJob = new Callable<GLGridTile[][]>() {
                    @Override
                    public GLGridTile[][] call() {
                        try {
                            return _genTileGrid(_south, _west, _north, _east,
                                    _genGridTileExecutor,
                                    _segmentLabelsExecutor);
                        } finally {
                            surface.requestRender();
                        }
                    }
                };
                FutureTask<GLGridTile[][]> task = new FutureTask<>(
                        loadJob);
                if (_genGridTileExecutor == null)
                    _genGridTileExecutor = surface.getBackgroundExecutor();
                _genGridTileExecutor.execute(task);
                _pendingGrid = task;
            }

            if (_pendingGrid != null && _pendingGrid.isDone()) {
                try {
                    _grid = _pendingGrid.get();
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }
                _pendingGrid = null;
            }

            // FIXME: better occlusion for speed
            if (_grid != null) {
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

                drawLabel = false;
                for (GLGridTile[] a_grid : _grid) {
                    for (GLGridTile t : a_grid) {
                        if (t.inView(ortho)) {
                            t.drawOrtho(surface, ortho, red, green, blue,
                                    renderPass);
                        }
                    }
                }

                GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
            }
        } else {
            // dump the grid if any
            if (_pendingGrid != null) {
                _pendingGrid.cancel(true);
                _pendingGrid = null;
            }
            _grid = null;
        }

        if (drawLabel) {
            if (_glText == null) {
                _glText = GLText.getInstance(MapView.getDefaultTextFormat());
            }

            // draw labels only if too far out
            ortho.scratch.geo.set((_south + _north) / 2d,
                    (_west + _east) / 2d);
            ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);
            if (ortho.scratch.pointD.z < 1f) {
                float xoffset = (float) ortho.scratch.pointD.x
                        - _glText.getStringWidth(_text) / 2;
                float yoffset = (float) ortho.scratch.pointD.y
                        - _glText.getStringHeight()
                                / 2;
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(xoffset, yoffset,
                        (float) ortho.scratch.pointD.z);
                _glText.draw(_text, red, green, blue, 1.0f);
                GLES20FixedPipeline.glPopMatrix();
            }
        }
    }

    private static GLGridTile[][] _genTileGrid(double south, double west,
            double north, double east,
            Executor genTileGridExecutor, Executor segmentLabelsExecutor) {
        //Log.d(TAG, "S: " + south + " W: " + west + " N: " + north + " E: "
        //        + east);
        ArrayList<GLGridTile[]> grid = new ArrayList<>();

        double lat = south;
        GLGridTile[] row;

        //if (lat < 0d) {
        //    Log.d(TAG, "latitude < 0");
        //}
        MutableMGRSPoint minRef;
        MutableMGRSPoint swRef = new MutableMGRSPoint(south, west);
        if (((int) west) % 6 == 0) {
            minRef = new MutableMGRSPoint(south, west + 2.999999);
        } else {
            minRef = new MutableMGRSPoint(south, west + 6);
        }
        minRef.alignToGrid();
        swRef.alignToGrid();

        minRef.offsetGrid(
                Math.min(swRef.getXGrid(), minRef.getXGrid())
                        - minRef.getXGrid(),
                Math.min(swRef.getYGrid(), minRef.getYGrid())
                        - minRef.getYGrid());

        while (lat < north) {
            row = _genTileRow(new MutableMGRSPoint(minRef), west, east, south,
                    north, genTileGridExecutor, segmentLabelsExecutor);
            if (row.length == 0) {
                break;
            }
            grid.add(row);
            minRef.offsetGrid(0, 1);
            minRef.alignToYGrid();
            double[] ll = {
                    0, 0
            };
            minRef.toLatLng(ll);
            lat = ll[0];
        }
        return grid.toArray(new GLGridTile[grid.size()][]);

    }

    static GLGridTile _clipTile(GLGridTile tile, double south,
            double west,
            double north, double east) {
        Vector2D[] clip = {
                new Vector2D(west, south),
                new Vector2D(east, south),
                new Vector2D(east, north),
                new Vector2D(west, north)
        };
        Vector2D[] poly = {
                new Vector2D(tile.sw.getLongitude(), tile.sw.getLatitude()),
                new Vector2D(tile.nw.getLongitude(), tile.nw.getLatitude()),
                new Vector2D(tile.ne.getLongitude(), tile.ne.getLatitude()),
                new Vector2D(tile.se.getLongitude(), tile.se.getLatitude())
        };

        Vector2D[] clipped = Vector2D.clipPolygonSutherlandHodgman(clip, poly,
                0.00000001);

        if (clipped.length < 3) {
            return null;
        }

        GeoPoint[] actual = new GeoPoint[clipped.length];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = new GeoPoint(clipped[i].y, clipped[i].x);
        }

        tile.setActualPolygon(actual);

        return tile;
    }

    private static GLGridTile[] _genTileRow(MutableMGRSPoint currRef,
            double west, double east, double south, double north,
            Executor genGridTileExecutor, Executor segmentLabelExecutor) {

        double lng = west;

        ArrayList<GLGridTile> row = new ArrayList<>();
        double[] ll = {
                0d, 0d
        };

        GLGridTile p = null;
        while (lng < east) {
            GLGridTile t = new GLGridTile();
            t.subResolution = 10000;

            t.mgrsRef = new MGRSPoint(currRef);
            if (p != null) {
                t.sw = p.se;
            } else {
                t.sw = _toGeo(currRef, ll);
            }

            currRef.offsetGrid(0, 1);
            currRef.alignToYGrid();

            if (p != null) {
                t.nw = p.ne;
            } else {
                t.nw = _toGeo(currRef, ll);
            }

            currRef.offsetGrid(1, 0);
            currRef.alignToXGrid();
            t.ne = _toGeo(currRef, ll);

            currRef.offsetGrid(0, -1);

            t.se = _toGeo(currRef, ll);

            lng = t.se.getLongitude();

            p = t;

            GLGridTile clipped = _clipTile(t, south, west, north, east);

            if (clipped != null) {
                // fill in on background thread based on actual to reduce per-frame overhead
                clipped._genGridTileExecutor = genGridTileExecutor;
                clipped._segmentLabelsExecutor = segmentLabelExecutor;
                segmentLabelExecutor
                        .execute(new GLGridTile.SegmentLabelResolver(clipped));

                row.add(clipped);
            }
        }

        return row.toArray(new GLGridTile[0]);
    }

    private static GeoPoint _toGeo(MGRSPoint mgrsPoint, double[] out) {
        double[] ll = mgrsPoint.toLatLng(out);
        return new GeoPoint(ll[0], ll[1]);
    }

    private GLText _glText;
    private final String _text;
    private GLGridTile[][] _grid;
    private Future<GLGridTile[][]> _pendingGrid;
    final double _south;
    final double _west;
    final double _north;
    final double _east;
    int gridX, gridY;
    boolean mark;
    GLZoneRegion next;
    GLZoneRegion prev;
    Executor _genGridTileExecutor;
    Executor _segmentLabelsExecutor = new DirectExecutorService();
}
