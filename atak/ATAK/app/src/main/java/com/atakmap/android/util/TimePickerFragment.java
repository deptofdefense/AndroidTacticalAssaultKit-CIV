
package com.atakmap.android.util;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.TimePicker;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 *
 */
public class TimePickerFragment extends DialogFragment
        implements TimePickerDialog.OnTimeSetListener {

    private static final String TAG = "TimePickerFragment";

    private TimePickerDialog dialog;
    private TimePickerListener _listener;
    private boolean _is24hourDisplay;
    private long _initialTime;

    public interface TimePickerListener {
        void onTimePicked(int hourOfDay, int minute);
    }

    public void init(long initialTime, TimePickerListener listener,
            boolean is24hourDisplay) {
        _initialTime = initialTime;
        _listener = listener;
        _is24hourDisplay = is24hourDisplay;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current time as the default values for the picker
        final Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        if (_initialTime >= 0)
            cal.setTime(new Date(_initialTime));
        else
            cal.setTime(new Date());

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // Create a new instance of TimePickerDialog and return it
        dialog = new TimePickerDialog(getActivity(), this, hour, minute,
                _is24hourDisplay);
        dialog.setIcon(R.drawable.ic_track_search);
        //        if (_is24hourDisplay) {
        //            dialog.setTitle(String.format(LocaleUtil.getCurrent(), "%02d:%02d", hour, minute));
        //        }
        return dialog;
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        Log.d(TAG, "onTimeSet");

        //        if (dialog != null){
        //            if (_is24hourDisplay) {
        //                dialog.setTitle(String.format(LocaleUtil.getCurrent(), "%02d:%02d", hourOfDay, minute));
        //            }
        //        }

        if (_listener != null) {
            _listener.onTimePicked(hourOfDay, minute);
        } else {
            Log.w(TAG, "No listener set for Time Picker");
        }

    }
}
