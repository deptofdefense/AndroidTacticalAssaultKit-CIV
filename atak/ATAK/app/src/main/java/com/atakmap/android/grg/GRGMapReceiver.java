
package com.atakmap.android.grg;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.Toast;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.widgets.SeekBarControl;
import com.atakmap.android.widgets.SeekBarControlCompat;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetRasterLayer2;
import com.atakmap.map.layer.raster.PersistentRasterDataStore;

import java.io.File;

/**
 * Receiver for GRG-related radial intents
 */
public class GRGMapReceiver extends BroadcastReceiver implements
        ClearContentRegistry.ClearContentListener, SeekBarControl.Subject,
        MapEventDispatcher.MapEventDispatchListener {

    private static final String TAG = "GRGMapReceiver";

    // Intent actions
    public static final String OUTLINE_VISIBLE = "com.atakmap.android.grg.OUTLINE_VISIBLE";
    public static final String TRANSPARENCY = "com.atakmap.android.grg.TRANSPARENCY";
    public static final String BRIGHTNESS = "com.atakmap.android.grg.BRIGHTNESS";
    public static final String COLOR = "com.atakmap.android.grg.COLOR";
    public static final String TOGGLE_VISIBILITY = "com.atakmap.android.grg.TOGGLE_VISIBILITY";

    // Slider modes
    private static final int SLIDER_NONE = 0,
            SLIDER_TRANSPARENCY = 1,
            SLIDER_BRIGHTNESS = 2;

    private final MapView _mapView;
    private final Context _context;
    private final FeatureDataStore _outlinesDB;
    private final PersistentRasterDataStore _rasterDB;
    private final DatasetRasterLayer2 _rasterLayer;
    private final GRGMapOverlay _overlay;

    // Active dataset when changing transparency
    private DatasetDescriptor _selectedInfo;
    private int _sliderMode = SLIDER_NONE;
    private final float[] _hsv = new float[3];
    private boolean _layerVisible;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public GRGMapReceiver(MapView mapView, FeatureDataStore outlinesDB,
            PersistentRasterDataStore rasterDB, DatasetRasterLayer2 rasterLayer,
            GRGMapOverlay overlay) {
        _mapView = mapView;
        _context = mapView.getContext();
        _outlinesDB = outlinesDB;
        _rasterDB = rasterDB;
        _rasterLayer = rasterLayer;
        _overlay = overlay;

        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(OUTLINE_VISIBLE, "Toggle visibility of GRG outlines");
        f.addAction(TRANSPARENCY, "Set GRG transparency");
        f.addAction(BRIGHTNESS, "Set GRG brightness");
        f.addAction(COLOR, "Set GRG color modulation");
        f.addAction(TOGGLE_VISIBILITY, "Toggle GRG visibility");
        AtakBroadcast.getInstance().registerReceiver(this, f);
        ClearContentRegistry.getInstance().registerListener(this);
    }

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public void dispose() {
        AtakBroadcast.getInstance().unregisterReceiver(this);
        ClearContentRegistry.getInstance().unregisterListener(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        // Toggle all GRG outlines
        if (action.equals(OUTLINE_VISIBLE)) {
            boolean b = intent.getBooleanExtra("visible", true);
            Log.d(TAG, "setting visibility for grg call: " + b);
            _outlinesDB.setFeatureSetsVisible(null, b);
            return;
        }

        DatasetDescriptor info = null;
        MapItem item = _mapView.getMapItem(intent.getStringExtra("uid"));
        if (item instanceof ImageOverlay)
            info = ((ImageOverlay) item).getLayerInfo();

        // Null check
        if (info == null) {
            if (action.equals(TRANSPARENCY)) {
                Toast.makeText(_mapView.getContext(),
                        R.string.unable_adjust_transparency_grg,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Make sure layer is visible when adjusting values
        if (!action.equals(TOGGLE_VISIBILITY)) {
            _layerVisible = _rasterLayer.isVisible(info.getName());
            _rasterLayer.setVisible(info.getName(), true);
        }

        switch (action) {

            // Set GRG transparency
            case TRANSPARENCY: {
                openSlider(info, SLIDER_TRANSPARENCY);
                break;
            }

            // Set GRG color modulation
            case COLOR: {
                promptGRGColor(info);
                break;
            }

            // Set GRG brightness via color modulation
            case BRIGHTNESS: {
                int c = getColor(info);
                Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c),
                        _hsv);
                openSlider(info, SLIDER_BRIGHTNESS);
                break;
            }

            // Toggle GRG visibility
            case TOGGLE_VISIBILITY: {
                String layerName = item.getMetaString("layerName", null);
                if (FileSystemUtils.isEmpty(layerName))
                    return;

                boolean visible = !_rasterLayer.isVisible(layerName);
                _rasterLayer.setVisible(layerName, visible);

                // Persist to extra data
                info.setExtraData("visible", String.valueOf(visible));
                _rasterDB.updateExtraData(info);

                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        HierarchyListReceiver.REFRESH_HIERARCHY));
                break;
            }
        }
    }

    @Override
    public int getValue() {
        if (_selectedInfo != null) {
            if (_sliderMode == SLIDER_TRANSPARENCY)
                return (int) (_rasterLayer.getTransparency(
                        _selectedInfo.getName()) * 100);
            else if (_sliderMode == SLIDER_BRIGHTNESS)
                return (int) (_hsv[2] * 100);
        }
        return 100;
    }

    @Override
    public void setValue(int value) {
        if (_selectedInfo != null) {
            if (_sliderMode == SLIDER_TRANSPARENCY)
                _rasterLayer.setTransparency(_selectedInfo.getName(),
                        value / 100f);
            else if (_sliderMode == SLIDER_BRIGHTNESS) {
                _hsv[2] = value / 100f;
                int c = getColor(_selectedInfo);
                updateColor(_selectedInfo, Color.HSVToColor(_hsv),
                        Color.alpha(c), false);
            }
        }
    }

    @Override
    public void onControlDismissed() {
        // Persist alpha/brightness to color
        if (_selectedInfo != null) {
            int color = getColor(_selectedInfo);
            int alpha = (int) (_rasterLayer.getTransparency(
                    _selectedInfo.getName()) * 255);
            if (_sliderMode == SLIDER_BRIGHTNESS)
                color = Color.HSVToColor(_hsv);
            updateColor(_selectedInfo, color, alpha, true);
        }
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.MAP_CLICK, this);
        _selectedInfo = null;
        _sliderMode = SLIDER_NONE;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        // the user clicked the map while the bar is showing, remove the
        // slider
        if (event.getType().equals(MapEvent.MAP_CLICK) && _selectedInfo != null)
            SeekBarControl.dismiss();
    }

    @Override
    public void onClearContent(boolean clearmaps) {
        Log.d(TAG, "Deleting GRGs");
        //remove from database
        try {
            _rasterDB.clear();
        } catch (Exception e) {
            Log.d(TAG, "database error during clear");
        }

        //remove from UI
        MapGroup mapGroup = _overlay.getRootGroup();
        if (mapGroup != null) {
            Log.d(TAG, "Clearing map group: " + mapGroup.getFriendlyName());
            mapGroup.clearGroups();
            mapGroup.clearItems();
        }

        //remove files
        final String[] mountPoints = FileSystemUtils.findMountPoints();
        for (String mountPoint : mountPoints) {
            File scanDir = new File(mountPoint, "grg");
            if (IOProviderFactory.exists(scanDir)
                    && IOProviderFactory.isDirectory(scanDir))
                FileSystemUtils.deleteDirectory(scanDir, true);
        }
    }

    private void openSlider(DatasetDescriptor info, int mode) {
        // show the seek bar control widget
        SeekBarControl.dismiss();
        _selectedInfo = info;
        _sliderMode = mode;
        SeekBarControlCompat.show(this, 5000L);
        // add a click listener to dismiss the slider
        _mapView.getMapEventDispatcher().addMapEventListenerToBase(
                MapEvent.MAP_CLICK, this);
    }

    private void promptGRGColor(final DatasetDescriptor info) {
        // Dismiss active seek bar first
        SeekBarControl.dismiss();

        int color = getColor(info);
        final int alpha = Color.alpha(color);

        AlertDialog.Builder b = new AlertDialog.Builder(_context)
                .setTitle(R.string.point_dropper_text21);
        ColorPalette palette = new ColorPalette(_context, color);
        b.setView(palette);
        final AlertDialog d = b.create();
        ColorPalette.OnColorSelectedListener l = new ColorPalette.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                updateColor(info, color, alpha, true);
                d.dismiss();
            }
        };
        palette.setOnColorSelectedListener(l);
        d.show();
    }

    private int getColor(DatasetDescriptor info) {
        return MathUtils.parseInt(info.getExtraData("color"), Color.WHITE);
    }

    private void updateColor(DatasetDescriptor info, int color, int alpha,
            boolean persist) {
        color = (alpha << 24) | (color & 0xFFFFFF);
        info.setExtraData("color", String.valueOf(color));

        // Update attributes in DB
        if (persist)
            _rasterDB.updateExtraData(info);

        // Ping the raster layer to update or restore visibility
        _rasterLayer.setVisible(info.getName(), !persist || _layerVisible);
    }
}
