package com.atakmap.map.formats.quantizedmesh;

/**
 * Tile extents metadata
 */
public interface TileExtents {
    public int getStartX();
    public int getStartY();
    public int getEndX();
    public int getEndY();
    public int getLevel();
}
