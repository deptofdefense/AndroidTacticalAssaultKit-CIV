package com.atakmap.net;

import android.content.Context;
import android.util.Pair;
import android.util.Patterns;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 *
 */
public class AtakCertificateDatabaseAdapter implements AtakCertificateDatabaseIFace {

    private static final String TAG = "AtakCertificateDatabaseAdapter";
    public static final Object lock = new Object();

    public void dispose() {
    }

    public void clear(File databaseFile) {
        synchronized (lock) {
            FileSystemUtils.deleteFile(databaseFile);
        }
    }

    @Override
    public byte[] getCertificateForType(String type) {
        return getCertificateForType(type, true);
    }

    @Override
    public byte[] getCertificateForType(String type, boolean validateHash) {
        synchronized (lock) {
            byte[] certificate = getCertificate(type);
            if (certificate == null) {
                Log.e(TAG, "certificate not found! type: " + type);
                return null;
            }

            String hash = getCertificateHash(type);
            if (hash == null || hash.length() == 0) {
                Log.e(TAG, "found certificate without hash: " + type);
                return null;
            }
            if(validateHash) {
                String hashCheck = HashingUtils.sha256sum(certificate);
                if (!hash.equals(hashCheck)) {
                    Log.e(TAG, "certificate hash validation failed!");
                    return null;
                }
            }

            return certificate;
        }
    }

    @Override
    public byte[] getCertificateForTypeAndServer(String type, String server) {
        return getCertificateForTypeAndServer(type, server, true);
    }

    @Override
    public byte[] getCertificateForTypeAndServer(String type, String server, boolean validateHash) {
        synchronized (lock) {

            byte[] certificate = getCertificateForServer(type, server);
            if (certificate == null) {
                Log.e(TAG, "certificate not found! type: " + type);
                return null;
            }

            String hash = getCertificateHashForServer(type, server);
            if (hash == null || hash.length() == 0) {
                Log.e(TAG, "found certificate without hash: " + type);
                return null;
            }
            if(validateHash) {
                String hashCheck = HashingUtils.sha256sum(certificate);
                if (!hash.equals(hashCheck)) {
                    Log.e(TAG, "certificate hash validation failed!");
                    return null;
                }
            }

            return certificate;
        }
    }

    public Pair<byte[], String> getCertificateForIPaddress(final String type, final String IP) {
        synchronized (lock) {
            try {

                // bail if we dont have a proper IP address
                if (IP == null || !Patterns.IP_ADDRESS.matcher(IP).matches()) {
                    return null;
                }

                String[] servers = getServers(type);
                if (servers == null) {
                    Log.i(TAG, "getServers returned null for " + type);
                    return null;
                }

                for (final String server : servers) {

                    // ignore any certs stored via IP address
                    if (Patterns.IP_ADDRESS.matcher(server).matches()) {
                        continue;
                    }

                    final CountDownLatch latch = new CountDownLatch(1);
                    final String[] results = new String[1];
                    results[0] = null;
                    Thread t = new Thread(TAG + "-GetHostAddress"){
                        @Override
                        public void run(){
                            try {
                                InetAddress inetAddress = InetAddress.getByName(server);
                                if (inetAddress == null) {
                                    Log.e(TAG, "getByName failed for " + server);
                                } else {
                                    results[0] = inetAddress.getHostAddress();
                                }
                            } catch (UnknownHostException uhe) {
                                Log.e(TAG, "UnknownHostException for " + server);
                            }

                            latch.countDown();
                        }
                    };
                    t.start();
                    latch.await();

                    if (results[0] == null) {
                        Log.e(TAG, "getByName failed for " + server);
                    } else if (results[0].compareTo(IP) == 0) {
                        byte[] cert = getCertificateForServer(type, server);
                        Pair<byte[], String> pair = new Pair<byte[], String>(cert, server);
                        return pair;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "exception in getCertificateForIPaddress: " + e.getMessage(), e);
            }

            return null;
        }
    }

    @Override
    public void saveCertificateForType(String type, byte[] certificate) {

        synchronized (lock) {
            String hash = HashingUtils.sha256sum(certificate);
            int rc = saveCertificate(type, certificate, hash);
            if (rc != 0) {
                Log.e(TAG, "saveCertificate returned " + rc);
            }
        }
    }

    @Override
    public void saveCertificateForTypeAndServer(String type, String server, byte[] certificate) {

        synchronized (lock) {
            String hash = HashingUtils.sha256sum(certificate);
            int rc = saveCertificateForServer(type, server, certificate, hash);
            if (rc != 0) {
                Log.e(TAG, "saveCertificate returned " + rc);
            }
        }
    }

    @Override
    public boolean deleteCertificateForType(String type) {
        synchronized (lock) {
            int rc = deleteCertificate(type);
            if (rc != 0) {
                Log.e(TAG, "deleteCertificate returned " + rc);
            }
            return (rc == 0);
        }
    }

    @Override
    public boolean deleteCertificateForTypeAndServer(String type, String server) {
        synchronized (lock) {
            int rc = deleteCertificateForServer(type, server);
            if (rc != 0) {
                Log.e(TAG, "deleteCertificateForTypeAndServer returned " + rc);
            }
            return (rc == 0);
        }
    }

    @Override
    public List<X509Certificate> getCACerts() {
        return AtakCertificateDatabase.getCACerts();
    }

    @Override
    public boolean checkValidity(byte[] keystore, String password) {
        AtakCertificateDatabase.CeritficateValidity validity = AtakCertificateDatabase.checkValidity(keystore, password);
        return validity != null && validity.isValid();
    }

    // these function pull the default certs
    protected byte[] getCertificate(String type) {
        for(CertificateData cert : _certs) {
            if(cert.server == null && cert.type.equals(type))
                return cert.data;
        }
        return null;
    }
    protected String getCertificateHash(String type) {
        for(CertificateData cert : _certs) {
            if(cert.server == null && cert.type.equals(type))
                return cert.hash;
        }
        return null;
    }
    protected int saveCertificate(String type, byte[] data, String hash) {
        for(CertificateData cert : _certs) {
            if(cert.server == null && cert.type.equals(type)) {
                cert.data = data;
                cert.hash = hash;
                return 0;
            }
        }
        CertificateData cert = new CertificateData();
        cert.type = type;
        cert.data  = data;
        cert.hash = hash;
        _certs.add(cert);
        return 0;
    }
    protected int deleteCertificate(String type) {
        for(int i = 0; i < _certs.size(); i++) {
            final CertificateData cert = _certs.get(i);
            if(cert.server == null && cert.type.equals(type)) {
                _certs.remove(i);
                break;
            }
        }
        // XXX - not sure if this should remove non-zero if not found
        return 0;
    }

    // these functions pull certs for individual servers
    @Override
    public String[] getServers(String type) {
        ArrayList<String> servers = new ArrayList<>(_certs.size());
        for(CertificateData cert : _certs) {
            if (cert.server != null)
                servers.add(cert.server);
        }
        return servers.toArray(new String[servers.size()]);
    }
    protected byte[] getCertificateForServer(String type, String server) {
        for(CertificateData cert : _certs) {
            if(cert.server.equals(server) && cert.type.equals(type))
                return cert.data;
        }
        return null;
    }
    protected String getCertificateHashForServer(String type, String server) {
        for(CertificateData cert : _certs) {
            if(cert.server.equals(server) && cert.type.equals(type))
                return cert.hash;
        }
        return null;
    }
    protected int saveCertificateForServer(String type, String server, byte[] data, String hash) {
        for(CertificateData cert : _certs) {
            if(cert.server.equals(server) && cert.type.equals(type)) {
                cert.data = data;
                cert.hash = hash;
                return 0;
            }
        }
        CertificateData cert = new CertificateData();
        cert.server = server;
        cert.type = type;
        cert.data  = data;
        cert.hash = hash;
        _certs.add(cert);
        return 0;
    }
    protected int deleteCertificateForServer(String type, String server) {
        for(int i = 0; i < _certs.size(); i++) {
            final CertificateData cert = _certs.get(i);
            if(cert.server.equals(server) && cert.type.equals(type)) {
                _certs.remove(i);
                break;
            }
        }
        // XXX - not sure if this should remove non-zero if not found
        return 0;
    }

    private List<CertificateData> _certs = new ArrayList<>();

    final static class CertificateData {
        String type;
        String server;
        byte[] data;
        String hash;
    }
}
