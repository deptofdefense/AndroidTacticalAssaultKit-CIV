
package com.atakmap.android.mapcompass;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;

import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.CameraController;
import com.atakmap.math.MathUtils;

/**
 * Map widget that shows the current Tilt Lock of the Map in 3-D
 */
class TiltLockWidgetController implements
        AtakMapView.OnMapMovedListener, MapWidget.OnWidgetPointChangedListener,
        MapWidget2.OnWidgetSizeChangedListener, MapWidget.OnMoveListener,
        MapWidget.OnPressListener {

    private static final String TAG = "TiltLockWidgetController";

    // Slider states based on enabled preferences and modes
    private static final int SLIDER_HIDDEN = 0;
    private static final int SLIDER_TILT = 1;
    private static final int SLIDER_ROTATE = 2;

    private double lockedTilt;
    private boolean _tiltEnabled = false;

    private final SharedPreferences _prefs;
    private final MapView _mapView;
    private final CompassArrowWidget _compass;

    private final MarkerIconWidget _tiltWidget;
    private final MarkerIconWidget _pointerWidget;
    private final SliderWidget _sliderWidget;
    private boolean _enableSlider = true;
    private GeoPoint _mapFocus;

    TiltLockWidgetController(MapView view, CompassArrowWidget compass) {
        _mapView = view;
        _compass = compass;
        _prefs = PreferenceManager.getDefaultSharedPreferences(
                view.getContext());

        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");
        LayoutWidget layoutWidget = new LayoutWidget();
        layoutWidget.setName("TiltLockLayout");
        layoutWidget.setTouchable(false);
        root.addWidget(layoutWidget);

        _tiltWidget = new MarkerIconWidget();
        layoutWidget.addWidget(_tiltWidget);

        _pointerWidget = new MarkerIconWidget();
        layoutWidget.addWidget(_pointerWidget);

        // Tilt/rotate controls along the left edge
        _sliderWidget = new SliderWidget(_mapView);
        LinearLayoutWidget leftEdgeV = root.getLayout(
                RootLayoutWidget.LEFT_EDGE).getOrCreateLayout("LE_V");
        leftEdgeV.setGravity(Gravity.TOP);
        leftEdgeV.setLayoutParams(LinearLayoutWidget.WRAP_CONTENT,
                LinearLayoutWidget.MATCH_PARENT);
        leftEdgeV.addChildWidgetAt(0, _sliderWidget);
        _sliderWidget.addOnPressListener(this);
        _sliderWidget.addOnMoveListener(this);

        _mapView.addOnMapMovedListener(this);
        _compass.addOnWidgetSizeChangedListener(this);

        MapWidget w = _compass;
        while (w != null && w != root) {
            w.addOnWidgetPointChangedListener(this);
            w = w.getParent();
        }
        refresh();
    }

    public void setLocked(boolean locked) {
        _mapView.getMapTouchController().setTiltEnabledState(!locked
                ? MapTouchController.STATE_TILT_ENABLED
                : MapTouchController.STATE_MANUAL_TILT_DISABLED);

        refresh();
    }

    public void setVisible(boolean visible) {
        if (_tiltWidget.isVisible() != visible) {
            _tiltWidget.setVisible(visible);
            _pointerWidget.setVisible(visible);
            refresh();
        }
    }

    public boolean isVisible() {
        return _tiltWidget.isVisible();
    }

    public void setSliderVisible(boolean visible) {
        _enableSlider = visible;
    }

    /**
     * Current action the slider controls
     * ROTATION > TILT > NONE
     * @return Slider state
     */
    private int getSliderState() {
        if (!_prefs.getBoolean("dexControls", false) || !_enableSlider)
            return SLIDER_HIDDEN;
        MapTouchController cn = _mapView.getMapTouchController();
        if (cn.isUserOrientationEnabled())
            return SLIDER_ROTATE;
        else if (_prefs.getBoolean("status_3d_enabled", false)
                && cn.getTiltEnabledState() == MapTouchController.STATE_TILT_ENABLED
                || _mapView.getMapTouchController().isFreeForm3DEnabled())
            return SLIDER_TILT;
        return SLIDER_HIDDEN;
    }

    @Override
    public void onMapWidgetPress(MapWidget widget, MotionEvent event) {
        if (widget == _sliderWidget)
            _mapFocus = _mapView.inverseWithElevation(
                    _mapView.getMapController().getFocusX(),
                    _mapView.getMapController().getFocusY()).get();
    }

    @Override
    public boolean onMapWidgetMove(MapWidget widget, MotionEvent event) {
        if (widget == _sliderWidget) {
            int state = getSliderState();
            if (state == SLIDER_HIDDEN)
                return true;

            // The "progress" on the slider where the bottom is 0 and top is 1
            float v = event.getY() - (_sliderWidget.getAbsolutePosition().y
                    + _sliderWidget.getPadding()[MapWidget2.TOP]);
            v = MathUtils.clamp(1 - (v / _sliderWidget.getSliderMax()), 0, 1);

            // Tilt the map
            if (state == SLIDER_TILT) {
                double curTilt = _mapView.getMapTilt();
                double tilt = v * 90d;
                double maxTilt = _mapView.getMaxMapTilt(_mapView.getMapScale());
                tilt = Math.min(tilt, maxTilt);

                GeoPoint focus = _mapFocus;
                GeoPoint ffPoint = _mapView.getMapTouchController()
                        .getFreeForm3DPoint();
                if (ffPoint != null)
                    focus = ffPoint;

                if (focus == null) {
                    onMapWidgetPress(widget, event);
                } else {
                    // map focus cannot be null here
                    _mapView.getMapController().tiltBy(tilt - curTilt,
                            focus, false);
                }
            }

            // Rotate the map
            else if (state == SLIDER_ROTATE) {
                double rotation = v * 359d;
                _mapView.getMapController().rotateTo(rotation, true);
            }
        }
        return true;
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        final double maxTilt = _mapView.getMaxMapTilt(view.getMapScale());
        int tiltStatus = _mapView.getMapTouchController().getTiltEnabledState();
        if (tiltStatus == MapTouchController.STATE_MANUAL_TILT_DISABLED) {
            //Log.d(TAG, "attempt to correct tilt: " + lockedTilt + "/" + maxTilt);
            if (_mapView.getMapTilt() < maxTilt && lockedTilt >= maxTilt) {

                CameraController.Programmatic.tiltTo(_mapView.getRenderer3(),
                        maxTilt, true);
                return;
            }
        }
        double d = view.getMapTilt();
        _pointerWidget.setRotation((float) d * -1);

        int state = getSliderState();
        if (state == SLIDER_TILT)
            _sliderWidget.setSliderValue((float) (1 - (d / 90)));
        else if (state == SLIDER_ROTATE)
            _sliderWidget.setSliderValue(
                    (float) (1 - (_mapView.getMapRotation() / 359d)));
    }

    @Override
    public void onWidgetPointChanged(MapWidget widget) {
        refresh();
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 widget) {
        refresh();
    }

    /**
     * Keep widget situated to the top-right of the compass
     */
    public void refresh() {
        float tMargin = _tiltWidget.isVisible() ? 8f * MapView.DENSITY : 0f;
        float rMargin = _tiltWidget.isVisible() ? 8f * MapView.DENSITY : 0f;
        _compass.setMargins(0f, tMargin, rMargin, 0f);

        _sliderWidget.setVisible(getSliderState() != SLIDER_HIDDEN);

        if (!_compass.isVisible() || !_tiltWidget.isVisible())
            return;

        // Update tilt widget icon and position
        String imageUri = "android.resource://" + _mapView.getContext()
                .getPackageName() + "/" + R.drawable.tilt;
        Icon compassIcon = _compass.getIcon();
        int width = Math.round(compassIcon.getWidth() * (4.0f / 3.0f));
        int height = Math.round(compassIcon.getHeight() * (4.0f / 3.0f));

        Icon.Builder tiltIcon = new Icon.Builder();
        tiltIcon.setAnchor(0, 0);
        tiltIcon.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        tiltIcon.setImageUri(Icon.STATE_DEFAULT, imageUri);
        tiltIcon.setSize(width, height);
        _tiltWidget.setIcon(tiltIcon.build());

        PointF cPos = _compass.getAbsolutePosition();
        float[] cSize = _compass.getSize(true, false);
        float[] tSize = _tiltWidget.getSize(true, false);
        float left = cPos.x + ((cSize[0] - tSize[0]) / 2);
        float top = cPos.y + ((cSize[1] - tSize[1]) / 2);
        _tiltWidget.setPoint(left, top);

        // Update pointer icon and position
        imageUri = "android.resource://"
                + _mapView.getContext().getPackageName()
                + "/" + R.drawable.tilt_pointer;
        Icon.Builder pointer = new Icon.Builder();
        pointer.setAnchor(0, 0);
        pointer.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        pointer.setImageUri(Icon.STATE_DEFAULT, imageUri);
        pointer.setSize(width, height);
        _pointerWidget.setIcon(pointer.build());
        _pointerWidget.setPoint(left, top);

        onMapMoved(_mapView, false);
    }
}
