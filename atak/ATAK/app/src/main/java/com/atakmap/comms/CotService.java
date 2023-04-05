
package com.atakmap.comms;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.network.ui.CredentialsPreference;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.comms.app.CotPortListActivity.CotPort;
import com.atakmap.comms.app.TLSUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakAuthenticationDatabaseIFace;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CotService implements OnSharedPreferenceChangeListener,
        ClearContentRegistry.ClearContentListener {

    public static final String TAG = "CotService";

    /**
     * Use all F's (bogus UID) so server will not route out to other users, rather just
     * store in server DB
     */
    public static final String SERVER_ONLY_CONTACT = "ffffffff-ffff-ffff-ffff-ffffffffffff";

    private int multicastTTL;
    private int udpNoDataTimeout;

    private final SharedPreferences prefs;

    private final Context context;

    private static final Object staticDatabaseIFaceLock = new Object();

    private static final Object propertyFileLock = new Object();

    /**
     * The cot service responsible for persisting and restoring the CoT ports used by the system.
     * @param context the context.
     */
    public CotService(final Context context) {
        this.context = context;

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            multicastTTL = Integer.parseInt(prefs.getString("multicastTTL",
                    "64"));
        } catch (NumberFormatException e) {
            multicastTTL = 64;
        }
        CommsMapComponent.getInstance().setTTL(multicastTTL);

        try {
            udpNoDataTimeout = Integer.parseInt(prefs.getString(
                    "udpNoDataTimeout",
                    "30"));
        } catch (NumberFormatException e) {
            udpNoDataTimeout = 30;
        }
        CommsMapComponent.getInstance().setUdpNoDataTimeout(udpNoDataTimeout);

        int tcpConnectTimeout;
        try {
            tcpConnectTimeout = Integer.parseInt(prefs.getString(
                    "tcpConnectTimeout", "20")) * 1000;
        } catch (NumberFormatException e) {
            tcpConnectTimeout = 20000;
        }
        CommsMapComponent.getInstance().setTcpConnTimeout(tcpConnectTimeout);

        prefs.registerOnSharedPreferenceChangeListener(this);
        CommsMapComponent.getInstance().setNonStreamsEnabled(
                prefs.getBoolean(
                        "enableNonStreamingConnections", true));

        // upgrade all legacy persisted information
        upgradeLegacyConfiguration("cot_streams");
        upgradeLegacyConfiguration("cot_inputs");
        upgradeLegacyConfiguration("cot_outputs");

        Log.i(TAG, "Loading inputs, outputs and streams");
        if (!_loadSavedInputs()
                && prefs.getBoolean("createDefaultCoTInputs", true)) {
            _addDefaultInputs();
        }

        if (!_loadSavedOutputs()) {
            Log.d(TAG, "error loading save outputs");
        }
        if (!_loadSavedStreams()) {
            Log.d(TAG, "error loading save streams");
        }

        ClearContentRegistry.getInstance().registerListener(this);

    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs,
            final String key) {

        if (key == null)
            return;

        if (key.compareTo("udpNoDataTimeout") == 0) {
            try {
                udpNoDataTimeout = Integer.parseInt(prefs.getString(
                        "udpNoDataTimeout",
                        "30"));
            } catch (NumberFormatException e) {
                udpNoDataTimeout = 30;
            }
            CommsMapComponent.getInstance().setUdpNoDataTimeout(
                    udpNoDataTimeout);
        } else if (key.compareTo("multicastTTL") == 0) {
            try {
                multicastTTL = Integer.parseInt(prefs.getString("multicastTTL",
                        "64"));
            } catch (NumberFormatException e) {
                multicastTTL = 64;
            }
            CommsMapComponent.getInstance().setTTL(multicastTTL);
        } else if (key.compareTo("tcpConnectTimeout") == 0) {
            try {
                int tcpConnectTimeout = Integer.parseInt(prefs
                        .getString("tcpConnectTimeout", "20")) * 1000;
                CommsMapComponent.getInstance().setTcpConnTimeout(
                        tcpConnectTimeout);
            } catch (NumberFormatException e) {
                Log.e(TAG,
                        "number format exception occurred trying to parse tcpConnectTimeout",
                        e);
            }
        } else if (key.compareTo("locationCallsign") == 0) {
            // Force ATAK to re-start all streaming connections when our
            // callsign changes.  This is necessary because Marti needs to know
            // that our callsign has changed, and this is the easiest way to notify
            // it currently.
            reconnectStreams();
        } else if (key.compareTo("enableNonStreamingConnections") == 0) {
            CommsMapComponent.getInstance().setNonStreamsEnabled(
                    prefs.getBoolean(
                            "enableNonStreamingConnections", true));
        }
    }

    private boolean _loadSavedInputs() {
        synchronized (propertyFileLock) {
            final File[] inputs = IOProviderFactory
                    .listFiles(getConnectionsDir(context, "cot_inputs"));
            if (inputs == null)
                return false;
            for (File config : inputs) {
                final Properties props = new Properties();
                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(config)) {
                    props.load(fis);
                } catch (IOException | IllegalArgumentException e) {
                    Log.w(TAG, "Failed to read input config file", e);
                    // If a failure has occurred for an existing Input, then the user might lose that
                    // address and port without realizing it.   Go ahead an trigger a reload of all
                    // of the basic "default" addresses and ports
                    IOProviderFactory.delete(config);
                    return false;
                }
                String desc = props.getProperty("description", "");
                String connectString = props
                        .getProperty(TAKServer.CONNECT_STRING_KEY, null);
                if (connectString != null) {
                    boolean isEnabled = !props.getProperty("enabled", "1")
                            .equals("0");
                    Bundle input = new Bundle();
                    input.putString("description", desc);
                    input.putBoolean("enabled", isEnabled);
                    addInput(connectString, input);
                }
            }
        }
        return true;
    }

    void removeInput(final String connectString) {
        CommsMapComponent.getInstance().removeInput(connectString);
        _removeSavedInputOutput("cot_inputs", connectString);
    }

    public void addInput(final String connectString, final Bundle input) {
        String[] connectParts = connectString.split(":");
        if (connectParts.length != 3)
            throw new IllegalArgumentException(
                    "Connect string not comprised of ip:port:protocol ("
                            + connectString + ")");

        int port;
        try {
            port = Integer.parseInt(connectParts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Connect string has invalid port number (" + connectString
                            + ")");
        }

        switch (connectParts[2]) {
            case "udp":
                input.putString(CotPort.CONNECT_STRING_KEY, connectString);
                if (connectParts[0].trim().equals("0.0.0.0")) {
                    CommsMapComponent.getInstance().addInput(connectString,
                            input,
                            port, null);
                } else {
                    CommsMapComponent.getInstance().addInput(connectString,
                            input,
                            port, connectParts[0]);
                }
                break;
            case "tcp":
                input.putString(CotPort.CONNECT_STRING_KEY, connectString);
                CommsMapComponent.getInstance().addTcpInput(connectString,
                        input,
                        port);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported protocol in connect string "
                                + connectString);
        }

        // save the inputs based on the original code, check for temporary and
        // no persist flags
        if (!input.getBoolean("temporary")) {
            if (!input.getBoolean("noPersist", false)) {
                _saveInputOutput("cot_inputs", connectString, input);
            }
        }
    }

    void removeOutput(final String connectString) {
        CommsMapComponent.getInstance().removeOutput(connectString);
        _removeSavedInputOutput("cot_outputs", connectString);
    }

    public void addOutput(String connectString, Bundle output) {
        String[] connectParts = connectString.split(":");
        if (connectParts.length != 3)
            throw new IllegalArgumentException(
                    "Connect string not comprised of ip:port:protocol ("
                            + connectString + ")");

        int port;
        try {
            port = Integer.parseInt(connectParts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Connect string has invalid port number (" + connectString
                            + ")");
        }

        if (connectParts[2].equals("udp")) {
            output.putString(CotPort.CONNECT_STRING_KEY, connectString);
            // Marking not connected is needed to prevent this local/lan output 
            // from being treated like a stream by UI components
            output.putBoolean("connected", false);

            CommsMapComponent.getInstance().addOutput(connectString, output,
                    port, connectParts[0]);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported protocol in connect string " + connectString);
        }

        if (!output.getBoolean("temporary")) {
            if (!output.getBoolean("noPersist", false)) {
                _saveInputOutput("cot_outputs", connectString, output);
            }
        }

    }

    public void reconnectStreams() {
        Log.d(TAG, "reconnectStreams");
        Bundle b = CommsMapComponent.getInstance().getAllPortsBundle();
        Bundle[] streams = (Bundle[]) b.getParcelableArray("streams");
        if (streams != null) {
            for (Bundle sb : streams) {
                String connStr = sb.getString(CotPort.CONNECT_STRING_KEY);
                if (connStr != null) {
                    addStreaming(connStr, sb);
                }
            }
        }
    }

    /**
     * Add a connection to the system.
     * @param connectString the connect string ip:port:protocol
     * @param output the results of the addition of the stream as a bundle.
     */
    public void addStreaming(String connectString, Bundle output) {
        String[] connectParts = connectString.split(":");
        if (connectParts.length != 3)
            throw new IllegalArgumentException(
                    "Connect string not comprised of ip:port:protocol ("
                            + connectString + ")");

        int port;
        try {
            port = Integer.parseInt(connectParts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Connect string has invalid port number (" + connectString
                            + ")");
        }

        boolean hadError = false;
        byte[] trustStore = null;
        byte[] clientCert = null;
        String trustPassword = null;
        String clientPassword = null;

        if (connectParts[2].equals("ssl")) {

            // retrieve the certificates. look for a connection specific cert
            trustStore = AtakCertificateDatabase
                    .getCertificateForServerAndPort(
                            AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                            connectParts[0],
                            Integer.parseInt(connectParts[1]));
            clientCert = AtakCertificateDatabase
                    .getCertificateForServerAndPort(
                            AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                            connectParts[0],
                            Integer.parseInt(connectParts[1]));

            boolean useConnectionTrustStore = trustStore != null;
            boolean useConnectionClientCert = clientCert != null;

            // try getting the global cert
            if (!useConnectionTrustStore) {
                trustStore = AtakCertificateDatabase
                        .getCertificate(
                                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA);
            }

            boolean enrollForCertificateWithTrust = output.getBoolean(
                    "enrollForCertificateWithTrust", false);

            // dont pull in default certs if the connection uses enrollment. enrollment
            // always stores the cert with the connection
            if (!useConnectionClientCert && !enrollForCertificateWithTrust) {
                clientCert = AtakCertificateDatabase
                        .getCertificate(
                                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE);
            }

            if (trustStore == null) {
                NotificationUtil.getInstance().postNotification(
                        31346,
                        NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                        NotificationUtil.RED,
                        context.getString(R.string.connection_error),
                        context.getString(R.string.preferences_text446),
                        new Intent("com.atakmap.app.NETWORK_SETTINGS"),
                        true);
                hadError = true;
            }

            if (clientCert == null) {
                if (!hadError)
                    NotificationUtil
                            .getInstance()
                            .postNotification(
                                    31346,
                                    NotificationUtil.GeneralIcon.NETWORK_ERROR
                                            .getID(),
                                    NotificationUtil.RED,
                                    context.getString(
                                            R.string.connection_error),
                                    context.getString(
                                            R.string.preferences_text447),
                                    new Intent(
                                            "com.atakmap.app.NETWORK_SETTINGS"),
                                    true);
                hadError = true;
            }

            // retrieve the passwords
            AtakAuthenticationCredentials caCertCredentials;
            AtakAuthenticationCredentials clientCertCredentials;

            if (useConnectionTrustStore) {
                caCertCredentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_caPassword,
                                connectParts[0]);
            } else {
                caCertCredentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_caPassword);
            }

            if (useConnectionClientCert) {
                clientCertCredentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_clientPassword,
                                connectParts[0]);
            } else {
                clientCertCredentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_clientPassword);
            }

            if (!hadError && (caCertCredentials == null
                    || caCertCredentials.password == null
                    || caCertCredentials.password.length() == 0)) {
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                31346,
                                NotificationUtil.GeneralIcon.NETWORK_ERROR
                                        .getID(),
                                NotificationUtil.RED,
                                context.getString(R.string.connection_error),
                                context.getString(R.string.preferences_text448),
                                new Intent(
                                        "com.atakmap.app.NETWORK_SETTINGS"),
                                true);
                hadError = true;
            } else if (!hadError) {
                trustPassword = caCertCredentials.password;
            }

            if (!hadError && (clientCertCredentials == null
                    || clientCertCredentials.password == null
                    || clientCertCredentials.password.length() == 0)) {
                NotificationUtil.getInstance().postNotification(
                        31346,
                        NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                        NotificationUtil.RED,
                        context.getString(R.string.connection_error),
                        context.getString(R.string.preferences_text449),
                        new Intent("com.atakmap.app.NETWORK_SETTINGS"),
                        true);
                hadError = true;
            } else if (!hadError) {
                clientPassword = clientCertCredentials.password;
            }

            if (!hadError) {
                //if we made it this far, validate the certificates for this connection
                validateCert(connectParts[0], Integer.parseInt(connectParts[1]),
                        output);
            }

            if (!hadError)
                NotificationUtil.getInstance().clearNotification(31346);

        } else if (!connectParts[2].equals("tcp")) {
            throw new IllegalArgumentException(
                    "Unknown protocol in connect string for streaming! ("
                            + connectString + ")");
        }

        String user = null;
        String pass = null;

        if (output.getBoolean("useAuth", false)) {
            user = output.getString("username", null);
            pass = output.getString("password", null);
            if (user == null || pass == null) {
                hadError = true;
                user = pass = null;
            }
        }

        output.putString(CotPort.CONNECT_STRING_KEY, connectString);
        output.putBoolean(CotPort.ISSTREAM_KEY, true);
        CommsMapComponent.getInstance().addStreaming(connectString, output,
                hadError, connectParts[0], port, clientCert, trustStore,
                clientPassword, trustPassword, user, pass);

        if (!output.getBoolean("temporary")) {
            if (!output.getBoolean("noPersist", false)) {
                _saveInputOutput("cot_streams", connectString, output);
            }
        }

    }

    public void addStreaming(TAKServer server) {
        addStreaming(server.getConnectString(), server.getData());
    }

    /**
     * Validate certificates for the specified host. Currently just notifies user of invalid certs,
     * does disable or affect connection attempts
     *
     * @param hostname the host name to validate
     * @param port the port to validate
     * @param b the bundle used to return the results of the validation process
     */
    private void validateCert(String hostname, int port, Bundle b) {
        Log.d(TAG, "Validating certificate for: " + hostname);

        //check if certificate is valid
        AtakCertificateDatabase.CertificateValidity validity = TLSUtils
                .validateCert(hostname, port);
        if (validity == null) {
            Log.w(TAG, "Cert invalid for: " + hostname);
            TLSUtils.promptCertificateError(context, hostname,
                    "Unable to validate TLS certificate", true);
            //leave enabled so red dot server notification will be displayed
            //b.putBoolean("enabled", false);
            CotPort.appendString(b, CotPort.ERROR_KEY, "Invalid certificate");
        } else if (!validity.isValid()) {
            Log.w(TAG, "Cert invalid for: " + hostname + ", "
                    + validity);
            TLSUtils.promptCertificateError(context, hostname,
                    "TLS Certificate invalid", true);
            //leave enabled so red dot server notification will be displayed
            //b.putBoolean("enabled", false);
            CotPort.appendString(b, CotPort.ERROR_KEY, "Invalid certificate");
        } else if (!validity.isValid(2)) {
            //expires very soon, use interactive dialog
            Log.w(TAG, "Cert expires very soon for: " + hostname + ", "
                    + validity);
            CotPort.appendString(b, CotPort.ERROR_KEY,
                    "TLS certificate expires in " + validity.daysRemaining()
                            + " days");
            TLSUtils.promptCertificateError(context, hostname,
                    "TLS certificate expires in " + validity.daysRemaining()
                            + " days. Check with your support team to avoid loss of connectivity",
                    true);
        } else if (!validity.isValid(14)) {
            //expires in a couple weeks, non interactive notification
            Log.w(TAG, "Cert expires soon for: " + hostname + ", "
                    + validity);
            CotPort.appendString(b, CotPort.ERROR_KEY,
                    "TLS certificate expires in " + validity.daysRemaining()
                            + " days");
            TLSUtils.promptCertificateError(context, hostname,
                    "TLS certificate expires in " + validity.daysRemaining()
                            + " days. Check with your support team to avoid loss of connectivity",
                    false);
        } else {
            Log.d(TAG, "Cert is valid for: " + hostname + ", "
                    + validity);
        }
    }

    /**
     * Allows for removal of a streaming connection based on it's connect string or 
     * wild card "**" to remove all streams.
     */
    public void removeStreaming(final String connectString, boolean soft) {

        if (connectString.equals("**")) {
            final File[] configs = IOProviderFactory
                    .listFiles(getConnectionsDir(context, "cot_streams"));
            if (configs != null) {
                List<String> connectionList = new ArrayList<>();
                for (File config : configs) {
                    connectionList
                            .add(configFileNameToConnectString(
                                    config.getName()));
                }
                for (String cString : connectionList) {
                    Log.d(TAG, "wildcard removal stream: " + cString);
                    CommsMapComponent.getInstance().removeStreaming(cString);
                    if (!soft)
                        _removeSavedInputOutput("cot_streams", cString);
                }
            }
        } else {
            CommsMapComponent.getInstance().removeStreaming(connectString);
            if (!soft)
                _removeSavedInputOutput("cot_streams", connectString);
        }
    }

    public void setCredentialsForStream(String connectString, String user,
            String pass) {
        CotPort port = CommsMapComponent.getInstance().removeStreaming(
                connectString);
        if (port != null) {
            CommsMapComponent.getInstance().removeStreaming(connectString);
            Bundle portBundle = port.getData();
            portBundle.putString(CotPort.USERNAME_KEY, user);
            portBundle.putString(CotPort.PASSWORD_KEY, pass);
            addStreaming(connectString, portBundle);
        }
    }

    public void setUseAuthForStream(String connectString, boolean useAuth) {
        CotPort port = CommsMapComponent.getInstance().removeStreaming(
                connectString);
        if (port != null) {
            CommsMapComponent.getInstance().removeStreaming(connectString);
            Bundle portBundle = port.getData();
            portBundle.putBoolean(CotPort.USEAUTH_KEY, useAuth);
            addStreaming(connectString, portBundle);
        }
    }

    private void _addDefaultInputs() {
        Log.d(TAG, "Creating default inputs");

        Bundle defaultInputData = new Bundle();
        defaultInputData.putString("description", "Default");
        addInput("0.0.0.0:4242:udp", defaultInputData);

        Bundle defaultTCPInputData = new Bundle();
        defaultTCPInputData.putString("description", "Default TCP");
        addInput("0.0.0.0:4242:tcp", defaultTCPInputData);

        Bundle saInputData = new Bundle();
        saInputData.putString("description", "SA Multicast");
        addInput("239.2.3.1:6969:udp", saInputData);

        Bundle saOutputData = new Bundle();
        saOutputData.putString("description", "SA Multicast");
        addOutput("239.2.3.1:6969:udp", saOutputData);

        Bundle rnInputData = new Bundle();
        rnInputData.putString("description",
                "SA Multicast: Sensor Data");
        rnInputData.putBoolean("enabled", false);
        addInput("239.5.5.55:7171:udp", rnInputData);

        Bundle onefiftytwoInputData = new Bundle();
        onefiftytwoInputData.putString("description", "PRC-152");
        addInput("0.0.0.0:10011:udp", onefiftytwoInputData);
    }

    private boolean _loadSavedOutputs() {
        final File[] outputs = IOProviderFactory
                .listFiles(getConnectionsDir(context, "cot_outputs"));
        if (outputs == null)
            return false;
        for (File config : outputs) {
            final Properties props = new Properties();
            try (FileInputStream fis = IOProviderFactory
                    .getInputStream(config)) {
                props.load(fis);
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "Failed to read output config file", e);
                // If a failure has occurred for an existing output, then the user might lose that
                // address and port without realizing it.   Go ahead an trigger a reload of all
                // of the basic "default" addresses and ports
                IOProviderFactory.delete(config);
                return false;
            }

            String desc = props.getProperty("description", "");
            String connectString = props
                    .getProperty(TAKServer.CONNECT_STRING_KEY, null);
            if (connectString != null) {
                boolean isEnabled = !props.getProperty("enabled", "1")
                        .equals("0");
                Bundle output = new Bundle();
                output.putString("description", desc);
                output.putBoolean("enabled", isEnabled);
                addOutput(connectString, output);
            }
        }
        return true;
    }

    private void upgradeLegacyConfiguration(String prefsName) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName,
                Context.MODE_PRIVATE);
        int count = prefs.getInt("count", 0);
        for (int i = 0; i < count; ++i) {
            String desc = prefs.getString("description" + i, "");
            String connectString = prefs
                    .getString(TAKServer.CONNECT_STRING_KEY + i, "");
            boolean isEnabled = prefs.getBoolean("enabled" + i, true);
            boolean isCompressed = prefs.getBoolean("compress" + i, false);
            boolean useAuth = prefs.getBoolean("useAuth" + i, false);
            boolean enrollForCertificateWithTrust = prefs.getBoolean(
                    "enrollForCertificateWithTrust" + i, false);

            long expiration = -1;
            try {
                expiration = prefs.getLong(TAKServer.EXPIRATION_KEY + i, -1);
            } catch (Exception e) {
                Log.d(TAG,
                        "Exception parsing EXPIRATION_KEY, using default value",
                        e);
            }

            Bundle input = new Bundle();
            input.putString("description", desc);
            input.putBoolean("enabled", isEnabled);
            input.putBoolean("compress", isCompressed);
            input.putBoolean("connected", false);
            input.putBoolean("useAuth", useAuth);
            input.putBoolean("enrollForCertificateWithTrust",
                    enrollForCertificateWithTrust);
            input.putLong(TAKServer.EXPIRATION_KEY, expiration);

            NetConnectString ncs = NetConnectString
                    .fromString(connectString);

            if (ncs == null) {
                Log.w(TAG, "Skipping invalid stream: " + i + ", "
                        + connectString);
                continue;
            }

            String username = null;
            String password = null;
            AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                            ncs.getHost());
            if (credentials != null) {
                username = credentials.username;
                password = credentials.password;
            }

            // pull the policy from the prefs and apply to stream
            String cacheCreds = prefs.getString("cacheCreds" + i, "");
            input.putString("cacheCreds", cacheCreds);

            // look at the policy to figure out what to apply back to stream
            if (cacheCreds != null && (cacheCreds.equals(context
                    .getString(R.string.cache_creds_both))
                    ||
                    cacheCreds.equals(context
                            .getString(R.string.cache_creds_username)))) {
                input.putString("username", (username == null) ? ""
                        : username);
            }

            if (cacheCreds != null && cacheCreds.equals(context
                    .getString(R.string.cache_creds_both))) {
                input.putString("password", (password == null) ? ""
                        : password);
            }

            _saveInputOutput(prefsName, connectString, input);
        }

        prefs.edit().clear().apply();
    }

    public static List<Properties> loadCotStreamProperties(Context context) {
        List<Properties> results = new ArrayList<>();

        final File[] streams = IOProviderFactory
                .listFiles(getConnectionsDir(context, "cot_streams"));
        if (streams == null)
            return null;
        for (File config : streams) {
            final Properties props = new Properties();
            try (FileInputStream fis = IOProviderFactory
                    .getInputStream(config)) {
                props.load(fis);
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "Failed to read streaming config file", e);
                // If a failure has occurred for an existing output, then the it probably needs to be
                // deleted.
                IOProviderFactory.delete(config);
                continue;
            }

            results.add(props);
        }

        return results;
    }

    private boolean _loadSavedStreams() {

        List<Properties> cotStreamProperties = loadCotStreamProperties(context);
        if (cotStreamProperties == null || cotStreamProperties.size() == 0) {
            return false;
        }

        for (Properties props : cotStreamProperties) {

            String desc = props.getProperty("description", "");
            String connectString = props
                    .getProperty(TAKServer.CONNECT_STRING_KEY, "");
            boolean isEnabled = !props.getProperty("enabled", "1").equals("0");
            boolean isCompressed = !props.getProperty("compress", "0")
                    .equals("0");
            boolean useAuth = !props.getProperty("useAuth", "0").equals("0");
            boolean enrollForCertificateWithTrust = !props.getProperty(
                    "enrollForCertificateWithTrust", "0").equals("0");

            Bundle input = new Bundle();
            input.putString("description", desc);
            input.putBoolean("enabled", isEnabled);
            input.putBoolean("compress", isCompressed);
            input.putBoolean("connected", false);
            input.putBoolean("useAuth", useAuth);
            input.putBoolean("enrollForCertificateWithTrust",
                    enrollForCertificateWithTrust);

            NetConnectString ncs = NetConnectString
                    .fromString(connectString);

            if (ncs == null) {
                Log.w(TAG, "Skipping invalid stream, "
                        + connectString);
                continue;
            }

            String username = null;
            String password = null;
            AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                            ncs.getHost());
            if (credentials != null) {
                username = credentials.username;
                password = credentials.password;
            }

            // pull the policy from the prefs and apply to stream
            String cacheCreds = props.getProperty("cacheCreds", "");
            input.putString("cacheCreds", cacheCreds);

            // look at the policy to figure out what to apply back to stream
            if (cacheCreds != null && (cacheCreds.equals(context
                    .getString(R.string.cache_creds_both))
                    ||
                    cacheCreds.equals(context
                            .getString(R.string.cache_creds_username)))) {
                input.putString("username", (username == null) ? ""
                        : username);
            }

            if (cacheCreds != null && cacheCreds.equals(context
                    .getString(R.string.cache_creds_both))) {
                input.putString("password", (password == null) ? ""
                        : password);
            }

            addStreaming(connectString, input);
        }
        return true;
    }

    public static File getConnectionsDir(Context context, String type) {
        File connections = new File(context.getFilesDir(), "cotservice");
        if (!FileSystemUtils.isEmpty(type))
            connections = new File(connections, type);
        return connections;
    }

    private static String connectStringToConfigFileName(String connectString) {
        return Base64.encodeToString(
                connectString.getBytes(FileSystemUtils.UTF8_CHARSET),
                Base64.NO_WRAP);
    }

    private static String configFileNameToConnectString(String filename) {
        return new String(
                Base64.decode(filename.getBytes(FileSystemUtils.UTF8_CHARSET),
                        Base64.NO_WRAP),
                FileSystemUtils.UTF8_CHARSET);
    }

    public static File getConnectionConfig(Context context, String type,
            String connectString) {
        return new File(getConnectionsDir(context, type),
                connectStringToConfigFileName(connectString));
    }

    private void _saveInputOutput(String prefsName, String connectString,
            Bundle data) {
        synchronized (propertyFileLock) {
            Properties props = new Properties();
            props.setProperty(TAKServer.CONNECT_STRING_KEY, connectString);
            boolean enabled = data.getBoolean("enabled", true);
            props.setProperty("enabled", enabled ? "1" : "0");
            setStringProperty(props, data, "description");
            props.setProperty("compress",
                    data.getBoolean("compress", false) ? "1" : "0");

            props.setProperty(TAKServer.EXPIRATION_KEY,
                    Long.toString(data.getLong(TAKServer.EXPIRATION_KEY, -1)));

            boolean useAuth = data.getBoolean("useAuth", false);
            props.setProperty("useAuth", useAuth ? "1" : "0");

            String cacheCreds = data.getString("cacheCreds");
            if (!FileSystemUtils.isEmpty(cacheCreds)) {
                props.setProperty("cacheCreds", cacheCreds);
                String cacheUsername = (cacheCreds
                        .equals(context.getString(R.string.cache_creds_both))
                        || cacheCreds
                                .equals(context
                                        .getString(
                                                R.string.cache_creds_username)))
                                                        ? data.getString(
                                                                "username")
                                                        : "";
                String cachePassword = cacheCreds
                        .equals(context.getString(R.string.cache_creds_both))
                                ? data.getString("password")
                                : "";

                NetConnectString ncs = NetConnectString
                        .fromString(connectString);

                AtakAuthenticationDatabase.saveCredentials(
                        AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                        ncs.getHost(),
                        (cacheUsername == null) ? "" : cacheUsername,
                        (cachePassword == null) ? "" : cachePassword,
                        data.getLong(TAKServer.EXPIRATION_KEY, -1));

                AtakBroadcast
                        .getInstance()
                        .sendBroadcast(
                                new Intent(
                                        CredentialsPreference.CREDENTIALS_UPDATED)
                                                .putExtra(
                                                        "type",
                                                        AtakAuthenticationCredentials.TYPE_COT_SERVICE)
                                                .putExtra("host",
                                                        ncs.getHost()));
            }

            setStringProperty(props, data, "caPassword");
            setStringProperty(props, data, "clientPassword");
            setStringProperty(props, data, "caLocation");
            setStringProperty(props, data, "certificateLocation");
            props.setProperty("enrollForCertificateWithTrust",
                    data.getBoolean("enrollForCertificateWithTrust", false)
                            ? "1"
                            : "0");

            final File configFile = getConnectionConfig(context, prefsName,
                    connectString);
            if (!IOProviderFactory.exists(configFile.getParentFile()))
                IOProviderFactory.mkdirs(configFile.getParentFile());

            try (FileOutputStream fos = IOProviderFactory
                    .getOutputStream(configFile)) {
                props.store(fos, null);
            } catch (IOException e) {
                Log.w(TAG,
                        "Failed to save network connection " + connectString);
            }
        }
    }

    private void _removeSavedInputOutput(String prefsName,
            String connectString) {

        NetConnectString ncs = NetConnectString.fromString(connectString);
        String server = ncs.getHost();
        int port = ncs.getPort();

        AtakCertificateDatabase.deleteCertificateForServer(
                AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                server);

        AtakCertificateDatabase.deleteCertificateForServerAndPort(
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                server, port);

        AtakCertificateDatabase.deleteCertificateForServerAndPort(
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                server, port);

        AtakAuthenticationDatabase.delete(
                AtakAuthenticationCredentials.TYPE_clientPassword, server);

        AtakAuthenticationDatabase.delete(
                AtakAuthenticationCredentials.TYPE_caPassword, server);

        final File configFile = getConnectionConfig(context, prefsName,
                connectString);
        IOProviderFactory.delete(configFile, IOProvider.SECURE_DELETE);
    }

    public void onDestroy() {
        ClearContentRegistry.getInstance().unregisterListener(this);
    }

    public void refreshAllStreams() {
        removeAllStreams(true);
        _loadSavedStreams();
    }

    private void removeAllStreams(boolean soft) {
        Log.d(TAG, "removeAllStreams");
        Bundle b = CommsMapComponent.getInstance().getAllPortsBundle();
        Bundle[] streams = (Bundle[]) b.getParcelableArray("streams");
        if (streams != null) {
            for (Bundle sb : streams) {
                String connStr = sb.getString(CotPort.CONNECT_STRING_KEY);
                if (connStr != null) {
                    removeStreaming(connStr, soft);
                }
            }
        }
    }

    private static void setStringProperty(Properties props, Bundle bundle,
            String key) {
        if (bundle.getString(key) != null)
            props.setProperty(key, bundle.getString(key));
    }

    @Override
    public void onClearContent(boolean clearmaps) {
        final String[] types = new String[] {
                "cot_streams", "cot_inputs", "cot_outputs"
        };
        for (String type : types) {
            final File dir = getConnectionsDir(context, type);
            final File[] inputs = IOProviderFactory.listFiles(dir);
            if (inputs != null)
                for (File config : inputs) {
                    IOProviderFactory.delete(config);
                }
            IOProviderFactory.delete(dir);
        }
    }
}
