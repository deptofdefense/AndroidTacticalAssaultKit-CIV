
package com.atakmap.android.routes.elevation;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.elevation.model.UnitConverter;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class SeekerBarPanelPresenter implements View.OnClickListener {

    private final Context _context;
    protected SeekerBarPanelView _seekerBarPanelView;

    private CoordinateFormat _locationUnit = CoordinateFormat.MGRS;
    protected UnitConverter.FORMAT _slopeUnit = UnitConverter.FORMAT.GRADE;
    private GeoPoint _seekerGeopoint = GeoPoint.ZERO_POINT;
    protected double _seekerSlope = 0;
    protected SharedPreferences _prefs;

    public SeekerBarPanelPresenter(MapView mapView) {
        _context = mapView.getContext();
    }

    public void bind(SeekerBarPanelView v) {
        _seekerBarPanelView = v;
        _prefs = PreferenceManager.getDefaultSharedPreferences(v.getContext());
        _seekerBarPanelView.getMgrsText().setOnClickListener(this);
        _seekerBarPanelView.getSlopeText().setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

        // Toggle location units between MGRS and DMS
        if (v == _seekerBarPanelView.getMgrsText()) {
            _locationUnit = _locationUnit.equals(CoordinateFormat.MGRS)
                    ? CoordinateFormat.DMS
                    : CoordinateFormat.MGRS;
            updateMgrsText(_seekerGeopoint);
        }

        // Toggle slope units
        else if (v == _seekerBarPanelView.getSlopeText()) {
            _slopeUnit = _slopeUnit.equals(UnitConverter.FORMAT.GRADE)
                    ? UnitConverter.FORMAT.DEGREE
                    : UnitConverter.FORMAT.GRADE;
            updateSlopeText(_seekerSlope);
        }
    }

    public synchronized void updateMgrsText(final GeoPoint point) {
        _seekerGeopoint = point;
        _seekerBarPanelView.post(new Runnable() {
            @Override
            public void run() {
                _seekerBarPanelView.getMgrsText().setText(
                        CoordinateFormatUtilities.formatToShortString(point,
                                _locationUnit));
            }

        });
    }

    public synchronized void updateMslText(final GeoPoint alt) {
        final int altFmt = Integer.parseInt(_prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));

        final String altDisplayPref = _prefs.getString("alt_display_pref",
                "MSL");

        _seekerBarPanelView.post(new Runnable() {
            @Override
            public void run() {
                double altConverted;
                if (altDisplayPref.equals("MSL")) {
                    altConverted = EGM96.getMSL(alt);
                } else {
                    altConverted = EGM96.getHAE(alt);
                }

                double altValue;
                Span altSpan;
                if (altFmt == Span.ENGLISH) {
                    altValue = altConverted
                            * ConversionFactors.METERS_TO_FEET;
                    altSpan = Span.FOOT;
                } else {
                    altValue = altConverted;
                    altSpan = Span.METER;
                }

                if (GeoPoint.isAltitudeValid(alt.getAltitude())) {
                    _seekerBarPanelView.getMslText().setText(
                            SpanUtilities.format(altValue,
                                    altSpan, 0)
                                    + " "
                                    + altDisplayPref);
                } else {
                    _seekerBarPanelView.getMslText().setText(R.string.ft_msl2);
                }
            }

        });
    }

    public synchronized void updateGainText(final double feet) {

        final int altFmt = Integer.parseInt(_prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));

        _seekerBarPanelView.post(new Runnable() {
            @Override
            public void run() {
                double value;
                Span valueSpan;
                if (altFmt == Span.METRIC) {
                    valueSpan = Span.METER;
                    value = SpanUtilities.convert(feet, Span.FOOT, Span.METER);
                } else {
                    value = feet;
                    valueSpan = Span.FOOT;
                }
                String text = _context.getString(R.string.gain);
                if (value < 0) {
                    text = _context.getString(R.string.loss_1);
                }
                value = Math.abs(value);

                _seekerBarPanelView.getGainText()
                        .setText(text + " " +
                                SpanUtilities.format(value, valueSpan, 0));
            }
        });
    }

    public synchronized void updateSlopeText(final double slope) {
        _seekerSlope = slope;
        _seekerBarPanelView.post(new Runnable() {
            @Override
            public void run() {
                _seekerBarPanelView.getSlopeText().setText(_context.getString(
                        R.string.slope)
                        + UnitConverter.formatToString(slope,
                                UnitConverter.FORMAT.SLOPE, _slopeUnit));
            }

        });
    }

    public synchronized void updateControlName(final String s) {
        _seekerBarPanelView.post(new Runnable() {
            @Override
            public void run() {
                _seekerBarPanelView.getCpText().setText(s);
            }
        });
    }
}
