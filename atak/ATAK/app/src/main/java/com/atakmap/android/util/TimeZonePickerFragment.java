
package com.atakmap.android.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import com.atakmap.app.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.AdapterView;

public class TimeZonePickerFragment extends DialogFragment
        implements DialogInterface.OnClickListener {

    private final String[] _availableZoneIds = getOrganizedTimeZones();

    private TimeZonePickerListener listener;
    private TimeZone timeZone;

    /**
     * Interface for the listener for an instance of the time zone dialog results.
     */
    public interface TimeZonePickerListener {
        /**
         * Returns the valid timezone when picked or will not be called if the dialog
         * is cancelled.
         * @param timeZone the timezone picked
         */
        void onTimeZonePicked(TimeZone timeZone);
    }

    /**
     * Initialize the time zone picker with the time zone and a listener.
     * @param timeZone the timezone to set as the default
     * @param listener the listener for the result of the time zone pick
     */
    public void init(TimeZone timeZone, TimeZonePickerListener listener) {
        this.listener = listener;
        this.timeZone = timeZone;
    }

    private static String[] getOrganizedTimeZones() {
        // place utc and the current local timezone at the top
        String[] availableTimeZones = TimeZone.getAvailableIDs();

        List<String> retval = new ArrayList<>();
        retval.add(TimeZone.getTimeZone("UTC").getID());
        retval.add(TimeZone.getDefault().getID());

        for (String id : availableTimeZones) {
            if (!id.startsWith("Etc")) {
                retval.add(id);
            }
        }
        return retval.toArray(new String[0]);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        int selectedItem = Arrays.asList(_availableZoneIds)
                .indexOf(timeZone.getID());

        // should probably have a search function instead of just a long picker
        // but for now I have added the two most commonly used ones to the top of
        // this picklist - UTC and LOCAL

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.select_time_zone)
                .setPositiveButton(R.string.ok, this)
                .setNegativeButton(R.string.cancel, null)
                .setSingleChoiceItems(_availableZoneIds, selectedItem, null)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (listener != null) {
            int pos = ((AlertDialog) dialog).getListView()
                    .getCheckedItemPosition();
            if (pos != AdapterView.INVALID_POSITION) {
                listener.onTimeZonePicked(
                        TimeZone.getTimeZone(_availableZoneIds[pos]));
            }
        }
    }
}
