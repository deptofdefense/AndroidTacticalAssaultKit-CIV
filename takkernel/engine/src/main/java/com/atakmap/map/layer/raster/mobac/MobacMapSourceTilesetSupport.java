
package com.atakmap.map.layer.raster.mobac;

import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.android.maps.tilesets.OnlineTilesetSupport;
import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.android.maps.tilesets.TilesetSupport;
import com.atakmap.database.StatementIface;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

public final class MobacMapSourceTilesetSupport implements OnlineTilesetSupport {

    protected final MobacMapSource mapSource;
    protected final TilesetInfo tsInfo;
    protected boolean checkConnectivity;
    protected MobacTileClient client;

    private boolean offlineMode;
    private int tilesVersion = 0;

    public static final String TAG = "MobacMapSourceTilesetSupport";

    MobacMapSourceTilesetSupport(TilesetInfo tsInfo, MobacMapSource mapSource) {
        this.tsInfo = tsInfo;
        this.mapSource = mapSource;

        this.checkConnectivity = true;

        this.client = null;
        
        this.offlineMode = false;
    }

    /**************************************************************************/
    // Online Tileset Support

    @Override
    public final synchronized void setOfflineMode(boolean offlineOnly) {
        if(this.client != null)
            this.client.setOfflineMode(offlineOnly);
        if(offlineOnly != this.offlineMode)
            this.tilesVersion++;
        this.offlineMode = offlineOnly;
    }

    @Override
    public final synchronized boolean isOfflineMode() {
        return this.offlineMode;
    }

    /**************************************************************************/
    // Tileset Support

    public final void init() {

        String offlineCache = this.tsInfo.getInfo().getExtraData("offlineCache");

        if (offlineCache != null) {
            DatabaseIface database = null;
            try {
                database = IOProviderFactory.createDatabase(new File(offlineCache));
                Set<String> tables = Databases.getTableNames(database);
                if (!tables.contains("tiles")) {
                    database.execute("CREATE TABLE tiles (key INTEGER PRIMARY KEY, provider TEXT, tile BLOB)", null);
                }
                if (!tables.contains("ATAK_catalog")) {
                    database.execute("CREATE TABLE ATAK_catalog (key INTEGER PRIMARY KEY, access INTEGER, expiration INTEGER, size INTEGER)", null);
                }
                if (!tables.contains("ATAK_metadata")) {
                    database.execute("CREATE TABLE ATAK_metadata (key TEXT, value TEXT)", null);
                    StatementIface stmt = null;
                    try {
                        stmt = database.compileStatement("INSERT INTO ATAK_metadata (key, value) VALUES(?, ?)");
                        stmt.bind(1, "srid");
                        stmt.bind(2, String.valueOf(this.tsInfo.getInfo().getSpatialReferenceID()));
                        stmt.execute();
                    } finally {
                        if(stmt != null)
                            stmt.close();
                    }
                }
            } catch (Exception e) {
                // received a could not open database exception and a hard crash, try to recover.
                Log.d(TAG, "exception occurred opening/using: " + offlineCache, e);
                try { 
                    FileSystemUtils.delete(offlineCache);
                    // todo, cycle back to the initialization code.   
                } catch (Exception ioe) { 
                    Log.d(TAG, "error occurred deleting cache: " + offlineCache, ioe);
                } 
                offlineCache = null;
            } finally {
                if (database != null)
                    database.close();
            }
        }

        this.mapSource.clearAuthFailed();
        synchronized(this) {
            this.client = new MobacTileClient(this.mapSource, offlineCache);
            this.client.setOfflineMode(this.offlineMode);
        }
    }

    public final synchronized void release() {
        if (this.client != null)
            this.client.close();
    }

    public final void start() {
        this.checkConnectivity = true;
    }

    public final void stop() {
    }

    public final FutureTask<Bitmap> getTile(int latIndex, int lngIndex,
            int level, BitmapFactory.Options opts) {

        FutureTask<Bitmap> r = new FutureTask<Bitmap>(new CachingBitmapLoader(latIndex,
                lngIndex, level, this.checkConnectivity, opts));
        this.checkConnectivity = false;
        return r;
    }

    /**************************************************************************/

    public static class Spi implements TilesetSupport.Spi {
        public final static TilesetSupport.Spi INSTANCE = new Spi();

        @Override
        public String getName() {
            return "mobac";
        }

        @Override
        public TilesetSupport create(TilesetInfo info, GLBitmapLoader loader) {
            MobacMapSource mapSource = null;
            try {
                mapSource = MobacMapSourceFactory.create(new File(info.getInfo().getUri()));
            } catch (Exception e) {
                Log.d(TAG, "exception occurred: ", e);
            }

            if (mapSource == null)
                return null;

            MobacMapSourceTilesetSupport impl = new MobacMapSourceTilesetSupport(info, mapSource);
            if (info.getInfo().getSpatialReferenceID() == EquirectangularMapProjection.INSTANCE
                    .getSpatialReferenceID())
                return new EquirectangularMobacMapSourceTilesetSupport(info, loader, impl);
            else if (info.getInfo().getSpatialReferenceID() == WebMercatorProjection.INSTANCE
                    .getSpatialReferenceID())
                return new WebMercatorMobacMapSourceTilesetSupport(info, loader, impl);
            else
                return null;
        }
    }

    public int getTilesVersion(int latIndex, int lngIndex, int level) {
        return this.tilesVersion;
    }
    
    /**************************************************************************/

    private class CachingBitmapLoader implements Callable<Bitmap> {
        private final int latIndex;
        private final int lngIndex;
        private final int level;
        private final boolean checkConnectivity;
        private final BitmapFactory.Options opts;

        public CachingBitmapLoader(int latIndex, int lngIndex, int level,
                boolean checkConnectivity, BitmapFactory.Options opts) {
            this.latIndex = latIndex;
            this.lngIndex = lngIndex;
            this.level = level;
            this.checkConnectivity = checkConnectivity;
            this.opts = opts;
        }

        @Override
        public Bitmap call() throws Exception {
            if (this.checkConnectivity)
                MobacMapSourceTilesetSupport.this.mapSource.checkConnectivity();

            return MobacMapSourceTilesetSupport.this.client.loadTile(
                    this.level, this.lngIndex, this.latIndex, this.opts, null);
        }
    }
}
