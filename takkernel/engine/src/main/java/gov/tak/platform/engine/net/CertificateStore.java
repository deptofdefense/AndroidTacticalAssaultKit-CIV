package gov.tak.platform.engine.net;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.database.impl.DatabaseImpl;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import gov.tak.api.engine.net.ICertificateStore;

/**
 * Class responsible for storing Certificates
 *
 */
public final class CertificateStore implements ICertificateStore {

    /**
     * Tag for logging
     */
    private static final String TAG = "CertificateStore";
    /**
     * Holds the certificate database.
     */
    private DatabaseIface certDb;

    final Object lock = new Object();

    /**
     * The constructor
     */
    public CertificateStore(String absolutePath, String pwd) throws IOException {
        if(openOrCreateDatabase(absolutePath, pwd) != 0)
            throw new IOException("Could not create file " + absolutePath);
    }

    /**
     * Closes the certificate database
     */
    public void dispose() {
        synchronized (lock) {
            if (certDb != null) {
                try {
                    certDb.close();
                } catch (Exception e) { }
            }
        }
    }

    @Override
    public byte[] getCertificate(String type, String server, int port) {
        synchronized (lock) {
            return getCertificateImpl(type, server, port);
        }
    }

    @Override
    public void saveCertificate(String type, String server, int port, byte[] certificate) {

        synchronized (lock) {
            String hash = HashingUtils.sha256sum(certificate);
            saveCertificate(type, server, port, certificate, hash);
        }
    }

    @Override
    public boolean deleteCertificate(String type, String server, int port) {
        synchronized (lock) {
            return deleteCertificateImpl(type, server, port);
        }
    }

    @Override
    public String[] getServers(String type) {
        if(this.certDb == null)
            return null;

        List<String> returnList = new ArrayList<>();
        // query is always against `server_certificates` as only that table contains server address
        String sql = "SELECT server FROM server_certificates WHERE type = ? AND" +
                " (server IS NOT NULL AND length(server) != 0);";
        QueryIface sqlQuery = null;
        try {
            sqlQuery = certDb.compileQuery(sql);

            sqlQuery.bind(1, type);
            try {
                while (sqlQuery.moveToNext()) {
                    returnList.add(sqlQuery.getString(0));
                }
            } catch (Exception e) {
                Log.e(TAG, "getServers: failed to query.", e);
                return new String[0];
            }
            return returnList.toArray(new String[0]);
        } finally {
            if (sqlQuery != null)
                sqlQuery.close();
        }
    }

    @Override
    public URI[] getServerURIs(String type) {
        if(this.certDb == null)
            return null;

        List<URI> returnList = new ArrayList<>();
        // query is always against `server_certificates` as only that table contains server address
        String sql = "SELECT server, port FROM server_certificates WHERE " +
                " port IS NOT NULL and type = ? AND" +
                " (server IS NOT NULL AND length(server) != 0);";
        QueryIface sqlQuery = null;
        try {
            sqlQuery = certDb.compileQuery(sql);

            sqlQuery.bind(1, type);
            try {
                while (sqlQuery.moveToNext()) {
                    returnList.add(new URI("ssl://" +
                            sqlQuery.getString(0) + ":" +
                                    sqlQuery.getString(1)));
                }
            } catch (Exception e) {
                Log.e(TAG, "getServerConnectStrings: failed to query.", e);
                return new URI[0];
            }
            return returnList.toArray(new URI[0]);
        } finally {
            if (sqlQuery != null)
                sqlQuery.close();
        }
    }

    /**
     * Gets the certificate of the given type and server and port
     *
     * @param type   the certificate type.
     * @param server the server.
     * @param port the server.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private byte[] getCertificateImpl(String type, String server, int port) {
        if(this.certDb == null)
            return null;

        String sql = (server != null) ?
                "SELECT certificate FROM server_certificates WHERE type = ? AND server = ?" :
                "SELECT certificate FROM certificates WHERE type = ?";
        if(server != null && port != -1)
            sql += "and port = ?";
        QueryIface query = null;
        try {
            query = this.certDb.compileQuery(sql);
            query.bind(1, type);
            if(server != null)
            {
                query.bind(2, server);
                if (port != -1)
                    query.bind(3, port);
            }
            return getCertificateHelperImpl(query, "getCertificateForServerLocal");
        } finally {
            if(query != null)
                query.close();
        }
    }


    /**
     * Gets the certificate of the given type and server.
     *
     * @param type   the certificate type.
     * @param server the server.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    @Override
    public String getCertificateHash(String type, String server, int port) {
        if(this.certDb == null)
            return null;
        String sql = (server != null) ?
                "SELECT hash FROM server_certificates WHERE type = ? AND server = ? " :
                "SELECT hash FROM certificates WHERE type = ?";
        if(server != null && port >= 0)
            sql += "AND port = ?;";
        QueryIface query = null;
        try {
            query = this.certDb.compileQuery(sql);
            query.bind(1, type);
            if(server != null)
            {
                query.bind(2, server);
                if (port >= 0)
                    query.bind(3, port);
            }
            return getCertificateHashHelperImpl(query, "getCertificateHashForServerAndPort");
        } finally {
            if(query != null)
                query.close();
        }
    }

    private boolean saveCertificate(String type, String server, int port, byte[] cert,
                                          String hash) {
        if (this.certDb == null)
            return false;

        // XXX - !!!retained from legacy!!!
        // these check looks problematic to me as the certificate may be considered not to exist in
        // a couple of situations even if there is a row present in the database.
        byte[] existing = null;
        if(ICertificateStore.validateCertificate(this, type, server, port))
            existing = getCertificate(type, server, port);

        StatementIface stmt = null;
        try {
            String sql;
            final boolean bindPort;
            if (existing == null || existing.length == 0)
            {
                sql = (server != null) ?
                        "insert into server_certificates ( certificate, hash, type, server, port ) values ( ?, ?, ?, ?, ? )" :
                        "insert into certificates ( certificate, hash, type ) values ( ?, ?, ? )";
                bindPort = (server != null);
            } else {
                sql = (server != null) ?
                        "update server_certificates set certificate = ?, hash = ? where type = ? and server = ?" :
                        "update certificates set certificate = ?, hash = ? where type = ?";
                if(server != null && port >= 0)
                    sql += " and port = ?";
                else if(server != null)
                    sql += " and port is null";
                bindPort = (server != null && port >= 0);

            }
            stmt = this.certDb.compileStatement(sql);

            stmt.bind(1, cert);
            stmt.bind(2, hash);
            stmt.bind(3, type);
            if(server != null)
            {
                stmt.bind(4, server);
                if (bindPort && port >= 0)
                    stmt.bind(5, port);
                else if(bindPort)
                    stmt.bindNull(5);
            }
            try {
                stmt.execute();
                return true;
            } catch(Throwable t) {
                Log.w(TAG, "saveCertificate: failed to execute SQL", t);
                return false;
            }
        } finally {
            if(stmt != null)
                stmt.close();
        }
    }

    /**
     * Deletes a certificate for server of the given type.
     *
     * @param type   the type of certificate to delete.
     * @param server the server for the certificate
     * @return True if successful, false otherwise.
     */
    private boolean deleteCertificateImpl(String type, String server, int port) {
        if(this.certDb == null)
            return false;
        String sql = (server != null) ?
                "DELETE FROM server_certificates WHERE type = ? AND server = ?" :
                "DELETE FROM certificates WHERE type = ?";
        if(server != null && port >= 0)
            sql += " AND port = ?";
        StatementIface sqlStatement = null;
        try {
            sqlStatement = certDb.compileStatement(sql);

            sqlStatement.bind(1, type);
            if(server != null)
            {
                sqlStatement.bind(2, server);
                if (port >= 0)
                    sqlStatement.bind(3, port);
            }

            try {
                sqlStatement.execute();
            } catch (Exception e) {
                Log.e(TAG, "deleteCertificate: failed to execute SQL", e);
                return false;
            }
            return true;
        } finally {
            if(sqlStatement != null)
                sqlStatement.close();
        }
    }

    /**
     * Helper method for returning a single <code>byte[]</code> representing a certificate.
     *
     * @param sqlQuery   The SQL query.
     * @param methodName the name of the calling method.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private byte[] getCertificateHelperImpl(QueryIface sqlQuery, String methodName) {
        List<byte[]> result = new ArrayList<>();
        //Iterate through cursor and add all rows returned
        try {
            while (sqlQuery.moveToNext()) {
                result.add(sqlQuery.getBlob(0));
            }
        } catch (Exception e) {
            Log.e(TAG, methodName + ": failed to query.", e);
        }

        if (result.isEmpty()) {
            // Return null, and log error if no rows are returned.
            return null;
        } else if (result.size() > 1) {
            //This should only return 1 row, log a warning if more than 1 is returned.
            Log.d(TAG, methodName + ": more than 1 row returned! Returning first row.");
        }

        return result.get(0);
    }

    /**
     * Helper method for returning a single <code>String</code> representing a certificate's hash.
     *
     * @param sqlQuery   The SQL statement.
     * @param methodName the name of the calling method.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private String getCertificateHashHelperImpl(QueryIface sqlQuery, String methodName) {
        List<String> result = new ArrayList<>();
        //Iterate through cursor and add all rows returned
        try {
            while (sqlQuery.moveToNext()) {
                result.add(sqlQuery.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, methodName + ": failed to query.\n" + e.toString());
        }

        if (result.isEmpty()) {
            // Return null, and log error if no rows are returned.
            return null;
        } else if (result.size() > 1) {
            //This should only return 1 row, log a warning if more than 1 is returned.
            Log.d(TAG, methodName + ": more than 1 row returned! Returning first row.");
        }

        return result.get(0);
    }

    int openOrCreateDatabase(String absolutePath, String pwd) {
        try {
            if (pwd != null)
                this.certDb = DatabaseImpl.open(absolutePath, pwd, 0x02000000 | DatabaseImpl.OPEN_CREATE);
            else if(absolutePath != null)
                this.certDb = IOProviderFactory.createDatabase(new File(absolutePath));
            else
                this.certDb = IOProviderFactory.createDatabase((File)null);
            if (certDb != null) {
                this.certDb.execute("create table if not exists certificates(type TEXT, certificate BLOB, hash TEXT);", null);
                this.certDb.execute("create table if not exists server_certificates (type TEXT, server TEXT, certificate BLOB, hash TEXT);", null);
                try {
                    this.certDb.execute("alter table server_certificates add column port INTEGER;", null);
                } catch (Throwable t) { }
            }
            return (this.certDb != null) ? 0 : -1;
        } catch(Throwable t) {
            return -1;
        }
    }
}
