package com.atakmap.commoncommo;

/**
 * Enum representing the protocols by which
 * cloud interactions may be performed
 */
public enum CloudIOProtocol {
    /** http, no SSL */
    HTTP(0),
    /** http with SSL */
    HTTPS(1),
    /** ftp, no SSL */
    FTP(2),
    /** ftp with SSL */
    FTPS(3);
    
    private final int id;
    
    private CloudIOProtocol(int id) {
        this.id = id;
    }
    
    int getNativeVal() {
        return id;
    }
}
