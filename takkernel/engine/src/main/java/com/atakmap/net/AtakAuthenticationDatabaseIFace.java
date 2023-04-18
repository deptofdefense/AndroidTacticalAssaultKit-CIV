
package com.atakmap.net;

import gov.tak.api.util.Disposable;

public interface AtakAuthenticationDatabaseIFace extends Disposable {

    /**
     * The value used in the case where credentials should be persisted and not
     * removed after an expiration time.
     */
    int PERPETUAL = -1;

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
     * @param expires the number of milliseconds that this certificate remains valid
     *                or pass in the constant @link PERPETUAL.
     */
    void saveCredentialsForType(
            String type,
            String site,
            String username,
            String password,
            long expires);

    /**
     * Saves the specific type of credentials for a given site.
     * @param type the type
     * @param username the username to save
     * @param password the password to save
     * @param expires the number of milliseconds that this certificate remains valid
     *                or pass in the constant @link PERPETUAL.
     */
    void saveCredentialsForType(
            String type,
            String username,
            String password,
            long expires);

    /**
     * Deletes the {@link AtakAuthenticationCredentials}  by the give
     * type and site.
     *
     * @param type the type to remove
     * @param site the site to remove.
     */
    void invalidateForType(
            String type,
            String site);

    /**
     * Delete the expired credentials give a time.
     *
     * @param time the time in millis since epoch
     * @return True if successful, false otherwise.
     */
    boolean deleteExpiredCredentials(long time);

    /**
     * Gets a distinct array of {@link AtakAuthenticationCredentials} only populated with
     * site and type.
     *
     * @return distinct array of {@link AtakAuthenticationCredentials}
     */
    AtakAuthenticationCredentials[] getDistinctSitesAndTypes();

}
