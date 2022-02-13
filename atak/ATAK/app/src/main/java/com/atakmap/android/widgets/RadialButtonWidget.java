
package com.atakmap.android.widgets;

import gov.tak.platform.ui.MotionEvent;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IRadialButtonWidget;

public class RadialButtonWidget extends AbstractButtonWidget
        implements IRadialButtonWidget {

    public interface OnSizeChangedListener {
        void onRadialButtonSizeChanged(RadialButtonWidget button);
    }

    public interface OnOrientationChangedListener {
        void onRadialButtonOrientationChanged(RadialButtonWidget button);
    }

    private float _angle;
    private float _radius;

    private float _span;
    private float _width;

    private final ConcurrentLinkedQueue<IRadialButtonWidget.OnSizeChangedListener> _onSizeChanged = new ConcurrentLinkedQueue<>();
    private final Map<RadialButtonWidget.OnSizeChangedListener, IRadialButtonWidget.OnSizeChangedListener> _onSizeChangedForwarders = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<IRadialButtonWidget.OnOrientationChangedListener> _onOrientationChanged = new ConcurrentLinkedQueue<>();
    private final Map<RadialButtonWidget.OnOrientationChangedListener, IRadialButtonWidget.OnOrientationChangedListener> _onOrientationChangedForwarders = new IdentityHashMap<>();

    public RadialButtonWidget() {
        // defaults which are also reflected in DataParser fallbacks
        _angle = 0f;
        _radius = 100f;
        _span = 360f / 8f;
        _width = 100f;
    }

    public static class Factory extends AbstractButtonWidget.Factory {
        @Override
        public IMapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            IRadialButtonWidget widget = new RadialButtonWidget();
            configAttributes(widget, config, defNode.getAttributes());
            return widget;
        }

        protected void configAttributes(IRadialButtonWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);

            // orientation
            float angle = DataParser.parseFloatText(
                    attrs.getNamedItem("angle"), 0f);
            float radius = DataParser.parseFloatText(
                    attrs.getNamedItem("radius"), 100f);
            widget.setOrientation(angle, radius);

            // button size
            float span = DataParser.parseFloatText(attrs.getNamedItem("span"),
                    360f / 8f);
            float width = DataParser.parseFloatText(
                    attrs.getNamedItem("width"), 100f);
            widget.setButtonSize(span, width);
        }
    }

    public float getOrientationAngle() {
        return _angle;
    }

    public float getOrientationRadius() {
        return _radius;
    }

    public float getButtonSpan() {
        return _span;
    }

    public float getButtonWidth() {
        return _width;
    }

    @Override
    public final void addOnSizeChangedListener(
            IRadialButtonWidget.OnSizeChangedListener l) {
        _onSizeChanged.add(l);
    }

    public void addOnSizeChangedListener(
            RadialButtonWidget.OnSizeChangedListener l) {
        registerForwardedListener(_onSizeChanged, _onSizeChangedForwarders, l,
                new SizeChangedForwarder(l));
    }

    @Override
    public final void removeOnSizeChangedListener(
            IRadialButtonWidget.OnSizeChangedListener l) {
        _onSizeChanged.remove(l);
    }

    public void removeOnSizeChangedListener(
            RadialButtonWidget.OnSizeChangedListener l) {
        unregisterForwardedListener(_onSizeChanged, _onSizeChangedForwarders,
                l);
    }

    @Override
    public final void addOnOrientationChangedListener(
            IRadialButtonWidget.OnOrientationChangedListener l) {
        _onOrientationChanged.add(l);
    }

    public final void addOnOrientationChangedListener(
            RadialButtonWidget.OnOrientationChangedListener l) {
        registerForwardedListener(_onOrientationChanged,
                _onOrientationChangedForwarders, l,
                new OrientationChangedForwarder(l));
    }

    @Override
    public final void removeOnOrientationChangedListener(
            IRadialButtonWidget.OnOrientationChangedListener l) {
        _onOrientationChanged.remove(l);
    }

    public void removeOnOrientationChangedListener(
            RadialButtonWidget.OnOrientationChangedListener l) {
        unregisterForwardedListener(_onOrientationChanged,
                _onOrientationChangedForwarders, l);
    }

    public void setOrientation(float angle, float radius) {
        if (_angle != angle || _radius != radius) {
            _angle = angle;
            _radius = radius;
            onOrientationChanged();
        }
    }

    @Override
    public MapWidget seekWidgetHit(MotionEvent event, float x, float y) {
        MapWidget hit = null;

        float d = (float) Math.sqrt(x * x + y * y);
        if (d >= _radius && d < _radius + _width) {

            double low = _angle - _span / 2;
            double high = _angle + _span / 2;

            double angle = -Math.atan2(y, x) * 180d / Math.PI;
            if (low >= 0d && angle < 0d) {
                angle = 360d + angle;
            }

            if (angle >= low && angle <= high) {
                hit = this;
            }
        }

        return hit;
    }

    public void setButtonSize(float span, float width) {
        if (_span != span || _width != width) {
            _span = span;
            _width = width;
            onSizeChanged();
        }
    }

    protected void onSizeChanged() {
        for (IRadialButtonWidget.OnSizeChangedListener l : _onSizeChanged) {
            l.onRadialButtonSizeChanged(this);
        }
    }

    protected void onOrientationChanged() {
        for (IRadialButtonWidget.OnOrientationChangedListener l : _onOrientationChanged) {
            l.onRadialButtonOrientationChanged(this);
        }
    }

    private final static class SizeChangedForwarder
            implements IRadialButtonWidget.OnSizeChangedListener {
        final RadialButtonWidget.OnSizeChangedListener _cb;

        SizeChangedForwarder(RadialButtonWidget.OnSizeChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onRadialButtonSizeChanged(IRadialButtonWidget button) {
            if (button instanceof RadialButtonWidget)
                _cb.onRadialButtonSizeChanged((RadialButtonWidget) button);
        }
    }

    private final static class OrientationChangedForwarder
            implements IRadialButtonWidget.OnOrientationChangedListener {
        final RadialButtonWidget.OnOrientationChangedListener _cb;

        OrientationChangedForwarder(
                RadialButtonWidget.OnOrientationChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onRadialButtonOrientationChanged(
                IRadialButtonWidget button) {
            if (button instanceof RadialButtonWidget)
                _cb.onRadialButtonOrientationChanged(
                        (RadialButtonWidget) button);
        }
    }
}
