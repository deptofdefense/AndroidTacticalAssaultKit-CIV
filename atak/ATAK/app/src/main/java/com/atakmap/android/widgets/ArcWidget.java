
package com.atakmap.android.widgets;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ArcWidget extends ShapeWidget {

    public interface OnOffsetAngleChangedListener {
        void onOffsetAngleChanged(ArcWidget arc);
    }

    public void addOnOffsetAngleChangedListener(
            OnOffsetAngleChangedListener l) {
        _onOffsetAngleChanged.add(l);
    }

    public void removeOnOffsetAngleChangedListener(
            OnOffsetAngleChangedListener l) {
        _onOffsetAngleChanged.remove(l);
    }

    public interface OnCentralAngleChangedListener {
        void onCentralAngleChanged(ArcWidget arc);
    }

    public void addOnCentralAngleChangedListener(
            OnCentralAngleChangedListener l) {
        _onCentralAngleChanged.add(l);
    }

    public void removeOnCentralAngleChangedListener(
            OnCentralAngleChangedListener l) {
        _onCentralAngleChanged.remove(l);
    }

    public interface OnRadiusChangedListener {
        void onRadiusChanged(ArcWidget arc);
    }

    public void addOnRadiusChangedListener(OnRadiusChangedListener l) {
        _onRadiusChanged.add(l);
    }

    public void removeOnRadiusChangedListener(OnRadiusChangedListener l) {
        _onRadiusChanged.remove(l);
    }

    public void setRadius(float radius) {
        if (_radius != radius) {
            _radius = radius;
            onRadiusChanged();
        }
    }

    public float getRadius() {
        return _radius;
    }

    /**
     * Set the angle that determines the arch length
     * 
     * @param centralAngle angle in degrees [0, 360]
     */
    public void setCentralAngle(float centralAngle) {
        if (_centralAngle != centralAngle) {
            _centralAngle = centralAngle;
            onCentralAngleChanged();
        }
    }

    public float getCentralAngle() {
        return _centralAngle;
    }

    /**
     * @param offsetAngle the offset to the arc widget.
     */
    public void setOffsetAngle(float offsetAngle) {
        if (_offsetAngle != offsetAngle) {
            _offsetAngle = offsetAngle;
            onOffsetAngleChanged();
        }
    }

    public float getOffsetAngle() {
        return _offsetAngle;
    }

    protected void onRadiusChanged() {
        for (OnRadiusChangedListener l : _onRadiusChanged) {
            l.onRadiusChanged(this);
        }
    }

    protected void onOffsetAngleChanged() {
        for (OnOffsetAngleChangedListener l : _onOffsetAngleChanged) {
            l.onOffsetAngleChanged(this);
        }
    }

    protected void onCentralAngleChanged() {
        for (OnCentralAngleChangedListener l : _onCentralAngleChanged) {
            l.onCentralAngleChanged(this);
        }
    }

    private final ConcurrentLinkedQueue<OnOffsetAngleChangedListener> _onOffsetAngleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnCentralAngleChangedListener> _onCentralAngleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnRadiusChangedListener> _onRadiusChanged = new ConcurrentLinkedQueue<>();
    private float _radius;
    private float _offsetAngle;
    private float _centralAngle;
}
