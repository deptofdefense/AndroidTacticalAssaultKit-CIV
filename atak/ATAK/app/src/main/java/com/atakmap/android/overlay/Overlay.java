
package com.atakmap.android.overlay;

import java.util.concurrent.ConcurrentLinkedQueue;

public class Overlay {

    public static final String TAG = "Overlay";

    private int _order = -1;
    private String _friendlyName;
    private Integer _unitCount;
    private String _iconUri;
    private Boolean _visible;
    private final OverlayService _manager;
    private final String _overlayId;
    private final ConcurrentLinkedQueue<OnVisibleChangedListener> _visibleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnIconResourceIdChangedListener> _iconResChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnUnitCountChangedListener> _unitCountChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnFriendlyNameChangedListener> _friendlyNameChanged = new ConcurrentLinkedQueue<>();

    public Overlay(OverlayService manager, String overlayId) {
        _overlayId = overlayId;
        _manager = manager;
    }

    public String getOverlayId() {
        return _overlayId;
    }

    public void setVisible(boolean visible) {
        _manager.setBooleanProperty(_overlayId, "visible", visible);
        if (_visible == null || _visible != visible) {
            _visible = visible;
            onVisibleChanged();
        }
    }

    public void setIconUri(String iconUri) {
        _manager.setStringProperty(_overlayId, "iconUri", iconUri);
        if (_iconUri == null || !(_iconUri.equals(iconUri))) {
            _iconUri = iconUri;
            onIconUriChanged();
        }
    }

    public void setFriendlyName(String friendlyName) {
        _manager.setStringProperty(_overlayId, "friendlyName", friendlyName);
        if (_friendlyName == null || !_friendlyName.equals(friendlyName)) {
            _friendlyName = friendlyName;
            onFriendlyNameChanged();
        }
    }

    public void setUnitCount(int unitCount) {
        _manager.setIntProperty(_overlayId, "unitCount", unitCount);
        if (_unitCount == null || _unitCount != unitCount) {
            _unitCount = unitCount;
            onUnitCountChanged();
        }
    }

    public void setOrder(String order) {
        try {
            _order = Integer.parseInt(order);
        } catch (NumberFormatException nfe) {
            // do nothing
        }

    }

    public void incrementUnitCount(int count) {
        int curr = getUnitCount();
        setUnitCount(curr + count);
    }

    public String getIconUri() {
        if (_iconUri == null) {
            _iconUri = _manager
                    .getStringProperty(_overlayId, "iconUri", "");
        }
        return _iconUri;
    }

    public boolean getVisible() {
        if (_visible == null) {
            _visible = _manager.getBooleanProperty(_overlayId, "visible",
                    true);
        }
        return (_visible == null ? false : _visible);
    }

    public String getFriendlyName() {
        if (_friendlyName == null) {
            _friendlyName = _manager.getStringProperty(_overlayId,
                    "friendlyName", _overlayId);
        }
        return _friendlyName;
    }

    public int getUnitCount() {
        if (_unitCount == null) {
            _unitCount = _manager
                    .getIntProperty(_overlayId, "unitCount", 0);
        }
        return (_unitCount == null ? 0 : _unitCount);
    }

    public int getOrder() {
        return _order;
    }

    public interface OnVisibleChangedListener {
        void onOverlayVisibleChanged(Overlay overlay);
    }

    public interface OnIconResourceIdChangedListener {
        void onIconResourceIdChanged(Overlay overlay);
    }

    public interface OnUnitCountChangedListener {
        void onOverlayUnitCountChanged(Overlay overlay);
    }

    public interface OnFriendlyNameChangedListener {
        void onFriendlyNameChanged(Overlay overlay);
    }

    public void addOnVisibleChangedListener(OnVisibleChangedListener listener) {
        _visibleChanged.add(listener);
    }

    public void removeOnVisibleChangedListener(
            OnVisibleChangedListener listener) {
        _visibleChanged.remove(listener);
    }

    public void addOnIconResourceIdChangedListener(
            OnIconResourceIdChangedListener l) {
        _iconResChanged.add(l);
    }

    public void removeOnIconResourceIdChangedListener(
            OnIconResourceIdChangedListener l) {
        _iconResChanged.remove(l);
    }

    protected void onVisibleChanged() {
        for (OnVisibleChangedListener cb : _visibleChanged) {
            cb.onOverlayVisibleChanged(this);
        }
    }

    protected void onIconUriChanged() {
        for (OnIconResourceIdChangedListener l : _iconResChanged) {
            l.onIconResourceIdChanged(this);
        }
    }

    protected void onFriendlyNameChanged() {
        for (OnFriendlyNameChangedListener l : _friendlyNameChanged) {
            l.onFriendlyNameChanged(this);
        }
    }

    protected void onUnitCountChanged() {
        for (OnUnitCountChangedListener l : _unitCountChanged) {
            l.onOverlayUnitCountChanged(this);
        }
    }

    public void addOnFriendlyNameChangedListener(
            OnFriendlyNameChangedListener l) {
        _friendlyNameChanged.add(l);
    }

    public void removeOnFriendlyNameChangedListener(
            OnFriendlyNameChangedListener l) {
        _friendlyNameChanged.remove(l);
    }

    public void addOnUnitCountChangedListener(
            OnUnitCountChangedListener l) {
        _unitCountChanged.add(l);
    }

    public void removeOnUnitCountChangedListener(
            OnUnitCountChangedListener l) {
        _unitCountChanged.remove(l);
    }

}
