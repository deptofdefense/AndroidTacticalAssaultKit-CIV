
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.app.preferences.NetworkConnectionPreferenceFragment;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.TAKServer;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.net.CertificateEnrollmentClient;
import com.atakmap.net.CertificateManager;

import java.io.File;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;

/**
 * Sorts P12 Certificate Files
 * 
 * 
 */
public class ImportCertSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportCertSort";
    private static final String CONTENT_TYPE = "P12 Certificate";

    private final Context _context;
    private int beginServerCount = 0;

    public ImportCertSort(Context context, boolean validateExt,
            boolean copyFile) {
        // Since we do not do any extra validation yet in match(), set validateExt to
        // true other wise this sorter will match everything when validateExt is passed
        // as false.
        super(".p12", "cert", true, copyFile, CONTENT_TYPE,
                context.getDrawable(R.drawable.ic_server_success));
        _context = context;

        SharedPreferences streamingPrefs = _context.getSharedPreferences(
                "cot_streams", MODE_PRIVATE);
        if (streamingPrefs != null) {
            beginServerCount = streamingPrefs.getInt("count", 0);
        }
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

    private boolean importCertificates(
            SharedPreferences prefs,
            String caLocation, String caPassword,
            String certificateLocation, String clientPassword,
            String connectString,
            boolean enrollForCertificateWithTrust,
            String defaultPassword) {

        boolean importedCaCert = false;
        boolean importedClientCert = false;

        if (AtakCertificateDatabase.importCertificateFromPreferences(
                prefs, caLocation, connectString,
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                true) != null) {
            AtakCertificateDatabase.importCertificatePasswordFromPreferences(
                    prefs, caPassword, defaultPassword,
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    connectString, true);
            importedCaCert = true;
        }

        if (AtakCertificateDatabase.importCertificateFromPreferences(
                prefs, certificateLocation, connectString,
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                true) != null) {
            AtakCertificateDatabase.importCertificatePasswordFromPreferences(
                    prefs, clientPassword, defaultPassword,
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    connectString, true);
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

        String defaultPassword = _context.getString(
                com.atakmap.R.string.defaultTrustStorePassword);

        String connectString = null;

        boolean enrollForCertificateWithTrust = false;

        SharedPreferences streamingPrefs = _context.getSharedPreferences(
                "cot_streams", MODE_PRIVATE);
        if (streamingPrefs != null) {
            // if we already have configured a streaming connection,
            //  associate any certificate in the 'default' slot with new connection
            if (beginServerCount != 0) {
                connectString = streamingPrefs
                        .getString(TAKServer.CONNECT_STRING_KEY
                                + beginServerCount, null);
            }

            enrollForCertificateWithTrust = streamingPrefs.getBoolean(
                    "enrollForCertificateWithTrust" + beginServerCount,
                    false);
        }

        // import default certs
        SharedPreferences defaultPrefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        boolean reconnect = importCertificates(
                defaultPrefs,
                "caLocation", "caPassword",
                "certificateLocation", "clientPassword",
                connectString, enrollForCertificateWithTrust,
                defaultPassword);

        // import connection specific certs
        if (streamingPrefs != null) {
            int count = streamingPrefs.getInt("count", 0);

            for (int stream = beginServerCount; stream < count; stream++) {
                connectString = streamingPrefs
                        .getString(TAKServer.CONNECT_STRING_KEY
                                + stream, null);

                enrollForCertificateWithTrust = streamingPrefs.getBoolean(
                        "enrollForCertificateWithTrust" + stream,
                        false);

                reconnect |= importCertificates(
                        streamingPrefs,
                        "caLocation" + stream, "caPassword" + stream,
                        "certificateLocation" + stream, "clientPassword"
                                + stream,
                        connectString, enrollForCertificateWithTrust,
                        defaultPassword);

                if (enrollForCertificateWithTrust) {
                    String description = streamingPrefs.getString("description"
                            + stream, null);
                    String cacheCreds = streamingPrefs.getString("cacheCreds"
                            + stream, "");
                    CertificateEnrollmentClient.getInstance().enroll(
                            MapView.getMapView().getContext(),
                            description, connectString, cacheCreds, null, true);
                }
            }
        }

        if (AtakCertificateDatabase.importCertificateFromPreferences(
                defaultPrefs, "updateServerCaLocation", null,
                AtakCertificateDatabaseIFace.TYPE_UPDATE_SERVER_TRUST_STORE_CA,
                true) != null) {
            AtakCertificateDatabase.importCertificatePasswordFromPreferences(
                    defaultPrefs, "updateServerCaPassword", defaultPassword,
                    AtakAuthenticationCredentials.TYPE_updateServerCaPassword,
                    null, true);
        }

        if (reconnect) {
            CommsMapComponent.getInstance().getCotService().reconnectStreams();
        }

        CertificateManager.getInstance().refresh();
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(CONTENT_TYPE, "application/x-pkcs12");
    }
}
