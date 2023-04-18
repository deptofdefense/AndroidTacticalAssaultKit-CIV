package com.atakmap.net;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.database.impl.DatabaseImpl;
import gov.tak.api.engine.net.ICredentialsStore;
import gov.tak.platform.engine.net.CredentialsStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
@ModifierApi(since = "4.3", target = "4.6", modifiers = {
        "final"
})
 public final class AtakAuthenticationDatabaseAdapter implements AtakAuthenticationDatabaseIFace {

    /**
     * 30 days in milliseconds.
     */
    private final static long EXPIRATION_INTERVAL = 3600000L * 24L * 30L;

    /**
     * Tag for logging
     */
    private static final String TAG = "AtakAuthenticationDatabaseLocalAdapter";

    /**
     * Holds the authentication database.
     */
    ICredentialsStore authenticationDb;

    final Object lock = new Object();

    public AtakAuthenticationDatabaseAdapter() {
        this.authenticationDb = null;
    }

    boolean isValid() {
        synchronized(lock) {
            return this.authenticationDb != null;
        }
    }

    /**
     * Disposes this class.
     */
    public void dispose() {
        synchronized (lock) {
            close();
        }
    }

    /**
     * Deletes the given file.
     *
     * @param databaseFile the file to delete.
     */
    public void clear(File databaseFile) {
        synchronized (lock) {
            FileSystemUtils.deleteFile(databaseFile);
            authenticationDb = null;
        }
    }

    /**
     * Gets the credentials by the given type and site.
     * @param type the type the type associated with the credential
     * @param site the site the site associated with the credential
     * @return a {@link AtakAuthenticationCredentials}
     */
    @Override
    public AtakAuthenticationCredentials getCredentialsForType(
            String type, String site) {
        synchronized (lock) {
            return getCredentials(type, site);
        }
    }

    /**
     * Gets the credentials by the given type.<p>
     * NOTE: This will conduct a query 'WHERE type = <code>type</code> AND site = <code>type</code>'
     * @param type the type
     * @return a {@link AtakAuthenticationCredentials}
     */
    @Override
    public AtakAuthenticationCredentials getCredentialsForType(
            String type) {
        synchronized (lock) {
            return getCredentials(type, type);
        }
    }

    /**
     * Saves the {@link AtakAuthenticationCredentials} represented by the given parameters to the
     * Auth DB.
     *
     * @param type     the type
     * @param site     the site
     * @param username the username
     * @param password the password
     * @param expires  if true the expiration time is set to 30 days from the current system time
     *                 from the time this method is called.
     *
     * @deprecated use {@link #saveCredentialsForType(String, String, String, String, long)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
    public void saveCredentialsForType(
            String type,
            String site,
            String username,
            String password,
            boolean expires) {
        synchronized (lock) {
            long expireTime = expires ? EXPIRATION_INTERVAL : -1;
            saveCredentialsForType(type, site, username, password, expireTime);
        }
    }

    /**
     * Saves the {@link AtakAuthenticationCredentials} represented by the given parameters to the
     * Auth DB.
     *
     * @param type     the type
     * @param site     the site
     * @param username the username
     * @param password the password
     * @param expires  when this credential expires as a time in milliseconds since the current
     *                 system time wehn the save command is invoked.
     */
    @Override
    public void saveCredentialsForType(String type, String site, String username, String password, long expires) {
        long expireTime = -1;
        if (expires > 0)
            expireTime = System.currentTimeMillis() + expires;

        synchronized (lock) {
            boolean rc = saveCredentials(type, site, username, password, expireTime);
            if (!rc) {
                Log.w(TAG, "saveCredentials returned false");
            }

            if (type != null && type.equals(AtakAuthenticationCredentials.TYPE_caPassword)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    /**
     * Saves the {@link AtakAuthenticationCredentials} represented by the given parameters to the
     * Auth DB.<p>
     * NOTE: This will save the type parameter into both the type and site column.
     * @param type     the type
     * @param username the username
     * @param password the password
     * @param expires  if true the expiration time is set to 30 days from the current system time
     *                 from the time this method is called.
     *
     * @deprecated use {@link #saveCredentialsForType(String, String, String, long)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
    public void saveCredentialsForType(
            String type,
            String username,
            String password,
            boolean expires) {
        synchronized (lock) {
            saveCredentialsForType(type, type, username, password, expires);
        }
    }

    /**
     * Saves the {@link AtakAuthenticationCredentials} represented by the given parameters to the
     * Auth DB.<p>
     * NOTE: This will save the type parameter into both the type and site column.
     * @param type     the type
     * @param username the username
     * @param password the password
     * @param expires  when this credential expires as a time in milliseconds since the current
     *                 system time wehn the save command is invoked.
     */
    @Override
    public void saveCredentialsForType(
            String type,
            String username,
            String password,
            long expires) {
        synchronized (lock) {
            saveCredentialsForType(type, type, username, password, expires);
        }
    }

    /**
     * Deletes the {@link AtakAuthenticationCredentials} row(s) from the Auth DB by the give
     * type and site.
     *
     * @param type the type to remove
     * @param site the site to remove.
     */
    @Override
    public void invalidateForType(
            String type,
            String site) {
        synchronized (lock) {
            boolean rc = invalidate(type, site);
            if (!rc) {
                Log.w(TAG, "invalidate returned false");
            }
        }
    }

    /**
     * Gets a distinct array of {@link AtakAuthenticationCredentials} only populated with
     * site and type from the auth DB.
     *
     * @return distinct array of {@link AtakAuthenticationCredentials}
     */
    @Override
    public AtakAuthenticationCredentials[] getDistinctSitesAndTypes() {
        if(this.authenticationDb == null)
            return new AtakAuthenticationCredentials[0];
        final ICredentialsStore.Credentials[] credentials = this.authenticationDb.getDistinctSitesAndTypes();
        final AtakAuthenticationCredentials[] acredentials = new AtakAuthenticationCredentials[credentials.length];
        for(int i = 0; i < credentials.length; i++)
            acredentials[i] = new AtakAuthenticationCredentials(credentials[i]);
        return acredentials;
    }

    /**
     * Closes the Auth DB.
     * @return true if successful, false otherwise.
     */
    protected boolean close() {
        if (this.authenticationDb != null) {
            try{
                this.authenticationDb.dispose();
            } catch (Exception e){
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the {@link AtakAuthenticationCredentials} populated with the given type and site, and
     * populated with the queried username and password of the given type and site.
     *
     * @param type the type to query
     * @param site the site to query
     * @return the {@link AtakAuthenticationCredentials} from the Auth DB.
     */
    protected AtakAuthenticationCredentials getCredentials(String type, String site) {
        List<AtakAuthenticationCredentials> result = new ArrayList<>();
        if (authenticationDb != null) {
            final ICredentialsStore.Credentials credentials = authenticationDb.getCredentials(type, site);
            return (credentials != null) ? new AtakAuthenticationCredentials(credentials) : null;
        } else {
            Log.e(TAG, "getCredentials: DB is null!");
            return null;
        }
    }

    /**
     * Saves the {@link AtakAuthenticationCredentials} represented by the given parameters to the
     * Auth DB.
     *
     * @param type     the type
     * @param site     the site
     * @param username the username
     * @param password the password
     * @param expires  when this credential expires.
     * @return True if successful, false otherwise.
     */
    protected boolean saveCredentials(String type, String site, String username, String password, long expires) {
        if (authenticationDb == null) {
            return false;
        }

        authenticationDb.saveCredentials(type, site, username, password, expires);
        return true;
    }

    /**
     * Deletes the {@link AtakAuthenticationCredentials} row(s) from the Auth DB by the give
     * type and site.
     *
     * @param type the type to remove
     * @param site the site to remove.
     * @return True if successful, false otherwise.
     */
    protected boolean invalidate(String type, String site) {
        if (authenticationDb == null) {
            return false;
        }

        authenticationDb.invalidate(type, site);
        return true;
    }

    /**
     * Credentials expire after <code>EXPIRATION_INTERVAL</code>
     * Currently 30 days
     *
     * @param time
     * @return True if successful, false otherwise.
     */
    @Override
    public boolean deleteExpiredCredentials(long time) {
        if (authenticationDb == null) {
            return false;
        }

        return authenticationDb.deleteExpiredCredentials(time);
    }

    public boolean openOrCreateDatabase(String absolutePath) {
        return (this.openOrCreateDatabase(absolutePath, null) == 0);
    }

    int openOrCreateDatabase(String absolutePath, String pwd) {
        synchronized(lock) {
            if(this.authenticationDb != null) {
                this.authenticationDb.dispose();
                this.authenticationDb = null;
            }

            try {
                this.authenticationDb = new CredentialsStore(absolutePath, pwd);
                return (this.authenticationDb != null) ? 0 : -1;
            } catch(Throwable t) {
                return -1;
            }
        }
    }
}
