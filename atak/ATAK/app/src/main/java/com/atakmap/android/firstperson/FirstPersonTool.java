
package com.atakmap.android.firstperson;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.PointF;
import android.os.Bundle;

import androidx.core.graphics.ColorUtils;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.widgets.DrawableWidget;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.R;
import com.atakmap.app.preferences.CustomActionBarFragment;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapSceneModel;
import com.atakmap.math.MathUtils;

import gov.tak.api.engine.map.IMapRendererEnums;
import gov.tak.api.widgets.ILinearLayoutWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.ui.MotionEvent;

/**
 * Broadcast receiver that can handle First Person events via intent.
 */
final class FirstPersonTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final static double SCROLL_SPEED_X = 0.0625d;
    private final static double SCROLL_SPEED_Y = -0.125;

    private final MapView _mapView;
    private final AtakPreferences _prefs;
    private ILinearLayoutWidget _layoutV;
    private ILinearLayoutWidget _widget;

    GeoPoint _at;
    GeoPoint _lookFrom;
    double _gsd;
    double _azimuth;
    double _tilt;

    public static final String TOOL_NAME = "com.atakmap.android.firstperson.FirstPersonTool";

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public FirstPersonTool(MapView mapView) {
        super(mapView, TOOL_NAME);
        _mapView = mapView;
        _prefs = new AtakPreferences(mapView);
        ToolManagerBroadcastReceiver.getInstance().registerTool(TOOL_NAME,
                this);
        _prefs.registerListener(this);
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {
        _lookFrom = GeoPoint.parseGeoPoint(extras.getString("fromPoint"));

        // capture current state
        MapSceneModel sm = _mapView.getRenderer3().getMapSceneModel(false,
                IMapRendererEnums.DisplayOrigin.UpperLeft);
        _at = sm.mapProjection.inverse(sm.camera.target, null);
        _azimuth = sm.camera.azimuth;
        _tilt = sm.camera.elevation + 90d;
        _gsd = sm.gsd;

        // enter first person mode
        _mapView.getRenderer3().lookFrom(_lookFrom, _azimuth, -10,
                IMapRendererEnums.CameraCollision.Ignore, true);

        // push all the dispatch listeners
        _mapView.getMapEventDispatcher().pushListeners();
        // clear all listeners
        _mapView.getMapEventDispatcher().clearListeners();
        // register on scroll to swivel camera on scroll motions
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_SCROLL, this);
        // attach on other motions to mark as consumed
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_ROTATE, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_TILT, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_SCALE, this);

        if (_widget == null) {
            // look-at widget
            final Resources res = _mapView.getContext().getResources();
            float size = res.getDimensionPixelSize(R.dimen.button_small);
            float pd = res.getDimensionPixelSize(R.dimen.auto_margin);
            _widget = new LinearLayoutWidget();
            _widget.setNinePatchBG(true);
            _widget.setPadding(pd, pd, pd, pd);
            _widget.setVisible(false);
            DrawableWidget dr = new DrawableWidget(res.getDrawable(
                    R.drawable.nav_firstperson));
            dr.setSize(size, size);
            dr.setColor(_mapView.getResources().getColor(R.color.maize));
            dr.addOnClickListener(new IMapWidget.OnClickListener() {
                @Override
                public void onMapWidgetClick(IMapWidget widget,
                        MotionEvent event) {
                    endTool();
                }
            });
            _widget.addChildWidget(dr);

            RootLayoutWidget root = (RootLayoutWidget) _mapView
                    .getComponentExtra(
                            "rootLayoutWidget");
            LinearLayoutWidget tlLayout = root
                    .getLayout(RootLayoutWidget.TOP_LEFT);
            _layoutV = tlLayout.getOrCreateLayout("TL_V");
            _layoutV.addChildWidget(_widget);
        }

        _widget.setVisible(true);
        updateColors();

        return super.onToolBegin(extras);
    }

    @Override
    protected void onToolEnd() {
        _mapView.getMapEventDispatcher().popListeners();
        if (_widget != null)
            _widget.setVisible(false);
        _mapView.getRenderer3().lookAt(_at, _gsd, _azimuth, _tilt, true);
        super.onToolEnd();
    }

    @Override
    public void dispose() {
        _layoutV.removeChildWidget(_widget);
        _prefs.unregisterListener(this);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.MAP_SCROLL)) {
            final MapSceneModel sm = _mapView.getRenderer3().getMapSceneModel(
                    false, IMapRendererEnums.DisplayOrigin.UpperLeft);
            final PointF dxdy = event.getPointF();

            _mapView.getRenderer3().lookFrom(
                    _lookFrom,
                    sm.camera.azimuth + (dxdy.x * SCROLL_SPEED_X),
                    MathUtils.clamp(
                            sm.camera.elevation + (dxdy.y * SCROLL_SPEED_Y),
                            -90d, 89d),
                    IMapRendererEnums.CameraCollision.Ignore,
                    false);
        }
        // consume
        event.getExtras().putBoolean("eventNotHandled", false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;

        if (key.equals(CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY))
            updateColors();
    }

    private void updateColors() {
        int bgColor = ColorUtils.setAlphaComponent(NavView.getInstance()
                .getUserIconShadowColor(), 128);
        if (_widget != null)
            _widget.setBackingColor(bgColor);
    }
}
