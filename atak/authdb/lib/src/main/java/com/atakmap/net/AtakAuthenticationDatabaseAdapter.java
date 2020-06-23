package com.atakmap.net;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 *
 */
public class AtakAuthenticationDatabaseAdapter implements AtakAuthenticationDatabaseIFace {

    private final static long EXPIRATION_INTERVAL = 3600000L * 24L * 30L; // 30 days

    private static final String TAG = "AtakAuthenticationDatabaseAdapter";
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
    public AtakAuthenticationCredentials getCredentialsForType(
            String type, String site) {
        synchronized (lock) {
            return getCredentials(type, site);
        }
    }

    @Override
    public AtakAuthenticationCredentials getCredentialsForType(
            String type) {
        synchronized (lock) {
            return getCredentials(type, type);
        }
    }

    @Override
    public void saveCredentialsForType(
            String type,
            String site,
            String username,
            String password,
            boolean expires) {
        synchronized (lock) {
            long expireTime = expires ? System.currentTimeMillis() + EXPIRATION_INTERVAL : -1;
            int rc = saveCredentials(type, site, username, password, expireTime);
            if (rc != 0) {
                Log.e(TAG, "saveCredentials returned " + rc);
            }

            if (type != null && type.equals(AtakAuthenticationCredentials.TYPE_caPassword)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    @Override
    public void saveCredentialsForType(
            String type,
            String username,
            String password,
            boolean expires) {
        synchronized (lock) {
            saveCredentialsForType(type, type, username, password, expires);
        }
    }

    @Override
    public void invalidateForType(
            String type,
            String site) {
        Log.i(TAG, "invalidate = " + type);

        synchronized (lock) {
            int rc = invalidate(type, site);
            if (rc != 0) {
                Log.e(TAG, "invalidate returned " + rc);
            }
        }
    }

    static {
        com.atakmap.coremap.loader.NativeLoader.loadLibrary("sqlcipher");
        com.atakmap.coremap.loader.NativeLoader.loadLibrary("authdb");
    }

    protected native int openOrCreateDatabase(String filename, String password);
    protected native int close();
    protected native AtakAuthenticationCredentials getCredentials(String type, String site);
    protected native AtakAuthenticationCredentials[] getDistinctSitesAndTypes();
    protected native int saveCredentials(String type, String site, String username, String password, long expires);
    protected native int invalidate(String type, String site);

    /**
     * Credentials expire after <code>EXPIRATION_INTERVAL</code>
     * Currently 30 days
     *
     * @param time
     * @return
     */
    protected native int deleteExpiredCredentials(long time);
}

