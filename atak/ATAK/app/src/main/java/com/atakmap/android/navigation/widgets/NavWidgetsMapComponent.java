
package com.atakmap.android.navigation.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.Overlay;
import com.atakmap.android.overlay.OverlayManager;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.CenterBeadWidget;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapFocusTextWidget;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.ScaleWidget;
import com.atakmap.app.R;

/**
 * Manages the scale bar and center designator widget
 */
public class NavWidgetsMapComponent extends AbstractMapComponent
        implements MapWidget2.OnWidgetSizeChangedListener,
        OverlayManager.OnServiceListener,
        Overlay.OnVisibleChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private UnitPreferences _prefs;

    // Scale bar
    private LinearLayoutWidget beLayout;
    private ScaleWidget scale;

    // Center cross-hair
    private CenterBeadWidget cb;
    private MapFocusTextWidget cbText;
    private Overlay cbOverlay;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        _prefs = new UnitPreferences(view);

        RootLayoutWidget root = (RootLayoutWidget) view.getComponentExtra(
                "rootLayoutWidget");
        LinearLayoutWidget blLayout = root.getLayout(
                RootLayoutWidget.BOTTOM_LEFT);
        beLayout = root.getLayout(RootLayoutWidget.BOTTOM_EDGE);

        // Scale bar
        final MapTextFormat mt = MapView.getTextFormat(Typeface.DEFAULT_BOLD,
                -2);
        this.scale = new ScaleWidget(view, mt);
        this.scale.setName("Map Scale");
        this.scale.setPadding(8f, 4f, 8f, 4f);
        this.scale.setMargins(16f, 0f, 16f, 0f);

        boolean scale_vis = _prefs.get("map_scale_visible", true);
        boolean scale_rounding = _prefs.get("map_scale_rounding", false);
        this.scale.setVisible(scale_vis);
        this.scale.setRounding(scale_rounding);
        this.scale.setRangeUnits(_prefs.getRangeSystem());
        beLayout.addWidgetAt(0, this.scale);

        // Center cross-hair
        boolean cbVisible = _prefs.get("map_center_designator", false);
        this.cb = new CenterBeadWidget();
        this.cb.setVisible(cbVisible);
        root.addWidget(this.cb);

        // Center coordinate text (corresponds with center cross-hair)
        this.cbText = new MapFocusTextWidget();
        this.cbText.setMargins(16f, 16f, 0f, 16f);
        this.cbText.setVisible(cbVisible);
        this.cbText.setText(" ");
        blLayout.addWidgetAt(0, this.cbText);

        Intent omIntent = new Intent("com.atakmap.android.overlay.SHARED");
        if (!OverlayManager.aquireService(context, omIntent, this)) {
            // try again but embed locally
            OverlayManager.aquireService(context, null, this);
        }

        beLayout.addOnWidgetSizeChangedListener(this);
        _prefs.registerListener(this);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        beLayout.removeOnWidgetSizeChangedListener(this);
        _prefs.unregisterListener(this);
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 widget) {
        if (this.scale != null)
            this.scale.setMaxWidth(beLayout.getWidth());
    }

    @Override
    public void onOverlayManagerBind(OverlayManager manager) {
        cbOverlay = manager.registerOverlay("Center Designator");
        cbOverlay.setVisible(cb.isVisible());
        cbOverlay.setIconUri(ATAKUtilities.getResourceUri(
                R.drawable.ic_center_designator));
        cbOverlay.addOnVisibleChangedListener(this);
    }

    @Override
    public void onOverlayManagerUnbind(OverlayManager manager) {
        if (cbOverlay != null) {
            cbOverlay.removeOnVisibleChangedListener(this);
            cbOverlay = null;
        }
    }

    @Override
    public void onOverlayVisibleChanged(Overlay overlay) {
        final boolean current = _prefs.get("map_center_designator", false);
        if (current != overlay.getVisible()) {
            _prefs.set("map_center_designator", overlay.getVisible());
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences prefs, final String key) {

        if (key == null)
            return;

        switch (key) {
            case "map_scale_visible":
                if (scale != null)
                    scale.setVisible(prefs.getBoolean(key, true));
                break;
            case "map_center_designator":
                boolean visible = prefs.getBoolean(key, true);
                if (cb != null) {
                    cb.setVisible(visible);
                    cbText.setVisible(visible);
                }
                if (cbOverlay != null)
                    cbOverlay.setVisible(visible);
                break;
            case "map_scale_rounding":
                if (scale != null)
                    scale.setRounding(prefs.getBoolean(key, false));
                break;
            case "rab_rng_units_pref":
                if (scale != null)
                    scale.setRangeUnits(_prefs.getRangeSystem());
                break;
        }
    }
}
