package com.atakmap.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.filesystem.HashingUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


/**
 *
 */
public class AtakCertificateDatabase {

    public static final String TAG = "AtakCertificateDatabase";
    private static String deviceId;
    private static File databaseFile;
    private static boolean initialized = false;
    private static AtakCertificateDatabaseAdapter atakCertificateDatabaseAdapter;
    private static Context ctx;

    /**
     * Gets the adapter which is used to access the underlying database
     * @deprecated
     * use the AtakCredentialDatabase directly.
     */
    @Deprecated
    @DeprecatedApi(since = "4.3", forRemoval = false)
    @ModifierApi(since = "4.3", target = "4.6", modifiers = {
            "final"
    })
    public static synchronized AtakCertificateDatabaseAdapter getAdapter() {
        if (atakCertificateDatabaseAdapter == null) {
            atakCertificateDatabaseAdapter = new AtakCertificateDatabaseAdapter();
        }
        return atakCertificateDatabaseAdapter;
    }

    /**
     * Sets the device uid which is used as part of the password generation
     * @param id the device identifier to be used
     */
    public static synchronized void setDeviceId(String id) {
        deviceId = id;
    }

    /**
     * Gets the password which is used to initialize the sqlcipher database. The password
     * is derived from the sha256 checksum of 1) a random uuid stored in shared preferences
     * and 2) the device uid
     * @param context the context to be used when creating the shared preference
     * @param key the key to be used
     * @param deviceId the device identifier
     */
    public static synchronized String getPwd(Context context, String key, String deviceId) {
        String databaseId;
        File token = new File(context.getFilesDir(), key);
        do {
            if (token.exists()) {
                try {
                    databaseId = FileSystemUtils.copyStreamToString(token);
                    if(databaseId != null)
                        break;
                } catch (IOException ignored) {}
            }

            databaseId = UUID.randomUUID().toString();
            try {
                try (FileOutputStream fos = new FileOutputStream(token)) {
                    FileSystemUtils.write(fos, databaseId);
                }
            } catch(IOException ignored) {}
        } while(false);

        return HashingUtils.sha256sum(databaseId + deviceId);
    }

    /**
     * Initializes the certificate database by opening or creating the underlying sqlcipher
     * database. If the initialization fails, the database will be deleted and recreated
     * @param context the context to use when initializing the Certificate database
     */
    public static void initialize(Context context) {
        synchronized (getAdapter().lock) {
            try {
                if (!initialized) {
                    databaseFile = context.getDatabasePath("certificates.sqlite");

                    String pwd = null;
                    // if the default provider is in effect, use the legacy
                    // password to unlock, otherwise defer to the provider's
                    // mechanism
                    if(IOProviderFactory.isDefault()) {
                        pwd = getPwd(
                            context,
                            Base64.encodeToString(
                                    databaseFile.getAbsolutePath().getBytes(FileSystemUtils.UTF8_CHARSET), Base64.NO_WRAP),
                            deviceId);
                        if (pwd == null) {
                            return;
                        }
                    }

                    File parent = databaseFile.getParentFile();
                    if(parent != null && !IOProviderFactory.exists(parent)){
                        Log.d(TAG, "Creating private database directory: " + parent.getAbsolutePath());
                        if(!IOProviderFactory.mkdirs(parent)){
                            Log.w(TAG, "Failed to create private database directory: " + parent.getAbsolutePath());
                        }
                    }
                    initialized = getAdapter()
                            .openOrCreateDatabase(databaseFile.getAbsolutePath(), pwd) == 0;

                    if (!initialized) {
                        // initialization can fail if preference have been cleared (with seed for pwd),
                        // but database file is left. give ourselves once chance to retry. this can
                        // happen during testing of cert migration with pre-sqlcipher versions
                        clear();
                        initialized = getAdapter()
                                .openOrCreateDatabase(databaseFile.getAbsolutePath(), pwd) == 0;
                    }
                }

                if (!initialized) {
                    Log.e(TAG, "Failed to initialize! " + databaseFile.getAbsolutePath());
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception in initialize! " +  (databaseFile == null ? "" : databaseFile.getAbsolutePath()), e);
            }
        }
    }

    /**
     * Calls dispose on the database adapter, which closes the underlyign sqlcipher database
     * connection
     */
    public static void dispose() {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling dispose prior to initialization or after a clear content");
            }

            getAdapter().dispose();
            initialized = false;
        }
    }

    /**
     * Deletes the underlying database file from the filesystem
     */
    public static void clear() {
        synchronized (getAdapter().lock) {
            getAdapter().clear(databaseFile);
            initialized = false;
        }
    }

    /**
     * Gets the default certificate for the requested type. Default certificates are not associated
     * with any particular server.
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     *
     * @return p12 file for the requested certificate
     */
    public static byte[] getCertificate(
            String type) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling getCertificate prior to initialization or after a clear content");
                return null;
            }

            return getAdapter().getCertificateForType(type, false);
        }
    }

    /**
     * Sets the default certificate for the requested type. Default certificates are not associated
     * with any particular server.
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     * @param certificate containing hte p12 file for the requested certificate
     */
    public static void saveCertificate(
            String type, byte[] certificate) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling saveCertificate prior to initialization or after a clear content");
                return;
            }

            getAdapter().saveCertificateForType(type, certificate);

            // Keep the CertificateManager in sync if we're saving a CA cert
            if (type.equals(AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA) ||
                    type.equals(AtakCertificateDatabaseIFace.TYPE_UPDATE_SERVER_TRUST_STORE_CA)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    /**
     * Gets the certificate for the requested type and server
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     * @param server host of certificate being requested
     *
     * @return byte[] containing hte p12 file for the requested certificate
     */
    public static byte[] getCertificateForServer(
            String type, String server) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling getCertificateForServer prior to initialization or after a clear content");
                return null;
            }

            return getAdapter().getCertificateForTypeAndServer(type, server);
        }
    }

    /**
     * Gets the certificate for the requested type and server and port
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     * @param server host of certificate being requested
     * @param port port of certificate being requested
     *
     * @return byte[] containing hte p12 file for the requested certificate
     */
    public static byte[] getCertificateForServerAndPort(
            String type, String server, final int port) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling getCertificateForTypeAndServerAndPort prior to initialization or after a clear content");
                return null;
            }

            return getAdapter().getCertificateForTypeAndServerAndPort(type, server, port);
        }
    }

    /**
     * Sets the certificate for the requested type and server
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     * @param server host of certificate being saved
     * @param certificate containing hte p12 file for the requested certificate
     */
    public static void saveCertificateForServer(
            String type, String server, byte[] certificate) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling saveCertificate prior to initialization  or after a clear content");
                return;
            }

            getAdapter().saveCertificateForTypeAndServer(type, server, certificate);

            // Keep the CertificateManager in sync if we're saving a CA cert
            if (type.equals(AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    /**
     * Sets the certificate for the requested type and server and port
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     * @param server host of certificate being saved
     * @param port port number that the certificate is associated with
     * @param certificate containing hte p12 file for the requested certificate
     */
    public static void saveCertificateForServerAndPort(
            String type, String server, int port, byte[] certificate) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling saveCertificate prior to initialization  or after a clear content");
                return;
            }

            getAdapter().saveCertificateForTypeAndServerAndPort(type, server, port, certificate);

            // Keep the CertificateManager in sync if we're saving a CA cert
            if (type.equals(AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    /**
     * deletes the default certificate of the requested type
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     */
    public static void deleteCertificate(
            String type) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling deleteCertificate prior to initialization  or after a clear content");
                return;
            }

            boolean rc = getAdapter().deleteCertificateForType(type);
            if (!rc) {
                Log.e(TAG, "deleteCertificate returned " + rc);
                return;
            }

            // Keep the CertificateManager in sync if we're deleting a CA cert
            if (type.equals(AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA)) {
                CertificateManager.getInstance().refresh();
            }
        }
     }

    /**
     * deletes the certificate for the requested type and server
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     * @param server host of certificate being saved
     */
    public static void deleteCertificateForServer(
            String type, String server) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling deleteCertificateForServer prior to initialization or after a clear content");
                return;
            }

            boolean rc = getAdapter().deleteCertificateForTypeAndServer(type, server);
            if (!rc) {
                Log.e(TAG, "deleteCertificateForServer returned " + rc);
                return;
            }

            // Keep the CertificateManager in sync if we're deleting a CA cert
            if (type.equals(AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    /**
     * deletes the certificate for the requested type and server
     *
     * @param type the type of certificate being requested, ATAK core defines a set of TYPE constants
     *            in AtakCertificateDatabaseIFace for the certificate types used in core. Plugins
     *             may specify any unique type
     * @param server host of certificate being saved
     */
    public static void deleteCertificateForServerAndPort(
            String type, String server, int port) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "calling deleteCertificateForTypeAndServerPort prior to initialization or after a clear content");
                return;
            }

            boolean rc = getAdapter().deleteCertificateForTypeAndServerAndPort(type, server, port);
            if (!rc) {
                Log.e(TAG, "deleteCertificateForTypeAndServerPort returned " + rc);
                return;
            }

            // Keep the CertificateManager in sync if we're deleting a CA cert
            if (type.equals(AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    /**
     * Imports certificates being imported from mission packages into the certificate database
     *
     * @param location location on disk to load certificate from
     * @param connectString if present, the certificate will associated with the host from the
     *                      connectString
     * @param type the type of certificate being import
     * @param delete boolean indicating if the certificate should be deleted after import
     */
    public static byte[] importCertificate(
            String location,
            String connectString,
            String type, boolean delete) {
        synchronized (getAdapter().lock) {

            if (location == null) {
                Log.e(TAG, "null location in importCertificate!");
                return null;
            }

            if (location.equals("(built-in)")) {
                return null;
            }

            File file = new File(location);
            if (!FileSystemUtils.isFile(file)) {
                file = FileSystemUtils.getItem(location);
            }
            if (!FileSystemUtils.isFile(file)) {
                return null;
            }

            byte[] contents;
            try {
                contents = FileSystemUtils.read(file);
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to read cert from: "
                        + file.getAbsolutePath(), ioe);
                return null;
            }

            if (delete) {
                FileSystemUtils.deleteFile(file);
            }

            if (connectString == null || connectString.length() == 0) {
                saveCertificate(type, contents);
            } else {
                NetConnectString ncs = NetConnectString.fromString(connectString);
                saveCertificateForServerAndPort(type, ncs.getHost(), ncs.getPort(), contents);
            }

            return contents;
        }
    }

    /**
     * Saves the certificate password within the AtakAuthenticationDatabase
     *
     * @param password password for the certificate
     * @param type certificate type
     * @param connectString if present, the password will be associated with the host within
     *                      the connectString
     */
    public static AtakAuthenticationCredentials
        saveCertificatePassword(
            String password,
            String type, String connectString) {
        synchronized (getAdapter().lock) {

            String site;
            if (connectString == null) {
                site = type;
            } else {
                NetConnectString ncs = NetConnectString.fromString(connectString);
                site = ncs.getHost();
            }

            AtakAuthenticationDatabase.saveCredentials(type, site, "", password, false);

            return AtakAuthenticationDatabase.getCredentials(type, site);
        }
    }

    /**
     * Migrates legacy certificates to sqlcipher
     *
     * @param context the context to use for getting the resources
     */
    public static void migrateCertificatesToSqlcipher(Context context) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int id = context.getResources().getIdentifier("defaultTrustStorePassword", "string", context.getPackageName());
        String defaultPassword = context.getString(id);

        if (importCertificate(
                prefs.getString("caLocation", "(built-in)"),
                null,
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    prefs.getString("caPassword", defaultPassword),
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    null);

            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("caPassword").apply();
        }

        if (importCertificate(
                prefs.getString("certificateLocation", "(built-in)"),
                null,
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    prefs.getString("clientPassword", defaultPassword),
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    null);

            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("clientPassword").apply();
        }
    }

    /**
     * Helper method that extracts a list of x509 certificates from the encrypted container
     *
     * @param p12 encrypted certificate container
     * @param password certificate container password
     */
    public static List<X509Certificate> loadCertificate(byte[] p12, String password) {
        try {
            List<X509Certificate> results = new LinkedList<>();

            ByteArrayInputStream caCertStream = new ByteArrayInputStream(p12);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(caCertStream, password.toCharArray());
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                X509Certificate cert = (X509Certificate) ks.getCertificate(aliases.nextElement());
                results.add(cert);
            }

            Log.d(TAG, "loadCertificate found " + results.size() + " certs");
            return results;
        }
        catch (Exception e) {
            Log.e(TAG, "Exception in loadCertificate!", e);
        }
        return null;
    }


    public static class CertificateValidity{

        final public X509Certificate certificate;
        final private boolean valid;
        final public String error;
        final public Date certNotAfter;

        public CertificateValidity(X509Certificate certificate, boolean valid, String error, Date certNotAfter) {
            this.certificate = certificate;
            this.valid = valid;
            this.error = error;
            this.certNotAfter = certNotAfter;
        }

        public boolean isValid(){
            return certificate != null && valid;
        }

        /**
         * See if the cert if valid from now until specified number of days
         *
         * @param days the number of days in the future from the current date.
         * @return true if the certificate will still be valid.
         */
        public boolean isValid(final int days){
            final CoordinatedTime t = new CoordinatedTime().addDays(days);
            return isValid(new Date(t.getMilliseconds()));
        }

        /**
         * See if cert is valid until the specified date
         *
         * @param date an absolute date and time
         * @return true if the certificate is still valid.
         */
        public boolean isValid(Date date){
            if(!isValid() || date == null || certNotAfter == null)
                return false;

            long timeLeft = certNotAfter.getTime() - date.getTime();
            return timeLeft > 0;
        }

        /**
         * See how many days are remaining for this cert
         *
         * @return the number of days that the certificate is still valid
         */
        public long daysRemaining(){
            if(!valid || certNotAfter == null)
                return -1;

            Date now = CoordinatedTime.currentDate();
            long timeLeft = certNotAfter.getTime() - now.getTime();
            if(timeLeft < 0)
                return -1;

            return timeLeft / (24 * 3600 * 1000);
        }

        @Override
        public String toString() {
            if(certificate == null || certificate.getSubjectDN() == null)
                return super.toString();

            return certificate.getSubjectDN().getName() + ": " + valid;
        }
    }

    /**
     * Check if cert if valid, and will be beyond the specified date
     *
     * @param keystore the keystore for the certificate
     * @param password the password for the keystore
     * @return the state of the certificate as described by the CertificateValidity.
     */
    public static CertificateValidity checkValidity(byte[] keystore, String password) {

        List<X509Certificate> certificates = loadCertificate(keystore, password);
        if (FileSystemUtils.isEmpty(certificates)) {
            Log.w(TAG, "loadCertificate failed within checkValidity, no certificates");
            return null;
        }

        //Date now = CoordinatedTime.currentDate();
        //Log.d(TAG, "now: " + now.toString());
        for (X509Certificate certificate : certificates) {
            try {
                certificate.checkValidity();
            } catch (CertificateExpiredException cee) {
                Log.e(TAG, "Found an expired certificate!", cee);
                return new CertificateValidity(certificate, false, cee.getMessage(), certificate.getNotAfter());
            } catch (CertificateNotYetValidException cnyve) {
                Log.e(TAG, "Found a not yet valid certificate!", cnyve);
                return new CertificateValidity(certificate, false, cnyve.getMessage(), certificate.getNotAfter());
            }

            //TODO dont break loop. check other certs? return first one to expire?
            return new CertificateValidity(certificate, true, null, certificate.getNotAfter());
        }

        Log.w(TAG, "loadCertificate failed within checkValidity");
        return null;
    }

    /**
     * Helper method that concerts the given x509 certificate to a PEM formatted String
     *
     * @param cert X509Certificate
     * @return PEM formatted String
     */
    protected static String convertToPem(X509Certificate cert)
            throws CertificateEncodingException {
        String cert_begin = "-----BEGIN CERTIFICATE-----\n";
        String end_cert = "-----END CERTIFICATE-----";
        byte[] derCert = cert.getEncoded();

        String encoded = new String(Base64.encode(derCert, Base64.DEFAULT), FileSystemUtils.UTF8_CHARSET);
        String pemCert = cert_begin + encoded + end_cert;
        return pemCert;
    }

    public static List<X509Certificate> getCACerts() {
        List<X509Certificate> retval = new ArrayList<>();
        try {

            // get the default ca cert
            byte[] caCertP12 = getCertificate(AtakCertificateDatabaseAdapter.TYPE_TRUST_STORE_CA);
            AtakAuthenticationCredentials caCertCredentials =
                    AtakAuthenticationDatabase.getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword);
            if (caCertP12 != null && caCertCredentials != null
                    && !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                List<X509Certificate> caCerts = loadCertificate(caCertP12, caCertCredentials.password);
                if (caCerts != null) {
                    retval.addAll(caCerts);
                }
            }

            // see if we have an update server ca cert
            caCertP12 = getCertificate(AtakCertificateDatabaseAdapter.TYPE_UPDATE_SERVER_TRUST_STORE_CA);
            caCertCredentials =
                    AtakAuthenticationDatabase.getCredentials(
                            AtakAuthenticationCredentials.TYPE_updateServerCaPassword);
            if (caCertP12 != null && caCertCredentials != null
                    && !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                List<X509Certificate> caCerts = loadCertificate(caCertP12, caCertCredentials.password);
                if (caCerts != null) {
                    retval.addAll(caCerts);
                }
            }

            // get legacy certs
            String[] servers = atakCertificateDatabaseAdapter.getServers(
                    AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA);
            if (servers != null) {
                for (String server : servers) {
                    caCertP12 = getCertificateForServer(
                            AtakCertificateDatabaseAdapter.TYPE_TRUST_STORE_CA, server);
                    caCertCredentials = AtakAuthenticationDatabase.getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword, server);
                    if (caCertP12 != null && caCertCredentials != null &&
                            !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                        List<X509Certificate> caCerts = loadCertificate(
                                caCertP12, caCertCredentials.password);
                        if (caCerts != null) {
                            retval.addAll(caCerts);
                        }
                    }
                }
            }

            // get certs for each server
            NetConnectString[] netConnectStrings = atakCertificateDatabaseAdapter.getServerConnectStrings(
                    AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA);
            if (netConnectStrings != null) {
                for (NetConnectString netConnectString : netConnectStrings) {
                    caCertP12 = getAdapter().getCertificateForTypeAndServerAndPort(
                            AtakCertificateDatabaseAdapter.TYPE_TRUST_STORE_CA,
                            netConnectString.getHost(), netConnectString.getPort());
                    caCertCredentials = AtakAuthenticationDatabase.getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword, netConnectString.getHost());
                    if (caCertP12 != null && caCertCredentials != null &&
                            !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                        List<X509Certificate> caCerts = loadCertificate(
                                caCertP12, caCertCredentials.password);
                        if (caCerts != null) {
                            retval.addAll(caCerts);
                        }
                    }
                }
            }

            Log.d(TAG, "getCACerts found " + retval.size() + " certs");

            return retval;
       } catch (Exception e) {
            Log.e(TAG, "exception in getCACerts!", e);
            return null;
       }
   }
}
