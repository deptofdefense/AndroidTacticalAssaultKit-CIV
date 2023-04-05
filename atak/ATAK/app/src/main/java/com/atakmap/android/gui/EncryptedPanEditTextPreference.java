
package com.atakmap.android.gui;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.network.ui.CredentialsPreference;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.CertificateManager;

public class EncryptedPanEditTextPreference extends PanEditTextPreference {

    public EncryptedPanEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public String getText() {

        String unencrypted = super.getText();
        if (!FileSystemUtils.isEmpty(unencrypted)) {
            this.setText(unencrypted);
            super.setText("");
        }

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(this.getKey());

        String text = "";
        if (credentials != null) {
            text = credentials.password;
        }

        return text;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        if (!isEnabled())
            v.setEnabled(false);
        return v;
    }

    @Override
    public void setText(String text) {
        // dont expire credentials used for certificates
        AtakAuthenticationDatabase.saveCredentials(this.getKey(), "",
                text, false);

        // if we just set a CA cert password, go ahead and refresh the certificate manager
        if (this.getKey().compareTo(
                AtakAuthenticationCredentials.TYPE_caPassword) == 0 ||
                this.getKey().compareTo(
                        AtakAuthenticationCredentials.TYPE_updateServerCaPassword) == 0) {
            CertificateManager.getInstance().refresh();
        }

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(CredentialsPreference.CREDENTIALS_UPDATED)
                        .putExtra("type", this.getKey()));
    }
}
