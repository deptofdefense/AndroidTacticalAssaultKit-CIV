
package com.atakmap.android.brightness;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlayRenderer;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

class BrightnessOverlay implements GLMapRenderable,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final GLMapSurface _surface;
    private final AtomicInteger _brightness;
    private final Context _context;
    private final SharedPreferences prefs;

    private static final FloatBuffer _vertexCoords;

    static {
        // 4 bytes per point * 2 components per point * 4 points
        final int vertexCoordsSize = 4 * 2 * 4;

        ByteBuffer b = com.atakmap.lang.Unsafe.allocateDirect(vertexCoordsSize);
        b.order(ByteOrder.nativeOrder());
        _vertexCoords = b.asFloatBuffer();
    }

    private MapOverlayRenderer renderer;

    BrightnessOverlay(final GLMapSurface surface,
            final AtomicInteger brightness) {
        _surface = surface;
        _brightness = brightness;
        _context = MapView.getMapView().getContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    void scheduleSetupOnGLThread() {
        _surface.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (BrightnessOverlay.this.renderer == null) {
                    BrightnessOverlay.this.renderer = new MapOverlayRenderer(
                            PreferenceManager
                                    .getDefaultSharedPreferences(_context)
                                    .getBoolean(
                                            //determines the layer enum type to set, higher layer covers more map items on screen
                                            "dim_map_with_brightness_key",
                                            false) ? MapView.RenderStack.WIDGETS
                                                    : //high layer
                    MapView.RenderStack.RASTER_OVERLAYS, //mid layer
                            BrightnessOverlay.this);
                }
                MapView mapView = (MapView) _surface.getMapView();
                MapOverlayManager.installOverlayRenderer(mapView,
                        BrightnessOverlay.this.renderer);
            }
        });
    }

    void scheduleDismissOnGLThread() {
        _surface.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (BrightnessOverlay.this.renderer == null)
                    return;

                MapView mapView = (MapView) _surface.getMapView();
                MapOverlayManager.uninstallOverlayRenderer(mapView,
                        BrightnessOverlay.this.renderer);
                BrightnessOverlay.this.renderer = null;
            }
        });
    }

    @Override
    public void release() {
    }

    @Override
    // Draw a rectangle over the whole screen. That is either white or black and
    // have varying transparency depending on whether and how much the user is
    // brightening or darkening the map.
    public void draw(GLMapView view) {

        // No source from which to get the brightness value? Then nothing to do.
        if (_brightness == null) {
            return;
        }

        // Get the brightness value. This will be a value in the range
        // [-255, 255], where -255 is as dark as this tool will darken, and 255
        // is as bright as this tool will brighten. A value of zero will have no
        // effect, so we can just return doing nothing in that case.
        int brightness = _brightness.intValue();
        if (brightness == 0) {
            return;
        }

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        int leftScreenPix = view.getLeft();
        int topScreenPix = view.getTop();
        int rightScreenPix = view.getRight();
        int bottomScreenPix = view.getBottom();

        // coords for a rectangle over the whole screen
        _vertexCoords.put(0, leftScreenPix);
        _vertexCoords.put(1, bottomScreenPix);
        _vertexCoords.put(2, leftScreenPix);
        _vertexCoords.put(3, topScreenPix);
        _vertexCoords.put(4, rightScreenPix);
        _vertexCoords.put(5, topScreenPix);
        _vertexCoords.put(6, rightScreenPix);
        _vertexCoords.put(7, bottomScreenPix);

        GLES20FixedPipeline.glVertexPointer(
                2, GLES20FixedPipeline.GL_FLOAT, 0, _vertexCoords);

        // Since we're either coloring black or white, all RGB components will
        // be either 1 or 0. Requested brightening/darkening intensity will be
        // scaled by the max alpha that the tool will draw over the map (i.e. it
        // will never totally black out or white out the map).
        final float MAX_ALPHA = 0.5f;
        float rgbComponent = (brightness > 0) ? 1f : 0f;
        float alpha = ((float) Math.abs(brightness) /
                (float) BrightnessReceiver.MAX_BRIGHTNESS_MAGNITUDE) *
                MAX_ALPHA;

        GLES20FixedPipeline.glColor4f(rgbComponent, rgbComponent, rgbComponent,
                alpha);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN,
                0, 4);

        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("dim_map_with_brightness_key")) {
            //remove and reattach the renderer!
            if (BrightnessOverlay.this.renderer != null) {
                scheduleDismissOnGLThread();
                scheduleSetupOnGLThread();
            }
        }
    }

    public void dispose() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }
}
