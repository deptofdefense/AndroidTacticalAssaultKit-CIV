
package com.atakmap.android.gui;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.SimpleItemSelectedListener;

import android.graphics.Color;
import android.widget.TextView;

import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;

public class RangeBearingInputViewPilots extends RangeBearingInputView {

    public static final String TAG = "RangeBearingInputViewPilots";

    private EditText rangeText, bearingText;
    private Spinner rangeUnitSpinner;
    private int selectedUnits = 0;

    private enum DistCategory {
        NM(0, "nm"),
        MILES(1, "miles"),
        KM(2, "km"),
        METERS(3, "meters"),
        FEET(4, "feet"),
        YARDS(5, "yards");

        private final int _value;
        private final String _name;

        DistCategory(int value, String name) {
            _value = value;
            _name = name;
        }

        public int getValue() {
            return _value;
        }

        public String getName() {
            return _name;
        }

        @Override
        public String toString() {
            return _name;
        }
    }

    public RangeBearingInputViewPilots(Context context) {
        super(context);
        this.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    public RangeBearingInputViewPilots(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    @Override
    public Double getRange() {
        try {
            double range = Double.parseDouble(rangeText.getText().toString());

            if (selectedUnits == DistCategory.METERS.getValue()) {
                return range;
            } else if (selectedUnits == DistCategory.FEET.getValue()) {
                return range * ConversionFactors.FEET_TO_METERS;
            } else if (selectedUnits == DistCategory.YARDS.getValue()) {
                return range * ConversionFactors.YARDS_TO_METERS;
            } else if (selectedUnits == DistCategory.KM.getValue()) {
                return range * ConversionFactors.KM_TO_METERS;
            } else if (selectedUnits == DistCategory.MILES.getValue()) {
                return range * ConversionFactors.MILES_TO_METERS;
            } else if (selectedUnits == DistCategory.NM.getValue()) {
                return range * ConversionFactors.NM_TO_METERS;
            } else {
                return null;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), R.string.invalid_range,
                    Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void init() {
        rangeText = findViewById(R.id.range_input);
        bearingText = findViewById(R.id.bearing_input);

        rangeUnitSpinner = findViewById(R.id.range_type_spin);

        final ArrayAdapter<DistCategory> unitsAdapter = new ArrayAdapter<>(
                this.getContext(),
                android.R.layout.simple_spinner_item,
                DistCategory.values());

        unitsAdapter
                .setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item);
        rangeUnitSpinner.setAdapter(unitsAdapter);

        MapView mapView = MapView.getMapView();
        if (mapView != null) {
            String units = PreferenceManager.getDefaultSharedPreferences(
                    mapView.getContext()).getString("rab_rng_units_pref",
                            String.valueOf(Span.METRIC));
            if (units.equals(String.valueOf(Span.METRIC)))
                selectedUnits = DistCategory.METERS.getValue();
            else if (units.equals(String.valueOf(Span.ENGLISH)))
                selectedUnits = DistCategory.FEET.getValue();
            else if (units.equals(String.valueOf(Span.NM)))
                selectedUnits = DistCategory.NM.getValue();
        }

        rangeUnitSpinner
                .setOnItemSelectedListener(new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parentView,
                            View selectedItemView,
                            int position, long id) {

                        if (selectedItemView instanceof TextView)
                            ((TextView) selectedItemView)
                                    .setTextColor(Color.WHITE);
                        selectedUnits = position;
                    }
                });
        rangeUnitSpinner.setSelection(selectedUnits);
    }

}
