
package com.atakmap.android.elev;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.android.hierarchy.items.LayerHierarchyListItem;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.elev.graphics.GLHeatMap;
import com.atakmap.android.elev.graphics.SharedDataModel;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay2;
import com.atakmap.android.overlay.MapOverlayBuilder;
import com.atakmap.android.viewshed.ContourLinesOverlay;
import com.atakmap.android.widgets.IsoKeyWidget;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.ProxyLayer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.elevation.TerrainSlopeLayer;
import com.atakmap.map.layer.opengl.GLLayerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElevationOverlaysMapComponent extends AbstractMapComponent
        implements MapView.OnMapViewResizedListener {

    public enum HeatMapMode {
        Elevation(R.string.heatmap),
        TerrainSlope(R.string.terrainslope);

        final int id;

        HeatMapMode(int id) {
            this.id = id;
        }

        static HeatMapMode findById(int id) {
            for (HeatMapMode m : values()) {
                if (m.id == id)
                    return m;
            }
            return null;
        }
    }

    public static final String PREFERENCE_VISIBLE_KEY = "prefs_dted_visible";
    public static final String PREFERENCE_COLOR_SATURATION_KEY = "prefs_dted_color_saturation";
    public static final String PREFERENCE_COLOR_VALUE_KEY = "prefs_dted_color_value";
    public static final String PREFERENCE_COLOR_INTENSITY_KEY = "prefs_dted_color_intensity";
    public static final String PREFERENCE_COLOR_INTENSITY_VALUE = "20";
    public static final String PREFERENCE_X_RES_KEY = "prefs_dted_x_res";
    public static final int PREFERENCE_X_RES_MIN = 7;
    public static final int PREFERENCE_X_RES_DEFAULT = 280;
    public static final int PREFERENCE_X_RES_MAX = 700;
    public static final String PREFERENCE_Y_RES_KEY = "prefs_dted_y_res";
    public static final int PREFERENCE_Y_RES_MIN = 5;
    public static final int PREFERENCE_Y_RES_DEFAULT = 200;
    public static final int PREFERENCE_Y_RES_MAX = 500;
    public static final int PREFERENCE_MAX_VALUE = 100;
    public static final String PREFERENCE_MODE_KEY = "prefs_heatmap_mode";

    private final SharedPreferences.OnSharedPreferenceChangeListener _prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {

            if (key == null)
                return;

            if (key.equals(PREFERENCE_COLOR_SATURATION_KEY)
                    || key.equals(PREFERENCE_COLOR_VALUE_KEY)
                    || key.equals(PREFERENCE_COLOR_INTENSITY_KEY)
                    || key.equals(PREFERENCE_X_RES_KEY)
                    || key.equals(PREFERENCE_Y_RES_KEY)
                    || key.equals(PREFERENCE_MODE_KEY)) {
                _setPrefs(sharedPreferences);
            }
        }
    };

    private static final String ROOT_LAYOUT_EXTRA = "rootLayoutWidget";
    public static final String ELEVATION_WIDGET = "ElevationWidget";
    private static HeatMapOverlay heatMapOverlay;
    private static ContourLinesOverlay contourLinesOverlay;
    private static TerrainSlopeLayer terrainSlopeOverlay;
    private IsoKeyWidget _keyWidget;
    private MapView _mapView;
    private SharedPreferences prefs;
    private final AtomicBoolean disposed = new AtomicBoolean(true);
    private static CardLayer overlayLayer;
    private MapOverlay2 overlay;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;
        disposed.set(false);

        GLLayerFactory.register(GLHeatMap.SPI2);

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(_prefsListener);

        heatMapOverlay = new HeatMapOverlay();
        terrainSlopeOverlay = new TerrainSlopeLayer("Terrain Slope Angle");

        overlayLayer = new CardLayer("Heatmap Overlay");
        overlayLayer.add(heatMapOverlay,
                context.getString(HeatMapMode.Elevation.id));
        overlayLayer.add(terrainSlopeOverlay,
                context.getString(HeatMapMode.TerrainSlope.id));

        // only respond to visibility preference on startup
        overlayLayer.setVisible(
                prefs.getBoolean(PREFERENCE_VISIBLE_KEY, false));

        _setPrefs(prefs);
        refreshPersistedState();

        RootLayoutWidget root = (RootLayoutWidget) _mapView.getComponentExtra(
                ROOT_LAYOUT_EXTRA);
        LinearLayoutWidget layoutH = root
                .getLayout(RootLayoutWidget.BOTTOM_LEFT)
                .getOrCreateLayout("BL_H");

        _keyWidget = new IsoKeyWidget();
        _keyWidget.setName(ELEVATION_WIDGET);
        _keyWidget.setPadding(12f, 12f, 12f, 12f);
        _keyWidget.setMargins(16f, 0f, 0f, 16f);
        _mapView.addOnMapViewResizedListener(this);
        onMapViewResized(_mapView);

        layoutH.addChildWidgetAt(0, _keyWidget);

        SharedDataModel.getInstance().isoDisplayMode = SharedDataModel.RELATIVE;

        overlayLayer
                .addOnLayerVisibleChangedListener(
                        new HeatMapOverlay.OnLayerVisibleChangedListener() {
                            @Override
                            public void onLayerVisibleChanged(Layer overlay) {
                                ElevationOverlaysMapComponent.this
                                        .toggleOverlay();
                            }
                        });
        overlayLayer
                .addOnProxySubjectChangedListener(
                        new ProxyLayer.OnProxySubjectChangedListener() {
                            @Override
                            public void onProxySubjectChanged(
                                    ProxyLayer overlay) {
                                ElevationOverlaysMapComponent.this
                                        .toggleHeatmapLegend();
                            }
                        });

        this.toggleOverlay();

        overlay = new MapOverlayBuilder()
                .setIdentifier(ElevationOverlaysMapComponent.class.getName())
                .setName(overlayLayer.getName())
                .setListItem(Actions.ACTION_VISIBILITY,
                        new LayerHierarchyListItem(overlayLayer,
                                "android.resource://"
                                        + MapView.getMapView().getContext()
                                                .getPackageName()
                                        + "/" +
                                        R.drawable.ic_overlay_dted,
                                false))
                .build();
        _mapView.getMapOverlayManager().addOtherOverlay(overlay);

        // add the viewshed receiver
        final ViewShedReceiver viewShedReceiver = ViewShedReceiver
                .getInstance();
        final DocumentedIntentFilter viewShedReceiverFilter = new DocumentedIntentFilter();
        viewShedReceiverFilter.addAction(ViewShedReceiver.ACTION_SHOW_VIEWSHED);
        viewShedReceiverFilter
                .addAction(ViewShedReceiver.ACTION_SHOW_VIEWSHED_LINE);
        viewShedReceiverFilter
                .addAction(ViewShedReceiver.UPDATE_VIEWSHED_INTENSITY);
        viewShedReceiverFilter
                .addAction(ViewShedReceiver.ACTION_DISMISS_VIEWSHED);
        viewShedReceiverFilter
                .addAction(ViewShedReceiver.ACTION_DISMISS_VIEWSHED_LINE);
        this.registerReceiver(context, viewShedReceiver,
                viewShedReceiverFilter);
    }

    private void refreshPersistedState() {
        int idx = -1;
        if (contourLinesOverlay != null) {
            List<Layer> layers = _mapView
                    .getLayers(MapView.RenderStack.MAP_LAYERS);
            idx = layers.indexOf(contourLinesOverlay);
            _mapView.removeLayer(MapView.RenderStack.MAP_LAYERS,
                    contourLinesOverlay);
            contourLinesOverlay.dispose();
        }

        if (disposed.get())
            return;
        contourLinesOverlay = new ContourLinesOverlay(_mapView);
        if (idx < 0)
            MapView.getMapView().addLayer(
                    MapView.RenderStack.MAP_LAYERS,
                    contourLinesOverlay);
        else
            MapView.getMapView().addLayer(
                    MapView.RenderStack.MAP_LAYERS,
                    idx,
                    contourLinesOverlay);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        disposed.set(true);

        _mapView.removeOnMapViewResizedListener(this);
        prefs.unregisterOnSharedPreferenceChangeListener(_prefsListener);

        // remove the layer
        _mapView.removeLayer(
                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                heatMapOverlay);

        // remove the layer
        _mapView.removeLayer(
                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                terrainSlopeOverlay);

        // remove the layer
        _mapView.removeLayer(
                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                contourLinesOverlay);
        contourLinesOverlay.dispose();
    }

    @Override
    public void onMapViewResized(AtakMapView view) {
        // Set the size of the color bar
        // The extra size is calculated based off the text size
        _keyWidget.setBarSize(_mapView.getWidth() * 0.01f,
                _mapView.getHeight() * 0.28f);
    }

    /**************************************************************************/

    private void toggleHeatmapLegend() {
        _keyWidget.setVisible(overlayLayer.isVisible()
                && overlayLayer.get() == heatMapOverlay);
    }

    private void toggleOverlay() {
        if (!overlayLayer.isVisible()) {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    _mapView.removeLayer(
                            MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                            overlayLayer);
                }
            });
        } else {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    _mapView.addLayer(MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                            overlayLayer);
                }
            });
        }
        toggleHeatmapLegend();
    }

    private void _setPrefs(final SharedPreferences prefs) {
        float saturation = 50 / ((float) PREFERENCE_MAX_VALUE);
        try {
            saturation = ((float) Integer.parseInt(prefs.getString(
                    PREFERENCE_COLOR_SATURATION_KEY, "50")) % 100)
                    / ((float) PREFERENCE_MAX_VALUE);
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "error parsing saturation key", nfe);
        }

        float value = 50 / ((float) PREFERENCE_MAX_VALUE);
        try {
            value = ((float) Integer.parseInt(prefs.getString(
                    PREFERENCE_COLOR_VALUE_KEY,
                    "50")) % 100)
                    / ((float) PREFERENCE_MAX_VALUE);
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "error parsing color value key", nfe);
        }

        float intensity = 10 / ((float) PREFERENCE_MAX_VALUE);
        try {
            intensity = ((float) Integer.parseInt(prefs.getString(
                    PREFERENCE_COLOR_INTENSITY_KEY,
                    PREFERENCE_COLOR_INTENSITY_VALUE)) % 100)
                    / ((float) PREFERENCE_MAX_VALUE);
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "error parsing color intensity key", nfe);
        }

        int xSampleResolution = PREFERENCE_X_RES_DEFAULT;
        try {
            int val = Integer.parseInt(prefs.getString(
                    PREFERENCE_X_RES_KEY,
                    String.valueOf(PREFERENCE_X_RES_DEFAULT)));
            if (val > PREFERENCE_X_RES_MAX)
                val = PREFERENCE_X_RES_MAX;
            xSampleResolution = val;
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "error parsing xres value key", nfe);
        }

        int ySampleResolution = PREFERENCE_Y_RES_DEFAULT;
        try {
            int val = Integer.parseInt(prefs.getString(
                    PREFERENCE_Y_RES_KEY,
                    String.valueOf(PREFERENCE_Y_RES_DEFAULT)));
            if (val > PREFERENCE_Y_RES_MAX)
                val = PREFERENCE_Y_RES_MAX;
            ySampleResolution = val;
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "error parsing yres value key", nfe);
        }

        heatMapOverlay.setColorComponents(saturation, value, intensity);
        heatMapOverlay.setResolution(xSampleResolution, ySampleResolution);

        terrainSlopeOverlay.setAlpha(intensity);

        HeatMapMode mode = HeatMapMode.Elevation;
        try {
            final Context context = _mapView.getContext();
            String v = prefs.getString(PREFERENCE_MODE_KEY,
                    _mapView.getContext().getString(HeatMapMode.Elevation.id));
            for (HeatMapMode m : HeatMapMode.values()) {
                if (context.getString(m.id).equals(v)) {
                    mode = m;
                    break;
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "error parsing heatmap mode value key", t);
        }
        setHeatMapMode(mode);

        refreshMap();
    }

    public static void setHeatMapVisible(boolean visible) {
        overlayLayer.setVisible(visible);
    }

    public static boolean isHeatMapVisible() {
        return overlayLayer.isVisible();
    }

    public static void setHeatMapMode(HeatMapMode mode) {
        if (mode == null)
            return;
        overlayLayer.show(MapView.getMapView().getContext().getString(mode.id));
        refreshMap();
    }

    public static HeatMapMode getHeatMapMode() {
        // default to heatmap
        if (overlayLayer == null)
            return HeatMapMode.Elevation;
        if (overlayLayer.get() == heatMapOverlay)
            return HeatMapMode.Elevation;
        else if (overlayLayer.get() == terrainSlopeOverlay)
            return HeatMapMode.TerrainSlope;
        else
            return HeatMapMode.Elevation;
    }

    public static void setContourLinesVisible(boolean visible) {
        final ContourLinesOverlay overlay = contourLinesOverlay;
        if (overlay.checkZoom()) {
            overlay.setVisible(visible);
            MapView.getMapView().post(new Runnable() {
                public void run() {
                    overlay.getContourData();
                }
            });
        } else {
            Toast.makeText(
                    MapView.getMapView()
                            .getContext(),
                    "Area too large, zoom in to generate contour lines",
                    Toast.LENGTH_LONG).show();
        }
    }

    public static void cancelContourLinesGeneration() {
        final ContourLinesOverlay overlay = contourLinesOverlay;
        if (overlay != null) {
            overlay.setCancelled();
        }
    }

    /**
     * Call to trigger a heat map refresh - used by GvLFStreamer
     */
    public static void refreshHeatmap() {
        heatMapOverlay.setResolution(heatMapOverlay.getSampleResolutionX(),
                heatMapOverlay.getSampleResolutionY());
    }

    private static void refreshMap() {
        final MapRenderer3 renderer = MapView.getMapView().getRenderer3();
        if (renderer != null) {
            final SurfaceRendererControl ctrl = renderer
                    .getControl(SurfaceRendererControl.class);
            if (ctrl != null)
                ctrl.markDirty();
        }
    }

}
