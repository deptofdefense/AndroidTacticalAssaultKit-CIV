package com.atakmap.commoncommo;


/**
 * Enumeration for status values associated with simple file
 * transfers. See the SimpleFileIOUpdate class and
 * simpleFileTransferInit() in Commo.
 * Status codes other than INPROGRESS indicate a completed transfer,
 * with SUCCESS being the only one indicated transfer was successful; all
 * others are various forms of errors.
 */
public enum SimpleFileIOStatus {
    /**
     * The transfer is in progress
     */
    INPROGRESS(0),
    /**
     * The transfer succeeded
     */
    SUCCESS(1),
    /**
     * The host part of the remote URI for the transfer was not able
     * to be resolved.
     */
    HOST_RESOLUTION_FAIL(2),
    /**
     * Connection to the remote host identified in the URI failed
     */
    CONNECT_FAIL(3),
    /**
     * The provided URI was invalid; check the syntax of the URI
     */
    URL_INVALID(4),
    /**
     * The provided URI was not supported
     */
    URL_UNSUPPORTED(5),
    /**
     * The provided URI pointed to a file or directory that didn't exist
     * on the remote server.
     */
    URL_NO_RESOURCE(6),
    /**
     * The local file indicated could not be opened (containing directory
     * did not exist/wasn't writable for downloads,
     * file didn't exist for uploads, etc)
     */
    LOCAL_FILE_OPEN_FAILURE(7),
    /**
     * An I/O error occurred reading or writing the local file.
     */
    LOCAL_IO_ERROR(8),
    /**
     * The remote server presented an SSL certificate that was not
     * signed by the provided CA certificate(s)
     */
    SSL_UNTRUSTED_SERVER(9),
    /**
     * An uncategorized SSL error occurred while communicating with the
     * remote server.  One common cause is giving an SSL-based protocol
     * for a non-SSL server port, or vice-versa.
     */
    SSL_OTHER_ERROR(10),
    /**
     * Authentication failed due to incorrect username/password combination.
     */
    AUTH_ERROR(11),
    /**
     * Access to the remote resource identified in the URI was not granted by
     * the server.  You may need to log in using username/password or check
     * server permissions.
     * NOTE: With FTP downloads, some servers will (oddly) return this error
     *       instead of URL_NO_RESOURCE. The additional error text
     *       will help to differentiate in most cases, but does not have
     *       consisten format from server to server.
     */
    ACCESS_DENIED(12),
    /**
     * The transfer timed out and did not complete.
     */
    TRANSFER_TIMEOUT(13),
    /**
     * Catch-all of other uncategorized errors
     */
    OTHER_ERROR(14);
    
    
    private final int id;
    
    private SimpleFileIOStatus(int id) {
        this.id = id;
    }
    
    int getNativeVal() {
        return id;
    }
}

