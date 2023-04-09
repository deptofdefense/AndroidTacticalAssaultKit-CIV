package gov.tak.platform.engine.net;

import gov.tak.api.commons.resources.IAssetManager;
import gov.tak.platform.commons.resources.JavaAssetManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.util.Enumeration;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import gov.tak.api.engine.net.ICertificateStore;
import gov.tak.api.engine.net.ICredentialsStore;
import android.content.Context;

public class CertificateManager extends CertificateManagerBase {

    public CertificateManager(ICertificateStore certificateStore, ICredentialsStore credentialsStore, IAssetManager assets)
    {
        super(certificateStore, credentialsStore, assets);
    }

    public static List<X509Certificate> loadCertificate(byte[] p12, String password)
    {
        // seeing generally inconsistent results with certificate loading between bouncycastle
        // and the system provider for Java desktop. Based on testing with various certs, best
        // results have been observed with preferring bouncycastle, but falling back on system.
        // Specific failure cases are observed with bouncycastle for certs produced during
        // certificate enrollment. May be related https://github.com/bcgit/bc-java/issues/400
        final Provider[] providers =
        {
            new BouncyCastleProvider(),
            null,
        };
        Throwable[] err = new Throwable[1];
        for(Provider p : providers)
        {
            final List<X509Certificate> cert = loadCertificate(p12, password, p, err);
            if(cert != null)
                return cert;
        }
        if(err[0] != null) {
            Log.e(TAG, "Exception in loadCertificate!", err[0]);
        } else {
            Log.e(TAG, "Exception in loadCertificate!");
        }
        return null;
    }

}
