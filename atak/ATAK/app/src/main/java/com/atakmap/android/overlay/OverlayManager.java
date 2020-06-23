
package com.atakmap.android.overlay;

import android.content.Context;
import android.content.Intent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Maintians a local 'cache' of overlay objects registered to the 'aquired' OverlayService.
 * 
 * 
 */
public class OverlayManager {

    public static final String TAG = "OverlayManager";

    public interface OnServiceListener {
        void onOverlayManagerBind(OverlayManager manager);

        void onOverlayManagerUnbind(OverlayManager manager);
    }

    public interface OnCacheListener {
        void onOverlayCached(OverlayManager manager, Overlay overlay);

        void onOverlayUncached(OverlayManager manager, Overlay overlay);
    }

    public static boolean aquireService(Context context,
            Intent customBindIntent,
            final OnServiceListener listener) {

        final OverlayManager manager = new OverlayManager();

        // initialize everything
        manager._mgr = OverlayService.getInstance();
        manager._startListening();
        manager._initializeCache();

        // notify client code
        if (listener != null) {
            listener.onOverlayManagerBind(manager);
        }
        return true;
    }

    public void releaseService() {
    }

    public Overlay registerOverlay(String overlayId) {
        Overlay overlay = _ensureIsCached(overlayId);
        _mgr.registerOverlay(overlayId);
        return overlay;
    }

    public Overlay[] getOverlays() {
        return _cache.values().toArray(new Overlay[0]);
    }

    public void addOnCacheListener(OnCacheListener l) {
        _cacheListeners.add(l);
    }

    public void removeOnCacheListener(OnCacheListener l) {
        _cacheListeners.remove(l);
    }

    protected void onOverlayCached(Overlay overlay) {
        for (OnCacheListener l : _cacheListeners) {
            l.onOverlayCached(this, overlay);
        }
    }

    protected void onOverlayRemoved(Overlay overlay) {
        for (OnCacheListener l : _cacheListeners) {
            l.onOverlayCached(this, overlay);
        }
    }

    private void _startListening() {
        _listener = new OverlayListener() {
            @Override
            public void onOverlayRegistered(String overlayId) {
                _ensureIsCached(overlayId);
            }

            @Override
            public void onOverlayUnregistered(String overlayId) {
                _removeFromCache(overlayId);
            }

            @Override
            public void onOverlayBooleanChanged(String overlayId,
                    String property, boolean value) {
                Overlay overlay = _fetchFromCache(overlayId);
                if (overlay != null) {
                    if (property.equals("visible")) {
                        overlay.setVisible(value);
                    }
                }
            }

            @Override
            public void onOverlayIntChanged(String overlayId, String property,
                    int value) {
                Overlay overlay = _fetchFromCache(overlayId);
                if (overlay != null) {
                    if (property.equals("unitCount")) {
                        overlay.setUnitCount(value);
                    }
                }
            }

            @Override
            public void onOverlayStringChanged(String overlayId,
                    String property, String value) {
                Overlay overlay = _fetchFromCache(overlayId);
                if (overlay != null) {
                    if (property.equals("iconUri")) {
                        overlay.setIconUri(value);
                    } else if (property.equals("friendlyName")) {
                        overlay.setFriendlyName(value);
                    }
                }
            }
        };
        _mgr.startConsuming(_listener);
    }

    private void _initializeCache() {
        String[] ids = _mgr.getRegisteredIds();
        for (String id : ids) {
            _ensureIsCached(id);
        }
    }

    private void _emptyCache() {
        Iterator<Overlay> iter = _cache.values().iterator();
        while (iter.hasNext()) {
            onOverlayRemoved(iter.next());
            iter.remove();
        }
    }

    private Overlay _ensureIsCached(String overlayId) {
        Overlay overlay = _cache.get(overlayId);
        if (overlay == null) {
            overlay = new Overlay(_mgr, overlayId);
            _cache.put(overlayId, overlay);
            onOverlayCached(overlay);
        }
        return overlay;
    }

    private Overlay _removeFromCache(String overlayId) {
        final Overlay retval = _cache.remove(overlayId);
        if (retval != null)
            this.onOverlayRemoved(retval);
        return retval;
    }

    private Overlay _fetchFromCache(String overlayId) {
        return _cache.get(overlayId);
    }

    private OverlayListener _listener;
    private final HashMap<String, Overlay> _cache = new HashMap<>();
    private final ConcurrentLinkedQueue<OnCacheListener> _cacheListeners = new ConcurrentLinkedQueue<>();
    private OverlayService _mgr = OverlayService.getInstance();
}
