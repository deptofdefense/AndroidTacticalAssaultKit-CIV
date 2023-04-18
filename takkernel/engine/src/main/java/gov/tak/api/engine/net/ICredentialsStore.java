
package gov.tak.api.engine.net;

import com.atakmap.annotations.FortifyFinding;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import gov.tak.api.util.Disposable;

public interface ICredentialsStore extends Disposable
{

    class Credentials {
        /**
         * Default type used until code initializes to a specific type
         */
        public static final String TYPE_UNKNOWN = "UNKNOWN";

        /**
         * HTTP Basic Auth credentials, may be stored per site
         */
        public static final String TYPE_HTTP_BASIC_AUTH = "HTTP_BASIC_AUTH";

        /**
         * CoT Service (TAK Server) credentials, may be stored per connection
         */
        public static final String TYPE_COT_SERVICE = "COT_SERVICE";

        /**
         * Credentials for Over the Air update server
         */
        public static final String TYPE_APK_DOWNLOADER = "APK_DOWNLOADER";

        /**
         * TAK Server CA Truststore password
         */
        @FortifyFinding(finding="Password Management: Hardcoded Password", rational = "This is only a key and not a password")
        public static final String TYPE_caPassword = "caPassword";

        /**
         * Update Server CA Truststore password
         */
        @FortifyFinding(finding="Password Management: Hardcoded Password", rational = "This is only a key and not a password")
        public static final String TYPE_updateServerCaPassword = "updateServerCaPassword";

        /**
         * TAK Server Client certificate password
         */
        @FortifyFinding(finding="Password Management: Hardcoded Password", rational = "This is only a key and not a password")
        public static final String TYPE_clientPassword = "clientPassword";

        /**
         * Video alias password
         */
        @FortifyFinding(finding="Password Management: Hardcoded Password", rational = "This is only a key and not a password")
        public static final String TYPE_videoPassword = "videoPassword";

        public String type = TYPE_UNKNOWN;
        public String site = "";
        public String username = "";

        @FortifyFinding(finding="Password Management: Hardcoded Password", rational = "This is a empty assignment just for the purposes of making the code simpler instead of extra null pointer checks.    This is not hardcoded.")
        public String password = "";

        /***
         * 'default' credentials have type == site, and can be shared across sites and used when site
         *  specific credentials are not available.
         *
         * @return boolean indicating if the credentials are defaults
         */
        public boolean isDefault() {
            return type != null && site != null && type.compareTo(site) == 0;
        }

        /**
         * Provide for an isValid check which indicates when the Authentication Credentials can be used.
         * This occurs when the site is empty or the password is empty.
         * @return true if the Credentials object is valid.
         */
        public boolean isValid() {
            return !FileSystemUtils.isEmpty(site)
                    && !FileSystemUtils.isEmpty(password);
        }
    }

    /**
     * Obtains the specific type of credentials for a given site.
     * @param type the type
     * @param site the site
     * @return the credentials
     */
    Credentials getCredentials(
            String type,
            String site);

    /**
     * Obtains the specific type of credentials.
     * @param type the type
     * @return the credentials
     */
    Credentials getCredentials(
            String type);

    /**
     * Saves the specific type of credentials for a given site.
     * @param type the type
     * @param site the site
     * @param username the username to save
     * @param password the password to save
     * @param expires the number of milliseconds that this certificate remains valid.
     */
    void saveCredentials(
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
     * @param expires the number of milliseconds that this certificate remains valid.
     */
    void saveCredentials(
            String type,
            String username,
            String password,
            long expires);

    void invalidate(
            String type,
            String site);

    boolean deleteExpiredCredentials(long time);

    Credentials[] getDistinctSitesAndTypes();
}
