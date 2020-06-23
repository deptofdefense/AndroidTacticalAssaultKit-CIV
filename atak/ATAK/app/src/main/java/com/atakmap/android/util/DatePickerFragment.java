
package com.atakmap.android.util;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.widget.DatePicker;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DatePickerFragment extends DialogFragment
        implements DatePickerDialog.OnDateSetListener {

    private static final String TAG = "DatePickerFragment";

    private DatePickerDialog dialog;
    private DatePickerListener _listener;
    private long _initialTime;
    private long _minDate;
    private long _maxDate;

    public interface DatePickerListener {
        void onDatePicked(int year, int month, int dayOfMonth);
    }

    public void init(long initialTime, DatePickerListener listener,
            long minDate, long maxDate) {
        _initialTime = initialTime;
        _listener = listener;
        _minDate = minDate;
        _maxDate = maxDate;
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

        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);

        dialog = new DatePickerDialog(getActivity(), this, year, month,
                dayOfMonth);
        if (_minDate > 0)
            dialog.getDatePicker().setMinDate(_minDate);
        if (_maxDate > 0)
            dialog.getDatePicker().setMaxDate(_maxDate);
        dialog.setIcon(R.drawable.ic_track_search);
        return dialog;
    }

    @Override
    public void onDateSet(DatePicker datePicker, int y, int m, int dom) {
        Log.d(TAG, "onDateSet");

        if (_listener == null) {
            Log.w(TAG, "No listener set for Time Picker");
        } else {
            _listener.onDatePicked(y, m, dom);
        }
    }
}
