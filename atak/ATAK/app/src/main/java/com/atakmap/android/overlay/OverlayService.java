
package com.atakmap.android.overlay;

import android.os.Bundle;

import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;

public class OverlayService {

    public static final String TAG = "OverlayService";

    private long _nextConsumerId;
    private final HashMap<String, Bundle> _overlays = new HashMap<>();
    private final TreeMap<Long, OverlayListener> _consumers = new TreeMap<>();

    private static OverlayService _instance;

    private OverlayService() {
    }

    static synchronized public OverlayService getInstance() {
        if (_instance == null)
            _instance = new OverlayService();
        return _instance;
    }

    public int getOverlayCount() {
        return _overlays.size();
    }

    public String[] getRegisteredIds() {
        return _overlays.keySet().toArray(new String[0]);
    }

    public void stopConsuming(long consumerId) {
        _consumers.remove(consumerId);
    }

    public void unregisterOverlay(String overlayId) {
        if (_overlays.remove(overlayId) != null) {
            Collection<OverlayListener> cs = _consumers.values();

            for (OverlayListener c : cs) {
                c.onOverlayUnregistered(overlayId);
            }
        }
    }

    public void registerOverlay(String overlayId) {

        // unregisterOverlay(overlayId);
        if (!_overlays.containsKey(overlayId)) {
            _overlays.put(overlayId, new Bundle());

            Collection<OverlayListener> cs = _consumers.values();

            for (OverlayListener c : cs) {
                c.onOverlayRegistered(overlayId);
            }
        }
    }

    public long startConsuming(OverlayListener listener) {
        _consumers.put(_nextConsumerId, listener);
        return _nextConsumerId++;
    }

    public boolean getBooleanProperty(String overlayId, String property,
            boolean fallback) {
        Bundle overlay = _overlays.get(overlayId);
        if (overlay != null) {
            return overlay.getBoolean(property, fallback);
        }
        return fallback;
    }

    public void setBooleanProperty(String overlayId, String property,
            boolean value) {
        Bundle overlay = _overlays.get(overlayId);
        if (overlay != null) {
            if (overlay.containsKey(property)
                    && overlay.getBoolean(property) == value) {
                // fix for when value == false and there is no mapping. JSS
            } else {
                overlay.putBoolean(property, value);
                Collection<OverlayListener> cs = _consumers.values();

                for (OverlayListener c : cs) {
                    c.onOverlayBooleanChanged(overlayId, property,
                            value);
                }
            }
        }
    }

    public int getIntProperty(String overlayId, String property,
            int fallback) {
        Bundle overlay = _overlays.get(overlayId);
        if (overlay != null) {
            return overlay.getInt(property, fallback);
        }
        return fallback;
    }

    public void setIntProperty(String overlayId, String property, int value) {
        Bundle overlay = _overlays.get(overlayId);
        if (overlay != null && overlay.getInt(property) != value) {
            overlay.putInt(property, value);
            Collection<OverlayListener> cs = _consumers.values();

            for (OverlayListener c : cs) {
                c.onOverlayIntChanged(overlayId, property, value);
            }
        }
    }

    public String getStringProperty(String overlayId, String property,
            String fallback) {
        Bundle overlay = _overlays.get(overlayId);
        if (overlay != null && overlay.containsKey(property)) {
            return overlay.getString(property);
        }
        return fallback;
    }

    public void setStringProperty(String overlayId, String property,
            String value) {
        Bundle overlay = _overlays.get(overlayId);
        if (overlay != null) {
            String currValue = overlay.getString(property);

            if (currValue == null || !currValue.equals(value)) {

                overlay.putString(property, value);
                Collection<OverlayListener> cs = _consumers.values();

                for (OverlayListener c : cs) {
                    c.onOverlayStringChanged(overlayId, property, value);
                }
            }
        }
    }

}
