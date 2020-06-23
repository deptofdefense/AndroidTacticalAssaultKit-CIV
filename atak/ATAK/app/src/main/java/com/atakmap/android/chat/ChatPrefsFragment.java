
package com.atakmap.android.chat;

import java.io.File;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.widget.Toast;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

/*
 * 
 * 
 * Used to access the preferences for the chat screen
 */

public class ChatPrefsFragment extends AtakPreferenceFragment {

    private static final String TAG = "ChatPrefsFragment";

    public ChatPrefsFragment() {
        super(R.xml.chat_preferences, R.string.chatPreference);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.addPreferencesFromResource(getResourceID());

        final Preference clearHistoryCheckBox = findPreference("clearHistory");

        clearHistoryCheckBox
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {

                        AlertDialog.Builder builder = new AlertDialog.Builder(
                                getActivity());
                        builder.setMessage(R.string.chat_text15);
                        builder.setPositiveButton(R.string.yes,
                                new OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        clearChatHistory();
                                    }
                                });
                        builder.setNegativeButton(R.string.no, null);
                        builder.show();
                        return true;
                    }

                });

        final Preference exportHistory = findPreference("exportHistory");
        exportHistory
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        GeoChatService chatService = GeoChatService
                                .getInstance();
                        if (chatService != null) {
                            try {
                                File file = new File(
                                        FileSystemUtils
                                                .getItem(
                                                        FileSystemUtils.EXPORT_DIRECTORY),
                                        "ChatDb-" + new CoordinatedTime()
                                                .getMilliseconds()
                                                + "-export.csv");
                                chatService.exportHistory(file
                                        .getAbsolutePath());
                                Log.d(TAG, "Exported the Chat DB to file: "
                                        + file);
                                Toast.makeText(getActivity(),
                                        getString(R.string.chat_text16) + file,
                                        Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Log.w(TAG,
                                        "Error exporting chat history from Chat DB",
                                        e);
                            }
                        } else {
                            Log.w(TAG,
                                    "Connected to NULL GeoChatService.");
                        }
                        return true;
                    }
                });

        ((PanEditTextPreference) findPreference("chatPort"))
                .setValidIntegerRange(0, 65535);

    }

    private void clearChatHistory() {
        GeoChatService chatService = GeoChatService.getInstance();
        if (chatService != null) {
            try {

                chatService.clearMessageDB();
                Log.d(TAG, "Cleared the Chat DB.");
                Toast.makeText(getActivity(),
                        R.string.chat_text17,
                        Toast.LENGTH_SHORT)
                        .show();
            } catch (Exception e) {
                Log.w(TAG,
                        "Error clearing chat message from Chat DB",
                        e);
            }
        } else {
            Log.w(TAG,
                    "Connected to NULL GeoChatService.");
        }
    }
}
