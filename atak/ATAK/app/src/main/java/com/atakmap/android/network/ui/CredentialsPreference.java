
package com.atakmap.android.network.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import com.atakmap.android.gui.PanPreference;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public class CredentialsPreference extends DialogPreference {

    private static final String TAG = "CredentialsPreference";
    public static final String CREDENTIALS_UPDATED = "com.atakmap.android.network.ui.CREDENTIALS_UPDATED";

    private final Map<String, Integer> otherAttributes = new HashMap<>();

    protected View view;
    protected String credentialsType = AtakAuthenticationCredentials.TYPE_UNKNOWN;
    private static Context appContext;
    private final Context pContext;

    protected boolean passwordOnly = false;

    /**
     * For plugins we are REQUIRED to set the application context to the
     * ATAK owned Activity and not the context owned by the plugin.
     */
    public static void setContext(Context c) {
        appContext = c;
    }

    /**
     * Creates a credential preference screen that knows how to interact
     * with the AtakAuthenticationCredentials stored in ATAK
     * @param context the context to use
     * @param attrs the attributes
     * @param defStyle the default style
     */
    public CredentialsPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super((appContext == null) ? context : appContext, attrs, defStyle);
        PanPreference.setup(attrs, context, this, otherAttributes);
        pContext = context;

        if (attrs == null)
            return;

        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            String attr = attrs.getAttributeName(i);
            String val = attrs.getAttributeValue(i);
            if (attr.equalsIgnoreCase("credentialsType")) {
                //Log.i(TAG, "credentialsType = " + val);
                credentialsType = val;
            } else if (attr.equalsIgnoreCase("passwordOnly")) {
                if (Boolean.parseBoolean(val))
                    passwordOnly = true;
            }
        }
    }

    public CredentialsPreference(Context context, AttributeSet attrs) {
        super((appContext == null) ? context : appContext, attrs);
        PanPreference.setup(attrs, context, this, otherAttributes);
        pContext = context;

        if (attrs == null)
            return;

        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            String attr = attrs.getAttributeName(i);
            String val = attrs.getAttributeValue(i);
            if (attr.equalsIgnoreCase("credentialsType")) {
                //Log.i(TAG, "credentialsType = " + val);
                credentialsType = val;
            } else if (attr.equalsIgnoreCase("passwordOnly")) {
                if (Boolean.parseBoolean(val))
                    passwordOnly = true;
            }
        }
    }

    @Override
    protected View onCreateDialogView() {

        LayoutInflater inflater = LayoutInflater.from(getContext());
        view = inflater.inflate(R.layout.login_dialog, null);

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(credentialsType);

        final EditText username = view.findViewById(R.id.txt_name);
        if (passwordOnly) {
            final TextView txtlabel = view.findViewById(R.id.txt_label);
            txtlabel.setVisibility(View.GONE);
            username.setVisibility(View.GONE);
        }

        final EditText pwdText = view.findViewById(R.id.password);

        final CheckBox checkBox = view.findViewById(R.id.password_checkbox);
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

        if (credentials != null) {
            username.setText(credentials.username);
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

        return view;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {

        if (!positiveResult)
            return;

        String username = ((EditText) view.findViewById(R.id.txt_name))
                .getText().toString();
        if (username != null)
            username = username.trim();
        String password = ((EditText) view.findViewById(R.id.password))
                .getText().toString();

        AtakAuthenticationDatabase.saveCredentials(
                credentialsType,
                username, password, true);

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(CREDENTIALS_UPDATED).putExtra("type",
                        credentialsType));
    }

    @Override
    protected void showDialog(Bundle bundle) {
        super.showDialog(bundle);
        Dialog dialog = getDialog();
        if (dialog != null) {

            try {
                Integer resId = otherAttributes.get("name");
                if (resId != null)
                    dialog.setTitle(pContext.getString(resId));
            } catch (Exception ignored) {
            }
            final Window window = dialog.getWindow();
            try {
                if (window != null) {
                    window.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

                    Rect displayRectangle = new Rect();
                    window.getDecorView()
                            .getWindowVisibleDisplayFrame(displayRectangle);
                    window.setLayout(
                            (int) (displayRectangle.width() * 1.0f),
                            (int) (displayRectangle.height() * 1.0f));
                }
            } catch (IllegalArgumentException e) {
                //     ATAK-7278 Preferences IllegalArgumentException
                Log.d(TAG, "guarding against an issue from a crash log",
                        e);
            }

        }
    }
}
