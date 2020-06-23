
package com.atakmap.android.location;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.app.R;

public class NoGpsWarningDialog {

    public interface Action {
        // usually just shows the dropdown as in the case of the NineLineBroadcastReceiver
        void onCancel();
    }

    public static Dialog create(final Action action,
            final Context context,
            final String linkback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(
                context.getString(R.string.no_gps))
                .setPositiveButton(R.string.skip,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                if (action != null)
                                    action.onCancel();
                            }
                        })
                .setNegativeButton(R.string.continue_text, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                // Start tool that allows user to tap a location
                                Intent beginToolIntent = new Intent(
                                        "com.atakmap.android.maps.toolbar.BEGIN_TOOL");
                                beginToolIntent
                                        .putExtra("tool",
                                                "com.atakmap.android.toolbar.tools.SPECIFY_SELF_LOCATION");
                                beginToolIntent.putExtra("linkBack", linkback);
                                AtakBroadcast.getInstance().sendBroadcast(
                                        beginToolIntent);
                            }
                        });

        return builder.create();
    }

}
