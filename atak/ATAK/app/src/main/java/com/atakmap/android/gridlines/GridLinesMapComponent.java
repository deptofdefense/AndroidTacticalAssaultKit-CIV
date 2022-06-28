
package com.atakmap.android.gridlines;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

import com.atakmap.android.gridlines.graphics.GLGridLinesOverlay;
import com.atakmap.android.imagecapture.CustomGrid;
import com.atakmap.android.imagecapture.GLCustomGrid;
import com.atakmap.android.imagecapture.GridTool;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.Overlay;
import com.atakmap.android.overlay.Overlay.OnVisibleChangedListener;
import com.atakmap.android.overlay.OverlayManager;
import com.atakmap.android.overlay.OverlayManager.OnServiceListener;
import com.atakmap.app.R;
import com.atakmap.map.layer.opengl.GLLayerFactory;

public class GridLinesMapComponent extends AbstractMapComponent implements
        OnServiceListener, OnVisibleChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;

        GLLayerFactory.register(GLGridLinesOverlay.SPI2);

        // Custom size grid
        _customGrid = new CustomGrid(view, GridTool.GRID_UID);
        GLLayerFactory.register(GLCustomGrid.SPI);
        _mapView.addLayer(MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                _customGrid);

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
        _overlay = new GridLinesOverlay();
        _setPrefs(prefs);
        this.overlayAdded = false;
        if (_showByDefault) {
            toggleGridLines();
        }

        Intent sharedOverlayServiceIntent = new Intent();
        sharedOverlayServiceIntent
                .setAction("com.atakmap.android.overlay.SHARED");
        if (!OverlayManager.aquireService(context, sharedOverlayServiceIntent,
                this)) {

            // try again but embed locally
            OverlayManager
                    .aquireService(context, null, this);
        }
    }

    @Override
    public void onOverlayManagerBind(OverlayManager manager) {
        _overlayManager = manager;
        _gridLinesOverlay = manager.registerOverlay("Grid Lines");
        _gridLinesOverlay.setVisible(_showByDefault);
        _gridLinesOverlay
                .setIconUri("android.resource://" +
                        _mapView.getContext().getPackageName() + "/" +
                        R.drawable.ic_overlay_gridlines);
        _gridLinesOverlay
                .addOnVisibleChangedListener(this);
    }

    @Override
    public void onOverlayManagerUnbind(OverlayManager manager) {
        _overlayManager = null;
        _gridLinesOverlay = null;
    }

    @Override
    public void onOverlayVisibleChanged(Overlay overlay) {
        toggleGridLines();
        prefs.edit().putBoolean("prefs_grid_default_show", overlayAdded)
                .apply();
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        if (key.equals("pref_grid_color") || key.equals("pref_grid_type")) {
            _setPrefs(sharedPreferences);
        }
    }

    private void _setPrefs(SharedPreferences prefs) {
        _gridColor = Color.parseColor(prefs.getString("pref_grid_color",
                "#ffffff"));
        _showByDefault = prefs.getBoolean("prefs_grid_default_show", false);
        _type = prefs.getString("pref_grid_type", "MGRS");
        if (_overlay != null) {
            _overlay.setColor(_gridColor);
            _overlay.setType(_type);
        }
    }

    private void toggleGridLines() {
        if (this.overlayAdded) {
            _mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, _overlay);
            this.overlayAdded = false;
        } else {
            _overlay.setColor(_gridColor);
            _mapView.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, 0, _overlay);
            this.overlayAdded = true;
        }
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        if (_overlayManager != null) {
            _overlayManager.releaseService();
        }
        _customGrid.clear();
        _mapView.removeLayer(MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                _customGrid);
        GLLayerFactory.unregister(GLCustomGrid.SPI);
    }

    private boolean _showByDefault;
    private Overlay _gridLinesOverlay;
    private OverlayManager _overlayManager;
    private MapView _mapView;
    private GridLinesOverlay _overlay;
    private static CustomGrid _customGrid;
    private boolean overlayAdded;
    private int _gridColor;
    private SharedPreferences prefs;
    private String _type;

    public static CustomGrid getCustomGrid() {
        return _customGrid;
    }

}
