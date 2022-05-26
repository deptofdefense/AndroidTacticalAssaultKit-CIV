
package gov.tak.platform.widgets;

import gov.tak.api.util.Visitor;
import gov.tak.platform.binding.AbstractPropertyBindingObject;
import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.graphics.PointF;
import gov.tak.platform.ui.MotionEvent;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import gov.tak.platform.widgets.config.ConfigWidgetModel;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IParentWidget;
import gov.tak.platform.config.ConfigEnvironment;
import gov.tak.platform.config.ConfigFactory;

import gov.tak.platform.widgets.config.ConfigWidgetController;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class MapWidget extends AbstractPropertyBindingObject<IMapWidget> implements IMapWidget {

    private final static AtomicLong serialIdGenerator = new AtomicLong(0L);

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    protected float _width;
    protected float _height;
    protected float[] _margin = new float[] {
            0, 0, 0, 0
    };
    protected float[] _padding = new float[] {
            0, 0, 0, 0
    };
    protected boolean _touchable = true;

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
    private final ConcurrentLinkedQueue<OnWidgetSizeChangedListener> _onSizeChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnHoverListener> onHoverListeners = new ConcurrentLinkedQueue<>();

    private float _pointx, _pointy;
    private String name = "";

    private double _zOrder = ZORDER_DEFAULT;
    private boolean _visible = true;
    private final long _serialID;

    IParentWidget parent;

    public MapWidget() {
        _serialID = serialIdGenerator.incrementAndGet();
    }

    @Deprecated
    public static class Factory implements ConfigFactory<IMapWidget> {
        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                                        Node defNode) {
            MapWidget widget = new MapWidget();
            ConfigWidgetController controller = new ConfigWidgetController(widget,
                    new ConfigWidgetModel(defNode, config));
            controller.refreshProperties();
            //configAttributes(widget, config, defNode.getAttributes());
            return widget;
        }

        protected void configAttributes(IMapWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {


        }
    }

    @Override
    public void addOnWidgetPointChangedListener(
            OnWidgetPointChangedListener l) {
        if (!_onPointChanged.contains(l))
            _onPointChanged.add(l);
    }

    @Override
    public void removeOnWidgetPointChangedListener(
            OnWidgetPointChangedListener l) {
        _onPointChanged.remove(l);
    }

    @Override
    public void addOnVisibleChangedListener(OnVisibleChangedListener l) {
        if (!_onVisibleChanged.contains(l))
            _onVisibleChanged.add(l);
    }

    @Override
    public void removeOnVisibleChangedListener(OnVisibleChangedListener l) {
        _onVisibleChanged.remove(l);
    }

    @Override
    public void addOnPressListener(OnPressListener l) {
        _onPress.add(l);
    }

    @Override
    public void removeOnPressListener(OnPressListener l) {
        _onPress.remove(l);
    }

    @Override
    public void addOnLongPressListener(OnLongPressListener l) {
        _onLongPress.add(l);
    }

    @Override
    public void removeOnLongPressListener(OnLongPressListener l) { _onLongPress.remove(l); }

    @Override
    public void addOnUnpressListener(OnUnpressListener l) {
        _onUnpress.add(l);
    }

    @Override
    public void removeOnUnpressListener(OnUnpressListener l) {
        _onUnpress.remove(l);
    }

    @Override
    public void addOnMoveListener(OnMoveListener l) {
        _onMove.add(l);
    }

    @Override
    public void removeOnMoveListener(OnMoveListener l) {
        _onMove.remove(l);
    }

    @Override
    public void addOnClickListener(OnClickListener l) {
        _onClick.add(l);
    }

    @Override
    public void removeOnClickListener(OnClickListener l) {
        _onClick.remove(l);
    }

    @Override
    public void addOnWidgetSizeChangedListener(
            OnWidgetSizeChangedListener l) {
        _onSizeChanged.add(l);
    }

    @Override
    public void removeOnWidgetSizeChangedListener(
            OnWidgetSizeChangedListener l) {
        _onSizeChanged.remove(l);
    }

    @Override
    public void addOnHoverListener(OnHoverListener l) {
        onHoverListeners.add(l);
    }

    @Override
    public void removeOnHoverListener(OnHoverListener l) {
        onHoverListeners.remove(l);
    }

    @Override
    public void onPress(MotionEvent event) {
        for (OnPressListener l : _onPress) {
            l.onMapWidgetPress(this, event);
        }
    }

    @Override
    public void onLongPress() {
        for (OnLongPressListener l : _onLongPress) {
            l.onMapWidgetLongPress(this);
        }
    }

    @Override
    public void onUnpress(MotionEvent event) {
        for (OnUnpressListener l : _onUnpress) {
            l.onMapWidgetUnpress(this, event);
        }
    }

    @Override
    public void onClick(MotionEvent event) {
        for (OnClickListener l : _onClick) {
            l.onMapWidgetClick(this, event);
        }
    }

    @Override
    public boolean onMove(MotionEvent event) {
        boolean ret = false;
        // Only return true if we are listening to this move event and we return true on it.
        for (OnMoveListener l : _onMove) {
            if (l.onMapWidgetMove(this, event))
                ret = true;
        }
        return ret;
    }

    @Override
    public void onHover(MotionEvent event) {
        for (OnHoverListener l : onHoverListeners)
            l.onMapWidgetHover(this, event);
    }

    @Override
    public boolean isEnterable() {
        return true;
    }

    @Override
    public void setPoint(float x, float y) {
        if (Float.compare(x, _pointx) != 0 || Float.compare(y, _pointy) != 0) {
            _pointx = x;
            _pointy = y;
            onPointChanged();
        }
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Subclass to allow for a new x,y,height,width to be computed when the orientation
     * has changed.
     */
    @Override
    public void orientationChanged() {
    }

    /**
     * Set the ascending order of the MapItem. zOrder affects hit order.
     * Note:  zOrder does not affect rendering order at this time.
     *
     * @param zOrder Z-order value
     */
    @Override
    public void setZOrder(double zOrder) {
        if (_zOrder != zOrder) {
            _zOrder = zOrder;
        }
    }

    @Override
    public double getZOrder() {
        return _zOrder;
    }

    /**
     * Set the visibility of this map widget
     * @param visible True to show, false to hide
     * @return True if visibility changed
     */
    @Override
    public boolean setVisible(boolean visible) {
        if (_visible != visible) {
            _visible = visible;
            onVisibleChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean isVisible() {
        return _visible;
    }

    @Override
    public boolean testHit(float x, float y) {
        return x >= 0 && x < _width + _padding[LEFT] + _padding[RIGHT]
                && y >= 0 && y < _height + _padding[TOP] + _padding[BOTTOM];
    }

    @Override
    public IMapWidget seekWidgetHit(MotionEvent event, float x, float y) {
        MapWidget r = null;
        if (isVisible() && isTouchable() && testHit(x, y))
            r = this;
        return r;
    }

    @Override
    public float getPointX() {
        return _pointx;
    }

    @Override
    public float getPointY() {
        return _pointy;
    }

    /**
     * Get the absolute position of this widget on the screen
     * @return Widget absolute position
     */
    @Override
    public PointF getAbsoluteWidgetPosition() {
        IMapWidget m = this;
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
    @Override
    public String getAbsolutePath() {
        List<String> path = new ArrayList<>();
        IMapWidget m = this;
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

    @Override
    public void setParent(IParentWidget parent) {
        this.parent = parent;
    }

    @Override
    public IParentWidget getParent() {
        return parent;
    }

    @Override
    public boolean setWidth(float width) {
        return setSize(width, _height);
    }

    /**
     * Invoked when the Widget should actually set the internal state to reflect
     * the property value change
     *
     * @param propertyName the property name
     * @param newValue the value of the property
     */
    protected void applyPropertyChange(String propertyName, Object newValue) {
        if (PROPERTY_POINT.canAssignValue(propertyName, newValue)) {
            PointF point = (PointF)newValue;
            if (point != null)
                this.setPoint(point.x, point.y);
        } else {
            super.applyPropertyChange(propertyName, newValue);
        }
    }

    @Override
    public Object getPropertyValue(String propertyName) {
        if (propertyName.equals(PROPERTY_POINT.getName()))
            return new PointF(this.getPointX(), this.getPointY());
        return null;
    }

    @Override
    public void visitPropertyInfos(Visitor<PropertyInfo> visitor) {
        visitor.visit(PROPERTY_POINT);
    }

    @Override
    public float getWidth() {
        return _width;
    }

    @Override
    public boolean setHeight(float height) {
        return setSize(_width, height);
    }

    @Override
    public float getHeight() {
        return _height;
    }

    @Override
    public boolean setSize(float width, float height) {
        if (Float.compare(width, _width) != 0
                || Float.compare(height, _height) != 0) {
            _width = width;
            _height = height;
            onSizeChanged();
            return true;
        }
        return false;
    }

    @Override
    public float[] getSize(boolean incPadding, boolean incMargin) {
        float width = _width;
        float height = _height;
        if (width <= 0 || height <= 0)
            return new float[] {
                    0, 0
            };
        if (incPadding) {
            width += _padding[LEFT] + _padding[RIGHT];
            height += _padding[TOP] + _padding[BOTTOM];
        }
        if (incMargin) {
            width += _margin[LEFT] + _margin[RIGHT];
            height += _margin[TOP] + _margin[BOTTOM];
        }
        return new float[] {
                Math.max(0, width), Math.max(0, height)
        };
    }

    @Override
    public void setMargins(float left, float top, float right, float bottom) {
        if (Float.compare(left, _margin[LEFT]) != 0
                || Float.compare(top, _margin[TOP]) != 0
                || Float.compare(right, _margin[RIGHT]) != 0
                || Float.compare(bottom, _margin[BOTTOM]) != 0) {
            _margin[LEFT] = left;
            _margin[TOP] = top;
            _margin[RIGHT] = right;
            _margin[BOTTOM] = bottom;
            onSizeChanged();
        }
    }

    @Override
    public float[] getMargins() {
        return new float[] {
                _margin[LEFT], _margin[TOP],
                _margin[RIGHT], _margin[BOTTOM]
        };
    }

    @Override
    public boolean setPadding(float left, float top, float right,
                              float bottom) {
        if (Float.compare(left, _padding[LEFT]) != 0
                || Float.compare(top, _padding[TOP]) != 0
                || Float.compare(right, _padding[RIGHT]) != 0
                || Float.compare(bottom, _padding[BOTTOM]) != 0) {
            _padding[LEFT] = left;
            _padding[TOP] = top;
            _padding[RIGHT] = right;
            _padding[BOTTOM] = bottom;
            onSizeChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean setPadding(float p) {
        return setPadding(p, p, p, p);
    }

    @Override
    public float[] getPadding() {
        return new float[] {
                _padding[LEFT], _padding[TOP],
                _padding[RIGHT], _padding[BOTTOM]
        };
    }

    /**
     * Set whether this widget can be touched
     * @param touchable True if touchable
     */
    @Override
    public void setTouchable(boolean touchable) {
        _touchable = touchable;
    }

    @Override
    public boolean isTouchable() {
        return _touchable;
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

    public void onPointChanged() {
        for (OnWidgetPointChangedListener l : _onPointChanged)
            l.onWidgetPointChanged(this);
    }

    public void onVisibleChanged() {
        for (OnVisibleChangedListener l : _onVisibleChanged)
            l.onVisibleChanged(this);
    }

    public void onSizeChanged() {
        for (OnWidgetSizeChangedListener l : _onSizeChanged) {
            l.onWidgetSizeChanged(this);
        }
    }

    /**
     * Helper method for retrieving size of a widget with included
     * visibility and class checks
     * @param w Map widget
     * @param incPadding Include padding in size
     * @param incMargin Include margin in size
     * @return [width, height]
     */
    public static float[] getSize(IMapWidget w, boolean incPadding,
            boolean incMargin) {
        if (w != null && w.isVisible() && w instanceof IMapWidget)
            return ((IMapWidget) w).getSize(incPadding, incMargin);
        return new float[] {
                0, 0
        };
    }
}
