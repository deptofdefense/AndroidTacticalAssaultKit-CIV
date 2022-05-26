
package com.atakmap.comms.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import org.apache.commons.lang.StringUtils;

/**
 *
 */

public class EnrollmentDialog {

    private static final String TAG = "EnrollmentDialog";

    public interface Callback {
        void onEnrollmentOk(Context context, String address, String cacheCreds,
                String description,
                String username, String password, Long expiration);

        void onEnrollmentCancel();
    }

    /**
     * Create a prompt to ask the user to enter name, address, and credentials for a connection.
     * Show the optional server, username, password params if provided
     *
     * @param server optional server to display
     * @param username optional username to display
     * @param password optional password to display
     * @param context  the context used to display the alert dialog.
     * @param callback the callback for when the credential dialog is dismissed.
     */
    public static void createEnrollmentDialog(final String server,
            final String username,
            final String password,
            final Context context,
            final EnrollmentDialog.Callback callback) {
        try {
            AlertDialog.Builder enrollmentBuilder = new AlertDialog.Builder(
                    context);
            enrollmentBuilder.setIcon(R.drawable.ic_secure);

            final MapView mapView = MapView.getMapView();
            final Context appCtx = mapView.getContext();

            View enrollmentView = LayoutInflater.from(appCtx)
                    .inflate(R.layout.enrollment,
                            null);

            final EditText addressET = enrollmentView
                    .findViewById(R.id.enrollment_address);
            final EditText usernameET = enrollmentView
                    .findViewById(R.id.enrollment_username);
            final EditText passwordET = enrollmentView
                    .findViewById(R.id.enrollment_password);

            if (StringUtils.isNotEmpty(server)) {
                addressET.setText(server);
            }

            if (StringUtils.isNotEmpty(username)) {
                usernameET.setText(username);
            }

            if (StringUtils.isNotEmpty(password)) {
                passwordET.setText(password);
            }

            final CheckBox checkBox = enrollmentView
                    .findViewById(R.id.password_checkbox);
            checkBox.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(
                                CompoundButton compoundButton,
                                boolean isChecked) {
                            if (isChecked) {
                                passwordET.setTransformationMethod(
                                        HideReturnsTransformationMethod
                                                .getInstance());
                            } else {
                                passwordET.setTransformationMethod(
                                        PasswordTransformationMethod
                                                .getInstance());
                            }
                            passwordET.setSelection(
                                    passwordET.getText().length());
                        }
                    });

            enrollmentBuilder
                    .setTitle(R.string.tak_server_quick_connect)
                    .setView(enrollmentView)
                    .setPositiveButton(appCtx.getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        DialogInterface dialog,
                                        int which) {

                                    if (callback == null) {
                                        return;
                                    }

                                    String address = addressET
                                            .getText()
                                            .toString().trim();
                                    String username = usernameET
                                            .getText()
                                            .toString().trim();
                                    String password = passwordET
                                            .getText()
                                            .toString().trim();

                                    callback.onEnrollmentOk(context, address,
                                            null, address,
                                            username, password, -1L);

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
                                        callback.onEnrollmentCancel();
                                    }
                                }
                            })

                    .setCancelable(false);

            enrollmentBuilder.show();

        } catch (Exception e) {
            Log.e(TAG, "exception in createEnrollmentDialog!", e);
        }
    }
}
