
package com.atakmap.comms;

import com.atakmap.coremap.log.Log;

import java.util.Locale;

public class SslNetCotPort {

    public static final String TAG = "SslNetCotPort";

    public enum Type {
        UNSECURE, //HTTP
        SECURE, //HTTPS with client certificate
        CERT_ENROLLMENT //HTTPS with HTTP Basic Authentication
    }

    private static int SERVER_API_PORT_UNSECURE = 8080;
    private static int SERVER_API_PORT_SECURE = 8443;
    private static int SERVER_API_PORT_CERT_ENROLLMENT = 8446;

    /**
     * The unsecure server api port to use by TAK.
     * @param port the port number to use when performing insecure communications
     */
    public static void setUnsecureServerApiPort(int port) {
        SERVER_API_PORT_UNSECURE = port;
    }

    /**
     * The secure server api port to use by TAK.
     * @param port the port number to use when performing secure communications
     */
    public static void setSecureServerApiPort(int port) {
        SERVER_API_PORT_SECURE = port;
    }

    /**
     * The cert enrollment api port to use by TAK.
     * @param port the cert enrollment port number to use when performing certificate enrollment
     */
    public static void setCertEnrollmentApiPort(int port) {
        SERVER_API_PORT_CERT_ENROLLMENT = port;
    }

    /**
     * The port based on the type requested.
     * @param type the type which is one of SERVER_API_PORT_SECURE, SERVER_API_PORT_UNSECURE,
     *             or SERVER_API_PORT_CERT_ENROLLMENT.
     * @return the port based on the type request.
     */
    public static int getServerApiPort(Type type) {
        switch (type) {
            case SECURE:
                return SERVER_API_PORT_SECURE;
            case UNSECURE:
                return SERVER_API_PORT_UNSECURE;
            case CERT_ENROLLMENT:
                return SERVER_API_PORT_CERT_ENROLLMENT;
        }

        Log.e(TAG, "Unknown port type requested!");
        return -1;
    }

    /**
     * Returns ":<port>/Marti"  e.g. :8443/Marti
     *
     * @param type the server API path based on he type which is one of SERVER_API_PORT_SECURE,
     *             SERVER_API_PORT_UNSECURE, or SERVER_API_PORT_CERT_ENROLLMENT.
     * @return the path based on the type provided.
     */
    public static String getServerApiPath(Type type) {
        return String.format(Locale.ENGLISH, ":%d/Marti",
                getServerApiPort(type));
    }
}
