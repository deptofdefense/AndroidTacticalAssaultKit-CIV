
package com.atakmap.android.util;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.TimePicker;

import com.atakmap.app.R;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Calendar;
import java.util.TimeZone;

public class TimePickerFragment extends DialogFragment
        implements TimePickerDialog.OnTimeSetListener {

    private static final String TAG = "TimePickerFragment";

    private TimePickerDialog dialog;
    private TimePickerListener listener;
    private boolean is24hourDisplay;
    private long initialTime;
    private TimeZone timeZone;

    /**
     * Interface for the listener for the hours and minute results from the time
     * picker.
     */
    public interface TimePickerListener {
        /**
         * Returns the valid hours and minutes for the picker or will not be called if
         * the Time Picker is cancelled
         * @param hourOfDay the hours of the day [0,23]
         * @param minute the minutes of the day [0,59]
         */
        void onTimePicked(int hourOfDay, int minute);
    }

    /**
     * Initialize a time picker.
     * @param initialTime the initial time to be used for display in millis since epoch
     * @param listener the listener to call when the time is selected, or is not called if
     *                 the dialog is cancelled
     * @param is24hourDisplay is the time picker displayed in 24 hour time or 12 hour time
     */
    public void init(long initialTime, TimePickerListener listener,
            boolean is24hourDisplay) {
        init(initialTime, listener, is24hourDisplay,
                TimeZone.getTimeZone("UTC"));
    }

    /**
     * Initialize a time picker.
     * @param initialTime the initial time to be used for display in millis since epoch
     * @param listener the listener to call when the time is selected, or is not called if
     *                 the dialog is cancelled
     * @param is24hourDisplay is the time picker displayed in 24 hour time or 12 hour time
     * @param timeZone the time zone for display purposes
     */
    public void init(long initialTime, TimePickerListener listener,
            boolean is24hourDisplay,
            TimeZone timeZone) {
        this.initialTime = initialTime;
        this.listener = listener;
        this.is24hourDisplay = is24hourDisplay;
        this.timeZone = timeZone;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current time as the default values for the picker
        final Calendar cal = Calendar.getInstance();
        cal.setTimeZone(timeZone);

        // assume the initial time is always greater or equal than epoch
        if (initialTime >= 0) {
            cal.setTimeInMillis(initialTime);
        } else {
            cal.setTimeInMillis(CoordinatedTime.currentTimeMillis());
        }

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // Create a new instance of TimePickerDialog and return it
        dialog = new TimePickerDialog(getActivity(), this, hour, minute,
                is24hourDisplay);
        dialog.setIcon(R.drawable.ic_track_search);
        //        if (_is24hourDisplay) {
        //            dialog.setTitle(String.format(LocaleUtil.getCurrent(), "%02d:%02d", hour, minute));
        //        }
        return dialog;
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        if (listener != null) {
            listener.onTimePicked(hourOfDay, minute);
        }

    }
}
