
package com.atakmap.net;

import gov.tak.api.engine.net.ICredentialsStore;

public class AtakAuthenticationCredentials extends ICredentialsStore.Credentials {
    public AtakAuthenticationCredentials() {}

    public AtakAuthenticationCredentials(ICredentialsStore.Credentials other) {
        this.password = other.password;
        this.site = other.site;
        this.type = other.type;
        this.username = other.username;
    }
}
