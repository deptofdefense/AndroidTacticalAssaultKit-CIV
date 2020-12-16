
package com.atakmap.android.network;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.text.Editable;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;
import android.content.DialogInterface;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.URL;

public class AtakAuthenticatedConnectionCallback implements
        AtakAuthenticationHandlerHTTP.OnAuthenticateCallback {

    private final Activity activity;

    public AtakAuthenticatedConnectionCallback(final Activity activity) {
        this.activity = activity;
    }

    // XXX - use instance synchronization ???

    @Override
    public String[] getBasicAuth(final URL url, final int previousStatus) {
        if (url == null)
            return null;

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
                    showPrompt(requestingSite, previousStatus, result,
                            complete);
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

    private void showPrompt(final String site, final int previousStatus,
            final String[] result,
            final boolean[] complete) {
        LayoutInflater inflater = LayoutInflater.from(this.activity);
        View dialogView = inflater.inflate(R.layout.login_dialog, null);

        final TextView reason = dialogView.findViewById(R.id.reason);

        reason.setVisibility(View.VISIBLE);

        if (previousStatus == HttpURLConnection.HTTP_UNAUTHORIZED) {
            // Similar to 403 Forbidden, but specifically for use when
            // authentication is required and has failed or has not yet been provided.
            reason.setText(R.string.http_401_message);

        } else if (previousStatus == HttpURLConnection.HTTP_FORBIDDEN) {
            //The request contained valid data and was understood by the server,
            // but the server is refusing action. This may be due to the user not
            // having the necessary permissions for a resource or needing an account
            // of some sort, or attempting a prohibited action
            reason.setText(R.string.http_403_message);
        } else {
            reason.setVisibility(View.GONE);
        }

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                this.activity);
        dialogBuilder.setTitle(activity.getString(R.string.login_to, site));
        dialogBuilder.setView(dialogView);
        dialogBuilder.setCancelable(false);

        final EditText uidText = dialogView
                .findViewById(R.id.txt_name);

        final EditText pwdText = dialogView
                .findViewById(R.id.password);

        final CheckBox checkBox = dialogView
                .findViewById(R.id.password_checkbox);
        checkBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton,
                            boolean isChecked) {
                        if (isChecked) {
                            pwdText.setTransformationMethod(
                                    HideReturnsTransformationMethod
                                            .getInstance());
                        } else {
                            pwdText.setTransformationMethod(
                                    PasswordTransformationMethod.getInstance());
                        }
                    }
                });

        // case password exists, do not show the password to the user but allow them to reuse it
        final AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_HTTP_BASIC_AUTH,
                        site);

        if (credentials != null && credentials.username != null)
            uidText.setText(credentials.username);

        if (credentials != null && credentials.password != null) {
            pwdText.setText(credentials.password);
            if (!FileSystemUtils.isEmpty(credentials.password)) {
                checkBox.setEnabled(false);
                pwdText.addTextChangedListener(new AfterTextChangedWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s != null && s.length() == 0) {
                            checkBox.setEnabled(true);
                            pwdText.removeTextChangedListener(this);
                        }
                    }
                });
            } else {
                checkBox.setEnabled(true);
            }
        }

        dialogBuilder.setPositiveButton(R.string.login,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {

                        try {
                            result[0] = uidText.getText().toString().trim();
                            result[1] = pwdText.getText().toString().trim();
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

        dialogBuilder.setNegativeButton(R.string.cancel,
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
