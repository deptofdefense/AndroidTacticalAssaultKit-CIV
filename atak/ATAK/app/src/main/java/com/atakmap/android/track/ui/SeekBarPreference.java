
package com.atakmap.android.track.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atakmap.app.R;
//import android.content.SharedPreferences;
//import android.view.View.OnClickListener;
//import android.widget.Button;

public class SeekBarPreference extends DialogPreference implements
        SeekBar.OnSeekBarChangeListener,
        DialogInterface.OnClickListener {

    private final Context context;
    private int value;
    private final int max;

    private TextView numCrmbsTV;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        String androidns = context
                .getString(R.string.SeekBarPreference_androidns);
        value = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
        max = attrs.getAttributeIntValue(androidns, "max", 100);

    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.seekbar_pref_layout, null);

        SeekBar numCrmbsSB = view.findViewById(R.id.seekBarSB);
        //TextView numCrmbsDescTV = (TextView) view.findViewById(R.id.descTV);
        numCrmbsTV = view.findViewById(R.id.numberTV);
        // Button posB = (Button) view.findViewById(R.id.posB);
        // Button negB = (Button) view.findViewById(R.id.negB);

        //numCrmbsDescTV.setText(text);

        numCrmbsSB.setMax(max);

        // if//set persisted value if it exists
        value = this.getPersistedInt(value);
        numCrmbsSB.setProgress(value);

        numCrmbsSB.setOnSeekBarChangeListener(this);
        if (value < 1) {
            value = 1;
        }
        numCrmbsTV.setText("" + value);

        return view;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            super.setLayoutResource(
                    R.layout.preference_layout_switch_prefs_grey_out);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        value = progress;
        if (progress < 1) {
            progress = 1;
        }
        numCrmbsTV.setText("" + progress);
        numCrmbsTV.invalidate();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            if (shouldPersist()) {
                persistInt(value);
            }
            super.onClick(dialog, which);
        }
    }

}
