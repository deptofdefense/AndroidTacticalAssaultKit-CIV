
package gov.tak.platform.engine.net;

import com.atakmap.coremap.log.Log;

import java.security.cert.X509Certificate;
import java.util.List;

import gov.tak.api.commons.resources.IAssetManager;
import gov.tak.api.engine.net.ICertificateStore;
import gov.tak.api.engine.net.ICredentialsStore;

public class CertificateManager extends CertificateManagerBase {

    public CertificateManager(ICertificateStore certificateStore, ICredentialsStore credentialsStore, IAssetManager assets) {
        super(certificateStore, credentialsStore, assets);
    }

    public static List<X509Certificate> loadCertificate(byte[] p12, String password)
    {
        Throwable[] err = new Throwable[1];
        final List<X509Certificate> cert = loadCertificate(p12, password, null, err);
        if(cert == null) {
            if(err[0] != null) {
                Log.e(TAG, "Exception in loadCertificate!", err[0]);
            } else {
                Log.e(TAG, "Exception in loadCertificate!");
            }
        }
        return cert;
    }
}
