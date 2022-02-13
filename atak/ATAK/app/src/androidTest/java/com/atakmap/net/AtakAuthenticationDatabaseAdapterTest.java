
package com.atakmap.net;

import java.io.File;
import java.io.IOException;

public class AtakAuthenticationDatabaseAdapterTest
        extends AbstractAtakAuthenticationDatabaseTest {
    @Override
    protected AtakAuthenticationDatabaseIFace newInstance(File path) {
        AtakAuthenticationDatabaseAdapter authdb = new AtakAuthenticationDatabaseAdapter();
        authdb.openOrCreateDatabase(path.getAbsolutePath());
        return authdb;
    }

    @Override
    public void newDatabaseIsEmpty() throws IOException {
        super.newDatabaseIsEmpty();
    }

    @Override
    public void insertTypeRoundtrip_with_expiration() throws IOException {
        super.insertTypeRoundtrip_with_expiration();
    }

    @Override
    public void insertTypeRoundtrip_with_no_expiration() throws IOException {
        super.insertTypeRoundtrip_with_no_expiration();
    }

    @Override
    public void insertTypeAndServerRoundtrip_with_expiration()
            throws IOException {
        super.insertTypeAndServerRoundtrip_with_expiration();

    }

    @Override
    public void insertTypeAndServerRoundtrip_with_no_expiration()
            throws IOException {
        super.insertTypeAndServerRoundtrip_with_no_expiration();
    }

    @Override
    public void type_and_site_record_deleted_after_delete() throws IOException {
        super.type_and_site_record_deleted_after_delete();
    }

    @Override
    public void type_only_record_deleted_after_delete() throws IOException {
        super.type_only_record_deleted_after_delete();
    }
}
