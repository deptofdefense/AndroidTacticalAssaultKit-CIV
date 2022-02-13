package com.atakmap.map.formats.quantizedmesh;

import gov.tak.api.annotation.DontObfuscate;

/**
 * Tile extents metadata
 */
@DontObfuscate
public interface TileExtents {
    public int getStartX();
    public int getStartY();
    public int getEndX();
    public int getEndY();
    public int getLevel();
}
