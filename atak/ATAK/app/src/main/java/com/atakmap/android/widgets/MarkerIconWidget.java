
package com.atakmap.android.widgets;

import android.graphics.Rect;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.assets.Icon;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.api.widgets.IMarkerIconWidget;

public class MarkerIconWidget extends MapWidget2 implements IMarkerIconWidget {

    private int _state;
    private final ConcurrentLinkedQueue<IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener> _onStateChanged = new ConcurrentLinkedQueue<>();
    private final Map<MarkerIconWidget.OnMarkerWidgetIconStateChangedListener, IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener> _onStateChangedForwarders = new IdentityHashMap<>();

    private Icon _icon;
    private final ConcurrentLinkedQueue<IMarkerIconWidget.OnMarkerWidgetIconChangedListener> _onIconChanged = new ConcurrentLinkedQueue<>();
    private final Map<MarkerIconWidget.OnMarkerWidgetIconChangedListener, IMarkerIconWidget.OnMarkerWidgetIconChangedListener> _onIconChangedForwarders = new IdentityHashMap<>();

    private float _rotation;
    private final ConcurrentLinkedQueue<IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener> _onRotationChanged = new ConcurrentLinkedQueue<>();
    private final Map<MarkerIconWidget.OnMarkerWidgetIconRotationChangedListener, IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener> _onRotationChangedForwarders = new IdentityHashMap<>();

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

    @Override
    public final void addOnMarkerWidgetIconStateChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener l) {
        _onStateChanged.add(l);
    }

    public void addOnMarkerWidgetIconStateChangedListener(
            MarkerIconWidget.OnMarkerWidgetIconStateChangedListener l) {
        registerForwardedListener(_onStateChanged, _onStateChangedForwarders, l,
                new StateChangedForwarder(l));
    }

    @Override
    public void removeOnMarkerWidgetIconStateChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener l) {
        _onStateChanged.remove(l);
    }

    public void removeOnMarkerWidgetIconStateChangedListener(
            MarkerIconWidget.OnMarkerWidgetIconStateChangedListener l) {
        unregisterForwardedListener(_onStateChanged, _onStateChangedForwarders,
                l);
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

    @Override
    public final IIcon getWidgetIcon() {
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

    @Override
    public final void addOnMarkerWidgetIconChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconChangedListener l) {
        _onIconChanged.add(l);
    }

    public void addOnMarkerWidgetIconChangedListener(
            MarkerIconWidget.OnMarkerWidgetIconChangedListener l) {
        registerForwardedListener(_onIconChanged, _onIconChangedForwarders, l,
                new IconChangedForwarder(l));
    }

    @Override
    public final void removeOnMarkerWidgetIconChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconChangedListener l) {
        _onIconChanged.remove(l);
    }

    public void removeOnMarkerWidgetIconChangedListener(
            MarkerIconWidget.OnMarkerWidgetIconChangedListener l) {
        unregisterForwardedListener(_onIconChanged, _onIconChangedForwarders,
                l);
    }

    @Override
    public final void addOnMarkerWidgetIconRotationChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener l) {
        _onRotationChanged.add(l);
    }

    public void addOnMarkerWidgetIconRotationChangedListener(
            MarkerIconWidget.OnMarkerWidgetIconRotationChangedListener l) {
        registerForwardedListener(_onRotationChanged,
                _onRotationChangedForwarders, l,
                new RotationChangedForwarder(l));
    }

    @Override
    public final void removeOnMarkerWidgetIconRotationChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener l) {
        _onRotationChanged.remove(l);
    }

    public void removeOnMarkerWidgetIconRotationChangedListener(
            MarkerIconWidget.OnMarkerWidgetIconRotationChangedListener l) {
        unregisterForwardedListener(_onRotationChanged,
                _onRotationChangedForwarders, l);
    }

    private void onIconChanged() {
        for (IMarkerIconWidget.OnMarkerWidgetIconChangedListener l : _onIconChanged) {
            l.onMarkerWidgetIconChanged(this);
        }
    }

    private void onStateChanged() {
        for (IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener l : _onStateChanged) {
            l.onMarkerWidgetStateChanged(this);
        }
    }

    private void onRotationChanged() {
        for (IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener l : _onRotationChanged) {
            l.onMarkerWidgetIconRotationChanged(this);
        }
    }

    private final static class IconChangedForwarder
            implements IMarkerIconWidget.OnMarkerWidgetIconChangedListener {
        final MarkerIconWidget.OnMarkerWidgetIconChangedListener _cb;

        IconChangedForwarder(
                MarkerIconWidget.OnMarkerWidgetIconChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onMarkerWidgetIconChanged(IMarkerIconWidget widget) {
            if (widget instanceof MarkerIconWidget)
                _cb.onMarkerWidgetIconChanged((MarkerIconWidget) widget);
        }
    }

    private final static class StateChangedForwarder implements
            IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener {
        final MarkerIconWidget.OnMarkerWidgetIconStateChangedListener _cb;

        StateChangedForwarder(
                MarkerIconWidget.OnMarkerWidgetIconStateChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onMarkerWidgetStateChanged(IMarkerIconWidget widget) {
            if (widget instanceof MarkerIconWidget)
                _cb.onMarkerWidgetStateChanged((MarkerIconWidget) widget);
        }
    }

    private final static class RotationChangedForwarder implements
            IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener {
        final MarkerIconWidget.OnMarkerWidgetIconRotationChangedListener _cb;

        RotationChangedForwarder(
                MarkerIconWidget.OnMarkerWidgetIconRotationChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onMarkerWidgetIconRotationChanged(
                IMarkerIconWidget widget) {
            if (widget instanceof MarkerIconWidget)
                _cb.onMarkerWidgetIconRotationChanged(
                        (MarkerIconWidget) widget);
        }
    }
}
