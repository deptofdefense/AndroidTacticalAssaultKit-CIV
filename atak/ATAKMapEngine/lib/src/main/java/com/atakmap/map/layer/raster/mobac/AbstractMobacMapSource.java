
package com.atakmap.map.layer.raster.mobac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;

public abstract class AbstractMobacMapSource implements MobacMapSource2 {

    protected final String name;
    protected final int srid;
    protected final int tileSize;
    protected final int minZoom;
    protected final int maxZoom;
    protected final String tileType;
    protected final long refreshInterval;
    protected Config config = new Config();

    protected AbstractMobacMapSource(String name, int srid, int tileSize, int minZoom, int maxZoom,
            String tileType) {
        this(name, srid, tileSize, minZoom, maxZoom, tileType, 0L);
    }

    protected AbstractMobacMapSource(String name, int srid, int tileSize, int minZoom, int maxZoom,
        String tileType, long refreshInterval) {
        this.srid = srid;
        this.name = name;
        this.tileSize = tileSize;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.tileType = tileType;
        this.refreshInterval = refreshInterval;
    }

    @Override
    public final int getSRID() {
        // XXX - move the web mercator ID translation upstream???
        if (this.srid == 900913)
            return WebMercatorProjection.INSTANCE.getSpatialReferenceID();
        return this.srid;
    }

    @Override
    public final String getName() {
        return this.name;
    }

    @Override
    public final int getTileSize() {
        return this.tileSize;
    }

    @Override
    public final int getMinZoom() {
        return this.minZoom;
    }

    @Override
    public final int getMaxZoom() {
        return this.maxZoom;
    }

    @Override
    public final String getTileType() {
        return this.tileType;
    }

    @Override
    public GeoBounds getBounds() {
        return null;
    }

    private static final int BUFFER_SIZE = 32 * 1024;

    protected static MobacMapTile load(URLConnection conn, BitmapFactory.Options opts)
            throws IOException {
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

            // XXX - was checking to see if data.length matched downloaded
            //       length but was observing a lot of Bitmap decode errors...
            final byte[] data = bytesFromWire.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);

            return new MobacMapTile(bitmap, data, conn.getExpiration());
        } finally {
            if (connection != null) { 
                // symetric to match the above
                connection.conn.disconnect();
                try {
                    connection.stream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public void setConfig(Config c) {
        config = c;
    }

    @Override
    public long getRefreshInterval() {
        return refreshInterval;
    }

    @Override
    public boolean invalidateCacheOnInit() {
        return false;
    }
}
