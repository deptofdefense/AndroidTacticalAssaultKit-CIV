
package com.atakmap.android.offscreenindicators;

//import android.app.Service;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.offscreenindicators.graphics.GLOffscreenIndicators;
import com.atakmap.android.overlay.Overlay;
import com.atakmap.android.overlay.Overlay.OnVisibleChangedListener;
import com.atakmap.android.overlay.OverlayManager;
import com.atakmap.android.overlay.OverlayManager.OnServiceListener;
import com.atakmap.app.R;
import com.atakmap.map.layer.opengl.GLLayerFactory;

/**
 * Provides for offscreen indicator support on the map.  This is 
 * responsible for rendering halos around each icon when they are offscreen
 * and either meet a certain threshold distance, are in the same team 
 * (permanent) or new (transient time based).
 */
public class OffScreenIndicatorsMapComponent extends AbstractMapComponent {

    static final int HALO_BITMAP_WIDTH = 16;
    static final int HALO_BORDER_WIDTH = 48;
    private OffscreenIndicatorController _offscreenCtrl;
    private ObserverNode _rootObserver;
    private OverlayManager _overlayManager;
    private Overlay offScrIndOverlay;
    private boolean isRendering = false;
    private MapView mapView;

    @Override
    public void onCreate(final Context context, Intent intent, MapView view) {
        this.mapView = view;

        GLLayerFactory.register(GLOffscreenIndicators.SPI2);

        _offscreenCtrl = new OffscreenIndicatorController(view, context);
        _rootObserver = new ObserverNode(view.getRootGroup(), _offscreenCtrl);

        _rootObserver.reconsider();

        Intent sharedOverlayServiceIntent = new Intent();
        sharedOverlayServiceIntent
                .setAction("com.atakmap.android.overlay.SHARED");
        if (!OverlayManager.aquireService(context, sharedOverlayServiceIntent,
                _overlayServiceListener)) {

            // try again but embed locally
            OverlayManager
                    .aquireService(context, null, _overlayServiceListener);
        }
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (_overlayManager != null) {
            _overlayManager.releaseService();
        }

        if (_offscreenCtrl != null) {
            mapView.removeLayer(MapView.RenderStack.WIDGETS,
                    _offscreenCtrl);

            _offscreenCtrl.dispose();
            _offscreenCtrl = null;
        }

        if (_rootObserver != null) {
            _rootObserver.dispose();
            _rootObserver = null;
        }
    }

    private final OnServiceListener _overlayServiceListener = new OnServiceListener() {
        @Override
        public void onOverlayManagerBind(OverlayManager manager) {
            _overlayManager = manager;
            offScrIndOverlay = manager.registerOverlay("Off-Screen Indicators");
            offScrIndOverlay.setVisible(_offscreenCtrl.getEnabled());
            offScrIndOverlay
                    .setIconUri("android.resource://"
                            + mapView.getContext().getPackageName() + "/"
                            + R.drawable.ic_overlay_offscrind);
            _toggleOffScrIndOverlayListener
                    .onOverlayVisibleChanged(offScrIndOverlay);
            offScrIndOverlay
                    .addOnVisibleChangedListener(
                            _toggleOffScrIndOverlayListener);
        }

        @Override
        public void onOverlayManagerUnbind(OverlayManager manager) {
            _overlayManager = null;
            offScrIndOverlay = null;
        }
    };

    private final OnVisibleChangedListener _toggleOffScrIndOverlayListener = new OnVisibleChangedListener() {
        @Override
        public void onOverlayVisibleChanged(Overlay overlay) {
            final boolean visible = overlay.getVisible();
            if (visible) {
                if (!isRendering) {
                    mapView.addLayer(MapView.RenderStack.WIDGETS, 0,
                            _offscreenCtrl);
                    isRendering = true;
                }
            } else {
                if (isRendering) {
                    mapView.removeLayer(MapView.RenderStack.WIDGETS,
                            _offscreenCtrl);
                    isRendering = false;
                }
            }
        }
    };

}
