
package com.atakmap.android.brightness;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.widgets.SeekBarControl;
import com.atakmap.android.widgets.SeekBarControlCompat;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.math.MathUtils;
import com.atakmap.annotations.ModifierApi;

import java.util.concurrent.atomic.AtomicInteger;

public class BrightnessReceiver extends BroadcastReceiver implements
        SeekBarControl.Subject {

    public final static int MAX_BRIGHTNESS_MAGNITUDE = 255;
    public final static String BRIGHT_PREF = "brightness";
    protected static final String NAV_REF = "brightness.xml";

    protected final MapView _mapView;
    protected BrightnessOverlay _brightnessOverlay;
    protected boolean _showingControl;

    protected AtomicInteger _brightness = new AtomicInteger(0);

    private static SharedPreferences _prefs;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public BrightnessReceiver(MapView mapView) {
        _mapView = mapView;

        GLMapSurface surface = mapView.findViewWithTag(
                GLMapSurface.LOOKUP_TAG);

        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        int brightness = _prefs.getInt(BRIGHT_PREF, -1);
        if (brightness != -1) {
            _brightness = new AtomicInteger(brightness);
        }

        _brightnessOverlay = new BrightnessOverlay(surface, _brightness);
        _brightnessOverlay.scheduleSetupOnGLThread();

        _showingControl = false;
    }

    @Override
    public void onReceive(final Context ctx, final Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action
                .equals(BrightnessComponent.SHOW_BRIGHTNESS_TOOL)) {
            int value = intent.getIntExtra("value", -1);
            if (value < 0)
                toggleBrightnessControl();
            else
                setValue((int) (value * 2.55f));
        }
    }

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public void dismiss() {
        hideControl();
        _brightnessOverlay.scheduleDismissOnGLThread();
        _brightnessOverlay.dispose();
        _brightnessOverlay = null;
    }

    protected void toggleBrightnessControl() {
        if (_showingControl) {
            hideControl();
            return;
        }

        SeekBarControlCompat.show(this, 5000L);
        _showingControl = true;
        NavView.getInstance().setButtonSelected(NAV_REF, true);
    }

    protected void hideControl() {
        if (_showingControl) {
            SeekBarControl.dismiss();
            _showingControl = false;
        }
    }

    @Override
    public int getValue() {
        final int max = MAX_BRIGHTNESS_MAGNITUDE * 2 + 1;
        final int value = _brightness.get() + MAX_BRIGHTNESS_MAGNITUDE;

        return MathUtils.clamp(
                (int) (((double) value / (double) max) * 100d), 0, 100);
    }

    @Override
    public void setValue(int value) {
        final int max = MAX_BRIGHTNESS_MAGNITUDE * 2 + 1;

        final int brightnessValue = MathUtils.clamp(
                (int) (((double) value / 100d) * max), 0, max);
        _brightness.set(brightnessValue - MAX_BRIGHTNESS_MAGNITUDE);
        _prefs.edit()
                .putInt(BRIGHT_PREF, _brightness.intValue())
                .apply();

        final SurfaceRendererControl ctrl = _mapView.getRenderer3()
                .getControl(SurfaceRendererControl.class);
        if (ctrl != null)
            ctrl.markDirty();
    }

    @Override
    public void onControlDismissed() {
        _showingControl = false;
        NavView.getInstance().setButtonSelected(NAV_REF, false);
    }

}
