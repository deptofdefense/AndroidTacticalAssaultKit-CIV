
package com.atakmap.net;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.app.CredentialsDialog;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import java.security.cert.X509Certificate;
import java.util.List;

public class CertificateEnrollmentClient implements
        RequestManager.RequestListener, CredentialsDialog.Callback {

    public enum CertificateEnrollmentStatus {
        SUCCESS,
        BAD_CREDENTIALS,
        ERROR
    }

    public interface CertificateEnrollmentCompleteCallback {
        void onCertificateEnrollmentComplete(
                CertificateEnrollmentStatus status);
    }

    private static CertificateEnrollmentClient instance = null;

    private CertificateEnrollmentClient() {
    }

    public synchronized static CertificateEnrollmentClient getInstance() {
        if (instance == null) {
            instance = new CertificateEnrollmentClient();
        }
        return instance;
    }

    private CertificateEnrollmentCompleteCallback certificateEnrollmentCompleteCallback = null;
    protected static final String TAG = "CertificateEnrollmentClient";
    private ProgressDialog progressDialog;
    private Context context;
    private boolean getProfile;

    private MapView view;
    private Context appCtx;

    public final static int REQUEST_TYPE_CERTIFICATE_CONFIG;
    public final static int REQUEST_TYPE_CERTIFICATE_SIGNING;

    static {
        REQUEST_TYPE_CERTIFICATE_CONFIG = NetworkOperationManager
                .register(
                        "com.atakmap.net.CertificateConfigOperation",
                        new CertificateConfigOperation());

        REQUEST_TYPE_CERTIFICATE_SIGNING = NetworkOperationManager
                .register(
                        "com.atakmap.net.CertificateSigningOperation",
                        new CertificateSigningOperation());
    }

    public void enroll(final Context context, final String desc,
            final String connectString, final String cacheCreds, final Long expiration,
            CertificateEnrollmentCompleteCallback certificateEnrollmentCompleteCallback,
            final boolean getProfile) {
        this.context = context;
        this.getProfile = getProfile;
        this.certificateEnrollmentCompleteCallback = certificateEnrollmentCompleteCallback;

        this.view = MapView.getMapView();
        this.appCtx = view.getContext();

        view.post(new Runnable() {
            @Override
            public void run() {
                progressDialog = new ProgressDialog(context);
                progressDialog.setTitle(
                        appCtx.getString(R.string.enroll_client_title));
                progressDialog.setIcon(
                        com.atakmap.android.util.ATAKConstants.getIconId());
                progressDialog.setMessage(appCtx.getString(
                        R.string.enroll_client_message));
            }
        });

        NetConnectString ncs = NetConnectString.fromString(connectString);

        if (ncs == null) {
            Log.e(TAG, "could not enroll for a bad connectString: "
                    + connectString, new Exception());
            return;
        }

        // clear out any current client cert for this server
        AtakCertificateDatabase.deleteCertificateForServer(
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                ncs.getHost());

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                        ncs.getHost());

        String username = null;
        String password = null;

        if (credentials != null) {
            username = credentials.username;
            password = credentials.password;
        }

        if (username == null || FileSystemUtils.isEmpty(username) ||
                password == null || FileSystemUtils.isEmpty(password)) {
            view.post(new Runnable() {
                @Override
                public void run() {
                    CredentialsDialog.createCredentialDialog(
                            desc, connectString, "", "", cacheCreds, expiration,
                            context, CertificateEnrollmentClient.this);
                }
            });
        } else {
            CertificateConfigRequest request = new CertificateConfigRequest(
                    connectString, cacheCreds, desc, username, password, expiration);
            verifyTrust(request);
        }
    }

    @Override
    public void onCredentialsEntered(String connectString, String cacheCreds,
            String description,
            String username, String password, Long expiration) {

        CommsMapComponent.getInstance().getCotService()
                .setCredentialsForStream(connectString, username, password);

        CertificateConfigRequest request = new CertificateConfigRequest(
                connectString, cacheCreds, description, username, password, expiration);

        verifyTrust(request);
    }

    @Override
    public void onCredentialsCancelled(String connectString) {
        Log.d(TAG, "cancelled out of CredentialsDialog");
    }

    private void verifyTrust(final CertificateConfigRequest request) {
        try {
            byte[] truststore = AtakCertificateDatabase
                    .getCertificateForServer(
                            AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                            request.getServer());

            request.setSaveAsDefault(false);
            request.setHasTruststore(truststore != null);
            request.setAllowAllHostnames(truststore != null);
            executeConfigRequest(request);

        } catch (Exception e) {
            Log.e(TAG, "Exception in post!", e);
        }
    }

    private void executeConfigRequest(CertificateConfigRequest request) {
        if (request == null || !request.isValid()) {
            Log.w(TAG, "Invalid CertificateConfigRequest!");
            return;
        }

        // notify user
        Log.d(TAG,
                "CertificateConfigRequest created for: " + request.toString());

        showProgress(true);

        // Kick off async HTTP request to post to server
        HTTPRequestManager.from(appCtx).execute(
                request.createCertificateConfigRequest(), this);
    }

    public void executeSigningRequest(CertificateSigningRequest request) {
        if (request == null || !request.isValid()) {
            Log.w(TAG, "Invalid CertificateSigningRequest!");
            return;
        }

        // notify user
        Log.d(TAG,
                "CertificateSigningRequest created for: " + request.toString());

        showProgress(true);

        request.setGetProfile(getProfile);

        // Kick off async HTTP request to post to server
        HTTPRequestManager.from(appCtx).execute(
                request.createCertificateSigningRequest(), this);
    }

    /**
     * The enrollment succeeded, force check for the enrollment profile
     * @param request the certificate signing request to use.
     */
    public void executeDeviceProfileRequest(
            final CertificateSigningRequest request) {
        if (request == null || !request.isValid()) {
            Log.w(TAG, "Invalid CertificateSigningRequest!");
            showProgress(false);
            return;
        }

        Log.d(TAG, "retrieving enrollment profile");
        if (!DeviceProfileClient.getInstance().getProfile(
                context,
                request.getServer(),
                request.getUsername(),
                request.getPassword(),
                request.getAllowAllHostnames(),
                true, false, -1, new DeviceProfileCallback(context) {

                    @Override
                    public void onDeviceProfileRequestComplete(boolean status,
                            Bundle resultData) {
                        Log.d(TAG,
                                "onDeviceProfileRequestComplete finished successfully: "
                                        + request.toString());

                        showProgress(false);
                        if (status) {
                            showAlertDialog(
                                    appCtx.getString(
                                            R.string.enroll_client_success),
                                    CertificateEnrollmentStatus.SUCCESS, null);
                        } else {
                            showAlertDialog(
                                    appCtx.getString(
                                            R.string.device_profile_failure),
                                    CertificateEnrollmentStatus.ERROR, null);
                        }
                    }
                })) {

            //if enrollment profile is disabled, then proceed
            Log.d(TAG, "getProfile not sent: " + request.toString());
            view.post(new Runnable() {
                @Override
                public void run() {
                    CommsMapComponent.getInstance().getCotService()
                            .reconnectStreams();
                    showProgress(false);
                    showAlertDialog(
                            appCtx.getString(R.string.enroll_client_success),
                            CertificateEnrollmentStatus.SUCCESS, null);
                }
            });
        } else {
            showProgress(false);
        }
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {
        try {
            // HTTP response received successfully
            Log.d(TAG, "CertificateEnrollmentRequest finished successfully: "
                    + request.getRequestType());

            if (request
                    .getRequestType() == CertificateEnrollmentClient.REQUEST_TYPE_CERTIFICATE_CONFIG) {

                Bundle certBundle = resultData.getBundle(
                        CertificateConfigOperation.PARAM_CONFIG_RESPONSE_SERVER_CERTS);
                if (certBundle != null) {
                    showProgress(false);
                    CertificateConfigRequest certificateConfigRequest = (CertificateConfigRequest) request
                            .getParcelable(
                                    CertificateConfigOperation.PARAM_CONFIG_REQUEST);
                    certificateConfigRequest.setAllowAllHostnames(true);

                    StringBuilder serverDNs = new StringBuilder();
                    List<X509Certificate> serverCerts = CertificateManager
                            .certBundleToArray(certBundle);
                    for (X509Certificate x509Certificate : serverCerts) {
                        if (serverDNs.length() > 0) {
                            serverDNs.append("; ");
                        }
                        serverDNs.append(
                                x509Certificate.getSubjectDN().getName());
                    }

                    String message = ("The hostname in the certificate does not match. "
                            +
                            "Expected to see a certificate for "
                            + certificateConfigRequest.getServer()) +
                            ". But the server responded with " + serverDNs;

                    final AlertDialog dialog = new AlertDialog.Builder(context)
                            .setIcon(ATAKConstants
                                    .getIconId())
                            .setTitle(R.string.server_auth_error)
                            .setMessage(message)
                            .setPositiveButton(R.string.ok, null).create();
                    dialog.show();
                }

            } else if (request
                    .getRequestType() == CertificateEnrollmentClient.REQUEST_TYPE_CERTIFICATE_SIGNING) {

                // if we're not retrieving a profile from TAK server, go ahead and
                // close the progress dialog and finish the enrollment
                if (!getProfile) {
                    showProgress(false);
                    showAlertDialog(
                            appCtx.getString(R.string.enroll_client_success),
                            CertificateEnrollmentStatus.SUCCESS,
                            null);
                }
            } else {
                Log.w(TAG, "onRequestFinished: " + request.getRequestType());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in OnRequestFinished!", e);
        }
    }

    @Override
    public void onRequestConnectionError(final Request request,
            RequestManager.ConnectionError ce) {
        String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, "CertificateEnrollmentRequest Failed - Connection Error: "
                + detail);

        CertificateConfigRequest certificateConfigRequest = null;

        String message = appCtx.getString(R.string.enroll_client_failure);
        CertificateEnrollmentClient.CertificateEnrollmentStatus status = CertificateEnrollmentStatus.ERROR;
        if (ce.getStatusCode() == 401) {
            message = appCtx.getString(R.string.invalid_credentials);
            status = CertificateEnrollmentStatus.BAD_CREDENTIALS;

            if (request
                    .getRequestType() == CertificateEnrollmentClient.REQUEST_TYPE_CERTIFICATE_CONFIG) {
                certificateConfigRequest = (CertificateConfigRequest) request
                        .getParcelable(
                                CertificateConfigOperation.PARAM_CONFIG_REQUEST);
            }
        }

        NotificationUtil.getInstance().postNotification(
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                appCtx.getString(R.string.connection_error),
                appCtx.getString(R.string.enroll_client_failure),
                message);

        // reconnect streams
        CommsMapComponent.getInstance().getCotService()
                .reconnectStreams();

        showProgress(false);

        showAlertDialog(message, status, certificateConfigRequest);
    }

    @Override
    public void onRequestDataError(final Request request) {
        Log.e(TAG, "CertificateEnrollmentRequest Failed - Data Error");

        NotificationUtil.getInstance().postNotification(
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                appCtx.getString(R.string.connection_error),
                appCtx.getString(R.string.enroll_client_failure),
                appCtx.getString(R.string.enroll_client_failure));

        // reconnect streams
        CommsMapComponent.getInstance().getCotService()
                .reconnectStreams();

        showProgress(false);
        showAlertDialog(appCtx.getString(R.string.enroll_client_failure),
                CertificateEnrollmentStatus.ERROR, null);
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "CertificateEnrollmentRequest Failed - Custom Error");

        NotificationUtil.getInstance().postNotification(
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                appCtx.getString(R.string.connection_error),
                appCtx.getString(R.string.enroll_client_failure),
                appCtx.getString(R.string.enroll_client_failure));

        // reconnect streams
        CommsMapComponent.getInstance().getCotService()
                .reconnectStreams();

        showProgress(false);
        showAlertDialog(appCtx.getString(R.string.enroll_client_failure),
                CertificateEnrollmentStatus.ERROR, null);
    }

    private void showProgress(final boolean show) {
        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (progressDialog == null) {
                        Log.w(TAG, "progress not set");
                        return;
                    }

                    if (show) {
                        progressDialog.show();
                    } else {
                        progressDialog.dismiss();
                    }
                } catch (Exception ex) {
                    Log.e(TAG, ex.getMessage());
                }
            }
        });
    }

    private void showAlertDialog(final String message,
            final CertificateEnrollmentStatus status,
            final CertificateConfigRequest certificateConfigRequest) {
        String title = status == CertificateEnrollmentStatus.SUCCESS
                ? appCtx.getString(R.string.enroll_client_success_title)
                : appCtx.getString(R.string.enroll_client_failure_title);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context)
                .setIcon(com.atakmap.android.util.ATAKConstants.getIconId())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(appCtx.getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (certificateEnrollmentCompleteCallback != null) {
                                    certificateEnrollmentCompleteCallback
                                            .onCertificateEnrollmentComplete(
                                                    status);
                                } else if (status == CertificateEnrollmentStatus.BAD_CREDENTIALS
                                        &&
                                        certificateConfigRequest != null) {
                                    view.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            CredentialsDialog
                                                    .createCredentialDialog(
                                                            certificateConfigRequest
                                                                    .getDescription(),
                                                            certificateConfigRequest
                                                                    .getConnectString(),
                                                            certificateConfigRequest
                                                                    .getUsername(),
                                                            certificateConfigRequest
                                                                    .getPassword(),
                                                            certificateConfigRequest
                                                                    .getCacheCreds(),
                                                            certificateConfigRequest
                                                                    .getExpiration(),
                                                            context,
                                                            CertificateEnrollmentClient.this);
                                        }
                                    });
                                }
                            }
                        });

        try { 
            alertDialog.show();
        } catch (Exception e) { 
            // if enrollment does not complete on time and the preference activity has been closed, 
            // just continue on with the application and do not error out.
            Log.e(TAG, "error occured and the preference activity has been closed prior to the enrollment completing", e);
        }
    }
}
