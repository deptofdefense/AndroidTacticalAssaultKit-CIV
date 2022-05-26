package com.atakmap.map.layer.raster.tilematrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

import com.atakmap.net.AsynchronousInetAddressResolver;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;

public abstract class AbstractURLTileClient extends AbstractTileClient {

    private static final int BUFFER_SIZE = 32 * 1024;
    
    protected AsynchronousInetAddressResolver dnsCheck;
    protected boolean checkConnectivity;
    protected boolean disconnected;
    protected boolean authFailed;

    protected boolean firstDnsLookup;
    protected TileClientSpi.Options opts;
    
    protected AbstractURLTileClient(String name, long expirationOffset, TileClientSpi.Options opts) {
        super(name, expirationOffset);

        this.checkConnectivity = true;
        this.disconnected = false;

        this.firstDnsLookup = true;
        this.authFailed = false;
        
        if(opts == null)
            opts = new TileClientSpi.Options();
        this.opts = opts;
    }


    @Override
    protected byte[] getTileDataImpl(int zoom, int x, int y) throws IOException {
        final URL url = this.getTileURL(zoom, x, y);

        synchronized (this) {
            if (this.authFailed) {
                throw new IOException("Not authorized");
            } else if (this.checkConnectivity) {
                this.disconnected = true;
                final InetAddress resolved;
                if (this.dnsCheck == null)
                    this.dnsCheck = new AsynchronousInetAddressResolver(url.getHost());
                try {
                    long dnsLookupTimeout = this.opts.dnsLookupTimeout;
                    if (this.firstDnsLookup)
                        dnsLookupTimeout = Math.max(this.opts.dnsLookupTimeout, 10000L);
                    resolved = this.dnsCheck.get(dnsLookupTimeout);
                    this.disconnected = (resolved == null);
                } catch (IOException e) {
                    this.dnsCheck = null;
                    throw e;
                } finally {
                    this.checkConnectivity = false;
                    this.firstDnsLookup = false;
                }
                if (resolved == null)
                    throw new SocketTimeoutException("Timeout occurred performing DNS lookup.");
            } else if (this.disconnected) {
                throw new NoRouteToHostException();
            }
        }

        URLConnection conn = url.openConnection();
        this.configureConnection(conn);

        try {
            return load(conn);
        } catch (IOException e) {
            if ((conn instanceof HttpURLConnection)
                    && isBadAccess(((HttpURLConnection) conn).getResponseCode()))
                synchronized (this) { 
                    this.authFailed = true;
                }
            throw e;
        }
    }

    private static boolean isBadAccess(int status) {
        return (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN);
    }

    protected void configureConnection(URLConnection conn) {

        conn.setRequestProperty("User-Agent", "TAK");
        conn.setUseCaches(true);

        conn.setConnectTimeout((int)this.opts.connectTimeout);
        conn.setReadTimeout((int)this.opts.connectTimeout);
    }

    @Override
    public synchronized void clearAuthFailed() {
        this.authFailed = false;
    }

    @Override
    public synchronized void checkConnectivity() {
        this.checkConnectivity = !this.authFailed;
    }

    
    protected abstract URL getTileURL(int zoom, int x, int y);

    /**************************************************************************/
    
    public static byte[] load(URLConnection conn) throws IOException {
        AtakAuthenticationHandlerHTTP.Connection connection = null;
        try {
            connection = AtakAuthenticationHandlerHTTP.makeAuthenticatedConnection(
                    (java.net.HttpURLConnection) conn, 5);
            // XXX: Can't rely on content-length HTTP header being set.
            final int contentLength = conn.getContentLength();
            byte[] buffer = new byte[Math.max(contentLength, BUFFER_SIZE)];

            ByteArrayOutputStream bytesFromWire = new ByteArrayOutputStream(buffer.length);
            int bytesReadThisTime = -1;
            do {
                bytesReadThisTime = connection.stream.read(buffer);
                if (bytesReadThisTime > 0) {
                    bytesFromWire.write(buffer, 0, bytesReadThisTime);
                }
            } while (bytesReadThisTime >= 0);

            connection.conn.disconnect();
            try {
                connection.stream.close();
            } catch (IOException ignored) {
            }
            connection = null;

            return bytesFromWire.toByteArray();
        } finally {
            if (connection != null)
                try {
                    connection.stream.close();
                } catch (IOException ignored) {}
        }
    }

}
