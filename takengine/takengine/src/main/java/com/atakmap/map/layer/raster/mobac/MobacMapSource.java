
package com.atakmap.map.layer.raster.mobac;

import com.atakmap.coremap.maps.coords.GeoBounds;

import java.io.IOException;

import android.graphics.BitmapFactory;

public interface MobacMapSource {
    public String getName();

    public int getMinZoom();

    public int getMaxZoom();

    public String getTileType();

    public int getTileSize();

    public MobacMapTile loadTile(int zoom, int x, int y, BitmapFactory.Options opts)
            throws IOException;

    public void checkConnectivity();

    public void setConfig(MobacMapSource.Config c);

    public void clearAuthFailed();
    
    public int getSRID();

    /**
     * Return the geographic coverage of this layer. If the layer covers the entire world (or the
     * bounds are unknown), null is returned.
     */
    public GeoBounds getBounds();

    /**
     * use this class to specify parameters for the MobacMapSource that is to be created
     */
    public static class Config {
        public final static Config defaults = new Config(false);

        public long dnsLookupTimeout;
        public int connectTimeout;
        
        public Config() {
            this.dnsLookupTimeout = defaults.dnsLookupTimeout;
            this.connectTimeout = defaults.connectTimeout;
        }
        
        private Config(boolean ignored) {
            this.dnsLookupTimeout = 1L;
            this.connectTimeout = 3000;
        }
    }
}
