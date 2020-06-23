
package com.atakmap.android.network;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.atakmap.app.R;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;
import android.content.DialogInterface;

import java.net.URL;

public class AtakAuthenticatedConnectionCallback implements
        AtakAuthenticationHandlerHTTP.OnAuthenticateCallback {

    private final Activity activity;

    public AtakAuthenticatedConnectionCallback(final Activity activity) {
        this.activity = activity;
    }

    // XXX - use instance synchronization ???

    @Override
    public String[] getBasicAuth(URL url) {
        final String requestingSite = url.getHost();

        final String[] result = new String[] {
                null, null
        };
        final boolean[] complete = new boolean[] {
                false
        };

        synchronized (AtakAuthenticatedConnectionCallback.class) {
            this.activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showPrompt(requestingSite, result, complete);
                }
            });
            while (!complete[0])
                try {
                    AtakAuthenticatedConnectionCallback.class.wait();
                } catch (InterruptedException ignored) {
                }
        }
        if (result[0] == null || result[1] == null)
            return null;
        else
            return result;
    }

    private void showPrompt(final String site, final String[] result,
            final boolean[] complete) {
        LayoutInflater inflater = LayoutInflater.from(this.activity);
        View dialogView = inflater.inflate(R.layout.login_dialog, null);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                this.activity);
        dialogBuilder.setTitle("Login to " + site);
        dialogBuilder.setView(dialogView);

        final EditText uidText = dialogView
                .findViewById(R.id.txt_name);
        final EditText pwdText = dialogView
                .findViewById(R.id.password);

        dialogBuilder.setPositiveButton("Login",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {

                        try {
                            result[0] = uidText.getText().toString();
                            result[1] = pwdText.getText().toString();
                        } finally {
                            dialog.dismiss();
                            synchronized (AtakAuthenticatedConnectionCallback.class) {
                                complete[0] = true;
                                AtakAuthenticatedConnectionCallback.class
                                        .notify();
                            }
                        }
                    }
                });

        dialogBuilder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {
                        dialog.cancel();
                    }
                });

        dialogBuilder.setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        try {
                            result[0] = null;
                            result[1] = null;
                        } finally {
                            dialog.dismiss();
                            synchronized (AtakAuthenticatedConnectionCallback.class) {
                                complete[0] = true;
                                AtakAuthenticatedConnectionCallback.class
                                        .notify();
                            }
                        }
                    }
                });

        final Dialog loginDialog = dialogBuilder.create();

        loginDialog.show();
    }
}
