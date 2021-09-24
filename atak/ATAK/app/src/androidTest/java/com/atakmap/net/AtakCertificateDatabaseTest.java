
package com.atakmap.net;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.androidtest.util.FileUtils;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AtakCertificateDatabaseTest extends ATAKInstrumentedTest {

    protected AtakCertificateDatabaseIFace newInstance(File path) {
        AtakCertificateDatabaseAdapter certdb = new AtakCertificateDatabaseAdapter();
        boolean dbInitialized = certdb.openOrCreateDatabase(
                path.getAbsolutePath(), "test") == 0;
        assertTrue("checking dbInitialized", dbInitialized);
        return certdb;
    }

    @Test
    public void insertTypeRoundtrip() throws IOException {
        FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile();
        final String type = AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE;
        final byte[] cert = new byte[] {
                0x01, 0x02, 0x03, 0x04
        };

        AtakCertificateDatabaseIFace certdb = newInstance(f.file);
        assertNotNull("checking certdb notNull", certdb);

        certdb.saveCertificateForType(type, cert);

        byte[] certCheck = certdb.getCertificateForType(type);
        assertNotNull("checking certCheck notNull", certCheck);
        assertEquals("checking cert length", cert.length, certCheck.length);
        assertEquals("checking byte 0", cert[0], certCheck[0]);
        assertEquals("checking byte 1", cert[1], certCheck[1]);
        assertEquals("checking byte 2", cert[2], certCheck[2]);
        assertEquals("checking byte 3", cert[3], certCheck[3]);
    }

    @Test
    public void insertTypeAndServerRoundtrip() throws IOException {
        FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile();
        final String type = AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE;
        final String server = "tak.gov";
        final byte[] cert = new byte[] {
                0x01, 0x02, 0x03, 0x04
        };

        AtakCertificateDatabaseIFace certdb = newInstance(f.file);
        assertNotNull("checking certdb notNull", certdb);

        certdb.saveCertificateForTypeAndServer(type, server, cert);

        byte[] certCheck = certdb.getCertificateForTypeAndServer(type, server);
        assertNotNull("checking certCheck notNull", certCheck);
        assertEquals("checking cert length", cert.length, certCheck.length);
        assertEquals("checking byte 0", cert[0], certCheck[0]);
        assertEquals("checking byte 1", cert[1], certCheck[1]);
        assertEquals("checking byte 2", cert[2], certCheck[2]);
        assertEquals("checking byte 3", cert[3], certCheck[3]);
    }

    @Test
    public void insertTypeAndServerAndPortRoundtrip() throws IOException {
        FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile();
        final String type = AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE;
        final String server = "tak.gov";
        final int port = 8089;
        final byte[] cert = new byte[] {
                0x01, 0x02, 0x03, 0x04
        };

        AtakCertificateDatabaseIFace certdb = newInstance(f.file);
        assertNotNull("checking certdb notNull", certdb);

        certdb.saveCertificateForTypeAndServerAndPort(type, server, port, cert);

        byte[] certCheck = certdb.getCertificateForTypeAndServerAndPort(type,
                server, port);
        assertNotNull("checking certCheck notNull", certCheck);
        assertEquals("checking cert length", cert.length, certCheck.length);
        assertEquals("checking byte 0", cert[0], certCheck[0]);
        assertEquals("checking byte 1", cert[1], certCheck[1]);
        assertEquals("checking byte 2", cert[2], certCheck[2]);
        assertEquals("checking byte 3", cert[3], certCheck[3]);
    }

    @Test
    public void insertWithTypeAndServerAndPortButLookupByTypeAndServerOnly()
            throws IOException {
        FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile();
        final String type = AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE;
        final String server = "tak.gov";
        final int port = 8089;
        final byte[] cert = new byte[] {
                0x01, 0x02, 0x03, 0x04
        };

        AtakCertificateDatabaseIFace certdb = newInstance(f.file);
        assertNotNull("checking certdb notNull", certdb);

        certdb.saveCertificateForTypeAndServerAndPort(type, server, port, cert);

        byte[] certCheck = certdb.getCertificateForTypeAndServer(type, server);
        assertNotNull("checking certCheck notNull", certCheck);
        assertEquals("checking cert length", cert.length, certCheck.length);
        assertEquals("checking byte 0", cert[0], certCheck[0]);
        assertEquals("checking byte 1", cert[1], certCheck[1]);
        assertEquals("checking byte 2", cert[2], certCheck[2]);
        assertEquals("checking byte 3", cert[3], certCheck[3]);
    }

    @Test
    public void insertWithTypeAndServerButLookupByTypeAndServerAndPort()
            throws IOException {
        FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile();
        final String type = AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE;
        final String server = "tak.gov";
        final int port = 8089;
        final byte[] cert = new byte[] {
                0x01, 0x02, 0x03, 0x04
        };

        AtakCertificateDatabaseIFace certdb = newInstance(f.file);
        assertNotNull("checking certdb notNull", certdb);

        certdb.saveCertificateForTypeAndServer(type, server, cert);

        byte[] certCheck = certdb.getCertificateForTypeAndServerAndPort(type,
                server, port);
        assertNotNull("checking certCheck notNull", certCheck);
        assertEquals("checking cert length", cert.length, certCheck.length);
        assertEquals("checking byte 0", cert[0], certCheck[0]);
        assertEquals("checking byte 1", cert[1], certCheck[1]);
        assertEquals("checking byte 2", cert[2], certCheck[2]);
        assertEquals("checking byte 3", cert[3], certCheck[3]);
    }

    @Test
    public void insertThenDeleteByPort() throws IOException {
        FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile();
        final String type = AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE;
        final String server = "tak.gov";
        final int port = 8089;
        final byte[] cert = new byte[] {
                0x01, 0x02, 0x03, 0x04
        };

        AtakCertificateDatabaseIFace certdb = newInstance(f.file);
        assertNotNull("checking certdb notNull", certdb);

        certdb.saveCertificateForTypeAndServerAndPort(type, server, port, cert);

        certdb.deleteCertificateForTypeAndServerAndPort(type, server, port);

        byte[] certCheck = certdb.getCertificateForTypeAndServer(type, server);
        assertEquals("checking certCheck notNull", null, certCheck);
    }

    @Test
    public void insertThenDeleteMixed() throws IOException {
        FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile();
        final String type = AtakCertificateDatabaseAdapter.TYPE_CLIENT_CERTIFICATE;
        final String server = "tak.gov";
        final int port = 8089;
        final byte[] cert = new byte[] {
                0x01, 0x02, 0x03, 0x04
        };

        AtakCertificateDatabaseIFace certdb = newInstance(f.file);
        assertNotNull("checking certdb notNull", certdb);

        certdb.saveCertificateForTypeAndServerAndPort(type, server, port, cert);

        certdb.deleteCertificateForTypeAndServer(type, server);

        byte[] certCheck = certdb.getCertificateForTypeAndServer(type, server);
        assertEquals("checking certCheck notNull", null, certCheck);
    }
}
