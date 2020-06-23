package com.atakmap.net;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.util.Patterns;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 *
 */
public class AtakCertificateDatabaseAdapter implements AtakCertificateDatabaseIFace {

    private static final String TAG = "AtakCertificateDatabaseAdapter";
    public static final Object lock = new Object();

    public void dispose() {

        synchronized (lock) {
            int rc = close();
            if (rc != 0) {
                Log.e(TAG, "close returned " + rc);
            }
        }
    }

    public void clear(File databaseFile) {
        synchronized (lock) {
            FileSystemUtils.deleteFile(databaseFile);
        }
    }

    @Override
    public byte[] getCertificateForType(String type) {
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
            String hashCheck = HashingUtils.sha256sum(certificate);
            if (!hash.equals(hashCheck)) {
                Log.e(TAG, "certificate hash validation failed!");
                return null;
            }

            return certificate;
        }
    }

    @Override
    public byte[] getCertificateForTypeAndServer(String type, String server) {
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
            String hashCheck = HashingUtils.sha256sum(certificate);
            if (!hash.equals(hashCheck)) {
                Log.e(TAG, "certificate hash validation failed!");
                return null;
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
    public void deleteCertificateForType(String type) {
        synchronized (lock) {
            int rc = deleteCertificate(type);
            if (rc != 0) {
                Log.e(TAG, "deleteCertificate returned " + rc);
            }
        }
    }

    @Override
    public void deleteCertificateForTypeAndServer(String type, String server) {
        synchronized (lock) {
            int rc = deleteCertificateForServer(type, server);
            if (rc != 0) {
                Log.e(TAG, "deleteCertificateForTypeAndServer returned " + rc);
            }
        }
    }

    @Override
    public byte[] importCertificateFromPreferences(
            Context context, String prefLocation, String type, boolean delete) {
        synchronized (lock) {
            return AtakCertificateDatabase.importCertificateFromPreferences(
                    PreferenceManager.getDefaultSharedPreferences(context), prefLocation, null, type, delete);
        }
    }

    @Override
    public AtakAuthenticationCredentials importCertificatePasswordFromPreferences(
            Context context, String prefLocation, String prefDefault,
            String type, boolean delete) {
        synchronized (lock) {
            return AtakCertificateDatabase.importCertificatePasswordFromPreferences(
                    PreferenceManager.getDefaultSharedPreferences(context), prefLocation, prefDefault, type, null, delete);
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

    static {
        com.atakmap.coremap.loader.NativeLoader.loadLibrary("sqlcipher");
        com.atakmap.coremap.loader.NativeLoader.loadLibrary("certdb");
    }

    protected native int openOrCreateDatabase(String filename, String password);
    protected native int close();

    // these function pull the default certs
    protected native byte[] getCertificate(String type);
    protected native String getCertificateHash(String type);
    protected native int saveCertificate(String type, byte[] cert, String hash);
    protected native int deleteCertificate(String type);

    // these functions pull certs for individual servers
    protected native String[] getServers(String type);
    protected native byte[] getCertificateForServer(String type, String server);
    protected native String getCertificateHashForServer(String type, String server);
    protected native int saveCertificateForServer(String type, String server, byte[] cert, String hash);
    protected native int deleteCertificateForServer(String type, String server);
}
