package com.atakmap.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 *  The AtakAuthenticationDatabase provides a secure way of storing credentials (username/password).
 *  Credentials are stored and retrieved based on type, and optionally can be associated with a
 *  specific site. Credentials not associated with a specific site are referred to as 'default'
 *  credentials for that type.
 */
public class AtakAuthenticationDatabase {

    public static final String TAG = "AtakAuthenticationDatabase";
    private static String deviceId;
    private static File databaseFile;
    private static boolean initialized = false;
    private static AtakAuthenticationDatabaseAdapter authenticationDatabaseAdapter;
    private static Context ctx;


    /**
     * Returns an adapter class used to access the authdb native library.
     *
     * @return AtakAuthenticationDatabaseAdapter
     */
    public static synchronized AtakAuthenticationDatabaseAdapter getAdapter() {
        if (authenticationDatabaseAdapter == null) {
            authenticationDatabaseAdapter = new AtakAuthenticationDatabaseAdapter();
        }
        return authenticationDatabaseAdapter;
    }

    /**
     * Stores the devices unique ID, as determined by _determineBestDeviceUID.
     *
     * @param id Unique identifier for this device
     * @return void
     */
    public static synchronized void setDeviceId(String id) {
        deviceId = id;
    }

    /**
     * Returns the password used to initialize the sqlcipher database. The password
     * is derived from the passed in key and device id. Key is used to reference a UUID in the
     * shared preferences. The password is sha256 hash of the key concatenated with the UUID.
     *
     * @param context A valid application context
     * @param key Used to lookup UUID in preferences
     * @param deviceId Unique identifier for this device
     * @return void
     */
    public static synchronized String getPwd(Context context, String key, String deviceId) {
        String databaseId;
        File token = new File(context.getFilesDir(), key);
        do {
            if (token.exists()) {
                try {
                    databaseId = FileSystemUtils.copyStreamToString(token);
                    if(databaseId != null)
                        break;
                } catch (IOException ignored) {}
            }

            databaseId = UUID.randomUUID().toString();
            try {
                try (FileOutputStream fos = new FileOutputStream(token)) {
                    FileSystemUtils.write(fos, databaseId);
                }
            } catch(IOException ignored) {}
        } while(false);

        return HashingUtils.sha256sum(databaseId + deviceId);
    }

    /**
     * Called during application startup to initialize the underlying sqlcipher database. When
     * called for the first time, the database will be created. Subsequent calls will open the
     * database and delete any credentials which have expired.
     *
     * @param context A valid application context
     * @return void
     */
    public static void initialize(Context context) {
        synchronized (getAdapter().lock) {
            if(ctx == null)
                ctx = context;
            try {
                if (!initialized) {
                    databaseFile = context.getDatabasePath("credentials.sqlite");
                    String pwd = null;
                    // if the default provider is in effect, use the legacy
                    // password to unlock, otherwise defer to the provider's
                    // mechanism
                    if(IOProviderFactory.isDefault()) {
                        pwd = getPwd(
                                context,
                                Base64.encodeToString(
                                        databaseFile.getAbsolutePath().getBytes(FileSystemUtils.UTF8_CHARSET), Base64.NO_WRAP),
                                deviceId);
                        if (pwd == null) {
                            return;
                        }
                    }

                    File parent = databaseFile.getParentFile();
                    if(parent != null && !IOProviderFactory.exists(parent)){
                        Log.d(TAG, "Creating private database directory: "
                                + parent.getAbsolutePath());
                        if(!IOProviderFactory.mkdirs(parent)){
                            Log.w(TAG, "Failed to create private database directory: "
                                    + parent.getAbsolutePath());
                        }
                    }
                    initialized = getAdapter()
                            .openOrCreateDatabase(databaseFile.getAbsolutePath(), pwd) == 0;

                    if (!initialized) {
                        // initialization can fail if pref has been cleared (with seed for pwd),
                        // but database file is left. give ourselves once chance to retry. this can
                        // happen during testing of cert migration with pre-sqlcipher versions
                        clear();
                        initialized = getAdapter()
                                .openOrCreateDatabase(databaseFile.getAbsolutePath(), pwd) == 0;
                    }

                    if(initialized)
                        getAdapter().deleteExpiredCredentials(System.currentTimeMillis());
                }

                if (!initialized) {
                    Log.e(TAG, "Failed to initialize! " + databaseFile.getAbsolutePath());
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception in initialize! " + (databaseFile == null ? ""
                        : databaseFile.getAbsolutePath()), e);
            }
        }
    }

    /**
     * Frees all runtime resources associated with the database. This function should be called
     * when the application shuts down to ensure that the sqlcihper database is properly closed.
     *
     * @return void
     */
    public static void dispose() {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling dispose prior to initialization!");
            }

            getAdapter().dispose();
            initialized = false;
        }
    }

    /**
     * Deletes the underlying sqlcipher database.
     *
     * @return void
     */
    public static void clear() {
        synchronized (getAdapter().lock) {
            IOProviderFactory.delete(databaseFile, IOProvider.SECURE_DELETE);
        }
    }

    /**
     * Returns the set of unique site/type pairs, as an array of credential objects with only the
     * site & type populated.
     *
     * 'default' credentials have type == site, and can be shared across sites and used when site
     *  specific credentials are not available. The AtakAuthenticationCredentials.isDefault method
     *  returns a boolean indicating if the credentials are defaults.
     *
     * @return AtakAuthenticationCredentials[]
     */
    public static AtakAuthenticationCredentials[] getDistinctSitesAndTypes() {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling getDistinctSitesAndTypes prior to initialization!");
                return null;
            }

            return getAdapter().getDistinctSitesAndTypes();
        }
    }

    /**
     * Returns credentials for the given type/site pair.
     *
     * @param type Credential type per TYPE_ constants in AtakAuthenticationCredentials
     * @param site FQDN or IP associated with credentials
     * @return AtakAuthenticationCredentials
     */
    public static AtakAuthenticationCredentials getCredentials(
            String type,
            String site) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling getCredentials prior to initialization!");
                return null;
            }

            //Log.i(TAG, "getCredentials = " + type);
            return getAdapter().getCredentialsForType(type, site);
        }
    }

    /**
     * Returns the default credentials for the given type.
     *
     * @param type Credential type per TYPE_ constants in AtakAuthenticationCredentials
     * @return AtakAuthenticationCredentials
     */
    public static AtakAuthenticationCredentials getCredentials(
            String type) {
        synchronized (getAdapter().lock) {
            return getCredentials(type, type);
        }
    }

    /**
     * Saves username and password credentials for the given type/site pair.
     *
     * @param type Credential type per TYPE_ constants in AtakAuthenticationCredentials
     * @param site FQDN or IP to associate credentials with
     * @param username the username to save
     * @param password the password to save
     */
    public static void saveCredentials(
            String type,
            String site,
            String username,
            String password,
            boolean expires) {
        //Log.i(TAG, "saveCredentials = " + type);

        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling saveCredentials prior to initialization!");
                return;
            }

            getAdapter().saveCredentialsForType(type, site, username, password, expires);
        }
    }

    /**
     * Saves username and password as the default credentials for the given type.
     *
     * @param type Credential type per TYPE_ constants in AtakAuthenticationCredentials
     * @param username
     * @param password
     * @return void
     */
    public static void saveCredentials(
            String type,
            String username,
            String password,
            boolean expires) {
        synchronized (getAdapter().lock) {
            saveCredentials(type, type, username, password, expires);
        }
    }

    /**
     * Delete the credentials for the specified type and site.
     *
     * @param type Credential type per TYPE_ constants in AtakAuthenticationCredentials
     * @param site FQDN or IP associated with credentials
     * @return void
     */
    public static void delete(
            String type,
            String site) {
        synchronized (getAdapter().lock) {
            if (!initialized) {
                Log.e(TAG, "Calling invalidate prior to initialization!");
                return;
            }

            getAdapter().invalidateForType(type, site);
        }
    }
}

