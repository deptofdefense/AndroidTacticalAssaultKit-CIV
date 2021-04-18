
package com.atakmap.net;

import com.atakmap.annotations.DeprecatedApi;

public interface AtakAuthenticationDatabaseIFace {

    /**
     * Obtains the specific type of credentials for a given site.
     * @param type the type
     * @param site the site
     * @return the credentials
     */
    AtakAuthenticationCredentials getCredentialsForType(
            String type,
            String site);

    /**
     * Obtains the specific type of credentials.
     * @param type the type
     * @return the credentials
     */
    AtakAuthenticationCredentials getCredentialsForType(
            String type);

    /**
     * Saves the specific type of credentials for a given site.
     * @param type the type
     * @param site the site
     * @param username the username to save
     * @param password the password to save
     * @param expires true if the system should expire the certificate after 30 days
     * @deprecated see saveCredentialsForType with a number passed in.
     */
    @Deprecated
    @DeprecatedApi(since = "4.2.1", forRemoval = true, removeAt = "4.5")
    void saveCredentialsForType(
            String type,
            String site,
            String username,
            String password,
            boolean expires);

    /**
     * Saves the specific type of credentials for a given site.
     * @param type the type
     * @param site the site
     * @param username the username to save
     * @param password the password to save
     * @param expires the number of milliseconds that this certificate remains valid.
     */
    void saveCredentialsForType(
            String type,
            String site,
            String username,
            String password,
            long expires);

    /**
     * Saves the specific type of credentials.
     * @param type the type
     * @param username the username to save
     * @param password the password to save
     * @deprecated see saveCredentialsForType with a number passed in.
     */
    @Deprecated
    @DeprecatedApi(since = "4.2.1", forRemoval = true, removeAt = "4.5")
    void saveCredentialsForType(
            String type,
            String username,
            String password,
            boolean expires);

    /**
     * Saves the specific type of credentials for a given site.
     * @param type the type
     * @param username the username to save
     * @param password the password to save
     * @param expires the number of milliseconds that this certificate remains valid.
     */
    void saveCredentialsForType(
            String type,
            String username,
            String password,
            long expires);

    void invalidateForType(
            String type,
            String site);

    boolean deleteExpiredCredentials(long time);

    AtakAuthenticationCredentials[] getDistinctSitesAndTypes();

    void dispose();
}
