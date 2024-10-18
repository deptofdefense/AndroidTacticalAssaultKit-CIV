
package com.atakmap.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.androidtest.util.FileUtils;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

public abstract class AbstractAtakAuthenticationDatabaseTest
        extends ATAKInstrumentedTest {
    protected abstract AtakAuthenticationDatabaseIFace newInstance(File path);

    @Test
    public void newDatabaseIsEmpty() throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile()) {
            AtakAuthenticationDatabaseIFace authdb = newInstance(f.file);
            assertEquals(0, authdb.getDistinctSitesAndTypes().length);
        }
    }

    @Test
    public void insertTypeRoundtrip_with_expiration() throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile()) {
            final String type = "test_type";
            final String username = "abcdef";
            final String password = "123456";
            final boolean expires = true;
            AtakAuthenticationDatabaseIFace authdb = newInstance(f.file);
            authdb.saveCredentialsForType(type, username, password, 3600000L * 24L * 30L);
            AtakAuthenticationCredentials creds = authdb
                    .getCredentialsForType(type);
            assertNotNull(creds);
            assertEquals(type, creds.type);
            assertEquals(username, creds.username);
            assertEquals(password, creds.password);
        }
    }

    @Test
    public void insertTypeRoundtrip_with_no_expiration() throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile()) {
            final String type = "test_type";
            final String username = "abcdef";
            final String password = "123456";
            final boolean expires = false;
            AtakAuthenticationDatabaseIFace authdb = newInstance(f.file);
            authdb.saveCredentialsForType(type, username, password, -1);
            AtakAuthenticationCredentials creds = authdb
                    .getCredentialsForType(type);
            assertNotNull(creds);
            assertEquals(type, creds.type);
            assertEquals(username, creds.username);
            assertEquals(password, creds.password);
        }
    }

    @Test
    public void insertTypeAndServerRoundtrip_with_expiration()
            throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile()) {
            final String type = "test_type";
            final String site = "the.test.server";
            final String username = "abcdef";
            final String password = "123456";
            final boolean expires = true;
            AtakAuthenticationDatabaseIFace authdb = newInstance(f.file);
            authdb.saveCredentialsForType(type, site, username, password,
                    3600000L * 24L * 30L);
            AtakAuthenticationCredentials creds = authdb
                    .getCredentialsForType(type, site);
            assertNotNull(creds);
            assertEquals(type, creds.type);
            assertEquals(site, creds.site);
            assertEquals(username, creds.username);
            assertEquals(password, creds.password);
        }
    }

    @Test
    public void insertTypeAndServerRoundtrip_with_no_expiration()
            throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile()) {
            final String type = "test_type";
            final String site = "the.test.server";
            final String username = "abcdef";
            final String password = "123456";
            final boolean expires = false;
            AtakAuthenticationDatabaseIFace authdb = newInstance(f.file);
            authdb.saveCredentialsForType(type, site, username, password,
                    -1);
            AtakAuthenticationCredentials creds = authdb
                    .getCredentialsForType(type, site);
            assertNotNull(creds);
            assertEquals(type, creds.type);
            assertEquals(site, creds.site);
            assertEquals(username, creds.username);
            assertEquals(password, creds.password);
        }
    }

    @Test
    public void type_and_site_record_deleted_after_delete() throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile()) {
            final String type = "test_type";
            final String site = "the.test.server";
            final String username = "abcdef";
            final String password = "123456";
            final boolean expires = true;
            AtakAuthenticationDatabaseIFace authdb = newInstance(f.file);
            authdb.saveCredentialsForType(type, site, username, password,
                    3600000L * 24L * 30L);
            authdb.invalidateForType(type, site);
            AtakAuthenticationCredentials creds = authdb
                    .getCredentialsForType(type);
            assertNull(creds);
        }
    }

    @Test
    public void type_only_record_deleted_after_delete() throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile()) {
            final String type = "test_type";
            final String username = "abcdef";
            final String password = "123456";
            final boolean expires = false;
            AtakAuthenticationDatabaseIFace authdb = newInstance(f.file);
            authdb.saveCredentialsForType(type, username, password, -1);
            authdb.invalidateForType(type, type);
            AtakAuthenticationCredentials creds = authdb
                    .getCredentialsForType(type);
            assertNull(creds);
        }
    }

}
