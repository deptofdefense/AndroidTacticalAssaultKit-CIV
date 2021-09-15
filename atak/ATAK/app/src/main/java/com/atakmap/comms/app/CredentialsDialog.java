
package com.atakmap.comms.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.network.ui.CredentialsPreference;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

/**
 *
 */

public class CredentialsDialog {

    private static final String TAG = "CredentialsDialog";

    public interface Callback {
        void onCredentialsEntered(String connectString, String cacheCreds,
                String description,
                String username, String password, Long expiration);

        void onCredentialsCancelled(String connectString);
    }

    /**
     * Create a prompt to ask the user to enter missing credentials for a connection
     * if user approves, will prompt for username and password
     *
     * @param desc           Human readable description of the connection - ie the name
     * @param connectString  Connection info for the connection so that it can be updated
     * @param usernameString Username string stored in prefs (if available)
     * @param passwordString Password string stored in prefs (if available)
     * @param cacheCreds
     * @param context  the context used to display the alert dialog.
     * @param callback the callback for when the credential dialog is dismissed.
     */
    public static void createCredentialDialog(final String desc,
            final String connectString, final String usernameString,
            final String passwordString,
            final String cacheCreds,
            final Long expiration,
            final Context context,
            final CredentialsDialog.Callback callback) {

        NetConnectString ncs = NetConnectString.fromString(connectString);
        final String host = ncs.getHost();

        AlertDialog.Builder credentialsBuilder = new AlertDialog.Builder(
                context);
        credentialsBuilder.setIcon(R.drawable.ic_secure);

        final MapView mapView = MapView.getMapView();
        final Context appCtx = mapView.getContext();

        View credentialsView = LayoutInflater.from(appCtx)
                .inflate(R.layout.enter_credentials,
                        null);

        TextView connection_info = credentialsView
                .findViewById(R.id.credentials_connection_info);
        connection_info.setText(String.format(
                context.getString(R.string.connection_info), desc, host));
        final EditText usernameET = credentialsView
                .findViewById(R.id.credentials_username);
        if (!FileSystemUtils.isEmpty(usernameString))
            usernameET.setText(usernameString);
        final EditText passwordET = credentialsView
                .findViewById(R.id.credentials_password);
        if (passwordString != null && !passwordString.isEmpty())
            passwordET.setText(passwordString);

        final CheckBox checkBox = credentialsView
                .findViewById(R.id.password_checkbox);
        checkBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton,
                            boolean isChecked) {
                        if (isChecked) {
                            passwordET.setTransformationMethod(
                                    HideReturnsTransformationMethod
                                            .getInstance());
                        } else {
                            passwordET.setTransformationMethod(
                                    PasswordTransformationMethod.getInstance());
                        }
                        passwordET.setSelection(passwordET.getText().length());
                    }
                });

        credentialsBuilder
                .setTitle(R.string.enter_credentials)
                //Display dialog if they want to provide credentials
                .setView(credentialsView)
                .setPositiveButton(appCtx.getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {

                                String username = usernameET
                                        .getText()
                                        .toString().trim();
                                String password = passwordET
                                        .getText()
                                        .toString().trim();

                                if (!FileSystemUtils.isEmpty(cacheCreds)) {
                                    String cacheUsername = (cacheCreds
                                            .equals(appCtx
                                                    .getString(
                                                            R.string.cache_creds_both))
                                            || cacheCreds
                                                    .equals(appCtx
                                                            .getString(
                                                                    R.string.cache_creds_username)))
                                                                            ? username
                                                                            : "";
                                    String cachePassword = cacheCreds
                                            .equals(appCtx
                                                    .getString(
                                                            R.string.cache_creds_both))
                                                                    ? password
                                                                    : "";

                                    AtakAuthenticationDatabase
                                            .saveCredentials(
                                                    AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                                                    host,
                                                    cacheUsername,
                                                    cachePassword, expiration);
                                }

                                if (callback != null) {
                                    callback.onCredentialsEntered(
                                            connectString, cacheCreds, desc,
                                            username, password, expiration);
                                } else {
                                    AtakBroadcast
                                            .getInstance()
                                            .sendBroadcast(
                                                    new Intent(
                                                            CredentialsPreference.CREDENTIALS_UPDATED)
                                                                    .putExtra(
                                                                            "type",
                                                                            AtakAuthenticationCredentials.TYPE_COT_SERVICE)
                                                                    .putExtra(
                                                                            "host",
                                                                            host));
                                }

                                dialog.dismiss();
                            }
                        })

                .setNegativeButton(appCtx.getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {
                                if (callback != null) {
                                    callback.onCredentialsCancelled(
                                            connectString);
                                }
                            }
                        })

                .setCancelable(false);

        try {
            credentialsBuilder.show();
        } catch (Exception bte) {
            // ATAK-13893
        }
    }
}
