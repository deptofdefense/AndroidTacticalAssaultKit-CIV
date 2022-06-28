
package com.atakmap.android.layers.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.view.IlluminationDialog;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.layer.control.IlluminationControl2;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Overlay manager list item for toggling visibility of illumination and launching configuration
 * dialog.
 */
public final class IlluminationListItem extends AbstractChildlessListItem
        implements Visibility, View.OnClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREF_KEY_IS_ENABLED = "illumination_is_enabled";
    public static final String PREF_KEY_ILLUMINATION_TIME = "illumination_simulated_date_time";
    public static final String PREF_KEY_CURRENT_TIME_LOCK = "illumination_current_time_lock";

    private final MapView _mapView;
    private final Context _context;
    private final IlluminationControl2 _illuminationControl;
    private final AtakPreferences preferences;

    private Timer timer;

    public IlluminationListItem(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        preferences = new AtakPreferences(mapView);

        preferences.registerListener(this);

        _illuminationControl = mapView.getRenderer3()
                .getControl(IlluminationControl2.class);
        refreshState();
        onSharedPreferenceChanged(preferences.getSharedPrefs(),
                PREF_KEY_CURRENT_TIME_LOCK);

    }

    @Override
    public String getTitle() {
        return _context.getString(R.string.sun_moon_illumination);
    }

    @Override
    public String getDescription() {
        return _context.getString(R.string.sun_moon_illumination_description);
    }

    @Override
    public String getUID() {
        return "illuminationVisibility";
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
    public View getExtraView(View v, ViewGroup parent) {
        ImageButton configure = v instanceof ImageButton ? (ImageButton) v
                : null;
        if (configure == null) {
            configure = (ImageButton) LayoutInflater.from(_context)
                    .inflate(R.layout.illumination_configuration_button, parent,
                            false);
        }
        configure.setOnClickListener(this);
        return configure;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.configure_illumination) {
            final Calendar simulatedDateTime = Calendar
                    .getInstance(TimeZone.getTimeZone("UTC"));

            if (_illuminationControl != null) {
                simulatedDateTime
                        .setTimeInMillis(_illuminationControl.getTime());
            } else {
                simulatedDateTime
                        .setTimeInMillis(CoordinatedTime.currentTimeMillis());
            }

            new IlluminationDialog(_mapView)
                    .setTitle(
                            _context.getString(R.string.sun_moon_illumination))
                    .setTime(simulatedDateTime.getTimeInMillis())
                    .setCallback(new IlluminationDialog.Callback() {
                        @Override
                        public void onSetIlluminationSettings(long time,
                                boolean continuous) {

                            preferences.set(PREF_KEY_CURRENT_TIME_LOCK,
                                    continuous);

                            preferences.set(PREF_KEY_ILLUMINATION_TIME,
                                    time);

                        }
                    }).show();
        }
    }

    @Override
    public boolean setVisible(boolean visible) {
        preferences.set(PREF_KEY_IS_ENABLED, visible);
        return true;
    }

    @Override
    public boolean isVisible() {
        return _illuminationControl != null
                && _illuminationControl.getEnabled();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals(PREF_KEY_IS_ENABLED)
                || key.equals(PREF_KEY_ILLUMINATION_TIME)
                || key.equals(PREF_KEY_CURRENT_TIME_LOCK)) {
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(HierarchyListReceiver.REFRESH_HIERARCHY));
            refreshState();
        }
    }

    /**
     * Update map engine state through the illumination control
     */
    private void refreshState() {
        final boolean isEnabled = preferences.get(PREF_KEY_IS_ENABLED, false);
        if (_illuminationControl != null) {
            _illuminationControl.setEnabled(isEnabled);
            _illuminationControl.setTime(
                    preferences.get(PREF_KEY_ILLUMINATION_TIME,
                            CoordinatedTime.currentTimeMillis()));

            if (preferences.get(PREF_KEY_CURRENT_TIME_LOCK, true)
                    && isEnabled) {
                startContinuous();
            } else
                stopContinuous();
        }
    }

    private synchronized void startContinuous() {
        stopContinuous();
        //Log.d("IlluminationTimerTask", "starting automated illumination timer");
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                final CoordinatedTime time = new CoordinatedTime();
                //Log.d("IlluminationTimerTask", "calculating a new illumination time for the map: " + time);
                _illuminationControl.setTime(time.getMilliseconds());
            }
        }, 0, 60000);
    }

    private synchronized void stopContinuous() {
        //Log.d("IlluminationTimerTask", "stopping automated illumination timer");
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

}
