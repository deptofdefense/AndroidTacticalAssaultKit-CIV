
package com.atakmap.map.layer.raster.osm;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.android.maps.tilesets.TilesetSupport;

public abstract class OSMTilesetSupport extends TilesetSupport {

    protected final int levelOffset;

    public OSMTilesetSupport(TilesetInfo tsInfo, GLBitmapLoader loader) {
        super(loader);

        this.levelOffset = Integer.parseInt(DatasetDescriptor.getExtraData(tsInfo.getInfo(), "levelOffset",
                "0"));
    }

    /**************************************************************************/

    @Override
    public double[] getTileBounds(int latIndex,
            int lngIndex, int level, double[] swne) {
        level += this.levelOffset;
        latIndex = (1 << level) - latIndex - 1;
        if (swne == null)
            swne = new double[4];
        swne[0] = OSMUtils.mapnikTileLat(level, latIndex + 1);
        swne[1] = OSMUtils.mapnikTileLng(level, lngIndex);
        swne[2] = OSMUtils.mapnikTileLat(level, latIndex);
        swne[3] = OSMUtils.mapnikTileLng(level, lngIndex + 1);

        return swne;
    }

    @Override
    public int getTileZeroX(double lng, int gridX,
            int gridWidth) {
        return OSMUtils.mapnikTileX(this.levelOffset, lng) - gridX;
    }

    @Override
    public int getTileZeroY(double lat, int gridY,
            int gridHeight) {
        if (lat > 85.0511)
            return gridHeight;
        else if (lat <= -85.0511)
            return gridY - 1;
        final int level = this.levelOffset;
        final int osmLatIndex = OSMUtils.mapnikTileY(level, lat);

        return ((1 << level) - osmLatIndex - 1) - gridY;
    }

    @Override
    public double getTilePixelX(int latIndex, int lngIndex,
            int level, double lng) {
        final int osmLevel = this.levelOffset + level;

        // call returns int
        return OSMUtils.mapnikPixelX(osmLevel, lngIndex, lng);
    }

    @Override
    public double getTilePixelY(int latIndex, int lngIndex,
            int level, double lat) {
        final int osmLevel = this.levelOffset + level;
        final int osmLatIndex = (1 << osmLevel) - latIndex - 1;

        // call returns int
        return OSMUtils.mapnikPixelY(osmLevel, osmLatIndex, lat);
    }

    @Override
    public double getTilePixelLat(int latIndex,
            int lngIndex, int level, int y) {
        final int osmLevel = this.levelOffset + level;
        final int osmLatIndex = (1 << osmLevel) - latIndex - 1;

        return OSMUtils.mapnikPixelLat(osmLevel, osmLatIndex, y);
    }

    @Override
    public double getTilePixelLng(int latIndex,
            int lngIndex, int level, int x) {
        final int osmLevel = this.levelOffset + level;
        return OSMUtils.mapnikPixelLng(osmLevel, lngIndex, x);
    }
}
