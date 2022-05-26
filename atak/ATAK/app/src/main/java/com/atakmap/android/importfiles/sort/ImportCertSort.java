
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.R;
import com.atakmap.app.preferences.NetworkConnectionPreferenceFragment;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotService;
import com.atakmap.comms.TAKServer;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.net.CertificateEnrollmentClient;
import com.atakmap.net.CertificateManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Sorts P12 Certificate Files
 * 
 * 
 */
public class ImportCertSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportCertSort";
    private static final String CONTENT_TYPE = "P12 Certificate";

    private final Context _context;
    private List<Properties> initialServers = null;

    public ImportCertSort(Context context, boolean validateExt,
            boolean copyFile) {
        // Since we do not do any extra validation yet in match(), set validateExt to
        // true other wise this sorter will match everything when validateExt is passed
        // as false.
        super(".p12", "cert", true, copyFile, CONTENT_TYPE,
                context.getDrawable(ATAKConstants.getServerConnection(true)));
        _context = context;

        initialServers = CotService.loadCotStreamProperties(_context);
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        //TODO look for magic numbers to further verify valid p12
        //or if we have password, we could use KeyStore.java to validate it...
        return true;
    }

    private static void setClientCertAndPwDialog(final String connectString) {
        MapView.getMapView().post(new Runnable() {
            @Override
            public void run() {
                NetworkConnectionPreferenceFragment.getCertFile(
                        MapView.getMapView().getContext(),
                        MapView.getMapView().getContext()
                                .getString(R.string.tak_server_client_cert),
                        AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                        true, connectString);
            }
        });
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }

    private boolean importCertificateFromPreferences(
            SharedPreferences prefs,
            String caLocation, String caPassword,
            String certificateLocation, String clientPassword,
            String connectString,
            boolean enrollForCertificateWithTrust,
            String defaultPassword) {

        boolean importedCaCert = false;
        boolean importedClientCert = false;

        String location = prefs.getString(caLocation, "(built-in)");

        if (AtakCertificateDatabase.importCertificate(
                location, connectString,
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    prefs.getString(caPassword, defaultPassword),
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    connectString);

            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(caLocation);
            editor.remove(caPassword);
            editor.apply();

            importedCaCert = true;
        }

        location = prefs.getString(certificateLocation, "(built-in)");

        if (AtakCertificateDatabase.importCertificate(
                location, connectString,
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    prefs.getString(clientPassword, defaultPassword),
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    connectString);

            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(certificateLocation);
            editor.remove(clientPassword);
            editor.apply();

            importedClientCert = true;
        }

        if (importedCaCert && !importedClientCert) {
            if (connectString == null && !enrollForCertificateWithTrust) {
                setClientCertAndPwDialog(connectString);
            }
        }

        return importedCaCert || importedClientCert;
    }

    private boolean importCertificatesFromProperties(
            Properties properties,
            String caLocation, String caPassword,
            String certificateLocation, String clientPassword,
            String connectString,
            boolean enrollForCertificateWithTrust,
            String defaultPassword) {

        boolean importedCaCert = false;
        boolean importedClientCert = false;

        String location = properties.getProperty(caLocation);

        if (AtakCertificateDatabase.importCertificate(
                location, connectString,
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    properties.getProperty(caPassword, defaultPassword),
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    connectString);
            importedCaCert = true;
        }

        location = properties.getProperty(certificateLocation);

        if (AtakCertificateDatabase.importCertificate(
                location, connectString,
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                true) != null) {
            AtakCertificateDatabase.saveCertificatePassword(
                    properties.getProperty(clientPassword, defaultPassword),
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    connectString);
            importedClientCert = true;
        }

        if (importedCaCert && !importedClientCert) {
            if (connectString == null && !enrollForCertificateWithTrust) {
                setClientCertAndPwDialog(connectString);
            }
        }

        return importedCaCert || importedClientCert;
    }

    @Override
    public void finalizeImport() {
        super.finalizeImport();

        if (!_bFileSorted) {
            return;
        }

        List<Properties> cotStreamProperties = CotService
                .loadCotStreamProperties(_context);
        if (cotStreamProperties == null || cotStreamProperties.size() == 0) {
            return;
        }

        if (initialServers != null) {
            cotStreamProperties.removeAll(initialServers);
        }

        if (cotStreamProperties.size() == 0) {
            Log.e(TAG, "no valid server connections found!");
            return;
        }

        String defaultPassword = _context.getString(
                com.atakmap.app.R.string.defaultTrustStorePassword);

        String connectString = null;

        boolean enrollForCertificateWithTrust = false;

        // if we already have configured a streaming connection,
        //  associate any certificate in the 'default' slot with new connection
        if (initialServers != null && initialServers.size() != 0) {
            connectString = cotStreamProperties.get(0)
                    .getProperty(TAKServer.CONNECT_STRING_KEY, "");
        }

        // scan through the connection specific entries and see if they will use enrollment
        for (Properties properties : cotStreamProperties) {
            enrollForCertificateWithTrust |= !properties.getProperty(
                    "enrollForCertificateWithTrust", "0").equals("0");
        }

        // import default certs
        SharedPreferences defaultPrefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        boolean reconnect = importCertificateFromPreferences(
                defaultPrefs,
                "caLocation", "caPassword",
                "certificateLocation", "clientPassword",
                connectString, enrollForCertificateWithTrust,
                defaultPassword);

        // import connection specific certs
        for (Properties properties : cotStreamProperties) {

            connectString = properties
                    .getProperty(TAKServer.CONNECT_STRING_KEY, null);

            enrollForCertificateWithTrust = !properties.getProperty(
                    "enrollForCertificateWithTrust", "0").equals("0");

            Long expiration = Long.parseLong(properties.getProperty(
                    TAKServer.EXPIRATION_KEY, "-1"));

            reconnect |= importCertificatesFromProperties(
                    properties,
                    "caLocation", "caPassword",
                    "certificateLocation", "clientPassword",
                    connectString, enrollForCertificateWithTrust,
                    defaultPassword);

            if (enrollForCertificateWithTrust) {
                String description = properties.getProperty("description",
                        null);
                String cacheCreds = properties.getProperty("cacheCreds", "");
                CertificateEnrollmentClient.getInstance().enroll(
                        MapView.getMapView().getContext(),
                        description, connectString, cacheCreds, expiration,
                        null, true);
            }
        }

        String location = defaultPrefs.getString("updateServerCaLocation",
                "(built-in)");

        if (AtakCertificateDatabase.importCertificate(
                location, null,
                AtakCertificateDatabaseIFace.TYPE_UPDATE_SERVER_TRUST_STORE_CA,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    defaultPrefs.getString("updateServerCaPassword",
                            defaultPassword),
                    AtakAuthenticationCredentials.TYPE_updateServerCaPassword,
                    null);

            SharedPreferences.Editor editor = defaultPrefs.edit();
            editor.remove("updateServerCaPassword").apply();
        }

        if (reconnect) {
            CommsMapComponent.getInstance().getCotService().reconnectStreams();
        }

        for (Properties properties : cotStreamProperties) {

            properties.remove("caLocation");
            properties.remove("caPassword");
            properties.remove("certificateLocation");
            properties.remove("clientPassword");

            connectString = properties.getProperty(TAKServer.CONNECT_STRING_KEY,
                    null);
            final File configFile = CotService.getConnectionConfig(_context,
                    "cot_streams", connectString);
            if (!IOProviderFactory.exists(configFile.getParentFile()))
                IOProviderFactory.mkdirs(configFile.getParentFile());

            try (FileOutputStream fos = IOProviderFactory
                    .getOutputStream(configFile)) {
                properties.store(fos, null);
            } catch (IOException e) {
                Log.w(TAG,
                        "Failed to save network connection " + connectString);
            }
        }

        CertificateManager.getInstance().refresh();
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(CONTENT_TYPE, "application/x-pkcs12");
    }
}
