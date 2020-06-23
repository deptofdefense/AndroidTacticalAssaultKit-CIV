
package com.atakmap.android.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import android.graphics.Color;
import android.widget.TextView;

import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;

public class RangeBearingInputView extends LinearLayout {

    public static final String TAG = "RangeBearingInputView";

    private enum DistCatagory {
        METERS(0, "meters"),
        FEET(1, "feet"),
        YARDS(2, "yards"),
        KM(3, "km"),
        MILES(4, "miles"),
        NM(5, "nm");

        private final int _value;
        private final String _name;

        DistCatagory(int value, String name) {
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

    public RangeBearingInputView(Context context) {
        super(context);
        this.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    public RangeBearingInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    public Double getRange() {
        try {
            double range = Double.parseDouble(rangeText.getText().toString());

            if (selectedUnits == DistCatagory.METERS.getValue()) {
                return range;
            } else if (selectedUnits == DistCatagory.FEET.getValue()) {
                return range * ConversionFactors.FEET_TO_METERS;
            } else if (selectedUnits == DistCatagory.YARDS.getValue()) {
                return range * ConversionFactors.YARDS_TO_METERS;
            } else if (selectedUnits == DistCatagory.KM.getValue()) {
                return range * ConversionFactors.KM_TO_METERS;
            } else if (selectedUnits == DistCatagory.MILES.getValue()) {
                return range * ConversionFactors.MILES_TO_METERS;
            } else if (selectedUnits == DistCatagory.NM.getValue()) {
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

    public Double getBearing() {
        try {
            return Double.parseDouble(bearingText.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), R.string.invalid_bearing2,
                    Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void init() {

        rangeText = findViewById(R.id.range_input);
        bearingText = findViewById(R.id.bearing_input);

        rangeUnitSpinner = findViewById(R.id.range_type_spin);

        final ArrayAdapter<DistCatagory> unitsAdapter = new ArrayAdapter<>(
                this.getContext(),
                android.R.layout.simple_spinner_item,
                DistCatagory.values());

        unitsAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        rangeUnitSpinner.setAdapter(unitsAdapter);

        rangeUnitSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parentView,
                            View selectedItemView,
                            int position, long id) {

                        if (selectedItemView instanceof TextView)
                            ((TextView) selectedItemView)
                                    .setTextColor(Color.WHITE);

                        selectedUnits = position;
                        // Log.v(TAG,"Position: " + position);
                    }

                });

    }

    private EditText rangeText, bearingText;
    private Spinner rangeUnitSpinner;
    private int selectedUnits = 0;
}
