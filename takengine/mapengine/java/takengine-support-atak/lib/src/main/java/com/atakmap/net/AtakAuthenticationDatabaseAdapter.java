package com.atakmap.net;

import android.util.Pair;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by jblomberg on 4/19/2016.
 */
public class AtakAuthenticationDatabaseAdapter implements AtakAuthenticationDatabaseIFace {

    private final static long EXPIRATION_INTERVAL = 3600000L * 24L * 30L; // 30 days

    private static final String TAG = "AtakAuthenticationDatabaseAdapter";
    public static final Object lock = new Object();

    private static Map<String, Map<String, AtakAuthenticationCredentials>> credentials = new HashMap<>();

    public void dispose() {
        synchronized (lock) {
            credentials.clear();
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
            Map<String, AtakAuthenticationCredentials> creds = credentials.get(site);
            if(creds == null)
                return null;
            return creds.get(type);
        }
    }

    @Override
    public AtakAuthenticationCredentials getCredentialsForType(
            String type) {
        synchronized (lock) {
            return getCredentialsForType(type, type);
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
            Map<String, AtakAuthenticationCredentials> siteCredentials = credentials.get(site);
            if(siteCredentials == null) {
                siteCredentials = new HashMap<>();
                credentials.put(site, siteCredentials);
            }
            AtakAuthenticationCredentials c = new AtakAuthenticationCredentials();
            c.username = username;
            c.site = site;
            c.type = type;
            c.password = password;
            siteCredentials.put(type, c);

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
            Map<String, AtakAuthenticationCredentials> siteCredentials = credentials.get(site);
            if(siteCredentials == null)
                return;
            if(type == null)
                siteCredentials.clear();
            else
                siteCredentials.remove(type);
        }
    }

    static int openOrCreateDatabase(String path, String pwd) {
        return 0;
    }

    @Override
    public boolean deleteExpiredCredentials(long epochMillis) {
        return true;
    }

    @Override
    public AtakAuthenticationCredentials[] getDistinctSitesAndTypes() {
        synchronized(lock) {
            LinkedList<AtakAuthenticationCredentials> result = new LinkedList<>();
            for (Map.Entry<String, Map<String, AtakAuthenticationCredentials>> entry : credentials.entrySet()) {
                for(String type : entry.getValue().keySet()) {
                    AtakAuthenticationCredentials c = new AtakAuthenticationCredentials();
                    c.site = entry.getKey();
                    c.type = type;
                    result.add(c);
                }
            }
            return result.toArray(new AtakAuthenticationCredentials[result.size()]);
        }
    }
}

