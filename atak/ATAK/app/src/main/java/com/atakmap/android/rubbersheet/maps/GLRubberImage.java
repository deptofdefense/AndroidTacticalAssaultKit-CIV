
package com.atakmap.android.rubbersheet.maps;

import android.graphics.Bitmap;
import android.util.Pair;

import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi3;
import com.atakmap.android.rubbersheet.data.BitmapPyramid;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.tilereader.opengl.GLTileMesh;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.Visitor;

import java.nio.DoubleBuffer;

public class GLRubberImage extends AbstractGLMapItem2 implements
        Shape.OnPointsChangedListener,
        AtakMapView.OnMapViewResizedListener,
        AtakMapView.OnMapMovedListener,
        RubberImage.OnAlphaChangedListener {

    public final static GLMapItemSpi3 SPI = new GLMapItemSpi3() {
        @Override
        public int getPriority() {
            // this SPI will be the fall-through if all else fails
            return -1;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> arg) {
            if (arg.second instanceof RubberImage)
                return new GLRubberImage(arg.first, (RubberImage) arg.second);
            return null;
        }
    };

    private final MapView _mapView;
    private final RubberImage _subject;
    private final DoubleBuffer _points;
    private final Vector2D[] _quad = new Vector2D[4];
    private GLTileMesh _mesh;
    private boolean _meshDirty;

    private Bitmap _bmp;
    private GLTexture _texture;
    private int _alpha = 255;
    private boolean _onScreen = false;

    private SurfaceRendererControl _surfaceCtrl;

    public GLRubberImage(MapRenderer surface, RubberImage subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE);
        _mapView = MapView.getMapView();
        _subject = subject;
        _points = com.atakmap.lang.Unsafe.allocateDirect(2 * 4,
                DoubleBuffer.class);
        _mesh = null;
        _meshDirty = false;
        onPointsChanged(subject);
        onAlphaChanged(subject, subject.getAlpha());

        _mapView.addOnMapViewResizedListener(this);
        _mapView.addOnMapMovedListener(this);

        if (surface instanceof MapRenderer3)
            _surfaceCtrl = ((MapRenderer3) surface)
                    .getControl(SurfaceRendererControl.class);
        else
            surface.visitControl(null, new Visitor<SurfaceRendererControl>() {
                @Override
                public void visit(SurfaceRendererControl object) {
                    _surfaceCtrl = object;
                }
            }, SurfaceRendererControl.class);
    }

    @Override
    public void startObserving() {
        super.startObserving();
        _subject.addOnPointsChangedListener(this);
        _subject.addOnAlphaChangedListener(this);
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.removeOnPointsChangedListener(this);
        _subject.removeOnAlphaChangedListener(this);
        _bmp = null;
    }

    @Override
    public void release() {
        super.release();
        if (_mesh != null) {
            _mesh.release();
            _mesh = null;
            _meshDirty = true;
        }
        if (_texture != null) {
            _texture.release();
            _texture = null;
        }
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!_onScreen || !MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE))
            return;

        BitmapPyramid image = _subject.getImage();
        if (image == null)
            return;

        Bitmap bmp = image.getBitmap(ortho, _subject.getWidth(),
                _subject.getLength());
        if (bmp == null)
            return;

        if (_bmp != bmp) {
            _bmp = bmp;
            if (_texture != null)
                _texture.release();

            Bitmap.Config config = _bmp.getConfig();
            if (config == null)
                // Some bitmaps have a null config, which will cause
                // a crash if we try to use it
                return;

            int bW = _bmp.getWidth(), bH = _bmp.getHeight();
            _texture = new GLTexture(bW, bH, config);
            _texture.load(_bmp);

            _meshDirty = true;
        }
        if (_texture == null)
            return;

        if (_meshDirty) {
            // NOTE: per the implementation of 'RubberImage' we know the points
            // are always in clockwise order, starting with upper-left. We can
            // compute the winding order here to determine if an IDL crossing
            // is occurring, and normalize the points to the one hemisphere

            GeoPoint[] pts = new GeoPoint[4];

            // derived from http://stackoverflow.com/questions/1165647/how-to-determine-if-a-list-of-polygon-points-are-in-clockwise-order
            double sum = 0.0; // >= 0d is clockwise
            for (int i = 0; i < 4; i++) {
                final int pt0 = i % 4;
                final int pt1 = (i + 1) % 4;
                final double lat0 = _points.get(pt0 * 2 + 1);
                final double lng0 = GeoCalculations
                        .wrapLongitude(_points.get(pt0 * 2));
                final double lat1 = _points.get(pt1 * 2 + 1);
                final double lng1 = GeoCalculations
                        .wrapLongitude(_points.get(pt1 * 2));
                double dx = lng1 - lng0;
                double dy = lat1 + lat0;
                sum += dx * dy;

                pts[i] = GeoPoint.createMutable();
                pts[i].set(lat0, lng0);
            }

            // ring was computed as CCW, indicating IDL crossing. normalize the
            // points to the same hemisphere as the UL
            if (sum < 0d) {
                if (pts[0].getLongitude() > 0d) {
                    for (int i = 1; i < pts.length; i++)
                        if (pts[i].getLongitude() < 0d)
                            pts[i].set(pts[i].getLatitude(),
                                    pts[i].getLongitude() + 360d);
                } else {
                    for (int i = 1; i < pts.length; i++)
                        if (pts[i].getLongitude() > 0d)
                            pts[i].set(pts[i].getLatitude(),
                                    pts[i].getLongitude() - 360d);
                }
            }

            _meshDirty = false;
            try {
                // construct the IMG <-> LLA transform
                DatasetProjection2 img2lla = new DefaultDatasetProjection2(
                        4326,
                        _bmp.getWidth(),
                        _bmp.getHeight(),
                        pts[0], pts[1], pts[2], pts[3]);

                if (_mesh == null) {
                    _mesh = new GLTileMesh(
                            _bmp.getWidth(), _bmp.getHeight(),
                            (float) _bmp.getWidth()
                                    / (float) _texture.getTexWidth(),
                            (float) _bmp.getHeight()
                                    / (float) _texture.getTexHeight(),
                            img2lla);
                } else {
                    _mesh.resetMesh(
                            0d, 0d,
                            _bmp.getWidth(), _bmp.getHeight(),
                            0f, 0f,
                            (float) _bmp.getWidth()
                                    / (float) _texture.getTexWidth(),
                            (float) _bmp.getHeight()
                                    / (float) _texture.getTexHeight(),
                            img2lla);
                }
            } catch (RuntimeException e) {
                _mesh = null;
            }
        }
        if (_mesh == null)
            return;

        bounds.setWrap180(ortho.continuousScrollEnabled);
        _mesh.drawMesh(ortho, _texture.getTexId(), 1f, 1f, 1f,
                _alpha / 255f);
    }

    @Override
    public void onPointsChanged(Shape shape) {
        GeoPoint[] points = shape.getPoints();
        MapView mv = MapView.getMapView();
        boolean wrap180 = mv != null && mv.isContinuousScrollEnabled();
        bounds.set(points, wrap180);
        _points.clear();
        for (int i = 0; i < 4; i++) {
            GeoPoint gp = points[i];
            _points.put(gp.getLongitude());
            _points.put(gp.getLatitude());
            _quad[i] = FOVFilter.geo2Vector(gp);
        }
        _points.flip();
        _meshDirty = true;
        dispatchOnBoundsChanged();
        checkShouldRecycle();
    }

    @Override
    public void onMapViewResized(AtakMapView view) {
        checkShouldRecycle();
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        checkShouldRecycle();
    }

    @Override
    public void onAlphaChanged(AbstractSheet sheet, int alpha) {
        _alpha = alpha;
        if (_surfaceCtrl != null) {
            _surfaceCtrl.markDirty(
                    new Envelope(
                            bounds.getWest(), bounds.getSouth(), 0d,
                            bounds.getEast(), bounds.getNorth(), 0d),
                    true);
        }
        context.requestRefresh();
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        if (bounds.intersects(params.bounds) && Vector2D.polygonContainsPoint(
                FOVFilter.geo2Vector(params.geo), _quad)) {
            return new HitTestResult(_subject, params.geo);
        }
        return null;
    }

    private void checkShouldRecycle() {
        _onScreen = true;
        GeoBounds mapBounds = new GeoBounds(_mapView.inverse(0, 0,
                AtakMapView.InverseMode.RayCast).get(),
                _mapView.inverse(_mapView.getWidth(), _mapView.getHeight(),
                        AtakMapView.InverseMode.RayCast).get());
        mapBounds.setWrap180(_mapView.isContinuousScrollEnabled());
        synchronized (bounds) {
            if (bounds.intersects(mapBounds))
                return;
        }
        _onScreen = false;
        BitmapPyramid img = _subject.getImage();
        if (_bmp != null || img != null && !img.isEmpty()) {
            context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    _subject.getImage().setBitmap(_bmp = null);
                }
            });
        }
    }
}
