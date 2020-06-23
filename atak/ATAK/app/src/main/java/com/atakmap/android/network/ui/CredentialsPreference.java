
package com.atakmap.android.network.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import com.atakmap.android.gui.PanPreference;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.app.R;
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
    private Context pContext;

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
            }
        }
    }

    @Override
    protected View onCreateDialogView() {

        LayoutInflater inflater = LayoutInflater.from(getContext());
        view = inflater.inflate(R.layout.login_dialog, null);

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(credentialsType);

        if (credentials != null) {
            EditText username = view.findViewById(R.id.txt_name);
            EditText password = view.findViewById(R.id.password);
            username.setText(credentials.username);
            password.setText(credentials.password);
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
            } catch (IllegalArgumentException ignore) {
                //     ATAK-7278 Preferences IllegalArgumentException
                Log.d(TAG, "guarding against an issue from a crash log",
                        ignore);
            }

        }
    }
}
