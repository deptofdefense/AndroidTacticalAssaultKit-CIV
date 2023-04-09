package com.atakmap.net;

import android.util.Pair;
import com.atakmap.comms.NetConnectString;
import gov.tak.test.KernelJniTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AtakCertificateDatabaseAdapterTest extends KernelJniTest
{
    private AtakCertificateDatabaseAdapter createInitializedDatabase()
    {
        AtakCertificateDatabaseAdapter db = new AtakCertificateDatabaseAdapter();
        try
        {
            final int opened = db.openOrCreateDatabase(File.createTempFile("certdb", ".sqlite").getAbsolutePath(), null);
            Assert.assertEquals(0, opened);

            return db;
        } catch(IOException e)
        {
            Assert.fail();
            throw new IllegalStateException(e); // unrechable
        }
    }


    // basic public API

    @Test
    public void openOrCreateDatabase() throws IOException {
        AtakCertificateDatabaseAdapter db = new AtakCertificateDatabaseAdapter();

        final String path = File.createTempFile("certdb", ".sqlite").getAbsolutePath();
        final boolean opened = db.openOrCreateDatabase(path);
        Assert.assertTrue(opened);
    }

    @Test
    public void openOrCreateDatabase_memory() throws IOException {
        AtakCertificateDatabaseAdapter db = new AtakCertificateDatabaseAdapter();

        final String path = null;
        final boolean opened = db.openOrCreateDatabase(path);
        Assert.assertTrue(opened);
    }

    @Test
    public void dispose_intialized()  {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        db.dispose();
    }

    @Test
    public void dispose_unintialized() {
        AtakCertificateDatabaseAdapter db = new AtakCertificateDatabaseAdapter();
        db.dispose();
    }

    private void clientCertificateRoundtrip(boolean validateHash) {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForType(type, data);

        final byte[] stored = db.getCertificateForType(type, validateHash);
        Assert.assertNotNull(stored);

        Assert.assertArrayEquals(data, stored);
    }

    @Test
    public void getCertificateForType_validate_hash() {
        clientCertificateRoundtrip(true);
    }

    @Test
    public void getCertificateForType_no_validate_hash() {
        clientCertificateRoundtrip(false);
    }

    @Test
    public void getCertificateForType_miss() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForType(type, data);

        final byte[] stored = db.getCertificateForType("typedoesnotexist", false);
        Assert.assertNull(stored);
    }

    public void serverCertificateRoundtrip_server(boolean validateHash) {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServer(type, server, data);

        final byte[] stored = db.getCertificateForTypeAndServer(type, server, validateHash);
        Assert.assertNotNull(stored);

        Assert.assertArrayEquals(data, stored);
    }

    @Test
    public void getCertificateForTypeAndServer_validate_hash() {
        serverCertificateRoundtrip_server(true);
    }

    @Test
    public void getCertificateForTypeAndServer_no_validate_hash() {
        serverCertificateRoundtrip_server(false);
    }

    @Test
    public void getCertificateForTypeAndServer_miss_type() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServer(type, server, data);

        final byte[] stored = db.getCertificateForTypeAndServer("othertype", server, false);
        Assert.assertNull(stored);
    }

    @Test
    public void getCertificateForTypeAndServer_miss_domain() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServer(type, server, data);

        final byte[] stored = db.getCertificateForTypeAndServer(type, "otherdomain.com", false);
        Assert.assertNull(stored);
    }

    private void serverCertificateRoundtrip_server_port(boolean validateHash) {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final int port = 1234;
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServerAndPort(type, server, port, data);

        final byte[] stored = db.getCertificateForTypeAndServerAndPort(type, server, port, validateHash);
        Assert.assertNotNull(stored);

        Assert.assertArrayEquals(data, stored);
    }

    @Test
    public void getCertificateForTypeAndServerAndPort_fallback_for_miss_on_port() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final int port = 1234;
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServerAndPort(type, server, port, data);

        // request on different port than insert
        final byte[] stored = db.getCertificateForTypeAndServerAndPort(type, server, 4321);
        Assert.assertNotNull(stored);

        Assert.assertArrayEquals(data, stored);
    }

    @Test
    public void getCertificateForTypeAndServerAndPort_validate_hash() {
        serverCertificateRoundtrip_server_port(true);
    }

    @Test
    public void getCertificateForTypeAndServerAndPort_no_validate_hash() {
        serverCertificateRoundtrip_server_port(false);
    }

    @Test
    public void getCertificateForIPaddress() throws UnknownHostException {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "git.tak.gov";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServer(type, server, data);

        final Pair<byte[], String> stored = db.getCertificateForIPaddress(type, InetAddress.getByName(server).getHostAddress());
        Assert.assertNotNull(stored);
        Assert.assertNotNull(stored.first);
        Assert.assertNotNull(stored.second);

        Assert.assertArrayEquals(data, stored.first);
    }


    @Test
    public void getCertificateForIPaddressAndPort() throws UnknownHostException {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "git.tak.gov";
        final int port = 1234;
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServerAndPort(type, server, port, data);

        final Pair<byte[], String> stored = db.getCertificateForIPaddressAndPort(type, InetAddress.getByName(server).getHostAddress(), port);
        Assert.assertNotNull(stored);
        Assert.assertNotNull(stored.first);
        Assert.assertNotNull(stored.second);

        Assert.assertArrayEquals(data, stored.first);
    }

    @Test
    public void saveCertificateForType() {
        clientCertificateRoundtrip(false);
    }

    @Test
    public void saveCertificateForTypeAndServer() {
        serverCertificateRoundtrip_server(false);
    }

    @Test
    public void saveCertificateForTypeAndServerAndPort() {
        serverCertificateRoundtrip_server_port(false);
    }

    @Test
    public void deleteCertificateForType() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForType(type, data);
        db.deleteCertificateForType(type);

        final byte[] stored = db.getCertificateForType(type, false);
        Assert.assertNull(stored);
    }

    @Test
    public void deleteCertificateForTypeAndServer() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServer(type, server, data);

        db.deleteCertificateForTypeAndServer(type, server);

        final byte[] stored = db.getCertificateForTypeAndServer(type, server, false);
        Assert.assertNull(stored);
    }

    @Test
    public void deleteCertificateForTypeAndServerAndPort() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final int port = 1234;
        final byte[] data = "0123456789".getBytes();
        db.saveCertificateForTypeAndServer(type, server, data);

        db.deleteCertificateForTypeAndServerAndPort(type, server, port);

        final byte[] stored = db.getCertificateForTypeAndServerAndPort(type, server, port, false);
        Assert.assertNull(stored);
    }

    @Test
    public void getServers() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final byte[] data = "0123456789".getBytes();

        final String type1 = "type1";
        final NetConnectString[] servers1 = new NetConnectString[]
                {
                        new NetConnectString("ssl", "a.somedomain.com", 1234),
                        new NetConnectString("ssl", "b.somedomain.com", 1234),
                        new NetConnectString("ssl", "a.somedomain.com", 4321),
                };

        Set<String> servers1s = new HashSet<>();
        for(NetConnectString server : servers1) {
            db.saveCertificateForTypeAndServerAndPort(type1, server.getHost(), server.getPort(), data);
            servers1s.add(server.getHost());
        }

        final String type2 = "type2";
        final NetConnectString[] servers2 = new NetConnectString[]
                {
                        new NetConnectString("ssl", "otherdomain.com", 1234),
                };

        Set<String> servers2s = new HashSet<>();
        for(NetConnectString server : servers2) {
            db.saveCertificateForTypeAndServerAndPort(type2, server.getHost(), server.getPort(), data);
            servers2s.add(server.getHost());
        }

        String[] dbServers1 = db.getServers(type1);
        Assert.assertTrue(servers1s.containsAll(Arrays.asList(dbServers1)));
        Assert.assertTrue(Arrays.asList(dbServers1).containsAll(servers1s));

        String[] dbServers2 = db.getServers(type2);
        Assert.assertTrue(servers2s.containsAll(Arrays.asList(dbServers2)));
        Assert.assertTrue(Arrays.asList(dbServers2).containsAll(servers2s));
    }

    @Test
    public void getServerConnectStrings() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final byte[] data = "0123456789".getBytes();

        final String type1 = "type1";
        final NetConnectString[] servers1 = new NetConnectString[]
        {
            new NetConnectString("ssl", "a.somedomain.com", 1234),
            new NetConnectString("ssl", "b.somedomain.com", 1234),
            new NetConnectString("ssl", "a.somedomain.com", 4321),
        };

        for(NetConnectString server : servers1) {
            db.saveCertificateForTypeAndServerAndPort(type1, server.getHost(), server.getPort(), data);
        }

        final String type2 = "type2";
        final NetConnectString[] servers2 = new NetConnectString[]
        {
            new NetConnectString("ssl", "otherdomain.com", 1234),
        };

        for(NetConnectString server : servers2) {
            db.saveCertificateForTypeAndServerAndPort(type2, server.getHost(), server.getPort(), data);
        }

        NetConnectString[] dbServers1 = db.getServerConnectStrings(type1);
        assertNetConnectStringsEqual(servers1, dbServers1);

        NetConnectString[] dbServers2 = db.getServerConnectStrings(type2);
        assertNetConnectStringsEqual(servers2, dbServers2);
    }

    private void assertNetConnectStringsEqual(NetConnectString[] expected, NetConnectString[] actual) {
        Assert.assertEquals(expected == null, actual == null);
        if(expected == null)    return; // done

        Assert.assertEquals(expected.length, actual.length);

        for(int i = 0; i < actual.length; i++) {
            Assert.assertTrue(contains(expected, actual[i]));
        }

        for(int i = 0; i < expected.length; i++) {
            Assert.assertTrue(contains(actual, expected[i]));
        }
    }

    private boolean contains(NetConnectString[] array, NetConnectString value) {
        for(int i = 0; i < array.length; i++) {
            if(value == null && array[i] == null) {
                return true;
            } else if(value != null && array[i] != null) {
                if(value.equals(array[i]))
                    return true;
            }
        }
        return false;
    }

    // branches

    @Test
    public void updatedClientCertificateRoundtrip() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final byte[] data1 = "0123456789".getBytes();
        final byte[] data2 = "ABCDEFGHIJ".getBytes();
        // original
        db.saveCertificateForType(type, data1);
        // update
        db.saveCertificateForType(type, data2);

        final byte[] stored = db.getCertificateForType(type, false);
        Assert.assertNotNull(stored);

        Assert.assertArrayEquals(data2, stored);
    }

    @Test
    public void updatedServerCertificateRoundtrip_server() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final byte[] data1 = "0123456789".getBytes();
        final byte[] data2 = "ABCDEFGHIJ".getBytes();
        // original
        db.saveCertificateForTypeAndServer(type, server, data1);
        // update
        db.saveCertificateForTypeAndServer(type, server, data2);

        final byte[] stored = db.getCertificateForTypeAndServer(type, server, false);
        Assert.assertNotNull(stored);

        Assert.assertArrayEquals(data2, stored);
    }

    @Test
    public void updatedServerCertificateRoundtrip_server_port() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final int port = 1234;
        final byte[] data1 = "0123456789".getBytes();
        final byte[] data2 = "ABCDEFGHIJ".getBytes();
        // original
        db.saveCertificateForTypeAndServerAndPort(type, server, port, data1);
        // update
        db.saveCertificateForTypeAndServerAndPort(type, server, port, data2);

        final byte[] stored = db.getCertificateForTypeAndServerAndPort(type, server, port, false);
        Assert.assertNotNull(stored);

        Assert.assertArrayEquals(data2, stored);
    }

    @Test
    public void saveCertificate_distinct_over_same_type() {
        AtakCertificateDatabaseAdapter db = createInitializedDatabase();
        final String type = "type";
        final String server = "somedomain.com";
        final int port = 1234;
        final byte[] data1 = "0123456789".getBytes();
        final byte[] data2 = "ABCDEFGHIJ".getBytes();
        // without server
        db.saveCertificateForType(type, data1);
        // with server
        db.saveCertificateForTypeAndServerAndPort(type, server, port, data2);

        final byte[] stored1 = db.getCertificateForType(type, false);
        Assert.assertNotNull(stored1);

        Assert.assertArrayEquals(data1, stored1);

        final byte[] stored2 = db.getCertificateForTypeAndServerAndPort(type, server, port, false);
        Assert.assertNotNull(stored2);

        Assert.assertArrayEquals(data2, stored2);
    }
}
