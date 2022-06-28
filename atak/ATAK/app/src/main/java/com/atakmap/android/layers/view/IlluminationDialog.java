
package com.atakmap.android.layers.view;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.android.layers.overlay.IlluminationListItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.util.DatePickerFragment;
import com.atakmap.android.util.DatePickerFragment.DatePickerListener;
import com.atakmap.android.util.TimePickerFragment;
import com.atakmap.android.util.TimePickerFragment.TimePickerListener;
import com.atakmap.android.util.TimeZonePickerFragment;
import com.atakmap.android.util.TimeZonePickerFragment.TimeZonePickerListener;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

/**
 * Dialog for configuring Illumination
 */
public class IlluminationDialog
        implements TimeZonePickerListener, DatePickerListener,
        TimePickerListener {

    private static final String PREF_KEY_DISPLAY_TIMEZONE = "illumination_dialog_display_timezone";

    /**
     * Callback when the date time is set.
     */
    public interface Callback {
        /**
         * Returns the selected simulation date and time for the illumination.
         * @param time the selected simulation date time.
         * @param continuous ignore the current time setting and just track the time on the
         *                   device.
         */
        void onSetIlluminationSettings(long time, boolean continuous);
    }

    private final MapView mapView;
    private final Context context;
    private final AtakPreferences preferences;

    private final CoordinatedTime.SimpleDateFormatThread dateFormatter = new CoordinatedTime.SimpleDateFormatThread(
            "dd MMM yyyy", Locale.getDefault());

    private final CoordinatedTime.SimpleDateFormatThread timeFormatter = new CoordinatedTime.SimpleDateFormatThread(
            "HH:mm",
            Locale.getDefault());
    private final CoordinatedTime.SimpleDateFormatThread timeZoneFormatter = new CoordinatedTime.SimpleDateFormatThread(
            "'UTC'Z",
            Locale.getDefault());
    private String title;
    private Button dateButton;
    private Button timeButton;
    private Button timezoneButton;
    private Callback callback;
    private CheckBox manualTimeEntry;
    private Button useCurrentTimeIB;

    private final Calendar time = Calendar
            .getInstance(TimeZone.getTimeZone("UTC"));

    public IlluminationDialog(MapView mapView) {
        this.mapView = mapView;

        context = mapView.getContext();
        preferences = new AtakPreferences(mapView);

    }

    /**
     * Sets the title of the IlluminationDialog
     * @param title the title to be used on the dialog
     * @return the IlluminationDialog for command chaining
     */
    public IlluminationDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the date time of the IlluminationDialog
     * @param time the time to use based on millis since epoch
     * @return the IlluminationDialog for command chaining
     */
    public IlluminationDialog setTime(long time) {
        this.time.setTimeInMillis(time);
        return this;
    }

    /**
     * Sets the callback for the IlluminationDialog
     * @param cb the callback to use
     * @return the IlluminationDialog for command chaining
     */
    public IlluminationDialog setCallback(Callback cb) {
        callback = cb;
        return this;
    }

    @Override
    public void onTimeZonePicked(TimeZone timeZone) {
        setDisplayTimeZone(timeZone);
        updateView();
    }

    @Override
    public void onDatePicked(int year, int month, int dayOfMonth) {
        time.set(Calendar.YEAR, year);
        time.set(Calendar.MONTH, month);
        time.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateView();
    }

    @Override
    public void onTimePicked(int hourOfDay, int minute) {
        Calendar displayCalendar = Calendar.getInstance(getDisplayTimeZone());
        displayCalendar.setTime(time.getTime());
        displayCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        displayCalendar.set(Calendar.MINUTE, minute);
        time.setTime(displayCalendar.getTime());
        updateView();
    }

    private void updateView() {
        dateFormatter.setTimeZone(getDisplayTimeZone());
        timeFormatter.setTimeZone(getDisplayTimeZone());
        timeZoneFormatter.setTimeZone(getDisplayTimeZone());

        timeButton.setText(timeFormatter.format(time.getTime()));
        dateButton.setText(dateFormatter.format(time.getTime()));

        // TODO: Determine if this should be a human readable timezone value or just
        // time offset
        timezoneButton.setText(timeZoneFormatter.format(time.getTime()));
    }

    private TimeZone getDisplayTimeZone() {
        return TimeZone.getTimeZone(
                preferences.get(PREF_KEY_DISPLAY_TIMEZONE,
                        TimeZone.getDefault().getID()));
    }

    private void setDisplayTimeZone(TimeZone timeZone) {
        preferences.set(PREF_KEY_DISPLAY_TIMEZONE, timeZone.getID());
    }

    public void show() {
        final View v = LayoutInflater.from(context).inflate(
                R.layout.illumination_dialog, mapView, false);

        timezoneButton = v.findViewById(R.id.timezone_button);

        dateButton = v.findViewById(R.id.pick_date_button);
        timeButton = v.findViewById(R.id.pick_time_button);
        manualTimeEntry = v.findViewById(R.id.manual_time_entry);
        manualTimeEntry.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                v.findViewById(R.id.manual_entry_screen)
                                        .setVisibility(isChecked ? View.VISIBLE
                                                : View.GONE);
                            }
                        });
                    }
                });
        manualTimeEntry.setChecked(!preferences.get(
                IlluminationListItem.PREF_KEY_CURRENT_TIME_LOCK,
                true));

        useCurrentTimeIB = v.findViewById(R.id.use_current_time_button);

        updateView();

        timezoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TimeZonePickerFragment startFragment = new TimeZonePickerFragment();
                startFragment.init(getDisplayTimeZone(),
                        IlluminationDialog.this);
                startFragment.show(((Activity) context).getFragmentManager(),
                        "startTimeZonePicker");
            }
        });
        dateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DatePickerFragment startFragment = new DatePickerFragment();
                startFragment.init(time.getTimeInMillis(),
                        IlluminationDialog.this,
                        0, Long.MAX_VALUE, getDisplayTimeZone());
                startFragment.show(((Activity) context).getFragmentManager(),
                        "startDatePicker");
            }
        });
        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TimePickerFragment startFragment = new TimePickerFragment();
                startFragment.init(time.getTimeInMillis(),
                        IlluminationDialog.this,
                        true, getDisplayTimeZone());
                startFragment.show(((Activity) context).getFragmentManager(),
                        "startTimePicker");
            }
        });
        useCurrentTimeIB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                time.setTime(new Date(CoordinatedTime.currentTimeMillis()));
                IlluminationDialog.this.updateView();
            }
        });

        final AlertDialog.Builder b = new AlertDialog.Builder(context);
        if (title != null)
            b.setTitle(title);
        b.setView(v);
        b.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        // Notify callback
                        if (callback != null) {
                            callback.onSetIlluminationSettings(
                                    time.getTimeInMillis(),
                                    !manualTimeEntry.isChecked());
                        }
                    }
                });

        b.setNegativeButton(R.string.cancel, null);
        AlertDialog ad = b.show();
        AlertDialogHelper.adjustWidth(ad, .90);
    }

}
