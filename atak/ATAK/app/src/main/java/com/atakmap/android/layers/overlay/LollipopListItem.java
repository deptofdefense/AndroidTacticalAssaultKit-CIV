
package com.atakmap.android.layers.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.map.layer.control.LollipopControl;
import com.atakmap.app.R;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.Layer2;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Overlay manager list item for toggling visibility of marker altitude "lollipops"
 */
public class LollipopListItem extends AbstractChildlessListItem implements
        LollipopControl, SharedPreferences.OnSharedPreferenceChangeListener,
        Visibility {

    private static final String PREF_KEY = "marker_lollipop_visibility";

    private final Context _context;
    private final MapRenderer3 _renderer;
    private final AtakPreferences _prefs;

    private boolean _lollipopVis;

    public LollipopListItem(MapView mapView) {
        _context = mapView.getContext();
        _renderer = mapView.getRenderer3();

        _prefs = new AtakPreferences(mapView);
        _prefs.registerListener(this);

        _lollipopVis = _prefs.get(PREF_KEY, true);
        refreshState();

        _renderer.registerControl(null, this);
    }

    @Override
    public boolean getLollipopsVisible() {
        return _lollipopVis;
    }

    @Override
    public void setLollipopsVisible(boolean v) {
        _lollipopVis = v;
        _prefs.set(PREF_KEY, v);
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(HierarchyListReceiver.REFRESH_HIERARCHY));
        refreshState();
    }

    @Override
    public String getTitle() {
        return _context.getString(R.string.lollipop_visibility);
    }

    @Override
    public String getDescription() {
        return _context.getString(R.string.lollipop_description);
    }

    @Override
    public String getUID() {
        return "lollipopVisibility";
    }

    @Override
    public Drawable getIconDrawable() {
        return _context.getDrawable(R.drawable.lollipop);
    }

    @Override
    public Object getUserObject() {
        return this;
    }

    @Override
    public boolean setVisible(boolean visible) {
        if (visible != _lollipopVis) {
            _lollipopVis = visible;
            _prefs.set(PREF_KEY, _lollipopVis);
            refreshState();
            return true;
        }
        return false;
    }

    @Override
    public boolean isVisible() {
        return _lollipopVis;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals(PREF_KEY)) {
            _lollipopVis = _prefs.get(key, true);
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(HierarchyListReceiver.REFRESH_HIERARCHY));
            refreshState();
        }
    }

    /**
     * Mass change of all of the controls for lollipop visibility.
     */
    private void refreshState() {
        _renderer.visitControls(
                new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                    @Override
                    public void visit(
                            Iterator<Map.Entry<Layer2, Collection<MapControl>>> object) {
                        while (object.hasNext()) {
                            for (MapControl c : object.next().getValue()) {
                                if (c instanceof LollipopControl) {
                                    ((LollipopControl) c)
                                            .setLollipopsVisible(_lollipopVis);
                                }
                            }
                        }
                    }
                });
    }
}
