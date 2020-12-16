
package com.atakmap.android.user;

import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.LayerSelection;
import com.atakmap.android.layers.LayerSelectionAdapter;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

import java.util.List;

/**
 * Responsible for overriding the volume switch action within the ATAK system.    The default
 * behavior is to provide the layer switcher.  Can be overridden by plugins.
 */

public class VolumeSwitchManager implements OnKeyListener {

    public static final String VOLUME_PRESS = "com.atakmap.android.user.VOLUME_PRESS";

    private static VolumeSwitchManager _instance = null;

    private boolean enabled = true;

    private VolumeSwitchAction vsa = null;
    private final LayerCycleVolumeAction lcva;

    /**
     *  If the volume switch is actively being intercepted by ATAK, the following method will be
     *  called when the direction is UP or DOWN
     */

    public interface VolumeSwitchAction {

        // The direction of the volume switch was pushed is UP or FORWARD
        int FORWARD = 1;

        // The direction of the volume switch was pushed is DOWN or BACKWARD
        int BACKWARD = -1;

        void direction(int direction);

    }

    private final MapView _mapView;

    private VolumeSwitchManager(MapView mapView) {
        _mapView = mapView;
        lcva = new LayerCycleVolumeAction();
    }

    /**
     * Used to override the default behavior in of the volume switch in ATAK.
     * Supplying null will restore the default behavior.
     * @param vsa volume switch action.
     */
    public void setVolumeSwitchAction(VolumeSwitchAction vsa) {
        this.vsa = vsa;
    }

    public void setActiveAdapter(final LayerSelectionAdapter adapter) {
        lcva.setLayerSelectionAdapter(adapter);

    }

    public static synchronized VolumeSwitchManager getInstance(
            MapView mapView) {
        if (_instance == null) {
            _instance = new VolumeSwitchManager(mapView);
        }
        return _instance;
    }

    /**
     * if enabled, when the volume keys are pressed, the map manager will cycle through the map
     * list. If disabled, any volume key presses will be completely ignored
     * 
     * @param enabled if the map manager will cycle through the layers.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        int action = event.getAction();
        if (!enabled
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                        || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN))
            return false;

        if (action != KeyEvent.ACTION_DOWN)
            return false;

        int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                direction = VolumeSwitchAction.BACKWARD;
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                direction = VolumeSwitchAction.FORWARD;
                break;
            default:
                return false;
        }

        Intent intent = new Intent(VOLUME_PRESS);
        intent.putExtra("direction", direction);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        if (vsa != null)
            vsa.direction(direction);
        else
            lcva.direction(direction);

        return true;
    }

    /**
     * Implementation of the layer switcher has been kept in the Volume Switch Manager for legacy
     * purposes.   This is the default action.
     */
    private class LayerCycleVolumeAction implements VolumeSwitchAction {
        private LayerSelectionAdapter activeAdapter;
        private Toast _toast = null;

        void setLayerSelectionAdapter(LayerSelectionAdapter lsa) {
            activeAdapter = lsa;
        }

        @Override
        public synchronized void direction(int direction) {
            final List<LayerSelection> ls = this.activeAdapter
                    .getAllSelectionsAt(_mapView.getPoint().get());
            final LayerSelection s = this.activeAdapter.getSelected();

            int selectedIndex = ls.indexOf(s);

            // if the selectionIndex is -1, this is most likely a case where
            // mobile layer adaper is being used.   layer equality during 3.1
            // is broken, just search on name.
            if (selectedIndex == -1 && s != null) {
                for (int i = 0; i < ls.size(); ++i)
                    if (ls.get(i).getName().equalsIgnoreCase(s.getName())) {
                        selectedIndex = i;
                    }
            }

            final int cycleIndex = selectedIndex + direction;

            if (cycleIndex < 0 || cycleIndex >= ls.size()) {
                _showToast2(null);
            } else {
                this.activeAdapter.setSelected(ls.get(cycleIndex));
                this.activeAdapter.setLocked(true);
                this.activeAdapter.notifyDataSetChanged();
                _showToast2(this.activeAdapter.getSelected());
            }
        }

        private void _showToast2(LayerSelection l) {
            String message = l != null ? l.getName()
                    : _mapView.getContext()
                            .getString(R.string.no_more_data);

            // If toast exists, just change it's content so the toast on screen is
            // always up to date.
            // NOTE: tried using _toast.cancel() but it doesn't work apparently
            if (_toast != null)
                _toast.setText(message);
            else
                _toast = Toast.makeText(_mapView.getContext(), message,
                        Toast.LENGTH_SHORT);

            _toast.show();
        }
    }
}
