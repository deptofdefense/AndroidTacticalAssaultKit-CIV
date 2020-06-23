
package com.atakmap.comms.app;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Represents a truststore and password to access stored certificate
 */
public class TrustStore {
    /**
     * The truststore
     */
    private final byte[] data;

    /**
     * The truststore pass
     */
    private final String pass;

    public TrustStore(final byte[] data, final String pass) {
        this.data = data;
        this.pass = pass;
    }

    /**
     * Obtain the data that represents this trust store.
     * @return the byte array
     */
    public byte[] getData() {
        return data;
    }

    public String getPass() {
        return pass;
    }

    public boolean isValid() {
        return data != null && data.length > 0
                && !FileSystemUtils.isEmpty(pass);
    }
}
