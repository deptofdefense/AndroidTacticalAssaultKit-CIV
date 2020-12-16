package com.atakmap.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.atakmap.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
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


    public static synchronized AtakCertificateDatabaseAdapter getAdapter() {
        if (atakCertificateDatabaseAdapter == null) {
            atakCertificateDatabaseAdapter = new AtakCertificateDatabaseAdapter();
        }
        return atakCertificateDatabaseAdapter;
    }

    public static synchronized void setDeviceId(String id) {
        deviceId = id;
    }

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

    public static void dispose() {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling dispose prior to initialization!");
            }

            getAdapter().dispose();
            initialized = false;
        }
    }

    public static void clear() {
        synchronized (getAdapter().lock) {
            getAdapter().clear(databaseFile);
        }
    }

    public static byte[] getCertificate(
            String type) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling getCertificate prior to initialization!");
                return null;
            }

            return getAdapter().getCertificateForType(type, false);
        }
    }

    public static void saveCertificate(
            String type, byte[] certificate) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling saveCertificate prior to initialization!");
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

    public static byte[] getCertificateForServer(
            String type, String server) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling getCertificate prior to initialization!");
                return null;
            }

            return getAdapter().getCertificateForTypeAndServer(type, server);
        }
    }

    public static void saveCertificateForServer(
            String type, String server, byte[] certificate) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling saveCertificate prior to initialization!");
                return;
            }

            getAdapter().saveCertificateForTypeAndServer(type, server, certificate);

            // Keep the CertificateManager in sync if we're saving a CA cert
            if (type.equals(AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    public static void deleteCertificate(
            String type) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling deleteCertificate prior to initialization!");
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

    public static void deleteCertificateForServer(
            String type, String server) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling deleteCertificateForServer prior to initialization!");
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
                saveCertificateForServer(type, ncs.getHost(), contents);
            }

            return contents;
        }
    }

    public static AtakAuthenticationCredentials
        saveCertificatePassword(
            String password,
            String type, String connectString) {
        synchronized (getAdapter().lock) {

            String site = null;
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

    public static void migrateCertificatesToSqlcipher(Context context) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String defaultPassword = context.getString(R.string.defaultTrustStorePassword);

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

    public static List<X509Certificate> loadCertificate(byte[] p12, String password) {
        try {
            List<X509Certificate> results = new LinkedList<X509Certificate>();

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
         * @param days
         * @return
         */
        public boolean isValid(int days){
            final CoordinatedTime t = new CoordinatedTime().addDays(days);
            return isValid(new Date(t.getMilliseconds()));
        }

        /**
         * See if cert is valid until the specified date
         *
         * @param date
         * @return
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
         * @return
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
     * @param keystore
     * @param password
     * @return
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
        List<X509Certificate> retval = new ArrayList<X509Certificate>();
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

            // get certs for each server
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

            Log.d(TAG, "getCACerts found " + retval.size() + " certs");

            return retval;
       } catch (Exception e) {
            Log.e(TAG, "exception in getCACerts!", e);
            return null;
       }
   }
}
