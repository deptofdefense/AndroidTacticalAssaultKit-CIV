
package com.atakmap.android.widgets;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.concurrent.ConcurrentLinkedQueue;

public class RadialButtonWidget extends AbstractButtonWidget {

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

    private final ConcurrentLinkedQueue<OnSizeChangedListener> _onSizeChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnOrientationChangedListener> _onOrientationChanged = new ConcurrentLinkedQueue<>();

    public RadialButtonWidget() {
        // defaults which are also reflected in DataParser fallbacks
        _angle = 0f;
        _radius = 100f;
        _span = 360f / 8f;
        _width = 100f;
    }

    public static class Factory extends AbstractButtonWidget.Factory {
        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            RadialButtonWidget widget = new RadialButtonWidget();
            configAttributes(widget, config, defNode.getAttributes());
            return widget;
        }

        protected void configAttributes(RadialButtonWidget widget,
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

    public void addOnSizeChangedListener(OnSizeChangedListener l) {
        _onSizeChanged.add(l);
    }

    public void removeOnSizeChangedListener(OnSizeChangedListener l) {
        _onSizeChanged.remove(l);
    }

    public void addOnOrientationChangedListener(
            OnOrientationChangedListener l) {
        _onOrientationChanged.add(l);
    }

    public void removeOnOrientationChangedListener(
            OnOrientationChangedListener l) {
        _onOrientationChanged.remove(l);
    }

    public void setOrientation(float angle, float radius) {
        if (_angle != angle || _radius != radius) {
            _angle = angle;
            _radius = radius;
            onOrientationChanged();
        }
    }

    @Override
    public MapWidget seekHit(float x, float y) {
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
        for (OnSizeChangedListener l : _onSizeChanged) {
            l.onRadialButtonSizeChanged(this);
        }
    }

    protected void onOrientationChanged() {
        for (OnOrientationChangedListener l : _onOrientationChanged) {
            l.onRadialButtonOrientationChanged(this);
        }
    }

}
