
package com.atakmap.android.maps.tilesets;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.map.layer.raster.DatasetDescriptor;

public abstract class EquirectangularTilesetSupport extends TilesetSupport {

    protected final int levelOffset;
    protected final double zeroWidth;
    protected final double zeroHeight;
    protected final double gridOriginLat;
    protected final double gridOriginLng;
    protected final int tilePixelHeight;
    protected final int tilePixelWidth;

    protected EquirectangularTilesetSupport(TilesetInfo tsInfo, GLBitmapLoader bitmapLoader) {
        super(bitmapLoader);

        this.levelOffset = Integer.parseInt(DatasetDescriptor.getExtraData(tsInfo.getInfo(), "levelOffset",
                "0"));
        this.zeroWidth = tsInfo.getZeroWidth();
        this.zeroHeight = tsInfo.getZeroHeight();
        this.gridOriginLat = tsInfo.getGridOriginLat();
        this.gridOriginLng = tsInfo.getGridOriginLng();
        this.tilePixelHeight = tsInfo.getTilePixelHeight();
        this.tilePixelWidth = tsInfo.getTilePixelWidth();
    }

    @Override
    public double[] getTileBounds(int latIndex,
            int lngIndex, int level, double[] swne) {

        final double tileWidth = this.zeroWidth / (1 << level);
        final double tileHeight = this.zeroHeight / (1 << level);

        final double south = (latIndex * tileHeight) + this.gridOriginLat;
        final double west = (lngIndex * tileWidth) + this.gridOriginLng;

        if (swne == null)
            swne = new double[4];
        swne[0] = south;
        swne[1] = west;
        swne[2] = south + tileHeight;
        swne[3] = west + tileWidth;
        return swne;
    }

    @Override
    public int getTileZeroX(double lng, int gridX, int gridWidth) {
        int westIndex = ((int) ((lng - this.gridOriginLng) / this.zeroWidth)) - gridX;
        return Math.max(0, Math.min(westIndex, gridWidth));
    }

    @Override
    public int getTileZeroY(double lat, int gridY, int gridHeight) {
        int southIndex = ((int) ((lat - this.gridOriginLat) / this.zeroHeight)) - gridY;
        return Math.max(0, Math.min(southIndex, gridHeight));
    }

    @Override
    public double getTilePixelX(int latIndex, int lngIndex,
            int level, double lng) {
        final double tileWidthDegLng = this.zeroWidth / (1 << level);
        final int tileWidthPixels = this.tilePixelWidth;
        final double tileGridOriginLngWest = this.gridOriginLng;

        final double degPerPixelLng = tileWidthDegLng / tileWidthPixels;
        final double tileOffsetWest = tileGridOriginLngWest + (tileWidthDegLng * lngIndex);

        return (lng - tileOffsetWest) / degPerPixelLng;
    }

    @Override
    public double getTilePixelY(int latIndex, int lngIndex,
            int level, double lat) {

        final double tileHeightDegLat = this.zeroHeight / (1 << level);
        final int tileHeightPixels = this.tilePixelHeight;
        final double tileGridOriginLatSouth = this.gridOriginLat;

        final double degPerPixelLat = tileHeightDegLat / tileHeightPixels;
        final double tileOffsetSouth = tileGridOriginLatSouth + (tileHeightDegLat * latIndex);

        return tileHeightPixels - ((lat - tileOffsetSouth) / degPerPixelLat);
    }

    @Override
    public double getTilePixelLat(int latIndex,
            int lngIndex, int level, int y) {

        final double tileHeightDegLat = this.zeroHeight / (1 << level);
        final int tileHeightPixels = this.tilePixelHeight;
        final double tileGridOriginLatSouth = this.gridOriginLat;

        final double degPerPixelLat = tileHeightDegLat / tileHeightPixels;
        final double tileOffsetSouth = tileGridOriginLatSouth + (tileHeightDegLat * latIndex);

        return tileOffsetSouth + (tileHeightPixels - y) * degPerPixelLat;
    }

    @Override
    public double getTilePixelLng(int latIndex,
            int lngIndex, int level, int x) {

        final double tileWidthDegLng = this.zeroWidth / (1 << level);
        final int tileWidthPixels = this.tilePixelWidth;
        final double tileGridOriginLngWest = this.gridOriginLng;

        final double degPerPixelLng = tileWidthDegLng / tileWidthPixels;
        final double tileOffsetWest = tileGridOriginLngWest + (tileWidthDegLng * lngIndex);

        return tileOffsetWest + x * degPerPixelLng;
    }
}
