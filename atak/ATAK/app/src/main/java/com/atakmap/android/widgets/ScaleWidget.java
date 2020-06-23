
package com.atakmap.android.widgets;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.opengl.GLText;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ScaleWidget extends ShapeWidget implements
        AtakMapView.OnMapMovedListener,
        AtakMapView.OnMapProjectionChangedListener {

    private final MapView _mapView;
    private final MapTextFormat _mapTextFormat;
    private final float _xdpi;

    private String _text = "";
    private float _maxWidth = 0;
    private int _rangeUnits = Span.METRIC;
    private boolean _useRounding = false;
    private double _lastScale = 0;
    private int _lastLatitude = 180;

    public ScaleWidget(MapView mapView, MapTextFormat mtf) {
        _mapView = mapView;
        _mapTextFormat = mtf;
        _xdpi = mapView.getContext().getResources().getDisplayMetrics().xdpi;
        _mapView.addOnMapMovedListener(this);
        _mapView.addOnMapProjectionChangedListener(this);
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

    public MapTextFormat getTextFormat() {
        return _mapTextFormat;
    }

    /**
     * Important - See Span.ENGLISH, Span.METRIC, Span.NM
     */
    public void setRangeUnits(int units) {
        if (units != _rangeUnits) {
            _rangeUnits = units;
            refresh();
        }
    }

    public void setRounding(boolean enabled) {
        if (enabled != _useRounding) {
            _useRounding = enabled;
            refresh();
        }
    }

    public void setMaxWidth(float maxWidth) {
        if (Float.compare(maxWidth, _maxWidth) != 0) {
            _maxWidth = maxWidth;
            refresh();
        }
    }

    private void refresh() {
        String oldText = _text;

        double mercatorscale;
        if (_mapView.getProjection()
                .getSpatialReferenceID() == ECEFProjection.INSTANCE
                        .getSpatialReferenceID()) {
            // 3D globe projection - scale is taken care of by the resolution
            mercatorscale = 1;
        } else {
            // Default mercator scale
            mercatorscale = Math.cos(_mapView.getLatitude() /
                    ConversionFactors.DEGREES_TO_RADIANS);
            if (mercatorscale < 0.0001)
                mercatorscale = 0.0001;
        }

        //System.out.println("SHB: latitude: " + latitude + " mercatorscale:" + mercatorscale);
        //System.out.println("SHB: actual scale: " + scale + " corrected scale:" + (scale / mercatorscale));
        //System.out.println("SHB: scale meters per inch" + ((scale * mercatorscale) * dpi));

        float barWidth = _xdpi;
        double meters = _mapView.getMapResolution() * mercatorscale;
        if (_useRounding) {
            double maxWidth = Math.max(_xdpi, _maxWidth - _padding[LEFT]
                    - _padding[RIGHT]);
            meters *= maxWidth;

            String text = SpanUtilities.formatType(_rangeUnits, meters,
                    Span.METER);
            Span displayUnit = Span.findFromAbbrev(text
                    .substring(text.lastIndexOf(" ") + 1));
            if (displayUnit == null)
                displayUnit = Span.METER;

            double converted = SpanUtilities.convert(meters,
                    Span.METER, displayUnit);
            int decimalPlaces = converted < 1 ? (int) Math.ceil(-Math
                    .log10(converted)) : 0;
            double exp10 = SpanUtilities.convert(Math.pow(10, Math.floor(
                    Math.log10(converted))), displayUnit, Span.METER);
            barWidth = (float) (maxWidth * (exp10 / meters));
            _text = SpanUtilities.formatType(_rangeUnits, exp10, Span.METER,
                    decimalPlaces);
        } else {
            meters *= _xdpi;
            _text = SpanUtilities.formatType(_rangeUnits, meters, Span.METER);
        }
        _text = GLText.localize(_text);

        if (_text.startsWith("0 "))
            _text = "<1 " + _text.substring(2);

        setSize(barWidth, _mapTextFormat.measureTextHeight(_text));
        if (!FileSystemUtils.isEquals(_text, oldText)) {
            for (OnTextChangedListener l : _onTextChanged)
                l.onScaleTextChanged(this);
        }
    }

    @Override
    public void onMapProjectionChanged(AtakMapView view) {
        refresh();
    }

    @Override
    public void onMapMoved(AtakMapView mapView, boolean animate) {
        double scale = mapView.getMapResolution();
        final double latitude = mapView.getLatitude();

        // account for the scale not changed and also that there has not been
        // a wide shift in latitude.

        if ((Double.compare(scale, _lastScale) == 0)
                && (_lastLatitude == (int) latitude))
            return;

        refresh();

        _lastScale = scale;
        _lastLatitude = (int) latitude;
    }

    public interface OnTextChangedListener {
        void onScaleTextChanged(ScaleWidget widget);
    }

    public void addOnTextChangedListener(OnTextChangedListener l) {
        _onTextChanged.add(l);
    }

    public void removeOnTextChangedListener(OnTextChangedListener l) {
        _onTextChanged.remove(l);
    }

    private final ConcurrentLinkedQueue<OnTextChangedListener> _onTextChanged = new ConcurrentLinkedQueue<>();
}
