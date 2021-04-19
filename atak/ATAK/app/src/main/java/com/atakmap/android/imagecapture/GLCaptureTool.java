
package com.atakmap.android.imagecapture;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Debug;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLCapture;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemFactory;
import com.atakmap.android.tilecapture.imagery.ImageryCaptureTask;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.opengl.GLResolvable.State;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.gdalconst.gdalconstConstants;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.atakmap.coremap.locale.LocaleUtil;

import java.util.Map;

/**
 *
 * Class for rendering and capturing map imagery
 * @deprecated Use {@link ImageryCaptureTask} instead for much
 * better performance and results
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class GLCaptureTool extends GLMapView implements GLLayer2,
        GLCapturableMapView, MapView.OnMapMovedListener,
        MapView.OnMapViewResizedListener, View.OnKeyListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            final MapRenderer surface = object.first;
            final Layer layer = object.second;
            if (layer instanceof ImageCapture)
                return new GLCaptureTool(surface,
                        (ImageCapture) layer);
            return null;
        }

        @Override
        public int getPriority() {
            return 1;
        }
    };

    public static final String TAG = "GLCaptureTool";

    private final ImageCapture _subject;
    private GLImageCapture _imgCap;
    private final SharedPreferences _prefs;
    private int _capCount = 0;

    private int _captureRes;

    private boolean _showImagery = true;
    private boolean _unregistering = false;
    private final boolean _customRender;
    private final String _toolbarId;

    private final ArrayList<GLMapItem2> _glitems;
    private GLLayer3 _grid;

    private final List<Layer> _surfaceLayers;
    private MapState _mState;

    private final MapView _mapView;
    private GeoBounds _bounds;

    private CaptureCallback _captureListener;

    private boolean _capReady = true;
    private boolean _renderOut = false;
    private boolean _capturing = false;
    private boolean _focused = false;
    private boolean _initialBaseRender = false;

    public GLCaptureTool(MapRenderer surface, ImageCapture subject) {
        this(surface, ((GLMapView) surface).getSurface(), subject);
    }

    protected GLCaptureTool(MapRenderer surface, GLMapSurface mapSurface,
            ImageCapture subject) {
        super(mapSurface, mapSurface.getLeft(), mapSurface.getTop(),
                mapSurface.getRight(), mapSurface.getBottom());

        subject.setGLSubject(this);
        _mapView = subject.getMapView();
        _toolbarId = subject.getToolbarId();

        _prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);

        _showImagery = subject.showMapImagery();
        this.continuousScrollEnabled = _mapView.isContinuousScrollEnabled();

        _surfaceLayers = subject.getLayers();
        for (Layer layer : _surfaceLayers)
            super.onLayerAdded(_mapView, layer);

        _customRender = subject.useCustomRenderer();

        // Find the grid
        if (_customRender) {
            List<Layer> surfaceLayers = _mapView.getLayers(
                    MapView.RenderStack.MAP_SURFACE_OVERLAYS);
            for (Layer layer : surfaceLayers) {
                if (layer instanceof CustomGrid) {
                    _grid = GLLayerFactory.create4(surface, layer);
                    if (_grid != null)
                        _grid.start();
                    break;
                }
            }
        }

        drawVersion++;

        _subject = subject;

        _captureRes = MathUtils.clamp(CapturePrefs.get(
                CapturePrefs.PREF_RES, 4), 1, 10);

        _imgCap = _subject.getImageCapture();

        //convert MapItems to GLMapItems on the surface
        if (_customRender) {
            List<MapItem> items = _subject.getAllItems();
            _glitems = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                MapItem item = items.get(i);
                GLMapItem2 glitem = GLMapItemFactory.create3(surface, item);
                if (glitem != null) {
                    // XXX - I believe that this is what was intended with the
                    //       previous type-check/callback invocations

                    // cycle start/stop observing to have the renderer load the
                    // current state of the item but avoid receiving further
                    // events
                    glitem.startObserving();
                    glitem.stopObserving();

                    _glitems.add(glitem);
                }
            }
            sortGLItems();
        } else
            _glitems = null;

        _mapView.addOnKeyListener(this);
        _mapView.addOnMapMovedListener(this);
        _mapView.addOnMapViewResizedListener(this);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    private void sortGLItems() {
        // Sort by z-order
        if (_customRender) {
            Collections.sort(_glitems, new Comparator<GLMapItem2>() {
                @Override
                public int compare(GLMapItem2 item1, GLMapItem2 item2) {
                    return Double.compare(item2.getZOrder(), item1.getZOrder());
                }
            });
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {
        // Ignore preferences changes once render out has been set
        if (_renderOut)
            return;
        if (key.equals(CapturePrefs.PREF_RES))
            _captureRes = MathUtils.clamp(
                    CapturePrefs.get(CapturePrefs.PREF_RES, 4), 1, 10);
        _showImagery = _subject.showMapImagery();
    }

    @Override
    protected void refreshLayersImpl2(
            List<Layer> layers, Map<Layer, GLLayer2> renderers) {
        super.refreshLayersImpl2(_surfaceLayers, renderers);
    }

    @Override
    public Layer getSubject() {
        return _subject;
    }

    @Override
    public void draw(GLMapView view) {
        drawImpl();
    }

    @Override
    public void drawImpl() {
        if (!_focused) {
            Point focusPoint = _mapView.getMapController().getFocusPoint();
            GeoPoint centerGeoPoint = _mapView.inverse(focusPoint.x,
                    focusPoint.y, MapView.InverseMode.RayCast).get();
            startAnimating(centerGeoPoint.getLatitude(),
                    centerGeoPoint.getLongitude(), _mapView.getMapScale(),
                    0, 0, 1d);
            startAnimatingFocus(focusPoint.x, focusPoint.y, 1d);
            _focused = true;
        }

        synchronized (this) {
            if (_unregistering) {
                baseRender();
                _unregistering = false;
                unregisterImpl();
                return;
            }
        }

        if (_customRender) {
            baseRender();

            // Only shown when setting up capture
            if (!_renderOut) {
                // Blank out map if not showing imagery
                if (!_showImagery) {
                    GLES20FixedPipeline.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
                    GLES20FixedPipeline
                            .glClear(GLES20FixedPipeline.GL_COLOR_BUFFER_BIT);
                }
                // Draw grid
                if (_grid != null)
                    _grid.draw(this);

                // Draw map items
                MutableGeoBounds bnds = new MutableGeoBounds(0d, 0d, 0d, 0d);
                for (GLMapItem2 item : _glitems) {
                    item.getBounds(bnds);
                    if (intersecting(bnds))
                        // XXX - update for other render passes when external
                        //       interface is updated to GLMapRenderable2
                        item.draw(this, GLMapView.RENDER_PASS_SURFACE);
                }
            }
        }

        //if the camera and imagery are settled
        if (_renderOut && _capReady) {
            if (!_customRender) {
                // We need to do an initial render of this layer
                // or else the imagery isn't captured correctly
                if (!_initialBaseRender) {
                    // Sync the capture layer position with the map layer
                    this.startAnimating(_mapView.getLatitude(),
                            _mapView.getLongitude(), _mapView.getMapScale(),
                            _mapView.getMapRotation(), _mapView.getMapTilt(),
                            1d);
                    _initialBaseRender = true;
                }
                baseRender();
            }
            if (super.settled && (!isCapturing() || glLayersSettled())) {
                _showImagery = true;
                continueCapture();
            }
        }
        _subject.drawOverlays();
    }

    private synchronized boolean isCapturing() {
        return _capturing;
    }

    private void baseRender() {
        // Clear out buffer and render map imagery
        GLES20FixedPipeline.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20FixedPipeline.glClear(
                GLES20FixedPipeline.GL_COLOR_BUFFER_BIT);
        super.render();
    }

    synchronized void unregister() {
        _unregistering = true;
    }

    private void unregisterImpl() {
        release();
        dispose();
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                _mapView.removeLayer(MapView.RenderStack.TARGETING,
                        _subject);
                _mapView.popStack(MapView.RenderStack.TARGETING);

                // Need to remove the extra imagery layers as well
                if (_surfaceLayers != null) {
                    for (Layer l : _surfaceLayers)
                        GLCaptureTool.super.onLayerRemoved(_mapView, l);
                }

                //unregister
                GLLayerFactory.unregister(GLCaptureTool.SPI);
            }
        });
    }

    // ImageCapturePP access
    public MapView getMapView() {
        return _mapView;
    }

    public GeoBounds getFullBounds() {
        return _subject.getBounds();
    }

    public synchronized GeoBounds getBounds() {
        return _bounds;
    }

    private boolean intersecting(GeoBounds bounds) {
        return !(bounds.getEast() < westBound
                || bounds.getWest() > eastBound
                || bounds.getNorth() < southBound
                || bounds.getSouth() > northBound);
    }

    public void cancelRender() {
        finish();
    }

    public void beginCapture() {
        if (_renderOut)
            return;
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                _renderOut = true;
            }
        });
    }

    /**
     * Capture a patch of the map imagery
     */
    private synchronized void continueCapture() {
        // Wait until final capture is finished
        if (_capCount >= _captureRes * _captureRes)
            return;
        if (!_capturing) {
            _capturing = true;
            if (_mState == null) {
                _bounds = getFullBounds();
                Log.d(TAG, "Full bounds: " + _bounds);
                Log.d(TAG, "Actual bounds: " + _mapView.getBounds());
                _mState = new MapState(this, _bounds);
                ImageCapturePP postDraw = _subject.getPostProcessor();
                _captureListener = new CaptureCallback(postDraw);
                if (postDraw != null)
                    postDraw.calcForwardPositions();
            }

            // Calculate focus area for subdivision patch
            int x = _capCount % _captureRes;
            int y = _captureRes - (_capCount / _captureRes) - 1;

            GeoBounds bounds = _mState.bounds;
            double north = bounds.getNorth();
            double south = bounds.getSouth();
            double east = bounds.getEast();
            double west = bounds.getWest();
            double lngSpan = east - west;
            if (bounds.crossesIDL()) {
                lngSpan = 360 - lngSpan;
                west = east;
            }

            double lat = (_mState.latOff / _captureRes) + south
                    + ((y * 2) + 1) * ((north - south) / (_captureRes * 2));
            double lng = (_mState.lonOff / _captureRes) + west
                    + ((x * 2) + 1) * (lngSpan / (_captureRes * 2));
            lng = GeoCalculations.wrapLongitude(lng);

            // Begin patch focus
            startAnimating(lat, lng, _mState.scale * _captureRes,
                    _mState.rot, _mState.tilt, 1);
        } else {
            //do the capture and quit
            _capturing = false;
            if (_captureListener != null) {
                Log.d(TAG, "Capturing tile #" + (_capCount + 1) + ": "
                        + (new GeoBounds(lowerLeft, upperRight)));
                _imgCap.capture(_captureListener);
            }
            _capCount++;
        }
    }

    /**
     * Determine if all layers are settled
     * @return True if all layers are settled
     */
    private boolean glLayersSettled() {
        List<GLMapRenderable> renderables = new ArrayList<>();
        getMapRenderables(renderables);

        if (renderables.isEmpty())
            return false;

        for (GLMapRenderable r : renderables) {
            GLResolvable.State state = GLResolvable.State.RESOLVED;
            if (r instanceof GLResolvableMapRenderable)
                state = ((GLResolvableMapRenderable) r).getState();
            if (!state.equals(GLResolvable.State.RESOLVED)
                    || state.equals(GLResolvable.State.UNRESOLVABLE))
                return false;
        }

        return true;
    }

    private void finish(boolean resetCapture) {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(ToolbarBroadcastReceiver.UNSET_TOOLBAR));

        _prefs.unregisterOnSharedPreferenceChangeListener(this);

        _mapView.removeOnKeyListener(this);
        _mapView.removeOnMapMovedListener(this);
        _mapView.removeOnMapViewResizedListener(this);

        _renderOut = false;

        synchronized (this) {
            _capCount = 0;
            if (resetCapture && _captureListener != null) {
                _captureListener.dispose();
                _captureListener = null;
            }

            // Restore full capture view
            if (_mState != null) {
                startAnimating(_mState.lat, _mState.lon, _mState.scale,
                        _mState.rot, _mState.tilt, 1);
                _mState = null;
            }
        }

        if (_grid != null) {
            _grid.stop();
            _grid.release();
            _grid = null;
        }
    }

    private void finish() {
        finish(true);
    }

    /**
     * Reset capture and bring up toolbar
     */
    public void redoCapture() {
        synchronized (this) {
            if (_captureListener != null)
                _captureListener.dispose();
            _captureListener = null;
        }
        // Restart image capture process
        finish(true);
        if (_toolbarId != null) {
            Intent toolbar = new Intent();
            toolbar.setAction(ToolbarBroadcastReceiver.OPEN_TOOLBAR);
            toolbar.putExtra("toolbar", _toolbarId);
            AtakBroadcast.getInstance().sendBroadcast(toolbar);
        }
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        super.onMapMoved(view, animate);
    }

    private static class MapState {
        final double lat, latOff, lon, lonOff, rot, tilt, scale;
        final GeoBounds bounds;

        public MapState(GLMapView mapView, GeoBounds bounds) {
            this.bounds = new GeoBounds(bounds);
            this.bounds.setWrap180(mapView.continuousScrollEnabled);
            lat = mapView.drawLat;
            lon = mapView.drawLng;
            rot = mapView.drawRotation;
            tilt = mapView.drawTilt;
            scale = mapView.drawMapScale;

            // Focus point offset from bounds center point
            GeoPoint cen = this.bounds.getCenter(null);
            latOff = lat - cen.getLatitude();
            lonOff = lon - cen.getLongitude();
        }
    }

    private class CaptureCallback implements GLImageCapture.RawCaptureCallback {

        private Bitmap _previewBmp;
        private int _tileCount = 0;
        private ImageCapturePP _postDraw;
        private final File _tileFile;
        private final Driver _driver;
        private ProgressDialog _progress;
        private CaptureDialog _postDialog;

        CaptureCallback(ImageCapturePP postDraw) {
            _postDraw = postDraw;
            _tileFile = new File(_subject.getOutputDirectory(), "." +
                    (new CoordinatedTime()).getMilliseconds() + "_tiles.tiff");
            if (IOProviderFactory.exists(_tileFile))
                FileSystemUtils.deleteFile(_tileFile);
            _driver = gdal.GetDriverByName("GTiff");
            if (_driver == null) {
                toast("Failed to capture. GeoTIFF format not supported.");
            } else {
                if (_subject != null && _subject.isDisplayDialog()) {
                    _mapView.post(new Runnable() {
                        @Override
                        public void run() {
                            _progress = new ProgressDialog(
                                    _mapView.getContext());
                            _progress.setMessage(
                                    _subject.getProcessingMessage());
                            _progress
                                    .setOnCancelListener(
                                            new DialogInterface.OnCancelListener() {
                                                @Override
                                                public void onCancel(
                                                        DialogInterface dialog) {
                                                    _subject.onCancelCapture();
                                                    finish();
                                                }
                                            });
                            _progress.setProgressStyle(
                                    ProgressDialog.STYLE_HORIZONTAL);
                            _progress.setMax(_captureRes * _captureRes);
                            _progress.show();
                        }
                    });
                }
            }
        }

        public void dispose() {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    if (_progress != null)
                        _progress.dismiss();
                }
            });
            if (_previewBmp != null && !_previewBmp.isRecycled()) {
                _previewBmp.recycle();
                _previewBmp = null;
            }
            if (_postDraw != null)
                _postDraw.dispose();
            _postDraw = null;
            _postDialog = null;
            _redrawThread.dispose();
            if (IOProviderFactory.exists(_tileFile))
                FileSystemUtils.deleteFile(_tileFile);
            _tileCount = 0;
        }

        @Override
        public void onCaptureStarted(GLCapture capture) {
            //toast("Capturing...");
        }

        @Override
        public void onCaptureComplete(GLCapture capture, Bitmap bitmap) {
            finish();
        }

        @Override
        public void onCaptureComplete(GLCapture capture,
                ByteBuffer rgba, int width, int height) {
            if (_driver == null) {
                finish();
                return;
            }

            int x = _tileCount % _captureRes;
            int y = _tileCount / _captureRes;
            _tileCount++;

            // Save tile
            Dataset ds;
            if (_tileCount == 1) {
                ds = _subject.createDataset(_driver, _tileFile,
                        width * _captureRes, height * _captureRes);
            } else
                ds = GdalLibrary.openDatasetFromFile(_tileFile,
                        gdalconst.GA_Update);

            if (ds != null) {
                _subject.onCaptureTile(rgba, x, y, width, height, ds);
                ds.delete();
            } else
                Unsafe.free(rgba);

            if (_tileCount == _captureRes * _captureRes) {
                finish(false);

                // Now we need to build the preview image
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (_progress != null) {
                            _progress.setProgress(_tileCount);
                            _progress.setMessage("Loading preview...");
                        }
                    }
                });
                _previewBmp = buildPreview();
                if (_previewBmp == null) {
                    toast("Failed to build preview. Unable to read imagery file.");
                    dispose();
                    return;
                }
                if (_postDraw != null) {
                    final Bitmap copy = _postDraw.createBitmap(_previewBmp);
                    _postDraw.drawElements(copy);
                    _mapView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (_progress != null)
                                _progress.dismiss();
                            previewResult(copy);
                        }
                    });
                }
                //logMemory("finished tiles");
            } else {
                // Update tile capture progress
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (_progress != null)
                            _progress.setProgress(_tileCount);
                    }
                });
            }
        }

        private Bitmap buildPreview() {
            Dataset ds = GdalLibrary.openDatasetFromFile(_tileFile,
                    gdalconst.GA_ReadOnly);
            if (ds == null)
                return null;
            int fullWidth = ds.GetRasterXSize(), fullHeight = ds
                    .GetRasterYSize(), width = fullWidth / _captureRes,
                    height = fullHeight
                            / _captureRes,
                    tWidth = width / _captureRes, tHeight = height
                            / _captureRes;
            int channels = ds.GetRasterCount();
            byte[] byteData = new byte[tWidth * tHeight * channels];
            int[] pixels = new int[tWidth * tHeight];
            Bitmap ret = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
            for (int y = 0; y < _captureRes; y++) {
                for (int x = 0; x < _captureRes; x++) {
                    // Read tile
                    //logMemory("pre-read tile(" + x + ", " + y + ")");
                    int err = ds.ReadRaster(x * width, y * height, width,
                            height,
                            tWidth, tHeight, gdalconstConstants.GDT_Byte,
                            byteData,
                            null, channels, tWidth * channels, 1);
                    if (err == gdalconst.CE_Failure) {
                        ret.recycle();
                        ds.delete();
                        return null;
                    }
                    //logMemory("post-read tile(" + x + ", " + y + ")");
                    // Convert RGB to ARGB
                    int i = 0, p = 0;
                    for (int iy = 0; iy < tHeight; iy++) {
                        for (int ix = 0; ix < tWidth; ix++) {
                            int r = byteData[p], g = byteData[p + 1],
                                    b = byteData[p + 2];
                            if (r < 0)
                                r += 256;
                            if (g < 0)
                                g += 256;
                            if (b < 0)
                                b += 256;
                            pixels[i++] = Color.rgb(r, g, b);
                            p += 3;
                        }
                    }
                    ret.setPixels(pixels, 0, tWidth, x * tWidth,
                            y * tHeight, tWidth, tHeight);
                }
            }
            ds.delete();
            return ret;
        }

        /**
         * Create a dialog showing the captured image and overlay elements
         * Allows the user to preview and edit overlays before saving
         * @param preview Post-processed bitmap capture
         */
        private void previewResult(Bitmap preview) {
            if (_postDraw == null)
                return;
            _postDialog = _postDraw.getCaptureDialog();
            _postDialog.initView();
            _postDialog.setupImage(preview);
            _postDialog.setOnChangedListener(new Runnable() {
                @Override
                public void run() {
                    _postDraw.cancelBusy();
                    _redrawThread.exec();
                }
            });
            _postDialog.setOnSaveListener(new Runnable() {
                @Override
                public void run() {
                    saveResult();
                }
            });
            _postDialog.setOnRedoListener(new Runnable() {
                @Override
                public void run() {
                    redoCapture();
                }
            });
            _postDialog.setOnCancelListener(new Runnable() {
                @Override
                public void run() {
                    dispose();
                }
            });
            try {
                _postDialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to preview result. Saving anyway...", e);
                //_resultBmp = preview;
                saveResult();
            }
        }

        private void saveResult() {
            if (_postDraw != null && _previewBmp != null) {
                TiledCanvas canvas = new TiledCanvas(_tileFile,
                        _previewBmp.getWidth(), _previewBmp.getHeight());
                _previewBmp.recycle();
                _previewBmp = null;
                _subject.saveResult(_postDraw, canvas);
            } else
                toast("Failed to save result.");
        }

        @Override
        public void onCaptureError(GLCapture capture, Throwable t) {
            toast("Capture Error");
            Log.e(TAG, "CAPTURE ERROR", t);
            finish();
        }

        private final LimitingThread _redrawThread = new LimitingThread(
                "GLCaptureTool-Redraw", new Runnable() {
                    @Override
                    public void run() {
                        if (_previewBmp == null || _previewBmp.isRecycled()
                                || _postDraw == null)
                            return;
                        final Bitmap copy = _postDraw.createBitmap(_previewBmp);
                        if (_postDraw.drawElements(copy)) {
                            _mapView.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (_postDialog != null)
                                        _postDialog.setupImage(copy);
                                    else
                                        copy.recycle();
                                }
                            });
                        } else
                            copy.recycle();
                    }
                });
    }

    @Override
    public void onMapViewResized(AtakMapView view) {
        GLMapSurface surf = view.getGLSurface();
        this._left = surf.getLeft();
        this._right = surf.getRight();
        this._top = surf.getBottom();
        this._bottom = surf.getTop();

        Point focusPoint = view.getMapController().getFocusPoint();
        startAnimatingFocus(focusPoint.x, focusPoint.y, 1d);

        _imgCap = _subject.getImageCapture();
        setCapReady(true);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return false;
    }

    private void setCapReady(boolean rdy) {

        final boolean ready = rdy;

        _mapView.post(new Runnable() {
            @Override
            public void run() {
                _capReady = ready;
            }
        });
    }

    private void toast(final String txt) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(_mapView.getContext(),
                        txt, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static final float MB = 1000000;

    private void logMemory(String tag) {
        Log.d(TAG, String.format(LocaleUtil.getCurrent(),
                "%s: size: %.2fMB, alloc: %.2fMB, free: %.2fMB",
                tag, Debug.getNativeHeapSize() / MB,
                Debug.getNativeHeapAllocatedSize() / MB,
                Debug.getNativeHeapFreeSize() / MB));
    }
}
