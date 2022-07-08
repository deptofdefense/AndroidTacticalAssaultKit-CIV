
package com.atakmap.android.attachment.layer;

import android.content.SharedPreferences;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.control.ClampToGroundControl;
import gov.tak.api.util.Disposable;
import com.atakmap.util.Visitor;

public class AttachmentBillboardLayer extends AbstractLayer implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        ClampToGroundControl, Disposable {

    private final MapView _mapView;
    private final AtakPreferences _prefs;
    private GLAttachmentBillboardLayer _glSubject;
    private boolean _nadirClamp;

    /**
     * Construct an attachment layer that shows up as a bilboard when in driving mode
     * @param view the mapview to use
     */
    public AttachmentBillboardLayer(MapView view) {
        super("Attachment Preview");
        _mapView = view;
        _prefs = new AtakPreferences(view);
        _prefs.registerListener(this);

        // Initialize nadir clamp value
        _mapView.getRenderer3().visitControl(null,
                new Visitor<ClampToGroundControl>() {
                    @Override
                    public void visit(ClampToGroundControl ctrl) {
                        _nadirClamp = ctrl.getClampToGroundAtNadir();
                    }
                }, ClampToGroundControl.class);

        _mapView.getRenderer3().registerControl(this, this);
        setVisible(false);
    }

    /**
     * Dispose of the layer.
     */
    @Override
    public void dispose() {
        _mapView.getRenderer3().unregisterControl(this, this);
        _prefs.unregisterListener(this);
    }

    void setGLSubject(GLAttachmentBillboardLayer layer) {
        _glSubject = layer;
        onSharedPreferenceChanged(null, "route_billboard_distance_m");
        onSharedPreferenceChanged(null, "relativeOverlaysScalingRadioList");
    }

    /**
     * Set the distance from the self marker billboards should be rendered
     * @param distanceMeters Distance in meters (NaN to always render)
     */
    public void setSelfMarkerDistance(double distanceMeters) {
        if (_glSubject != null)
            _glSubject.setSelfMarkerDistance(distanceMeters);
    }

    private void setRelativeScaling(float scale) {
        if (_glSubject != null)
            _glSubject.setRelativeScaling(scale);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (_glSubject == null)
            return;

        if (key == null)
            return;

        if (key.equals("route_billboard_distance_m"))
            setSelfMarkerDistance(_prefs.get(key, 500));
        else if (key.equals("relativeOverlaysScalingRadioList")) {
            setRelativeScaling((float) _prefs.get(key, 1.0f));
        }
    }

    @Override
    public void setClampToGroundAtNadir(boolean v) {
        _nadirClamp = v;
    }

    @Override
    public boolean getClampToGroundAtNadir() {
        return _nadirClamp;
    }
}
