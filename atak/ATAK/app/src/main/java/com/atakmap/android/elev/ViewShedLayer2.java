
package com.atakmap.android.elev;

import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.HashSet;
import java.util.Set;

public class ViewShedLayer2 extends HeatMapOverlay {

    public interface OnPointOfInterestChangedListener {
        void onPointOfInterestChanged(ViewShedLayer2 layer);
    }

    public interface OnRadiusChangedListener {
        void onRadiusChanged(ViewShedLayer2 layer);
    }

    public interface OnOpacityChangedListener {
        void onOpacityChanged(ViewShedLayer2 layer);
    }

    private static final int maxSampleRate = 501;
    private static final int minSampleRate = 15;
    private static final int maxSampleDensity = 5;
    private GeoPoint pointOfInterest;
    private double radius;
    private boolean circle = false;
    private int opacity = 50;
    private final Set<OnPointOfInterestChangedListener> pointOfInterestChangedListeners;
    private final Set<OnRadiusChangedListener> radiusChangedListeners;
    private final Set<OnOpacityChangedListener> opacityChangedListeners;

    public ViewShedLayer2() {
        this.pointOfInterest = null;
        this.radius = Double.NaN;
        this.pointOfInterestChangedListeners = new HashSet<>();
        this.radiusChangedListeners = new HashSet<>();
        this.opacityChangedListeners = new HashSet<>();
    }

    public synchronized void setPointOfInterest(GeoPoint value) {
        this.pointOfInterest = value;
        this.dispatchPointOfInterestChangedNoSync();
    }

    public void setCircle(boolean circle) {
        this.circle = circle;
    }

    public synchronized void setRadius(double value) {
        this.radius = value;
        //make sure that the area isnt being sampled in a density higher than needed
        if ((radius * 2) / maxSampleRate < maxSampleDensity) {
            int sampleRate = (int) Math.ceil((radius * 2) / maxSampleDensity);
            //make sure the resolution is odd so the center falls on the center vertices
            if (sampleRate % 2 == 0)
                sampleRate++;
            this.setResolution(sampleRate, sampleRate);
        } else {
            this.setResolution(maxSampleRate, maxSampleRate);
        }

        this.dispatchRadiusChangedNoSync();
    }

    public void setResolution(int res) {
        if (res > maxSampleRate)
            this.setResolution(maxSampleRate, maxSampleRate);
        else if (res < minSampleRate)
            this.setResolution(minSampleRate, minSampleRate);
        else
            this.setResolution(res, res);

    }

    public synchronized GeoPoint getPointOfInterest() {
        return this.pointOfInterest;
    }

    public synchronized double getRadius() {
        return this.radius;
    }

    public synchronized boolean getCircle() {
        return this.circle;
    }

    public void setOpacity(int opacity) {
        if (opacity > 0 && opacity < 100) {
            this.opacity = opacity;
        }
        this.dispatchOpacityChangedNoSync();
    }

    public int getOpacity() {
        return this.opacity;
    }

    public synchronized void addOnPointOfInterestChangedListener(
            OnPointOfInterestChangedListener l) {
        this.pointOfInterestChangedListeners.add(l);
    }

    public synchronized void removeOnPointOfInterestChangedListener(
            OnPointOfInterestChangedListener l) {
        this.pointOfInterestChangedListeners.remove(l);
    }

    private void dispatchPointOfInterestChangedNoSync() {
        for (OnPointOfInterestChangedListener l : this.pointOfInterestChangedListeners)
            l.onPointOfInterestChanged(this);
    }

    public synchronized void addOnRadiusChangedListener(
            OnRadiusChangedListener l) {
        this.radiusChangedListeners.add(l);
    }

    public synchronized void removeOnRadiusChangedListener(
            OnRadiusChangedListener l) {
        this.radiusChangedListeners.remove(l);
    }

    private void dispatchRadiusChangedNoSync() {
        for (OnRadiusChangedListener l : this.radiusChangedListeners)
            l.onRadiusChanged(this);
    }

    public synchronized void addOnOpacityChangedListener(
            OnOpacityChangedListener l) {
        this.opacityChangedListeners.add(l);
    }

    public synchronized void removeOnOpacityChangedListener(
            OnOpacityChangedListener l) {
        this.opacityChangedListeners.remove(l);
    }

    private void dispatchOpacityChangedNoSync() {
        for (OnOpacityChangedListener l : this.opacityChangedListeners)
            l.onOpacityChanged(this);
    }

    /**************************************************************************/
    // HeatMapOverlay

    @Override
    public String getName() {
        return "Elevation View Shed";
    }
}
