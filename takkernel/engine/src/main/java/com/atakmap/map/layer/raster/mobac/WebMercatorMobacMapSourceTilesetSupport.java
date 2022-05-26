
package com.atakmap.map.layer.raster.mobac;

import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.map.layer.raster.osm.OSMTilesetSupport;
import com.atakmap.android.maps.tilesets.OnlineTilesetSupport;
import com.atakmap.android.maps.tilesets.TilesetInfo;

class WebMercatorMobacMapSourceTilesetSupport extends OSMTilesetSupport implements
        OnlineTilesetSupport {

    private final MobacMapSourceTilesetSupport impl;

    WebMercatorMobacMapSourceTilesetSupport(TilesetInfo tsInfo, GLBitmapLoader bitmapLoader,
            MobacMapSourceTilesetSupport impl) {
        super(tsInfo, bitmapLoader);

        this.impl = impl;
    }

    /**************************************************************************/
    // Online Tileset Support

    @Override
    public void setOfflineMode(boolean offlineOnly) {
        this.impl.setOfflineMode(offlineOnly);
    }

    @Override
    public boolean isOfflineMode() {
        return this.impl.isOfflineMode();
    }

    /**************************************************************************/
    // Tileset Support

    @Override
    public void init() {
        this.impl.init();
    }

    @Override
    public void release() {
        this.impl.release();
    }

    @Override
    public void start() {
        this.impl.start();
    }

    @Override
    public void stop() {
        this.impl.stop();
    }

    @Override
    public FutureTask<Bitmap> getTile(int latIndex, int lngIndex,
            int level, Options opts) {
        level += this.levelOffset;
        latIndex = (1 << level) - latIndex - 1;

        final FutureTask<Bitmap> retval = this.impl.getTile(latIndex, lngIndex, level, opts);
        this.bitmapLoader.loadBitmap(retval,GLBitmapLoader.QueueType.REMOTE);
        return retval;
    }
    
    @Override
    public int getTilesVersion(int latIndex, int lngIndex, int level) {
        return this.impl.getTilesVersion(latIndex, lngIndex, level);
    }
}
