
package com.atakmap.comms.app;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.comms.NetConnectString;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.comms.TAKServer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseAdapter;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.net.CertificateManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class AddNetInfoActivity extends MetricActivity {

    private static final String TAG = "AddNetInfoActivity";

    private String getProto() {
        RadioButton sslRadio = findViewById(R.id.ssl_radio);
        RadioButton tcpRadio = findViewById(R.id.tcp_radio);
        RadioButton udpRadio = findViewById(R.id.udp_radio);
        // determine protocol
        String proto = CotServiceRemote.Proto.udp.toString();
        if (tcpRadio.isChecked()) {
            proto = CotServiceRemote.Proto.tcp.toString();
        } else if (sslRadio.isChecked()) {
            proto = CotServiceRemote.Proto.ssl.toString();
        } else if (udpRadio.isChecked()) {
            proto = CotServiceRemote.Proto.udp.toString();
        }

        return proto;
    }

    private String getAddress() {
        // validate address text
        EditText addressEditText = findViewById(R.id.add_host);
        String addressText = addressEditText.getEditableText()
                .toString().trim();

        addressText = addressText.replaceAll(":", "");
        addressText = com.atakmap.coremap.locale.LocaleUtil
                .getNaturalNumber(addressText);

        return addressText;
    }

    private Integer getPort() {
        // validate port text
        EditText portEditText = findViewById(R.id.add_port);
        String portText = portEditText.getEditableText()
                .toString();
        int portNumber;
        try {
            portNumber = Integer.parseInt(portText);
            if (portNumber > 65535)
                throw new Exception();
        } catch (final Exception ex) {
            String message = "Port is invalid";
            showErrorDialog(message);
            return null;
        }

        return portNumber;
    }

    private String getConnectionString() {

        final String addressText = getAddress();

        if (FileSystemUtils.isEmpty(addressText)) {
            String message = getString(R.string.address_blank_error);
            showErrorDialog(message);
            return null;
        } else if (addressText.equals("0.0.0.0")) {
            // valid case specifying localhost
        } else if (!NetworkUtils.isValid(addressText)) {
            String message = String.format(
                    getString(R.string.address_invalid_error), addressText);
            showErrorDialog(message);
            return null;
        }

        final Integer portNumber = getPort();
        if (portNumber == null) {
            String message = "Port is invalid";
            showErrorDialog(message);
            return null;
        }

        final String retval = addressText + ":" + portNumber + ":" + getProto();
        NetConnectString ncs = NetConnectString.fromString(retval);
        if (ncs != null) {
            return retval;
        } else {
            String message = "server address is invalid";
            showErrorDialog(message);
            return null;

        }
    }

    protected void showErrorDialog(final String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.setMessage(message);
                builder.setPositiveButton(R.string.ok, null);
                final AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        AtakPreferenceFragment.setOrientation(this);

        setContentView(R.layout.add_network_layout);

        RadioButton sslRadio = findViewById(R.id.ssl_radio);
        RadioButton tcpRadio = findViewById(R.id.tcp_radio);
        RadioButton udpRadio = findViewById(R.id.udp_radio);
        CheckBox compress = findViewById(R.id.compression_checkbox);
        compress.setChecked(false);

        final CheckBox useAuthBox = findViewById(
                R.id.useAuthCheckbox);
        final LinearLayout useAuthCheckLayout = findViewById(
                R.id.useAuthCheckLayout);
        useAuthBox.setChecked(false);
        final TextView userNameLabel = findViewById(
                R.id.usernameLabel);
        final TextView passwordLabel = findViewById(
                R.id.passwordLabel);
        final EditText username = findViewById(R.id.add_username);
        final EditText password = findViewById(R.id.add_password);

        final View usernameBlock = findViewById(R.id.username_block);
        final View passwordBlock = findViewById(R.id.password_block);
        final Spinner spinnerCacheCreds = findViewById(
                R.id.spinnerCacheCreds);

        final LinearLayout truststoreLayout = findViewById(
                R.id.truststore_layout);
        final LinearLayout keystoreLayout = findViewById(
                R.id.keystore_layout);
        final LinearLayout certCheckboxLayout = findViewById(
                R.id.cert_checkbox_layout);
        final CheckBox advancedBox = findViewById(
                R.id.advanced_options_cb);
        final LinearLayout advancedLayout = findViewById(
                R.id.advanced_options);

        TextView title = findViewById(R.id.title);

        Bundle extras;
        if (getIntent() != null && getIntent().getExtras() != null)
            extras = getIntent().getExtras();
        else
            extras = new Bundle();

        String type = extras.getString("type");

        // under the possibility that the type is null, just close the activity and return;
        if (type == null) {
            Log.e(TAG, "error occurred, extras type == null");
            this.finish();
            return;
        }

        if (type.compareTo("input") == 0 || type.compareTo("inputs") == 0) {
            setTitle(getString(R.string.point_dropper_text58));
            title.setText(R.string.point_dropper_text59);
            sslRadio.setEnabled(false);
            sslRadio.setVisibility(View.GONE);
            compress.setVisibility(View.GONE);
            useAuthBox.setVisibility(View.GONE);
            userNameLabel.setVisibility(View.GONE);
            passwordLabel.setVisibility(View.GONE);
            username.setVisibility(View.GONE);
            password.setVisibility(View.GONE);
            keystoreLayout.setVisibility(View.GONE);
            truststoreLayout.setVisibility(View.GONE);
            certCheckboxLayout.setVisibility(View.GONE);
        } else if (type.compareTo("output") == 0
                || type.compareTo("outputs") == 0) {
            setTitle(getString(R.string.point_dropper_text60));
            title.setText(R.string.point_dropper_text61);
            sslRadio.setEnabled(false);
            sslRadio.setVisibility(View.GONE);
            compress.setVisibility(View.GONE);
            useAuthBox.setVisibility(View.GONE);
            userNameLabel.setVisibility(View.GONE);
            passwordLabel.setVisibility(View.GONE);
            username.setVisibility(View.GONE);
            password.setVisibility(View.GONE);
            keystoreLayout.setVisibility(View.GONE);
            truststoreLayout.setVisibility(View.GONE);
            certCheckboxLayout.setVisibility(View.GONE);
        } else if (type.compareTo("streaming") == 0
                || type.compareTo("streams") == 0) {
            setTitle(getString(R.string.point_dropper_text62));
            title.setText(R.string.point_dropper_text63);
            udpRadio.setEnabled(false);
            udpRadio.setVisibility(View.GONE);
            sslRadio.setChecked(true);
            compress.setVisibility(View.GONE); // disable for now, needs more testing
        }

        // Populate fields based on any info we get from the Intent
        String protocol = extras.getString("protocol");
        if (protocol != null) {
            if (CotServiceRemote.Proto.tcp.toString().equals(protocol)) {
                tcpRadio.setChecked(true);
            } else if (CotServiceRemote.Proto.udp.toString().equals(protocol)) {
                udpRadio.setChecked(true);
            } else if (CotServiceRemote.Proto.ssl.toString().equals(protocol)) {
                sslRadio.setChecked(true);
            }
        }
        String host = extras.getString("host");
        if (host != null) {
            EditText addressEditText = findViewById(R.id.add_host);
            addressEditText.setText(host);
        }
        EditText portEditText = findViewById(R.id.add_port);
        portEditText.setText(R.string.number_8089);
        Integer port = extras.getInt("port");
        if (port != null && port > 0)
            portEditText.setText(Integer.toString(port));
        String description = extras.getString("description");
        if (description != null) {
            EditText descEditText = findViewById(
                    R.id.add_description);
            descEditText.setText(description);
        }
        boolean useAuthFlag = extras.getBoolean("useAuth",
                false);
        useAuthBox.setChecked(useAuthFlag);
        if (useAuthFlag) {
            String usernameStr = extras.getString("username");
            if (usernameStr != null) {
                username.setText(usernameStr);
            }
            String passwordStr = extras.getString("password");
            if (passwordStr != null) {
                password.setText(passwordStr);
            }
            String cacheCreds = extras.getString("cacheCreds");
            if (cacheCreds != null) {
                if (cacheCreds.equals(getString(R.string.cache_creds_both)))
                    spinnerCacheCreds.setSelection(0);
                else if (cacheCreds
                        .equals(getString(R.string.cache_creds_username)))
                    spinnerCacheCreds.setSelection(1);
                else {
                    //previously set to 'Do not cache'
                    spinnerCacheCreds.setSelection(2);
                }
            }
        }

        int vis = View.GONE;
        if (useAuthFlag) {
            vis = View.VISIBLE;
        }
        usernameBlock.setVisibility(vis);
        passwordBlock.setVisibility(vis);
        spinnerCacheCreds.setVisibility(vis);

        username.setEnabled(useAuthFlag);
        password.setEnabled(useAuthFlag);
        spinnerCacheCreds.setEnabled(useAuthFlag);

        boolean enrollWithTrust = extras.getBoolean(
                "enrollForCertificateWithTrust", false);

        boolean compressFlag = extras.getBoolean("compress",
                false);
        compress.setChecked(compressFlag);

        final CheckBox useDefaultCertsCheckbox = findViewById(
                R.id.useDefaultCertsCheckbox);

        final Button importTruststoreButton = findViewById(
                R.id.import_truststore_button);
        final Button importKeystoreButton = findViewById(
                R.id.import_keystore_button);
        final Button exportKeystoreButton = findViewById(
                R.id.export_keystore_button);

        final EditText truststorePassword = findViewById(
                R.id.truststore_password);
        final EditText keystorePassword = findViewById(
                R.id.cert_store_password);

        final TextView truststoreName = findViewById(
                R.id.truststore_name);
        final TextView keystoreName = findViewById(
                R.id.keystore_name);

        // if we're launching the dialog for the first time,
        // or we don't have a client cert for this connection,
        // we must be using default certs
        boolean useDefaultCerts = host == null
                ||
                AtakCertificateDatabase.getCertificateForServer(
                        AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                        host) == null;

        useDefaultCertsCheckbox.setChecked(useDefaultCerts);

        importTruststoreButton.setEnabled(!useDefaultCerts);
        importKeystoreButton.setEnabled(!useDefaultCerts);
        truststorePassword.setEnabled(!useDefaultCerts);
        keystorePassword.setEnabled(!useDefaultCerts);

        boolean showExportKeystoreButton = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean("certEnrollmentExport", false);

        exportKeystoreButton
                .setVisibility(showExportKeystoreButton ? View.VISIBLE
                        : View.GONE);
        exportKeystoreButton.setEnabled(enrollWithTrust);

        if (host != null && !useDefaultCerts) {
            AtakAuthenticationCredentials caCreds = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword,
                            host);
            if (caCreds != null &&
                    caCreds.password != null &&
                    caCreds.password.length() > 0) {
                truststorePassword.setText(caCreds.password);
            }

            AtakAuthenticationCredentials clientCertCreds = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_clientPassword,
                            host);
            if (clientCertCreds != null &&
                    clientCertCreds.password != null &&
                    clientCertCreds.password.length() > 0) {
                keystorePassword.setText(clientCertCreds.password);
            }
        }

        final AtomicReference<String> truststoreFile = new AtomicReference<>();
        final AtomicReference<String> keystoreFile = new AtomicReference<>();

        // done population of fields

        if (sslRadio.isChecked()) {
            useAuthCheckLayout.setVisibility(View.VISIBLE);
        } else {
            //clear/hide auth views
            useAuthCheckLayout.setVisibility(View.GONE);
            useAuthBox.setChecked(false);
            username.setText("");
            password.setText("");
        }

        sslRadio.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("", "clicked sslRadio button");
                keystoreLayout.setVisibility(View.VISIBLE);
                truststoreLayout.setVisibility(View.VISIBLE);
                certCheckboxLayout.setVisibility(View.VISIBLE);
                useAuthCheckLayout.setVisibility(View.VISIBLE);
            }
        });

        tcpRadio.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                keystoreLayout.setVisibility(View.GONE);
                truststoreLayout.setVisibility(View.GONE);
                certCheckboxLayout.setVisibility(View.GONE);

                //clear/hide auth views
                useAuthCheckLayout.setVisibility(View.GONE);
                useAuthBox.setChecked(false);
                username.setText("");
                password.setText("");
            }
        });

        final CheckBox enrollKeystoreBox = findViewById(
                R.id.enroll_keystore_cb);
        enrollKeystoreBox.setChecked(enrollWithTrust);
        enrollKeystoreBox
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton cb,
                            boolean checked) {
                        exportKeystoreButton.setEnabled(checked);
                    }
                });

        useAuthBox
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton cb,
                            boolean checked) {
                        int vis = checked ? View.VISIBLE : View.GONE;
                        usernameBlock.setVisibility(vis);
                        passwordBlock.setVisibility(vis);
                        spinnerCacheCreds.setVisibility(vis);

                        username.setEnabled(checked);
                        password.setEnabled(checked);
                        spinnerCacheCreds.setEnabled(checked);
                    }
                });

        useDefaultCertsCheckbox
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton,
                            boolean checked) {
                        importTruststoreButton.setEnabled(!checked);
                        importKeystoreButton.setEnabled(!checked);
                        truststorePassword.setEnabled(!checked);
                        keystorePassword.setEnabled(!checked);
                    }
                });

        Button submitButton = findViewById(R.id.add_net_button);
        submitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // the InetAddress.getByName could hit the network if you give it a
                // hostname, so to avoid the android.os.NetworkOnMainThreadException
                // we need to do this whole block on another thread
                new Thread(TAG + "-Submit") {
                    @Override
                    public void run() {

                        if (getConnectionString() == null)
                            return;

                        // determine description
                        EditText descEditText = findViewById(
                                R.id.add_description);
                        final String description = descEditText
                                .getEditableText().toString();

                        // make sure that the description isn't already used by another stream
                        Bundle extras = getIntent()
                                .getExtras();

                        ArrayList<String> descriptionArray = null;
                        if (extras != null)
                            descriptionArray = extras
                                    .getStringArrayList("descriptions");

                        if (descriptionArray != null
                                && descriptionArray.contains(description)) {
                            String message = "Description "
                                    + description
                                    + " is already used. Enter a unique description";
                            showErrorDialog(message);
                            return;
                        }

                        CheckBox compressBox = findViewById(
                                R.id.compression_checkbox);

                        Bundle data = new Bundle();
                        data.putString("description", description);
                        data.putBoolean("enrollForCertificateWithTrust",
                                enrollKeystoreBox.isChecked());
                        data.putBoolean("compress", compressBox.isChecked());
                        data.putBoolean("useAuth", useAuthBox.isChecked());
                        if (useAuthBox.isChecked()) {
                            if (!getProto().equals(CotServiceRemote.Proto.ssl
                                    .toString())) {
                                String message = "Error: Auth messages can not be sent over un-encrypted channels";
                                showErrorDialog(message);
                                return;
                            }

                            data.putString("username", username
                                    .getEditableText().toString().trim());
                            data.putString("password", password
                                    .getEditableText().toString().trim());
                            data.putString("cacheCreds", spinnerCacheCreds
                                    .getSelectedItem().toString());
                        }
                        //Log.d("", "data bundle: " + data);

                        String server = getAddress();
                        if (useDefaultCertsCheckbox.isChecked()) {
                            Integer port = getPort();
                            // do not continue with a null port
                            if (port == null)
                                return;

                            // clear out certs that may have been stored with this connection
                            AtakCertificateDatabase
                                    .deleteCertificateForServerAndPort(
                                            AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                                            server, port);
                            AtakCertificateDatabase
                                    .deleteCertificateForServerAndPort(
                                            AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                                            server, port);

                            // clear out cert passwords that may have been stored with this connection
                            AtakAuthenticationDatabase
                                    .delete(
                                            AtakAuthenticationCredentials.TYPE_clientPassword,
                                            server);
                            AtakAuthenticationDatabase
                                    .delete(
                                            AtakAuthenticationCredentials.TYPE_caPassword,
                                            server);

                        } else {

                            // save the ca cert password
                            String caPassword = truststorePassword
                                    .getEditableText().toString().trim();
                            AtakAuthenticationDatabase
                                    .saveCredentials(
                                            AtakAuthenticationCredentials.TYPE_caPassword,
                                            server,
                                            "", caPassword, false);

                            // save client cert password
                            String clientPassword = keystorePassword
                                    .getEditableText().toString().trim();
                            AtakAuthenticationDatabase
                                    .saveCredentials(
                                            AtakAuthenticationCredentials.TYPE_clientPassword,
                                            server,
                                            "", clientPassword, false);
                        }

                        // remove any cached off socket factory associated with this connection
                        CertificateManager.invalidate(server);

                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("data", data);
                        resultIntent.putExtra(TAKServer.CONNECT_STRING_KEY,
                                getConnectionString());

                        setResult(Activity.RESULT_OK, resultIntent);
                        finish();
                    }
                }.start();
            }
        });

        Button cancelButton = findViewById(R.id.cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        importTruststoreButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getFile(truststoreFile,
                        getString(R.string.preferences_text412),
                        AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                        "certs", "p12", new Callback() {
                            public void fileSelected(final File f) {
                                importTruststoreButton.post(new Runnable() {
                                    public void run() {
                                        if (f != null)
                                            truststoreName.setText(f.getName());
                                    }
                                });
                            }
                        });

            }
        });

        importKeystoreButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getFile(keystoreFile,
                        getString(R.string.preferences_text413),
                        AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                        "certs", "p12", new Callback() {
                            public void fileSelected(final File f) {
                                importKeystoreButton.post(new Runnable() {
                                    public void run() {
                                        if (f != null)
                                            keystoreName.setText(f.getName());
                                    }
                                });
                            }
                        });
            }
        });

        exportKeystoreButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("", "clicked export keystore button");

                byte[] clientCertificate;

                if (useDefaultCertsCheckbox.isChecked()) {
                    clientCertificate = AtakCertificateDatabase
                            .getCertificate(
                                    AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE);
                } else {
                    clientCertificate = AtakCertificateDatabase
                            .getCertificateForServer(
                                    AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE,
                                    getAddress());
                }

                // determine description
                EditText descEditText = findViewById(
                        R.id.add_description);
                final String description = descEditText
                        .getEditableText().toString();

                String absolutePath = FileSystemUtils
                        .sanitizeWithSpacesAndSlashes(FileSystemUtils.getRoot()
                                + "/cert/" + description + "_clientCert.p12");

                try (FileOutputStream fos = IOProviderFactory
                        .getOutputStream(new File(absolutePath));
                        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                    if (clientCertificate != null) {
                        baos.write(clientCertificate);
                    }
                    baos.writeTo(fos);
                } catch (Exception e) {
                    Log.e(TAG, "Exception exporting client certificate!", e);
                    return;
                }

                new AlertDialog.Builder(AddNetInfoActivity.this)
                        .setTitle(String.format(
                                AddNetInfoActivity.this
                                        .getString(R.string.importmgr_exported),
                                "Certificate"))
                        .setMessage(
                                String.format(
                                        AddNetInfoActivity.this.getString(
                                                R.string.importmgr_exported_file),
                                        absolutePath))
                        .setPositiveButton(
                                R.string.done, null)
                        .show();
            }
        });

        advancedBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean checked) {
                advancedLayout
                        .setVisibility(checked ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    protected void onResume() {
        AtakPreferenceFragment.setOrientation(this);
        super.onResume();
    }

    interface Callback {
        void fileSelected(File f);
    }

    /**
     * TODO combine with NetworkConnectionPreferenceFragment.getCertFile()?
     *  It does an invalidate
     * but this one sends new Intent(CERTIFICATE_UPDATED)
     * and they handle the 'server' a bit differently, but could be consolidated
     *
     * @param selectedFile
     * @param type
     * @param atakSubdir
     * @param extensionType
     * @param c the callback to call when the file is sucessfully selected.
     */
    private void getFile(final AtomicReference<String> selectedFile,
            final String title, final String type,
            final String atakSubdir, final String extensionType,
            final Callback c) {

        File certDir = FileSystemUtils.getItem(atakSubdir);

        String directory;
        if (IOProviderFactory.isDefault()) {
            if (certDir != null && IOProviderFactory.exists(certDir)
                    && IOProviderFactory.isDirectory(certDir)) {
                directory = certDir.getAbsolutePath();
            } else {
                directory = Environment.getExternalStorageDirectory().getPath();
            }
        } else {
            directory = ATAKUtilities
                    .getStartDirectory(MapView.getMapView().getContext());
        }

        ImportFileBrowserDialog.show(getString(R.string.select_space) +
                title + getString(R.string.to_import),
                directory,
                new String[] {
                        extensionType
                },
                new ImportFileBrowserDialog.DialogDismissed() {
                    @Override
                    public void onFileSelected(final File file) {
                        if (!FileSystemUtils.isFile(file))
                            return;

                        byte[] contents;
                        try {
                            contents = FileSystemUtils.read(file);
                        } catch (IOException ioe) {
                            Log.e(TAG, "Failed to read cert from: "
                                    + file.getAbsolutePath(), ioe);
                            return;
                        }

                        String server = getAddress();
                        Integer port = getPort();
                        // do not continue with a null port
                        if (port == null)
                            return;

                        AtakCertificateDatabase.saveCertificateForServerAndPort(
                                type,
                                server, port, contents);
                        CertificateManager.invalidate(server);
                        if (c != null)
                            c.fileSelected(file);
                    }

                    @Override
                    public void onDialogClosed() {
                    }
                }, this);
    }

}
