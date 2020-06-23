
package com.atakmap.android.radiolibrary;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class Rover implements RoverDropDownReceiver.RadioStatusListener {
    private final MapView mapView;
    private final Context con;
    public static final String TAG = "Rover";
    private final View _layout;

    private final Switch roverSwitch;
    private final TextView roverState;
    private final TextView roverState2;
    private final RoverDropDownReceiver rddr;

    public Rover(final MapView mapView) {
        this.mapView = mapView;
        con = mapView.getContext();
        LayoutInflater inf = LayoutInflater.from(con);
        _layout = inf.inflate(R.layout.radio_item_rover, null);

        rddr = new RoverDropDownReceiver(mapView);
        RoverDropDownReceiver.addRadioStatusListener(this);

        roverState = _layout.findViewById(R.id.rover_sub_tv);
        roverState2 = _layout.findViewById(R.id.rover_sub2_tv);
        // set rover connected or disconnected text

        roverSwitch = _layout.findViewById(R.id.rover_switch);

        _layout.findViewById(R.id.rover_cfg_btn).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rddr.run("com.atakmap.radiocontrol.ROVER_SHOW_CONFIG");
                    }
                });

        // set initial state
        roverSwitch.setChecked(false);

        // set switch listener
        roverSwitch
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(
                            CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            Toast.makeText(
                                    con,
                                    con.getString(R.string.rover)
                                            + con.getString(
                                                    R.string.radio_coldstart_till_ready),
                                    Toast.LENGTH_SHORT).show();

                            Log.d(TAG, "starting rover interactions");
                            rddr.run(
                                    "com.atakmap.radiocontrol.ROVER_CONTROL_START");
                        } else {
                            Log.d(TAG, "stopping rover interactions");
                            rddr.run(
                                    "com.atakmap.radiocontrol.ROVER_CONTROL_STOP");
                            setRoverStatusText(Color.WHITE, "", "");
                        }
                    }
                });

        _layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                rddr.run("com.atakmap.radiocontrol.ROVER_CONTROL");
            }
        });

        _layout.findViewById(R.id.roverArrow)
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rddr.run("com.atakmap.radiocontrol.ROVER_CONTROL");
                    }
                });

    }

    void roverStatusChanged(int color, String status) {
        roverStatusChanged(color, status, "");
    }

    @Override
    public void roverStatusChanged(int color, String status, String subtext) {
        if (!roverSwitch.isChecked()) {
            Log.d(TAG, "status message received, but rover control is off");
            return;
        }
        setRoverStatusText(color, status.replace("\n", " "),
                subtext.replace("\n", " "));
    }

    private void setRoverStatusText(final int color, final String text,
            final String subtext) {
        mapView.post(new Runnable() {
            @Override
            public void run() {
                roverState.setTextColor(color);
                roverState.setText(text);
                roverState2.setTextColor(color);
                roverState2.setText(subtext);
            }
        });
    }

    public View getView() {
        return _layout;
    }

    public void dispose() {
        rddr.dispose();
    }

}
