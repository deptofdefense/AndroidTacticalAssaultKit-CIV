package com.atakmap.commoncommo;

/**
 * Enum representing error codes reported back to InterfaceStatusListeners
 */
public enum NetInterfaceErrorCode {
    /** Name resolution for a hostname failed */
    CONN_NAME_RES_FAILED(0),
    /** Connection to remote host actively refused */
    CONN_REFUSED(1),
    /** Connection remote host timed out */
    CONN_TIMEOUT(2),
    /** Remote host is known to be unreachable at this time */
    CONN_HOST_UNREACHABLE(3),
    /** Remote host was expected to present an SSL certificate but didn't */
    CONN_SSL_NO_PEER_CERT(4),
    /** Remote host's SSL certificate was not trusted */
    CONN_SSL_PEER_CERT_NOT_TRUSTED(5),
    /** SSL handshake with remote host encountered an error */
    CONN_SSL_HANDSHAKE(6),
    /** Some other, non-specific, error occurred during attempting
     *  to connect to a remote host
     */
    CONN_OTHER(7),
    /** No data was received and the connection was considered
     *  in error/timed out and is being reset
     */
    IO_RX_DATA_TIMEOUT(8),
    /** A general IO error occurred */
    IO(9),
    /** Some internal error occurred (out of memory, etc) */
    INTERNAL(10),
    /** Some unclassified error has occurred */
    OTHER(11);

    
    private final int id;
    
    private NetInterfaceErrorCode(int id) {
        this.id = id;
    }
    
    int getNativeVal() {
        return id;
    }
}
