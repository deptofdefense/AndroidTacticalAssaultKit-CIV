
package com.atakmap.android.attachment.layer;

import android.content.SharedPreferences;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.map.layer.AbstractLayer;

public class AttachmentBillboardLayer extends AbstractLayer implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final AtakPreferences _prefs;
    private GLAttachmentBillboardLayer _glSubject;

    public AttachmentBillboardLayer(MapView view) {
        super("Attachment Preview");
        _prefs = new AtakPreferences(view);
        _prefs.registerListener(this);
        setVisible(false);
    }

    public void dispose() {
        _prefs.unregisterListener(this);
    }

    void setGLSubject(GLAttachmentBillboardLayer layer) {
        _glSubject = layer;
        setSelfMarkerDistance(500);
    }

    /**
     * Set the distance from the self marker billboards should be rendered
     * @param distanceMeters Distance in meters (NaN to always render)
     */
    public void setSelfMarkerDistance(double distanceMeters) {
        if (_glSubject != null)
            _glSubject.setSelfMarkerDistance(distanceMeters);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key.equals("route_billboard_distance_m"))
            setSelfMarkerDistance(_prefs.get(key, Double.NaN));
    }
}
