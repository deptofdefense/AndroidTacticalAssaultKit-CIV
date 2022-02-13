
package gov.tak.platform.widgets;

import android.graphics.Rect;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.platform.ui.MotionEvent;

import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.map.opengl.GLRenderGlobals;

import gov.tak.api.widgets.IMarkerIconWidget;

import java.util.concurrent.ConcurrentLinkedQueue;

public class MarkerIconWidget extends MapWidget implements IMarkerIconWidget  {

    private int _state;
    private final ConcurrentLinkedQueue<IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener> _onStateChanged = new ConcurrentLinkedQueue<>();

    private IIcon _icon;
    private final ConcurrentLinkedQueue<IMarkerIconWidget.OnMarkerWidgetIconChangedListener> _onIconChanged = new ConcurrentLinkedQueue<>();

    private float _rotation;
    private final ConcurrentLinkedQueue<IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener> _onRotationChanged = new ConcurrentLinkedQueue<>();

    private final Rect _hitBounds = new Rect(0, 0, 0, 0);

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
    public MapWidget seekWidgetHit(MotionEvent event, float x, float y) {
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
            IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener l) {
        _onStateChanged.add(l);
    }

    public void removeOnMarkerWidgetIconStateChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener l) {
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
        _hitBounds.left = Math.round(left * GLRenderGlobals.getRelativeScaling());
        _hitBounds.right = Math.round(right * GLRenderGlobals.getRelativeScaling());
        _hitBounds.top = Math.round(top * GLRenderGlobals.getRelativeScaling());
        _hitBounds.bottom = Math.round(bottom * GLRenderGlobals.getRelativeScaling());
        if (_icon != null)
            setSize(_icon.getWidth() * GLRenderGlobals.getRelativeScaling(),
                    _icon.getHeight() * GLRenderGlobals.getRelativeScaling());
    }

    /**
     * Set the size of the icon in pixels
     * @param width Width in pixels
     * @param height Height in pixels
     */
    public void setIconSizePx(float width, float height) {
        setIcon(new gov.tak.platform.commons.graphics.Icon.Builder(getWidgetIcon()).setSize((int) (width / GLRenderGlobals.getRelativeScaling()),
                (int) (height / GLRenderGlobals.getRelativeScaling())).build());
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
    public void setIcon(IIcon icon) {
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
    @Override
    public IIcon getWidgetIcon() {
        return _icon;
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
            IMarkerIconWidget.OnMarkerWidgetIconChangedListener l) {
        _onIconChanged.add(l);
    }

    public void removeOnMarkerWidgetIconChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconChangedListener l) {
        _onIconChanged.remove(l);
    }

    public void addOnMarkerWidgetIconRotationChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener l) {
        _onRotationChanged.add(l);
    }

    public void removeOnMarkerWidgetIconRotationChangedListener(
            IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener l) {
        _onRotationChanged.remove(l);
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

}
