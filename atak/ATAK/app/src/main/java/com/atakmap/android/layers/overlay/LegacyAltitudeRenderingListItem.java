
package com.atakmap.android.layers.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.app.R;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer3;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * The overlay manager list item for toggling enhanced depth perception rendering rendering
 */
public class LegacyAltitudeRenderingListItem extends AbstractChildlessListItem
        implements ClampToGroundControl,
        SharedPreferences.OnSharedPreferenceChangeListener, Visibility {

    private static final String PREF_KEY = "enhanced_depth_perception_rendering";

    private final Context _context;
    private final MapRenderer3 _renderer;
    private final AtakPreferences _prefs;
    private boolean _clampToGroundAtNadir;

    public LegacyAltitudeRenderingListItem(MapView mapView) {
        _context = mapView.getContext();
        _renderer = mapView.getRenderer3();

        _prefs = new AtakPreferences(mapView);
        _prefs.registerListener(this);

        _clampToGroundAtNadir = !_prefs.get(PREF_KEY, true);
        refreshState();

        _renderer.registerControl(null, this);
    }

    @Override
    public void setClampToGroundAtNadir(boolean v) {
        _clampToGroundAtNadir = v;
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.REFRESH_HIERARCHY));
        _prefs.set(PREF_KEY, !_clampToGroundAtNadir);
        refreshState();
    }

    @Override
    public boolean getClampToGroundAtNadir() {
        return _clampToGroundAtNadir;
    }

    @Override
    public String getTitle() {
        return _context.getString(R.string.legacy_altitude_rendering);
    }

    @Override
    public String getUID() {
        return "legacyAltitudeRendering";
    }

    @Override
    public String getDescription() {
        return _context.getString(R.string.disable_parallax_viewing);
    }

    @Override
    public String getIconUri() {
        return "gone";
    }

    @Override
    public Object getUserObject() {
        return this;
    }

    @Override
    public boolean setVisible(boolean visible) {
        if (visible != !_clampToGroundAtNadir) {
            _clampToGroundAtNadir = !visible;
            _prefs.set(PREF_KEY, visible);
            refreshState();
            return true;
        }
        return false;
    }

    @Override
    public boolean isVisible() {
        return !getClampToGroundAtNadir();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals(PREF_KEY)) {
            _clampToGroundAtNadir = !_prefs.get(key, true);
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    HierarchyListReceiver.REFRESH_HIERARCHY));
            refreshState();
        }
    }

    /**
     * Mass change of all of the layer controls for clampToGroundAtNadir.
     */
    private void refreshState() {
        _renderer.visitControls(
                new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                    @Override
                    public void visit(
                            Iterator<Map.Entry<Layer2, Collection<MapControl>>> object) {
                        while (object.hasNext()) {
                            for (MapControl c : object.next().getValue()) {
                                if (c instanceof ClampToGroundControl)
                                    ((ClampToGroundControl) c)
                                            .setClampToGroundAtNadir(
                                                    _clampToGroundAtNadir);
                            }
                        }
                    }
                });
    }
}
