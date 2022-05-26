
package com.atakmap.android.widgets;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IScaleWidget2;

public class ScaleWidget extends ShapeWidget implements IScaleWidget2 {

    private final MapView _mapView;
    private final MapTextFormat _mapTextFormat;

    private String _text = "";
    private final float _minWidth;
    private float _maxWidth;
    private int _rangeUnits = Span.METRIC;
    private boolean _useRounding = false;
    private double _scale;

    private final ConcurrentLinkedQueue<OnDisplayChangedListener> _dispListeners = new ConcurrentLinkedQueue<>();

    public ScaleWidget(MapView mapView, MapTextFormat mtf) {
        _mapView = mapView;
        _mapTextFormat = mtf;
        _minWidth = mapView.getContext().getResources()
                .getDisplayMetrics().xdpi;
        _maxWidth = mapView.getWidth();
    }

    @Override
    public boolean testHit(float x, float y) {
        return false;
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        return null;
    }

    public String getText() {
        return _text;
    }

    @Override
    public double getScale() {
        return _scale;
    }

    @Override
    public int getUnits() {
        return _rangeUnits;
    }

    @Override
    public boolean isRounded() {
        return _useRounding;
    }

    @Override
    public float getMinWidth() {
        return _minWidth;
    }

    @Override
    public float getMaxWidth() {
        return _maxWidth;
    }

    @Override
    public void update(String text, double scale) {
        _scale = scale;
        if (!FileSystemUtils.isEquals(_text, text)) {
            _text = text;
            for (IScaleWidget2.OnTextChangedListener l : _onTextChanged)
                l.onScaleTextChanged(this);
        }
    }

    public MapTextFormat getTextFormat() {
        return _mapTextFormat;
    }

    /**
     * Important - See Span.ENGLISH, Span.METRIC, Span.NM
     */
    public void setRangeUnits(int units) {
        if (units != _rangeUnits) {
            _rangeUnits = units;
            onDisplayChanged();
        }
    }

    public void setRounding(boolean enabled) {
        if (enabled != _useRounding) {
            _useRounding = enabled;
            onDisplayChanged();
        }
    }

    public void setMaxWidth(float maxWidth) {
        if (Float.compare(maxWidth, _maxWidth) != 0) {
            _maxWidth = maxWidth;
            onDisplayChanged();
        }
    }

    private void onDisplayChanged() {
        for (OnDisplayChangedListener l : _dispListeners)
            l.onDisplayChanged(this);
    }

    @Override
    protected void onPointChanged() {
        super.onPointChanged();
        onDisplayChanged();
    }

    @Override
    public void removeOnDisplayChangedListener(
            OnDisplayChangedListener listener) {
        _dispListeners.remove(listener);
    }

    @Override
    public void addOnDisplayChangedListener(OnDisplayChangedListener listener) {
        _dispListeners.add(listener);
    }

    public interface OnTextChangedListener {
        void onScaleTextChanged(ScaleWidget widget);
    }

    @Override
    public final void addOnTextChangedListener(
            IScaleWidget2.OnTextChangedListener l) {
        _onTextChanged.add(l);
    }

    public void addOnTextChangedListener(ScaleWidget.OnTextChangedListener l) {
        registerForwardedListener(_onTextChanged, _onTextChangedForwarders, l,
                new TextChangedForwarder(l));
    }

    @Override
    public final void removeOnTextChangedListener(
            IScaleWidget2.OnTextChangedListener l) {
        _onTextChanged.remove(l);
    }

    public void removeOnTextChangedListener(
            ScaleWidget.OnTextChangedListener l) {
        unregisterForwardedListener(_onTextChanged, _onTextChangedForwarders,
                l);
    }

    private final ConcurrentLinkedQueue<IScaleWidget2.OnTextChangedListener> _onTextChanged = new ConcurrentLinkedQueue<>();
    private final Map<ScaleWidget.OnTextChangedListener, IScaleWidget2.OnTextChangedListener> _onTextChangedForwarders = new IdentityHashMap<>();

    private final static class TextChangedForwarder
            implements IScaleWidget2.OnTextChangedListener {
        final ScaleWidget.OnTextChangedListener _cb;

        TextChangedForwarder(ScaleWidget.OnTextChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onScaleTextChanged(IScaleWidget2 widget) {
            if (widget instanceof ScaleWidget)
                _cb.onScaleTextChanged((ScaleWidget) widget);
        }
    }
}
