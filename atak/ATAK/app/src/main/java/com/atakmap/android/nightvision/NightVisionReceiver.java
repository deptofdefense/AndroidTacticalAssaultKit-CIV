
package com.atakmap.android.nightvision;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

/**
 *
 * Broadcast Receiver that intercepts intent calls to show the
 * adjustment seekbar on the map view- allowing a user to adjust the dim value from within atak giving them
 * more customization
 */

public class NightVisionReceiver extends BroadcastReceiver
        implements SeekBar.OnSeekBarChangeListener {

    private static final String TAG = "NightVisionReceiver";
    private final MapView _mapView;

    public static final String ADJUST_NIGHT_VISION_VALUE = "adjust_night_vision_value";
    public static final String UPDATE_DIM_VALUE = "update_dim_value";
    public static final String GET_DIM_VALUE = "com.atak.nightvision.send_dim_value";

    //display window for seeks
    protected PopupWindow _controlWindow;
    protected HideControlAction _hideControlAction = null;
    public static final int MAX_NV_DIM = 91;//the largest value to give dim computes to 90% dim

    public static NightVisionReceiver instance;
    private SeekBar seekBar;

    public NightVisionReceiver(final MapView mapview) {
        this._mapView = mapview;
        instance = this;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            Log.d(TAG, "action: " + action);
            if (action.equals(ADJUST_NIGHT_VISION_VALUE)) {
                showSeekbar();
                getValue();
            } else if (action.equals(GET_DIM_VALUE)) {
                bindProgress(intent.getFloatExtra("value", 0.5f));
            }
        }
    }

    private void bindProgress(final float value) {
        final SeekBar bar = NightVisionReceiver.instance.seekBar;
        if (bar != null) {
            MapView.getMapView().post(new Runnable() {
                @Override
                public void run() {
                    float dim = (((value) * 100));
                    int intDim = (int) dim;
                    bar.setProgress(MAX_NV_DIM - intDim);
                }
            });
        } else {
            Log.d(TAG, "seekbar is null");
        }
    }

    /**
     * writes the value dim file using the supplied float value
     * @param value change the current dim value
     */
    public void writeValue(final float value) {
        Intent intent = new Intent("com.atak.nightvision.set_dim_value");
        intent.putExtra("value", value);
        AtakBroadcast.getInstance().sendSystemBroadcast(intent);
    }

    /**
     * Returns the value stored for the current user saved dim value
     * @return float value indicating saved dim value
     */
    public void getValue() {
        //send intent out to get current value
        Intent intent = new Intent("com.atak.nightvision.get_dim_value");
        AtakBroadcast.getInstance().sendSystemBroadcast(intent);
    }

    /**
     * converts the seekbar progress to a dim value from 0.0-.90f
     * this is the value we used to store the dim value and set the window
     * parameter to this dim value
     * @param progress the progress bar for the dim value
     * @return float of a dim value (0.0f-0.90f)
     */
    public static float convertSliderToDim(final int progress) {
        float d = MAX_NV_DIM - progress;
        return ((d) * .01f);
    }

    /**
     * Builds the seekbar inside a ControlWindow
     */
    private void showSeekbar() {

        if (_controlWindow != null) {
            hideControl();
            return;
        }

        LayoutInflater inflater = (LayoutInflater) _mapView.getContext()
                .getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        RelativeLayout brightnessControl = (RelativeLayout) inflater.inflate(
                R.layout.brightness_control, null);

        seekBar = brightnessControl
                .findViewById(R.id.map_brightness_control);
        seekBar.setMax(MAX_NV_DIM);
        seekBar.setProgress(MAX_NV_DIM / 2);
        seekBar.setOnSeekBarChangeListener(this);

        DisplayMetrics displayMetrics = _mapView.getContext().getResources()
                .getDisplayMetrics();
        float width = (float) displayMetrics.widthPixels * 0.4f;
        float height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                50, displayMetrics);

        _controlWindow = new PopupWindow(_mapView.getContext());

        _controlWindow.setContentView(brightnessControl);
        _controlWindow.setWidth((int) width);
        _controlWindow.setHeight((int) height);
        _controlWindow.setBackgroundDrawable(new ColorDrawable(
                android.graphics.Color.TRANSPARENT));
        _controlWindow.showAtLocation(
                _mapView, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0,
                (int) height); //bump up 50px above the brightness bar so they do not collide
        resetHideTimer();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        startHideTimer();
        writeValue(convertSliderToDim(seekBar.getProgress()));
        handleChangesInDim(
                convertSliderToDim(seekBar.getProgress()));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        stopHideTimer();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {

    }

    /**
     * Stops and creates the service when changing the dim value
     * by sending a intent into the service and updating the view attachment
     */
    public void handleChangesInDim(float change) {
        Intent changeIntent = new Intent("nightvision.com.atak.NVG_MODE_ON");
        changeIntent.putExtra(UPDATE_DIM_VALUE, change);
        AtakBroadcast.getInstance().sendSystemBroadcast(changeIntent);
    }

    /**
     * removes the control window which
     * houses the seekbar from view of the mapview
     */
    public void hideControl() {
        stopHideTimer();

        if (_controlWindow != null) {
            _controlWindow.dismiss();
            _controlWindow = null;
        }
    }

    /**
     * nullifies the runnable hide timer
     */
    protected void stopHideTimer() {
        if (_hideControlAction != null) {
            _hideControlAction.cancel();
            _hideControlAction = null;
        }
    }

    /**
     * starts the runnable hide timer when a user
     * stops tracking on the seekbar
     */
    protected void startHideTimer() {
        if (_hideControlAction == null) {
            _hideControlAction = new HideControlAction();
            _mapView.postDelayed(_hideControlAction, 4000);
        }
    }

    protected void resetHideTimer() {
        stopHideTimer();
        startHideTimer();
    }

    public void onDestroy() {

        //hide and disbatch popup window properly
        if (_hideControlAction != null)
            _hideControlAction.cancel();

        if (_controlWindow != null)
            _controlWindow.dismiss();

    }

    private class HideControlAction implements Runnable {

        boolean _cancelled = false;

        @Override
        public void run() {
            if (!_cancelled) {
                _hideControlAction = null;
                hideControl();
            }
        }

        public void cancel() {
            _cancelled = true;
        }
    }
}
