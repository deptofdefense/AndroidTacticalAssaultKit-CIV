
package com.atakmap.android.maps.graphics.widgets;

import android.content.Context;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLMapItemFactory;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.WidgetsLayer;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;

import java.util.Timer;
import java.util.TimerTask;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.marshal.MarshalManager;

public class GLWidgetsMapComponent extends AbstractMapComponent implements
        AtakMapView.OnActionBarToggledListener {
    private MapView _mapView;
    private GLMapSurface _mapSurface;
    private GLMapView _orthoWorldMap;
    private LayoutWidget _rootLayout;
    private WidgetsLayer _widgetsLayer;

    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;

        _widgetsLayer = new WidgetsLayer("Map Widgets",
                new RootLayoutWidget(_mapView));
        _rootLayout = _widgetsLayer.getRoot();

        _touchListener = new WidgetTouchHandler(view, _rootLayout);

        _mapView.addOnMapViewResizedListener(_resizedListener);
        _mapView.addOnActionBarToggledListener(this);
        _mapView.addOnTouchListenerAt(0, _touchListener);

        GLWidgetFactory.registerSpi(GLScrollLayoutWidget.SPI);
        GLWidgetFactory.registerSpi(GLButtonWidget.SPI);
        GLWidgetFactory.registerSpi(GLMapMenuButtonWidget.SPI);
        GLWidgetFactory.registerSpi(GLArcWidget.SPI);
        GLWidgetFactory.registerSpi(GLIsoKeyWidget.SPI);
        GLMapItemFactory.registerSpi(GLFahArrowWidget.GLITEM_SPI);
        GLWidgetFactory.registerSpi(GLCenterBeadWidget.SPI);
        GLWidgetFactory.registerSpi(GLMarkerDrawableWidget.SPI);
        GLWidgetFactory.registerSpi(GLDrawableWidget.SPI);

        gov.tak.platform.widgets.opengl.GLWidgetFactory.registerSpi(
                gov.tak.platform.widgets.opengl.GLRadialButtonWidget.SPI);
        gov.tak.platform.widgets.opengl.GLWidgetFactory
                .registerSpi(gov.tak.platform.widgets.opengl.GLScaleWidget.SPI);
        GLWidgetFactory.registerSpi(GLMapFocusTextWidget.SPI);
        gov.tak.platform.widgets.opengl.GLWidgetFactory.registerSpi(
                gov.tak.platform.widgets.opengl.GLLinearLayoutWidget.SPI);
        gov.tak.platform.widgets.opengl.GLWidgetFactory.registerSpi(
                gov.tak.platform.widgets.opengl.GLLayoutWidget.SPI);
        gov.tak.platform.widgets.opengl.GLWidgetFactory
                .registerSpi(gov.tak.platform.widgets.opengl.GLTextWidget.SPI);
        gov.tak.platform.widgets.opengl.GLWidgetFactory.registerSpi(
                gov.tak.platform.widgets.opengl.GLMarkerIconWidget.SPI);
        gov.tak.platform.widgets.opengl.GLWidgetFactory.registerSpi(
                gov.tak.platform.widgets.opengl.GLCenterBeadWidget.SPI);

        _mapView.setComponentExtra("rootLayoutWidget", _rootLayout);

        _rootLayout.setSize(_mapView.getWidth(), _mapView.getHeight());

        _mapView.addLayer(MapView.RenderStack.WIDGETS, _widgetsLayer);

        _mapSurface = _mapView
                .findViewWithTag(GLMapSurface.LOOKUP_TAG);
        if (_mapSurface == null) {
            // register a callback to listen when it is set...
            view.setOnHierarchyChangeListener(
                    new ViewGroup.OnHierarchyChangeListener() {
                        @Override
                        public void onChildViewRemoved(final View parent,
                                final View child) {
                        }

                        @Override
                        public void onChildViewAdded(final View parent,
                                final View child) {
                            if (_mapSurface == null) {
                                if (parent == _mapView
                                        && child instanceof GLMapSurface) {
                                    _mapSurface = (GLMapSurface) child;
                                }
                            }
                        }
                    });
        } else {
            // _finishCreate();
        }
    }

    public LayoutWidget getRootLayout() {
        return _rootLayout;
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        // clean up any static references

        _mapView.removeLayer(MapView.RenderStack.WIDGETS, _widgetsLayer);

        _mapView.removeOnMapViewResizedListener(_resizedListener);
        _mapView.removeOnActionBarToggledListener(this);
        _mapView.removeOnTouchListener(_touchListener);
    }

    private final AtakMapView.OnMapViewResizedListener _resizedListener = new AtakMapView.OnMapViewResizedListener() {
        @Override
        public void onMapViewResized(AtakMapView view) {
            if (_rootLayout != null) {
                Log.d(TAG, "GLWidgetsMapComponent resized: " +
                        view.getWidth() + "x" + view.getHeight());

                _rootLayout.setSize(view.getWidth(),
                        view.getHeight());
            }
        }
    };

    private View.OnTouchListener _touchListener;

    public static final class WidgetTouchHandler implements
            View.OnTouchListener {

        public WidgetTouchHandler(MapView mapView, LayoutWidget rootLayout) {
            _mapView = mapView;
            _rootLayout = rootLayout;
        }

        @Override
        public boolean onTouch(final View v, final MotionEvent aEvent) {
            if (v instanceof MapView && ((MapView) v).getMapTouchController()
                    .isLongPressDragging())
                // Prevent widgets from interfering with the long-press drag event
                return false;
            final gov.tak.platform.ui.MotionEvent event = MarshalManager
                    .marshal(aEvent, android.view.MotionEvent.class,
                            gov.tak.platform.ui.MotionEvent.class);
            IMapWidget hit = _rootLayout.seekWidgetHit(event, event.getX(),
                    event.getY());
            if (hit == null && _pressedWidget != null
                    && event.getAction() == MotionEvent.ACTION_MOVE) {
                boolean used = _pressedWidget.onMove(event);
                // If this widget isn't handling move events then get rid of it
                if (!used) {
                    _pressedWidget.onUnpress(event);
                    _pressedWidget = null;
                    return false;
                } else {
                    return true;
                }
            }
            if (hit != _pressedWidget) {
                if (_pressedWidget != null) {
                    _pressedWidget.onUnpress(event);
                    _pressedWidget = null;
                }
            }
            if (hit != null) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        hit.onPress(event);
                        _pressedWidget = hit;

                        //start long press countdown
                        widTimer = new Timer("GLWidgetsMapComponent");
                        widTimer.schedule(widTask = new WidTimerTask(),
                                ViewConfiguration.getLongPressTimeout());

                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (_pressedWidget != hit
                                && hit.isEnterable()/* && hit != null */) {
                            hit.onPress(event);
                            _pressedWidget = hit;
                        } else {
                            hit.onMove(event);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        hit.onUnpress(event);
                        if (_pressedWidget == hit) {

                            if (widTask != null) {

                                widTask.cancel();
                                if (widTimer.purge() > 0) {
                                    //the long press task was canceled, so onClick
                                    hit.onClick(event);
                                } //otherwise, ignore
                            }
                        } else
                            return false; // TODO this is ugly
                        _pressedWidget = null;
                        break;
                }
            }
            return hit != null;
        }

        private final MapView _mapView;
        private final LayoutWidget _rootLayout;
        private IMapWidget _pressedWidget;
        private Timer widTimer;
        private WidTimerTask widTask;

        class WidTimerTask extends TimerTask {
            @Override
            public void run() {
                final IMapWidget mw = _pressedWidget;
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mw != null)
                            mw.onLongPress();
                    }
                });
            }
        }

    }

    /**
     * Note individual MapWidgets do not need to register for action bar events. They simply
     * need to implement AtakMapView.OnActionBarToggledListener amd they will be notified via
     * thier parent MapWidget/container
     * @param showing true when the action bar is showing.
     *
     */
    @Override
    public void onActionBarToggled(boolean showing) {
        _rootLayout.onActionBarToggled(showing);
    }

}
