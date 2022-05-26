package com.atakmap.map.layer.model;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.Matrix;

public interface SceneObjectControl extends MapControl {
    public static interface OnBoundsChangedListener {
        /**
         * Returns the updated bounds of the scene, in the scene's LCS.
         *
         * @param aabb          The bounds of the scene, in LCS
         * @param minDisplayRes The minimum display resolution for the scene
         * @param maxDisplayRes The maximum display resolution for the scene
         */
        public void onBoundsChanged(Envelope aabb, double minDisplayRes, double maxDisplayRes);
    }

    public boolean isModifyAllowed();
    public void setLocation(GeoPoint location);
    public void setLocalFrame(Matrix localFrame);
    public void setSRID(int srid);
    public void setAltitudeMode(ModelInfo.AltitudeMode mode);
    public GeoPoint getLocation();
    public int getSRID();
    public Matrix getLocalFrame();
    public ModelInfo.AltitudeMode getAltitudeMode();
    public void addOnSceneBoundsChangedListener(OnBoundsChangedListener l);
    public void removeOnSceneBoundsChangedListener(OnBoundsChangedListener l);
}
