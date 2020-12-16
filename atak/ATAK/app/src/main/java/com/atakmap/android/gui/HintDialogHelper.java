
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.Map;

public class HintDialogHelper {
    public static final String TAG = "HintDialogHelper";
    private static final String basename = "atak.hint.";

    public interface HintActions {
        void preHint();

        void postHint();
    }

    /**
     * Shows a hint that can be dismissed once, or permanently dismissed.
     * @param title title the title to show in the dialog.
     * @param message the message to display.
     * @param id a unique but human readable identifier. If null, the dialog will always be shown.
     */
    static public void showHint(final Context context, final String title,
            final String message, final String id) {
        TextView tv = new TextView(context);
        tv.setText(message);
        showHint(context, title, message, id, null);
    }

    /**
     * Shows a hint that can be dismissed once, or permanently dismissed.
     * @param title title the title to show in the dialog.
     * @param message the message to display.
     * @param id a unique but human readable identifier. If null, the dialog will always be shown.
     * @param action an action taken right before the dialog is displayed and right after the dialog is dismissed, 
     * can be null.
     */
    static public void showHint(final Context context, final String title,
            final String message, final String id, final HintActions action) {
        TextView tv = new TextView(context);
        tv.setText(message);
        showHint(context, title, tv, id, action);
    }

    /**
     * Shows a hint that can be dismissed once, or permanently dismissed.
     * @param title title the title to show in the dialog.
     * @param message the message to display.
     * @param id a unique but human readable identifier. If null, the dialog will always be shown.
     * @param action an action taken right before the dialog is displayed and right after the dialog is dismissed,
     * can be null.
     * @param onlyShowOnce only show once by default
     */
    static public void showHint(final Context context, final String title,
            final String message, final String id, final HintActions action,
            boolean onlyShowOnce) {
        TextView tv = new TextView(context);
        tv.setText(message);
        showHint(context, title, tv, id, action, onlyShowOnce);
    }

    /**
     * Shows a hint that can be dismissed once, or permanently dismissed.
     * @param title title the title to show in the dialog.
     * @param view the view to display.
     * @param id a unique but human readable identifier. If null, the dialog will always be shown.
     */
    static public void showHint(final Context context, final String title,
            final View view, final String id) {
        showHint(context, title, view, id, null);
    }

    /**
     * Shows a hint that can be dismissed once, or permanently dismissed.
     * @param title title the title to show in the dialog.
     * @param view the view to display.
     * @param id a unique but human readable identifier. If null, the dialog will always be shown.
     * @param action an action taken right before the dialog is displayed and right after the dialog is dismissed, 
     * can be null.
     */
    static public void showHint(final Context context, final String title,
            final View view, final String id, final HintActions action) {
        showHint(context, title, view, id, action, true);
    }

    /**
     * Shows a hint that can be dismissed once, or permanently dismissed.
     * @param title title the title to show in the dialog.
     * @param view the view to display.
     * @param id a unique but human readable identifier. If null, the dialog will always be shown.
     * @param action an action taken right before the dialog is displayed and right after the dialog is dismissed, 
     * can be null.
     * @param onlyShowOnce only show once by default
     */
    static public void showHint(final Context context, final String title,
            final View view, final String id, final HintActions action,
            final boolean onlyShowOnce) {

        final SharedPreferences _prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        boolean displayHint = true;

        if (id != null)
            displayHint = _prefs.getBoolean(basename + id, true);

        final View v = LayoutInflater.from(context)
                .inflate(R.layout.hint_dialog, null);

        final CheckBox cb = v.findViewById(R.id.showAgain);
        if (id != null) {
            cb.setChecked(onlyShowOnce);
        } else {
            cb.setVisibility(View.GONE);
            cb.setChecked(false);
        }

        if (displayHint) {

            if (MetricsApi.shouldRecordMetric()) {
                Bundle b = new Bundle();
                b.putString("id", id);
                b.putString("title", title);
                MetricsApi.record("hint", b);
            }

            if (action != null)
                action.preHint();

            final ViewGroup vg = v.findViewById(R.id.view);
            try {
                vg.addView(view);
            } catch (IllegalStateException ise) {
                // in this case where the view is already being shown
                // basically the hint is shown and then the hint it shown
                // again without it being closed.  
                // it is easier to catch the condition and not reshow the 
                // hint.
                Log.d(TAG, "view is already being shown in another hint", ise);
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setIcon(R.drawable.ic_dialog_hint)
                    .setCancelable(false)
                    .setView(v)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    vg.removeView(view);
                                    if (cb.isChecked())
                                        _prefs.edit()
                                                .putBoolean(basename + id,
                                                        false)
                                                .apply();
                                    if (action != null)
                                        action.postHint();
                                    dialog.dismiss();
                                }
                            });
            try {
                builder.show();
            } catch (Exception ignored) {
            }
        }

    }

    static public void resetHints(final Context context) {
        final SharedPreferences _prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        final Map<String, ?> keys = _prefs.getAll();

        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith(basename)) {
                Log.d(TAG, "resetting: " + key);
                _prefs.edit().remove(key).apply();
            }
        }
    }

}
