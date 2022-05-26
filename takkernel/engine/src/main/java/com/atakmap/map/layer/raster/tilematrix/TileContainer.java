package com.atakmap.map.layer.raster.tilematrix;

import android.graphics.Bitmap;

public interface TileContainer extends TileMatrix {

    /**
     * Returns <code>true</code> if the tile container is read-only.
     * 
     * @return  <code>true</code> if the tile container is read-only,
     *          <code>false</code> otherwise.
     */
    public boolean isReadOnly();
    /**
     * Sets the data for the specified tile in the container.
     * 
     * @param level         The tile zoom level
     * @param x             The tile column
     * @param y             The tile row
     * @param data          The tile data
     * @param expiration    The expiration for the tile data, specified in
     *                      epoch milliseconds (ignored if
     *                      {@link #hasTileExpirationMetadata()} returns
     *                      <code>false</code>)
     * 
     * @throws UnsupportedOperationException    If the container is read-only
     */
    public void setTile(int level, int x, int y, byte[] data, long expiration);
    /**
     * Sets the data for the specified tile in the container.
     * 
     * @param level         The tile zoom level
     * @param x             The tile column
     * @param y             The tile row
     * @param data          The tile data, as a {@link Bitmap}
     * @param expiration    The expiration for the tile data, specified in
     *                      epoch milliseconds (ignored if
     *                      {@link #hasTileExpirationMetadata()} returns
     *                      <code>false</code>)
     * 
     * @throws UnsupportedOperationException    If the container is read-only
     * @throws TileEncodeException              If an error occurs while
     *                                          encoding the tile data into
     *                                          a serialized representation.
     */
    public void setTile(int level, int x, int y, Bitmap data, long expiration) throws TileEncodeException;
    /**
     * Returns <code>true</code> if expiration metadata is associated with tiles
     * in the container.
     * 
     * @return   <code>true</code> if expiration metadata is associated with
     *           tiles in the container, <code>false</code> otherwise.
     */
    public boolean hasTileExpirationMetadata();
    /**
     * Returns the expiration date of the specified tile, in epoch milliseconds,
     * or <code>-1LL</code> if the tile does not exist or expiration metadata is
     * not available for this container.
     * 
     * @param level The zoom level
     * @param x     The tile column
     * @param y     The tile row
     * 
     * @return  The expiration date of the specified tile, in epoch
     *          milliseconds, or <code>-1LL</code> if the tile does not exist or
     *          expiration metadata is not available for this container.
     */
    public long getTileExpiration(int level, int x, int y);
}
