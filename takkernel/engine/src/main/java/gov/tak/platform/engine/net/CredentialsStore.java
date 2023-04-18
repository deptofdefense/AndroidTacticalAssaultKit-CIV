package gov.tak.platform.engine.net;


import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.database.impl.DatabaseImpl;
import com.atakmap.net.CertificateManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import gov.tak.api.engine.net.ICredentialsStore;

/**
 * Class responsible for storing Certificates
 *
 */
public final class CredentialsStore implements ICredentialsStore {



    /**
     * Tag for logging
     */
    private static final String TAG = "AtakAuthenticationDatabaseLocalAdapter";

    /**
     * Holds the authentication database.
     */
    private DatabaseIface authenticationDb;
    private static File databaseFile;

    final Object lock = new Object();

    public CredentialsStore(String absolutePath, String pwd) throws IOException {
        if(openOrCreateDatabase(absolutePath, pwd) != 0)
            throw new IOException("Could not create file " + absolutePath);
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
     * Gets the credentials by the given type and site.
     * @param type the type the type associated with the credential
     * @param site the site the site associated with the credential
     * @return a {@link ICredentialsStore.Credentials}
     */
    @Override
    public ICredentialsStore.Credentials getCredentials(
            String type, String site) {
        synchronized (lock) {
            return getCredentialsImpl(type, site);
        }
    }

    /**
     * Gets the credentials by the given type.<p>
     * NOTE: This will conduct a query 'WHERE type = <code>type</code> AND site = <code>type</code>'
     * @param type the type
     * @return a {@link ICredentialsStore.Credentials}
     */
    @Override
    public ICredentialsStore.Credentials getCredentials(
            String type) {
        synchronized (lock) {
            return getCredentialsImpl(type, type);
        }
    }

    /**
     * Saves the {@link ICredentialsStore.Credentials} represented by the given parameters to the
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
    public void saveCredentials(String type, String site, String username, String password, long expires) {
        long expireTime = -1;
        if (expires > 0)
            expireTime = System.currentTimeMillis() + expires;

        synchronized (lock) {
            boolean rc = saveCredentialsImpl(type, site, username, password, expireTime);
            if (!rc) {
                Log.w(TAG, "saveCredentials returned false");
            }

            if (type != null && type.equals(ICredentialsStore.Credentials.TYPE_caPassword)) {
                CertificateManager.getInstance().refresh();
            }
        }
    }

    /**
     * Saves the {@link ICredentialsStore.Credentials} represented by the given parameters to the
     * Auth DB.<p>
     * NOTE: This will save the type parameter into both the type and site column.
     * @param type     the type
     * @param username the username
     * @param password the password
     * @param expires  when this credential expires as a time in milliseconds since the current
     *                 system time wehn the save command is invoked.
     */
    @Override
    public void saveCredentials(
            String type,
            String username,
            String password,
            long expires) {
        synchronized (lock) {
            saveCredentials(type, type, username, password, expires);
        }
    }

    /**
     * Deletes the {@link ICredentialsStore.Credentials} row(s) from the Auth DB by the give
     * type and site.
     *
     * @param type the type to remove
     * @param site the site to remove.
     */
    @Override
    public void invalidate(
            String type,
            String site) {
        synchronized (lock) {
            boolean rc = invalidateImpl(type, site);
            if (!rc) {
                Log.w(TAG, "invalidate returned false");
            }
        }
    }

    /**
     * Gets a distinct array of {@link ICredentialsStore.Credentials} only populated with
     * site and type from the auth DB.
     *
     * @return distinct array of {@link ICredentialsStore.Credentials}
     */
    @Override
    public ICredentialsStore.Credentials[] getDistinctSitesAndTypes() {
        return getDistinctSitesAndTypesImpl().toArray(new ICredentialsStore.Credentials[0]);
    }

    /**
     * Closes the Auth DB.
     * @return true if successful, false otherwise.
     */
    protected boolean close() {
        if (this.authenticationDb != null) {
            try{
                this.authenticationDb.close();
            } catch (Exception e){
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the {@link ICredentialsStore.Credentials} populated with the given type and site, and
     * populated with the queried username and password of the given type and site.
     *
     * @param type the type to query
     * @param site the site to query
     * @return the {@link ICredentialsStore.Credentials} from the Auth DB.
     */
    protected ICredentialsStore.Credentials getCredentialsImpl(String type, String site) {
        List<ICredentialsStore.Credentials> result = new ArrayList<>();
        if (authenticationDb != null) {
            //Build SQL query
            String sql = "SELECT username, password FROM credentials WHERE type = ? AND site = ?;";
            QueryIface sqlQuery = null;
            try {
                sqlQuery = authenticationDb.compileQuery(sql);
                sqlQuery.bind(1, type);
                sqlQuery.bind(2, site);

                //Iterate through cursor and add all rows returned
                try {
                    while (sqlQuery.moveToNext()) {
                        ICredentialsStore.Credentials toAdd = new ICredentialsStore.Credentials();
                        toAdd.type = type;
                        toAdd.site = site;
                        toAdd.username = sqlQuery.getString(0);
                        toAdd.password = sqlQuery.getString(1);
                        result.add(toAdd);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getCredentials: failed to query.", e);
                }
            } finally {
                if(sqlQuery != null)
                    sqlQuery.close();
            }

            if (result.isEmpty()) {
                // Return null, and log error if no rows are returned.
                return null;
            } else if (result.size() > 1) {
                //This should only return 1 row, log a warning if more than 1 is returned.
                Log.d(TAG, "getCredentials: more than 1 row returned! Returning first row.");
            }

            return result.get(0);
        } else {
            Log.e(TAG, "getCredentials: DB is null!");
            return null;
        }
    }

    /**
     * Gets a distinct list of {@link ICredentialsStore.Credentials} only populated with
     * site and type from the auth DB.
     *
     * @return distinct list of {@link ICredentialsStore.Credentials}
     */
    private List<ICredentialsStore.Credentials> getDistinctSitesAndTypesImpl() {
        List<ICredentialsStore.Credentials> result = new ArrayList<>();
        if (authenticationDb != null) {
            //Build SQL query
            String sql = "SELECT DISTINCT site, type FROM credentials;";
            QueryIface sqlQuery = authenticationDb.compileQuery(sql);

            //Iterate through cursor and add all rows returned
            try {
                while (sqlQuery.moveToNext()) {
                    ICredentialsStore.Credentials toAdd = new ICredentialsStore.Credentials();
                    toAdd.site = sqlQuery.getString(0);
                    toAdd.type = sqlQuery.getString(1);
                    result.add(toAdd);
                }
            } catch (Exception e) {
                Log.e(TAG, "getDistinctSitesAndTypes: failed to query.", e);
            }

            return result;
        } else {
            Log.e(TAG, "getDistinctSitesAndTypes: DB is null!");
            return null;
        }
    }

    /**
     * Saves the {@link ICredentialsStore.Credentials} represented by the given parameters to the
     * Auth DB.
     *
     * @param type     the type
     * @param site     the site
     * @param username the username
     * @param password the password
     * @param expires  when this credential expires.
     * @return True if successful, false otherwise.
     */
    protected boolean saveCredentialsImpl(String type, String site, String username, String password, long expires) {
        if (authenticationDb == null) {
            return false;
        }


        ICredentialsStore.Credentials creds = getCredentialsImpl(type, site);
        if (creds == null || creds.password == null) {
            StatementIface stmt = null;
            try {
                stmt = authenticationDb.compileStatement(
                        "insert into credentials ( type, site, username, password, expires ) values ( ?, ?, ?, ?, ? );");

                stmt.bind(1, type);
                stmt.bind(2, site);
                stmt.bind(3, username);
                stmt.bind(4, password);
                stmt.bind(5, expires);

                try {
                    stmt.execute();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            } finally {
                if(stmt != null)
                    stmt.close();
            }
        } else {
            StatementIface stmt = null;
            try {
                stmt = authenticationDb.compileStatement(
                        "update credentials set username = ?, password = ?, expires = ? where type = ? and site =  ?;");

                stmt.bind(1, username);
                stmt.bind(2, password);
                stmt.bind(3, expires);
                stmt.bind(4, type);
                stmt.bind(5, site);

                try {
                    stmt.execute();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            } finally {
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    /**
     * Deletes the {@link ICredentialsStore.Credentials} row(s) from the Auth DB by the give
     * type and site.
     *
     * @param type the type to remove
     * @param site the site to remove.
     * @return True if successful, false otherwise.
     */
    protected boolean invalidateImpl(String type, String site) {
        if (authenticationDb == null) {
            return false;
        }

        String sql = "DELETE FROM credentials WHERE type = ?";
        if (!FileSystemUtils.isEmpty(site)) {
            sql = sql + " AND site = ?;";
        } else {
            sql = sql + ";";
        }

        StatementIface sqlStatement = null;
        try {
            sqlStatement = authenticationDb.compileStatement(sql);
            sqlStatement.bind(1, type);

            if (!FileSystemUtils.isEmpty(site))
                sqlStatement.bind(2, site);

            try {
                sqlStatement.execute();
            } catch (Exception e) {
                Log.e(TAG, "invalidate: failed to execute SQL", e);
                return false;
            }
            return true;
        } finally {
            if(sqlStatement != null)
                sqlStatement.close();
        }
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

        String sql = "DELETE FROM credentials WHERE expires > 0 AND expires <= ?;";
        StatementIface sqlStatement = null;
        try {
            sqlStatement = authenticationDb.compileStatement(sql);
            sqlStatement.bind(1, time);

            try {
                sqlStatement.execute();
            } catch (Exception e) {
                Log.e(TAG, "deleteExpiredCredentials: failed to execute SQL", e);
                return false;
            }
            return true;
        } finally {
            if(sqlStatement != null)
                sqlStatement.close();
        }
    }

    public int openOrCreateDatabase(String absolutePath, String pwd) {
        synchronized(lock) {
            if(this.authenticationDb != null) {
                this.authenticationDb.close();
                this.authenticationDb = null;
            }

            try {
                if (pwd != null) {
                    this.authenticationDb = DatabaseImpl.open(absolutePath, pwd, 0x01000000 | DatabaseImpl.OPEN_CREATE);
                } else if(absolutePath != null) {
                    this.authenticationDb = IOProviderFactory.createDatabase(new File(absolutePath));
                } else {
                    this.authenticationDb = IOProviderFactory.createDatabase((File)null);
                }
                if(authenticationDb != null)
                    this.authenticationDb.execute("CREATE TABLE IF NOT EXISTS credentials(" +
                                    "type TEXT, site TEXT, username TEXT, password TEXT, expires INTEGER);",
                            null);
                return (this.authenticationDb != null) ? 0 : -1;
            } catch(Throwable t) {
                return -1;
            }
        }
    }

}
