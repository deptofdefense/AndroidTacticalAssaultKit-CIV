
package com.atakmap.net;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.certconfig.CertificateConfig;
import com.atakmap.net.certconfig.NameEntry;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.client.methods.HttpGet;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;

public class CertificateConfigOperation extends HTTPOperation {

    private static final String TAG = "CertificateConfigOperation";

    public static final String PARAM_CONFIG_REQUEST = com.atakmap.net.CertificateConfigOperation.class
            .getName()
            + ".PARAM_CONFIG_REQEUST";

    public static final String PARAM_CONFIG_RESPONSE = com.atakmap.net.CertificateConfigOperation.class
            .getName()
            + ".PARAM_CONFIG_RESPONSE";

    public static final String PARAM_CONFIG_RESPONSE_SERVER_CERTS = com.atakmap.net.CertificateConfigOperation.class
            .getName()
            + ".PARAM_CONFIG_RESPONSE_SERVER_CERTS";

    public static final String PARAM_CONFIG_RESPONSE_PEER_UNVERIFIED = com.atakmap.net.CertificateConfigOperation.class
            .getName()
            + ".PARAM_CONFIG_RESPONSE_PEER_UNVERIFIED";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException {

        TakHttpClient httpClient = null;
        CertificateManager.ExtendedSSLSocketFactory sslSocketFactory;

        try {
            // Get request data
            CertificateConfigRequest configRequest = (CertificateConfigRequest) request
                    .getParcelable(
                            CertificateConfigOperation.PARAM_CONFIG_REQUEST);
            if (configRequest == null) {
                throw new DataException("Unable to serialize config request");
            }

            final String baseUrl = "https://"
                    + configRequest.getServer()
                    +
                    SslNetCotPort
                            .getServerApiPath(
                                    SslNetCotPort.Type.CERT_ENROLLMENT);

            sslSocketFactory = CertificateManager.getSockFactory(
                    false, baseUrl, configRequest.getAllowAllHostnames());

            httpClient = new TakHttpClient(baseUrl, sslSocketFactory);

            String path = "/api/tls/config";

            AtakAuthenticationCredentials credentials = new AtakAuthenticationCredentials();
            credentials.username = configRequest.getUsername();
            credentials.password = configRequest.getPassword();

            HttpGet httpget = new HttpGet(baseUrl + path);
            TakHttpResponse response = null;
            X509Certificate[] serverCerts;

            Bundle output = new Bundle();
            output.putParcelable(PARAM_CONFIG_REQUEST, configRequest);

            boolean sslError = false;

            try {
                response = httpClient.execute(httpget, credentials);
            } catch (SSLPeerUnverifiedException sslPeerUnverifiedException) {
                sslError = true;
                output.putBoolean(PARAM_CONFIG_RESPONSE_PEER_UNVERIFIED, true);
            } catch (SSLException sslException) {
                final String message = sslException.getMessage();
                if (message != null && message
                        .contains("hostname in certificate didn't match")) {
                    sslError = true;
                    if (sslSocketFactory == null)
                        throw sslException;
                    serverCerts = sslSocketFactory.getServerCerts();
                    Bundle certBundle = CertificateManager
                            .certArrayToBundle(serverCerts);
                    output.putBundle(PARAM_CONFIG_RESPONSE_SERVER_CERTS,
                            certBundle);
                } else {
                    throw sslException;
                }
            }

            if (!sslError) {
                response.verifyOk();
                HttpEntity resEntity = response.getEntity();
                String config = EntityUtils.toString(resEntity);
                output.putString(PARAM_CONFIG_RESPONSE, config);
                processResults(context, output);
            }

            return output;

        } catch (TakHttpException e) {
            Log.e(TAG, "CertificateConfigRequest failed!", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "CertificateConfigRequest failed!", e);
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
                    "CertificateConfigRequest Failed - Unable to obtain configuration");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate config request failed",
                    "Certificate config request failed");
            return;
        }

        // the initial request that was sent out
        CertificateConfigRequest initialRequest = resultData
                .getParcelable(CertificateConfigOperation.PARAM_CONFIG_REQUEST);
        if (initialRequest == null || !initialRequest.isValid()) {
            Log.e(TAG,
                    "CertificateConfigRequest - Unable to parse request");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate config request failed",
                    "Certificate config request failed");
            return;
        }

        // extract the cert configuration
        String xml = resultData.getString(
                CertificateConfigOperation.PARAM_CONFIG_RESPONSE);
        Serializer serializer = new Persister();
        CertificateConfig config = serializer
                .read(
                        com.atakmap.net.certconfig.CertificateConfig.class,
                        xml);

        //
        // if we have an existing password use it, otherwise generate a new uuid
        //

        String keystorePassword;
        AtakAuthenticationCredentials keystoreCreds = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_clientPassword,
                        initialRequest.getServer());

        if (keystoreCreds == null
                || FileSystemUtils.isEmpty(keystoreCreds.password)) {
            keystorePassword = UUID.randomUUID().toString();
        } else {
            keystorePassword = keystoreCreds.password;
        }

        //
        // generate a new private key and store it
        //
        String privateKey = CommsMapComponent.getInstance()
                .generateKey(keystorePassword);
        if (FileSystemUtils.isEmpty(privateKey)) {
            Log.e(TAG, "generateKey failed!");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate config request failed",
                    "Certificate config request failed");
            return;
        }

        AtakCertificateDatabase.saveCertificate(
                AtakCertificateDatabaseAdapter.TYPE_PRIVATE_KEY,
                privateKey.getBytes(FileSystemUtils.UTF8_CHARSET));

        AtakAuthenticationDatabase.saveCredentials(
                AtakAuthenticationCredentials.TYPE_clientPassword,
                initialRequest.getServer(),
                "", keystorePassword, false);

        //
        // generate and submit the csr
        //

        // set the CN to the current username
        Map<String, String> dnEntries = new HashMap<>();
        dnEntries.put("CN", initialRequest.getUsername());

        // add in all required fields
        for (NameEntry nameEntry : config.getNameEntries()) {
            dnEntries.put(
                    nameEntry.getName(), nameEntry.getValue());
        }

        String signingRequest = CommsMapComponent.getInstance()
                .generateCSR(dnEntries, privateKey, keystorePassword);
        if (FileSystemUtils.isEmpty(signingRequest)) {
            Log.e(TAG, "generateCSR failed!");
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    context.getString(R.string.connection_error),
                    "Certificate config request failed",
                    "Certificate config request failed");
            return;
        }

        signingRequest = signingRequest.replace(
                "-----BEGIN CERTIFICATE REQUEST-----", "");
        signingRequest = signingRequest.replace(
                "-----END CERTIFICATE REQUEST-----", "");

        CertificateSigningRequest csr = new CertificateSigningRequest(
                initialRequest.getConnectString(),
                initialRequest.getUsername(),
                initialRequest.getPassword(),
                initialRequest.hasTruststore(),
                initialRequest.getAllowAllHostnames(),
                signingRequest);

        CertificateEnrollmentClient.getInstance().executeSigningRequest(csr);
    }
}
