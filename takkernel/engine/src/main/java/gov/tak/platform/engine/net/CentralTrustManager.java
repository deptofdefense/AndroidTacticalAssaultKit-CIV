package gov.tak.platform.engine.net;

import com.atakmap.coremap.log.Log;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Implementation of a self signed trust manager that will allow for either certificates signed
 * with a certificate installed on the device, one of the pre-supplied DoD certificates or will
 * just validate a self signed certificate.   Self signed certificates are still in use on private
 * tactical networks.
 */
public final class CentralTrustManager implements X509TrustManager
{
    private final static String TAG = "CentralTrustManager";

    private boolean iss = false;

    // store an instance of the socket factory that's using a CentralTrustManager in order
    // to pass back the server certificates

    private final static CertificatePrompt PERMISSIVE_CERT_PROMPT = new CertificatePrompt() {
        @Override
        public boolean promptToUseSystemTrustManager(
                java.security.cert.X509Certificate[] certs) {
            Log.d(TAG,
                    "using permissive prompt for system trust manager access");
            return true;
        }

        @Override
        public boolean promptToAccept(
                java.security.cert.X509Certificate[] certs) {
            Log.d(TAG,
                    "self signed server cert detected, without anchor, blocked by default");
            return false;
        }

    };

    private CertificatePrompt cp = PERMISSIVE_CERT_PROMPT;

    public interface CertificatePrompt {
        /**
         * Callback that can be used to prompt the user to make use of
         * the System TrustStore.
         * @param certs are the certs passed in so the user can decide if to accept them or not.
         */
        boolean promptToUseSystemTrustManager(
                java.security.cert.X509Certificate[] certs);

        /**
         * Callback that can be used to prompt the user to make use of self signed certs of length 1
         * generated for example, by this little gem:
         * <p>
         *     openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout mysitename.key -out mysitename.crt
         * </p>
         * @param certs are the certs passed in so the user can decide if to accept them or not.
         */
        boolean promptToAccept(java.security.cert.X509Certificate[] certs);
    }

    /**
     * Whether to use the system TrustManager when validating a certificate that
     * may be self signed.
     * @param iss true to use the system TrustManager, false to only use the
     * local TrustManager.
     *
     */
    public void setIgnoreSystemCerts(final boolean iss) {
        this.iss = iss;
    }

    private CertificateManager certificateManager;

    public CentralTrustManager(CertificateManager certificateManager) {
        this.certificateManager = certificateManager;
    }

    /**
     * Install a prompt implementation if the System Trust Store Manager needs to
     * be accessed.   This is a global change across the application and only one
     * system prompt may be registered.
     */
    public void setSystemCertificatePrompt(final CertificatePrompt cp) {
        if (cp != null)
            this.cp = cp;
        else
            this.cp = PERMISSIVE_CERT_PROMPT;
    }

    @Override
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {

        X509TrustManager x509Tm = null;

        if (!iss) {
            Log.d(TAG, "using system trust manager, getAcceptedIssuers");
            x509Tm = certificateManager
                    .getSystemTrustManager();
        }

        return certificateManager.getCertificates(x509Tm);
    }

    @Override
    public void checkClientTrusted(
            java.security.cert.X509Certificate[] certs,
            String authType) throws CertificateException
    {

        X509TrustManager ltm = certificateManager.getLocalTrustManager();
        if (iss) {
            ltm.checkClientTrusted(certs, authType);
        } else {
            try {
                ltm.checkClientTrusted(certs, authType);
            } catch (CertificateException ce) {
                X509TrustManager x509Tm = certificateManager.getSystemTrustManager();
                if (x509Tm != null
                        && cp.promptToUseSystemTrustManager(certs)) {
                    Log.d(TAG,
                            "using system trust manager, checkClientTrusted");
                    x509Tm.checkClientTrusted(certs, authType);
                } else {
                    Log.d(TAG, "System TrustManager access denied");
                    throw new CertificateException(
                            "System TrustManager denied");
                }
            }
        }

    }

    @Override
    public void checkServerTrusted(
            java.security.cert.X509Certificate[] certs,
            String authType) throws CertificateException {

        if (certs == null) {
            Log.e(TAG, "checkServerTrusted called with null certs array!");
            throw new IllegalArgumentException();
        }

        for (X509Certificate cert : certs) {
            cert.checkValidity();
        }

        X509TrustManager ltm = certificateManager.getLocalTrustManager();
        if (iss) {
            try {
                ltm.checkServerTrusted(certs, authType);
            } catch (CertificateException ce) {
                if (cp.promptToAccept(certs)) {
                    // user has decided to accept the risk
                } else {
                    throw ce;
                }
            }
        } else {
            try {
                ltm.checkServerTrusted(certs, authType);
            } catch (CertificateException ce) {

                X509TrustManager x509Tm = certificateManager.getSystemTrustManager();
                if (x509Tm != null
                        && cp.promptToUseSystemTrustManager(certs)) {
                    Log.d(TAG,
                            "using system trust manager, checkServerTrusted");
                    try {
                        x509Tm.checkServerTrusted(certs, authType);
                    } catch (CertificateException ce2) {
                        if (cp.promptToAccept(certs)) {
                            // user has decided to accept the risk
                        } else {
                            throw ce2;
                        }
                    }
                } else {
                    Log.d(TAG, "System TrustManager access denied");
                    throw new CertificateException(
                            "System TrustManager denied");
                }
            }
        }
    }
}