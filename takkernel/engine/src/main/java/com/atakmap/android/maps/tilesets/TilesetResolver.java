
package com.atakmap.android.maps.tilesets;

public interface TilesetResolver {
    /**
     * Resolves the URI for a given tile. The tile grid offsets are defined by the native tiling
     * format.
     * 
     * @param info
     * @param tileRow
     * @param tileColumn
     * @param level
     * @return
     */
    public String resolve(TilesetInfo info, int tileRow, int tileColumn,
            int level);
}
