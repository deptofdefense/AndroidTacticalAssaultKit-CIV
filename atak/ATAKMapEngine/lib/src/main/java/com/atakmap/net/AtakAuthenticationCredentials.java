
package com.atakmap.net;

import com.atakmap.coremap.filesystem.FileSystemUtils;

public class AtakAuthenticationCredentials {

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
     * Fortify has flagged this as Password Management: Hardcoded Password
     * this is only a key.
     */
    public static final String TYPE_caPassword = "caPassword";

    /**
     * Update Server CA Truststore password
     * Fortify has flagged this as Password Management: Hardcoded Password
     * this is only a key.
     */
    public static final String TYPE_updateServerCaPassword = "updateServerCaPassword";

    /**
     * TAK Server Client certificate password
     * Fortify has flagged this as Password Management: Hardcoded Password
     * this is only a key.
     */
    public static final String TYPE_clientPassword = "clientPassword";

    /**
     * Video alias password
     * Fortify has flagged this as Password Management: Hardcoded Password
     * this is only a key.
     */
    public static final String TYPE_videoPassword = "videoPassword";

    public String type = TYPE_UNKNOWN;
    public String site = "";
    public String username = "";

    /**
     * Fortify has flagged this as Password Management: Hardcoded Password
     * This is a empty assignment just for the purposes of making the code simpler instead of
     * extra null pointer checks.    This is not hardcoded.
     */
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
     * @return true if the AtakAuthenticationCredentials object is valid.
     */
    public boolean isValid() {
        return !FileSystemUtils.isEmpty(site)
                && !FileSystemUtils.isEmpty(password);
    }
}
