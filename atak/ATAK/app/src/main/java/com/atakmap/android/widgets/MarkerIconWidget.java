
package com.atakmap.android.widgets;

import android.graphics.Rect;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.assets.Icon;

import java.util.concurrent.ConcurrentLinkedQueue;

public class MarkerIconWidget extends MapWidget2 {

    private int _state;
    private final ConcurrentLinkedQueue<OnMarkerWidgetIconStateChangedListener> _onStateChanged = new ConcurrentLinkedQueue<>();

    private Icon _icon;
    private final ConcurrentLinkedQueue<OnMarkerWidgetIconChangedListener> _onIconChanged = new ConcurrentLinkedQueue<>();

    private float _rotation;
    private final ConcurrentLinkedQueue<OnMarkerWidgetIconRotationChangedListener> _onRotationChanged = new ConcurrentLinkedQueue<>();

    private final Rect _hitBounds = new Rect(0, 0, 0, 0);

    public interface OnMarkerWidgetIconChangedListener {
        void onMarkerWidgetIconChanged(MarkerIconWidget widget);
    }

    public interface OnMarkerWidgetIconStateChangedListener {
        void onMarkerWidgetStateChanged(MarkerIconWidget widget);
    }

    public interface OnMarkerWidgetIconRotationChangedListener {
        void onMarkerWidgetIconRotationChanged(MarkerIconWidget widget);
    }

    public void setState(int state) {
        if (_state != state) {
            _state = state;
            onStateChanged();
        }
    }

    @Override
    public boolean isEnterable() {
        return false;
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        MapWidget hit = null;
        if (isVisible() && _icon != null &&
                _hitBounds.contains((int) x, (int) y))
            hit = this;
        return hit;
    }

    public int getState() {
        return _state;
    }

    public void addOnMarkerWidgetIconStateChangedListener(
            OnMarkerWidgetIconStateChangedListener l) {
        _onStateChanged.add(l);
    }

    public void removeOnMarkerWidgetIconStateChangedListener(
            OnMarkerWidgetIconStateChangedListener l) {
        _onStateChanged.remove(l);
    }

    /**
     * Set the hitBounds property.
     * 
     * @param left offset left of MapIcon anchor
     * @param top offset above MapIcon anchor
     * @param right offset right of MapIcon anchor
     * @param bottom offset below MapIcon anchor
     */
    public void setMarkerHitBounds(int left, int top, int right, int bottom) {
        _hitBounds.left = Math.round(left * MapView.DENSITY);
        _hitBounds.right = Math.round(right * MapView.DENSITY);
        _hitBounds.top = Math.round(top * MapView.DENSITY);
        _hitBounds.bottom = Math.round(bottom * MapView.DENSITY);
        if (_icon != null)
            setSize(_icon.getWidth() * MapView.DENSITY,
                    _icon.getHeight() * MapView.DENSITY);
    }

    /**
     * Set the hitBounds property.
     * 
     * @param hitBounds offset values from MapIcon anchor
     */
    public void setMarkerHitBounds(Rect hitBounds) {
        setMarkerHitBounds(hitBounds.left, hitBounds.top, hitBounds.right,
                hitBounds.bottom);
    }

    /**
     * Get the hitBounds property
     * 
     * @return offset values from MapIcon anchor
     */
    public Rect getMarkerHitBounds() {
        return getMarkerHitBounds(null);
    }

    /**
     * Get the hitBounds property
     * 
     * @param out the Rect to use (may be null)
     * @return offset values from MapIcon anchor
     */
    public Rect getMarkerHitBounds(Rect out) {
        if (out == null) {
            out = new Rect();
        }
        out.set(_hitBounds);
        // TODO: convert back to dp?
        return out;
    }

    /**
     * Sets the icon for the MarkerIconWidget
     * @param icon a valid icon
     */
    public void setIcon(Icon icon) {
        if (_icon != icon) {
            _icon = icon;
            setMarkerHitBounds(-_icon.getAnchorX(), -_icon.getAnchorY(),
                    _icon.getWidth() - _icon.getAnchorX(), _icon.getHeight()
                            - _icon.getAnchorY());
            onIconChanged();
        }
    }

    /**
     * Retrieve the icon for the MarkerIconWidget
     * @return the icon if set, otherwise null
     */
    public Icon getIcon() {
        return _icon;
    }

    /**
     * Set the size of the icon in pixels
     * @param width Width in pixels
     * @param height Height in pixels
     */
    public void setIconSizePx(float width, float height) {
        setIcon(getIcon().buildUpon().setSize((int) (width / MapView.DENSITY),
                (int) (height / MapView.DENSITY)).build());
    }

    @Override
    public void orientationChanged() {
        super.orientationChanged();

        if (_icon != null) {
            setMarkerHitBounds(-_icon.getAnchorX(),
                    -_icon.getAnchorY(),
                    _icon.getWidth() - _icon.getAnchorX(),
                    _icon.getHeight() - _icon.getAnchorY());
        }
    }

    public void setRotation(float rotation) {
        if (Float.compare(rotation, _rotation) != 0) {
            _rotation = rotation;
            onRotationChanged();
        }
    }

    public float getRotation() {
        return _rotation;
    }

    public void addOnMarkerWidgetIconChangedListener(
            OnMarkerWidgetIconChangedListener l) {
        _onIconChanged.add(l);
    }

    public void removeOnMarkerWidgetIconChangedListener(
            OnMarkerWidgetIconChangedListener l) {
        _onIconChanged.remove(l);
    }

    public void addOnMarkerWidgetIconRotationChangedListener(
            OnMarkerWidgetIconRotationChangedListener l) {
        _onRotationChanged.add(l);
    }

    public void removeOnMarkerWidgetIconRotationChangedListener(
            OnMarkerWidgetIconRotationChangedListener l) {
        _onRotationChanged.remove(l);
    }

    private void onIconChanged() {
        for (OnMarkerWidgetIconChangedListener l : _onIconChanged) {
            l.onMarkerWidgetIconChanged(this);
        }
    }

    private void onStateChanged() {
        for (OnMarkerWidgetIconStateChangedListener l : _onStateChanged) {
            l.onMarkerWidgetStateChanged(this);
        }
    }

    private void onRotationChanged() {
        for (OnMarkerWidgetIconRotationChangedListener l : _onRotationChanged) {
            l.onMarkerWidgetIconRotationChanged(this);
        }
    }

}
