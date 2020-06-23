/*
 * TileSource.java
 *
 * Created on June 9, 2013, 10:07 AM
 */

package com.atakmap.map.layer.raster.tilereader;

/**
 * @author Developer
 */
public interface TileSource {

    /**
     * Returns the width of the image at its native resolution.
     * 
     * @return The width of the image at its native resolution.
     */
    public int getWidth();

    /**
     * Returns the width of the image at the specified level.
     * 
     * @param level The level
     * @return The width of the image at the specified level.
     */
    public int getWidth(int level);

    /**
     * Returns the height of the image at its native resolution.
     * 
     * @return The height of the image at its native resolution.
     */
    public int getHeight();

    /**
     * Returns the height of the image at the specified level.
     * 
     * @return The height of the image at the specified level.
     */
    public int getHeight(int level);

    /**
     * Returns the nominal width of a tile.
     * 
     * @return The nominal width of a tile.
     */
    public int getTileWidth();

    /**
     * Returns the width of the tile in the specified column at the specified level.
     * 
     * @return The width of the tile in the specified column at the specified level.
     */
    public int getTileWidth(int level, int tileColumn);

    /**
     * Returns the nominal height of a tile.
     * 
     * @return The nominal height of a tile.
     */
    public int getTileHeight();

    /**
     * Returns the height of the tile in the specified row at the specified level.
     * 
     * @return The height of the tile in the specified row at the specified level.
     */
    public int getTileHeight(int level, int tileRow);

    /**
     * Reads the specified tile at the specified level and stores the data in the specified array.
     * The returned data will have dimensions consistent with {@link #getTileWidth(int, int)} and
     * {@link #getTileHeight(int, int)} for the specified level, tile column and tile row.
     * 
     * @param level
     * @param tileColumn
     * @param tileRow
     * @param data
     * @return <code>true</code> if the read completed successfuly, <code>false</code> if the
     *         operation was canceled asynchronously
     */
    public boolean read(int level, int tileColumn, int tileRow, byte[] data);

    /**
     * Reads an arbitrary region of the image at an arbitrary scale.
     * 
     * @param srcX
     * @param srcY
     * @param srcW
     * @param srcH
     * @param dstW
     * @param dstH
     * @param buf
     * @return <code>true</code> if the read completed successfuly, <code>false</code> if the
     *         operation was canceled asynchronously
     */
    public boolean read(int srcX, int srcY, int srcW, int srcH, int dstW,
            int dstH, byte[] buf);

    public int getNumTilesX();

    public int getNumTilesX(int level);

    public int getNumTilesY();

    public int getNumTilesY(int level);

    public int getTileSourceX(int level, int tileColumn);

    public int getTileSourceY(int leve, int tileRow);

    public int getTileSourceWidth(int level, int tileColumn);

    public int getTileSourceHeight(int level, int tileRow);

    public int getTileColumn(int level, int sourceX);

    public int getTileRow(int level, int sourceY);
}
