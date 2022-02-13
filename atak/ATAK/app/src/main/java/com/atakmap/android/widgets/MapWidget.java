
package com.atakmap.android.widgets;

import android.graphics.PointF;

import gov.tak.api.util.Visitor;
import gov.tak.platform.binding.AbstractPropertyBindingObject;
import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.marshal.MarshalManager;
import android.view.MotionEvent;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.ConfigFactory;
import com.atakmap.android.config.DataParser;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import gov.tak.api.widgets.IParentWidget;
import gov.tak.api.widgets.IMapWidget;

@Deprecated
@DeprecatedApi(since = "4.4")
public class MapWidget extends AbstractPropertyBindingObject<IMapWidget>
        implements IMapWidget {

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    private final static AtomicLong serialIdGenerator = new AtomicLong(0L);

    /**
     * Default value of the zOrder property
     */
    private static final double ZORDER_DEFAULT = 1d;

    private final Map<MapWidget.OnWidgetPointChangedListener, IMapWidget.OnWidgetPointChangedListener> _onPointChangedForwarders = new IdentityHashMap<>();
    private final Map<MapWidget.OnVisibleChangedListener, IMapWidget.OnVisibleChangedListener> _onVisibleChangedForwarders = new IdentityHashMap<>();
    private final Map<MapWidget.OnPressListener, IMapWidget.OnPressListener> _onPressForwarders = new IdentityHashMap<>();
    private final Map<MapWidget.OnLongPressListener, IMapWidget.OnLongPressListener> _onLongPressForwarders = new IdentityHashMap<>();
    private final Map<MapWidget.OnMoveListener, IMapWidget.OnMoveListener> _onMoveForwarders = new IdentityHashMap<>();
    private final Map<MapWidget.OnUnpressListener, IMapWidget.OnUnpressListener> _onUnpressForwarders = new IdentityHashMap<>();
    private final Map<MapWidget.OnClickListener, IMapWidget.OnClickListener> _onClickForwarders = new IdentityHashMap<>();

    private final ConcurrentLinkedQueue<IMapWidget.OnWidgetPointChangedListener> _onPointChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnVisibleChangedListener> _onVisibleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnPressListener> _onPress = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnLongPressListener> _onLongPress = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnMoveListener> _onMove = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnUnpressListener> _onUnpress = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnClickListener> _onClick = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnHoverListener> _onHover = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IMapWidget.OnWidgetSizeChangedListener> _onSizeChanged = new ConcurrentLinkedQueue<>();

    private float _pointx, _pointy;
    private String name = "";

    private double _zOrder = ZORDER_DEFAULT;
    private boolean _visible = true;
    private final long _serialID;

    protected float _width;
    protected float _height;

    protected float[] _margin = new float[] {
            0, 0, 0, 0
    };
    protected float[] _padding = new float[] {
            0, 0, 0, 0
    };
    protected boolean _touchable = true;

    AbstractParentWidget parent;

    @Override
    public Object getPropertyValue(String property) {
        return null;
    }

    @Override
    public void visitPropertyInfos(Visitor<PropertyInfo> visitor) {
        // nothing
    }

    public interface OnWidgetPointChangedListener {
        void onWidgetPointChanged(MapWidget widget);
    }

    public interface OnVisibleChangedListener {
        void onVisibleChanged(MapWidget widget);
    }

    public interface OnPressListener {
        void onMapWidgetPress(MapWidget widget, android.view.MotionEvent event);
    }

    public interface OnLongPressListener {
        void onMapWidgetLongPress(MapWidget widget);
    }

    public interface OnUnpressListener {
        void onMapWidgetUnpress(MapWidget widget,
                android.view.MotionEvent event);
    }

    public interface OnMoveListener {
        boolean onMapWidgetMove(MapWidget widget,
                android.view.MotionEvent event);
    }

    public interface OnClickListener {
        void onMapWidgetClick(MapWidget widget, android.view.MotionEvent event);
    }

    public MapWidget() {
        _serialID = serialIdGenerator.incrementAndGet();
    }

    public static class Factory implements ConfigFactory<IMapWidget> {
        @Override
        public IMapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            MapWidget widget = new MapWidget();
            configAttributes(widget, config, defNode.getAttributes());
            return widget;
        }

        protected void configAttributes(MapWidget widget,
                ConfigEnvironment config, NamedNodeMap attrs) {
            configAttributes((IMapWidget) widget, config, attrs);
        }

        protected void configAttributes(IMapWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            float x = DataParser.parseFloatText(attrs.getNamedItem("x"), 0f);
            float y = DataParser.parseFloatText(attrs.getNamedItem("y"), 0f);
            widget.setPoint(x, y);
        }
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public final void addOnPressListener(IMapWidget.OnPressListener l) {
        _onPress.add(l);
    }

    public void addOnPressListener(MapWidget.OnPressListener l) {
        registerForwardedListener(_onPress, _onPressForwarders, l,
                new WidgetPressForwarder(l));
    }

    @Override
    public final void removeOnPressListener(IMapWidget.OnPressListener l) {
        _onPress.remove(l);
    }

    public void removeOnPressListener(MapWidget.OnPressListener l) {
        unregisterForwardedListener(_onPress, _onPressForwarders, l);
    }

    @Override
    public final void addOnLongPressListener(IMapWidget.OnLongPressListener l) {
        _onLongPress.add(l);
    }

    public void addOnLongPressListener(MapWidget.OnLongPressListener l) {
        registerForwardedListener(_onLongPress, _onLongPressForwarders, l,
                new WidgetLongPressForwarder(l));
    }

    @Override
    public final void removeOnLongPressListener(
            IMapWidget.OnLongPressListener l) {
        _onLongPress.remove(l);
    }

    public void removeOnLongPressListener(MapWidget.OnLongPressListener l) {
        unregisterForwardedListener(_onLongPress, _onLongPressForwarders, l);
    }

    @Override
    public final void addOnMoveListener(IMapWidget.OnMoveListener l) {
        _onMove.add(l);
    }

    public void addOnMoveListener(MapWidget.OnMoveListener l) {
        registerForwardedListener(_onMove, _onMoveForwarders, l,
                new WidgetMoveForwarder(l));
    }

    @Override
    public final void removeOnMoveListener(IMapWidget.OnMoveListener l) {
        _onMove.remove(l);
    }

    public void removeOnMoveListener(MapWidget.OnMoveListener l) {
        unregisterForwardedListener(_onMove, _onMoveForwarders, l);
    }

    @Override
    public final void addOnUnpressListener(IMapWidget.OnUnpressListener l) {
        _onUnpress.add(l);
    }

    public void addOnUnpressListener(MapWidget.OnUnpressListener l) {
        registerForwardedListener(_onUnpress, _onUnpressForwarders, l,
                new WidgetUnpressForwarder(l));
    }

    @Override
    public final void removeOnUnpressListener(IMapWidget.OnUnpressListener l) {
        _onUnpress.remove(l);
    }

    public void removeOnUnpressListener(MapWidget.OnUnpressListener l) {
        unregisterForwardedListener(_onUnpress, _onUnpressForwarders, l);
    }

    @Override
    public final void addOnClickListener(IMapWidget.OnClickListener l) {
        _onClick.add(l);
    }

    public void addOnClickListener(MapWidget.OnClickListener l) {
        registerForwardedListener(_onClick, _onClickForwarders, l,
                new WidgetClickForwarder(l));
    }

    @Override
    public final void removeOnClickListener(IMapWidget.OnClickListener l) {
        _onClick.remove(l);
    }

    @Override
    public void addOnWidgetSizeChangedListener(OnWidgetSizeChangedListener l) {
        _onSizeChanged.add(l);
    }

    @Override
    public void removeOnWidgetSizeChangedListener(
            OnWidgetSizeChangedListener l) {
        _onSizeChanged.remove(l);
    }

    @Override
    public void addOnHoverListener(OnHoverListener l) {
        _onHover.add(l);
    }

    @Override
    public void removeOnHoverListener(OnHoverListener l) {
        _onHover.remove(l);
    }

    public void removeOnClickListener(MapWidget.OnClickListener l) {
        unregisterForwardedListener(_onClick, _onClickForwarders, l);
    }

    public void onPress(MotionEvent aEvent) {
        final gov.tak.platform.ui.MotionEvent event = MarshalManager.marshal(
                aEvent,
                android.view.MotionEvent.class,
                gov.tak.platform.ui.MotionEvent.class);
        onPress(event);
    }

    @Override
    public void onPress(gov.tak.platform.ui.MotionEvent event) {
        for (IMapWidget.OnPressListener l : _onPress) {
            l.onMapWidgetPress(this, event);
        }
    }

    @Override
    public void onLongPress() {
        for (IMapWidget.OnLongPressListener l : _onLongPress) {
            l.onMapWidgetLongPress(this);
        }
    }

    public void onUnpress(MotionEvent aEvent) {
        final gov.tak.platform.ui.MotionEvent event = MarshalManager.marshal(
                aEvent,
                android.view.MotionEvent.class,
                gov.tak.platform.ui.MotionEvent.class);
        onUnpress(event);
    }

    @Override
    public void onUnpress(gov.tak.platform.ui.MotionEvent event) {
        for (IMapWidget.OnUnpressListener l : _onUnpress) {
            l.onMapWidgetUnpress(this, event);
        }
    }

    public void onClick(MotionEvent aEvent) {
        final gov.tak.platform.ui.MotionEvent event = MarshalManager.marshal(
                aEvent,
                android.view.MotionEvent.class,
                gov.tak.platform.ui.MotionEvent.class);
        onClick(event);
    }

    @Override
    public void onClick(gov.tak.platform.ui.MotionEvent event) {
        for (IMapWidget.OnClickListener l : _onClick) {
            l.onMapWidgetClick(this, event);
        }
    }

    public boolean onMove(MotionEvent aEvent) {
        final gov.tak.platform.ui.MotionEvent event = MarshalManager.marshal(
                aEvent,
                android.view.MotionEvent.class,
                gov.tak.platform.ui.MotionEvent.class);
        return onMove(event);
    }

    @Override
    public boolean onMove(gov.tak.platform.ui.MotionEvent event) {
        boolean ret = false;
        // Only return true if we are listening to this move event and we return true on it.
        for (IMapWidget.OnMoveListener l : _onMove) {
            if (l.onMapWidgetMove(this, event))
                ret = true;
        }
        return ret;
    }

    @Override
    public void onHover(gov.tak.platform.ui.MotionEvent event) {
        for (IMapWidget.OnHoverListener l : _onHover)
            l.onMapWidgetHover(this, event);
    }

    @Override
    public IMapWidget seekWidgetHit(gov.tak.platform.ui.MotionEvent event,
            float x, float y) {
        return null;
    }

    public MapWidget seekHit(float x, float y) {
        return null;
    }

    public boolean isEnterable() {
        return true;
    }

    @Override
    public final void addOnWidgetPointChangedListener(
            IMapWidget.OnWidgetPointChangedListener l) {
        if (!_onPointChanged.contains(l))
            _onPointChanged.add(l);
    }

    public void addOnWidgetPointChangedListener(
            MapWidget.OnWidgetPointChangedListener l) {
        registerForwardedListener(_onPointChanged, _onPointChangedForwarders, l,
                new WidgetPointChangedForwarder(l));
    }

    @Override
    public final void removeOnWidgetPointChangedListener(
            IMapWidget.OnWidgetPointChangedListener l) {
        _onPointChanged.remove(l);
    }

    public void removeOnWidgetPointChangedListener(
            MapWidget.OnWidgetPointChangedListener l) {
        unregisterForwardedListener(_onPointChanged, _onPointChangedForwarders,
                l);
    }

    @Override
    public final void addOnVisibleChangedListener(
            IMapWidget.OnVisibleChangedListener l) {
        if (!_onVisibleChanged.contains(l))
            _onVisibleChanged.add(l);
    }

    public void addOnVisibleChangedListener(
            MapWidget.OnVisibleChangedListener l) {
        registerForwardedListener(_onVisibleChanged,
                _onVisibleChangedForwarders, l,
                new WidgetVisibleChangedForwarder(l));
    }

    @Override
    public final void removeOnVisibleChangedListener(
            IMapWidget.OnVisibleChangedListener l) {
        _onVisibleChanged.remove(l);
    }

    public void removeOnVisibleChangedListener(
            MapWidget.OnVisibleChangedListener l) {
        unregisterForwardedListener(_onVisibleChanged,
                _onVisibleChangedForwarders, l);
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
        IMapWidget m = this;
        PointF pos = new PointF(0, 0);
        while (m != null
                && !(m instanceof gov.tak.platform.widgets.RootLayoutWidget)) {
            pos.x += m.getPointX();
            pos.y += m.getPointY();
            m = m.getParent();
        }
        return pos;
    }

    @Override
    public final gov.tak.platform.graphics.PointF getAbsoluteWidgetPosition() {
        final PointF xy = getAbsolutePosition();
        return (xy != null) ? new gov.tak.platform.graphics.PointF(xy.x, xy.y)
                : null;
    }

    /**
     * Get the absolute path to this widget, starting from the root layout
     * @return Widget absolute path
     */
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
    public AbstractParentWidget getParent() {
        return parent;
    }

    @Override
    public boolean setWidth(float width) {
        return setSize(width, _height);
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

    /**
     * Set the size of the widget
     * @param width Bounds width
     * @param height Bounds height
     */
    public boolean setSize(final float width, final float height) {
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

    public void setParent(IParentWidget parent) {
        this.parent = (AbstractParentWidget) parent;
    }

    public void setParent(AbstractParentWidget parent) {
        setParent((IParentWidget) parent);
    }

    protected void onPointChanged() {
        for (IMapWidget.OnWidgetPointChangedListener l : _onPointChanged)
            l.onWidgetPointChanged(this);
    }

    protected void onVisibleChanged() {
        for (IMapWidget.OnVisibleChangedListener l : _onVisibleChanged)
            l.onVisibleChanged(this);
    }

    protected void onSizeChanged() {
        for (IMapWidget.OnWidgetSizeChangedListener l : _onSizeChanged) {
            l.onWidgetSizeChanged(this);
        }
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

    protected <LegacyListener, Listener> void registerForwardedListener(
            Collection<Listener> listeners,
            Map<LegacyListener, Listener> forwarders, LegacyListener listener,
            Listener forwarder) {
        synchronized (forwarders) {
            if (forwarders.containsKey(listener))
                return;
            forwarders.put(listener, forwarder);
        }
        listeners.add(forwarder);
    }

    protected <LegacyListener, Listener> void unregisterForwardedListener(
            Collection<Listener> listeners,
            Map<LegacyListener, Listener> forwarders, LegacyListener listener) {
        final Listener forwarder;
        synchronized (forwarders) {
            forwarder = forwarders.remove(listener);
            if (forwarder == null)
                return;
        }
        listeners.remove(forwarder);
    }
}
