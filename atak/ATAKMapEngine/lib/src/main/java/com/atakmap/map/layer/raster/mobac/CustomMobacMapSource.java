
package com.atakmap.map.layer.raster.mobac;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

import com.atakmap.net.AsynchronousInetAddressResolver;

import android.graphics.BitmapFactory.Options;

public class CustomMobacMapSource extends AbstractMobacMapSource {

    private AsynchronousInetAddressResolver dnsCheck;
    protected String url;
    private final String[] serverParts;
    private final boolean invertYCoordinate;
    private int serverPartIdx;
    private AtomicBoolean checkConnectivity;
    private boolean disconnected;
    private AtomicBoolean authFailed;

    private boolean firstDnsLookup;

    public CustomMobacMapSource(String name, int srid, int tileSize, int minZoom, int maxZoom, String type,
                                String url, String[] serverParts, int backgroundColor, boolean invertYCoordinate) {
        this(name, srid, tileSize, minZoom, maxZoom, type, url, serverParts, backgroundColor, invertYCoordinate, 0L);
    }
    public CustomMobacMapSource(String name, int srid, int tileSize, int minZoom, int maxZoom, String type,
            String url, String[] serverParts, int backgroundColor, boolean invertYCoordinate, long refreshInterval) {
        super(name, srid, tileSize, minZoom, maxZoom, type, refreshInterval);

        this.url = url;
        this.serverParts = serverParts;
        this.invertYCoordinate = invertYCoordinate;
        this.serverPartIdx = 0;
        this.checkConnectivity = new AtomicBoolean(true);
        this.disconnected = false;

        this.firstDnsLookup = true;
        this.authFailed = new AtomicBoolean(false);
    }

    protected String getUrl(int zoom, int x, int y) {
        String retval = this.url;
        if (this.serverParts != null) {
            synchronized (this) {
                retval = retval.replace("{$serverpart}", this.serverParts[this.serverPartIdx]);
                this.serverPartIdx = (this.serverPartIdx + 1) % this.serverParts.length;
            }
        }
        retval = retval.replace("{$x}", String.valueOf(x));
        retval = retval.replace("{$y}", String.valueOf(y));
        retval = retval.replace("{$z}", String.valueOf(zoom));
        retval = retval.replace("{$q}", getQuadKey(zoom, x, y));

        return retval;
    }

    private static String getQuadKey(int zoom, int x, int y) {
        StringBuilder retval = new StringBuilder();
        char digit;
        int mask;
        for (int i = zoom; i > 0; i--) {
            digit = '0';
            mask = 1 << (i - 1);
            if ((x & mask) != 0)
                digit++;
            if ((y & mask) != 0)
                digit += 2;
            retval.append(digit);
        }
        return retval.toString();
    }

    protected void configureConnection(URLConnection conn) {

        conn.setRequestProperty("User-Agent", "TAK");
        conn.setRequestProperty("x-common-site-name", getName());
        conn.setUseCaches(true);
        if(this.config != null) { 
            conn.setConnectTimeout(this.config.connectTimeout);
            conn.setReadTimeout(this.config.connectTimeout);
        } else { 
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
        }
    }

    @Override
    public void clearAuthFailed() {
        this.authFailed.set(false);
    }

    @Override
    public void checkConnectivity() {
        this.checkConnectivity.set(!this.authFailed.get());
    }

    @Override
    public final MobacMapTile loadTile(int zoom, int x, int y, Options opts) throws IOException {
        if (this.invertYCoordinate)
            y = ((1 << zoom) - 1) - y;

        final URL url = new URL(this.getUrl(zoom, x, y));
        //System.out.println("shb: " + this.getUrl(zoom,x,y));

        synchronized (this) {
            if (this.authFailed.get()) {
                throw new IOException("Not authorized");
            } else if (this.checkConnectivity.getAndSet(false)) {
                this.disconnected = true;
                final InetAddress resolved;
                if (this.dnsCheck == null)
                    this.dnsCheck = new AsynchronousInetAddressResolver(url.getHost());
                try {
                    long dnsLookupTimeout = config.dnsLookupTimeout;
                    if (this.firstDnsLookup)
                        dnsLookupTimeout = Math.max(config.dnsLookupTimeout, 10000L);
                    resolved = this.dnsCheck.get(dnsLookupTimeout);
                    this.disconnected = (resolved == null);
                } catch (IOException e) {
                    this.dnsCheck = null;
                    throw e;
                } finally {
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
            MobacMapTile tile = load(conn, opts);
            return tile;
        } catch (IOException e) {
            if ((conn instanceof HttpURLConnection)
                    && isBadAccess(((HttpURLConnection) conn).getResponseCode() ))
                this.authFailed.set(true);
            throw e;
        }
    }
    private static boolean isBadAccess(int status) {
        return (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN);
    }
}
