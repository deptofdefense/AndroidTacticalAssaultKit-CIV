
package com.atakmap.net;

import java.io.File;

public class AtakAuthenticationDatabaseAdapterTest
        extends AbstractAtakAuthenticationDatabaseTest {
    @Override
    protected AtakAuthenticationDatabaseIFace newInstance(File path) {
        AtakAuthenticationDatabaseAdapter authdb = new AtakAuthenticationDatabaseAdapter();
        authdb.openOrCreateDatabase(path.getAbsolutePath());
        return authdb;
    }
}
