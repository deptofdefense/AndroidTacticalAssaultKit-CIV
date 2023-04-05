
package com.atakmap.android.user.feedback;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;

import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.util.UUID;

public class UserFeedbackCollector {

    public static final String FEEDBACK_FOLDER = "tools/userfeedback";

    final private static String TAG = "UserFeedbackCollector";
    final private Context context;
    final private LayoutInflater inflater;
    private final SharedPreferences prefs;

    /**
     * Construct a new User Feedback Collector with the provided context.
     * @param c the context
     */
    public UserFeedbackCollector(Context c) {
        context = c;
        inflater = LayoutInflater.from(c);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

    }

    /**
     * Show the user feedback collector
     */
    public void showCollector() {

        final View v = inflater.inflate(R.layout.user_feedback_selection, null,
                false);
        final ListView listView = v.findViewById(R.id.listview);

        final UserFeedbackAdapter userFeedbackAdapter = new UserFeedbackAdapter(
                context);
        listView.setAdapter(userFeedbackAdapter);

        final ImageButton add_user_feedback = v
                .findViewById(R.id.add_user_feedback);
        add_user_feedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File folder = new File(FileSystemUtils.getItem(FEEDBACK_FOLDER),
                        UUID.randomUUID().toString());
                IOProviderFactory.mkdirs(folder);

                final String callsign = prefs.getString("locationCallsign", "");

                Feedback feedback = new Feedback(folder, callsign);
                userFeedbackAdapter.showEditor(feedback);
            }
        });

        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(
                context);
        alertDialog.setTitle(R.string.user_feedback)
                .setView(v)
                .setCancelable(false)
                .setPositiveButton(R.string.done, null);

        try {
            final AlertDialog ad = alertDialog.show();
            AlertDialogHelper.adjustHeight(ad, .90d);
        } catch (Exception ignored) {
        }

    }

}
