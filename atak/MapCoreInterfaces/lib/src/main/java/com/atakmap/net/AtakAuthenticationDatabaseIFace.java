
package com.atakmap.net;

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
     */
    void saveCredentialsForType(
            String type,
            String site,
            String username,
            String password,
            boolean expires);

    /**
     * Saves the specific type of credentials.
     * @param type the type
     * @param username the username to save
     * @param password the password to save
     */
    void saveCredentialsForType(
            String type,
            String username,
            String password,
            boolean expires);

    void invalidateForType(
            String type,
            String site);

    boolean deleteExpiredCredentials(long time);

    AtakAuthenticationCredentials[] getDistinctSitesAndTypes();

    void dispose();
}
