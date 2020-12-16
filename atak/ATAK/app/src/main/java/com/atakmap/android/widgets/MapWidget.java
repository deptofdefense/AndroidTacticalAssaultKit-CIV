
package com.atakmap.android.widgets;

import android.graphics.PointF;
import android.view.MotionEvent;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.ConfigFactory;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class MapWidget {

    private final static AtomicLong serialIdGenerator = new AtomicLong(0L);

    /**
     * Default value of the zOrder property
     */
    private static final double ZORDER_DEFAULT = 1d;

    private final ConcurrentLinkedQueue<OnWidgetPointChangedListener> _onPointChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnVisibleChangedListener> _onVisibleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnPressListener> _onPress = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnLongPressListener> _onLongPress = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnMoveListener> _onMove = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnUnpressListener> _onUnpress = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnClickListener> _onClick = new ConcurrentLinkedQueue<>();
    private float _pointx, _pointy;
    private String name = "";

    private double _zOrder = ZORDER_DEFAULT;
    private boolean _visible = true;
    private final long _serialID;

    AbstractParentWidget parent;

    public interface OnWidgetPointChangedListener {
        void onWidgetPointChanged(MapWidget widget);
    }

    public interface OnVisibleChangedListener {
        void onVisibleChanged(MapWidget widget);
    }

    public interface OnPressListener {
        void onMapWidgetPress(MapWidget widget, MotionEvent event);
    }

    public interface OnLongPressListener {
        void onMapWidgetLongPress(MapWidget widget);
    }

    public interface OnUnpressListener {
        void onMapWidgetUnpress(MapWidget widget, MotionEvent event);
    }

    public interface OnMoveListener {
        boolean onMapWidgetMove(MapWidget widget, MotionEvent event);
    }

    public interface OnClickListener {
        void onMapWidgetClick(MapWidget widget, MotionEvent event);
    }

    public MapWidget() {
        _serialID = serialIdGenerator.incrementAndGet();
    }

    public static class Factory implements ConfigFactory<MapWidget> {
        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            MapWidget widget = new MapWidget();
            configAttributes(widget, config, defNode.getAttributes());
            return widget;
        }

        protected void configAttributes(MapWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            float x = DataParser.parseFloatText(attrs.getNamedItem("x"), 0f);
            float y = DataParser.parseFloatText(attrs.getNamedItem("y"), 0f);
            widget.setPoint(x, y);
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addOnPressListener(OnPressListener l) {
        _onPress.add(l);
    }

    public void removeOnPressListener(OnPressListener l) {
        _onPress.remove(l);
    }

    public void addOnLongPressListener(OnLongPressListener l) {
        _onLongPress.add(l);
    }

    public void removeOnLongPressListener(OnLongPressListener l) {
        _onLongPress.remove(l);
    }

    public void addOnMoveListener(OnMoveListener l) {
        _onMove.add(l);
    }

    public void removeOnMoveListener(OnMoveListener l) {
        _onMove.remove(l);
    }

    public void addOnUnpressListener(OnUnpressListener l) {
        _onUnpress.add(l);
    }

    public void removeOnUnpressListener(OnUnpressListener l) {
        _onUnpress.remove(l);
    }

    public void addOnClickListener(OnClickListener l) {
        _onClick.add(l);
    }

    public void removeOnClickListener(OnClickListener l) {
        _onClick.remove(l);
    }

    public void onPress(MotionEvent event) {
        for (OnPressListener l : _onPress) {
            l.onMapWidgetPress(this, event);
        }
    }

    public void onLongPress() {
        for (OnLongPressListener l : _onLongPress) {
            l.onMapWidgetLongPress(this);
        }
    }

    public void onUnpress(MotionEvent event) {
        for (OnUnpressListener l : _onUnpress) {
            l.onMapWidgetUnpress(this, event);
        }
    }

    public void onClick(MotionEvent event) {
        for (OnClickListener l : _onClick) {
            l.onMapWidgetClick(this, event);
        }
    }

    public boolean onMove(MotionEvent event) {
        boolean ret = false;
        // Only return true if we are listening to this move event and we return true on it.
        for (OnMoveListener l : _onMove) {
            if (l.onMapWidgetMove(this, event))
                ret = true;
        }
        return ret;
    }

    public boolean isEnterable() {
        return true;
    }

    public void addOnWidgetPointChangedListener(
            OnWidgetPointChangedListener l) {
        if (!_onPointChanged.contains(l))
            _onPointChanged.add(l);
    }

    public void removeOnWidgetPointChangedListener(
            OnWidgetPointChangedListener l) {
        _onPointChanged.remove(l);
    }

    public void addOnVisibleChangedListener(OnVisibleChangedListener l) {
        if (!_onVisibleChanged.contains(l))
            _onVisibleChanged.add(l);
    }

    public void removeOnVisibleChangedListener(OnVisibleChangedListener l) {
        _onVisibleChanged.remove(l);
    }

    public void setPoint(float x, float y) {
        if (Float.compare(x, _pointx) != 0 || Float.compare(y, _pointy) != 0) {
            _pointx = x;
            _pointy = y;
            onPointChanged();
        }
    }

    /**
     * Subclass to allow for a new x,y,height,width to be computed when the orientation 
     * has changed.
     */
    public void orientationChanged() {
    }

    /**
     * Set the ascending order of the MapItem. zOrder affects hit order.
     * Note:  zOrder does not affect rendering order at this time.
     *
     * @param zOrder Z-order value
     */
    public void setZOrder(double zOrder) {
        if (_zOrder != zOrder) {
            _zOrder = zOrder;
        }
    }

    public double getZOrder() {
        return _zOrder;
    }

    /**
     * Set the visibility of this map widget
     * @param visible True to show, false to hide
     * @return True if visibility changed
     */
    public boolean setVisible(boolean visible) {
        if (_visible != visible) {
            _visible = visible;
            onVisibleChanged();
            return true;
        }
        return false;
    }

    public boolean isVisible() {
        return _visible;
    }

    public boolean testHit(float x, float y) {
        return false;
    }

    public MapWidget seekHit(float x, float y) {
        return null;
    }

    public float getPointX() {
        return _pointx;
    }

    public float getPointY() {
        return _pointy;
    }

    /**
     * Get the absolute position of this widget on the screen
     * @return Widget absolute position
     */
    public PointF getAbsolutePosition() {
        MapWidget m = this;
        PointF pos = new PointF(0, 0);
        while (m != null && !(m instanceof RootLayoutWidget)) {
            pos.x += m.getPointX();
            pos.y += m.getPointY();
            m = m.getParent();
        }
        return pos;
    }

    /**
     * Get the absolute path to this widget, starting from the root layout
     * @return Widget absolute path
     */
    public String getAbsolutePath() {
        List<String> path = new ArrayList<>();
        MapWidget m = this;
        while (m != null && !(m instanceof RootLayoutWidget)) {
            String name = m.getName();
            if (FileSystemUtils.isEmpty(name))
                name = m.getClass().getSimpleName();
            path.add(0, name);
            m = m.getParent();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i));
            if (i < path.size() - 1)
                sb.append("/");
        }
        return sb.toString();
    }

    public AbstractParentWidget getParent() {
        return parent;
    }

    public void setParent(AbstractParentWidget parent) {
        this.parent = parent;
    }

    protected void onPointChanged() {
        for (OnWidgetPointChangedListener l : _onPointChanged)
            l.onWidgetPointChanged(this);
    }

    protected void onVisibleChanged() {
        for (OnVisibleChangedListener l : _onVisibleChanged)
            l.onVisibleChanged(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MapWidget mapWidget = (MapWidget) o;
        return _serialID == mapWidget._serialID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_serialID);
    }
}
