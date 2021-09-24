
package com.atakmap.android.bloodhound.ui;

import android.graphics.Typeface;

import com.atakmap.android.bloodhound.BloodHoundPreferences;
import com.atakmap.android.bloodhound.BloodHoundTool;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.coremap.locale.LocaleUtil;

import org.apache.commons.lang.StringUtils;

/**
 * A TextWidget responsible for handling the display of the bloodhound information.
 *  Includes:
 *    <ul>
 *        <li>Callsign of target map item</li>
 *        <li>Current location of the target map item</li>
 *        <li>Range and bearing information to the target map item</li>
 *        <li>An ETA to the target map item</li>
 *    </ul>
 */
public class BloodHoundHUD extends TextWidget
        implements MapWidget.OnLongPressListener {

    private static final String TAG = "BloodHoundHUD";

    private final BloodHoundPreferences _prefs;
    private final MapView _mapView;
    private MapTextFormat _format;

    private BloodHoundTool _bloodhoundTool;

    private final LinearLayoutWidget layoutWidget;

    public void setToolbarButton(BloodHoundTool button) {
        _bloodhoundTool = button;
        // try to compute the largest width without doing this each time the text is redrawn
        int currentColor = _bloodhoundTool.currentColor();
        setColor(currentColor);
        addOnLongPressListener(this);
    }

    public BloodHoundHUD(MapView mapView) {
        _prefs = new BloodHoundPreferences(mapView);
        _mapView = mapView;

        setName("Bloodhound Text");

        int textSize = _prefs.get("rab_bloodhound_large_textwidget",
                false) ? isTablet() ? 16 : 10 : isTablet() ? 6 : 3;
        _format = MapView.getTextFormat(Typeface.DEFAULT_BOLD, textSize);
        setTextFormat(_format);

        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");

        this.layoutWidget = root.getLayout(RootLayoutWidget.BOTTOM_LEFT)
                .getOrCreateLayout("BL_H/BL_V/Bloodhound_V/");
        setLayoutVisible(false);
        this.layoutWidget.setMargins(16f, 0f, 0f, 16f);

        //end Bloodhound ETA Flash and Radius Color and Time prefs

        this.layoutWidget.addWidget(this);
    }

    /** Updates the text displayed in the text widget with the supplied
     *  value. When supplied an argument of "", closes the textWidget.  */
    public void refreshWidget(final String textToDisplay) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (StringUtils.isEmpty(textToDisplay)) {
                    setVisible(false);
                } else {
                    setVisible(true);
                    setText(textToDisplay);
                }
            }
        });
    }

    private static final double div3600d = 1d / 3600d;
    private static final double div60d = 1d / 60d;

    public static String formatTime(double timeInSeconds) {
        int hours = (int) (timeInSeconds * div3600d);
        int remainder = (int) timeInSeconds % 3600;
        int minutes = (int) (remainder * div60d);
        int seconds = remainder % 60;
        if (hours > 0)
            return String.format(LocaleUtil.getCurrent(), "%02d:%02d:%02d",
                    hours, minutes, seconds);
        else
            return String.format(LocaleUtil.getCurrent(), "%02d:%02d",
                    minutes, seconds);
    }

    public void stop() {
        setLayoutVisible(false);
    }

    public void updateWidget() {
        int textSize = _prefs.get("rab_bloodhound_large_textwidget",
                false) ? isTablet() ? 16 : 10 : isTablet() ? 6 : 3;
        _format = MapView.getTextFormat(Typeface.DEFAULT_BOLD, textSize);
        this.setTextFormat(_format);
        this.setVisible(_prefs.get(
                "rab_bloodhound_display_textwidget", true));
    }

    public boolean isTablet() {
        return _mapView.getContext().getResources()
                .getBoolean(com.atakmap.app.R.bool.isTablet);
    }

    public void setLayoutVisible(boolean visible) {
        this.layoutWidget.setVisible(visible);
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        if (_bloodhoundTool != null)
            _bloodhoundTool.dismissTimer();
    }
}
