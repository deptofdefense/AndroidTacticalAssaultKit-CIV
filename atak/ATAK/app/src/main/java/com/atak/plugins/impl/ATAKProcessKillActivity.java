
package com.atak.plugins.impl;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;

import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;

/**
 * Class that will display a dialog when new plugins are added or removed allowing the app to restart to have the
 * changes take effect.
 *
 * 
 */
@Deprecated
@DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
public class ATAKProcessKillActivity extends MetricActivity
        implements DialogInterface.OnClickListener,
        DialogInterface.OnDismissListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        String appLabel = "The app";
        PackageManager packageManager = getPackageManager();

        // first, make sure we should bother
        if (!isRestartRequired()) {
            // no need to restart
            finish();
            return;
        }

        if (isPromptRequired()) {
            // get a nice label for the app...if there is one
            try {
                CharSequence applicationLabel = packageManager
                        .getApplicationLabel(
                                packageManager.getApplicationInfo(
                                        getPackageName(), 0));
                if (applicationLabel != null) {
                    appLabel = String.valueOf(applicationLabel);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // won't happen.  we're in the package being checked!
            }

            // prompt the user
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("New plugins were added or removed.  "
                    +
                    appLabel
                    + " must be restarted before changes take effect.  Restart?");
            builder.setPositiveButton(R.string.yes_restart, this);
            builder.setNegativeButton(R.string.no_restart_later, this);
            builder.create().show();
        } else {
            restartProcess();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // killing takes a sec...go ahead and dismiss
            dialog.dismiss();

            restartProcess();
        }
        dialog.dismiss();
    }

    /**
     * Actually restart the process
     */
    protected void restartProcess() {
        // also finish()
        finish();
        //Send an intent to restart myself from the top
        Intent i = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            // kill the process
            Process.killProcess(Process.myPid());
        }
    }

    /**
     * @return true if we should bother prompting the user
     */
    protected boolean isPromptRequired() {
        return true;
    }

    /**
     * @return true if we should bother restarting the process
     */
    protected boolean isRestartRequired() {
        PackageManager packageManager = getPackageManager();
        // also, make sure there is a launch intent...
        return packageManager
                .getLaunchIntentForPackage(getPackageName()) != null;
    }
}
