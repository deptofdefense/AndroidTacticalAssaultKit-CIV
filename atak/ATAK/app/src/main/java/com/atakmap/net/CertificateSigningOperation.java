
package com.atakmap.net;

import android.content.Context;
import android.os.Bundle;
import android.util.Base64;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.comms.http.HttpUtil;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

public class CertificateSigningOperation extends HTTPOperation {

    private static final String TAG = "CertificateSigningOperation";

    public static final String PARAM_CERTIFICATE_SIGNING_REQUEST = com.atakmap.net.CertificateSigningOperation.class
            .getName()
            + ".PARAM_CERTIFICATE_SIGNING_REQUEST";

    public static final String PARAM_SIGNED_CERTIFICATE = com.atakmap.net.CertificateSigningOperation.class
            .getName()
            + ".PARAM_SIGNED_CERTIFICATE";

    public static final int DEFAULT_SO_TIMEOUT_MS = 30000;

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException {

        TakHttpClient httpClient = null;
        try {
            // Get request data
            CertificateSigningRequest signingRequest = (CertificateSigningRequest) request
                    .getParcelable(
                            CertificateSigningOperation.PARAM_CERTIFICATE_SIGNING_REQUEST);
            if (signingRequest == null) {
                throw new DataException("Unable to serialize CSR request");
            }

            final String baseUrl = "https://"
                    + signingRequest.getServer()
                    +
                    SslNetCotPort
                            .getServerApiPath(
                                    SslNetCotPort.Type.CERT_ENROLLMENT);

            SSLSocketFactory sslSocketFactory = CertificateManager
                    .getSockFactory(
                            false, baseUrl,
                            signingRequest.getAllowAllHostnames());

            httpClient = new TakHttpClient(baseUrl, sslSocketFactory,
                    HttpUtil.DEFAULT_CONN_TIMEOUT_MS, DEFAULT_SO_TIMEOUT_MS);

            String path = "/api/tls/signClient?clientUid="
                    + MapView.getDeviceUid()
                    + "&version=" + URLEncoder.encode(
                            ATAKConstants.getVersionName(),
                            FileSystemUtils.UTF8_CHARSET.name());

            // setup credentials for basic auth
            AtakAuthenticationCredentials credentials = new AtakAuthenticationCredentials();
            credentials.username = signingRequest.getUsername();
            credentials.password = signingRequest.getPassword();

            // submit the CSR
            HttpPost httpPost = new HttpPost(baseUrl + path);
            StringEntity se = new StringEntity(signingRequest.getCSR());
            httpPost.setEntity(se);
            TakHttpResponse response = httpClient
                    .execute(httpPost, credentials);
            response.verifyOk();

            // read the response into a byte array
            HttpEntity resEntity = response.getEntity();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resEntity.writeTo(baos);
            byte[] keystore = baos.toByteArray();
            baos.close();

            // bundle up the results
            Bundle output = new Bundle();
            output.putParcelable(PARAM_CERTIFICATE_SIGNING_REQUEST,
                    signingRequest);
            output.putByteArray(PARAM_SIGNED_CERTIFICATE, keystore);

            processResults(context, output);

            return output;

        } catch (TakHttpException e) {
            Log.e(TAG, "CertificateSigningRequest failed!", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "CertificateSigningRequest failed!", e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        } finally {
            try {
                if (httpClient != null)
                    httpClient.shutdown();
            } catch (Exception e) {
                Log.d(TAG, "Failed to shutdown the client", e);
            }
        }
    }

    private void processResults(Context context, Bundle resultData)
            throws Exception {
        if (resultData == null) {
            Log.e(TAG,
                    "CertificateSigningRequest Failed - Unable to sign request");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate signing request failed",
                    "Certificate signing request failed");
            return;
        }

        // get the initial request that was sent out
        CertificateSigningRequest initialRequest = resultData
                .getParcelable(
                        CertificateSigningOperation.PARAM_CERTIFICATE_SIGNING_REQUEST);
        if (initialRequest == null || !initialRequest.isValid()) {
            Log.e(TAG,
                    "CertificateSigningRequest - Unable to parse request");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate signing request failed",
                    "Certificate signing request failed");
            return;
        }

        NetConnectString ncs = NetConnectString
                .fromString(initialRequest.getConnectString());

        // retrieve the private key from sqlcipher
        byte[] privateKey = AtakCertificateDatabase.getCertificate(
                AtakCertificateDatabaseIFace.TYPE_PRIVATE_KEY);

        // guard against a null return
        if (privateKey == null) {
            Log.d(TAG, "private key is null");
            return;
        }

        String privateKeyPem = new String(privateKey,
                FileSystemUtils.UTF8_CHARSET);

        // copy the signed client cert and the ca cert out of the response
        byte[] resultStore = resultData.getByteArray(
                CertificateSigningOperation.PARAM_SIGNED_CERTIFICATE);
        if (resultStore == null) {
            Log.d(TAG, "result store is null");
            return;
        }

        // load up the results into a keystore
        ByteArrayInputStream bais = new ByteArrayInputStream(resultStore);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        String defaultPassword = context
                .getString(R.string.defaultTrustStorePassword);
        ks.load(bais, defaultPassword.toCharArray());
        bais.close();

        // create a new trust store to hold the trust chain
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);

        // iterate across the certs that came back from the signing request to extract
        // the signing cert and trust chain
        String certPem = null;
        List<String> caPemList = new ArrayList<>();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            String pem = AtakCertificateDatabase.convertToPem(cert);
            if (alias.compareTo("signedCert") == 0) {
                certPem = pem;
            } else {
                caPemList.add(pem);
                trustStore.setCertificateEntry(alias, cert);
            }
        }

        //
        // get the truststore password
        //
        AtakAuthenticationCredentials truststoreCreds = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_caPassword,
                        initialRequest.getServer());

        String truststorePassword;
        if (truststoreCreds == null
                || FileSystemUtils.isEmpty(truststoreCreds.password)) {
            truststorePassword = UUID.randomUUID().toString();
        } else {
            truststorePassword = truststoreCreds.password;
        }

        //
        // get the keystore password
        //
        AtakAuthenticationCredentials keystoreCreds = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_clientPassword,
                        initialRequest.getServer());

        if (keystoreCreds == null
                || FileSystemUtils.isEmpty(keystoreCreds.password)) {
            Log.e(TAG,
                    "CertificateSigningRequest - Error retrieving keystore password");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate signing request failed",
                    "Certificate signing request failed");
            return;
        }
        String keystorePassword = keystoreCreds.password;

        // write out the truststore
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        trustStore.store(baos, truststorePassword.toCharArray());
        byte[] trustStoreP12 = baos.toByteArray();

        // generate the client keystore
        String friendlyName = "TAK Client Cert";
        String encodedKeystore = CommsMapComponent.getInstance()
                .generateKeystore(
                        certPem,
                        caPemList,
                        privateKeyPem,
                        keystorePassword,
                        friendlyName);

        if (FileSystemUtils.isEmpty(encodedKeystore)) {
            Log.e(TAG, "generateKeystore failed!");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate signing request failed",
                    "Certificate signing request failed");
            return;
        }

        byte[] p12 = Base64.decode(encodedKeystore, Base64.DEFAULT);

        // store the client certificate password
        AtakAuthenticationDatabase.saveCredentials(
                AtakAuthenticationCredentials.TYPE_clientPassword,
                initialRequest.getServer(),
                "", keystorePassword, false);

        // store the client certificate
        AtakCertificateDatabase.saveCertificateForServerAndPort(
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                initialRequest.getServer(), ncs.getPort(),
                p12);

        if (!initialRequest.hasTruststore()) {
            // store the CA certificate password
            AtakAuthenticationDatabase.saveCredentials(
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    initialRequest.getServer(),
                    "", truststorePassword, false);

            // store the CA certificate
            AtakCertificateDatabase.saveCertificateForServerAndPort(
                    AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                    initialRequest.getServer(), ncs.getPort(),
                    trustStoreP12);
        }

        // clear out any cached socket factories for this server
        CertificateManager.invalidate(initialRequest.getServer());

        if (initialRequest.getProfile()) {
            CertificateEnrollmentClient.getInstance()
                    .executeDeviceProfileRequest(initialRequest);
        } else {
            //
            // reconnect streams only if we're not about to retrieve an enrollment profile.
            // the reconnect will trigger a query for a connection profile, so we'll delay
            // reconnect until after enrollment profile is processed
            //

            CommsMapComponent.getInstance().getCotService()
                    .reconnectStreams();
        }
    }
}
