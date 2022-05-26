
package com.atakmap.android.routes.elevation;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.elevation.model.UnitConverter;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.android.viewshed.ViewshedDropDownReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class AnalysisPanelPresenter implements View.OnClickListener {
    public static final String PREFERENCE_SHOW_VIEWSHED = "ElevationProfileShowViewshed";
    public static final String PREFERENCE_PROFILE_VIEWSHED_ALT = "ElevationProfileViewshedAlt";
    public static final String PREFERENCE_PROFILE_VIEWSHED_RADIUS = "ElevationProfileViewshedRadius";
    public static final String PREFERENCE_PROFILE_VIEWSHED_CIRCLE = "ElevationProfileViewshedCircle";
    public static final String PREFERENCE_PROFILE_VIEWSHED_OPACITY = "ElevationProfileViewshedOpacity";

    private AnalysisPanelView _analysisPanelView;
    protected View _viewshedDetails;
    protected CheckBox _showViewshedCB;
    protected SharedPreferences prefs = null;
    private double _totalDistance = 0;
    private double _maxSlope = 0;
    private boolean _isOpen = true;
    private Drawable _arrowOpen;
    private Drawable _arrowClosed;
    private SharedPreferences _prefs;

    protected int intensity = 50;

    protected final LimitingThread intensityLT;

    // private int _totalDistanceUnit = Span.ENGLISH;
    private UnitConverter.FORMAT _maxSlopeUnit = UnitConverter.FORMAT.GRADE;

    public AnalysisPanelPresenter() {
        intensityLT = new LimitingThread("viewshedopacity",
                new Runnable() {
                    @Override
                    public void run() {
                        //set the viewshed transparency
                        prefs.edit()
                                .putInt(
                                        PREFERENCE_PROFILE_VIEWSHED_OPACITY,
                                        intensity)
                                .apply();

                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                        }
                    }
                });
    }

    public void bind(AnalysisPanelView v, final MapView mapView) {
        _analysisPanelView = v;
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(_analysisPanelView.getContext());
        _analysisPanelView.getTotalDistText().setOnClickListener(this);
        _analysisPanelView.getMaxSlopeText().setOnClickListener(this);
        _analysisPanelView.getToggleView().setOnClickListener(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());

        View viewshedView = _analysisPanelView.getViewshedView();
        if (viewshedView instanceof ViewGroup)
            GenericDetailsView.addEditTextPrompts((ViewGroup) viewshedView);
        _showViewshedCB = viewshedView.findViewById(
                R.id.showViewshed_cb);
        final TextView altitudeET = viewshedView
                .findViewById(R.id.altitude_et);
        final int heightAboveMarker = prefs.getInt(
                PREFERENCE_PROFILE_VIEWSHED_ALT, 6);
        altitudeET.setText(Integer.toString(heightAboveMarker));

        altitudeET.addTextChangedListener(getAltitudeTextWatcher(altitudeET));

        final EditText radiusET = viewshedView
                .findViewById(R.id.radius_et);
        radiusET.setText(String.valueOf(prefs.getInt(
                PREFERENCE_PROFILE_VIEWSHED_RADIUS, 7000)));
        radiusET.addTextChangedListener(
                getRadiusTextWatcher(mapView, radiusET));

        CheckBox circleCB = viewshedView
                .findViewById(R.id.circularViewshed_cb);

        //boolean circlePref = prefs.getBoolean(
        //        PREFERENCE_PROFILE_VIEWSHED_CIRCLE, false);

        boolean circlePref = true; // hard code this always

        circleCB.setChecked(circlePref);
        circleCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                prefs.edit()
                        .putBoolean(
                                PREFERENCE_PROFILE_VIEWSHED_CIRCLE,
                                isChecked)
                        .apply();
            }
        });

        intensity = prefs.getInt(
                PREFERENCE_PROFILE_VIEWSHED_OPACITY, 50);
        SeekBar transSeek = viewshedView
                .findViewById(R.id.intensity_seek);
        transSeek.setProgress(intensity);
        transSeek.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                //there is a bug when the alpha is set to 100, so to avoid it set 99 as the max
                if (progress == 100)
                    progress = 99;
                intensity = progress;

                //update the percentage editText
                intensityLT.exec();
            }
        });

        _viewshedDetails = viewshedView.findViewById(
                R.id.viewshedDetailsLayout);
        _showViewshedCB.setOnClickListener(this);
        if (prefs.getBoolean(PREFERENCE_SHOW_VIEWSHED, false)) {
            _showViewshedCB.setChecked(true);
            _viewshedDetails.setVisibility(View.VISIBLE);
        }
        Resources resources = _analysisPanelView.getResources();
        _arrowOpen = resources.getDrawable(R.drawable.arrowright);
        _arrowClosed = resources.getDrawable(R.drawable.arrowleft);

    }

    protected AfterTextChangedWatcher getRadiusTextWatcher(MapView mapView,
            EditText radiusET) {
        return new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (radiusET.getText().length() == 0)
                    return;
                int radius;
                try {
                    radius = Integer.parseInt(radiusET.getText().toString());
                } catch (Exception e) {
                    Toast.makeText(
                            mapView.getContext(),
                            mapView.getContext().getString(
                                    R.string.radius_num_warn),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (radius < 1
                        || radius > ViewshedDropDownReceiver.MAX_RADIUS) {
                    Toast.makeText(
                            mapView.getContext(),
                            mapView.getContext().getString(
                                    R.string.radius_1_to_40000),
                            Toast.LENGTH_SHORT).show();
                    s.clear();
                    if (radius > ViewshedDropDownReceiver.MAX_RADIUS) {
                        s.append(Integer
                                .toString(ViewshedDropDownReceiver.MAX_RADIUS));
                    }
                    return;
                }

                prefs.edit().putInt(
                        PREFERENCE_PROFILE_VIEWSHED_RADIUS,
                        radius).apply();

            }
        };
    }

    protected AfterTextChangedWatcher getAltitudeTextWatcher(
            TextView altitudeET) {
        return new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (altitudeET.getText().length() > 0) {
                    int heightAboveGround;
                    try {
                        heightAboveGround = Integer.parseInt(altitudeET
                                .getText()
                                .toString());
                    } catch (Exception e) {
                        return;
                    }

                    heightAboveGround = (int) Math.round(heightAboveGround
                            * ConversionFactors.FEET_TO_METERS);
                    prefs.edit().putInt(
                            PREFERENCE_PROFILE_VIEWSHED_ALT,
                            heightAboveGround).apply();
                }
            }
        };
    }

    private void _closeView() {
        synchronized (this) {
            _isOpen = false;
            _analysisPanelView.getToggleImage().setImageDrawable(_arrowClosed);
            _analysisPanelView.setVisibility(View.GONE);
        }
    }

    private synchronized void _openView() {
        synchronized (this) {
            _isOpen = true;
            _analysisPanelView.getToggleImage().setImageDrawable(_arrowOpen);
            _analysisPanelView.setVisibility(View.VISIBLE);
        }
    }

    public void updateTotalDist(final double feet) {
        _analysisPanelView.post(new Runnable() {
            @Override
            public void run() {
                int rangeFmt = Integer
                        .parseInt(_prefs.getString("rab_rng_units_pref",
                                String.valueOf(Span.METRIC)));
                Span rangeUnits = Span.METER;
                if (rangeFmt == Span.METRIC) {
                    rangeUnits = Span.METER;
                } else if (rangeFmt == Span.ENGLISH) {
                    rangeUnits = Span.MILE;
                } else if (rangeFmt == Span.NM) {
                    rangeUnits = Span.NAUTICALMILE;
                }
                _totalDistance = SpanUtilities.convert(feet, Span.FOOT,
                        rangeUnits);
                _analysisPanelView.getTotalDistText()
                        .setText(
                                SpanUtilities.formatType(rangeFmt,
                                        _totalDistance,
                                        rangeUnits));

            }
        });
    }

    @Override
    public void onClick(View v) {
        if (_analysisPanelView == null)
            return;
        // Total distance units toggle
        if (v == _analysisPanelView.getTotalDistText()) {
            // if(_totalDistanceUnit == Span.ENGLISH) {
            // _totalDistanceUnit = Span.METRIC;
            // } else {
            // _totalDistanceUnit = Span.ENGLISH;
            // }
            // updateTotalDist(_totalDistance);
            // ((RouteElevationPresenter)
            // Presenter.getInstance(RouteElevationPresenter.class)).onRouteElevationChartClick(v);
        }

        // Max slope units toggle
        else if (v == _analysisPanelView.getMaxSlopeText()) {
            if (_maxSlopeUnit.equals(UnitConverter.FORMAT.GRADE)) {
                _maxSlopeUnit = UnitConverter.FORMAT.DEGREE;
            } else {
                _maxSlopeUnit = UnitConverter.FORMAT.GRADE;
            }
            updateMaxSlope(_maxSlope);
        }

        // Toggle right-side view
        else if (v == _analysisPanelView.getToggleView()) {
            if (_isOpen) {
                _closeView();
            } else {
                _openView();
            }
        }

        // Toggle viewshed
        else if (v == _showViewshedCB) {
            prefs.edit().putBoolean(PREFERENCE_SHOW_VIEWSHED,
                    _showViewshedCB.isChecked()).apply();
            if (_showViewshedCB.isChecked())
                _viewshedDetails.setVisibility(View.VISIBLE);
            else
                _viewshedDetails.setVisibility(View.GONE);
        }
    }

    public void updateMaxAlt(final GeoPointMetaData alt) {
        _analysisPanelView.post(new Runnable() {
            @Override
            public void run() {
                _analysisPanelView.getMaxAltText().setText(
                        AltitudeUtilities.format(alt.get(), prefs));
            }
        });
    }

    public void updateMinAlt(final GeoPointMetaData alt) {
        _analysisPanelView.post(new Runnable() {
            @Override
            public void run() {
                _analysisPanelView.getMinAltText().setText(
                        AltitudeUtilities.format(alt.get(), prefs));
            }
        });
    }

    public void updateTotalGain(final double feet) {

        final int altFmt = Integer.parseInt(prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));

        _analysisPanelView.post(new Runnable() {
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
                _analysisPanelView.getTotalGainText().setText(
                        SpanUtilities.format(value, valueSpan, 0));
            }
        });
    }

    public void updateTotalLoss(final double feet) {
        final int altFmt = Integer.parseInt(prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));

        _analysisPanelView.post(new Runnable() {
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
                _analysisPanelView.getTotalLossText().setText(
                        SpanUtilities.format(value, valueSpan, 0));
            }
        });
    }

    public void updateMaxSlope(final double slope) {
        _maxSlope = slope;
        _analysisPanelView.post(new Runnable() {
            @Override
            public void run() {
                _analysisPanelView.getMaxSlopeText().setText(
                        UnitConverter.formatToString(slope,
                                UnitConverter.FORMAT.SLOPE,
                                _maxSlopeUnit));
            }
        });
    }
}
